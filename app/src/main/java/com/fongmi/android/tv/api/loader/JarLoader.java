package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import dalvik.system.DexClassLoader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JarLoader {

    private static final int SPIDER_INIT_ATTEMPTS = 2;
    private static final Interner<String> JAR_FILE_LOCKS = Interners.newWeakInterner();

    /**
     * On translated runtimes (x86 emulator executing arm64 native code) ART's
     * {@code UnloadNativeLibraries()} calls into the binary translator and can
     * SIGSEGV from {@code HeapTaskDaemon}. Dropping a replaced
     * {@code DexClassLoader} therefore crashed the app on every interface
     * reload. Retiring the loader keeps it strongly reachable for the process
     * lifetime on those devices only, which skips the unsafe unload path.
     */
    private static final List<DexClassLoader> RETAINED_ON_TRANSLATED_RUNTIME = new ArrayList<>();

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private volatile String recent;
    private volatile boolean retired;
    private boolean cleanupStarted;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    synchronized void retire() {
        retired = true;
    }

    public void clear() {
        synchronized (this) {
            if (cleanupStarted) return;
            retire();
            cleanupStarted = true;
        }
        SpiderDebug.log("jar-loader", "clear loaders=%s spiders=%s methods=%s", loaders.size(), spiders.size(), methods.size());
        try {
            SpiderCleanup.destroy("jar-loader", spiders);
        } finally {
            retainTranslatedLoaders(loaders.values());
            loaders.clear();
            methods.clear();
            spiders.clear();
            locks.clear();
            recent = null;
        }
    }

    private void retainTranslatedLoaders(Collection<DexClassLoader> previous) {
        if (previous.isEmpty() || !NativeBridgeGuard.isTranslatedRuntime()) return;
        synchronized (RETAINED_ON_TRANSLATED_RUNTIME) {
            RETAINED_ON_TRANSLATED_RUNTIME.addAll(previous);
            SpiderDebug.log("jar-loader", "retained translated loaders count=%s total=%s",
                    previous.size(), RETAINED_ON_TRANSLATED_RUNTIME.size());
        }
    }

    private synchronized Object lockIfActive(String key) {
        return retired ? null : locks.computeIfAbsent(key, ignored -> new Object());
    }

    public void setRecent(String recent) {
        synchronized (this) {
            if (retired) return;
            this.recent = recent;
        }
        SpiderDebug.log("jar-loader", "recent=%s", recent);
    }

    public java.util.Set<String> activeKeys() {
        return java.util.Set.copyOf(loaders.keySet());
    }

    private DexClassLoader openDex(String key, File file) {
        if (retired || Thread.currentThread().isInterrupted()) {
            SpiderDebug.log("jar-loader", "load skip retired or interrupted key=%s", key);
            return null;
        }
        if (!Path.exists(file)) {
            SpiderDebug.log("jar-loader", "load skip missing key=%s file=%s", key, file);
            return null;
        }
        if (!file.setReadOnly()) {
            SpiderDebug.log("jar-loader", "load skip readonly failed key=%s file=%s size=%s", key, file.getAbsolutePath(), file.length());
            return null;
        }
        String cachePath = Path.jar().getAbsolutePath();
        SpiderDebug.log("jar-loader", "load start key=%s file=%s size=%s cache=%s", key, file.getAbsolutePath(), file.length(), cachePath);
        return new CspDexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
    }

    private void initializeJar(String key, DexClassLoader loader) {
        if (retired) {
            retainTranslatedLoaders(List.of(loader));
            return;
        }
        long start = System.currentTimeMillis();
        invokeInit(key, loader);
        invokeNetworkCompat(key, loader);
        Method proxy = findProxy(key, loader);
        if (publishJar(key, loader, proxy)) {
            SpiderDebug.log("jar-loader", "load done key=%s cost=%sms", key, System.currentTimeMillis() - start);
        } else {
            retainTranslatedLoaders(List.of(loader));
        }
    }

    private synchronized boolean publishJar(String key, DexClassLoader loader, Method proxy) {
        if (retired) return false;
        if (proxy != null) methods.put(key, proxy);
        loaders.put(key, loader);
        return true;
    }

    private void invokeNetworkCompat(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.parser.merge.j0.b");
            Method method = clz.getMethod("b", okhttp3.OkHttpClient.class);
            OkHttpClient client = OkHttp.client().newBuilder().addInterceptor(chain -> {
                Request request = chain.request();
                boolean iframe = request.url().host().endsWith("youtube.com") && request.url().encodedPath().contains("iframe_api");
                long start = System.currentTimeMillis();
                try {
                    Response response = chain.proceed(request);
                    if (iframe) SpiderDebug.log("youtube-iframe", "response code=%s final=%s type=%s encoding=%s length=%s cost=%sms", response.code(), response.request().url(), response.header("Content-Type"), response.header("Content-Encoding"), response.header("Content-Length"), System.currentTimeMillis() - start);
                    return response;
                } catch (Throwable e) {
                    if (iframe) SpiderDebug.log("youtube-iframe", "failed url=%s cost=%sms error=%s", request.url(), System.currentTimeMillis() - start, error(e));
                    throw e;
                }
            }).build();
            Class<?> environment = loader.loadClass("com.github.catvod.parser.merge.j0.c");
            environment.getConstructor(okhttp3.OkHttpClient.class).newInstance(client);
            SpiderDebug.log("jar-loader", "network client injected key=%s", key);
            try {
                Class<?> provider = loader.loadClass("com.github.catvod.parser.merge.P1.P");
                Object fetcher = provider.getMethod("b").invoke(null);
                Object response = clz.getMethod("a", String.class).invoke(fetcher, "https://www.youtube.com/iframe_api");
                String body = (String) response.getClass().getMethod("a").invoke(response);
                SpiderDebug.log("youtube-iframe", "probe success chars=%s", body == null ? -1 : body.length());
            } catch (Throwable e) {
                Throwable cause = e;
                while (cause.getCause() != null) cause = cause.getCause();
                SpiderDebug.log("youtube-iframe", "probe failed error=%s", cause.getClass().getName() + ":" + cause.getMessage());
                SpiderDebug.log("youtube-iframe", cause);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Optional compatibility hook used only by jars which expose it.
        } catch (Throwable e) {
            SpiderDebug.log("jar-loader", "network client inject failed key=%s error=%s", key, error(e));
        }
    }

    private void invokeInit(String key, DexClassLoader loader) {
        long start = System.currentTimeMillis();
        try {
            SpiderDebug.log("jar-loader", "jar init start key=%s", key);
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
            SpiderDebug.log("jar-loader", "jar init done key=%s cost=%sms", key, System.currentTimeMillis() - start);
        } catch (Throwable e) {
            SpiderDebug.log("jar-loader", "jar init error key=%s cost=%sms error=%s", key, System.currentTimeMillis() - start, error(e));
            SpiderDebug.log("jar-loader", e);
            e.printStackTrace();
        }
    }

    private Method findProxy(String key, DexClassLoader loader) {
        long start = System.currentTimeMillis();
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            SpiderDebug.log("jar-loader", "proxy method ready key=%s cost=%sms", key, System.currentTimeMillis() - start);
            return method;
        } catch (Throwable e) {
            SpiderDebug.log("jar-loader", "proxy method missing key=%s cost=%sms error=%s", key, System.currentTimeMillis() - start, error(e));
            e.printStackTrace();
            return null;
        }
    }

    public void parseJar(String key, String jar) {
        if (retired || loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = lockIfActive(key);
        if (lock == null) return;
        synchronized (lock) {
            if (retired || loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            SpiderDebug.log("jar-loader", "parse start key=%s source=%s md5=%s", key, source(jar), !md5.isEmpty());
            DexClassLoader loader = openJar(key, jar, md5);
            if (loader != null) initializeJar(key, loader);
        }
    }

    private DexClassLoader openJar(String key, String jar, String md5) {
        boolean remote = jar.startsWith("http");
        if (remote || !md5.isEmpty()) {
            File cache = Path.jar(jar);
            // Serialize writes to a shared archive, not plugin initialization.
            synchronized (JAR_FILE_LOCKS.intern(cache.getAbsolutePath())) {
                if (retired || Thread.currentThread().isInterrupted()) return null;
                if (!md5.isEmpty() && Util.equals(jar, md5)) return openDex(key, cache);
                if (remote) return openDex(key, Download.create(jar, cache).get());
            }
        }
        return jar.startsWith("file") ? openDex(key, Path.local(jar)) : null;
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Util.md5(jar);
            parseJar(jaKey, jar);
            return retired ? null : loaders.get(jaKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return getSpider("", key, api, ext, jar);
    }

    /**
     * 按 (命名空间, jar, siteKey) 取 spider：桥接解析非激活配置时不能写进激活配置的槽位。
     * 空命名空间等于原来的行为（jar md5 + siteKey），loaders 仍只按 jar md5 建键。
     */
    public Spider getSpider(String namespace, String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        String spKey = jaKey + BaseLoader.slot(namespace, key);
        return getOrCreateSpider(spKey, () -> {
            long start = System.currentTimeMillis();
            try {
                if (Thread.currentThread().isInterrupted()) {
                    SpiderDebug.log("jar-loader", "spider init skip interrupted site=%s api=%s jar=%s", key, api, jaKey);
                    return new SpiderNull();
                }
                SpiderDebug.log("jar-loader", "spider init start site=%s api=%s jar=%s ext=%s", key, api, jaKey, ext == null ? 0 : ext.length());
                parseJar(jaKey, jar);
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) {
                    SpiderDebug.log("jar-loader", "spider init skip loader missing site=%s api=%s jar=%s cost=%sms", key, api, jaKey, System.currentTimeMillis() - start);
                    return new SpiderNull();
                }
                Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
                spider.siteKey = key;
                spider.init(App.get(), ext);
                SpiderDebug.log("jar-loader", "spider init done site=%s api=%s jar=%s class=%s cost=%sms", key, api, jaKey, spider.getClass().getName(), System.currentTimeMillis() - start);
                return spider;
            } catch (Throwable e) {
                if (isInterrupted(e)) Thread.currentThread().interrupt();
                SpiderDebug.log("jar-loader", "spider init error site=%s api=%s jar=%s cost=%sms error=%s", key, api, jaKey, System.currentTimeMillis() - start, error(e));
                SpiderDebug.log("jar-loader", e);
                e.printStackTrace();
                return new SpiderNull();
            }
        });
    }

    Spider getOrCreateSpider(String key, Supplier<Spider> factory) {
        // A canceled request must stop immediately; a transient initialization failure
        // gets one retry and SpiderNull is never cached across requests.
        if (retired || Thread.currentThread().isInterrupted()) return new SpiderNull();
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        Object lock = lockIfActive("spider:" + key);
        if (lock == null) return new SpiderNull();
        synchronized (lock) {
            if (retired || Thread.currentThread().isInterrupted()) return new SpiderNull();
            cached = spiders.get(key);
            if (cached != null) return cached;
            Spider created = null;
            for (int attempt = 1; attempt <= SPIDER_INIT_ATTEMPTS; attempt++) {
                if (retired || Thread.currentThread().isInterrupted()) break;
                created = factory.get();
                if (created != null && !(created instanceof SpiderNull)) {
                    if (!publishSpider(key, created)) {
                        SpiderCleanup.destroy("jar-loader", Map.of(key, created));
                        return new SpiderNull();
                    }
                    if (!retired && !Thread.currentThread().isInterrupted()) return created;
                    SpiderDebug.log("jar-loader", "spider init completed after retire or cancel key=%s", key);
                    return new SpiderNull();
                }
                if (retired || Thread.currentThread().isInterrupted() || attempt == SPIDER_INIT_ATTEMPTS) break;
                SpiderDebug.log("jar-loader", "spider init retry key=%s attempt=%s", key, attempt + 1);
            }
            return created == null ? new SpiderNull() : created;
        }
    }

    private synchronized boolean publishSpider(String key, Spider spider) {
        if (retired) return false;
        spiders.put(key, spider);
        return true;
    }

    private static boolean isInterrupted(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException) return true;
        }
        return false;
    }

    private String source(String jar) {
        if (jar.startsWith("http")) return "http:" + Util.md5(jar);
        if (jar.startsWith("file")) return "file:" + jar.length();
        return jar;
    }

    private String error(Throwable e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getClass().getSimpleName() + ":" + cause.getMessage();
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (retired) return null;
        String key = recent;
        Method method = key != null ? methods.get(key) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
