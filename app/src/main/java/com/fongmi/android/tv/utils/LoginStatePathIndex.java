package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Compact prefix index for normalized login-state paths.
 *
 * <p>The login-state UI frequently asks whether a path is covered by a selected
 * directory or whether a directory contains a selected descendant. A linear
 * scan for every row becomes quadratic when learning produces many files. This
 * index keeps only the shortest covering paths and uses a sorted set for
 * descendant lookups.</p>
 */
public final class LoginStatePathIndex {

    private static final String PREFIX_END = "\uffff";
    private final NavigableSet<String> paths = new TreeSet<>();

    public LoginStatePathIndex() {
    }

    public LoginStatePathIndex(Collection<String> values) {
        addAll(values);
    }

    public void addAll(Collection<String> values) {
        if (values == null) return;
        for (String value : values) add(value);
    }

    /** Adds a path while retaining an existing covering parent. */
    public void add(String value) {
        String path = normalized(value);
        if (path.isEmpty() || hasAncestor(path)) return;
        removeDescendants(path);
        paths.add(path);
    }

    /** Removes an exact path and any selected ancestors that cover it. */
    public void removeCoveredBy(String value) {
        String path = normalized(value);
        if (path.isEmpty()) return;
        String current = path;
        while (!current.isEmpty()) {
            paths.remove(current);
            int slash = current.lastIndexOf('/');
            current = slash < 0 ? "" : current.substring(0, slash);
        }
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public int size() {
        return paths.size();
    }

    public boolean contains(String value) {
        String path = normalized(value);
        return !path.isEmpty() && paths.contains(path);
    }

    /** Returns true when an exact path or one of its parents is selected. */
    public boolean hasAncestor(String value) {
        String path = normalized(value);
        if (path.isEmpty()) return false;
        if (paths.contains(path)) return true;
        int slash = path.lastIndexOf('/');
        while (slash > 0) {
            if (paths.contains(path.substring(0, slash))) return true;
            slash = path.lastIndexOf('/', slash - 1);
        }
        return false;
    }

    /** Returns true when a selected path is a descendant of the given path. */
    public boolean hasDescendant(String value) {
        String path = normalized(value);
        if (path.isEmpty()) return false;
        String prefix = path + "/";
        String candidate = paths.ceiling(prefix);
        return candidate != null && candidate.startsWith(prefix);
    }

    public List<String> asList() {
        return new ArrayList<>(paths);
    }

    public static List<String> compact(Collection<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return new LoginStatePathIndex(values).asList();
    }

    private void removeDescendants(String path) {
        String prefix = path + "/";
        paths.subSet(prefix, true, prefix + PREFIX_END, true).clear();
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String path = value.trim().replace('\\', '/');
        while (path.contains("//")) path = path.replace("//", "/");
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.equals(".") || path.equals("..") || path.contains("../") || path.contains("/..") ? "" : path;
    }
}
