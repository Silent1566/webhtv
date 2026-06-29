package com.fongmi.android.tv.model;

public record SearchProgress(int current, int total, boolean finished) {

    public SearchProgress {
        total = Math.max(0, total);
        current = Math.max(0, Math.min(current, total));
        finished = finished || current >= total;
    }

    public static SearchProgress start(int total) {
        return new SearchProgress(0, total, total == 0);
    }

    public static SearchProgress of(int current, int total) {
        return new SearchProgress(current, total, current >= total);
    }
}
