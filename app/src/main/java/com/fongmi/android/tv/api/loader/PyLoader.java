package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private static final String PY_TAG = "python-spider";

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final Map<String, LoadSlot> loading = new HashMap<>();
    private final Loader loader;
    private volatile String recent;
    private volatile boolean retired;
    private boolean cleanupStarted;

    public PyLoader() {
        this(new Loader());
    }

    private PyLoader(Loader loader) {
        this.loader = loader;
    }

    PyLoader nextGeneration() {
        return new PyLoader(loader);
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
            SpiderCleanup.destroy("py-loader", spiders);
        } finally {
            spiders.clear();
            recent = null;
        }
    }

    public void setRecent(String recent) {
        setRecent("", recent);
    }

    /**
     * 与 {@link JsLoader#setRecent(String, String)} 同构：spiders 按
     * {@link BaseLoader#slot(String, String)} 建键，proxy() 又用 recent 查同一张表，
     * 所以 recent 也必须是槽位键，否则桥接配置的 ?do=py 查不到自己或命中激活配置的同名站源。
     */
    public synchronized void setRecent(String namespace, String key) {
        if (!retired) recent = BaseLoader.slot(namespace, key);
    }

    /** 缓存键可能带命名空间前缀；对外的"活动插件"仍按脚本自己的 key 报告。 */
    public java.util.Set<String> activeKeys() {
        return com.fongmi.android.tv.api.loader.BaseLoader.baseKeys(spiders.keySet());
    }

    public Spider getSpider(String key, String api, String ext) {
        return getSpider("", key, api, ext);
    }

    /**
     * 按 (命名空间, siteKey) 取 spider：桥接解析非激活配置时不能写进激活配置的槽位，
     * 否则 A、B 两个配置的同名站点会互相串源。空命名空间等于原来的行为。
     */
    public Spider getSpider(String namespace, String key, String api, String ext) {
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
                    spider = createSpider(key, api, ext);
                    if (spider == null) return new SpiderNull();
                    if (!publish(slot, spider)) {
                        SpiderCleanup.destroy("py-loader", Map.of(slot, spider));
                        return new SpiderNull();
                    }
                }
                return retired ? new SpiderNull() : spider;
            }
        } finally {
            endLoad(slot, pending);
        }
    }

    private Spider createSpider(String key, String api, String ext) {
        return SpiderInit.create(() -> loader.spider(api), spider -> {
            spider.siteKey = key;
            spider.init(App.get(), normalizeExt(ext));
        }, e -> {
            SpiderDebug.log(PY_TAG, "init failed key=%s api=%s error=%s:%s", key, api, e.getClass().getName(), e.getMessage());
            SpiderDebug.log(PY_TAG, e);
            // Structured diagnostics are opt-in; always preserve the Logcat failure.
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

    private String normalizeExt(String ext) {
        String value = TextUtils.isEmpty(ext) ? "" : ext.trim();
        // Many live Python spiders treat ext as an option object and call .get().
        // TV-style configs commonly express an empty extension as [], which would
        // otherwise deserialize to a list and crash those spiders during init.
        return "[]".equals(value) ? "{}" : ext;
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
