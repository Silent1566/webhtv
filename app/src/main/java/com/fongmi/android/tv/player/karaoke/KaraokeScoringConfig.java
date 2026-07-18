package com.fongmi.android.tv.player.karaoke;

public class KaraokeScoringConfig {

    public static final KaraokeScoringConfig DEFAULT = new Builder().build();

    private final double toleranceSemitones;
    private final boolean ignoreOctave;
    private final double minVolume;
    private final double minConfidence;
    private final long inputLatencyMs;
    private final long maxSliceMs;

    private KaraokeScoringConfig(Builder builder) {
        this.toleranceSemitones = Math.max(0, builder.toleranceSemitones);
        this.ignoreOctave = builder.ignoreOctave;
        this.minVolume = clamp01(builder.minVolume);
        this.minConfidence = clamp01(builder.minConfidence);
        this.inputLatencyMs = builder.inputLatencyMs;
        this.maxSliceMs = Math.max(20, builder.maxSliceMs);
    }

    public double getToleranceSemitones() {
        return toleranceSemitones;
    }

    public boolean isIgnoreOctave() {
        return ignoreOctave;
    }

    public double getMinVolume() {
        return minVolume;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public long getInputLatencyMs() {
        return inputLatencyMs;
    }

    public long getMaxSliceMs() {
        return maxSliceMs;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public static class Builder {

        private double toleranceSemitones = 2.0;
        private boolean ignoreOctave = true;
        private double minVolume = 0.05;
        private double minConfidence = 0.1;
        private long inputLatencyMs;
        private long maxSliceMs = 250;

        public Builder toleranceSemitones(double toleranceSemitones) {
            this.toleranceSemitones = toleranceSemitones;
            return this;
        }

        public Builder ignoreOctave(boolean ignoreOctave) {
            this.ignoreOctave = ignoreOctave;
            return this;
        }

        public Builder minVolume(double minVolume) {
            this.minVolume = minVolume;
            return this;
        }

        public Builder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }

        public Builder inputLatencyMs(long inputLatencyMs) {
            this.inputLatencyMs = inputLatencyMs;
            return this;
        }

        public Builder maxSliceMs(long maxSliceMs) {
            this.maxSliceMs = maxSliceMs;
            return this;
        }

        public KaraokeScoringConfig build() {
            return new KaraokeScoringConfig(this);
        }
    }
}
