package com.fongmi.android.tv.player.danmaku;

public record LiveDanmakuMessage(Type type, String text, int colorArgb, long receivedAtMs, long generation) {

    public enum Type {
        NORMAL,
        SUPER_CHAT
    }
}
