package com.fongmi.android.tv.player.karaoke;

import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsParser;
import com.fongmi.android.tv.player.lyrics.LyricsWord;

import java.util.List;

public class KaraokeFreeSingScorer {

    private static final long WARMUP_MS = 3000;
    private static final long MAX_LINE_MS = 8000;
    private static final long WORD_MARGIN_MS = 250;
    private static final int RECENT_PITCH_SIZE = 18;

    private final KaraokeScoringConfig config;
    private long lastPositionMs = -1;
    private long warmupUntilMs = WARMUP_MS;
    private long totalWeightMs;
    private double hitWeightMs;
    private long voicedWeightMs;
    private long currentComboMs;
    private long bestComboMs;
    private int lineIndex = -1;
    private long lineActiveMs;
    private long lineVoicedMs;
    private long lineTotalWeightMs;
    private double lineHitWeightMs;
    private int completedLineCount;
    private int completedLineScoreSum;
    private int bestLineScorePercent;
    private double lastMidi = Double.NaN;
    private final double[] recentPitch = new double[RECENT_PITCH_SIZE];
    private int recentPitchSize;
    private KaraokeScoreSnapshot snapshot;

    public KaraokeFreeSingScorer() {
        this(KaraokeScoringConfig.DEFAULT);
    }

    public KaraokeFreeSingScorer(KaraokeScoringConfig config) {
        this.config = config == null ? KaraokeScoringConfig.DEFAULT : config;
        this.snapshot = empty();
    }

    public KaraokeScoreSnapshot update(long positionMs, KaraokePitchSample sample, List<LyricsLine> lines) {
        long adjustedPositionMs = Math.max(0, positionMs - config.getInputLatencyMs());
        Window window = findWindow(lines, adjustedPositionMs);
        Sample current = sample(sample);
        long sliceMs = nextSlice(adjustedPositionMs);
        if (sliceMs > 0 && adjustedPositionMs >= warmupUntilMs && window.active) score(sliceMs, window, current);
        lastPositionMs = adjustedPositionMs;
        snapshot = new KaraokeScoreSnapshot(adjustedPositionMs, totalWeightMs, hitWeightMs, voicedWeightMs, currentComboMs, bestComboMs, null, current.sungMidi, Double.NaN, current.voiced, current.voiced, lineIndex, lineCount(), scoredLineCount(), currentLineScore(), bestLineScore(), averageLineScore());
        return snapshot;
    }

    public KaraokeScoreSnapshot getSnapshot() {
        return snapshot;
    }

    public void reset() {
        lastPositionMs = -1;
        warmupUntilMs = WARMUP_MS;
        totalWeightMs = 0;
        hitWeightMs = 0;
        voicedWeightMs = 0;
        currentComboMs = 0;
        bestComboMs = 0;
        lineIndex = -1;
        lineActiveMs = 0;
        lineVoicedMs = 0;
        lineTotalWeightMs = 0;
        lineHitWeightMs = 0;
        completedLineCount = 0;
        completedLineScoreSum = 0;
        bestLineScorePercent = 0;
        lastMidi = Double.NaN;
        recentPitchSize = 0;
        snapshot = empty();
    }

    private void score(long sliceMs, Window window, Sample sample) {
        if (lineIndex != window.index) {
            finishLine();
            lineIndex = window.index;
            lineActiveMs = 0;
            lineVoicedMs = 0;
            lineTotalWeightMs = 0;
            lineHitWeightMs = 0;
            lastMidi = Double.NaN;
            recentPitchSize = 0;
        }
        lineActiveMs += sliceMs;
        if (sample.voiced) lineVoicedMs += sliceMs;
        if (sample.voiced) appendRecentPitch(sample.sungMidi);
        double score = freeScore(sample);
        totalWeightMs += sliceMs;
        hitWeightMs += sliceMs * score;
        lineTotalWeightMs += sliceMs;
        lineHitWeightMs += sliceMs * score;
        if (sample.voiced) {
            voicedWeightMs += sliceMs;
            currentComboMs += sliceMs;
            bestComboMs = Math.max(bestComboMs, currentComboMs);
        } else {
            currentComboMs = 0;
        }
        if (sample.voiced) lastMidi = sample.sungMidi;
    }

