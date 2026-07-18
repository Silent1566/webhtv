package com.fongmi.android.tv.player.karaoke;

import java.io.Serializable;

public class KaraokeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean scoring;
    private final boolean pitchScoring;
    private final int scorePercent;
    private final int hitPercent;
    private final int voicedPercent;
    private final int perfectPercent;
    private final int vibratoPercent;
    private final long totalMs;
    private final int bestComboSeconds;
    private final int scoredLineCount;
    private final int averageLineScorePercent;
    private final int bestLineScorePercent;
    private final String grade;
    private final String trackLabel;

    private KaraokeResult(boolean scoring, boolean pitchScoring, int scorePercent, int hitPercent, int voicedPercent, int perfectPercent, int vibratoPercent, long totalMs, int bestComboSeconds, int scoredLineCount, int averageLineScorePercent, int bestLineScorePercent, String trackLabel) {
        this.scoring = scoring;
        this.pitchScoring = pitchScoring;
        this.scorePercent = scorePercent;
        this.hitPercent = hitPercent;
        this.voicedPercent = voicedPercent;
        this.perfectPercent = Math.max(0, Math.min(100, perfectPercent));
        this.vibratoPercent = Math.max(0, Math.min(100, vibratoPercent));
        this.totalMs = totalMs;
        this.bestComboSeconds = Math.max(0, bestComboSeconds);
        this.scoredLineCount = Math.max(0, scoredLineCount);
        this.averageLineScorePercent = Math.max(0, Math.min(100, averageLineScorePercent));
        this.bestLineScorePercent = Math.max(0, Math.min(100, bestLineScorePercent));
        this.grade = KaraokeGrade.fromScore(scorePercent);
        this.trackLabel = trackLabel == null ? "" : trackLabel.trim();
    }

    public static KaraokeResult from(KaraokeTrack track, KaraokeScoreSnapshot snapshot) {
        if (snapshot == null) return empty(track);
        boolean scoring = track != null && track.hasScoredNotes();
        boolean pitchScoring = scoring && track.hasPitchRequiredNotes();
        int score = snapshot.getScorePercent();
        int hit = snapshot.getTotalWeightMs() <= 0 ? 0 : (int) Math.round(Math.max(0, Math.min(100, snapshot.getHitWeightMs() * 100.0 / snapshot.getTotalWeightMs())));
        return new KaraokeResult(scoring, pitchScoring, score, hit, snapshot.getVoicedPercent(), snapshot.getPerfectPercent(), snapshot.getVibratoPercent(), Math.round(snapshot.getTotalWeightMs()), snapshot.getBestComboSeconds(), snapshot.getScoredLineCount(), snapshot.getAverageLineScorePercent(), snapshot.getBestLineScorePercent(), label(track));
    }

    public static KaraokeResult empty(KaraokeTrack track) {
        boolean scoring = track != null && track.hasScoredNotes();
        return new KaraokeResult(scoring, scoring && track.hasPitchRequiredNotes(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, label(track));
    }

    public boolean isScoring() {
        return scoring;
    }

    public boolean isPitchScoring() {
        return pitchScoring;
    }

    public int getScorePercent() {
        return scorePercent;
    }

    public int getHitPercent() {
        return hitPercent;
    }

    public int getVoicedPercent() {
        return voicedPercent;
    }

    public int getPerfectPercent() {
        return perfectPercent;
    }

    public int getVibratoPercent() {
        return vibratoPercent;
    }

    public int getBonusPercent() {
        return Math.max(0, Math.min(100, Math.round(perfectPercent * 0.65f + vibratoPercent * 0.35f)));
    }

    public long getTotalSeconds() {
        return Math.max(0, Math.round(totalMs / 1000.0));
    }

    public int getBestComboSeconds() {
        return bestComboSeconds;
    }

    public int getScoredLineCount() {
        return scoredLineCount;
    }

    public int getAverageLineScorePercent() {
        return averageLineScorePercent;
    }

    public int getBestLineScorePercent() {
        return bestLineScorePercent;
    }

    public String getGrade() {
        return grade;
    }

    public String getTrackLabel() {
        return trackLabel;
    }

    private static String label(KaraokeTrack track) {
        if (track == null) return "";
        String artist = track.getArtist();
        String title = track.getTitle();
        if (!artist.isEmpty() && !title.isEmpty()) return artist + " - " + title;
        if (!title.isEmpty()) return title;
        return artist;
    }
}
