package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeTitleCompact {

    private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(mp4|mkv|avi|mov|flv|wmv|ts|m2ts|m3u8|rmvb|webm)$");
    private static final String SIZE_VALUE = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";
    private static final Pattern SIZE_PREFIX = Pattern.compile("(?i)^\\s*[\\[\\(（【]?\\s*" + SIZE_VALUE + "\\s*(?:GB|G|MB|M)\\s*[\\]\\)）】]?\\s*");
    private static final Pattern SIZE_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]?\\s*" + SIZE_VALUE + "\\s*(?:GB|G|MB|M)\\s*[\\]\\)）】]?\\s*$");
    private static final Pattern HASH_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]\\s*[A-F0-9]{8,32}\\s*[\\]\\)）】]\\s*$");
    private static final Pattern EPISODE_START = Pattern.compile("(?i)^(?:S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}(?:\\s*(?:E|[-~—–])\\s*[0-9]{1,4})?|[0-9]{1,2}\\s*x\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?|第\\s*[0-9一二三四五六七八九十百]+\\s*(?:集|话|話|期|章|回)|[0-9]{1,4}\\s*(?:集|话|話|期|章|回)|(?:EP|E)\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?|[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}|[0-9]{1,4}(?:\\D|$)|[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮)");
    private static final Pattern VARIANT_TOKEN = Pattern.compile("(?i)(?:^|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])((?:4320|2160|1080|720)P|[48]K|HQ|HD|HDR10(?:\\+|⁺)?|HDR|SDR|DV|60FPS|50FPS|30FPS|25FPS|24FPS|10BITS|8BITS|HEVC|H265|H\\.265|AVC|H264|H\\.264|AV1|DDP\\s*2[.·]?0|AAC\\s*2[.·]?0)(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])");
    private static final Pattern TAIL_CODE = Pattern.compile("(?i)[._\\-·]([0-9]{8,})$");
    private static final Pattern[] EPISODE_TOKENS = {
            Pattern.compile("(?i)S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}(?:\\s*(?:E|[-~—–])\\s*[0-9]{1,4})?"),
            Pattern.compile("(?i)[0-9]{1,2}\\s*x\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?"),
            Pattern.compile("(?i)第\\s*[0-9一二三四五六七八九十百]+\\s*(?:集|话|話|期|章|回)"),
            Pattern.compile("(?i)[0-9]{1,4}\\s*(?:集|话|話|期|章|回)"),
            Pattern.compile("(?i)(?:EP|E)\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?"),
            Pattern.compile("(?i)[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}"),
            Pattern.compile("(?i)(?:^|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])([0-9]{1,4}v[0-9]+|[0-9]{1,3})(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])"),
            Pattern.compile("(?i)[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮")
    };
    private static final Pattern TECH_SUFFIX = Pattern.compile("(?i)^[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字)(?:[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字))*[\\s._\\-\\[\\]()（）【】]*$");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+$");
    private static final int MAX_COMPACT_LENGTH = 14;

    private EpisodeTitleCompact() {
    }

    public static void apply(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return;
        if (!Setting.isCompactEpisodeTitle()) {
            for (Episode episode : episodes) episode.setDisplayName(null);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Episode episode : episodes) names.add(cleanFileNoise(episode.getRawDisplayName()));
        if (episodes.size() < 2) {
            for (int i = 0; i < episodes.size(); i++) episodes.get(i).setDisplayName(names.get(i));
            return;
        }
        int prefix = findPrefix(names);
        int suffix = findSuffix(names, prefix);
        List<String> compacted = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        for (String name : names) {
            String compact = cleanupEdge(name.substring(Math.min(prefix, name.length()), Math.max(Math.min(name.length() - suffix, name.length()), Math.min(prefix, name.length()))));
            if (TextUtils.isEmpty(compact)) compact = name;
            String token = findEpisodeToken(name);
            String display = preferEpisodeToken(token, compact);
            compacted.add(display);
            fallback.add(compact);
            tokens.add(token);
            count.put(display, count.getOrDefault(display, 0) + 1);
        }
        compacted = resolveDuplicates(names, compacted, fallback, tokens, count);
        for (int i = 0; i < episodes.size(); i++) {
            episodes.get(i).setDisplayName(compacted.get(i));
        }
    }

    private static String cleanFileNoise(String value) {
        String text = TextUtils.isEmpty(value) ? "" : value.trim();
        text = cleanEdgeNoise(text);
        text = EXTENSION.matcher(text).replaceFirst("");
        text = cleanEdgeNoise(text);
        return cleanupEdge(text);
    }

    private static String cleanEdgeNoise(String value) {
        String text = HASH_SUFFIX.matcher(value).replaceFirst("");
        text = SIZE_SUFFIX.matcher(text).replaceFirst("");
        text = SIZE_PREFIX.matcher(text).replaceFirst("");
        return text;
    }

    private static int findPrefix(List<String> names) {
        int prefix = commonPrefix(names);
        for (int i = prefix; i > 0; i--) {
            if (isUsefulPrefix(names, i)) return i;
        }
        return 0;
    }

    private static int commonPrefix(List<String> names) {
        int prefix = names.get(0).length();
        for (String name : names) {
            prefix = Math.min(prefix, name.length());
            for (int i = 0; i < prefix; i++) {
                if (names.get(0).charAt(i) != name.charAt(i)) {
                    prefix = i;
                    break;
                }
            }
        }
        return prefix;
    }

    private static boolean isUsefulPrefix(List<String> names, int index) {
        if (index < 2) return false;
        for (String name : names) {
            if (index > name.length()) return false;
            String rest = cleanupEdge(name.substring(index));
            if (TextUtils.isEmpty(rest)) return false;
            if (!isBoundary(name, index) && !canStartEpisodeAfterPrefix(name, index, rest)) return false;
        }
        return index >= 6 || allRestStartsEpisode(names, index);
    }

    private static boolean allRestStartsEpisode(List<String> names, int index) {
        for (String name : names) {
            if (!startsEpisode(cleanupEdge(name.substring(index)))) return false;
        }
        return true;
    }

    private static int findSuffix(List<String> names, int prefix) {
        int suffix = commonSuffix(names, prefix);
        for (int i = suffix; i > 0; i--) {
            if (isUsefulSuffix(names, i, prefix)) return i;
        }
        return 0;
    }

    private static int commonSuffix(List<String> names, int prefix) {
        int suffix = names.get(0).length() - Math.min(prefix, names.get(0).length());
        for (String name : names) {
            int max = name.length() - Math.min(prefix, name.length());
            suffix = Math.min(suffix, max);
            for (int i = 0; i < suffix; i++) {
                if (names.get(0).charAt(names.get(0).length() - 1 - i) != name.charAt(name.length() - 1 - i)) {
                    suffix = i;
                    break;
                }
            }
        }
        return suffix;
    }

    private static boolean isUsefulSuffix(List<String> names, int suffix, int prefix) {
        if (suffix < 2) return false;
        for (String name : names) {
            int start = name.length() - suffix;
            if (start <= prefix || !isBoundary(name, start)) return false;
            if (TextUtils.isEmpty(cleanupEdge(name.substring(prefix, start)))) return false;
        }
        String removed = names.get(0).substring(names.get(0).length() - suffix);
        return suffix >= 6 || TECH_SUFFIX.matcher(removed.toUpperCase(Locale.ROOT)).matches();
    }

    private static boolean isBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) return true;
        return isSeparator(text.charAt(index - 1)) || isSeparator(text.charAt(index));
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || "-_.·|/\\:：,，;；[]()（）【】《》".indexOf(c) >= 0;
    }

    private static boolean startsEpisode(String text) {
        return EPISODE_START.matcher(text).find();
    }

    private static boolean canStartEpisodeAfterPrefix(String text, int index, String rest) {
        if (!startsEpisode(rest)) return false;
        char previous = text.charAt(index - 1);
        return !Character.isDigit(previous) && !isAsciiLetter(previous);
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static String preferEpisodeToken(String token, String compact) {
        if (compact.length() <= MAX_COMPACT_LENGTH) return compact;
        return TextUtils.isEmpty(token) ? compact : token;
    }

    private static List<String> resolveDuplicates(List<String> names, List<String> compacted, List<String> fallback, List<String> tokens, Map<String, Integer> count) {
        List<String> result = new ArrayList<>(compacted);
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            if (entry.getValue() <= 1) continue;
            List<Integer> indexes = indexesOf(compacted, entry.getKey());
            List<String> displays = buildDistinctDisplays(names, fallback, tokens, indexes);
            for (int i = 0; i < indexes.size(); i++) result.set(indexes.get(i), displays.get(i));
        }
        return ensureUnique(result);
    }

    private static List<Integer> indexesOf(List<String> values, String target) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) if (values.get(i).equals(target)) indexes.add(i);
        return indexes;
    }

    private static List<String> buildDistinctDisplays(List<String> names, List<String> fallback, List<String> tokens, List<Integer> indexes) {
        List<String> firstCandidates = null;
        for (int level = 1; level <= 4; level++) {
            List<String> candidates = new ArrayList<>();
            for (int index : indexes) candidates.add(buildVariantDisplay(names.get(index), tokens.get(index), fallback.get(index), level));
            if (level == 1) firstCandidates = candidates;
            if (allDistinct(candidates)) return candidates;
        }
        return ensureUnique(firstCandidates == null ? new ArrayList<>() : firstCandidates);
    }

    private static String buildVariantDisplay(String name, String token, String fallback, int level) {
        if (TextUtils.isEmpty(token)) return fallback;
        List<String> hints = findVariantHints(name);
        if (hints.isEmpty()) return token;
        StringBuilder builder = new StringBuilder(token);
        for (int i = 0; i < Math.min(level, hints.size()); i++) builder.append(' ').append(hints.get(i));
        return builder.toString();
    }

    private static List<String> findVariantHints(String text) {
        List<String> hints = new ArrayList<>();
        Matcher matcher = VARIANT_TOKEN.matcher(text);
        while (matcher.find()) addHint(hints, normalizeVariantToken(matcher.group(1)));
        Matcher tail = TAIL_CODE.matcher(text);
        if (tail.find()) addHint(hints, tail.group(1));
        return hints;
    }

    private static void addHint(List<String> hints, String hint) {
        if (TextUtils.isEmpty(hint) || hints.contains(hint)) return;
        hints.add(hint);
    }

    private static String normalizeVariantToken(String token) {
        String value = token.replaceAll("\\s+", "").replace('⁺', '+').replace('·', '.').toUpperCase(Locale.ROOT);
        if (value.equals("H265")) return "HEVC";
        if (value.equals("H.265")) return "HEVC";
        if (value.equals("H264")) return "AVC";
        if (value.equals("H.264")) return "AVC";
        if (value.matches("[0-9]{3,4}P")) return value.substring(0, value.length() - 1) + "p";
        if (value.matches("[0-9]{2}FPS")) return value.substring(0, value.length() - 3) + "fps";
        if (value.matches("[0-9]{1,2}BITS")) return value.substring(0, value.length() - 4) + "bits";
        if (value.matches("AAC2[.]?0")) return "AAC2.0";
        if (value.matches("DDP2[.]?0")) return "DDP2.0";
        return value.replace("HDR10+", "HDR10");
    }

    private static boolean allDistinct(List<String> values) {
        Map<String, Integer> count = new HashMap<>();
        for (String value : values) {
            count.put(value, count.getOrDefault(value, 0) + 1);
            if (count.get(value) > 1) return false;
        }
        return true;
    }

    private static List<String> ensureUnique(List<String> values) {
        Map<String, Integer> total = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        List<String> result = new ArrayList<>();
        for (String value : values) total.put(value, total.getOrDefault(value, 0) + 1);
        for (String value : values) {
            if (total.get(value) <= 1) {
                result.add(value);
            } else {
                int index = used.getOrDefault(value, 0) + 1;
                used.put(value, index);
                result.add(value + "-" + index);
            }
        }
        return result;
    }

    private static String findEpisodeToken(String text) {
        for (Pattern pattern : EPISODE_TOKENS) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;
            String token = matcher.groupCount() > 0 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
            if (isStandaloneYear(token)) continue;
            return normalizeEpisodeToken(token);
        }
        return "";
    }

    private static String normalizeEpisodeToken(String token) {
        if (token == null) return "";
        String value = token.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.matches("[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}")) value = value.replace('.', '-').replace('_', '-');
        return value;
    }

    private static boolean isStandaloneYear(String token) {
        if (token == null || !token.matches("[0-9]{4}")) return false;
        int year = Integer.parseInt(token);
        return year >= 1900 && year <= 2099;
    }

    private static String cleanupEdge(String text) {
        return EDGE_SEPARATORS.matcher(text == null ? "" : text.trim()).replaceAll("");
    }
}
