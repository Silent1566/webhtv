package com.fongmi.android.tv.player.karaoke;

import android.text.TextUtils;

import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsWord;

import java.util.ArrayList;
import java.util.List;

public class KaraokeGeneratedTrackBuilder {

    private static final double BPM = 6000.0;
    private static final long BEAT_MS = 10;
    private static final int PITCH = 60;
    private static final int MAX_NOTES = 900;
    private static final long MIN_NOTE_MS = 80;
    private static final long DEFAULT_LAST_LINE_MS = 3000;

    private KaraokeGeneratedTrackBuilder() {
    }

    public static boolean canGenerate(List<LyricsLine> lines) {
        if (lines == null || lines.isEmpty()) return false;
        int usable = 0;
        for (LyricsLine line : lines) {
            if (line != null && !TextUtils.isEmpty(line.getText())) usable++;
            if (usable >= 2) return true;
        }
        return false;
    }

    public static String build(String title, String artist, List<LyricsLine> lines, long durationMs) {
        if (!canGenerate(lines)) throw new IllegalStateException("no timed lyrics");
        StringBuilder builder = new StringBuilder();
        builder.append("#TITLE:").append(tag(title, "Generated track")).append('\n');
        builder.append("#ARTIST:").append(tag(artist, "Unknown")).append('\n');
        builder.append("#BPM:").append(BPM).append('\n');
        builder.append("#GAP:0").append('\n');
        builder.append("#COMMENT:Generated rhythm scoring track from lyric timing; pitch is not required").append('\n');
        int count = appendNotes(builder, lines, durationMs);
        if (count < 3) throw new IllegalStateException("not enough lyric timing");
        builder.append('E').append('\n');
        return builder.toString();
    }

    private static int appendNotes(StringBuilder builder, List<LyricsLine> lines, long durationMs) {
        int count = 0;
        for (int i = 0; i < lines.size() && count < MAX_NOTES; i++) {
            LyricsLine line = lines.get(i);
            if (line == null || TextUtils.isEmpty(line.getText())) continue;
            long startMs = line.getTimeMs();
            long endMs = lineEnd(lines, i, durationMs);
            int before = count;
            if (line.hasWords()) {
                count = appendWordNotes(builder, line, startMs, endMs, count);
            } else {
                count = appendLineNotes(builder, line.getText(), startMs, endMs, count);
            }
            if (count > before) builder.append("-\n");
        }
        return count;
    }

    private static int appendWordNotes(StringBuilder builder, LyricsLine line, long lineStartMs, long lineEndMs, int count) {
        List<LyricsWord> words = line.getWords();
        for (int i = 0; i < words.size() && count < MAX_NOTES; i++) {
            LyricsWord word = words.get(i);
            if (word == null || TextUtils.isEmpty(word.getText())) continue;
            long startMs = lineStartMs + word.getStartOffsetMs();
            long endMs = word.getDurationMs() > 0 ? startMs + word.getDurationMs() : nextWordStart(lineStartMs, lineEndMs, words, i);
            if (appendNote(builder, startMs, endMs, word.getText())) count++;
        }
        return count;
    }

    private static int appendLineNotes(StringBuilder builder, String text, long startMs, long endMs, int count) {
        List<String> units = splitUnits(text);
        if (units.isEmpty()) return count;
        long durationMs = Math.max(MIN_NOTE_MS * units.size(), endMs - startMs);
        long unitMs = Math.max(MIN_NOTE_MS, durationMs / units.size());
        for (int i = 0; i < units.size() && count < MAX_NOTES; i++) {
            long unitStart = startMs + unitMs * i;
            long unitEnd = i == units.size() - 1 ? startMs + durationMs : unitStart + unitMs;
            if (appendNote(builder, unitStart, unitEnd, units.get(i))) count++;
        }
        return count;
    }

    private static boolean appendNote(StringBuilder builder, long startMs, long endMs, String lyric) {
        String text = lyric(lyric);
        if (TextUtils.isEmpty(text)) return false;
        long safeStart = Math.max(0, startMs);
        long safeEnd = Math.max(safeStart + MIN_NOTE_MS, endMs);
        int startBeat = (int) Math.max(0, Math.round(safeStart / (double) BEAT_MS));
        int lengthBeat = (int) Math.max(1, Math.round((safeEnd - safeStart) / (double) BEAT_MS));
        builder.append('R').append(' ')
                .append(startBeat).append(' ')
                .append(lengthBeat).append(' ')
                .append(PITCH).append(' ')
                .append(text).append('\n');
        return true;
    }

    private static long lineEnd(List<LyricsLine> lines, int index, long durationMs) {
        long startMs = lines.get(index).getTimeMs();
        long nextMs = index + 1 < lines.size() ? lines.get(index + 1).getTimeMs() : 0;
        long fallback = startMs + DEFAULT_LAST_LINE_MS;
        if (durationMs > startMs + MIN_NOTE_MS) fallback = Math.min(durationMs, fallback);
        if (nextMs <= startMs) return fallback;
        return Math.max(startMs + MIN_NOTE_MS, nextMs);
    }

    private static long nextWordStart(long lineStartMs, long lineEndMs, List<LyricsWord> words, int index) {
        if (index + 1 < words.size()) return lineStartMs + words.get(index + 1).getStartOffsetMs();
        return lineEndMs;
    }

    private static List<String> splitUnits(String text) {
        String clean = lyric(text);
        List<String> units = new ArrayList<>();
        if (TextUtils.isEmpty(clean)) return units;
        if (clean.matches(".*\\s+.*")) {
            for (String unit : clean.split("\\s+")) if (!TextUtils.isEmpty(unit)) units.add(unit);
        } else if (clean.codePointCount(0, clean.length()) <= 24) {
            for (int i = 0; i < clean.length(); ) {
                int codePoint = clean.codePointAt(i);
                units.add(new String(Character.toChars(codePoint)));
                i += Character.charCount(codePoint);
            }
        } else {
            units.add(clean);
        }
        return units;
    }

    private static String tag(String value, String fallback) {
        String text = lyric(value);
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    private static String lyric(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
