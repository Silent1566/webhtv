package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TtmlClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final String TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter";
    private static final String TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata";
    private static final int MIN_SCORE = 58;
    private static final String[] AMLL_URLS = {
            "https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml",
            "https://amll-ttml-db.stevexmh.net/ncm/%s",
            "https://amll.mirror.dimeta.top/api/db/ncm-lyrics/%s.ttml",
            "https://cdn.jsdmirror.cn/gh/Steve-xmh/amll-ttml-db@main/ncm-lyrics/%s.ttml",
            "https://raw.githubusercontent.com/Steve-xmh/amll-ttml-db/refs/heads/main/ncm-lyrics/%s.ttml"
    };
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public LyricsResult find(LyricsRequest request) {
        Entry best = best(request, search(request));
        if (best == null) return null;
        String ttml = ttml(best.id);
        String text = toEnhancedLrc(ttml);
        if (!LyricsParser.hasTimedLine(text)) return null;
        return new LyricsResult("AMLL TTML", best.name, best.artist, best.album, text, best.durationSec * 1000L, true, best.score + 4);
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String keyword : keywords(request)) search(entries, seen, request, keyword);
        return entries;
    }

    private void search(List<Entry> entries, Set<Long> seen, LyricsRequest request, String keyword) {
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/search/get/web").newBuilder()
                .addQueryParameter("s", keyword)
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "8")
                .addQueryParameter("offset", "0")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject result = object.optJSONObject("result");
            JSONArray array = result == null ? null : result.optJSONArray("songs");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Entry entry = new Entry();
                entry.id = item.optLong("id");
                entry.name = clean(item.optString("name"));
                entry.artist = artists(item.optJSONArray("artists"));
                JSONObject album = item.optJSONObject("album");
                entry.album = album == null ? "" : clean(album.optString("name"));
                entry.durationSec = Math.round(item.optInt("duration", 0) / 1000f);
                if (entry.id > 0 && !TextUtils.isEmpty(entry.name) && seen.add(entry.id)) entries.add(entry);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "ttml search failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private Entry best(LyricsRequest request, List<Entry> entries) {
        Entry best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Entry entry : entries) {
            int score = score(request, entry);
            if (score <= bestScore) continue;
            entry.score = score;
            best = entry;
            bestScore = score;
        }
        return best != null && bestScore >= MIN_SCORE ? best : null;
    }

    private int score(LyricsRequest request, Entry entry) {
        return LyricsMatcher.matchScore(request, entry.name, entry.artist, entry.durationSec);
    }

    private String ttml(long id) {
        for (String pattern : AMLL_URLS) {
            try {
                String text = get(String.format(Locale.US, pattern, id));
                if (!TextUtils.isEmpty(text) && text.contains("<tt")) return text;
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "ttml request failed id=%s error=%s", id, e.getMessage());
            }
        }
        return "";
    }

    public static String toEnhancedLrc(String ttml) {
        if (TextUtils.isEmpty(ttml)) return "";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            try {
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
            } catch (Exception ignored) {
            }
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(ttml)));
            NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
            long globalOffset = readGlobalOffset(document);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < paragraphs.getLength(); i++) {
                Node node = paragraphs.item(i);
                if (!(node instanceof Element)) continue;
                Element p = (Element) node;
                long lineStart = parseTime(timingAttribute(p, "begin"));
                if (lineStart < 0) lineStart = firstSpanBegin(p);
                if (lineStart < 0) continue;
                lineStart = Math.max(0, lineStart + globalOffset);
                StringBuilder line = new StringBuilder();
                appendWords(line, p, lineStart, globalOffset);
                if (line.length() > 0) builder.append(formatTime(lineStart)).append(line).append('\n');
            }
            return builder.toString();
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "ttml parse failed error=%s", e.getMessage());
            return "";
        }
    }

    private static long readGlobalOffset(Document document) {
        NodeList audios = document.getElementsByTagNameNS("*", "audio");
        for (int i = 0; i < audios.getLength(); i++) {
            Node node = audios.item(i);
            if (node instanceof Element) return parseOffset(attribute((Element) node, "lyricOffset"));
        }
        return 0;
    }

    private static void appendWords(StringBuilder builder, Element parent, long lineStart, long globalOffset) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                builder.append(child.getTextContent());
                continue;
            }
            if (!(child instanceof Element)) continue;
            Element span = (Element) child;
            if (skipRole(span)) continue;
            long begin = parseTime(timingAttribute(span, "begin"));
            long end = parseTime(timingAttribute(span, "end"));
            String text = span.getTextContent();
            if (TextUtils.isEmpty(text)) continue;
            if (begin >= 0 && end > begin) {
                begin += globalOffset;
                end += globalOffset;
                builder.append('<')
                        .append(Math.max(0, begin - lineStart))
                        .append(',')
                        .append(end - begin)
                        .append('>')
                        .append(text);
            } else if (hasElementChild(span)) {
                appendWords(builder, span, lineStart, globalOffset);
            } else {
                builder.append(text);
            }
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (Exception ignored) {
        }
    }

    private static boolean skipRole(Element element) {
        String role = attribute(element, "role");
        return "x-translation".equals(role) || "x-roman".equals(role) || "x-bg".equals(role);
    }

    private static long firstSpanBegin(Element parent) {
        long best = -1;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element element = (Element) child;
            if (!"span".equals(localName(element))) continue;
            long begin = parseTime(timingAttribute(element, "begin"));
            if (begin >= 0 && (best < 0 || begin < best)) best = begin;
        }
        return best;
    }

    private static boolean hasElementChild(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) if (children.item(i) instanceof Element) return true;
        return false;
    }

    private static String timingAttribute(Element element, String localName) {
        return attribute(element, localName, TTML_PARAMETER_NS);
    }

    private static String attribute(Element element, String localName, String... namespaces) {
        if (element == null || TextUtils.isEmpty(localName)) return "";
        String direct = element.getAttribute(localName);
        if (!TextUtils.isEmpty(direct)) return direct;
        if (namespaces != null) {
            for (String namespace : namespaces) {
                String value = element.getAttributeNS(namespace, localName);
                if (!TextUtils.isEmpty(value)) return value;
            }
        }
        String metadata = element.getAttributeNS(TTML_METADATA_NS, localName);
        if (!TextUtils.isEmpty(metadata)) return metadata;
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (node == null) continue;
            String name = node.getLocalName();
            if (TextUtils.isEmpty(name)) name = node.getNodeName();
            if (localName.equals(name) || node.getNodeName().endsWith(":" + localName)) return node.getNodeValue();
        }
        return "";
    }

    private static String localName(Element element) {
        String name = element.getLocalName();
        return TextUtils.isEmpty(name) ? element.getNodeName().replaceFirst("^.*:", "") : name;
    }

    private static long parseOffset(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return 0;
        boolean negative = value.startsWith("-");
        if (negative || value.startsWith("+")) value = value.substring(1).trim();
        long parsed = parseTime(value);
        return parsed < 0 ? 0 : negative ? -parsed : parsed;
    }

    private static long parseTime(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return -1;
        try {
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.endsWith("ms")) return Math.round(parseDouble(value.substring(0, value.length() - 2)));
            if (lower.endsWith("s")) return Math.round(parseDouble(value.substring(0, value.length() - 1)) * 1000);
            if (lower.endsWith("m")) return Math.round(parseDouble(value.substring(0, value.length() - 1)) * 60_000);
            if (lower.endsWith("h")) return Math.round(parseDouble(value.substring(0, value.length() - 1)) * 3_600_000);
            String[] parts = value.split(":");
            double seconds;
            if (parts.length == 3) {
                seconds = parseDouble(parts[0]) * 3600 + parseDouble(parts[1]) * 60 + parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds = parseDouble(parts[0]) * 60 + parseDouble(parts[1]);
            } else {
                seconds = parseDouble(value);
            }
            return seconds < 0 ? -1 : Math.round(seconds * 1000);
        } catch (Exception e) {
            return -1;
        }
    }

    private List<String> keywords(LyricsRequest request) {
        return request.searchKeywords();
    }

    private int textScore(String wanted, String actual, int exact, int contains, int mismatch) {
        String a = LyricsMatcher.normalize(wanted);
        String b = LyricsMatcher.normalize(actual);
        if (TextUtils.isEmpty(a)) return 0;
        if (TextUtils.isEmpty(b)) return mismatch / 2;
        if (a.equals(b)) return exact;
        if (a.contains(b) || b.contains(a)) return contains;
        return mismatch;
    }

    private int durationScore(int wantedSec, int actualSec) {
        if (wantedSec <= 0 || actualSec <= 0) return 0;
        int delta = Math.abs(wantedSec - actualSec);
        if (delta <= 2) return 24;
        if (delta <= 5) return 20;
        if (delta <= 10) return 14;
        if (delta <= 20) return 4;
        if (delta <= 40) return -18;
        return -40;
    }

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String artists(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject artist = array.optJSONObject(i);
                String name = artist == null ? "" : clean(artist.optString("name"));
                if (!TextUtils.isEmpty(name)) names.add(name);
            }
        }
        return TextUtils.join(" / ", names);
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private static String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        long second = timeMs % 60000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minute, second / 1000, second % 1000);
    }

    private static class Entry {
        private long id;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
    }
}
