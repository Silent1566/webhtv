package com.fongmi.android.tv.player.karaoke;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Xml;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Response;

class KaraokeAudioExtractor {

    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    private static final long MAX_MANIFEST_BYTES = 1024L * 1024L;
    private static final OkHttpClient CLIENT = OkHttp.player()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private KaraokeAudioExtractor() {
    }

    static Opened open(KaraokeTrackRepository.MediaInput input) throws Exception {
        if (input == null || input.isEmpty()) throw new IllegalStateException("empty player");
        List<Candidate> candidates = candidates(input.getUrl(), input.getHeaders());
        Exception last = null;
        for (Candidate candidate : candidates) {
            MediaExtractor extractor = new MediaExtractor();
            try {
                setDataSource(extractor, candidate.url, candidate.headers);
                int track = selectAudioTrack(extractor);
                if (track < 0) throw new IllegalStateException("no audio track");
                if (SpiderDebug.isEnabled()) SpiderDebug.log("karaoke-pitch", "audio source open kind=%s track=%d url=%s headers=%s", candidate.kind, track, summarize(candidate.url), candidate.headers.size());
                return new Opened(extractor, track, candidate.kind);
            } catch (Exception e) {
                last = e;
                if (SpiderDebug.isEnabled()) SpiderDebug.log("karaoke-pitch", "audio source failed kind=%s url=%s error=%s", candidate.kind, summarize(candidate.url), errorChain(e));
                try {
                    extractor.release();
                } catch (Exception ignored) {
                }
            }
        }
        throw unsupported(last);
    }

    static boolean isUnsupportedError(String error) {
        if (TextUtils.isEmpty(error)) return false;
        String lower = error.toLowerCase(Locale.ROOT);
        return lower.contains("unsupported media source")
                || lower.contains("failed to instantiate extractor")
                || lower.contains("failed to create mediaextractor")
                || lower.contains("no audio track");
    }

