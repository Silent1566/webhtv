package androidx.media3.mpvplayer;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;

import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.io.StringWriter;
import org.xml.sax.InputSource;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

public final class MpvHlsProxy extends NanoHTTPD {

    private static final String TAG = "mpv-proxy";
    private static final String MIME_M3U8 = "application/vnd.apple.mpegurl; charset=utf-8";
    private static final String MIME_TS = "video/MP2T";
    private static final String MIME_BINARY = "application/octet-stream";
    private static final String CACHE_FILE_SUFFIX = ".bin";
    private static final String CACHE_META_SUFFIX = ".meta";
    private static final int PREFIX_SCAN_LIMIT = 64 * 1024;
    private static final long SESSION_TTL_MS = TimeUnit.MINUTES.toMillis(3);
    private static final long MIN_CACHE_FILE_BYTES = 1;
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PNG_IEND = new byte[]{0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern DASH_BASE_URL = Pattern.compile("(<BaseURL\\b[^>]*>)(.*?)(</BaseURL>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", Pattern.CASE_INSENSITIVE);

    private final OkHttpClient client;
    private final int kernel;
    private final Map<Integer, Session> sessions;
    private final Map<Integer, SessionStats> sessionStats;
    private final Map<String, Target> targets;
    private final AtomicLong nextId;
    private final java.util.Set<String> preloading;
    private ExecutorService preloadExecutor;
    private PlaybackRouteRegistry.Registration routeRegistration;
    private int preloadThreads;
    private volatile int sessionId;
    private volatile boolean started;

    public MpvHlsProxy() {
        this(PlayerSetting.MPV);
    }

    public MpvHlsProxy(int kernel) {
        super("127.0.0.1", 0);
        this.kernel = PlayerSetting.sanitizePlayer(kernel);
        client = OkHttp.player().newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        sessions = new ConcurrentHashMap<>();
        sessionStats = new ConcurrentHashMap<>();
        targets = new ConcurrentHashMap<>();
        nextId = new AtomicLong();
        preloading = ConcurrentHashMap.newKeySet();
    }

    public synchronized String proxy(String url, Map<String, String> headers) throws IOException {
        ensureStarted();
        int id = ++this.sessionId;
        Session session = new Session(url, sanitize(headers), System.currentTimeMillis());
        sessions.put(id, session);
        sessionStats.put(id, new SessionStats());
        pruneExpiredSessions(session.createdAtMs);
        pruneCache();
        String proxyUrl = baseUrl() + "/mpv/index.m3u8?s=" + sessionId;
        SpiderDebug.log(TAG, "enabled session=%d url=%s headers=%s proxy=%s", sessionId, shortUrl(url), session.headers.keySet(), proxyUrl);
        return proxyUrl;
    }

    public synchronized String proxyDash(String url, Map<String, String> headers) throws IOException {
        ensureStarted();
        int id = ++this.sessionId;
        Session session = new Session(url, sanitize(headers), System.currentTimeMillis());
        sessions.put(id, session);
        sessionStats.put(id, new SessionStats());
        pruneExpiredSessions(session.createdAtMs);
        String proxyUrl = baseUrl() + "/mpv/index.mpd?s=" + id;
        SpiderDebug.log(TAG, "dash enabled session=%d url=%s headers=%s proxy=%s", id, shortUrl(url), session.headers.keySet(), proxyUrl);
        return proxyUrl;
    }

    public synchronized void clear() {
        sessions.clear();
        sessionStats.clear();
        targets.clear();
        cancelPreloads();
    }

    public synchronized void release() {
        clear();
        if (started) stop();
        if (routeRegistration != null) routeRegistration.close();
        routeRegistration = null;
        started = false;
    }

    String diagnostics() {
        return "session " + sessionId
                + " / items " + targets.size()
                + " / cache " + formatBytes(cacheBytes())
                + "/" + formatBytes(cacheLimitBytes())
                + " / preload " + preloading.size()
                + " / " + statsText();
    }

    void preloadAround(long positionMs) {
        Session owner = sessions.get(sessionId);
        SessionStats stats = sessionStats.get(sessionId);
        if (owner == null || stats == null || !stats.vod || stats.segments.isEmpty()) return;
        preloadSegments(owner, stats.segments, Math.max(0, positionMs) / 1000.0);
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String path = session.getUri();
            if (path == null) return error(Status.NOT_FOUND, "missing path");
            if (path.startsWith("/mpv/index.m3u8")) return servePlaylist(session);
            if (path.startsWith("/mpv/index.mpd")) return serveDash(session);
            if (path.startsWith("/mpv/dash-item/")) return serveItem(session, dashItemId(path));
            if (path.startsWith("/mpv/item")) return serveItem(session, null);
            return error(Status.NOT_FOUND, "not found");
        } catch (Throwable e) {
            SpiderDebug.log(TAG, e);
            return error(Status.INTERNAL_ERROR, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void ensureStarted() throws IOException {
        if (started) return;
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        routeRegistration = PlaybackRouteRegistry.registerAppService(getListeningPort(), PlaybackRouteRegistry.AppOwner.HLS_PROXY);
        started = true;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + getListeningPort();
    }

    private Response servePlaylist(IHTTPSession httpSession) throws IOException {
        int id = parseSessionId(httpSession);
        Session session = sessions.get(id);
        if (session == null || TextUtils.isEmpty(session.url)) return error(Status.NOT_FOUND, "expired playlist");
        try (okhttp3.Response response = fetch(session, session.url, null, false)) {
            if (!response.isSuccessful()) {
                recordPlaylistResponse(id, response.code(), session.url, null);
                SpiderDebug.log(TAG, "playlist error session=%d code=%d url=%s", id, response.code(), shortUrl(session.url));
                return error(toStatus(response.code()), "playlist http " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return error(Status.INTERNAL_ERROR, "empty playlist body");
            String text = body.string();
            recordPlaylistResponse(id, response.code(), session.url, text);
            if (!looksLikePlaylist(text)) {
                SpiderDebug.log(TAG, "invalid playlist session=%d code=%d bytes=%d url=%s", id, response.code(), text.length(), shortUrl(session.url));
                return error(Status.BAD_REQUEST, "invalid playlist");
            }
            text = applyAdblock(text, id, session.url);
            String rewritten = rewritePlaylist(response.request().url().toString(), text, id);
            byte[] data = rewritten.getBytes(StandardCharsets.UTF_8);
            SpiderDebug.log(TAG, "playlist session=%d code=%d bytes=%d rewritten=%d url=%s", id, response.code(), text.length(), data.length, shortUrl(session.url));
            return noCache(newFixedLengthResponse(Status.OK, MIME_M3U8, new ByteArrayInputStream(data), data.length));
        }
    }

    private Response serveDash(IHTTPSession httpSession) throws IOException {
        int id = parseSessionId(httpSession);
        Session session = sessions.get(id);
        if (session == null || TextUtils.isEmpty(session.url)) return error(Status.NOT_FOUND, "expired dash");
        try (okhttp3.Response response = fetch(session, session.url, null, false)) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return error(toStatus(response.code()), "dash http " + response.code());
            String manifestUrl = response.request().url().toString();
            String text = body.string();
            if (!text.toLowerCase(Locale.US).contains("<mpd")) return error(Status.BAD_REQUEST, "invalid dash");
            text = rewriteSegmentBase(session, manifestUrl, text);
            Matcher matcher = DASH_BASE_URL.matcher(text);
            StringBuffer rewritten = new StringBuffer();
            int count = 0;
            while (matcher.find()) {
                String target = resolve(manifestUrl, decodeXml(matcher.group(2).trim()));
                String local = proxyDashItemUrl(target, id).replace("&", "&amp;");
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group(1) + local + matcher.group(3)));
                count++;
            }
            matcher.appendTail(rewritten);
            byte[] data = rewritten.toString().getBytes(StandardCharsets.UTF_8);
            SpiderDebug.log(TAG, "dash manifest session=%d code=%d bytes=%d baseUrls=%d url=%s", id, response.code(), data.length, count, shortUrl(session.url));
            return noCache(newFixedLengthResponse(Status.OK, "application/dash+xml; charset=utf-8", new ByteArrayInputStream(data), data.length));
        }
    }

    private String rewriteSegmentBase(Session session, String manifestUrl, String text) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
            pruneDashAlternatives(document);
            NodeList representations = document.getElementsByTagNameNS("*", "Representation");
            int converted = 0;
            for (int i = 0; i < representations.getLength(); i++) {
                Element representation = (Element) representations.item(i);
                Element baseUrl = directChild(representation, "BaseURL");
                Element segmentBase = directChild(representation, "SegmentBase");
                Element initialization = segmentBase == null ? null : directChild(segmentBase, "Initialization");
                if (baseUrl == null || segmentBase == null || initialization == null) continue;
                ByteRange index = ByteRange.parse(segmentBase.getAttribute("indexRange"));
                ByteRange init = ByteRange.parse(initialization.getAttribute("range"));
                if (index == null || init == null) continue;
                String target = resolve(manifestUrl, baseUrl.getTextContent().trim());
                Sidx sidx = fetchSidx(session, target, index);
                if (sidx == null || sidx.ranges.isEmpty()) continue;
                List<SidxRange> groups = sidx.grouped(60_000);
                String namespace = segmentBase.getNamespaceURI();
                Element segmentList = document.createElementNS(namespace, "SegmentList");
                segmentList.setAttribute("timescale", Long.toString(sidx.timescale));
                segmentList.setAttribute("duration", Long.toString(groups.get(0).duration));
                Element initNode = document.createElementNS(namespace, "Initialization");
                initNode.setAttribute("range", init.start + "-" + (init.end + 1));
                segmentList.appendChild(initNode);
                for (SidxRange range : groups) {
                    Element segment = document.createElementNS(namespace, "SegmentURL");
                    segment.setAttribute("mediaRange", range.start + "-" + (range.end + 1));
                    segmentList.appendChild(segment);
                }
                representation.replaceChild(segmentList, segmentBase);
                converted++;
            }
            if (converted == 0) return text;
            StringWriter output = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(output));
            SpiderDebug.log(TAG, "dash SegmentBase grouped representations=%d url=%s", converted, shortUrl(manifestUrl));
            return output.toString();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "dash SegmentBase grouping skipped url=%s error=%s", shortUrl(manifestUrl), e.getMessage());
            return text;
        }
    }

    private void pruneDashAlternatives(Document document) {
        NodeList sets = document.getElementsByTagNameNS("*", "AdaptationSet");
        List<Node> remove = new ArrayList<>();
        boolean videoKept = false;
        boolean audioKept = false;
        for (int i = 0; i < sets.getLength(); i++) {
            Element set = (Element) sets.item(i);
            Element representation = directChild(set, "Representation");
            Element component = directChild(set, "ContentComponent");
            String type = component == null ? "" : component.getAttribute("contentType");
            if (TextUtils.isEmpty(type) && representation != null) {
                String mime = representation.getAttribute("mimeType");
                if (mime.startsWith("video/")) type = "video";
                else if (mime.startsWith("audio/")) type = "audio";
            }
            if ("video".equals(type)) {
                if (videoKept) remove.add(set);
                else videoKept = true;
            } else if ("audio".equals(type)) {
                if (audioKept) remove.add(set);
                else audioKept = true;
            }
        }
        for (Node node : remove) if (node.getParentNode() != null) node.getParentNode().removeChild(node);
    }

    @Nullable
    private Sidx fetchSidx(Session session, String url, ByteRange range) throws IOException {
        try (okhttp3.Response response = fetch(session, url, "bytes=" + range.start + "-" + range.end, true)) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return null;
            byte[] data = body.bytes();
            long absoluteStart = response.code() == 206 ? range.start : 0;
            String contentRange = response.header("Content-Range");
            if (contentRange != null) {
                Matcher matcher = CONTENT_RANGE.matcher(contentRange);
                if (matcher.find()) absoluteStart = Long.parseLong(matcher.group(1));
            }
            return Sidx.parse(data, absoluteStart);
        }
    }

    @Nullable
    private static Element directChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private Response serveItem(IHTTPSession httpSession, @Nullable String forcedId) throws IOException {
        String id = forcedId == null ? httpSession.getParms().get("id") : forcedId;
        if (id != null && id.endsWith("/")) id = id.substring(0, id.length() - 1);
        Target target = id == null ? null : targets.get(id);
        Session owner = target == null ? null : sessions.get(target.sessionId);
        if (target == null || owner == null) return error(Status.NOT_FOUND, "expired item");
        String range = requestHeader(httpSession, "range");
        boolean targetPlaylist = isPlaylistUrl(target.url, null);
        String forwardedRange = targetPlaylist ? null : range;
        if (!targetPlaylist && target.cacheable) {
            Response cached = serveCached(owner, target.url, range);
            if (cached != null) {
                SpiderDebug.log(TAG, "cache hit id=%s range=%s url=%s", id, range, shortUrl(target.url));
                return cached;
            }
        }
        okhttp3.Response response = fetch(owner, target.url, forwardedRange, !targetPlaylist);
        ResponseBody body = response.body();
        if (body == null) {
            recordItemResponse(target.sessionId, response.code(), target.url);
            response.close();
            return error(Status.INTERNAL_ERROR, "empty item body");
        }
        String finalUrl = response.request().url().toString();
        MediaType type = body.contentType();
        if (isPlaylistUrl(target.url, type) || isPlaylistUrl(finalUrl, type)) {
            try (response; body) {
                if (!response.isSuccessful()) {
                    recordPlaylistResponse(target.sessionId, response.code(), target.url, null);
                    SpiderDebug.log(TAG, "nested playlist error id=%s code=%d url=%s", id, response.code(), shortUrl(target.url));
                    return error(toStatus(response.code()), "nested playlist http " + response.code());
                }
                String text = body.string();
                recordPlaylistResponse(target.sessionId, response.code(), target.url, text);
                if (!looksLikePlaylist(text)) {
                    SpiderDebug.log(TAG, "invalid nested playlist id=%s code=%d bytes=%d url=%s", id, response.code(), text.length(), shortUrl(target.url));
                    return error(Status.BAD_REQUEST, "invalid playlist");
                }
                text = applyAdblock(text, target.sessionId, target.url);
                String rewritten = rewritePlaylist(finalUrl, text, target.sessionId);
                byte[] data = rewritten.getBytes(StandardCharsets.UTF_8);
                SpiderDebug.log(TAG, "nested playlist id=%s code=%d bytes=%d url=%s", id, response.code(), data.length, shortUrl(target.url));
                return noCache(newFixedLengthResponse(Status.OK, MIME_M3U8, new ByteArrayInputStream(data), data.length));
            }
        }

        boolean mayStripPngPrefix = isPngMime(type);
        recordItemResponse(target.sessionId, response.code(), target.url);
        long contentLength = body.contentLength();
        String mime = mediaMimeFor(target.url, finalUrl, type);
        InputStream source = body.byteStream();
        if (mayStripPngPrefix) {
            source = new PngPrefixStrippingInputStream(source, target.url);
        } else {
            source = maybeCacheStreaming(owner, target, source, response.code(), forwardedRange, response.header("Content-Range"), contentLength, mime);
        }
        InputStream stream = new CloseResponseInputStream(source, response);
        Response.IStatus streamingStatus = streamingStatus(response, forwardedRange);
        Response result = mayStripPngPrefix || contentLength < 0
                ? newChunkedResponse(streamingStatus, mime, stream)
                : newFixedLengthResponse(streamingStatus, mime, stream, contentLength);
        addStreamingHeaders(result, response, forwardedRange);
        SpiderDebug.log(TAG, "item id=%s code=%d range=%s contentRange=%s length=%d mime=%s url=%s",
                id, response.code(), forwardedRange, response.header("Content-Range"), contentLength, mime, shortUrl(target.url));
        return result;
    }

    private okhttp3.Response fetch(Session session, String url, @Nullable String range, boolean identityEncoding) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : session.headers.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || TextUtils.isEmpty(entry.getValue())) continue;
            builder.header(entry.getKey(), entry.getValue());
        }
        if (identityEncoding) builder.header("Accept-Encoding", "identity");
        if (!TextUtils.isEmpty(range)) builder.header("Range", range);
        return client.newCall(builder.build()).execute();
    }

    private String applyAdblock(String text, int session, String url) {
        if (!Setting.isAdblock() || !isVodPlaylist(text)) return text;
        if (HlsAdblockPipeline.isCoreM3u8Proxy(url)) return text;
        try {
            HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(url, text, HlsRuleConfig.getRules(), true);
            if (!TextUtils.equals(outcome.manifest(), text)) {
                SpiderDebug.log(TAG, "adblock filtered session=%d bytes=%d->%d structured=%s legacy=%s url=%s",
                        session, text.length(), outcome.manifest().length(), outcome.structured(), outcome.legacy(), shortUrl(url));
            }
            return outcome.manifest();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "adblock ignored session=%d url=%s error=%s", session, shortUrl(url), e.getMessage());
            return text;
        }
    }

    private String rewritePlaylist(String playlistUrl, String text, int session) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 256);
        List<Segment> segments = new ArrayList<>();
        boolean[] skipVariantLines = lowerVariantLinesToSkip(lines, session);
        boolean pendingByteRange = false;
        double pendingDuration = 0;
        double elapsed = 0;
        for (int i = 0; i < lines.length; i++) {
            if (skipVariantLines != null && skipVariantLines[i]) continue;
            String raw = trimCr(lines[i]);
            String line = raw.trim();
            if (line.startsWith("#") && line.contains("URI=\"")) {
                out.append(rewriteUriAttributes(playlistUrl, raw, session, isCacheableUriAttribute(line)));
                if (line.startsWith("#EXT-X-MAP") && line.toUpperCase(Locale.US).contains("BYTERANGE=")) {
                    stats(session).hasByteRange = true;
                }
            } else if (line.startsWith("#EXTINF:")) {
                pendingDuration = parseExtInfDuration(line);
                out.append(raw);
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                pendingByteRange = true;
                stats(session).hasByteRange = true;
                out.append(raw);
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                String targetUrl = resolve(playlistUrl, line);
                out.append(proxyItemUrl(targetUrl, session, pendingDuration > 0 && !pendingByteRange));
                if (pendingDuration > 0) {
                    segments.add(new Segment(targetUrl, pendingDuration, elapsed, pendingByteRange));
                    elapsed += pendingDuration;
                }
                pendingDuration = 0;
                pendingByteRange = false;
            } else {
                out.append(raw);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        recordPlaylistDetails(session, text, segments);
        return out.toString();
    }

    @Nullable
    private boolean[] lowerVariantLinesToSkip(String[] lines, int session) {
        List<Variant> variants = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = trimCr(lines[i]).trim();
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;
            int uriLine = nextUriLine(lines, i + 1);
            if (uriLine < 0) continue;
            variants.add(new Variant(i, uriLine, variantBandwidth(line), variantWidth(line), variantHeight(line)));
        }
        if (variants.size() <= 1) return null;
        Variant best = null;
        for (Variant variant : variants) if (best == null || variant.betterThan(best)) best = variant;
        if (best == null || best.score() <= 0) return null;
        boolean[] skip = new boolean[lines.length];
        for (Variant variant : variants) {
            if (variant == best) continue;
            skip[variant.tagLine] = true;
            skip[variant.uriLine] = true;
        }
        SpiderDebug.log(TAG, "master playlist select highest variant session=%d variants=%d bandwidth=%d resolution=%dx%d", session, variants.size(), best.bandwidth, best.width, best.height);
        return skip;
    }

    private int nextUriLine(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            String line = trimCr(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("#")) return i;
            if (line.startsWith("#EXT-X-STREAM-INF")) return -1;
        }
        return -1;
    }

    private long variantBandwidth(String line) {
        long average = parseLongAttribute(line, "AVERAGE-BANDWIDTH");
        return average > 0 ? average : parseLongAttribute(line, "BANDWIDTH");
    }

    private int variantWidth(String line) {
        return variantResolution(line)[0];
    }

    private int variantHeight(String line) {
        return variantResolution(line)[1];
    }

    private int[] variantResolution(String line) {
        String value = attributeValue(line, "RESOLUTION");
        if (TextUtils.isEmpty(value)) return new int[]{0, 0};
        String[] parts = value.toLowerCase(Locale.US).split("x", 2);
        if (parts.length < 2) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Throwable ignored) {
            return new int[]{0, 0};
        }
    }

    private long parseLongAttribute(String line, String name) {
        String value = attributeValue(line, name);
        if (TextUtils.isEmpty(value)) return 0;
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Nullable
    private String attributeValue(String line, String name) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon >= line.length() - 1) return null;
        for (String part : line.substring(colon + 1).split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String key = part.substring(0, equals).trim();
            if (!name.equalsIgnoreCase(key)) continue;
            String value = part.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            return value;
        }
        return null;
    }

    private boolean isCacheableUriAttribute(String line) {
        if (!line.startsWith("#EXT-X-MAP")) return false;
        return !line.toUpperCase(Locale.US).contains("BYTERANGE=");
    }

    private String rewriteUriAttributes(String playlistUrl, String line, int session, boolean cacheable) {
        Matcher matcher = URI_ATTR.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            String replacement = value;
            if (!TextUtils.isEmpty(value) && !value.startsWith("data:")) {
                replacement = proxyItemUrl(resolve(playlistUrl, value), session, cacheable);
            }
            matcher.appendReplacement(buffer, "URI=\"" + Matcher.quoteReplacement(replacement) + "\"");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String proxyItemUrl(String targetUrl, int session, boolean cacheable) {
        String id = Long.toString(nextId.incrementAndGet());
        targets.put(id, new Target(session, targetUrl, System.currentTimeMillis(), cacheable));
        return baseUrl() + "/mpv/item?s=" + session + "&id=" + id;
    }

    private String proxyDashItemUrl(String targetUrl, int session) {
        String id = Long.toString(nextId.incrementAndGet());
        targets.put(id, new Target(session, targetUrl, System.currentTimeMillis(), true));
        return baseUrl() + "/mpv/dash-item/" + id + "/media.m4s";
    }

    @Nullable
    private String dashItemId(String path) {
        String prefix = "/mpv/dash-item/";
        if (path == null || !path.startsWith(prefix)) return null;
        String value = path.substring(prefix.length());
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    private String decodeXml(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    @Nullable
    private Response serveCached(Session session, String url, @Nullable String rangeHeader) throws IOException {
        if (!isCacheEnabled()) return null;
        File file = cacheFile(session, url);
        if (!file.isFile() || file.length() < MIN_CACHE_FILE_BYTES) return null;
        long length = file.length();
        Range range = parseRange(rangeHeader, length);
        if (rangeHeader != null && range == null) {
            Response response = error(Status.RANGE_NOT_SATISFIABLE, "invalid range");
            response.addHeader("Content-Range", "bytes */" + length);
            return response;
        }
        long start = range == null ? 0 : range.start;
        long end = range == null ? length - 1 : range.end;
        FileInputStream input = new FileInputStream(file);
        skipFully(input, start);
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(System.currentTimeMillis());
        Response response = newFixedLengthResponse(range == null ? Status.OK : Status.PARTIAL_CONTENT, cacheMime(url, file), new LimitedInputStream(input, end - start + 1), end - start + 1);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "close");
        response.addHeader("Accept-Ranges", "bytes");
        if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        return response;
    }

    private InputStream maybeCacheStreaming(Session session, Target target, InputStream source, int status, @Nullable String range, @Nullable String contentRange, long contentLength, String mime) {
        if (!shouldWriteThroughCache(target, status, range, contentRange, contentLength)) return source;
        File file = cacheFile(session, target.url);
        if (file.isFile() && file.length() >= MIN_CACHE_FILE_BYTES) return source;
        String key = file.getName();
        if (!preloading.add(key)) return source;
        File dir = cacheDir();
        if (!dir.exists() && !dir.mkdirs()) {
            preloading.remove(key);
            return source;
        }
        File temp = tempFile(dir, file);
        try {
            return new CacheWritingInputStream(source, new FileOutputStream(temp), temp, file, key, target.url, contentLength, mime);
        } catch (Throwable e) {
            preloading.remove(key);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            SpiderDebug.log(TAG, "stream cache open failed url=%s error=%s", shortUrl(target.url), e.getMessage());
            return source;
        }
    }

    private boolean shouldWriteThroughCache(Target target, int status, @Nullable String range, @Nullable String contentRange, long contentLength) {
        if (target == null || !target.cacheable) return false;
        if (!isCacheEnabled() || !stats(target.sessionId).vod) return false;
        if (!isHttpUrl(target.url)) return false;
        if (contentLength < MIN_CACHE_FILE_BYTES) return false;
        if (contentLength > cacheLimitBytes()) return false;
        if (status == 200) return true;
        return status == 206 && isCompleteRangeResponse(range, contentRange, contentLength);
    }

    private void preloadSegments(Session session, List<Segment> segments, double startSeconds) {
        if (!PreloadSetting.isPreload(kernel) || !isCacheEnabled() || segments.isEmpty()) return;
        double seconds = 0;
        for (Segment segment : segments) {
            if (segment.endSeconds() <= startSeconds) continue;
            if (segment.startSeconds <= startSeconds && segment.endSeconds() > startSeconds) continue;
            if (segment.byteRange) continue;
            if (seconds >= PreloadSetting.getPreloadTimeSeconds(kernel)) break;
            seconds += Math.max(0, segment.durationSeconds);
            preloadSegment(session, segment.url);
        }
    }

    private void preloadSegment(Session session, String url) {
        if (!isHttpUrl(url)) return;
        File file = cacheFile(session, url);
        if (file.isFile() && file.length() > 0) return;
        String key = file.getName();
        if (!preloading.add(key)) return;
        getPreloadExecutor().execute(() -> {
            try {
                prefetchToCache(session, url, file);
            } catch (Throwable e) {
                SpiderDebug.log(TAG, "preload failed url=%s error=%s", shortUrl(url), e.getMessage());
            } finally {
                preloading.remove(key);
            }
        });
    }

    private synchronized ExecutorService getPreloadExecutor() {
        int threads = PreloadSetting.getPreloadThreads(kernel);
        if (preloadExecutor != null && preloadThreads == threads) return preloadExecutor;
        releasePreloadExecutor();
        preloadThreads = threads;
        return preloadExecutor = Executors.newFixedThreadPool(threads);
    }

    private synchronized void cancelPreloads() {
        releasePreloadExecutor();
        preloading.clear();
    }

    private synchronized void releasePreloadExecutor() {
        if (preloadExecutor == null) return;
        preloadExecutor.shutdownNow();
        preloadExecutor = null;
    }

    private void prefetchToCache(Session session, String url, File file) throws IOException {
        if (!isCacheEnabled() || (file.isFile() && file.length() > 0)) return;
        try (okhttp3.Response response = fetch(session, url, null, true)) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null || isPlaylistUrl(url, body.contentType()) || isPngMime(body.contentType())) return;
            long contentLength = body.contentLength();
            if (contentLength < MIN_CACHE_FILE_BYTES || contentLength > cacheLimitBytes()) return;
            writeCacheFile(body.byteStream(), file, contentLength, mediaMimeFromUrl(url, body.contentType()));
            SpiderDebug.log(TAG, "preload cached length=%d url=%s", contentLength, shortUrl(url));
        }
    }

    private void writeCacheFile(InputStream input, File file, long expectedLength, String mime) throws IOException {
        File dir = cacheDir();
        if (!dir.exists() && !dir.mkdirs()) return;
        File temp = tempFile(dir, file);
        long written = 0;
        try (InputStream in = input; OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (written > cacheLimitBytes()) throw new IOException("cache item exceeds limit");
            }
        } catch (Throwable e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            if (e instanceof IOException io) throw io;
            throw new IOException(e);
        }
        if (expectedLength > 0 && written != expectedLength) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        commitCacheFile(temp, file, mime);
        pruneCache();
    }

    private File cacheFile(Session session, String url) {
        return new File(cacheDir(), Util.md5(url + "\n" + session.headers) + CACHE_FILE_SUFFIX);
    }

    private File cacheDir() {
        return Path.cache("mpv_hls");
    }

    private boolean isCacheEnabled() {
        return cacheLimitBytes() > 0;
    }

    private long cacheLimitBytes() {
        long playCache = Math.max(0, PlayerSetting.getPlayCacheSize(kernel));
        long preloadCache = PreloadSetting.isPreload(kernel) ? Math.max(0, PreloadSetting.getPreloadSizeBytes(kernel)) : playCache;
        return Math.max(0, Math.min(playCache, preloadCache));
    }

    private void pruneCache() {
        File dir = cacheDir();
        if (!dir.exists() && !dir.mkdirs()) return;
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(CACHE_FILE_SUFFIX));
        if (files == null || files.length == 0) return;
        long total = 0;
        for (File file : files) total += Math.max(0, file.length());
        long limit = cacheLimitBytes();
        if (limit <= 0 || total <= limit) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= limit) break;
            long length = Math.max(0, file.length());
            if (file.delete()) {
                deleteMeta(file);
                total -= length;
            }
        }
    }

    private long cacheBytes() {
        File[] files = cacheDir().listFiles(file -> file.isFile() && file.getName().endsWith(CACHE_FILE_SUFFIX));
        long total = 0;
        if (files != null) for (File file : files) total += Math.max(0, file.length());
        return total;
    }

    private int parseSessionId(IHTTPSession session) {
        try {
            return Integer.parseInt(session.getParms().get("s"));
        } catch (Throwable e) {
            return sessionId;
        }
    }

    private void pruneExpiredSessions(long now) {
        for (Map.Entry<Integer, Session> entry : sessions.entrySet()) {
            if (entry.getKey() == sessionId) continue;
            if (now - entry.getValue().createdAtMs > SESSION_TTL_MS) {
                sessions.remove(entry.getKey());
                sessionStats.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            Target target = entry.getValue();
            if (sessions.containsKey(target.sessionId)) continue;
            if (now - target.createdAtMs > SESSION_TTL_MS) targets.remove(entry.getKey());
        }
    }

    private void recordPlaylistResponse(int session, int status, String url, @Nullable String text) {
        SessionStats stats = stats(session);
        stats.seenPlaylist = true;
        stats.playlistRequests++;
        recordStatus(stats, status, url);
        if (isVodPlaylist(text)) stats.vod = true;
    }

    private void recordPlaylistDetails(int session, String text, List<Segment> segments) {
        SessionStats stats = stats(session);
        stats.vod = isVodPlaylist(text);
        if (stats.vod && !segments.isEmpty()) stats.segments = List.copyOf(segments);
        else if (!stats.vod) stats.segments = List.of();
    }

    private void recordItemResponse(int session, int status, String url) {
        SessionStats stats = stats(session);
        stats.itemRequests++;
        recordStatus(stats, status, url);
    }

    private SessionStats stats(int session) {
        SessionStats stats = sessionStats.get(session);
        if (stats != null) return stats;
        stats = new SessionStats();
        SessionStats existing = sessionStats.putIfAbsent(session, stats);
        return existing == null ? stats : existing;
    }

    private void recordStatus(SessionStats stats, int status, String url) {
        stats.lastStatus = status;
        stats.lastUrl = url;
        if (status >= 400) stats.lastErrorAtMs = System.currentTimeMillis();
    }

    private String statsText() {
        SessionStats stats = sessionStats.get(sessionId);
        if (stats == null) return "playlist -";
        String type = stats.vod ? "vod" : stats.seenPlaylist ? "live" : "-";
        String status = stats.lastStatus <= 0 ? "-" : String.valueOf(stats.lastStatus);
        return "playlist " + type + " p" + stats.playlistRequests + "/i" + stats.itemRequests + " last " + status + (stats.hasByteRange ? " byterange" : "");
    }

    private static boolean isVodPlaylist(String text) {
        return text != null && text.toUpperCase(Locale.US).contains("#EXT-X-ENDLIST");
    }

    private static double parseExtInfDuration(String line) {
        try {
            int colon = line.indexOf(':');
            int comma = line.indexOf(',', colon + 1);
            String value = comma >= 0 ? line.substring(colon + 1, comma) : line.substring(colon + 1);
            return Double.parseDouble(value.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isHttpUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    @Nullable
    private static Range parseRange(@Nullable String header, long length) {
        if (TextUtils.isEmpty(header)) return null;
        String value = header.trim().toLowerCase(Locale.US);
        if (!value.startsWith("bytes=") || length <= 0) return null;
        String spec = value.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        try {
            long start;
            long end;
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) return null;
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? length - 1 : Long.parseLong(right);
            }
            if (start < 0 || end < start || start >= length) return null;
            return new Range(start, Math.min(end, length - 1));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isCompleteRangeResponse(@Nullable String range, @Nullable String contentRange, long contentLength) {
        if (TextUtils.isEmpty(range) || TextUtils.isEmpty(contentRange) || contentLength <= 0) return false;
        String value = range.trim().toLowerCase(Locale.US);
        if (!value.startsWith("bytes=0-")) return false;
        Matcher matcher = CONTENT_RANGE.matcher(contentRange.trim());
        if (!matcher.matches()) return false;
        try {
            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            String totalText = matcher.group(3);
            if ("*".equals(totalText)) return false;
            long total = Long.parseLong(totalText);
            return start == 0 && end >= start && end + 1 == total && total == contentLength;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void skipFully(InputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) throw new IOException("unexpected EOF");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "-";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0);
        return String.format(Locale.US, "%.1fGB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private String resolve(String baseUrl, String uri) {
        try {
            URI parsed = URI.create(uri);
            if (parsed.isAbsolute()) return uri;
            return URI.create(baseUrl).resolve(parsed).toString();
        } catch (Throwable e) {
            return uri;
        }
    }

    private static String trimCr(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isPlaylistUrl(String url, @Nullable MediaType type) {
        String mime = type == null ? "" : type.toString().toLowerCase(Locale.US);
        if (mime.contains("mpegurl") || mime.contains("m3u8")) return true;
        String lower = url == null ? "" : url.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u");
    }

    private static boolean looksLikePlaylist(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String value = text.trim();
        return value.startsWith("#EXTM3U") || value.contains("\n#EXTM3U");
    }

    private static String mediaMime(@Nullable MediaType type) {
        String value = type == null ? "" : type.toString();
        if (isPngMime(type)) return MIME_TS;
        return TextUtils.isEmpty(value) ? MIME_BINARY : value;
    }

    private static String mediaMimeFor(String targetUrl, String finalUrl, @Nullable MediaType type) {
        String mime = mediaMimeFromUrl(finalUrl, type);
        return MIME_BINARY.equals(mime) ? mediaMimeFromUrl(targetUrl, type) : mime;
    }

    private static String mediaMimeFromUrl(String url, @Nullable MediaType type) {
        String value = mediaMime(type);
        if (!MIME_BINARY.equals(value)) return value;
        String lower = stripQuery(url).toLowerCase(Locale.US);
        if (lower.endsWith(".ts") || lower.endsWith(".m2ts")) return MIME_TS;
        if (lower.endsWith(".mp4") || lower.endsWith(".m4s") || lower.endsWith(".m4v") || lower.endsWith(".cmfv")) return "video/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a") || lower.endsWith(".cmfa")) return "audio/mp4";
        if (lower.endsWith(".vtt") || lower.endsWith(".webvtt")) return "text/vtt";
        if (lower.endsWith(".srt")) return "application/x-subrip";
        return MIME_BINARY;
    }

    private String cacheMime(String url, File file) {
        File meta = metaFile(file);
        if (meta.isFile() && meta.length() > 0 && meta.length() < 256) {
            try (InputStream input = new FileInputStream(meta)) {
                byte[] data = new byte[(int) meta.length()];
                int read = input.read(data);
                String value = read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8).trim();
                if (!TextUtils.isEmpty(value)) return value;
            } catch (Throwable ignored) {
            }
        }
        return mediaMimeFromUrl(url, null);
    }

    private void writeCacheMeta(File file, String mime) throws IOException {
        if (TextUtils.isEmpty(mime)) mime = MIME_BINARY;
        try (OutputStream out = new FileOutputStream(metaFile(file))) {
            out.write(mime.getBytes(StandardCharsets.UTF_8));
        }
    }

    private File metaFile(File file) {
        return new File(file.getParentFile(), file.getName() + CACHE_META_SUFFIX);
    }

    private void deleteMeta(File file) {
        //noinspection ResultOfMethodCallIgnored
        metaFile(file).delete();
    }

    private File tempFile(File dir, File file) {
        return new File(dir, file.getName() + "." + Thread.currentThread().getId() + "." + System.nanoTime() + ".tmp");
    }

    private void commitCacheFile(File temp, File file, String mime) throws IOException {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        deleteMeta(file);
        if (!temp.renameTo(file)) throw new IOException("rename cache failed");
        try {
            writeCacheMeta(file, mime);
        } catch (IOException ignored) {
        }
    }

    private static String stripQuery(String url) {
        String value = url == null ? "" : url;
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        return value;
    }

    @Nullable
    private static String requestHeader(IHTTPSession session, String name) {
        if (session == null || session.getHeaders() == null) return null;
        for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static void addStreamingHeaders(Response result, okhttp3.Response upstream, @Nullable String range) {
        result.addHeader("Access-Control-Allow-Origin", "*");
        result.addHeader("Cache-Control", "no-cache");
        result.addHeader("Connection", "close");
        copyHeader(result, upstream, "Content-Range");
        copyHeader(result, upstream, "ETag");
        copyHeader(result, upstream, "Last-Modified");
        String acceptRanges = upstream.header("Accept-Ranges");
        if (!TextUtils.isEmpty(acceptRanges)) {
            result.addHeader("Accept-Ranges", acceptRanges);
        } else if (!TextUtils.isEmpty(range) || upstream.code() == 206) {
            result.addHeader("Accept-Ranges", "bytes");
        }
    }

    private static void copyHeader(Response result, okhttp3.Response upstream, String name) {
        String value = upstream.header(name);
        if (!TextUtils.isEmpty(value)) result.addHeader(name, value);
    }

    private static boolean isPngMime(@Nullable MediaType type) {
        String value = type == null ? "" : type.toString();
        return value.toLowerCase(Locale.US).contains("image/png");
    }

    private static Response noCache(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "close");
        return response;
    }

    private static Response error(Response.IStatus status, String text) {
        return noCache(newFixedLengthResponse(status, MIME_PLAINTEXT, text == null ? "" : text));
    }

    private static Response.IStatus toStatus(int code) {
        Status status = Status.lookup(code);
        return status != null ? status : Status.OK;
    }

    private static Response.IStatus streamingStatus(okhttp3.Response upstream, @Nullable String requestedRange) {
        if (!TextUtils.isEmpty(requestedRange) && !TextUtils.isEmpty(upstream.header("Content-Range"))) {
            return Status.PARTIAL_CONTENT;
        }
        return toStatus(upstream.code());
    }

    private static Map<String, String> sanitize(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null) return result;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || TextUtils.isEmpty(entry.getValue())) continue;
            result.put(entry.getKey().trim(), entry.getValue().trim());
        }
        return result;
    }

    private static String shortUrl(String value) {
        if (value == null || value.length() <= 120) return value;
        return value.substring(0, 120) + "...";
    }

    private record Session(String url, Map<String, String> headers, long createdAtMs) {
    }

    private record Target(int sessionId, String url, long createdAtMs, boolean cacheable) {
    }

    private record Segment(String url, double durationSeconds, double startSeconds, boolean byteRange) {
        double endSeconds() {
            return startSeconds + durationSeconds;
        }
    }

    private record Variant(int tagLine, int uriLine, long bandwidth, int width, int height) {
        long area() {
            return (long) width * height;
        }

        long score() {
            return Math.max(bandwidth, area());
        }

        boolean betterThan(Variant other) {
            if (bandwidth != other.bandwidth) return bandwidth > other.bandwidth;
            return area() > other.area();
        }
    }

    private record Range(long start, long end) {
    }

    private final class CacheWritingInputStream extends FilterInputStream {

        private final File temp;
        private final File file;
        private final String key;
        private final String url;
        private final long expectedLength;
        private final String mime;
        private OutputStream cache;
        private long written;
        private boolean completed;
        private boolean released;

        CacheWritingInputStream(InputStream in, OutputStream cache, File temp, File file, String key, String url, long expectedLength, String mime) {
            super(in);
            this.cache = cache;
            this.temp = temp;
            this.file = file;
            this.key = key;
            this.url = url;
            this.expectedLength = expectedLength;
            this.mime = mime;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) {
                finishCache();
            } else {
                writeCacheByte(value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read == -1) {
                finishCache();
            } else if (read > 0) {
                writeCache(buffer, offset, read);
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (!completed) abortCache();
            }
        }

        private void writeCacheByte(int value) {
            OutputStream out = cache;
            if (out == null) return;
            try {
                out.write(value);
                written++;
                checkCacheProgress();
            } catch (Throwable e) {
                disableCache(e.getMessage());
            }
        }

        private void writeCache(byte[] buffer, int offset, int length) {
            OutputStream out = cache;
            if (out == null) return;
            try {
                out.write(buffer, offset, length);
                written += length;
                checkCacheProgress();
            } catch (Throwable e) {
                disableCache(e.getMessage());
            }
        }

        private void checkCacheProgress() {
            if (expectedLength > 0 && written > expectedLength) {
                disableCache("item exceeds expected length");
            } else if (written > cacheLimitBytes()) {
                disableCache("item exceeds limit");
            } else if (expectedLength > 0 && written == expectedLength) {
                finishCache();
            }
        }

        private void finishCache() {
            if (completed) return;
            completed = true;
            OutputStream out = cache;
            cache = null;
            try {
                if (out != null) out.close();
                if (expectedLength > 0 && written != expectedLength) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                    SpiderDebug.log(TAG, "stream cache discard length=%d expected=%d url=%s", written, expectedLength, shortUrl(url));
                    return;
                }
                commitCacheFile(temp, file, mime);
                pruneCache();
                SpiderDebug.log(TAG, "stream cached length=%d url=%s", written, shortUrl(url));
            } catch (Throwable e) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                SpiderDebug.log(TAG, "stream cache commit failed url=%s error=%s", shortUrl(url), e.getMessage());
            } finally {
                releaseKey();
            }
        }

        private void disableCache(String reason) {
            OutputStream out = cache;
            cache = null;
            closeQuietly(out);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            releaseKey();
            SpiderDebug.log(TAG, "stream cache disabled url=%s error=%s", shortUrl(url), reason);
        }

        private void abortCache() {
            OutputStream out = cache;
            cache = null;
            closeQuietly(out);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            releaseKey();
        }

        private void releaseKey() {
            if (released) return;
            released = true;
            preloading.remove(key);
        }

        private void closeQuietly(@Nullable OutputStream out) {
            if (out == null) return;
            try {
                out.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private record ByteRange(long start, long end) {

        @Nullable
        private static ByteRange parse(String value) {
            if (TextUtils.isEmpty(value)) return null;
            int separator = value.indexOf('-');
            if (separator <= 0 || separator >= value.length() - 1) return null;
            try {
                long start = Long.parseLong(value.substring(0, separator).trim());
                long end = Long.parseLong(value.substring(separator + 1).trim());
                return start >= 0 && end >= start ? new ByteRange(start, end) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private record SidxRange(long start, long end, long duration) {
    }

    private static final class Sidx {

        private final long timescale;
        private final List<SidxRange> ranges;

        private Sidx(long timescale, List<SidxRange> ranges) {
            this.timescale = timescale;
            this.ranges = ranges;
        }

        private List<SidxRange> grouped(long targetMs) {
            long target = Math.max(1, targetMs * timescale / 1000);
            List<SidxRange> result = new ArrayList<>();
            long start = -1;
            long end = -1;
            long duration = 0;
            for (SidxRange range : ranges) {
                if (start < 0) start = range.start;
                end = range.end;
                duration += range.duration;
                if (duration >= target) {
                    result.add(new SidxRange(start, end, duration));
                    start = -1;
                    end = -1;
                    duration = 0;
                }
            }
            if (start >= 0) result.add(new SidxRange(start, end, duration));
            return result;
        }

        @Nullable
        private static Sidx parse(byte[] data, long absoluteStart) {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            for (int position = 0; position + 8 <= data.length; ) {
                long size = uint32(buffer, position);
                int headerSize = 8;
                if (size == 1) {
                    if (position + 16 > data.length) return null;
                    size = buffer.getLong(position + 8);
                    headerSize = 16;
                } else if (size == 0) {
                    size = data.length - position;
                }
                if (size < headerSize || size > data.length - position) return null;
                if (buffer.getInt(position + 4) == 0x73696478) return parseBox(buffer, position, size, headerSize, absoluteStart + position);
                position += (int) size;
            }
            return null;
        }

        @Nullable
        private static Sidx parseBox(ByteBuffer buffer, int position, long boxSize, int headerSize, long absoluteBoxStart) {
            int cursor = position + headerSize;
            int limit = position + (int) boxSize;
            if (cursor + 20 > limit) return null;
            int version = buffer.get(cursor) & 0xFF;
            cursor += 8;
            long timescale = uint32(buffer, cursor);
            cursor += 4;
            if (timescale <= 0) return null;
            long firstOffset;
            if (version == 0) {
                if (cursor + 8 > limit) return null;
                cursor += 4;
                firstOffset = uint32(buffer, cursor);
                cursor += 4;
            } else if (version == 1) {
                if (cursor + 16 > limit) return null;
                cursor += 8;
                firstOffset = buffer.getLong(cursor);
                cursor += 8;
            } else return null;
            if (firstOffset < 0 || cursor + 4 > limit) return null;
            cursor += 2;
            int count = buffer.getShort(cursor) & 0xFFFF;
            cursor += 2;
            long segmentStart = absoluteBoxStart + boxSize + firstOffset;
            List<SidxRange> ranges = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                if (cursor + 12 > limit) return null;
                long reference = uint32(buffer, cursor);
                cursor += 4;
                long size = reference & 0x7FFFFFFFL;
                long duration = uint32(buffer, cursor);
                cursor += 8;
                if ((reference & 0x80000000L) == 0 && size > 0) ranges.add(new SidxRange(segmentStart, segmentStart + size - 1, duration));
                segmentStart += size;
            }
            return ranges.isEmpty() ? null : new Sidx(timescale, ranges);
        }

        private static long uint32(ByteBuffer buffer, int offset) {
            return buffer.getInt(offset) & 0xFFFFFFFFL;
        }
    }

    private static final class SessionStats {
        private volatile boolean seenPlaylist;
        private volatile boolean vod;
        private volatile boolean hasByteRange;
        private volatile int playlistRequests;
        private volatile int itemRequests;
        private volatile int lastStatus;
        private volatile long lastErrorAtMs;
        private volatile String lastUrl;
        private volatile List<Segment> segments = List.of();
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        LimitedInputStream(InputStream in, long length) {
            super(in);
            remaining = Math.max(0, length);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int value = super.read();
            if (value != -1) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) return -1;
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read != -1) remaining -= read;
            return read;
        }
    }

    private static final class CloseResponseInputStream extends FilterInputStream {

        private final okhttp3.Response response;

        CloseResponseInputStream(InputStream in, okhttp3.Response response) {
            super(in);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }

    private static final class PngPrefixStrippingInputStream extends InputStream {

        private final InputStream upstream;
        private final String url;
        private byte[] prefix;
        private int prefixOffset;
        private int prefixLength;
        private boolean initialized;

        PngPrefixStrippingInputStream(InputStream upstream, String url) {
            this.upstream = upstream;
            this.url = url;
        }

        @Override
        public int read() throws IOException {
            ensureInitialized();
            if (prefixOffset < prefixLength) return prefix[prefixOffset++] & 0xFF;
            return upstream.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            ensureInitialized();
            if (prefixOffset < prefixLength) {
                int count = Math.min(length, prefixLength - prefixOffset);
                System.arraycopy(prefix, prefixOffset, buffer, offset, count);
                prefixOffset += count;
                return count;
            }
            return upstream.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            upstream.close();
        }

        private void ensureInitialized() throws IOException {
            if (initialized) return;
            initialized = true;
            prefix = readPrefix();
            prefixLength = prefix.length;
            int stripOffset = pngStripOffset(prefix);
            if (stripOffset > 0 && stripOffset < prefixLength && looksLikeTransportStream(prefix, stripOffset)) {
                prefixOffset = stripOffset;
                SpiderDebug.log(TAG, "strip png prefix offset=%d prefixBytes=%d url=%s", stripOffset, prefixLength, shortUrl(url));
            } else {
                prefixOffset = 0;
            }
        }

        private byte[] readPrefix() throws IOException {
            byte[] buffer = new byte[PREFIX_SCAN_LIMIT];
            int length = 0;
            while (length < buffer.length) {
                int read = upstream.read(buffer, length, buffer.length - length);
                if (read == -1) break;
                length += read;
                if (length >= PNG_SIGNATURE.length && !startsWith(buffer, length, PNG_SIGNATURE)) break;
                int offset = pngStripOffset(buffer, length);
                if (offset > 0 && length > offset + 188) break;
            }
            byte[] result = new byte[length];
            System.arraycopy(buffer, 0, result, 0, length);
            return result;
        }

        private static int pngStripOffset(byte[] data) {
            return pngStripOffset(data, data.length);
        }

        private static int pngStripOffset(byte[] data, int length) {
            if (!startsWith(data, length, PNG_SIGNATURE)) return -1;
            int iend = indexOf(data, length, PNG_IEND);
            return iend < 0 ? -1 : iend + PNG_IEND.length;
        }

        private static boolean looksLikeTransportStream(byte[] data, int offset) {
            if (offset >= data.length || data[offset] != 0x47) return false;
            if (offset + 188 < data.length && data[offset + 188] == 0x47) return true;
            if (offset + 376 < data.length && data[offset + 376] == 0x47) return true;
            return true;
        }

        private static boolean startsWith(byte[] data, int length, byte[] prefix) {
            if (length < prefix.length) return false;
            for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
            return true;
        }

        private static int indexOf(byte[] data, int length, byte[] needle) {
            int end = length - needle.length;
            for (int i = 0; i <= end; i++) {
                boolean match = true;
                for (int j = 0; j < needle.length; j++) {
                    if (data[i + j] != needle[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) return i;
            }
            return -1;
        }
    }
}
