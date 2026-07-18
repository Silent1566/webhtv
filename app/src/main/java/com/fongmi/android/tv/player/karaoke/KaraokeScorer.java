package com.fongmi.android.tv.player.karaoke;

public class KaraokeScorer {

    private static final double PERFECT_TOLERANCE_SEMITONES = 0.38;
    private static final long WARMUP_MS = 1200;
    private static final int PITCH_HISTORY_SIZE = 24;
    private static final long PITCH_HISTORY_MAX_GAP_MS = 260;
    private static final long VIBRATO_WINDOW_MS = 1300;
    private static final double VIBRATO_MIN_DELTA = 0.08;
    private static final double VIBRATO_MIN_RANGE = 0.35;
    private static final double VIBRATO_MAX_RANGE = 2.4;

    private final KaraokeTrack track;
    private final KaraokeScoringConfig config;
    private long lastPositionMs = -1;
    private long warmupUntilMs = WARMUP_MS;
    private double totalWeightMs;
    private double hitWeightMs;
    private double voicedWeightMs;
    private double perfectWeightMs;
    private double vibratoWeightMs;
    private long currentComboMs;
    private long bestComboMs;
    private final double[] lineTotalWeightMs;
    private final double[] lineHitWeightMs;
    private final long[] pitchHistoryTime = new long[PITCH_HISTORY_SIZE];
    private final double[] pitchHistoryMidi = new double[PITCH_HISTORY_SIZE];
    private int pitchHistorySize;
    private long pitchHistoryNoteStart = -1;
    private KaraokeScoreSnapshot snapshot;

    public KaraokeScorer(KaraokeTrack track) {
        this(track, KaraokeScoringConfig.DEFAULT);
    }

    public KaraokeScorer(KaraokeTrack track, KaraokeScoringConfig config) {
        this.track = track == null ? new KaraokeTrack("", "", 0, 0, null) : track;
        this.config = config == null ? KaraokeScoringConfig.DEFAULT : config;
        int lineCount = Math.max(1, this.track.getScoredLineCount());
        this.lineTotalWeightMs = new double[lineCount];
        this.lineHitWeightMs = new double[lineCount];
        this.snapshot = new KaraokeScoreSnapshot(0, 0, null, Double.NaN, Double.NaN, false, false);
    }

    public KaraokeScoreSnapshot update(long positionMs, double frequencyHz, double volume, double confidence) {
        long adjustedPositionMs = Math.max(0, positionMs - config.getInputLatencyMs());
        Sample sample = sample(adjustedPositionMs, frequencyHz, volume, confidence, true);
        long sliceMs = nextSlice(adjustedPositionMs);
        if (sliceMs > 0 && adjustedPositionMs >= warmupUntilMs && sample.note != null && sample.note.isScored()) {
            double weight = sliceMs * sample.note.getType().getScoreWeight();
            totalWeightMs += weight;
            if (sample.voiced) voicedWeightMs += weight;
            if (sample.hit) hitWeightMs += weight;
            if (sample.perfect) perfectWeightMs += weight;
            if (sample.vibrato) vibratoWeightMs += weight;
            scoreLine(sample.note, weight, sample.hit);
            updateCombo(sliceMs, sample.hit);
        }
        lastPositionMs = adjustedPositionMs;
        return snapshot = snapshot(adjustedPositionMs, sample);
    }

    public KaraokeScoreSnapshot evaluate(long positionMs, double frequencyHz, double volume, double confidence) {
        long adjustedPositionMs = Math.max(0, positionMs - config.getInputLatencyMs());
        Sample sample = sample(adjustedPositionMs, frequencyHz, volume, confidence, false);
        return snapshot(adjustedPositionMs, sample);
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
        perfectWeightMs = 0;
        vibratoWeightMs = 0;
        currentComboMs = 0;
        bestComboMs = 0;
        pitchHistorySize = 0;
        pitchHistoryNoteStart = -1;
        for (int i = 0; i < lineTotalWeightMs.length; i++) {
            lineTotalWeightMs[i] = 0;
            lineHitWeightMs[i] = 0;
        }
        snapshot = new KaraokeScoreSnapshot(0, 0, null, Double.NaN, Double.NaN, false, false);
    }

