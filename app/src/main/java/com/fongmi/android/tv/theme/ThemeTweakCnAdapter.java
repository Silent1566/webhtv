package com.fongmi.android.tv.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline, allowlisted adapter for common shadcn/TweakCN color tokens. */
public final class ThemeTweakCnAdapter {

    private ThemeTweakCnAdapter() {
    }

    public static Result parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("theme JSON is empty");
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("theme JSON is invalid", e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("theme JSON must be an object");
        JsonObject object = root.getAsJsonObject();
        ThemeProfile profile;
        if (object.has("format") || object.has("schemaVersion")) {
            profile = ThemeProfileCodec.parse(json);
            return new Result(profile, List.of());
        }
        profile = ThemeProfile.defaultProfile();
        ThemeProfile.ColorSet light = profile.colors.light;
        ThemeProfile.ColorSet dark = profile.colors.dark;
        List<String> warnings = new ArrayList<>();
        int recognized = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String value = color(entry.getValue());
            if (value == null) {
                warnings.add(entry.getKey() + " ignored: expected opaque color");
                continue;
            }
            if (apply(key, value, light, dark)) recognized++;
            else warnings.add(entry.getKey() + " ignored: unsupported token");
        }
        if (recognized == 0) throw new IllegalArgumentException("no supported TweakCN color tokens");
        profile.id = "webhtv.tweakcn.imported";
        profile.name = "Imported TweakCN theme";
        profile.source.type = "tweakcn";
        profile.seedSource = ThemeProfile.SEED_CUSTOM;
        profile.seedColor = light.primary != null ? light.primary : dark.primary;
        ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(profile);
        if (!checked.valid()) throw new IllegalArgumentException(checked.message());
        return new Result(checked.profile(), warnings);
    }

    private static boolean apply(String key, String value, ThemeProfile.ColorSet light, ThemeProfile.ColorSet dark) {
        switch (key) {
            case ThemeProfile.TWEAK_PRIMARY -> { light.primary = value; dark.primary = value; }
            case ThemeProfile.TWEAK_PRIMARY_FOREGROUND -> { light.onPrimary = value; dark.onPrimary = value; }
            case ThemeProfile.TWEAK_BACKGROUND -> { light.appBackground = value; dark.appBackground = value; }
            case ThemeProfile.TWEAK_CARD -> { light.surface = value; dark.surface = value; }
            case ThemeProfile.TWEAK_POPOVER -> { light.surfaceElevated = value; dark.surfaceElevated = value; }
            case ThemeProfile.TWEAK_FOREGROUND -> { light.onSurface = value; dark.onSurface = value; }
            case ThemeProfile.TWEAK_MUTED_FOREGROUND -> { light.onSurfaceVariant = value; dark.onSurfaceVariant = value; }
            case ThemeProfile.TWEAK_BORDER, ThemeProfile.TWEAK_INPUT -> { light.outline = value; dark.outline = value; }
            case ThemeProfile.TWEAK_RING -> { light.focus = value; dark.focus = value; }
            case ThemeProfile.TWEAK_DESTRUCTIVE -> { light.error = value; dark.error = value; }
            default -> { return false; }
        }
        return true;
    }

    private static String normalizeKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("--") ? key : "--" + key;
    }

    private static String color(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        return ThemeColorUtil.normalize(value.getAsString());
    }

    public record Result(ThemeProfile profile, List<String> warnings) {
        public Result {
            warnings = warnings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }
}
