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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFollowingCalendarBinding;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.CalendarProjection;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Entry;
import com.fongmi.android.tv.following.FollowingCalendarRepository.CalendarSnapshot;
import com.fongmi.android.tv.following.FollowingStore;
import com.fongmi.android.tv.model.FollowingCalendarViewModel;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Grid;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Mode;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Section;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.ui.adapter.FollowingCalendarDayAdapter;
import com.fongmi.android.tv.ui.adapter.FollowingCalendarEntryAdapter;
import com.fongmi.android.tv.ui.custom.CustomWallView;
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
    private Mode mode = Mode.MONTH;
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
        mode = state != null && Mode.WEEK.name().equals(state.getString(STATE_MODE)) ? Mode.WEEK : Mode.MONTH;
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
        binding.entries.setLayoutManager(new LinearLayoutManager(this));
        binding.entries.setAdapter(entryAdapter = new FollowingCalendarEntryAdapter(this));
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
                ? getString(R.string.following_calendar_week_label, grid.cells.get(0).getMonthValue(), grid.cells.get(0).getDayOfMonth(),
                grid.cells.get(6).getMonthValue(), grid.cells.get(6).getDayOfMonth())
                : getString(R.string.following_calendar_month_label, YearMonth.from(anchor).getYear(), YearMonth.from(anchor).getMonthValue()));
        binding.modeGroup.check(mode == Mode.WEEK ? R.id.weekMode : R.id.monthMode);
        binding.statusLabel.setText(statusText(snapshot));
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
        // 空日提示只在加载结束后给出：加载中既不能断言“当天没排期”，也不能把空日当成整月空。
        boolean emptyDay = settled && !nothing && !FollowingCalendarViewModel.hasDaySection(sections, selectedDate);
        binding.empty.setVisibility(nothing ? View.VISIBLE : View.GONE);
        binding.dayNotice.setVisibility(emptyDay ? View.VISIBLE : View.GONE);
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

    private void scrollToSelection(int position) {
        ActivityFollowingCalendarBinding target = binding;
        long issued = scrollEpoch;
        target.scroll.post(() -> scrollToSection(target, issued, position, 0));
    }

    private void scrollToSection(ActivityFollowingCalendarBinding target, long issued, int position, int attempt) {
        if (!visible || binding != target || issued != scrollEpoch || isFinishing() || isDestroyed()) return;
        RecyclerView.ViewHolder holder = target.entries.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            if (attempt < 2) target.entries.postOnAnimation(() -> scrollToSection(target, issued, position, attempt + 1));
            return;
        }
        int offset = Math.max(0, target.entries.getTop() + holder.itemView.getTop());
        target.scroll.smoothScrollTo(0, offset);
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
    public void onEntryIdentity(String identityKey) {
        Intent data = new Intent();
        data.putExtra(FollowingActivity.EXTRA_IDENTITY_KEY, identityKey);
        setResult(RESULT_OK, data);
        finish();
    }

    private void openAttribution() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TMDB_HOME)));
        } catch (ActivityNotFoundException error) {
            Notify.show(error.getMessage());
        }
    }
}
