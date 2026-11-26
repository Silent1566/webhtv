package com.fongmi.android.tv.smb;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * SMB URI 与凭据之间的转换。
 *
 * <p>落库与展示只使用无凭据形态（{@code smb://host[:port]/share/path}），播放前才由
 * {@link #playbackUri(String, String, String, String)} 拼上 userinfo，因此历史、推送和日志里
 * 不会留下密码。契约与出货包里的 {@code androidx.media3.datasource.SmbDataSource} 一致：
 * userinfo 缺失即 guest，端口默认 445，路径首段是共享名。
 */
public final class SmbUriResolver {

    private static final String SCHEME = "smb://";
    private static final int DEFAULT_PORT = 445;
    /** userinfo 里必须编码的字符（RFC 3986 之外再加上分隔符本身）。 */
    private static final String USER_INFO_SAFE = "-._~!$&'()*+,;=";

    private SmbUriResolver() {
    }

    /** 生成可落库的无凭据 URI；host 与共享名缺失时返回 null。 */
    public static String storageUri(String host, int port, String share, String path) {
        String hostPart = trim(host);
        String sharePart = trim(share);
        String pathPart = trim(path);
        if (hostPart.isEmpty() || sharePart.isEmpty() || pathPart.isEmpty()) return null;
        StringBuilder builder = new StringBuilder(SCHEME).append(hostPart);
        if (port > 0 && port != DEFAULT_PORT) builder.append(':').append(port);
        builder.append('/').append(sharePart);
        if (!pathPart.startsWith("/")) builder.append('/');
        builder.append(pathPart);
        return builder.toString();
    }

    /** 拼上凭据得到播放 URI；无用户名时原样返回（走 guest）。 */
    public static String playbackUri(String storageUri, String username, String password, String domain) {
        String stripped = stripCredentials(storageUri);
        if (stripped == null) return null;
        String user = trim(username);
        if (user.isEmpty()) return stripped;
        String userInfo = encode(user) + (password == null || password.isEmpty() ? "" : ":" + encode(password));
        return SCHEME + userInfo + "@" + stripped.substring(SCHEME.length());
    }

    /** 去掉 userinfo，幂等；输入非法时返回 null。 */
    public static String stripCredentials(String raw) {
        String value = trim(raw);
        if (!startsWithScheme(value)) return null;
        String rest = value.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        String authority = rest.substring(0, slash);
        int at = authority.lastIndexOf('@');
        if (at < 0) return value;
        String hostPort = authority.substring(at + 1);
        return hostPort.isEmpty() ? null : SCHEME + hostPort + rest.substring(slash);
    }

    /** 凭据索引键：host（小写）+ 共享名；无法构成时返回 null。 */
    public static String hostKey(String raw) {
        String stripped = stripCredentials(raw);
        if (stripped == null) return null;
        String rest = stripped.substring(SCHEME.length());
        String[] segments = rest.split("/");
        if (segments.length < 2) return null;
        String share = segments[1].trim();
        if (share.isEmpty()) return null;
        String authority = segments[0];
        int colon = authority.lastIndexOf(':');
        String host = colon > 0 && authority.indexOf(':') == colon ? authority.substring(0, colon) : authority;
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return null;
        return host + "/" + share;
    }

    public static String username(String raw) {
        String userInfo = userInfo(raw);
        if (userInfo == null) return "";
        int colon = userInfo.indexOf(':');
        return decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
    }

    public static String password(String raw) {
        String userInfo = userInfo(raw);
        if (userInfo == null) return "";
        int colon = userInfo.indexOf(':');
        return colon < 0 ? "" : decode(userInfo.substring(colon + 1));
    }

    private static String userInfo(String raw) {
        String value = trim(raw);
        if (!startsWithScheme(value)) return null;
        String rest = value.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        String authority = rest.substring(0, slash);
        int at = authority.lastIndexOf('@');
        return at < 0 ? null : authority.substring(0, at);
    }

    private static boolean startsWithScheme(String value) {
        return value.regionMatches(true, 0, SCHEME, 0, SCHEME.length());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** userinfo 百分号编码：保留 RFC 3986 的 unreserved 与子分隔符，其余转 UTF-8 十六进制。 */
    static String encode(String value) {
        StringBuilder builder = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || USER_INFO_SAFE.indexOf(c) >= 0) {
                builder.append((char) c);
            } else {
                builder.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0x0F, 16)));
            }
        }
        return builder.toString();
    }

    static String decode(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    out.write((high << 4) + low);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
