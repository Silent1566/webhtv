package com.fongmi.android.tv.player.karaoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UltraStarParser {

    private static final double MIN_BPM = 1.0;

    private UltraStarParser() {
    }

    public static KaraokeTrack parse(String text) {
        Builder builder = new Builder();
        if (text == null || text.trim().isEmpty()) return builder.build();
        String[] lines = text.replace("\r", "").split("\n");
        for (String raw : lines) parseLine(builder, raw);
        return builder.build();
    }

    public static boolean looksLikeUltraStar(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("#BPM:") && upper.contains("#GAP:") && hasNoteLine(text);
    }

    private static boolean hasNoteLine(String text) {
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.length() > 2 && isNotePrefix(line.charAt(0))) return true;
        }
        return false;
    }

    private static void parseLine(Builder builder, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) return;
        if (line.charAt(0) == '#') {
            parseTag(builder, line);
            return;
        }
        if (line.charAt(0) == '-') {
            builder.nextLine();
            return;
        }
        if (line.charAt(0) == 'E') return;
        if (!isNotePrefix(line.charAt(0))) return;
        parseNote(builder, line);
    }

    private static void parseTag(Builder builder, String line) {
        int index = line.indexOf(':');
        if (index <= 1) return;
        String key = line.substring(1, index).trim().toUpperCase(Locale.ROOT);
        String value = line.substring(index + 1).trim();
        switch (key) {
            case "TITLE" -> builder.title = value;
            case "ARTIST" -> builder.artist = value;
            case "BPM" -> builder.bpm = parseDouble(value, builder.bpm);
            case "GAP" -> builder.gapMs = Math.round(parseDouble(value, builder.gapMs));
        }
    }

    private static void parseNote(Builder builder, String line) {
        char prefix = line.charAt(0);
        String body = line.substring(1).trim();
        if (body.isEmpty()) return;
        String[] parts = body.split("\\s+", 4);
        if (parts.length < 3) return;
        int startBeat = parseInt(parts[0], 0);
        int lengthBeat = parseInt(parts[1], 0);
        int pitch = parseInt(parts[2], 0);
        String lyric = parts.length >= 4 ? parts[3] : "";
        if (lengthBeat <= 0 || builder.bpm < MIN_BPM) return;
        long startMs = builder.beatToMs(startBeat);
        long endMs = builder.beatToMs(startBeat + lengthBeat);
        builder.notes.add(new KaraokeNote(startMs, endMs, startBeat, lengthBeat, pitch, lyric, typeOf(prefix), builder.lineIndex));
    }

    private static boolean isNotePrefix(char prefix) {
        return prefix == ':' || prefix == '*' || prefix == 'F' || prefix == 'R' || prefix == 'G';
    }

    private static KaraokeNoteType typeOf(char prefix) {
        return switch (prefix) {
            case '*' -> KaraokeNoteType.GOLDEN;
            case 'F' -> KaraokeNoteType.FREESTYLE;
            case 'R' -> KaraokeNoteType.RAP;
            case 'G' -> KaraokeNoteType.RAP_GOLDEN;
            default -> KaraokeNoteType.NORMAL;
        };
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static class Builder {

        private String title;
        private String artist;
        private double bpm;
        private long gapMs;
        private int lineIndex;
        private final List<KaraokeNote> notes = new ArrayList<>();

        private long beatToMs(int beat) {
            return Math.max(0, Math.round(gapMs + beat * 60_000.0 / bpm));
        }

        private KaraokeTrack build() {
            return new KaraokeTrack(title, artist, bpm, gapMs, notes);
        }

        private void nextLine() {
            if (!notes.isEmpty()) lineIndex++;
        }
    }
}