    private void scoreLine(KaraokeNote note, double weight, boolean hit) {
        int index = lineIndex(note);
        lineTotalWeightMs[index] += weight;
        if (hit) lineHitWeightMs[index] += weight;
    }

    private void updateCombo(long sliceMs, boolean hit) {
        if (hit) {
            currentComboMs += sliceMs;
            bestComboMs = Math.max(bestComboMs, currentComboMs);
        } else {
            currentComboMs = 0;
        }
    }

    private KaraokeScoreSnapshot snapshot(long positionMs, Sample sample) {
        return new KaraokeScoreSnapshot(positionMs, totalWeightMs, hitWeightMs, voicedWeightMs, perfectWeightMs, vibratoWeightMs, currentComboMs, bestComboMs, sample.note, sample.sungMidi, sample.distanceSemitones, sample.voiced, sample.hit, sample.perfect, sample.vibrato, lineIndex(sample.note), lineTotalWeightMs.length, scoredLineCount(), currentLineScore(sample.note), bestLineScore(), averageLineScore());
    }

    private long nextSlice(long positionMs) {
        if (lastPositionMs < 0) {
            warmupUntilMs = positionMs + WARMUP_MS;
            return 0;
        }
        long delta = positionMs - lastPositionMs;
        if (delta < -1_000 || delta > 2_000) {
            warmupUntilMs = positionMs + WARMUP_MS;
            currentComboMs = 0;
            clearPitchHistory();
            return 0;
        }
        if (delta <= 0) {
            currentComboMs = 0;
            return 0;
        }
        return Math.min(delta, config.getMaxSliceMs());
    }

    private Sample sample(long positionMs, double frequencyHz, double volume, double confidence, boolean recordHistory) {
        KaraokeNote note = track.findScoredNote(positionMs);
        boolean voiced = frequencyHz > 0 && volume >= config.getMinVolume() && confidence >= config.getMinConfidence();
        double sungMidi = voiced ? KaraokePitch.frequencyToMidi(frequencyHz) : Double.NaN;
        double distance = note != null && voiced ? KaraokePitch.semitoneDistance(sungMidi, note.getPitch(), config.isIgnoreOctave()) : Double.NaN;
        boolean hit = note != null && voiced && (!note.isPitchRequired() || Math.abs(distance) <= config.getToleranceSemitones());
        boolean perfect = hit && note != null && note.isPitchRequired() && Math.abs(distance) <= PERFECT_TOLERANCE_SEMITONES;
        boolean vibrato = hit && note != null && note.isPitchRequired() && recordHistory && updatePitchHistory(positionMs, note, sungMidi);
        return new Sample(note, sungMidi, distance, voiced, hit, perfect, vibrato);
    }

    private boolean updatePitchHistory(long positionMs, KaraokeNote note, double sungMidi) {
        if (note == null || Double.isNaN(sungMidi)) {
            clearPitchHistory();
            return false;
        }
        if (pitchHistoryNoteStart != note.getStartMs()
                || (pitchHistorySize > 0 && positionMs - pitchHistoryTime[pitchHistorySize - 1] > PITCH_HISTORY_MAX_GAP_MS)) {
            clearPitchHistory();
            pitchHistoryNoteStart = note.getStartMs();
        }
        appendPitchHistory(positionMs, sungMidi);
        return detectVibrato();
    }

    private void appendPitchHistory(long positionMs, double sungMidi) {
        if (pitchHistorySize == PITCH_HISTORY_SIZE) {
            System.arraycopy(pitchHistoryTime, 1, pitchHistoryTime, 0, PITCH_HISTORY_SIZE - 1);
            System.arraycopy(pitchHistoryMidi, 1, pitchHistoryMidi, 0, PITCH_HISTORY_SIZE - 1);
            pitchHistorySize--;
        }
        pitchHistoryTime[pitchHistorySize] = positionMs;
        pitchHistoryMidi[pitchHistorySize] = sungMidi;
        pitchHistorySize++;
    }

