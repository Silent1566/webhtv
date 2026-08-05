package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.History;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HistoryDisplayPolicy {

    private HistoryDisplayPolicy() {
    }

    public static List<History> project(List<History> source, boolean aggregateByTmdb) {
        List<History> items = new ArrayList<>();
        if (source != null) for (History item : source) if (item != null) items.add(item);
        if (!aggregateByTmdb) return sort(items);

        Map<String, History> aggregated = new HashMap<>();
        List<History> result = new ArrayList<>();
        for (History item : items) {
            String identity = tmdbIdentity(item);
            if (identity.isEmpty()) {
                result.add(item);
                continue;
            }
            History existing = aggregated.get(identity);
            if (existing == null || item.getCreateTime() > existing.getCreateTime()) aggregated.put(identity, item);
        }
        result.addAll(aggregated.values());
        return sort(result);
    }

    public static String tmdbIdentity(History item) {
        if (item == null || item.getTmdbId() <= 0) return "";
        String mediaType = item.getMediaType() == null ? "" : item.getMediaType().trim().toLowerCase(Locale.ROOT);
        if (!mediaType.equals("movie") && !mediaType.equals("tv")) return "";
        return mediaType + ":" + item.getTmdbId();
    }

    private static List<History> sort(List<History> items) {
        items.sort((first, second) -> Long.compare(second.getCreateTime(), first.getCreateTime()));
        return items;
    }
}
