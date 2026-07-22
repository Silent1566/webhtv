package com.fongmi.android.tv.ui.helper;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

public final class VodEventGuard {

    public static boolean matches(Vod item, String currentSiteKey, String currentId) {
        if (item == null) return false;
        String id = item.getId();
        String siteKey = item.getSiteKey();
        if (!TextUtils.isEmpty(id) && !TextUtils.equals(id, stripPageSuffix(currentId))) return false;
        return TextUtils.isEmpty(siteKey) || TextUtils.equals(siteKey, currentSiteKey);
    }

    static String stripPageSuffix(String id) {
        if (TextUtils.isEmpty(id)) return id;
        int slash = id.indexOf('/');
        return slash > 0 ? id.substring(0, slash) : id;
    }

    private VodEventGuard() {
    }
}
