package com.fongmi.android.tv.player.karaoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class KaraokeTrack {

    private final String title;
    private final String artist;
    private final double bpm;
    private final long gapMs;
    private final List<KaraokeNote> notes;
    private final double weightedDurationMs;
    private final int scoredLineCount;
    private final boolean pitchRequired;

    public KaraokeTrack(String title, String artist, double bpm, long gapMs, List<KaraokeNote> notes) {
        this.title = title == null ? "" : title.trim();
        this.artist = artist == null ? "" : artist.trim();
        this.bpm = Math.max(0, bpm);
        this.gapMs = gapMs;
        this.notes = immutableSorted(notes);
        this.weightedDurationMs = calculateWeightedDurationMs(this.notes);
        this.scoredLineCount = calculateScoredLineCount(this.notes);
        this.pitchRequired = calculatePitchRequired(this.notes);
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public double getBpm() {
        return bpm;
    }

    public long getGapMs() {
        return gapMs;
    }

    public List<KaraokeNote> getNotes() {
        return notes;
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    public boolean hasScoredNotes() {
        return weightedDurationMs > 0;
    }

    public double getWeightedDurationMs() {
        return weightedDurationMs;
    }

    public long getDurationMs() {
        return notes.isEmpty() ? 0 : notes.get(notes.size() - 1).getEndMs();
    }

    public int getScoredLineCount() {
        return scoredLineCount;
    }

    public boolean hasPitchRequiredNotes() {
        return pitchRequired;
    }

    public KaraokeNote findNote(long positionMs) {
        if (notes.isEmpty()) return null;
        int low = 0;
        int high = notes.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            KaraokeNote note = notes.get(mid);
            if (positionMs < note.getStartMs()) {
                high = mid - 1;
            } else if (positionMs >= note.getEndMs()) {
                low = mid + 1;
            } else {
                return note;
            }
        }
        return null;
    }

    public KaraokeNote findScoredNote(long positionMs) {
        KaraokeNote note = findNote(positionMs);
        return note != null && note.isScored() ? note : null;
    }

    private static List<KaraokeNote> immutableSorted(List<KaraokeNote> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        ArrayList<KaraokeNote> output = new ArrayList<>(input);
        output.sort(Comparator.comparingLong(KaraokeNote::getStartMs).thenComparingLong(KaraokeNote::getEndMs));
        return Collections.unmodifiableList(output);
    }

    private static double calculateWeightedDurationMs(List<KaraokeNote> notes) {
        double total = 0;
        for (KaraokeNote note : notes) total += note.getWeightedDurationMs();
        return total;
    }

    private static int calculateScoredLineCount(List<KaraokeNote> notes) {
        int max = -1;
        for (KaraokeNote note : notes) if (note.isScored()) max = Math.max(max, note.getLineIndex());
        return max + 1;
    }

    private static boolean calculatePitchRequired(List<KaraokeNote> notes) {
        for (KaraokeNote note : notes) if (note.isScored() && note.isPitchRequired()) return true;
        return false;
    }
}
