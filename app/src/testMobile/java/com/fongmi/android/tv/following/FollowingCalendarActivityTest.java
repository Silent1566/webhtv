package com.fongmi.android.tv.following;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.adapter.FollowingCalendarEntryAdapter;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Entry;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Episode;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.SeasonSchedule;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.Source;
import com.fongmi.android.tv.following.FollowingCalendarPolicy.TemporalState;
import com.fongmi.android.tv.model.FollowingCalendarViewModel;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Grid;
import com.fongmi.android.tv.model.FollowingCalendarViewModel.Section;

import org.junit.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 追剧日历页的纯布局契约：7 列周一开始的月份网格、周范围，以及按日分段的可见范围。
 *
 * <p>这些计算必须与网络请求无关——周/月切换和选日只重新投影已有快照，所以它们被放在
 * {@link FollowingCalendarViewModel} 的可测静态面上，不依赖 Activity 或 Android 资源。
 */
public class FollowingCalendarActivityTest {

    /** 月份网格必须是整周、周一开始、4-6 行，且覆盖该月每一天。 */
    @Test
    public void monthGridStartsMondayAndSpansFourToSixWeeks() {
        for (String text : new String[]{"2027-02", "2026-02", "2026-11", "2026-08", "2027-01"}) {
            YearMonth month = YearMonth.parse(text);
            Grid grid = FollowingCalendarViewModel.monthGrid(month);
            assertEquals(text + " 必须是整周", 0, grid.cells.size() % 7);
            assertTrue(text + " 行数必须在 4-6 之间，实际 " + grid.rows, grid.rows >= 4 && grid.rows <= 6);
            assertEquals(text + " 必须周一开始", DayOfWeek.MONDAY, grid.cells.get(0).getDayOfWeek());
            assertEquals(text + " 必须周日结束", DayOfWeek.SUNDAY,
                    grid.cells.get(grid.cells.size() - 1).getDayOfWeek());
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                assertTrue(text + " 缺少 " + date, grid.cells.contains(date));
                assertTrue(text + " 的 " + date + " 必须属于本月", grid.inMonth(date));
            }
            for (int index = 1; index < grid.cells.size(); index++) {
                assertEquals("网格日期必须连续", grid.cells.get(index - 1).plusDays(1), grid.cells.get(index));
            }
            for (LocalDate cell : grid.cells) {
                if (grid.inMonth(cell)) continue;
                assertTrue("相邻月格只能是月初之前或月末之后：" + cell,
                        cell.isBefore(month.atDay(1)) || cell.isAfter(month.atEndOfMonth()));
            }
        }
    }

    /** 2027-02 从周一开始且恰好 28 天，网格必须只有 4 行而不是硬编码 5 行。 */
    @Test
    public void februaryStartingOnMondayGetsExactlyFourRows() {
        Grid grid = FollowingCalendarViewModel.monthGrid(YearMonth.of(2027, 2));
        assertEquals(4, grid.rows);
        assertEquals(28, grid.cells.size());
    }

    /** 周视图固定 7 天，跨年也要落在同一周。 */
    @Test
    public void weekGridIsSevenMondayFirstDaysAcrossYear() {
        Grid grid = FollowingCalendarViewModel.weekGrid(LocalDate.of(2027, 1, 1));
        assertEquals(1, grid.rows);
        assertEquals(7, grid.cells.size());
        assertEquals(LocalDate.of(2026, 12, 28), grid.cells.get(0));
        assertEquals(LocalDate.of(2027, 1, 3), grid.cells.get(6));
        assertEquals(FollowingCalendarPolicy.weekStart(LocalDate.of(2027, 1, 1)), grid.cells.get(0));
    }

    /** 文档顺序：日期分段在前，日期待定与本地记录始终保留，且只取可见范围。 */
    @Test
    public void sectionsKeepDateOrderAndAlwaysKeepUndatedAndLocalRecords() {
        LocalDate today = LocalDate.of(2026, 11, 26);
        Following alpha = following("tmdb:tv:900001:s1", "Alpha", 900001, 1, 0);
        Following bravo = following("tmdb:tv:900002:s1", "Bravo", 900002, 1, 1);
        Following local = following("local:s1", "No TMDB", 0, 1, 0);

        Map<String, SeasonSchedule> schedules = new LinkedHashMap<>();
        schedules.put(bravo.identityKey, schedule(1, Source.NETWORK,
                new Episode(1, 1, "Pilot", "2026-11-25"),
                new Episode(1, 2, "", "2026-11-26"),
                new Episode(1, 3, "", null)));
        schedules.put(alpha.identityKey, schedule(1, Source.FRESH_CACHE,
                new Episode(1, 1, "Start", "2026-11-26")));

        FollowingCalendarPolicy.CalendarProjection projection = FollowingCalendarPolicy.project(
                List.of(alpha, bravo, local), schedules, today);

        List<Section> sections = FollowingCalendarViewModel.sections(
                projection, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29));
        assertEquals(4, sections.size());

        assertEquals(Section.Kind.DAY, sections.get(0).kind);
        assertEquals(LocalDate.of(2026, 11, 25), sections.get(0).day);
        assertEquals(1, sections.get(0).entries.size());
        assertEquals(TemporalState.PAST, sections.get(0).entries.get(0).temporalState);

        assertEquals(LocalDate.of(2026, 11, 26), sections.get(1).day);
        assertEquals(2, sections.get(1).entries.size());
        assertEquals("Alpha", sections.get(1).entries.get(0).title);
        assertEquals("Bravo", sections.get(1).entries.get(1).title);
        for (Entry entry : sections.get(1).entries) assertEquals(TemporalState.TODAY, entry.temporalState);

        assertEquals(Section.Kind.UNDATED, sections.get(2).kind);
        assertEquals(1, sections.get(2).entries.size());
        assertEquals(3, sections.get(2).entries.get(0).episodeNumber);
        assertEquals(TemporalState.UNKNOWN, sections.get(2).entries.get(0).temporalState);

        assertEquals(Section.Kind.LOCAL, sections.get(3).kind);
        assertEquals(1, sections.get(3).records.size());
        assertEquals(FollowingCalendarPolicy.LocalReason.NO_IDENTITY, sections.get(3).records.get(0).reason);
    }

    /** 选中的空日只影响那一天：其它日期分段、待定与本地记录仍然可见。 */
    @Test
    public void emptyDayKeepsEveryOtherSectionAvailable() {
        LocalDate today = LocalDate.of(2026, 11, 26);
        Following bravo = following("tmdb:tv:900002:s1", "Bravo", 900002, 1, 0);
        Map<String, SeasonSchedule> schedules = new LinkedHashMap<>();
        schedules.put(bravo.identityKey, schedule(1, Source.NETWORK,
                new Episode(1, 1, "", "2026-11-24"),
                new Episode(1, 2, "", null)));
        FollowingCalendarPolicy.CalendarProjection projection = FollowingCalendarPolicy.project(
                List.of(bravo), schedules, today);

        List<Section> sections = FollowingCalendarViewModel.sections(
                projection, LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 30));
        assertEquals(2, sections.size());
        assertEquals(LocalDate.of(2026, 11, 24), sections.get(0).day);
        assertFalse(FollowingCalendarViewModel.hasDaySection(sections, LocalDate.of(2026, 11, 26)));
        assertTrue(FollowingCalendarViewModel.hasDaySection(sections, LocalDate.of(2026, 11, 24)));
        assertEquals(Section.Kind.UNDATED, sections.get(1).kind);
    }

    /** 未来/今天/过去只是排期日关系，不得被当成可播或已播断言。 */
    @Test
    public void rangeBoundsAreInclusiveAndFollowRecomputedToday() {
        LocalDate today = LocalDate.of(2026, 11, 26);
        Following alpha = following("tmdb:tv:900001:s1", "Alpha", 900001, 1, 0);
        Map<String, SeasonSchedule> schedules = new LinkedHashMap<>();
        schedules.put(alpha.identityKey, schedule(1, Source.NETWORK,
                new Episode(1, 1, "", "2026-11-26"),
                new Episode(1, 2, "", "2026-11-27")));
        FollowingCalendarPolicy.CalendarProjection projection = FollowingCalendarPolicy.project(
                List.of(alpha), schedules, today);

        List<Section> tomorrow = FollowingCalendarViewModel.sections(
                projection, LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 27));
        assertEquals(1, tomorrow.size());
        assertEquals(TemporalState.FUTURE, tomorrow.get(0).entries.get(0).temporalState);

        LocalDate nextDay = today.plusDays(1);
        FollowingCalendarPolicy.CalendarProjection later = FollowingCalendarPolicy.project(
                List.of(alpha), schedules, nextDay);
        assertEquals(TemporalState.TODAY, later.days.get(LocalDate.of(2026, 11, 27)).get(0).temporalState);
        assertNotNull(later.days.get(LocalDate.of(2026, 11, 26)));
    }

    /** pause 之前排队、pause 之后才执行的回调不得覆写快照，也不得再惊动界面。 */
    @Test
    public void snapshotQueuedBeforePauseIsDropped() {
        FakeLoader loader = new FakeLoader();
        ManualDispatcher dispatcher = new ManualDispatcher();
        FollowingCalendarViewModel viewModel = new FollowingCalendarViewModel(loader, Clock.systemUTC(), dispatcher);
        int[] delivered = new int[1];
        viewModel.setListener(() -> delivered[0]++);

        viewModel.load(List.<Following>of(), false);
        FollowingCalendarViewModel.Sink sink = loader.sink;
        sink.onSnapshot(null);
        viewModel.pause();
        dispatcher.drain();

        assertEquals("pause 后的迟到回调必须被代次挡掉", 0, delivered[0]);
        assertNull(viewModel.snapshot());
        assertEquals(1, loader.cancels);
    }

    /** 只有最新一次 load 的回调能投递，旧代次即使先到达也要丢弃。 */
    @Test
    public void staleLoadCannotDeliverAfterNewerLoad() {
        FakeLoader loader = new FakeLoader();
        ManualDispatcher dispatcher = new ManualDispatcher();
        FollowingCalendarViewModel viewModel = new FollowingCalendarViewModel(loader, Clock.systemUTC(), dispatcher);
        int[] delivered = new int[1];
        viewModel.setListener(() -> delivered[0]++);

        viewModel.load(List.<Following>of(), false);
        FollowingCalendarViewModel.Sink first = loader.sinks.get(0);
        viewModel.load(List.<Following>of(), false);
        FollowingCalendarViewModel.Sink second = loader.sinks.get(1);

        first.onSnapshot(null);
        dispatcher.drain();
        assertEquals(0, delivered[0]);

        second.onSnapshot(null);
        dispatcher.drain();
        assertEquals(1, delivered[0]);
    }

    /** 回前台每次都重读：core 用同 key 去重，这里必须是 load(false)，不能自动强刷。 */
    @Test
    public void resumeAlwaysReadsFreshSnapshotWithoutForcedRefresh() {
        FakeLoader loader = new FakeLoader();
        ManualDispatcher dispatcher = new ManualDispatcher();
        FollowingCalendarViewModel viewModel = new FollowingCalendarViewModel(loader, Clock.systemUTC(), dispatcher);

        viewModel.resume(List.<Following>of());
        viewModel.resume(List.<Following>of());

        assertEquals(2, loader.loads);
        assertFalse(loader.refreshed);
    }

    /** 显式刷新才允许 refresh=true。 */
    @Test
    public void explicitLoadCarriesRefreshFlag() {
        FakeLoader loader = new FakeLoader();
        ManualDispatcher dispatcher = new ManualDispatcher();
        FollowingCalendarViewModel viewModel = new FollowingCalendarViewModel(loader, Clock.systemUTC(), dispatcher);

        viewModel.load(List.<Following>of(), true);

        assertEquals(1, loader.loads);
        assertTrue(loader.refreshed);
    }

    @Test
    public void futureAndUnknownDatesHaveNoUnwatchedLabel() {
        assertEquals(0, FollowingCalendarEntryAdapter.watchLabelResource(TemporalState.FUTURE, false));
        assertEquals(0, FollowingCalendarEntryAdapter.watchLabelResource(TemporalState.UNKNOWN, false));
        assertEquals(0, FollowingCalendarEntryAdapter.watchLabelResource(TemporalState.FUTURE, true));
        assertEquals(0, FollowingCalendarEntryAdapter.watchLabelResource(TemporalState.UNKNOWN, true));
        assertEquals(R.string.following_calendar_temporal_future,
                FollowingCalendarEntryAdapter.temporalLabelResource(TemporalState.FUTURE));
        assertEquals(R.string.following_calendar_temporal_unknown,
                FollowingCalendarEntryAdapter.temporalLabelResource(TemporalState.UNKNOWN));
    }

    @Test
    public void pastAndTodayKeepReadOnlyViewingStateSeparateFromScheduleState() {
        for (TemporalState state : List.of(TemporalState.PAST, TemporalState.TODAY)) {
            assertEquals(R.string.following_calendar_watched, FollowingCalendarEntryAdapter.watchLabelResource(state, true));
            assertEquals(R.string.following_calendar_unwatched, FollowingCalendarEntryAdapter.watchLabelResource(state, false));
        }
        assertEquals(R.string.following_calendar_temporal_past,
                FollowingCalendarEntryAdapter.temporalLabelResource(TemporalState.PAST));
        assertEquals(R.string.following_calendar_temporal_today,
                FollowingCalendarEntryAdapter.temporalLabelResource(TemporalState.TODAY));
    }

    @Test
    public void initialAndRangeChangesDoNotScrollEvenWhenSelectedDayHasEntries() {
        assertFalse(FollowingCalendarViewModel.shouldScrollToSection(false, 0));
        assertFalse(FollowingCalendarViewModel.shouldScrollToSection(false, 10));
    }

    @Test
    public void explicitEmptyDaySelectionDoesNotScrollToEntriesTop() {
        assertFalse(FollowingCalendarViewModel.shouldScrollToSection(true, -1));
    }

    @Test
    public void explicitSelectionOfExistingDaySectionStillScrolls() {
        assertTrue(FollowingCalendarViewModel.shouldScrollToSection(true, 0));
        assertTrue(FollowingCalendarViewModel.shouldScrollToSection(true, 10));
    }

    private static final class FakeLoader implements FollowingCalendarViewModel.Loader {
        private final List<FollowingCalendarViewModel.Sink> sinks = new ArrayList<>();
        private FollowingCalendarViewModel.Sink sink;
        private boolean refreshed;
        private int loads, cancels, closes;

        @Override
        public void load(List<Following> items, boolean refresh, FollowingCalendarViewModel.Sink sink) {
            loads++;
            refreshed |= refresh;
            this.sink = sink;
            sinks.add(sink);
        }

        @Override
        public void cancel() {
            cancels++;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class ManualDispatcher implements FollowingCalendarViewModel.Dispatcher {
        private final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void post(Runnable action) {
            queue.add(action);
        }

        private void drain() {
            Runnable action;
            while ((action = queue.poll()) != null) action.run();
        }
    }

    private static Following following(String identityKey, String title, int tmdbId, int season, int watchedEpisode) {
        Following item = new Following();
        item.identityKey = identityKey;
        item.seriesKey = identityKey.substring(0, Math.max(0, identityKey.lastIndexOf(':')));
        item.mediaType = "tv";
        item.tmdbId = tmdbId;
        item.vodName = title;
        item.trackedSeason = season;
        item.watchedSeason = season;
        item.watchedEpisode = watchedEpisode;
        item.createdAt = 1L;
        item.updatedAt = 1L;
        return item;
    }

    private static SeasonSchedule schedule(int season, Source source, Episode... episodes) {
        return new SeasonSchedule(season, List.of(episodes), source, 1_700_000_000_000L, "", 0, null);
    }
}
