package com.fongmi.android.tv.player.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LyricsLine {

    private final long timeMs;
    private final String text;
    private final List<LyricsWord> words;

    public LyricsLine(long timeMs, String text) {
        this(timeMs, text, Collections.emptyList());
    }

    public LyricsLine(long timeMs, String text, List<LyricsWord> words) {
        this.timeMs = Math.max(0, timeMs);
        this.text = text == null ? "" : text.trim();
        this.words = words == null || words.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(words));
    }

    public long getTimeMs() {
        return timeMs;
    }

    public String getText() {
        return text;
    }

    public List<LyricsWord> getWords() {
        return words;
    }

    public boolean hasWords() {
        return !words.isEmpty();
    }
}
