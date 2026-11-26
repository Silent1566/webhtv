package com.fongmi.android.tv.following;

import org.junit.Test;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import static org.junit.Assert.*;
import static com.fongmi.android.tv.following.FollowingCalendarPolicy.*;

public class FollowingCalendarPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test public void unknownOrInvalidDatesAreNotToday() {
        for (String date : new String[]{null, "", " ", "2026-02-29", "2026-11-26T00:00:00Z", "2026-1-01", "+2026-11-26", "2026-11-26 "}) assertNull(parseAirDate(date));
        assertEquals(LocalDate.of(2028, 2, 29), parseAirDate("2028-02-29"));
    }

    @Test public void mondayBoundaryAndVariableMonthLengths() {
        assertEquals(LocalDate.of(2026, 12, 28), weekStart(LocalDate.of(2027, 1, 1)));
        assertEquals(28, YearMonth.of(2027, 2).lengthOfMonth());
        assertEquals(29, YearMonth.of(2028, 2).lengthOfMonth());
    }

    @Test public void preservesEveryEpisodeAndFirstValidDuplicateDateWithWarnings() {
        Following item = item("a", 0);
        item.watchedSeason = 0; item.watchedEpisode = 1; item.readWatermarkEpisode = 99;
        SeasonSchedule season = schedule(0, ep(0, 1, null), ep(0, 1, "2026-11-25"), ep(0, 1, "2026-11-27"),
                ep(0, 2, "2026-11-26"), ep(0, 3, "2026-11-26"), ep(0, 4, "bad"), ep(1, 5, "2026-11-26"), ep(0, 0, "2026-11-26"), ep(0, 6, "2035-01-01"));
        CalendarProjection result = project(Collections.singletonList(item), Collections.singletonMap("a", season), TODAY);
        assertEquals(1, result.days.get(TODAY.minusDays(1)).size());
        assertEquals(2, result.days.get(TODAY).size());
        assertEquals(1, result.undated.size());
        assertEquals(3, result.warnings.size());
        Entry past = result.days.firstEntry().getValue().get(0);
        assertEquals(0, past.seasonNumber); assertTrue(past.watched); assertEquals(TemporalState.PAST, past.temporalState);
        assertEquals(TemporalState.TODAY, result.days.get(TODAY).get(0).temporalState);
        assertFalse(result.days.get(TODAY).get(0).watched);
        assertEquals(TemporalState.FUTURE, result.days.lastEntry().getValue().get(0).temporalState);
        assertEquals(99, item.readWatermarkEpisode); assertEquals(0, item.lastCheckedAt);
        assertThrows(UnsupportedOperationException.class, () -> result.days.clear());
        assertThrows(UnsupportedOperationException.class, () -> result.days.get(TODAY).clear());
    }

    @Test public void identitiesStaySeparateDeletedHiddenAndLocalRecordsNeverFabricateEpisodes() {
        Following a = item("a", 1), b = item("b", 1), deleted = item("deleted", 1), local = item("local", 1), movie = item("movie", 1);
        a.enabled = false; a.notifyEnabled = false; a.watchedSeason = 2; a.watchedEpisode = 99;
        b.vodName = "A"; deleted.deletedAt = 1; local.tmdbId = 0; local.nextAirAt = 123456789; movie.mediaType = "movie";
        Map<String, SeasonSchedule> schedules = new HashMap<>();
        for (String id : Arrays.asList("a", "b", "deleted", "local", "movie")) schedules.put(id, schedule(1, ep(1, 1, "2026-11-26")));
        CalendarProjection result = project(Arrays.asList(a, b, a, deleted, local, movie), schedules, TODAY);
        assertEquals(2, result.days.get(TODAY).size());
        assertEquals("b", result.days.get(TODAY).get(0).identityKey);
        assertFalse(result.days.get(TODAY).get(1).watched);
        assertEquals(2, result.localRecords.size()); assertTrue(result.undated.isEmpty());
    }

    @Test public void emptyUnavailableAndMismatchedSeasonsRemainDistinct() {
        Following a = item("a", 2), b = item("b", 2), c = item("c", 2);
        Map<String, SeasonSchedule> schedules = new HashMap<>();
        schedules.put("a", schedule(2)); schedules.put("b", new SeasonSchedule(2, Collections.emptyList(), Source.NONE, 0, "offline", 0, LocalReason.FAILED));
        schedules.put("c", schedule(1, ep(1, 1, "2026-11-26")));
        CalendarProjection result = project(Arrays.asList(a,b,c), schedules, TODAY);
        assertTrue(result.days.isEmpty()); assertEquals(3, result.localRecords.size());
        assertEquals(LocalReason.EMPTY, result.localRecords.get(0).reason);
        assertEquals(LocalReason.FAILED, result.localRecords.get(1).reason);
        assertEquals(LocalReason.INVALID_DATA, result.localRecords.get(2).reason);
        assertEquals(1, result.warnings.size());
    }

    @Test public void absentIdentityKeepsSeparateLocalRecordsWithoutInventedIdentity() {
        Following a=item("",0), b=item("",0); a.vodName="A"; b.vodName="B";
        CalendarProjection result=project(Arrays.asList(a,b),Collections.emptyMap(),TODAY);
        assertEquals(2,result.localRecords.size());
        assertEquals(LocalReason.NO_IDENTITY,result.localRecords.get(0).reason);
        assertEquals("",result.localRecords.get(0).identityKey);
        assertTrue(result.days.isEmpty());
    }

    @Test public void airDatesNeverFollowDefaultTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[]{"Asia/Shanghai", "America/Los_Angeles", "Pacific/Apia"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(TODAY, parseAirDate("2026-11-26"));
                assertEquals(LocalDate.of(2026, 3, 8), parseAirDate("2026-03-08"));
            }
        } finally { TimeZone.setDefault(original); }
    }

    static Following item(String id, int season) {
        Following value = new Following(); value.identityKey = id; value.vodName = "Title";
        value.mediaType = "tv"; value.tmdbId = 123; value.trackedSeason = season; return value;
    }
    static Episode ep(int season, int number, String date) { return new Episode(season, number, "E" + number, date); }
    static SeasonSchedule schedule(int season, Episode... episodes) {
        return new SeasonSchedule(season, Arrays.asList(episodes), Source.NETWORK, 100, "", 0, null);
    }
}
