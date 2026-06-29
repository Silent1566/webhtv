package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GithubProxy {

    public static final String DEFAULT = "https://gh-proxy.com/";
    private static final String[] BUILT_IN = {
            DEFAULT,
            "https://ghfast.top/",
            "https://99z.top/",
            "https://proxy.v2gh.com/",
            "https://proxy.api.030101.xyz/"
    };

    private GithubProxy() {
    }

    public static String apply(String url) {
        return apply(url, Setting.getGithubProxy());
    }

    static String apply(String url, String configured) {
        if (!isGithubDownload(url) || isProxied(url)) return url;
        String proxy = first(configured);
        return isEmpty(proxy) ? url : normalize(proxy) + url;
    }

    public static String defaultSources() {
        return String.join("\n", BUILT_IN);
    }

    public static String normalizeConfig(String value) {
        List<String> sources = sources(value);
        return sources.isEmpty() ? defaultSources() : String.join("\n", sources);
    }

    private static String first(String configured) {
        List<String> sources = sources(configured);
        return sources.isEmpty() ? DEFAULT : sources.get(0);
    }

    private static List<String> sources(String value) {
        List<String> list = new ArrayList<>();
        String text = isEmpty(value) ? defaultSources() : value;
        for (String item : text.split("[\\r\\n,;\\s]+")) {
            if (!item.startsWith("http://") && !item.startsWith("https://")) continue;
            String source = normalize(item);
            if (!list.contains(source)) list.add(source);
        }
        return list;
    }

    private static String normalize(String proxy) {
        return proxy.endsWith("/") ? proxy : proxy + "/";
    }

    private static boolean isGithubDownload(String url) {
        if (isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://raw.githubusercontent.com/")
                || lower.startsWith("https://gist.githubusercontent.com/")
                || lower.matches("https://github\\.com/[^/]+/[^/]+/releases/(latest/)?download/.+");
    }

    private static boolean isProxied(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (String source : BUILT_IN) if (lower.startsWith(source.toLowerCase(Locale.ROOT))) return true;
        return lower.matches("https?://[^/]+/https?://.*");
    }

    private static boolean isEmpty(String text) {
        return text == null || text.length() == 0;
    }
}
