package com.fongmi.android.tv.player.karaoke;

public class KaraokeNote {

    private final long startMs;
    private final long endMs;
    private final int startBeat;
    private final int lengthBeat;
    private final int pitch;
    private final String text;
    private final KaraokeNoteType type;
    private final int lineIndex;

    public KaraokeNote(long startMs, long endMs, int startBeat, int lengthBeat, int pitch, String text, KaraokeNoteType type) {
        this(startMs, endMs, startBeat, lengthBeat, pitch, text, type, 0);
    }

    public KaraokeNote(long startMs, long endMs, int startBeat, int lengthBeat, int pitch, String text, KaraokeNoteType type, int lineIndex) {
        this.startMs = Math.max(0, startMs);
        this.endMs = Math.max(this.startMs, endMs);
        this.startBeat = startBeat;
        this.lengthBeat = Math.max(0, lengthBeat);
        this.pitch = pitch;
        this.text = text == null ? "" : text;
        this.type = type == null ? KaraokeNoteType.NORMAL : type;
        this.lineIndex = Math.max(0, lineIndex);
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public long getDurationMs() {
        return endMs - startMs;
    }

    public int getStartBeat() {
        return startBeat;
    }

    public int getLengthBeat() {
        return lengthBeat;
    }

    public int getPitch() {
        return pitch;
    }

    public String getText() {
        return text;
    }

    public KaraokeNoteType getType() {
        return type;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startMs && positionMs < endMs;
    }

    public boolean isScored() {
        return type.isScored() && getDurationMs() > 0;
    }

    public boolean isPitchRequired() {
        return type.isPitchRequired();
    }

    public double getWeightedDurationMs() {
        return isScored() ? getDurationMs() * type.getScoreWeight() : 0;
    }
}
