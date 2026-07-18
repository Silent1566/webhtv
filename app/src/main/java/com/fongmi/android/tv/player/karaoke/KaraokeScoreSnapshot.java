package com.fongmi.android.tv.player.karaoke;

public class KaraokeScoreSnapshot {

    private final long positionMs;
    private final double totalWeightMs;
    private final double hitWeightMs;
    private final double voicedWeightMs;
    private final double perfectWeightMs;
    private final double vibratoWeightMs;
    private final double currentComboMs;
    private final double bestComboMs;
    private final KaraokeNote targetNote;
    private final double sungMidi;
    private final double distanceSemitones;
    private final boolean voiced;
    private final boolean hit;
    private final boolean perfect;
    private final boolean vibrato;
    private final int lineIndex;
    private final int lineCount;
    private final int scoredLineCount;
    private final int currentLineScorePercent;
    private final int bestLineScorePercent;
    private final int averageLineScorePercent;

    public KaraokeScoreSnapshot(double totalWeightMs, double hitWeightMs, KaraokeNote targetNote, double sungMidi, double distanceSemitones, boolean voiced, boolean hit) {
        this(totalWeightMs, hitWeightMs, voiced ? totalWeightMs : 0, 0, 0, targetNote, sungMidi, distanceSemitones, voiced, hit);
    }

    public KaraokeScoreSnapshot(double totalWeightMs, double hitWeightMs, double voicedWeightMs, double currentComboMs, double bestComboMs, KaraokeNote targetNote, double sungMidi, double distanceSemitones, boolean voiced, boolean hit) {
        this(0, totalWeightMs, hitWeightMs, voicedWeightMs, currentComboMs, bestComboMs, targetNote, sungMidi, distanceSemitones, voiced, hit);
    }

    public KaraokeScoreSnapshot(long positionMs, double totalWeightMs, double hitWeightMs, double voicedWeightMs, double currentComboMs, double bestComboMs, KaraokeNote targetNote, double sungMidi, double distanceSemitones, boolean voiced, boolean hit) {
        this(positionMs, totalWeightMs, hitWeightMs, voicedWeightMs, currentComboMs, bestComboMs, targetNote, sungMidi, distanceSemitones, voiced, hit, -1, 0, 0, 0, 0, 0);
    }

    public KaraokeScoreSnapshot(long positionMs, double totalWeightMs, double hitWeightMs, double voicedWeightMs, double currentComboMs, double bestComboMs, KaraokeNote targetNote, double sungMidi, double distanceSemitones, boolean voiced, boolean hit, int lineIndex, int lineCount, int scoredLineCount, int currentLineScorePercent, int bestLineScorePercent, int averageLineScorePercent) {
        this(positionMs, totalWeightMs, hitWeightMs, voicedWeightMs, 0, 0, currentComboMs, bestComboMs, targetNote, sungMidi, distanceSemitones, voiced, hit, false, false, lineIndex, lineCount, scoredLineCount, currentLineScorePercent, bestLineScorePercent, averageLineScorePercent);
    }

    public KaraokeScoreSnapshot(long positionMs, double totalWeightMs, double hitWeightMs, double voicedWeightMs, double perfectWeightMs, double vibratoWeightMs, double currentComboMs, double bestComboMs, KaraokeNote targetNote, double sungMidi, double distanceSemitones, boolean voiced, boolean hit, boolean perfect, boolean vibrato, int lineIndex, int lineCount, int scoredLineCount, int currentLineScorePercent, int bestLineScorePercent, int averageLineScorePercent) {
        this.positionMs = Math.max(0, positionMs);
        this.totalWeightMs = Math.max(0, totalWeightMs);
        this.hitWeightMs = Math.max(0, hitWeightMs);
        this.voicedWeightMs = Math.max(0, voicedWeightMs);
        this.perfectWeightMs = Math.max(0, perfectWeightMs);
        this.vibratoWeightMs = Math.max(0, vibratoWeightMs);
        this.currentComboMs = Math.max(0, currentComboMs);
        this.bestComboMs = Math.max(0, bestComboMs);
        this.targetNote = targetNote;
        this.sungMidi = sungMidi;
        this.distanceSemitones = distanceSemitones;
        this.voiced = voiced;
        this.hit = hit;
        this.perfect = perfect;
        this.vibrato = vibrato;
        this.lineIndex = lineIndex;
        this.lineCount = Math.max(0, lineCount);
        this.scoredLineCount = Math.max(0, scoredLineCount);
        this.currentLineScorePercent = clampPercent(currentLineScorePercent);
        this.bestLineScorePercent = clampPercent(bestLineScorePercent);
        this.averageLineScorePercent = clampPercent(averageLineScorePercent);
    }

    public long getPositionMs() {
        return positionMs;
    }

    public double getTotalWeightMs() {
        return totalWeightMs;
    }

    public double getHitWeightMs() {
        return hitWeightMs;
    }

    public double getVoicedWeightMs() {
        return voicedWeightMs;
    }

    public double getPerfectWeightMs() {
        return perfectWeightMs;
    }

    public int getPerfectPercent() {
        if (totalWeightMs <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, perfectWeightMs * 100.0 / totalWeightMs)));
    }

    public double getVibratoWeightMs() {
        return vibratoWeightMs;
    }

    public int getVibratoPercent() {
        if (totalWeightMs <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, vibratoWeightMs * 100.0 / totalWeightMs)));
    }

    public int getVoicedPercent() {
        if (totalWeightMs <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, voicedWeightMs * 100.0 / totalWeightMs)));
    }

    public int getCurrentComboSeconds() {
        return (int) Math.round(currentComboMs / 1000.0);
    }

    public int getBestComboSeconds() {
        return (int) Math.round(bestComboMs / 1000.0);
    }

    public KaraokeNote getTargetNote() {
        return targetNote;
    }

    public double getSungMidi() {
        return sungMidi;
    }

    public int getNearestSungMidi() {
        return Double.isNaN(sungMidi) ? -1 : (int) Math.round(sungMidi);
    }

    public double getDistanceSemitones() {
        return distanceSemitones;
    }

    public boolean isVoiced() {
        return voiced;
    }

    public boolean isHit() {
        return hit;
    }

    public boolean isPerfect() {
        return perfect;
    }

    public boolean hasVibrato() {
        return vibrato;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public int getLineCount() {
        return lineCount;
    }

    public int getScoredLineCount() {
        return scoredLineCount;
    }

    public int getCurrentLineScorePercent() {
        return currentLineScorePercent;
    }

    public int getBestLineScorePercent() {
        return bestLineScorePercent;
    }

    public int getAverageLineScorePercent() {
        return averageLineScorePercent;
    }

    public int getScorePercent() {
        if (totalWeightMs <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, hitWeightMs * 100.0 / totalWeightMs)));
    }

    public String getGrade() {
        return KaraokeGrade.fromScore(getScorePercent());
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
