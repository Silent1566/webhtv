package com.fongmi.android.tv.player.danmaku;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

public final class LiveDanmakuParser {

    static final int MAX_FRAME_BYTES = 64 * 1024;
    static final int MAX_MESSAGE_CODE_POINTS = 120;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private LiveDanmakuParser() {
    }

    public static Result parse(String frame, long generation, long receivedAtMs) {
        if (frame == null || frame.isBlank() || utf8Length(frame) > MAX_FRAME_BYTES) return Result.invalid();
        try {
            JsonElement root = JsonParser.parseString(frame);
            if (!root.isJsonObject()) return Result.invalid();
            JsonObject object = root.getAsJsonObject();
            String type = stringValue(object.get("type")).toLowerCase(Locale.ROOT);
            if ("online".equals(type)) return parseOnline(object.get("data"));
            LiveDanmakuMessage.Type messageType;
            if ("chat".equals(type)) {
                messageType = LiveDanmakuMessage.Type.NORMAL;
            } else if ("superchat".equals(type)) {
                messageType = LiveDanmakuMessage.Type.SUPER_CHAT;
            } else {
                return Result.invalid();
            }
            String message = normalizeText(stringValue(object.get("message")));
            if (message.isEmpty()) return Result.invalid();
            int color = parseColor(stringValue(object.get("color")));
            return Result.message(new LiveDanmakuMessage(messageType, message, color, receivedAtMs, generation));
        } catch (RuntimeException | StackOverflowError e) {
            return Result.invalid();
        }
    }

    private static Result parseOnline(JsonElement data) {
        if (data == null || !data.isJsonPrimitive() || !data.getAsJsonPrimitive().isNumber()) return Result.invalid();
        try {
            long online = data.getAsLong();
            return online < 0 ? Result.invalid() : Result.online(online);
        } catch (RuntimeException e) {
            return Result.invalid();
        }
    }

    private static String stringValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return "";
        return element.getAsString();
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder builder = new StringBuilder(Math.min(text.length(), MAX_MESSAGE_CODE_POINTS * 2));
        boolean previousSpace = false;
        int accepted = 0;
        for (int offset = 0; offset < text.length() && accepted < MAX_MESSAGE_CODE_POINTS; ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) continue;
            if (Character.isWhitespace(codePoint)) {
                if (builder.length() == 0 || previousSpace) continue;
                builder.append(' ');
                previousSpace = true;
                accepted++;
                continue;
            }
            builder.appendCodePoint(codePoint);
            previousSpace = false;
            accepted++;
        }
        int length = builder.length();
        while (length > 0 && Character.isWhitespace(builder.codePointBefore(length))) {
            length -= Character.charCount(builder.codePointBefore(length));
        }
        if (length != builder.length()) builder.setLength(length);
        return builder.toString();
    }

    private static int parseColor(String color) {
        if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) return DEFAULT_COLOR;
        try {
            return 0xFF000000 | Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    private static int utf8Length(String value) {
        int bytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            bytes += codePoint <= 0x7F ? 1 : codePoint <= 0x7FF ? 2 : codePoint <= 0xFFFF ? 3 : 4;
            if (bytes > MAX_FRAME_BYTES) return bytes;
        }
        return bytes;
    }

    public record Result(Kind kind, LiveDanmakuMessage message, long online) {

        static Result invalid() {
            return new Result(Kind.INVALID, null, -1L);
        }

        static Result online(long online) {
            return new Result(Kind.ONLINE, null, online);
        }

        static Result message(LiveDanmakuMessage message) {
            return new Result(Kind.MESSAGE, message, -1L);
        }

        public boolean isAccepted() {
            return kind != Kind.INVALID;
        }
    }

    public enum Kind {
        MESSAGE,
        ONLINE,
        INVALID
    }
}
