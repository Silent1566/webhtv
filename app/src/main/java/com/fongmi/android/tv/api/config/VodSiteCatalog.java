package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.TmdbSourceCredentialIngress;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按配置标识读取站源清单，且<b>不触碰当前激活配置</b>。
 *
 * <p>{@link VodConfig} 是"当前激活源"的全局单例：它的解析路径会顺带写入 live/wall 配置行、
 * 注册全局 spider 加载器、覆盖 WebHomeExtensionRegistry 与配置行上的 logo/notice。
 * PC 端桥接需要一次拿到某一配置的全部站源并逐个切换，因此这里只做纯解析：
 * 当前激活配置仍归 VodConfig，非激活配置归本类。
 */
public final class VodSiteCatalog {

    private static final String TAG = "VodSiteCatalog";
    private static final int MAX_DEPOT_DEPTH = 3;
    /** 配置解析要发网络请求，/proxy 又会被逐段调用，按标识短缓存。 */
    private static final long CACHE_TTL = 60_000L;
    private static final int CACHE_SIZE = 8;

    private VodSiteCatalog() {
    }

    /** 一份配置解析出来的、可直接对外提供网关服务的内容。 */
    public static final class Catalog {
        private final List<Site> sites;
        private final List<Doh> doh;
        private final List<Rule> rules;
        private final List<String> flags;

        public Catalog(List<Site> sites, List<Doh> doh, List<Rule> rules, List<String> flags) {
            this.sites = sites == null ? Collections.<Site>emptyList() : sites;
            this.doh = doh == null ? Collections.<Doh>emptyList() : doh;
            this.rules = rules == null ? Collections.<Rule>emptyList() : rules;
            this.flags = flags == null ? Collections.<String>emptyList() : flags;
        }

        public List<Site> getSites() {
            return sites;
        }

        public List<Doh> getDoh() {
            return doh;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public List<String> getFlags() {
            return flags;
        }

        /** 与 {@link VodConfig#getSite(String)} 一致：先精确、再忽略大小写，未命中给空站点。 */
        public Site find(String key) {
            if (key == null || key.isEmpty()) return new Site();
            for (Site site : sites) {
                if (site == null || site.getKey().isEmpty()) continue;
                if (site.getKey().equals(key)) return site;
            }
            for (Site site : sites) {
                if (site == null || site.getKey().isEmpty()) continue;
                if (site.getKey().equalsIgnoreCase(key)) return site;
            }
            return new Site();
        }
    }

    /** 配置标识 → 站源清单。默认实现查数据库；单元测试可注入替身。 */
    public interface Source {
        Catalog load(String interfaceKey) throws Exception;
    }

    /**
     * 默认实现：按 interfaceKey 从数据库取配置行再解析。
     * 未知标识抛 IllegalArgumentException，由调用方转成 400。
     * 进程内共享，缓存才能跨请求命中。
     */
    public static Source database() {
        return Database.INSTANCE;
    }

    private static final class Database {
        static final Source INSTANCE = newDatabase();
    }

    private static Source newDatabase() {
        return new Source() {
            private final Map<String, Entry> cache = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, VodSiteCatalog.Entry> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

            @Override
            public Catalog load(String interfaceKey) throws Exception {
                if (interfaceKey == null || interfaceKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("缺少配置标识");
                }
                String id = interfaceKey.trim();
                long now = System.currentTimeMillis();
                synchronized (cache) {
                    Entry entry = cache.get(id);
                    if (entry != null && now - entry.at < CACHE_TTL) return entry.catalog;
                }
                Config config = AppDatabase.get().getConfigDao().findByInterfaceKey(id, BaseConfig.VOD);
                if (config == null || isBlank(config.getUrl())) {
                    throw new IllegalArgumentException("未知的配置标识: " + id);
                }
                Catalog catalog = catalogOf(config);
                synchronized (cache) {
                    cache.put(id, new Entry(catalog, now));
                }
                return catalog;
            }
        };
    }

    public static List<Site> sitesOf(Config config) throws Exception {
        return catalogOf(config).getSites();
    }

    /** 解析一份配置：只读网络 + 纯函数变换，不写数据库、不切换全局状态。 */
    public static Catalog catalogOf(Config config) throws Exception {
        if (config == null || isBlank(config.getUrl())) throw new IllegalArgumentException("配置为空");
        return catalogOf(objectOf(config.getUrl(), 0));
    }

    /** 由已解析的配置对象抽取站源；CustomCspSetting 只合并进传入对象。 */
    public static Catalog catalogOf(JsonObject object) {
        if (object == null) throw new IllegalArgumentException("配置为空");
        CustomCspSetting.inject(object);
        String spider = Json.safeString(object, "spider");
        List<Site> sites = new ArrayList<>();
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            Site site = Site.objectFrom(element, spider);
            if (site == null || site.getKey().isEmpty()) continue;
            sites.add(site);
        }
        return new Catalog(sites, dohOf(object), rulesOf(object), Json.safeListString(object, "flags"));
    }

    private static JsonObject objectOf(String configUrl, int depth) throws Exception {
        if (depth > MAX_DEPOT_DEPTH) throw new IllegalArgumentException("配置订阅层级过深");
        String url = CatSource.isBundle(configUrl) ? CatSource.serve(configUrl) : UrlUtil.convert(configUrl);
        String raw = Decoder.getJson(url, TAG);
        TmdbSourceCredentialIngress.Ingress ingress = TmdbSourceCredentialIngress.extractRootAndStrip(raw);
        JsonObject object = CatSource.normalize(url, Json.parse(ingress.getSanitizedJson()));
        // 订阅列表：取首条继续解析，且不像 VodConfig 那样把中间配置写进数据库。
        if (!object.has("sites") && object.has("urls")) {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            if (items.isEmpty()) throw new IllegalArgumentException("配置订阅为空");
            return objectOf(items.get(0).getUrl(), depth + 1);
        }
        return object;
    }

    private static List<Doh> dohOf(JsonObject object) {
        if (!object.has("doh") || !object.get("doh").isJsonArray()) return Collections.emptyList();
        return Doh.arrayFrom(object.get("doh"));
    }

    private static List<Rule> rulesOf(JsonObject object) {
        if (!object.has("rules") || !object.get("rules").isJsonArray()) return Collections.emptyList();
        return Rule.arrayFrom(object.get("rules"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Entry {
        final Catalog catalog;
        final long at;

        Entry(Catalog catalog, long at) {
            this.catalog = catalog;
            this.at = at;
        }
    }
}
