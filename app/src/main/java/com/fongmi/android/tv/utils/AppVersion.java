package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.BuildConfig;

public final class AppVersion {

    private AppVersion() {
    }

    public static String fullName() {
        String tag = BuildConfig.BUILD_TAG == null ? "" : BuildConfig.BUILD_TAG.trim();
        if (tag.isEmpty()) tag = BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILD_TIME;
        return stripPrefix(tag);
    }

    public static boolean isCurrent(String name) {
        return stripPrefix(name).equals(stripPrefix(fullName()));
    }

    public static String stripPrefix(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.startsWith("v") && value.length() > 1 && Character.isDigit(value.charAt(1)) ? value.substring(1) : value;
    }
}
