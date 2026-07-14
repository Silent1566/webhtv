package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.util.Base64;
import android.util.Xml;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MpdEdlResolver {

    private MpdEdlResolver() {
    }

    public static String resolve(String manifestUrl, Map<String, String> headers) {
        try {
            String xml = readManifest(manifestUrl, headers);
            if (xml.isEmpty()) return manifestUrl;
            Streams streams = parse(xml, manifestUrl);
            if (streams.video == null || streams.audio == null) {
                SpiderDebug.log("mpd-edl", "skip missing streams video=%s audio=%s", streams.video != null, streams.audio != null);
                return manifestUrl;
            }
            String edl = "edl://file=" + value(streams.video.url) + ";!new_stream;file=" + value(streams.audio.url);
            SpiderDebug.log("mpd-edl", "resolved videoCodec=%s videoBandwidth=%d audioCodec=%s audioBandwidth=%d edlLen=%d",
                    streams.video.codec, streams.video.bandwidth, streams.audio.codec, streams.audio.bandwidth, edl.length());
            return edl;
        } catch (Throwable e) {
            SpiderDebug.log("mpd-edl", "resolve failed error=%s", e.getMessage());
            return manifestUrl;
        }
    }

    private static String readManifest(String url, Map<String, String> headers) throws Exception {
        if (url.regionMatches(true, 0, "data:application/dash+xml", 0, "data:application/dash+xml".length())) {
            int comma = url.indexOf(',');
            if (comma < 0) return "";
            String meta = url.substring(0, comma).toLowerCase(Locale.US);
            String data = url.substring(comma + 1);
            return meta.contains(";base64")
                    ? new String(Base64.decode(data, Base64.DEFAULT), StandardCharsets.UTF_8)
                    : Uri.decode(data);
        }
        try (Response response = OkHttp.newCall(OkHttp.player(), url, headers).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return "";
            return body.string();
        }
    }

    private static Streams parse(String xml, String manifestUrl) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        Streams streams = new Streams();
        Candidate current = null;
        String adaptationType = "";
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("AdaptationSet".equalsIgnoreCase(name)) {
                    adaptationType = mediaType(parser.getAttributeValue(null, "contentType"), parser.getAttributeValue(null, "mimeType"));
                } else if ("ContentComponent".equalsIgnoreCase(name) && adaptationType.isEmpty()) {
                    adaptationType = mediaType(parser.getAttributeValue(null, "contentType"), null);
                } else if ("Representation".equalsIgnoreCase(name)) {
                    current = new Candidate();
                    current.type = mediaType(parser.getAttributeValue(null, "contentType"), parser.getAttributeValue(null, "mimeType"));
                    if (current.type.isEmpty()) current.type = adaptationType;
                    current.codec = text(parser.getAttributeValue(null, "codecs"));
                    current.bandwidth = number(parser.getAttributeValue(null, "bandwidth"));
                    current.height = number(parser.getAttributeValue(null, "height"));
                } else if ("BaseURL".equalsIgnoreCase(name) && current != null) {
                    current.url = absolute(manifestUrl, parser.nextText().trim());
                    streams.offer(current);
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("Representation".equalsIgnoreCase(parser.getName())) current = null;
                else if ("AdaptationSet".equalsIgnoreCase(parser.getName())) adaptationType = "";
            }
            event = parser.next();
        }
        return streams;
    }

    private static String mediaType(String contentType, String mimeType) {
        String value = text(contentType).toLowerCase(Locale.US);
        if ("video".equals(value) || "audio".equals(value)) return value;
        value = text(mimeType).toLowerCase(Locale.US);
        if (value.startsWith("video/")) return "video";
        if (value.startsWith("audio/")) return "audio";
        return "";
    }

    private static String absolute(String manifestUrl, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        try {
            if (manifestUrl.startsWith("http")) return URI.create(manifestUrl).resolve(value).toString();
        } catch (Throwable ignored) {
        }
        return value;
    }

    private static String value(String url) {
        return "%" + url.getBytes(StandardCharsets.UTF_8).length + "%" + url;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final class Streams {
        private Candidate video;
        private Candidate audio;

        private void offer(Candidate item) {
            if (item.url == null || item.url.isEmpty()) return;
            if ("video".equals(item.type) && betterVideo(item, video)) video = item.copy();
            if ("audio".equals(item.type) && (audio == null || item.bandwidth > audio.bandwidth)) audio = item.copy();
        }

        private boolean betterVideo(Candidate next, Candidate current) {
            if (current == null) return true;
            int nextCodec = codecPriority(next.codec);
            int currentCodec = codecPriority(current.codec);
            if (nextCodec != currentCodec) return nextCodec > currentCodec;
            if (next.height != current.height) return next.height > current.height;
            return next.bandwidth > current.bandwidth;
        }

        private int codecPriority(String codec) {
            String value = text(codec).toLowerCase(Locale.US);
            if (value.startsWith("avc") || value.startsWith("h264")) return 3;
            if (value.startsWith("hev") || value.startsWith("hvc")) return 2;
            if (value.startsWith("av01") || value.startsWith("av1")) return 1;
            return 0;
        }
    }

    private static final class Candidate {
        private String type;
        private String codec;
        private String url;
        private int bandwidth;
        private int height;

        private Candidate copy() {
            Candidate item = new Candidate();
            item.type = type;
            item.codec = codec;
            item.url = url;
            item.bandwidth = bandwidth;
            item.height = height;
            return item;
        }
    }
}
