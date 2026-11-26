package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFollowingCalendarBinding;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.CalendarProjection;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Entry;
import com.fongmi.android.tv.following.FollowingCalendarRepository.CalendarSnapshot;
import com.fongmi.android.tv.following.FollowingSource;
import com.fongmi.android.tv.following.FollowingStore;
import com.fongmi.android.tv.model.FollowingCalendarViewModel;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Grid;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Mode;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Section;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.ui.adapter.FollowingCalendarDayAdapter;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.FollowingCalendarEntryAdapter;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 只读追剧日历：整周/整月 + 按日分段，手机与电视同构。
 *
 * <p>页面只读：不标已读、不改通知、不写观看进度，也不判断某个站源此刻能否播放。
 * 排期日只表达 TMDB 的 air_date；“待播/今日排期/已到排期日”是日期关系，不是可播性结论。
 * 周/月切换、翻页与选日都只重新投影 ViewModel 里已有的快照，不发起任何请求。
 * 点集行只把身份通过 Activity Result 交回原追剧列表定位。
 */
public class FollowingCalendarActivity extends AppCompatActivity
        implements FollowingCalendarDayAdapter.Listener, FollowingCalendarEntryAdapter.Listener {

    private static final String STATE_MODE = "following_calendar_mode";
    private static final String STATE_SELECTED = "following_calendar_selected";
    private static final String STATE_ANCHOR = "following_calendar_anchor";
    private static final String TMDB_HOME = "https://www.themoviedb.org/";

    private final FollowingCalendarViewModel.Listener snapshotListener = this::onCalendarSnapshot;
    private ActivityFollowingCalendarBinding binding;
    private FollowingCalendarViewModel viewModel;
    private FollowingCalendarDayAdapter dayAdapter;
    private FollowingCalendarEntryAdapter entryAdapter;
    private boolean visible;
    private int loadEpoch;
    private Mode mode = Mode.WEEK;
    private LocalDate selectedDate;
    private LocalDate anchor;
    private boolean scrollPending;
    private long scrollEpoch;

    public static Intent intent(Context context) {
        return new Intent(context, FollowingCalendarActivity.class);
    }

    public static void start(Activity activity) {
        activity.startActivity(intent(activity));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        super.onCreate(savedInstanceState);
        // 与追更页一致：先解析持久化外观 token，再建立视图，最后再绑定一次主题。
        ThemeController.applyFromPreferences(this);
        binding = ActivityFollowingCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ThemeController.bindTheme(binding.getRoot());
        addWallpaper();
        viewModel = new ViewModelProvider(this).get(FollowingCalendarViewModel.class);
        restoreState(savedInstanceState);
        initView();
        ThemeController.bindTheme(binding.getRoot());
    }

    private void addWallpaper() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        content.addView(new CustomWallView(this, null), 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void restoreState(Bundle state) {
        LocalDate today = viewModel.today();
        // 日历的主任务是「这周看什么」，默认周视图；用户切到月视图后按状态恢复。
        mode = state != null && Mode.MONTH.name().equals(state.getString(STATE_MODE)) ? Mode.MONTH : Mode.WEEK;
        selectedDate = parse(state == null ? null : state.getString(STATE_SELECTED), today);
        anchor = parse(state == null ? null : state.getString(STATE_ANCHOR), selectedDate);
        scrollPending = false;
    }

    private static LocalDate parse(String isoDate, LocalDate fallback) {
        if (isoDate == null) return fallback;
        try {
            return LocalDate.parse(isoDate);
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private void initView() {
        binding.grid.setLayoutManager(new GridLayoutManager(this, 7));
        binding.grid.setAdapter(dayAdapter = new FollowingCalendarDayAdapter(this));
        entryAdapter = new FollowingCalendarEntryAdapter(this);
        int width = getResources().getConfiguration().screenWidthDp - 28;
        GridLayoutManager agenda = new GridLayoutManager(this, width >= 608 ? 2 : 1);
        agenda.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return entryAdapter.getItemViewType(position) != 1 ? agenda.getSpanCount() : 1;
            }
        });
        binding.entries.setLayoutManager(agenda);
        binding.entries.setAdapter(entryAdapter);
        initWeekdays();
        binding.prev.setOnClickListener(view -> shift(-1));
        binding.next.setOnClickListener(view -> shift(1));
        binding.today.setOnClickListener(view -> selectToday());
        binding.refresh.setOnClickListener(view -> refreshItems(true));
        binding.modeGroup.check(mode == Mode.WEEK ? R.id.weekMode : R.id.monthMode);
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            Mode next = checkedId == R.id.weekMode ? Mode.WEEK : Mode.MONTH;
            if (next == mode) return;
            mode = next;
            anchor = selectedDate;
            scrollPending = false;
            render();
        });
        binding.attribution.setOnClickListener(view -> openAttribution());
    }

    /** 星期表头沿用已有 following_weekdays（周日开头），这里按周一开始的列序取用。 */
    private void initWeekdays() {
        String[] names = getResources().getStringArray(R.array.following_weekdays);
        int count = binding.weekdays.getChildCount();
        for (int index = 0; index < count; index++) {
            View child = binding.weekdays.getChildAt(index);
            if (!(child instanceof TextView)) continue;
            int weekday = index + 1;
            TextView label = (TextView) child;
            label.setText(names[weekday % names.length]);
            label.setContentDescription(names[weekday % names.length]);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        viewModel.setListener(snapshotListener);
        // 每次回前台都重读一次最新追剧快照：配置/语言/跟踪季变化才会换批读取，
        // 同 key 时 core 直接重投递当前快照，因此这里不会产生重复请求。
        refreshItems(false);
        render();
    }

    @Override
    protected void onStop() {
        visible = false;
        loadEpoch++;
        scrollEpoch++;
        scrollPending = false;
        viewModel.clearListener(snapshotListener);
        // 旋转重建时保留在途读取（快照留在 ViewModel 里），只有真正离开页面才停止排队。
        if (!isChangingConfigurations()) viewModel.pause();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MODE, mode.name());
        outState.putString(STATE_SELECTED, selectedDate == null ? null : selectedDate.toString());
        outState.putString(STATE_ANCHOR, anchor == null ? null : anchor.toString());
    }

    private void onCalendarSnapshot() {
        if (binding == null || isFinishing() || isDestroyed()) return;
        render();
    }

    /**
     * 读库在后台线程，回到界面线程还要过三道门：页面仍可见、代次未作废、视图还在。
     * 因此页面停止后迟到的读库回调不会重新发起 load。
     */
    private void refreshItems(boolean refresh) {
        CalendarSnapshot snapshot = viewModel.snapshot();
        if (refresh && snapshot != null && snapshot.loading) return;
        int issued = ++loadEpoch;
        Task.execute(() -> {
            List<Following> list = FollowingStore.list();
            App.post(() -> {
                if (!visible || issued != loadEpoch || binding == null || isFinishing() || isDestroyed()) return;
                if (refresh) viewModel.load(list, true);
                else viewModel.resume(list);
                render();
            });
        });
    }

    private Grid currentGrid() {
        return mode == Mode.WEEK ? FollowingCalendarViewModel.weekGrid(anchor) : FollowingCalendarViewModel.monthGrid(YearMonth.from(anchor));
    }

    private void shift(int direction) {
        anchor = mode == Mode.WEEK ? anchor.plusWeeks(direction) : anchor.plusMonths(direction);
        if (mode == Mode.MONTH) anchor = anchor.withDayOfMonth(1);
        if (!currentGrid().cells.contains(selectedDate)) selectedDate = anchor;
        scrollPending = false;
        render();
    }

    private void selectToday() {
        LocalDate today = viewModel.today();
        anchor = today;
        selectedDate = today;
        scrollPending = false;
        render();
    }

    private void render() {
        if (binding == null) return;
        scrollEpoch++;
        LocalDate today = viewModel.today();
        if (selectedDate == null) selectedDate = today;
        if (anchor == null) anchor = selectedDate;
        CalendarSnapshot snapshot = viewModel.snapshot();
        CalendarProjection projection = snapshot == null ? null : snapshot.project(today);
        Grid grid = currentGrid();

        binding.rangeLabel.setText(mode == Mode.WEEK
                ? getString(R.string.following_calendar_week_range, grid.cells.get(0).getMonthValue(), grid.cells.get(0).getDayOfMonth(),
                grid.cells.get(6).getMonthValue(), grid.cells.get(6).getDayOfMonth())
                : getString(R.string.following_calendar_month_label, YearMonth.from(anchor).getYear(), YearMonth.from(anchor).getMonthValue()));
        binding.modeGroup.check(mode == Mode.WEEK ? R.id.weekMode : R.id.monthMode);
        // 整页只有一个滚动面：日期面板跟着滚走，所以月历可以完整展开 4–6 行，
        // 不再需要把月切成固定行数、再让日期面板自己滚一层。
        int maxRows = mode == Mode.WEEK ? 1 : grid.rows;
        binding.statusLabel.setText(statusText(snapshot));
        binding.statusLabel.setVisibility(snapshot == null || snapshot.loading || snapshot.failedSeasons > 0
                || snapshot.retryAtMillis > System.currentTimeMillis() ? View.VISIBLE : View.GONE);
        int rowHeight = Math.round(48 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams gridParams = binding.grid.getLayoutParams();
        gridParams.height = rowHeight * Math.min(grid.rows, maxRows);
        binding.grid.setLayoutParams(gridParams);
        binding.loading.setVisibility(snapshot != null && snapshot.loading ? View.VISIBLE : View.GONE);
        binding.refresh.setEnabled(snapshot == null || !snapshot.loading);

        Map<LocalDate, List<Entry>> days = projection == null ? Collections.emptyMap() : projection.days;
        List<FollowingCalendarDayAdapter.Cell> cells = new ArrayList<>(grid.cells.size());
        for (LocalDate day : grid.cells) {
            List<Entry> entries = days.get(day);
            cells.add(new FollowingCalendarDayAdapter.Cell(day, mode == Mode.MONTH && !grid.inMonth(day),
                    entries == null ? 0 : entries.size(), day.equals(today), day.equals(selectedDate)));
        }
        dayAdapter.setCells(cells, today);

        LocalDate first = grid.cells.get(0);
        LocalDate last = grid.cells.get(grid.cells.size() - 1);
        List<Section> sections = projection == null ? Collections.emptyList() : FollowingCalendarViewModel.sections(projection, first, last);
        entryAdapter.setSections(this, sections, today);

        boolean settled = snapshot != null && !snapshot.loading;
        boolean nothing = sections.isEmpty() && settled;
        // 「没有任何追更」和「这一周/这月没有排期」是两件事：前者要求用户先去加追更，
        // 后者只需要他换个范围看，用同一句话会把已有追更的用户误导成配置丢了。
        boolean hasFollowings = snapshot != null && snapshot.totalSeasons > 0;
        boolean emptyRange = nothing && hasFollowings;
        binding.entries.setVisibility(emptyRange ? View.GONE : View.VISIBLE);
        // 空日提示只在加载结束后给出：加载中既不能断言“当天没排期”，也不能把空日当成整月空。
        boolean emptyDay = settled && !nothing && !FollowingCalendarViewModel.hasDaySection(sections, selectedDate);
        binding.empty.setVisibility(nothing && !hasFollowings ? View.VISIBLE : View.GONE);
        binding.dayNotice.setVisibility(emptyDay || emptyRange ? View.VISIBLE : View.GONE);
        binding.dayNotice.setText(emptyRange
                ? getString(mode == Mode.WEEK ? R.string.following_calendar_empty_week : R.string.following_calendar_empty_month)
                : getString(R.string.following_calendar_empty_day));
        int position = entryAdapter.indexOfDay(selectedDate);
        if (FollowingCalendarViewModel.shouldScrollToSection(scrollPending, position)) scrollToSelection(position);
        scrollPending = false;
    }

    private String statusText(CalendarSnapshot snapshot) {
        if (snapshot == null) return getString(R.string.following_calendar_reason_loading);
        long now = System.currentTimeMillis();
        if (snapshot.retryAtMillis > now)
            return getString(R.string.following_calendar_status_rate, Math.max(1L, (snapshot.retryAtMillis - now) / 1000L));
        if (snapshot.loading) return getString(R.string.following_calendar_status_loading, snapshot.completedSeasons, snapshot.totalSeasons);
        int loaded = Math.max(0, snapshot.completedSeasons - snapshot.failedSeasons);
        return getString(R.string.following_calendar_status_idle, snapshot.totalSeasons, loaded, snapshot.failedSeasons);
    }

    /** 选中某天后把该日分段滚到视口顶部附近：整页共用一个滚动面，所以滚动的是页面而不是列表。 */
    private void scrollToSelection(int position) {
        ActivityFollowingCalendarBinding target = binding;
        long issued = scrollEpoch;
        target.entries.post(() -> {
            if (!visible || binding != target || issued != scrollEpoch || isFinishing() || isDestroyed()) return;
            RecyclerView.ViewHolder holder = target.entries.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            int offset = Math.max(0, target.entries.getTop() + holder.itemView.getTop() - Math.round(8 * getResources().getDisplayMetrics().density));
            target.scroll.smoothScrollTo(0, offset);
        });
    }

    @Override
    public void onSelectDay(LocalDate day) {
        if (day == null) return;
        selectedDate = day;
        if (mode == Mode.WEEK ? !FollowingCalendarViewModel.weekGrid(anchor).cells.contains(day)
                : !YearMonth.from(day).equals(YearMonth.from(anchor))) anchor = day;
        scrollPending = true;
        render();
    }

    @Override
    /**
     * 点某条排期：和追更页的「继续看」走同一条链路——有历史就续播，没有就打开详情页选线路。
     * 日历本身仍是只读的：它不标已读、不改通知，只负责把用户带到那一集。
     */
    public void onEntryIdentity(String identityKey) {
        if (identityKey == null || identityKey.isEmpty()) return;
        Task.execute(() -> {
            Following item = FollowingStore.find(identityKey);
            if (item == null) return;
            FollowingStore.reconcile(item);
            FollowingSource source = FollowingStore.preferredSource(identityKey);
            History history = FollowingStore.historyFor(item, source);
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (history != null) {
                    VideoActivity.startFromFollowingHistory(this, history);
                    finish();
                    return;
                }
                FollowingSource target = source != null ? source : fallbackSource(item);
                if (target == null || target.siteKey == null || target.siteKey.isEmpty()
                        || target.vodId == null || target.vodId.isEmpty()) {
                    Notify.show(R.string.following_check_failed);
                    return;
                }
                TmdbItem tmdb = item.tmdbId > 0 ? new TmdbItem(item.tmdbId, item.mediaType, item.vodName,
                        "", "", item.vodPic, "", "", 0.0) : null;
                TmdbDetailActivity.start(this, target.siteKey, target.vodId, item.vodName, item.vodPic, "",
                        tmdb, Setting.getDetailOpenMode());
                finish();
            });
        });
    }

    /** 没有优先源时退回追更记录本身的站点/条目，和追更页 preferred() 的兜底一致。 */
    private static FollowingSource fallbackSource(Following item) {
        FollowingSource target = new FollowingSource();
        target.followingKey = item.identityKey;
        target.cid = item.cid;
        target.siteKey = item.siteKey;
        target.vodId = item.vodId;
        target.vodName = item.vodName;
        target.vodPic = item.vodPic;
        target.preferred = true;
        return target;
    }

    private void openAttribution() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TMDB_HOME)));
        } catch (ActivityNotFoundException error) {
            Notify.show(error.getMessage());
        }
    }
}
