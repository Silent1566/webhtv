package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.bean.Config;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * /manage/config/import 的纯逻辑：只登记 PC 下发的配置，不下载、不解析、不切换当前配置。
 *
 * <p>入口只接受本机 ADB 转发来的 POST：浏览器 Origin/Referer、任何转发头、target 重定向和
 * 非本机 peer 一律拒绝；该约束保护 PC 控制面，不构成多租户或强配对认证。
 */
public final class ConfigImport {

    /** 仅 Nano 需要：路由识别、请求体护栏与拒绝结果；其余成员保持包内可见。 */
    public static final String PATH = "/manage/config/import";
    public static final String PC_HEADER = "x-webhtv-pc-import";
    static final int MAX_URL_LENGTH = 2048;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int MAX_TYPE = 2;
    private static final String FORM = "application/x-www-form-urlencoded";
    /** 配置表 (url,type) 索引非唯一，find-then-create 必须在同一临界区内完成。 */
    private static final Object IMPORT_LOCK = new Object();

    interface Store {

        Config find(String url, int type);

        Config create(int type, String url, String name);
    }

    public static final class Rejection {

        private final int status;
        private final String message;

        private Rejection(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    static final class Parsed {

        private final String url;
        private final String name;
        private final int type;

        private Parsed(String url, String name, int type) {
            this.url = url;
            this.name = name;
            this.type = type;
        }

        String getUrl() {
            return url;
        }

        String getName() {
            return name;
        }

        int getType() {
            return type;
        }
    }

    static final class Result {

        private final Config config;
        private final boolean created;

        private Result(Config config, boolean created) {
            this.config = config;
            this.created = created;
        }

        Config getConfig() {
            return config;
        }

        boolean isCreated() {
            return created;
        }
    }

    private ConfigImport() {
    }

    static Parsed parse(Map<String, String> params) {
        String url = value(params, "url");
        if (url.isEmpty()) throw new IllegalArgumentException("Missing url");
        if (url.length() > MAX_URL_LENGTH) throw new IllegalArgumentException("url too long");
        for (int i = 0; i < url.length(); i++) {
            char ch = url.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                throw new IllegalArgumentException("url contains invalid characters");
            }
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(hostOf(url).isEmpty() ? "url must contain a host" : "url is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url must start with http:// or https://");
        }
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("url must contain a host");
        if (uri.getRawUserInfo() != null) throw new IllegalArgumentException("url must not contain credentials");
        if (uri.getRawFragment() != null) throw new IllegalArgumentException("url must not contain a fragment");
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) throw new IllegalArgumentException("url port out of range");
        int type = 0;
        String rawType = value(params, "type");
        if (!rawType.isEmpty()) {
            try {
                type = Integer.parseInt(rawType);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid type");
            }
            if (type < 0 || type > MAX_TYPE) throw new IllegalArgumentException("type must be 0, 1 or 2");
        }
        return new Parsed(url, cap(value(params, "name")), type);
    }

    static Rejection writeGuard(String method, String remoteIp, Map<String, String> headers, Map<String, String> params) {
        if (!"POST".equalsIgnoreCase(method)) return new Rejection(405, "Use POST");
        if (!isLoopback(remoteIp)) return new Rejection(403, "PC import is loopback-only");
        if (headers == null) return new Rejection(403, "Missing PC import header");
        if (present(headers, "origin") || present(headers, "referer") || present(headers, "forwarded")
                || present(headers, "x-forwarded-for") || present(headers, "x-real-ip")) {
            return new Rejection(403, "Browser or forwarded requests are not allowed");
        }
        if (params != null && params.containsKey("target")) return new Rejection(403, "target forwarding is not allowed");
        if (!"1".equals(trim(headers.get(PC_HEADER)))) return new Rejection(403, "Missing X-WebHTV-PC-Import: 1");
        String contentType = headers.get("content-type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(FORM)) {
            return new Rejection(415, "Content-Type must be application/x-www-form-urlencoded");
        }
        return null;
    }

