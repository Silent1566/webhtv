package com.fongmi.android.tv.theme;

import java.util.Locale;

/** Small, dependency-free color helpers shared by validation and token resolution. */
public final class ThemeColorUtil {

    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    private ThemeColorUtil() {
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String color = value.trim().toUpperCase(Locale.ROOT);
        if (!color.startsWith("#")) return null;
        if (color.length() == 4 && isHex(color, 1)) {
            return "#" + color.charAt(1) + color.charAt(1)
                    + color.charAt(2) + color.charAt(2)
                    + color.charAt(3) + color.charAt(3);
        }
        if (color.length() == 7 && isHex(color, 1)) return color;
        if (color.length() == 9 && color.startsWith("#FF") && isHex(color, 3)) return color.substring(3);
        return null;
    }

    private static boolean isHex(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character < '0' || character > '9')
                    && (character < 'A' || character > 'F')) return false;
        }
        return true;
    }

    public static boolean isValid(String value) {
        return normalize(value) != null;
    }

    public static int parse(String value, int fallback) {
        String normalized = normalize(value);
        if (normalized == null) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(normalized.substring(1), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String format(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    public static double contrast(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static int readableOn(int background) {
        return contrast(WHITE, background) >= contrast(BLACK, background) ? WHITE : BLACK;
    }

    public static int mix(int first, int second, float amountOfSecond) {
        float amount = clamp(amountOfSecond, 0f, 1f);
        int r = Math.round(red(first) + (red(second) - red(first)) * amount);
        int g = Math.round(green(first) + (green(second) - green(first)) * amount);
        int b = Math.round(blue(first) + (blue(second) - blue(first)) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int ensureContrast(int foreground, int background, double minimum) {
        if (contrast(foreground, background) >= minimum) return foreground;
        return readableOn(background);
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    private static double luminance(int color) {
        double r = channel(red(color));
        double g = channel(green(color));
        double b = channel(blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
