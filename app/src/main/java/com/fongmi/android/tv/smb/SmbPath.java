package com.fongmi.android.tv.smb;

import androidx.annotation.Nullable;

/**
 * SMB 共享内路径的纯字符串处理：统一分隔符、拼接、回溯，并拒绝 {@code ..} 越界。
 *
 * <p>这里的路径是"共享内的相对路径"，共享名单独作为一段（见 {@link #share(String)}）。
 */
public final class SmbPath {

    private SmbPath() {
    }

    /** 归一化为无前导/尾随斜杠、单斜杠分隔的相对路径；含 {@code ..} 段时返回 null。 */
    @Nullable
    public static String normalize(String path) {
        if (path == null) return "";
        String unified = path.trim().replace('\\', '/');
        String[] segments = unified.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            if (".".equals(segment)) continue;
            if ("..".equals(segment)) return null;
            if (builder.length() > 0) builder.append('/');
            builder.append(segment);
        }
        return builder.toString();
    }

    /** 拼接父路径与子名；子名越界时返回 null。 */
    @Nullable
    public static String resolve(String base, String name) {
        String parent = normalize(base);
        String child = normalize(name);
        if (parent == null || child == null) return null;
        if (parent.isEmpty()) return child;
        if (child.isEmpty()) return parent;
        return parent + "/" + child;
    }

    /** 上一级；已在共享根时返回空串。 */
    public static String parent(String path) {
        String value = normalize(path);
        if (value == null || value.isEmpty()) return "";
        int slash = value.lastIndexOf('/');
        return slash < 0 ? "" : value.substring(0, slash);
    }

    public static String displayName(String path) {
        String value = normalize(path);
        if (value == null || value.isEmpty()) return "";
        int slash = value.lastIndexOf('/');
        return slash < 0 ? value : value.substring(slash + 1);
    }

    public static String share(String path) {
        String value = normalize(path);
        if (value == null || value.isEmpty()) return "";
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }
}
