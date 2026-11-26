package com.fongmi.android.tv.following;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public final class FollowingCalendarPolicy {
    private FollowingCalendarPolicy() { }

    public enum Source { NETWORK, FRESH_CACHE, STALE_CACHE, NONE }
    public enum TemporalState { PAST, TODAY, FUTURE, UNKNOWN }
    public enum LocalReason { NO_IDENTITY, UNSUPPORTED, NO_CONFIG, LOADING, FAILED, EMPTY, INVALID_DATA, RATE_LIMITED }
    public enum WarningKind { SEASON_MISMATCH, INVALID_EPISODE, CONFLICTING_DATE }

    public static final class Episode {
        public final int seasonNumber, episodeNumber;
        public final String name, airDate;
        public Episode(int seasonNumber, int episodeNumber, String name, String airDate) {
            this.seasonNumber = seasonNumber; this.episodeNumber = episodeNumber;
            this.name = text(name); this.airDate = airDate;
        }
    }

    public static final class SeasonSchedule {
        public final int seasonNumber;
        public final List<Episode> episodes;
        public final Source source;
        public final long cachedAtMillis, retryAtMillis;
        public final String sanitizedError;
        public final LocalReason unavailableReason;
        public SeasonSchedule(int seasonNumber, List<Episode> episodes, Source source, long cachedAtMillis,
                              String sanitizedError, long retryAtMillis, LocalReason unavailableReason) {
            this.seasonNumber = seasonNumber; this.episodes = immutable(episodes); this.source = source;
            this.cachedAtMillis = cachedAtMillis; this.sanitizedError = text(sanitizedError);
            this.retryAtMillis = retryAtMillis; this.unavailableReason = unavailableReason;
        }
    }

    public static final class Entry {
        public final String identityKey, title, poster, name;
        public final int seasonNumber, episodeNumber;
        public final LocalDate date;
        public final Source source;
        public final TemporalState temporalState;
        public final boolean watched;
        public final long cachedAtMillis;
        public final String sanitizedError;
        private Entry(Following item, Episode episode, LocalDate date, SeasonSchedule schedule, LocalDate today) {
            identityKey = text(item.identityKey); title = text(item.vodName); poster = text(item.vodPic);
            name = episode.name; seasonNumber = episode.seasonNumber; episodeNumber = episode.episodeNumber;
            this.date = date; source = schedule.source; cachedAtMillis = schedule.cachedAtMillis;
            sanitizedError = schedule.sanitizedError;
            watched = item.watchedSeason == seasonNumber && episodeNumber <= item.watchedEpisode;
            temporalState = date == null ? TemporalState.UNKNOWN : date.isBefore(today) ? TemporalState.PAST
                    : date.isAfter(today) ? TemporalState.FUTURE : TemporalState.TODAY;
        }
    }

    public static final class LocalRecord {
        public final String identityKey, title, poster, sanitizedError;
        public final int seasonNumber, watchedSeason, watchedEpisode, releasedEpisodes, totalEpisodes;
        public final LocalReason reason;
        private LocalRecord(Following item, LocalReason reason, String error) {
            identityKey = text(item.identityKey); title = text(item.vodName); poster = text(item.vodPic);
            seasonNumber = item.trackedSeason; watchedSeason = item.watchedSeason; watchedEpisode = item.watchedEpisode;
            releasedEpisodes = item.seasonReleasedEpisodes; totalEpisodes = item.seasonTotalEpisodes;
            this.reason = reason; sanitizedError = text(error);
        }
    }

    public static final class Warning {
        public final String identityKey;
        public final int seasonNumber, episodeNumber;
        public final WarningKind kind;
        private Warning(Following item, int episode, WarningKind kind) {
            identityKey = text(item.identityKey); seasonNumber = item.trackedSeason; episodeNumber = episode; this.kind = kind;
        }
    }

    public static final class CalendarProjection {
        public final NavigableMap<LocalDate, List<Entry>> days;
        public final List<Entry> undated;
        public final List<LocalRecord> localRecords;
        public final List<Warning> warnings;
        private CalendarProjection(NavigableMap<LocalDate, List<Entry>> days, List<Entry> undated,
                                   List<LocalRecord> localRecords, List<Warning> warnings) {
            NavigableMap<LocalDate, List<Entry>> copy = new TreeMap<>();
            for (Map.Entry<LocalDate, List<Entry>> day : days.entrySet()) copy.put(day.getKey(), immutable(day.getValue()));
            this.days = Collections.unmodifiableNavigableMap(copy); this.undated = immutable(undated);
            this.localRecords = immutable(localRecords); this.warnings = immutable(warnings);
        }
    }

    public static LocalDate parseAirDate(String isoDate) {
        if (isoDate == null || !isoDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try { return LocalDate.parse(isoDate); } catch (DateTimeParseException e) { return null; }
    }

    public static LocalDate weekStart(LocalDate selected) {
        return selected.minusDays(selected.getDayOfWeek().getValue() - 1);
    }

    public static CalendarProjection project(List<Following> items, Map<String, SeasonSchedule> schedules, LocalDate today) {
        NavigableMap<LocalDate, List<Entry>> days = new TreeMap<>();
        List<Entry> undated = new ArrayList<>();
        List<LocalRecord> localRecords = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (Following item : items) {
            if (item == null || item.deletedAt > 0) continue;
            if (!text(item.identityKey).isEmpty() && !identities.add(item.identityKey + ":s" + item.trackedSeason)) continue;
            LocalReason reason = localReason(item);
            SeasonSchedule schedule = schedules.get(item.identityKey);
            if (reason == null && schedule == null) reason = LocalReason.LOADING;
            if (reason == null && schedule.seasonNumber != item.trackedSeason) {
                reason = LocalReason.INVALID_DATA; warnings.add(new Warning(item, 0, WarningKind.SEASON_MISMATCH));
            }
            if (reason == null) reason = schedule.unavailableReason;
            if (reason != null) {
                localRecords.add(new LocalRecord(item, reason, schedule == null ? "" : schedule.sanitizedError)); continue;
            }
            Map<Integer, Entry> episodes = new LinkedHashMap<>();
            for (Episode episode : schedule.episodes) {
                if (episode.seasonNumber != item.trackedSeason) { warnings.add(new Warning(item, episode.episodeNumber, WarningKind.SEASON_MISMATCH)); continue; }
                if (episode.episodeNumber <= 0) { warnings.add(new Warning(item, episode.episodeNumber, WarningKind.INVALID_EPISODE)); continue; }
                LocalDate date = parseAirDate(episode.airDate);
                Entry previous = episodes.get(episode.episodeNumber);
                if (previous != null) {
                    if (previous.date != null && date != null && !previous.date.equals(date)) warnings.add(new Warning(item, episode.episodeNumber, WarningKind.CONFLICTING_DATE));
                    if (previous.date != null || date == null) continue;
                }
                episodes.put(episode.episodeNumber, new Entry(item, episode, date, schedule, today));
            }
            if (episodes.isEmpty()) localRecords.add(new LocalRecord(item, schedule.episodes.isEmpty() ? LocalReason.EMPTY : LocalReason.INVALID_DATA, schedule.sanitizedError));
            for (Entry entry : episodes.values()) {
                if (entry.date == null) undated.add(entry);
                else days.computeIfAbsent(entry.date, key -> new ArrayList<>()).add(entry);
            }
        }
        Comparator<Entry> order = Comparator.comparing((Entry e) -> e.title).thenComparing(e -> e.identityKey)
                .thenComparingInt(e -> e.seasonNumber).thenComparingInt(e -> e.episodeNumber);
        for (List<Entry> entries : days.values()) entries.sort(order);
        undated.sort(order);
        localRecords.sort(Comparator.comparing((LocalRecord r) -> r.title).thenComparing(r -> r.identityKey).thenComparingInt(r -> r.seasonNumber));
        return new CalendarProjection(days, undated, localRecords, warnings);
    }

    static LocalReason localReason(Following item) {
        if (text(item.identityKey).isEmpty() || item.tmdbId <= 0) return LocalReason.NO_IDENTITY;
        if (!"tv".equalsIgnoreCase(text(item.mediaType).trim()) || item.trackedSeason < 0) return LocalReason.UNSUPPORTED;
        return null;
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<>(values)); }
}