    /** NanoHTTPD 在通用 parseBody 之前调用：未知长度/分块/超大请求体一律拒绝。 */
    public static Rejection bodyGuard(String method, Map<String, String> headers) {
        if (!"POST".equalsIgnoreCase(method)) return null;
        if (headers == null) return new Rejection(411, "Content-Length required");
        if (present(headers, "transfer-encoding")) return new Rejection(400, "Chunked request body is not supported");
        String length = trim(headers.get("content-length"));
        if (length.isEmpty()) return new Rejection(411, "Content-Length required");
        long size;
        try {
            size = Long.parseLong(length);
        } catch (NumberFormatException e) {
            return new Rejection(411, "Content-Length required");
        }
        if (size < 0) return new Rejection(411, "Content-Length required");
        if (size > MAX_BODY_BYTES) return new Rejection(413, "Request body too large");
        return null;
    }

    static Result importConfig(Parsed parsed, Store store) {
        synchronized (IMPORT_LOCK) {
            Config existing = store.find(parsed.getUrl(), parsed.getType());
            if (existing != null) return new Result(existing, false);
            return new Result(store.create(parsed.getType(), parsed.getUrl(), parsed.getName()), true);
        }
    }

    static JsonObject data(Result result, Config current) {
        if (result == null || result.getConfig() == null) throw new IllegalStateException("import returned no config");
        Config config = result.getConfig();
        if (config.getId() == 0) throw new IllegalStateException("import returned no config id");
        String interfaceKey = trim(config.getInterfaceKey());
        if (interfaceKey.isEmpty()) throw new IllegalStateException("import returned no interfaceKey");
        JsonObject data = new JsonObject();
        data.addProperty("id", config.getId());
        data.addProperty("interfaceKey", interfaceKey);
        data.addProperty("url", config.getUrl() == null ? "" : config.getUrl());
        data.addProperty("name", config.getName() == null ? "" : config.getName());
        data.addProperty("created", result.isCreated());
        data.addProperty("active", isActive(config, current));
        return data;
    }

    static boolean isActive(Config imported, Config current) {
        return imported != null && current != null && imported.getId() != 0 && imported.getId() == current.getId();
    }

    static Map<String, String> withBody(Map<String, String> params, String postData) {
        Map<String, String> merged = new HashMap<>();
        if (params != null) merged.putAll(params);
        if (postData == null || postData.isEmpty() || merged.containsKey("url")) return merged;
        for (String pair : postData.split("&")) {
            int cut = pair.indexOf('=');
            if (cut <= 0) continue;
            String key = decode(pair.substring(0, cut));
            if (key.isEmpty() || merged.containsKey(key)) continue;
            merged.put(key, decode(pair.substring(cut + 1)));
        }
        return merged;
    }

    /** URI 拒绝解析时仍区分"没有主机"和"语法非法"，错误信息不泄露输入内容。 */
    private static String hostOf(String url) {
        int start = url.indexOf("://");
        if (start < 0) return "";
        String authority = url.substring(start + 3);
        int cut = authority.length();
        for (int i = 0; i < authority.length(); i++) {
            char ch = authority.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') {
                cut = i;
                break;
            }
        }
        authority = authority.substring(0, cut);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        if (authority.startsWith("[")) return authority;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) authority = authority.substring(0, colon);
        return authority.trim();
    }

    private static boolean isLoopback(String remoteIp) {
        if (remoteIp == null) return false;
        String value = remoteIp.trim();
        if (value.isEmpty()) return false;
        try {
            return InetAddress.getByName(value).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean present(Map<String, String> headers, String name) {
        String value = headers.get(name);
        return value != null && !value.trim().isEmpty();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private static String cap(String name) {
        if (name.length() <= MAX_NAME_LENGTH) return name;
        int end = MAX_NAME_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(Map<String, String> params, String key) {
        return params == null ? "" : trim(params.get(key));
    }
}
