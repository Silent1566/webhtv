package com.fongmi.android.tv.player.karaoke;

public enum KaraokeNoteType {

    NORMAL(true, true, 1.0),
    GOLDEN(true, true, 2.0),
    FREESTYLE(false, false, 0.0),
    RAP(true, false, 0.5),
    RAP_GOLDEN(true, false, 1.0);

    private final boolean scored;
    private final boolean pitchRequired;
    private final double scoreWeight;

    KaraokeNoteType(boolean scored, boolean pitchRequired, double scoreWeight) {
        this.scored = scored;
        this.pitchRequired = pitchRequired;
        this.scoreWeight = scoreWeight;
    }

    public boolean isScored() {
        return scored;
    }

    public boolean isPitchRequired() {
        return pitchRequired;
    }

    public double getScoreWeight() {
        return scoreWeight;
    }
}
