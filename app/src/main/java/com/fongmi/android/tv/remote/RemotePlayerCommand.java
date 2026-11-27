package com.fongmi.android.tv.remote;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * {@code player.command} 载荷的解析与校验。
 *
 * <p>把"远端传来的字符串"收敛成有限的动作集，越界或未知动作在这里就被拒掉，
 * 执行层因此不需要再做防御；纯逻辑、可单测。
 */
public final class RemotePlayerCommand {

    public enum Action {

        PLAY_PAUSE("play_pause"),
        NEXT("next"),
        PREV("prev"),
        SEEK_FORWARD("seek_forward"),
        SEEK_BACK("seek_back"),
        BACK("back"),
        UP("up"),
        DOWN("down"),
        LEFT("left"),
        RIGHT("right"),
        ENTER("enter"),
        VOLUME_UP("volume_up"),
        VOLUME_DOWN("volume_down");

        public final String wire;

        Action(String wire) {
            this.wire = wire;
        }

        @Nullable
        static Action of(String wire) {
            for (Action action : values()) {
                if (action.wire.equals(wire)) return action;
            }
            return null;
        }
    }

    /** 解析结果：动作 + 已收敛的 seek 步长（导航类恒为 0）。 */
    public static final class Parsed {

        public final Action action;
        public final long valueMs;

        Parsed(Action action, long valueMs) {
            this.action = action;
            this.valueMs = valueMs;
        }
    }

    /** seek 步长下界：太小没有意义，连续下发还会抖。 */
    private static final long MIN_SEEK_MS = 1000L;
    /** seek 步长上界：避免一次命令直接跳到片尾。 */
    private static final long MAX_SEEK_MS = 600000L;
    private static final long DEFAULT_SEEK_MS = 10000L;

    private RemotePlayerCommand() {
    }

    /** 解析载荷；动作缺失或未知时返回 null，调用方按"不支持的命令"回复。 */
    @Nullable
    public static Parsed parse(@Nullable JsonObject payload) {
        if (payload == null) return null;
        String wire = payload.has("action") && !payload.get("action").isJsonNull()
                ? payload.get("action").getAsString().trim() : "";
        Action action = Action.of(wire);
        if (action == null) return null;
        boolean seeking = action == Action.SEEK_FORWARD || action == Action.SEEK_BACK;
        long value = seeking ? seekValue(payload) : 0L;
        return new Parsed(action, value);
    }

    private static long seekValue(JsonObject payload) {
        if (!payload.has("value") || payload.get("value").isJsonNull()) return DEFAULT_SEEK_MS;
        long value;
        try {
            value = payload.get("value").getAsLong();
        } catch (Throwable e) {
            return DEFAULT_SEEK_MS;
        }
        if (value <= 0L) return DEFAULT_SEEK_MS;
        return Math.max(MIN_SEEK_MS, Math.min(MAX_SEEK_MS, value));
    }
}
