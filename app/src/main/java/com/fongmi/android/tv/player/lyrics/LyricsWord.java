package com.fongmi.android.tv.player.lyrics;

import java.util.Objects;

public class LyricsWord {

    private final long startOffsetMs;
    private final long durationMs;
    private final String text;

    public LyricsWord(long startOffsetMs, long durationMs, String text) {
        this.startOffsetMs = Math.max(0, startOffsetMs);
        this.durationMs = Math.max(0, durationMs);
        this.text = text == null ? "" : text;
    }

    public long getStartOffsetMs() {
        return startOffsetMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getEndOffsetMs() {
        return startOffsetMs + durationMs;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof LyricsWord)) return false;
        LyricsWord word = (LyricsWord) object;
        return startOffsetMs == word.startOffsetMs && durationMs == word.durationMs && Objects.equals(text, word.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startOffsetMs, durationMs, text);
    }
}