    private static List<Candidate> candidates(String url, Map<String, String> headers) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, String> safeHeaders = sanitizeHeaders(headers);
        if (isEdlUrl(url)) addEdlCandidates(candidates, url, safeHeaders);
        if (isConcatenatingUrl(url)) addConcatCandidates(candidates, url, safeHeaders);
        if (isDashLike(url)) {
            try {
                candidates.addAll(dashCandidates(url, safeHeaders));
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("karaoke-pitch", "dash resolve failed url=%s error=%s", summarize(url), errorChain(e));
            }
        }
        addUnique(candidates, new Candidate(url, safeHeaders, "media"));
        return candidates;
    }

    private static void addEdlCandidates(List<Candidate> candidates, String url, Map<String, String> headers) {
        List<Candidate> primary = new ArrayList<>();
        List<Candidate> secondary = new ArrayList<>();
        String body = url.substring("edl://".length());
        int cursor = 0;
        int stream = 0;
        while (cursor < body.length()) {
            while (cursor < body.length() && body.charAt(cursor) == ';') cursor++;
            if (body.regionMatches(cursor, "!new_stream", 0, "!new_stream".length())) {
                stream++;
                cursor += "!new_stream".length();
                continue;
            }
            if (!body.regionMatches(cursor, "file=", 0, "file=".length())) {
                int next = body.indexOf(';', cursor);
                cursor = next < 0 ? body.length() : next + 1;
                continue;
            }
            EdlValue value = readEdlValue(body, cursor + "file=".length());
            if (value == null) break;
            Candidate candidate = new Candidate(value.text, headers, stream > 0 ? "edl-audio" : "edl-stream");
            if (stream > 0) secondary.add(candidate);
            else primary.add(candidate);
            cursor = value.end;
            int next = body.indexOf(';', cursor);
            cursor = next < 0 ? body.length() : next + 1;
        }
        for (Candidate candidate : secondary) addUnique(candidates, candidate);
        for (Candidate candidate : primary) addUnique(candidates, candidate);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("karaoke-pitch", "edl candidates audio=%d primary=%d len=%d", secondary.size(), primary.size(), url.length());
    }

    private static EdlValue readEdlValue(String body, int start) {
        if (start >= body.length()) return null;
        if (body.charAt(start) != '%') {
            int comma = body.indexOf(',', start);
            int semicolon = body.indexOf(';', start);
            int end = comma < 0 ? semicolon : semicolon < 0 ? comma : Math.min(comma, semicolon);
            if (end < 0) end = body.length();
            return end <= start ? null : new EdlValue(body.substring(start, end), end);
        }
        int marker = body.indexOf('%', start + 1);
        if (marker < 0) return null;
        int byteLength;
        try {
            byteLength = Integer.parseInt(body.substring(start + 1, marker));
        } catch (NumberFormatException e) {
            return null;
        }
        if (byteLength <= 0) return null;
        int valueStart = marker + 1;
        int end = utf8End(body, valueStart, byteLength);
        if (end < 0) return null;
        return new EdlValue(body.substring(valueStart, end), end);
    }

    private static int utf8End(String value, int start, int byteLength) {
        int bytes = 0;
        int cursor = start;
        while (cursor < value.length() && bytes < byteLength) {
            int codePoint = value.codePointAt(cursor);
            bytes += new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            cursor += Character.charCount(codePoint);
        }
        return bytes == byteLength ? cursor : -1;
    }

    private static void addConcatCandidates(List<Candidate> candidates, String url, Map<String, String> headers) {
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length == 0 || TextUtils.isEmpty(info[0])) continue;
            addUnique(candidates, new Candidate(info[0], headers, "concat"));
        }
    }

    private static List<Candidate> dashCandidates(String url, Map<String, String> headers) throws Exception {
        String manifest = fetchManifest(url, headers);
        List<Candidate> candidates = parseDashAudio(url, manifest, headers);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("karaoke-pitch", "dash audio candidates count=%d url=%s", candidates.size(), summarize(url));
        return candidates;
    }

    private static String fetchManifest(String url, Map<String, String> headers) throws Exception {
        if (isDataUri(url)) return decodeDataManifest(url);
        try (Response response = OkHttp.newCall(CLIENT, url, headers).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("manifest http " + response.code());
            long length = response.body() == null ? 0 : response.body().contentLength();
            if (length > MAX_MANIFEST_BYTES) throw new IllegalStateException("manifest too large");
            String text = response.body() == null ? "" : response.body().string();
            if (text.getBytes().length > MAX_MANIFEST_BYTES) throw new IllegalStateException("manifest too large");
            if (!looksLikeDash(text)) throw new IllegalStateException("not dash manifest");
            return text;
        }
    }

    private static String decodeDataManifest(String url) {
        int comma = url == null ? -1 : url.indexOf(',');
        if (comma < 0) throw new IllegalArgumentException("invalid data manifest");
        String meta = url.substring(0, comma).toLowerCase(Locale.ROOT);
        String payload = url.substring(comma + 1);
        byte[] bytes = meta.contains(";base64")
                ? decodeBase64(Uri.decode(payload))
                : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MANIFEST_BYTES) throw new IllegalStateException("manifest too large");
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!looksLikeDash(text)) throw new IllegalStateException("not dash manifest");
        return text;
    }

    private static byte[] decodeBase64(String payload) {
        try {
            return Base64.decode(payload, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return Base64.decode(payload, Base64.URL_SAFE);
        }
    }

    private static List<Candidate> parseDashAudio(String manifestUrl, String manifest, Map<String, String> headers) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(manifest));
        boolean inAudioAdaptation = false;
        boolean representationAudio = false;
        int adaptationDepth = -1;
        int representationDepth = -1;
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            String name = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("AdaptationSet".equals(name)) {
                    adaptationDepth = parser.getDepth();
                    inAudioAdaptation = isAudio(parser);
                } else if ("ContentComponent".equals(name) && adaptationDepth > 0 && parser.getDepth() == adaptationDepth + 1) {
                    inAudioAdaptation = inAudioAdaptation || isAudio(parser);
                } else if ("Representation".equals(name)) {
                    representationDepth = parser.getDepth();
                    representationAudio = inAudioAdaptation || isAudio(parser);
                } else if ("BaseURL".equals(name)) {
                    String value = parser.nextText();
                    if ((representationDepth > 0 ? representationAudio : inAudioAdaptation) && !TextUtils.isEmpty(value)) {
                        addUnique(candidates, new Candidate(resolve(manifestUrl, value.trim()), headers, "dash-audio"));
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("Representation".equals(name) && parser.getDepth() == representationDepth) {
                    representationDepth = -1;
                    representationAudio = false;
                } else if ("AdaptationSet".equals(name) && parser.getDepth() == adaptationDepth) {
                    adaptationDepth = -1;
                    inAudioAdaptation = false;
                }
            }
        }
        return candidates;
    }

    private static boolean isAudio(XmlPullParser parser) {
        String contentType = attr(parser, "contentType");
        String mime = attr(parser, "mimeType");
        String codecs = attr(parser, "codecs");
        return "audio".equalsIgnoreCase(contentType)
                || startsWith(mime, "audio/")
                || startsWith(codecs, "mp4a")
                || startsWith(codecs, "ac-3")
                || startsWith(codecs, "ec-3")
                || startsWith(codecs, "opus");
    }

    private static String attr(XmlPullParser parser, String name) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (name.equalsIgnoreCase(parser.getAttributeName(i))) return parser.getAttributeValue(i);
        }
        return "";
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private static String resolve(String base, String value) {
        try {
            return URI.create(base).resolve(value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean looksLikeDash(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("<mpd");
    }

    private static boolean isDashLike(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains(".mpd")
                || lower.contains("type=mpd")
                || lower.contains("format=mpd")
                || lower.contains("application/dash");
    }

    private static boolean isDataUri(String url) {
        return !TextUtils.isEmpty(url) && url.regionMatches(true, 0, "data:", 0, 5);
    }

    private static boolean isEdlUrl(String url) {
        return !TextUtils.isEmpty(url) && url.regionMatches(true, 0, "edl://", 0, 6);
    }

    private static boolean isConcatenatingUrl(String url) {
        return url != null && url.contains(CONCAT_SOURCE_SEPARATOR) && url.contains(CONCAT_DURATION_SEPARATOR);
    }

    private static void addUnique(List<Candidate> candidates, Candidate candidate) {
        if (candidate == null || TextUtils.isEmpty(candidate.url)) return;
        for (Candidate item : candidates) if (TextUtils.equals(item.url, candidate.url)) return;
        candidates.add(candidate);
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> safe = new HashMap<>();
        if (headers == null) return safe;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry == null || TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null) continue;
            if (HttpHeaders.RANGE.equalsIgnoreCase(entry.getKey())) continue;
            safe.put(entry.getKey(), entry.getValue());
        }
        return safe;
    }

    private static void setDataSource(MediaExtractor extractor, String url, Map<String, String> headers) throws Exception {
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("empty url");
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme)) {
            extractor.setDataSource(App.get(), uri, headers);
        } else if ("file".equalsIgnoreCase(scheme)) {
            extractor.setDataSource(Uri.decode(uri.getPath()));
        } else if (TextUtils.isEmpty(scheme)) {
            extractor.setDataSource(url);
        } else {
            extractor.setDataSource(url, headers);
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) return i;
        }
        return -1;
    }

    private static Exception unsupported(Exception cause) {
        String message = cause == null || TextUtils.isEmpty(cause.getMessage()) ? "unsupported media source" : "unsupported media source: " + cause.getMessage();
        return new IllegalStateException(message, cause);
    }

    private static String summarize(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            StringBuilder builder = new StringBuilder();
            builder.append(uri.getScheme()).append("://");
            builder.append(TextUtils.isEmpty(uri.getHost()) ? "unknown" : uri.getHost());
            if (uri.getPort() > 0) builder.append(':').append(uri.getPort());
            if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
            if (!TextUtils.isEmpty(uri.getQuery())) builder.append(" len=").append(url.length());
            return builder.toString();
        } catch (Exception ignored) {
            return "len=" + url.length();
        }
    }

    private static String errorChain(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 4) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getSimpleName());
            if (!TextUtils.isEmpty(current.getMessage())) builder.append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return builder.toString();
    }

    static class Opened implements AutoCloseable {

        final MediaExtractor extractor;
        final int track;
        final String kind;

        private Opened(MediaExtractor extractor, int track, String kind) {
            this.extractor = extractor;
            this.track = track;
            this.kind = kind;
        }

        @Override
        public void close() {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static class Candidate {

        private final String url;
        private final Map<String, String> headers;
        private final String kind;

        private Candidate(String url, Map<String, String> headers, String kind) {
            this.url = url;
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
            this.kind = kind;
        }
    }

    private static class EdlValue {

        private final String text;
        private final int end;

        private EdlValue(String text, int end) {
            this.text = text;
            this.end = end;
        }
    }
}
