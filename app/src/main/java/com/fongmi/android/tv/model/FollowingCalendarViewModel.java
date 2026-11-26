package com.fongmi.android.tv.model;

import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingCalendarPolicy;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.CalendarProjection;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Entry;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.LocalRecord;
import com.fongmi.android.tv.following.FollowingCalendarRepository;
import com.fongmi.android.tv.following.FollowingCalendarRepository.CalendarSnapshot;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 只读日历页的状态持有者：仓库与最近一次快照跨旋转复用，界面只在可见时接收投递。
 *
 * <p>周/月切换与选日只调用 {@link CalendarSnapshot#project(LocalDate)}，不触发任何请求；
 * 请求只发生在首次进入、回到前台与显式刷新。回到前台时 core 会用最新追剧快照与配置比较
 * 作用域，同 key 直接重投递、不联网。{@link #onCleared()} 关闭仓库；{@link #pause()} 把当前
 * 代次作废并停止排队，因此 pause 之前排队、pause 之后才执行的回调不能覆写快照。
 */
public class FollowingCalendarViewModel extends ViewModel {

    public interface Listener {
        void onCalendarSnapshot();
    }

    /** 仓储回调的接收端；回调在线程池上执行，由本类投递到界面线程。 */
    public interface Sink {
        void onSnapshot(CalendarSnapshot snapshot);
    }

    /** 只读读取器接缝，默认实现直接委托 {@link FollowingCalendarRepository}。 */
    public interface Loader {
        void load(List<Following> items, boolean refresh, Sink sink);

        void cancel();

        void close();
    }

    /** 界面线程投递接缝，默认实现是 {@link App#post(Runnable)}。 */
    public interface Dispatcher {
        void post(Runnable action);
    }

    public enum Mode { WEEK, MONTH }

    private final Loader loader;
    private final Clock clock;
    private final Dispatcher dispatcher;
    private Listener listener;
    private CalendarSnapshot snapshot;
    private boolean closed;
    private long epoch;

    public FollowingCalendarViewModel() {
        this(new RepositoryLoader(new FollowingCalendarRepository()), Clock.systemUTC(), App::post);
    }

    public FollowingCalendarViewModel(Loader loader, Clock clock, Dispatcher dispatcher) {
        this.loader = loader;
        this.clock = clock;
        this.dispatcher = dispatcher;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void clearListener(Listener owner) {
        if (listener == owner) listener = null;
    }

    public CalendarSnapshot snapshot() {
        return snapshot;
    }

    /** 一次渲染只计算一次今天；跨午夜或回前台由界面重新渲染取得新值。 */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.systemDefault()));
    }

    public void load(List<Following> items, boolean refresh) {
        if (closed) return;
        long issued = ++epoch;
        loader.load(items, refresh, snapshot -> dispatcher.post(() -> deliver(issued, snapshot)));
    }

    /**
     * 回到前台：每条新快照都要让 core 做一次作用域比较。同 key 时它只重投递当前快照，
     * 因此频繁回前台不会产生请求；配置/语言/跟踪季变化才会换批读取。
     */
    public void resume(List<Following> items) {
        load(items, false);
    }

    public void pause() {
        if (closed) return;
        epoch++;
        loader.cancel();
    }

    private void deliver(long issued, CalendarSnapshot next) {
        if (closed || issued != epoch) return;
        snapshot = next;
        Listener target = listener;
        if (target != null) target.onCalendarSnapshot();
    }

    @Override
    protected void onCleared() {
        closed = true;
        epoch++;
        listener = null;
        loader.close();
    }

    public static final class Grid {
        public final List<LocalDate> cells;
        public final int rows;
        private final YearMonth month;

        private Grid(List<LocalDate> cells, YearMonth month) {
            this.cells = Collections.unmodifiableList(cells);
            this.rows = cells.size() / 7;
            this.month = month;
        }

        /** 相邻月格为 false：网格只弱化它们，不隐藏，选中后跳到所属月。 */
        public boolean inMonth(LocalDate day) {
            return month != null && YearMonth.from(day).equals(month);
        }
    }

    public static final class Section {
        public enum Kind { DAY, UNDATED, LOCAL }

        public final Kind kind;
        public final LocalDate day;
        public final List<Entry> entries;
        public final List<LocalRecord> records;

        private Section(Kind kind, LocalDate day, List<Entry> entries, List<LocalRecord> records) {
            this.kind = kind;
            this.day = day;
            this.entries = Collections.unmodifiableList(entries);
            this.records = Collections.unmodifiableList(records);
        }
    }

    public static Grid weekGrid(LocalDate day) {
        LocalDate start = FollowingCalendarPolicy.weekStart(day);
        List<LocalDate> cells = new ArrayList<>(7);
        for (int offset = 0; offset < 7; offset++) cells.add(start.plusDays(offset));
        return new Grid(cells, null);
    }

    public static Grid monthGrid(YearMonth month) {
        LocalDate start = FollowingCalendarPolicy.weekStart(month.atDay(1));
        LocalDate end = FollowingCalendarPolicy.weekStart(month.atEndOfMonth()).plusDays(6);
        List<LocalDate> cells = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) cells.add(day);
        return new Grid(cells, month);
    }

    public static List<Section> sections(CalendarProjection projection, LocalDate start, LocalDate end) {
        if (projection == null || start == null || end == null || start.isAfter(end)) return Collections.emptyList();
        List<Section> sections = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Entry>> day : projection.days.subMap(start, true, end, true).entrySet()) {
            if (!day.getValue().isEmpty()) sections.add(new Section(Section.Kind.DAY, day.getKey(), day.getValue(), Collections.emptyList()));
        }
        if (!projection.undated.isEmpty()) sections.add(new Section(Section.Kind.UNDATED, null, projection.undated, Collections.emptyList()));
        if (!projection.localRecords.isEmpty()) sections.add(new Section(Section.Kind.LOCAL, null, Collections.emptyList(), projection.localRecords));
        return Collections.unmodifiableList(sections);
    }

    public static boolean shouldScrollToSection(boolean userSelected, int sectionPosition) {
        return userSelected && sectionPosition >= 0;
    }

    public static boolean hasDaySection(List<Section> sections, LocalDate day) {
        for (Section section : sections) {
            if (section.kind == Section.Kind.DAY && day != null && day.equals(section.day)) return true;
        }
        return false;
    }

    private static final class RepositoryLoader implements Loader {
        private final FollowingCalendarRepository repository;

        private RepositoryLoader(FollowingCalendarRepository repository) {
            this.repository = repository;
        }

        @Override
        public void load(List<Following> items, boolean refresh, Sink sink) {
            repository.load(items, refresh, sink::onSnapshot);
        }

        @Override
        public void cancel() {
            repository.cancel();
        }

        @Override
        public void close() {
            repository.close();
        }
    }
}