    private double freeScore(Sample sample) {
        if (!sample.voiced) return 0;
        double coverage = 1.0;
        double confidence = confidenceScore(sample.confidence);
        double volume = volumeScore(sample.volume);
        double stability = stabilityScore(sample.sungMidi);
        double snap = snapScore(sample.sungMidi, confidence);
        double musicality = musicalityScore(sample.sungMidi);
        double phrase = lineActiveMs <= 0 ? 0.5 : clamp01(lineVoicedMs / (double) lineActiveMs);
        return coverage * 0.18 + confidence * 0.20 + volume * 0.13 + stability * 0.16 + snap * 0.13 + musicality * 0.15 + phrase * 0.05;
    }

    private double confidenceScore(double confidence) {
        return clamp01((confidence - config.getMinConfidence()) / Math.max(0.1, 1.0 - config.getMinConfidence()));
    }

    private double volumeScore(double volume) {
        if (volume <= config.getMinVolume()) return 0;
        double score = volume / 0.16;
        if (volume > 0.86) score -= (volume - 0.86) * 1.5;
        return clamp01(score);
    }

    private double stabilityScore(double sungMidi) {
        if (Double.isNaN(lastMidi)) return 0.72;
        double diff = Math.abs(sungMidi - lastMidi);
        if (diff <= 0.75) return 1.0;
        if (diff <= 2.5) return 0.82;
        if (diff <= 5.0) return 0.55;
        return 0.28;
    }

    private double snapScore(double sungMidi, double confidenceScore) {
        double deviation = Math.abs(sungMidi - Math.round(sungMidi));
        double tolerance = Math.max(0.35, config.getToleranceSemitones() / 3.0);
        return clamp01(1.0 - deviation / tolerance) * confidenceScore;
    }

    private double musicalityScore(double sungMidi) {
        if (recentPitchSize < 5) return 0.42;
        double min = sungMidi;
        double max = sungMidi;
        for (int i = 0; i < recentPitchSize; i++) {
            min = Math.min(min, recentPitch[i]);
            max = Math.max(max, recentPitch[i]);
        }
        double range = max - min;
        double rangeScore;
        if (range < 0.5) rangeScore = 0.18;
        else if (range <= 6.0) rangeScore = clamp01(range / 6.0);
        else rangeScore = Math.max(0.28, 1.0 - (range - 6.0) / 10.0);
        double intervalScore = 0.55;
        if (!Double.isNaN(lastMidi)) {
            double interval = Math.abs(sungMidi - lastMidi);
            if (interval < 0.3) intervalScore = 0.62;
            else if (interval <= 5.0) intervalScore = 1.0;
            else if (interval <= 8.0) intervalScore = 0.42;
            else intervalScore = 0.12;
        }
        return rangeScore * 0.5 + intervalScore * 0.5;
    }

    private void appendRecentPitch(double sungMidi) {
        if (recentPitchSize == RECENT_PITCH_SIZE) {
            System.arraycopy(recentPitch, 1, recentPitch, 0, RECENT_PITCH_SIZE - 1);
            recentPitchSize--;
        }
        recentPitch[recentPitchSize] = sungMidi;
        recentPitchSize++;
    }

