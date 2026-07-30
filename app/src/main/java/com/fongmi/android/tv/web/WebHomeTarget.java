package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public final class WebHomeTarget {

    public static final String ECLIPSE_URL = "file:///android_asset/webhome/eclipse.html";

    public enum Mode {
        SITE,
        GLOBAL
    }

    private final Mode mode;
    private final String url;

    private WebHomeTarget(Mode mode, String url) {
        this.mode = mode;
        this.url = url;
    }

    public static WebHomeTarget resolve(Site site) {
        return resolve(site == null ? null : site.getHomePage(), Setting.isWebHomeThemeEnabled(),
                Setting.getWebHomeThemeUrl(), Setting.getWebHomeThemeTrustedUrl());
    }

    static WebHomeTarget resolve(String siteHomePage, boolean globalEnabled, String globalUrl) {
        return resolve(siteHomePage, globalEnabled, globalUrl, globalUrl);
    }

    static WebHomeTarget resolve(String siteHomePage, boolean globalEnabled, String globalUrl, String trustedRemoteUrl) {
        String siteUrl = trim(siteHomePage);
        if (!siteUrl.isEmpty()) return new WebHomeTarget(Mode.SITE, siteUrl);
        if (!globalEnabled) return null;
        String themeUrl = trim(globalUrl);
        if (themeUrl.isEmpty()) themeUrl = ECLIPSE_URL;
        if (!isSafeThemeUrl(themeUrl)) return null;
        if (isRemoteUrl(themeUrl) && !themeUrl.equals(trim(trustedRemoteUrl))) return null;
        return new WebHomeTarget(Mode.GLOBAL, themeUrl);
    }

    public static boolean canLoad(Site site) {
        return resolve(site) != null;
    }

    public Mode getMode() {
        return mode;
    }

    public String getUrl() {
        return url;
    }

    public boolean isGlobal() {
        return mode == Mode.GLOBAL;
    }

    public boolean isRemoteGlobal() {
        return isGlobal() && isRemoteUrl(url);
    }

    public boolean injectsSiteExtensions() {
        return mode == Mode.SITE;
    }

    public String identity(String sourceKey) {
        if (mode == Mode.SITE) return "site:" + url;
        return "global:" + trim(sourceKey);
    }

    public String getOriginRule() {
        if (!isRemoteGlobal()) return "";
        try {
            URI uri = URI.create(url);
            int port = effectivePort(uri);
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (port == 443 ? "" : ":" + port);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public boolean allowsMainFrameUrl(String value) {
        if (!isRemoteGlobal()) return true;
        try {
            URI expected = URI.create(url);
            URI actual = URI.create(trim(value));
            return sameOrigin(expected, actual);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme() != null && second.getScheme() != null
                && first.getHost() != null && second.getHost() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public static boolean isSafeThemeUrl(String value) {
        String url = trim(value);
        String lower = url.toLowerCase(Locale.ROOT);
        String assetPrefix = "file:///android_asset/";
        if (lower.startsWith(assetPrefix)) return ECLIPSE_URL.equals(url);
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) || !trim(uri.getHost()).equals(uri.getHost())) return false;
            if (uri.getUserInfo() != null || uri.getPort() == 0 || uri.getPort() > 65535 || isBlockedHost(uri.getHost())) return false;
            return !trim(uri.getHost()).isEmpty();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isBlockedHost(String host) {
        String normalized = normalizeHost(host);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return true;
        if ("localhost".equals(lower) || lower.endsWith(".localhost") || lower.endsWith(".local")
                || "local".equals(lower) || "ip6-localhost".equals(lower) || "ip6-loopback".equals(lower)) return true;
        if (!isIpLiteral(normalized)) return isAmbiguousNumericAddress(normalized);
        try {
            for (InetAddress address : InetAddress.getAllByName(stripIpv6Brackets(normalized))) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocal(address)
                        || isReservedIpv4(address)) return true;
            }
        } catch (UnknownHostException ignored) {
            return true;
        }
        return false;
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host;
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean isIpLiteral(String host) {
        String value = stripIpv6Brackets(host);
        if (value.indexOf(':') >= 0) return true;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            if (part.length() > 1 && part.charAt(0) == '0') return false;
            int number = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                number = number * 10 + c - '0';
            }
            if (number > 255) return false;
        }
        return true;
    }

    private static boolean isAmbiguousNumericAddress(String host) {
        String value = stripIpv6Brackets(host);
        String component = "(?:0[xX][0-9a-fA-F]+|[0-9]+)";
        return value.matches(component + "(?:\\." + component + "){0,3}");
    }

    private static String stripIpv6Brackets(String host) {
        if (host != null && host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isReservedIpv4(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return false;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0) || (first == 192 && second == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51) || (first == 203 && second == 0)
                || first >= 224;
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isRemoteUrl(String value) {
        return value != null && value.regionMatches(true, 0, "https://", 0, 8);
    }
}
