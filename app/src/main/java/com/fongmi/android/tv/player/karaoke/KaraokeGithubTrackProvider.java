package com.fongmi.android.tv.player.karaoke;

import android.text.TextUtils;

import com.fongmi.android.tv.setting.PlayerSetting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KaraokeGithubTrackProvider implements KaraokeTrackProvider {

    private static final Pattern GITHUB_REPO = Pattern.compile("(?i)(?:https://github\\.com/)?([^/\\s|]+)/([^/@\\s|]+)(?:/(?:tree|blob)/([^\\s|]+))?(?:@([^\\s|]+))?");
    private static final Source[] DEFAULT_SOURCES = new Source[]{
            new Source("GitHub USDX", "razzertronic", "usdx-songs", "master", "Unlicense"),
            new Source("GitHub UltraStar", "Vasil-Pahomov", "UltraStarSongs", "master", "GPL-3.0")
    };
    private static final Map<String, List<TreeEntry>> CACHE = new HashMap<>();

    @Override
    public List<KaraokeTrackRepository.SearchResult> search(String keyword) throws Exception {
        List<KaraokeTrackRepository.SearchResult> results = new ArrayList<>();
        if (TextUtils.isEmpty(keyword) || keyword.trim().length() < 2) return results;
        for (Source source : sources()) {
            for (TreeEntry entry : getTree(source)) {
                if (results.size() >= 24) return results;
                if (!matchesKeyword(entry.path, keyword)) continue;
                String label = labelFromPath(entry.path);
                String note = "UltraStar .txt · " + source.license;
                results.add(new KaraokeTrackRepository.SearchResult(source.name, KaraokeTrackRepository.parseSong(label), KaraokeTrackRepository.parseArtist(label), note, rawUrl(source, entry.path), false));
            }
        }
        return results;
    }

    private static List<Source> sources() {
        List<Source> sources = new ArrayList<>();
        for (Source source : DEFAULT_SOURCES) sources.add(source);
        String custom = PlayerSetting.getKaraokeGithubSources();
        if (TextUtils.isEmpty(custom)) return sources;
        for (String line : custom.split("\\r?\\n")) {
            Source source = Source.parse(line);
            if (source == null || contains(sources, source)) continue;
            sources.add(source);
        }
        return sources;
    }

    private static boolean contains(List<Source> sources, Source source) {
        for (Source item : sources) if (item.key().equals(source.key())) return true;
        return false;
    }

    private static List<TreeEntry> getTree(Source source) throws Exception {
        synchronized (CACHE) {
            List<TreeEntry> cached = CACHE.get(source.key());
            if (cached != null) return cached;
        }
        String url = "https://api.github.com/repos/" + source.owner + "/" + source.repo + "/git/trees/" + source.branch + "?recursive=1";
        String json = KaraokeTrackRepository.getRemoteText(url, null);
        JSONArray tree = new JSONObject(json).optJSONArray("tree");
        List<TreeEntry> entries = new ArrayList<>();
        if (tree != null) {
            for (int i = 0; i < tree.length(); i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item == null || !"blob".equals(item.optString("type"))) continue;
                String path = item.optString("path");
                int size = item.optInt("size", 0);
                if (!path.toLowerCase(Locale.ROOT).endsWith(".txt")) continue;
                if (size <= 0 || size > KaraokeTrackRepository.MAX_REMOTE_BYTES) continue;
                entries.add(new TreeEntry(path));
            }
        }
        synchronized (CACHE) {
            CACHE.put(source.key(), entries);
        }
        return entries;
    }

    private static boolean matchesKeyword(String path, String keyword) {
        String normalizedPath = KaraokeTrackRepository.normalizeSearch(path);
        String normalizedKeyword = KaraokeTrackRepository.normalizeSearch(keyword);
        if (TextUtils.isEmpty(normalizedPath) || TextUtils.isEmpty(normalizedKeyword)) return false;
        if (normalizedPath.contains(normalizedKeyword)) return true;
        for (String token : keyword.trim().split("[^\\p{L}\\p{N}]+")) {
            String normalizedToken = KaraokeTrackRepository.normalizeSearch(token);
            if (normalizedToken.length() >= 2 && !normalizedPath.contains(normalizedToken)) return false;
        }
        return true;
    }

    private static String labelFromPath(String path) {
        String value = stripExtension(path);
        int slash = value.lastIndexOf('/');
        String file = slash >= 0 ? value.substring(slash + 1) : value;
        if (isGenericSongFile(file) && slash > 0) {
            int parentSlash = value.lastIndexOf('/', slash - 1);
            return value.substring(parentSlash + 1, slash);
        }
        return file.replaceAll("(?i)\\s*\\((?:minus|plus|duet|karaoke)\\)\\s*$", "").trim();
    }

    private static String stripExtension(String name) {
        String value = name == null ? "" : name.trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int dot = value.lastIndexOf('.');
        if (dot > 0 && value.length() - dot <= 16) value = value.substring(0, dot);
        return value.trim();
    }

    private static boolean isGenericSongFile(String value) {
        String normalized = KaraokeTrackRepository.normalizeSearch(value);
        return "s".equals(normalized) || "sm".equals(normalized) || "sp".equals(normalized) || "song".equals(normalized);
    }

    private static String rawUrl(Source source, String path) throws Exception {
        return "https://raw.githubusercontent.com/" + source.owner + "/" + source.repo + "/" + source.branch + "/" + encodePath(path);
    }

    private static String encodePath(String path) throws Exception {
        StringBuilder builder = new StringBuilder();
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('/');
            builder.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
        }
        return builder.toString();
    }

    private static class Source {

        private final String name;
        private final String owner;
        private final String repo;
        private final String branch;
        private final String license;

        private Source(String name, String owner, String repo, String branch, String license) {
            this.name = name;
            this.owner = owner;
            this.repo = repo;
            this.branch = branch;
            this.license = license;
        }

        private String key() {
            return owner + "/" + repo + "/" + branch;
        }

        private static Source parse(String line) {
            String text = line == null ? "" : line.trim();
            if (text.isEmpty() || text.startsWith("#")) return null;
            String[] parts = text.split("\\|", -1);
            Matcher matcher = GITHUB_REPO.matcher(parts[0].trim());
            if (!matcher.find()) return null;
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            String branch = firstNonEmpty(matcher.group(4), matcher.group(3), "master");
            String name = parts.length > 1 && !TextUtils.isEmpty(parts[1].trim()) ? parts[1].trim() : "GitHub " + owner + "/" + repo;
            String license = parts.length > 2 && !TextUtils.isEmpty(parts[2].trim()) ? parts[2].trim() : "custom";
            return new Source(name, owner, repo, branch, license);
        }

        private static String firstNonEmpty(String... values) {
            if (values == null) return "";
            for (String value : values) if (!TextUtils.isEmpty(value)) return value;
            return "";
        }
    }

    private static class TreeEntry {

        private final String path;

        private TreeEntry(String path) {
            this.path = path;
        }
    }
}
