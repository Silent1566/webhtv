package com.fongmi.android.tv.player.diagnostic;

import java.util.Locale;

public enum PanProvider {
    QUARK("夸克"),
    BAIDU("百度"),
    UC("UC"),
    ALI("阿里"),
    XUNLEI("迅雷"),
    GENERIC("通用资源");

    private final String label;

    PanProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PanProvider fromHost(String host) {
        String value = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (matches(value, "quark.cn", "quark.com")) return QUARK;
        if (matches(value, "baidu.com", "baidupcs.com", "baiducontent.com")) return BAIDU;
        if (matches(value, "uc.cn", "ucweb.com")) return UC;
        if (matches(value, "aliyundrive.com", "alipan.com", "aliyuncs.com")) return ALI;
        if (matches(value, "xunlei.com", "xlpan.com")) return XUNLEI;
        return GENERIC;
    }

    private static boolean matches(String host, String... suffixes) {
        for (String suffix : suffixes) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }
}