    private boolean detectVibrato() {
        if (pitchHistorySize < 7) return false;
        long startMs = pitchHistoryTime[pitchHistorySize - 1] - VIBRATO_WINDOW_MS;
        int first = 0;
        while (first < pitchHistorySize && pitchHistoryTime[first] < startMs) first++;
        if (pitchHistorySize - first < 7) return false;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int changes = 0;
        int lastDirection = 0;
        long lastChangeMs = 0;
        long minInterval = Long.MAX_VALUE;
        long maxInterval = 0;
        long intervalSum = 0;
        int intervalCount = 0;
        for (int i = first; i < pitchHistorySize; i++) {
            min = Math.min(min, pitchHistoryMidi[i]);
            max = Math.max(max, pitchHistoryMidi[i]);
            if (i == first) continue;
            double delta = pitchHistoryMidi[i] - pitchHistoryMidi[i - 1];
            if (Math.abs(delta) < VIBRATO_MIN_DELTA) continue;
            int direction = delta > 0 ? 1 : -1;
            if (lastDirection != 0 && direction != lastDirection) {
                changes++;
                if (lastChangeMs > 0) {
                    long interval = pitchHistoryTime[i] - lastChangeMs;
                    minInterval = Math.min(minInterval, interval);
                    maxInterval = Math.max(maxInterval, interval);
                    intervalSum += interval;
                    intervalCount++;
                }
                lastChangeMs = pitchHistoryTime[i];
            } else if (lastDirection == 0) {
                lastChangeMs = pitchHistoryTime[i];
            }
            lastDirection = direction;
        }
        double range = max - min;
        if (changes < 4 || range < VIBRATO_MIN_RANGE || range > VIBRATO_MAX_RANGE || intervalCount < 2) return false;
        double average = intervalSum / (double) intervalCount;
        return maxInterval < average * 1.9 && minInterval > average / 1.9;
    }

    private void clearPitchHistory() {
        pitchHistorySize = 0;
        pitchHistoryNoteStart = -1;
    }

    private int lineIndex(KaraokeNote note) {
        if (note == null) return -1;
        return Math.max(0, Math.min(lineTotalWeightMs.length - 1, note.getLineIndex()));
    }

    private int currentLineScore(KaraokeNote note) {
        int index = lineIndex(note);
        if (index < 0 || lineTotalWeightMs[index] <= 0) return 0;
        return percent(lineHitWeightMs[index], lineTotalWeightMs[index]);
    }

    private int bestLineScore() {
        int best = 0;
        for (int i = 0; i < lineTotalWeightMs.length; i++) if (lineTotalWeightMs[i] > 0) best = Math.max(best, percent(lineHitWeightMs[i], lineTotalWeightMs[i]));
        return best;
    }

    private int averageLineScore() {
        int count = 0;
        int total = 0;
        for (int i = 0; i < lineTotalWeightMs.length; i++) {
            if (lineTotalWeightMs[i] <= 0) continue;
            total += percent(lineHitWeightMs[i], lineTotalWeightMs[i]);
            count++;
        }
        return count == 0 ? 0 : Math.round(total / (float) count);
    }

    private int scoredLineCount() {
        int count = 0;
        for (double weight : lineTotalWeightMs) if (weight > 0) count++;
        return count;
    }

    private static int percent(double hit, double total) {
        if (total <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(100, hit * 100.0 / total)));
    }

    private static class Sample {

        private final KaraokeNote note;
        private final double sungMidi;
        private final double distanceSemitones;
        private final boolean voiced;
        private final boolean hit;
        private final boolean perfect;
        private final boolean vibrato;

        private Sample(KaraokeNote note, double sungMidi, double distanceSemitones, boolean voiced, boolean hit, boolean perfect, boolean vibrato) {
            this.note = note;
            this.sungMidi = sungMidi;
            this.distanceSemitones = distanceSemitones;
            this.voiced = voiced;
            this.hit = hit;
            this.perfect = perfect;
            this.vibrato = vibrato;
        }
    }
}