    private long nextSlice(long positionMs) {
        if (lastPositionMs < 0) {
            warmupUntilMs = positionMs + WARMUP_MS;
            return 0;
        }
        long delta = positionMs - lastPositionMs;
        if (delta < -1_000 || delta > 2_000) {
            warmupUntilMs = positionMs + WARMUP_MS;
            lineIndex = -1;
            lineActiveMs = 0;
            lineVoicedMs = 0;
            lineTotalWeightMs = 0;
            lineHitWeightMs = 0;
            lastMidi = Double.NaN;
            recentPitchSize = 0;
            currentComboMs = 0;
            return 0;
        }
        if (delta <= 0) return 0;
        return Math.min(delta, config.getMaxSliceMs());
    }

    private Sample sample(KaraokePitchSample sample) {
        boolean voiced = sample != null
                && sample.getFrequencyHz() > 0
                && sample.getVolume() >= config.getMinVolume()
                && sample.getConfidence() >= config.getMinConfidence();
        double sungMidi = voiced ? KaraokePitch.frequencyToMidi(sample.getFrequencyHz()) : Double.NaN;
        double volume = sample == null ? 0 : sample.getVolume();
        double confidence = sample == null ? 0 : sample.getConfidence();
        return new Sample(voiced, sungMidi, volume, confidence);
    }

    private Window findWindow(List<LyricsLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return Window.INACTIVE;
        int index = LyricsParser.findLine(lines, positionMs);
        if (index < 0 || index >= lines.size()) return Window.INACTIVE;
        LyricsLine line = lines.get(index);
        long lineStart = line.getTimeMs();
        long nextStart = index + 1 < lines.size() ? lines.get(index + 1).getTimeMs() : lineStart + MAX_LINE_MS;
        long start = lineStart;
        long end = nextStart;
        if (line.hasWords()) {
            List<LyricsWord> words = line.getWords();
            start = lineStart + Math.max(0, words.get(0).getStartOffsetMs() - WORD_MARGIN_MS);
            long wordEnd = 0;
            for (LyricsWord word : words) wordEnd = Math.max(wordEnd, word.getEndOffsetMs());
            end = lineStart + wordEnd + WORD_MARGIN_MS;
            if (nextStart > lineStart) end = Math.min(end, nextStart);
        } else {
            end = Math.min(end, lineStart + MAX_LINE_MS);
        }
        boolean active = positionMs >= start && positionMs < end && end > start + 300;
        return new Window(index, active);
    }

    private KaraokeScoreSnapshot empty() {
        return new KaraokeScoreSnapshot(0, 0, null, Double.NaN, Double.NaN, false, false);
    }

    private void finishLine() {
        if (lineTotalWeightMs <= 0) return;
        int score = currentLineScore();
        completedLineCount++;
        completedLineScoreSum += score;
        bestLineScorePercent = Math.max(bestLineScorePercent, score);
    }

    private int lineCount() {
        return scoredLineCount();
    }

    private int scoredLineCount() {
        return completedLineCount + (lineTotalWeightMs > 0 ? 1 : 0);
    }

    private int currentLineScore() {
        if (lineTotalWeightMs <= 0) return 0;
        return percent(lineHitWeightMs, lineTotalWeightMs);
    }

    private int bestLineScore() {
        return Math.max(bestLineScorePercent, currentLineScore());
    }

    private int averageLineScore() {
        int count = scoredLineCount();
        if (count <= 0) return 0;
        return Math.round((completedLineScoreSum + (lineTotalWeightMs > 0 ? currentLineScore() : 0)) / (float) count);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int percent(double hit, double total) {
        if (total <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, hit * 100.0 / total)));
    }

    private static class Sample {

        private final boolean voiced;
        private final double sungMidi;
        private final double volume;
        private final double confidence;

        private Sample(boolean voiced, double sungMidi, double volume, double confidence) {
            this.voiced = voiced;
            this.sungMidi = sungMidi;
            this.volume = volume;
            this.confidence = confidence;
        }
    }

    private static class Window {

        private static final Window INACTIVE = new Window(-1, false);

        private final int index;
        private final boolean active;

        private Window(int index, boolean active) {
            this.index = index;
            this.active = active;
        }
    }
}
