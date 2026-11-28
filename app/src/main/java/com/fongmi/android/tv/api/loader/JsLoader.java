package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.quickjs.crawler.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import dalvik.system.DexClassLoader;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final Map<String, LoadSlot> loading = new HashMap<>();
    private final Loader loader;
    private final Function<String, DexClassLoader> dex;
    private volatile String recent;
    private volatile boolean retired;
    private boolean cleanupStarted;

    public JsLoader() {
        this(jar -> BaseLoader.get().dex(jar));
    }

    JsLoader(Function<String, DexClassLoader> dex) {
        this.dex = dex;
        loader = new Loader();
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
        try {
            SpiderCleanup.destroy("js-loader", spiders);
            loader.clearModules();
        } finally {
            spiders.clear();
            recent = null;
        }
    }

    public void setRecent(String recent) {
        setRecent("", recent);
    }

    /**
     * recent 必须与 {@link #getSpider(String, String, String, String, String)} 的槽位键同形：
     * spiders 按 {@link BaseLoader#slot(String, String)} 建键，而 {@link #proxy} 又拿 recent 去查
     * 同一张表。recent 若仍是裸 siteKey，桥接配置的 ?do=js 要么查不到自己（调用方拿到 500），
     * 要么命中激活配置里同名站源的实例。空命名空间等于改动前的裸 key。
     */
    public synchronized void setRecent(String namespace, String key) {
        if (!retired) recent = BaseLoader.slot(namespace, key);
    }

    /** 缓存键可能带命名空间前缀；对外的"活动插件"仍按脚本自己的 key 报告。 */
    public java.util.Set<String> activeKeys() {
        return com.fongmi.android.tv.api.loader.BaseLoader.baseKeys(spiders.keySet());
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return getSpider("", key, api, ext, jar);
    }

    /**
     * 按 (命名空间, siteKey) 取 spider：桥接解析非激活配置时不能写进激活配置的槽位，
     * 否则 A、B 两个配置的同名站点会互相串源。空命名空间等于原来的行为。
     */
    public Spider getSpider(String namespace, String key, String api, String ext, String jar) {
        if (retired) return new SpiderNull();
        String slot = BaseLoader.slot(namespace, key);
        Spider cached = spiders.get(slot);
        if (cached != null) return retired ? new SpiderNull() : cached;
        LoadSlot pending = beginLoad(slot);
        if (pending == null) return new SpiderNull();
        try {
            if (Thread.holdsLock(pending)) throw new IllegalStateException("Recursive update");
            synchronized (pending) {
                if (retired) return new SpiderNull();
                Spider spider = spiders.get(slot);
                if (spider == null) {
                    spider = createSpider(key, api, ext, jar);
                    if (spider == null) return new SpiderNull();
                    if (!publish(slot, spider)) {
                        SpiderCleanup.destroy("js-loader", Map.of(slot, spider));
                        return new SpiderNull();
                    }
                }
                return retired ? new SpiderNull() : spider;
            }
        } finally {
            endLoad(slot, pending);
        }
    }

    private Spider createSpider(String key, String api, String ext, String jar) {
        return SpiderInit.create(() -> loader.spider(api, dex.apply(jar)), spider -> {
            spider.siteKey = key;
            spider.init(App.get(), ext);
        }, e -> {
            SpiderDebug.log("JsLoader", "JS spider init failed: key=%s api=%s", key, api);
            SpiderDebug.log("JsLoader", e);
            e.printStackTrace();
        });
    }

    private synchronized LoadSlot beginLoad(String key) {
        if (retired) return null;
        LoadSlot pending = loading.computeIfAbsent(key, ignored -> new LoadSlot());
        pending.users++;
        return pending;
    }

    private synchronized void endLoad(String key, LoadSlot pending) {
        if (--pending.users == 0) loading.remove(key);
    }

    private synchronized boolean publish(String key, Spider spider) {
        if (retired) return false;
        spiders.put(key, spider);
        return true;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        String key = recent;
        if (retired || key == null) return null;
        Spider spider = spiders.get(key);
        return spider != null ? spider.proxy(params) : null;
    }

    private static final class LoadSlot {
        int users;
    }
}
