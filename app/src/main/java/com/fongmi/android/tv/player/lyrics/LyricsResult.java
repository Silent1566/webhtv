package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import java.util.Collections;
import java.util.List;

public class LyricsResult {

    private static final int CACHE_VERSION = 14;

    private int cacheVersion;
    private String source;
    private String trackName;
    private String artistName;
    private String albumName;
    private String lyrics;
    private long durationMs;
    private boolean synced;
    private int score;
    private transient Boolean wordTiming;

    public LyricsResult() {
    }

    public LyricsResult(String source, String trackName, String artistName, String albumName, String lyrics, long durationMs, boolean synced, int score) {
        this.cacheVersion = CACHE_VERSION;
        this.source = source;
        this.trackName = trackName;
        this.artistName = artistName;
        this.albumName = albumName;
        this.lyrics = lyrics;
        this.durationMs = durationMs;
        this.synced = synced;
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getLyrics() {
        return lyrics;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSynced() {
        return synced;
    }

    public int getScore() {
        return score;
    }

    public boolean isCacheCurrent() {
        return cacheVersion == CACHE_VERSION;
    }

    public boolean hasWordTiming() {
        if (wordTiming != null) return wordTiming;
        if (TextUtils.isEmpty(lyrics) || !lyrics.matches("(?s).*<\\d+,-?\\d+>.*")) return wordTiming = false;
        for (LyricsLine line : LyricsParser.parseTimed(lyrics)) {
            if (line.hasWords()) return wordTiming = true;
        }
        return wordTiming = false;
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(lyrics);
    }

    public List<LyricsLine> getLines(long fallbackDurationMs) {
        if (!isValid()) return Collections.emptyList();
        List<LyricsLine> lines = synced ? LyricsParser.parseTimed(lyrics) : LyricsParser.parsePlain(lyrics, fallbackDurationMs);
        return LyricsParser.filterMetadataLines(lines, trackName, artistName);
    }
}
