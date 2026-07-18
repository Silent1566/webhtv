package com.fongmi.android.tv.player.karaoke;

public class KaraokeGrade {

    private KaraokeGrade() {
    }

    public static String fromScore(int score) {
        int value = Math.max(0, Math.min(100, score));
        if (value >= 95) return "S+";
        if (value >= 90) return "S";
        if (value >= 80) return "A";
        if (value >= 70) return "B";
        if (value >= 60) return "C";
        if (value >= 40) return "D";
        return "E";
    }
}
