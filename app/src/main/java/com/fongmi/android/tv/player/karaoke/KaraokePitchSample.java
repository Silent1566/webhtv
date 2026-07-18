package com.fongmi.android.tv.player.karaoke;

public class KaraokePitchSample {

    private final long timestampMs;
    private final double frequencyHz;
    private final double volume;
    private final double confidence;

    public KaraokePitchSample(long timestampMs, double frequencyHz, double volume, double confidence) {
        this.timestampMs = timestampMs;
        this.frequencyHz = frequencyHz;
        this.volume = Math.max(0, Math.min(1, volume));
        this.confidence = Math.max(0, Math.min(1, confidence));
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public double getFrequencyHz() {
        return frequencyHz;
    }

    public double getVolume() {
        return volume;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isVoiced() {
        return frequencyHz > 0 && confidence > 0;
    }
}
