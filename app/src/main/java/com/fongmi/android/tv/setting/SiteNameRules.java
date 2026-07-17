package com.fongmi.android.tv.setting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SiteNameRules {

    private static final Pattern GROUP_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    private SiteNameRules() {
    }

    static String effectiveName(String rawName, String customName) {
        String custom = safe(customName).trim();
        return custom.isEmpty() ? safe(rawName) : custom;
    }

    static String customNameForStorage(String rawName, String inputName) {
        String value = safe(inputName).trim();
        return value.equals(safe(rawName).trim()) ? "" : value;
    }

    static List<String> groups(String rawName, String customName) {
        Set<String> groups = new LinkedHashSet<>();
        Matcher matcher = GROUP_PATTERN.matcher(effectiveName(rawName, customName));
        while (matcher.find()) {
            String group = matcher.group(1).trim();
            if (!group.isEmpty()) groups.add("[" + group + "]");
        }
        return new ArrayList<>(groups);
    }

    static boolean matchesSearch(String rawName, String customName, String key, String keyword) {
        String query = safe(keyword).trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        return contains(effectiveName(rawName, customName), query)
                || contains(rawName, query)
                || contains(key, query);
    }

    private static boolean contains(String value, String query) {
        return safe(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
