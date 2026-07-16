package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class VodDetailCache {

    private static final int MAX_PENDING = 8;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ConcurrentHashMap<String, Vod> CACHE = new ConcurrentHashMap<>();

    public static String put(Vod vod) {
        if (vod == null) return "";
        if (CACHE.size() >= MAX_PENDING) CACHE.clear();
        String key = "vod_detail_" + NEXT_ID.incrementAndGet();
        CACHE.put(key, vod);
        return key;
    }

    public static Vod take(String key) {
        if (key == null || key.isEmpty()) return null;
        return CACHE.remove(key);
    }

    private VodDetailCache() {
    }
}
