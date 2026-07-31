package com.fongmi.android.tv.web;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class WebThemeManifestLoader {

    private static final int MAX_CACHE_ENTRIES = 8;
    private static final Map<String, WebThemeManifest> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .authenticator(okhttp3.Authenticator.NONE)
            .proxyAuthenticator(okhttp3.Authenticator.NONE)
            .dns(WebThemeManifestLoader::lookupPublic)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    private WebThemeManifestLoader() {
    }

    static WebThemeManifest load(Context context, String url, String target, boolean force) throws IOException {
        String cacheKey = url + "\n" + target;
        if (!force) {
            synchronized (CACHE) {
                WebThemeManifest cached = CACHE.get(cacheKey);
                if (cached != null) return cached;
            }
        }
        String json = WebHomeTarget.canonicalThemeAsset(url).equals(WebHomeTarget.ECLIPSE_URL)
                ? read(context.getAssets().open("webhome/theme.json"), WebThemeManifest.MAX_MANIFEST_BYTES)
                : fetch(url);
        WebThemeManifest manifest;
        try {
            manifest = WebThemeManifest.parse(url, json, target);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid theme manifest", e);
        }
        synchronized (CACHE) {
            CACHE.put(cacheKey, manifest);
            while (CACHE.size() > MAX_CACHE_ENTRIES) {
                String eldest = CACHE.keySet().iterator().next();
                CACHE.remove(eldest);
            }
        }
        return manifest;
    }

    private static String fetch(String url) throws IOException {
        if (!WebHomeTarget.isSafeThemeUrl(url) || !WebHomeTarget.isManifestUrl(url)) {
            throw new IOException("Unsafe theme manifest URL");
        }
        Request request = new Request.Builder().url(url).get().header("Accept", "application/json").build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || !response.request().url().equals(request.url())) {
                throw new IOException("Theme manifest request failed: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > WebThemeManifest.MAX_MANIFEST_BYTES) {
                throw new IOException("Theme manifest is too large");
            }
            return read(body.byteStream(), WebThemeManifest.MAX_MANIFEST_BYTES);
        }
    }

    static String read(InputStream input, int maxBytes) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IOException("Theme manifest is too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static List<InetAddress> lookupPublic(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        if (addresses.isEmpty()) throw new UnknownHostException(hostname);
        for (InetAddress address : addresses) {
            if (WebHomeTarget.isBlockedAddress(address)) throw new UnknownHostException("Blocked theme host");
        }
        return addresses;
    }
}
