package com.fongmi.android.tv.ui.helper;

import android.app.Activity;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.AiRecommendationService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 数据适配器
 *
 * 负责在 VideoActivity 的 TMDB 模式下：
 * 1. 根据视频名称自动搜索匹配 TMDB 条目
 * 2. 加载详情、演员、简介等元数据
 * 3. 把元数据写回 {@link Vod} 并通过 {@link RefreshEvent#vod(Vod)} 推送到 UI
 *
 * 该类位于 src/main，被 mobile / leanback 两个 flavor 共享，因此只依赖
 * 两端都存在的事件机制，不直接操作各自布局生成的 binding 字段。
 */
public class TmdbUIAdapter {

    private final Activity activity;
    private final TmdbService tmdbService;
    private final TmdbMatcher tmdbMatcher;
    private final TmdbConfig tmdbConfig;

    private TmdbItem tmdbItem;
    private JsonObject tmdbDetail;
    private List<TmdbPerson> tmdbCast;
    private List<TmdbItem> recommendations;
    private List<TmdbItem> personalTmdbRecommendations;
    private List<TmdbItem> personalDoubanRecommendations;
    private List<TmdbItem> personalAiRecommendations;
    private PersonalRecommendationService.RecommendationPage personalTmdbPage;
    private PersonalRecommendationService.RecommendationPage personalDoubanPage;
    private PersonalRecommendationService.RecommendationPage personalAiPage;
    private Vod vod;
    private int recommendationPage;
    private boolean recommendationHasMore;
    private boolean recommendationLoading;
    private boolean personalTmdbLoading;
    private boolean personalDoubanLoading;
    private boolean personalRefreshLoading;
    private boolean personalAiLoading;
    private boolean loaded;
    private volatile int loadGeneration;
    private PersonalAiUpdateListener personalAiUpdateListener;

    public interface LoadMoreCallback {
        void onLoaded(boolean changed);
    }

    public interface PersonalAiUpdateListener {
        void onPersonalAiRecommendationsUpdated();
    }

    public TmdbUIAdapter(Activity activity) {
        this.activity = activity;
        this.tmdbService = new TmdbService();
        this.tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        this.tmdbMatcher = new TmdbMatcher(tmdbService, tmdbConfig);
    }

    public boolean isReady() {
        return tmdbConfig.isReady();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setPersonalAiUpdateListener(PersonalAiUpdateListener listener) {
        this.personalAiUpdateListener = listener;
    }

    public TmdbItem getTmdbItem() {
        return tmdbItem;
    }

    public JsonObject getTmdbDetail() {
        return tmdbDetail;
    }

    public String getPosterUrl() {
        return TmdbImageSelector.poster(tmdbDetail, tmdbConfig.getImageBase(), tmdbItem == null ? "" : tmdbItem.getPosterUrl());
    }

    public List<TmdbPerson> getCast() {
        return tmdbCast == null ? new ArrayList<>() : tmdbCast;
    }

    public List<TmdbItem> getPersonalTmdbRecommendations() {
        return getPersonalRecommendations(personalTmdbRecommendations);
    }

    public List<TmdbItem> getPersonalDoubanRecommendations() {
        return getPersonalRecommendations(personalDoubanRecommendations);
    }

    public List<TmdbItem> getPersonalAiRecommendations() {
        return getPersonalRecommendations(personalAiRecommendations);
    }

    private List<TmdbItem> getPersonalRecommendations(List<TmdbItem> personalRecommendations) {
        if (personalRecommendations == null || personalRecommendations.isEmpty()) return new ArrayList<>();
        List<TmdbItem> items = new ArrayList<>();
        for (TmdbItem item : personalRecommendations) {
            if (containsRecommendation(items, item)) continue;
            items.add(item);
        }
        return items;
    }

    /**
     * 直接指定 TMDB 条目并加载详情。
     */
    public void load(TmdbItem item, Vod vod) {
        if (item == null) return;
        int generation = resetLoadState();
        this.tmdbItem = item;
        saveMatch(vod, item);
        loadDetail(vod, item, generation);
    }

    /**
     * 根据视频名称自动搜索匹配并加载详情。
     *
     * @param videoName 视频标题（通常取详情页解析出的名称）
     * @param vod       待增强的 Vod；增强后通过事件推回 UI
     */
    public void autoMatch(String videoName, Vod vod) {
        int generation = resetLoadState();
        if (!isReady()) {
            SpiderDebug.log("tmdb", "skip auto match: config not ready");
            notifyLoadComplete(vod, generation);
            return;
        }
        if (TextUtils.isEmpty(videoName)) {
            notifyLoadComplete(vod, generation);
            return;
        }
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            if (!isCurrentGeneration(generation)) return;
            TmdbItem matched = getCachedMatch(vod);
            if (matched != null) {
                SpiderDebug.log("tmdb", "auto match cache hit title=%s cost=%dms", matched.getTitle(), System.currentTimeMillis() - start);
                if (isCachedSplitSeasonMismatch(videoName, vod, matched)) {
                    SpiderDebug.log("tmdb", "auto match cache skipped split-season variant title=%s id=%d name=%s", matched.getTitle(), matched.getTmdbId(), videoName);
                    matched = null;
                }
            }
            if (matched == null) {
                long searchStart = System.currentTimeMillis();
                matched = tmdbMatcher.searchAndMatch(videoName, vod);
                SpiderDebug.log("tmdb", "auto match search cost=%dms hit=%s name=%s", System.currentTimeMillis() - searchStart, matched != null, videoName);
            }
            if (!isCurrentGeneration(generation)) return;
            if (matched == null) {
                SpiderDebug.log("tmdb", "auto match miss name=%s total=%dms", videoName, System.currentTimeMillis() - start);
                tmdbItem = null;
                notifyLoadComplete(vod, generation);
                return;
            }
            saveMatch(vod, matched);
            tmdbItem = matched;
            SpiderDebug.log("tmdb", "auto match ready title=%s total=%dms", matched.getTitle(), System.currentTimeMillis() - start);
            loadDetailSync(vod, matched, generation);
        });
    }

    public List<TmdbItem> search(String keyword) throws Exception {
        return search(keyword, null);
    }

    public List<TmdbItem> search(String keyword, Vod vod) throws Exception {
        return tmdbMatcher.search(keyword, vod);
    }

    public String cleanSearchQuery(String keyword) {
        return tmdbMatcher.cleanVideoName(keyword);
    }

    private void loadDetail(Vod vod, TmdbItem item, int generation) {
        if (item == null || !isReady()) {
            notifyLoadComplete(vod, generation);
            return;
        }
        Task.execute(() -> loadDetailSync(vod, item, generation));
    }

    private int resetLoadState() {
        int generation = ++loadGeneration;
        tmdbItem = null;
        tmdbDetail = null;
        tmdbCast = null;
        recommendations = null;
        personalTmdbRecommendations = null;
        personalDoubanRecommendations = null;
        personalAiRecommendations = null;
        personalTmdbPage = null;
        personalDoubanPage = null;
        personalAiPage = null;
        vod = null;
        recommendationPage = 1;
        recommendationHasMore = false;
        recommendationLoading = false;
        personalTmdbLoading = false;
        personalDoubanLoading = false;
        personalRefreshLoading = false;
        personalAiLoading = false;
        loaded = false;
        return generation;
    }

    private boolean isCurrentGeneration(int generation) {
        return generation == loadGeneration;
    }

    private void loadDetailSync(Vod vod, TmdbItem item, int generation) {
        long start = System.currentTimeMillis();
        try {
            SpiderDebug.log("tmdb", "detail core start title=%s media=%s id=%d", item.getTitle(), item.getMediaType(), item.getTmdbId());
            long detailStart = System.currentTimeMillis();
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            SpiderDebug.log("tmdb", "detail core tmdbDetail cost=%dms title=%s", System.currentTimeMillis() - detailStart, item.getTitle());
            long castStart = System.currentTimeMillis();
            List<TmdbPerson> cast = tmdbService.cast(detail, tmdbConfig);
            SpiderDebug.log("tmdb", "detail core castParse cost=%dms count=%d title=%s", System.currentTimeMillis() - castStart, cast.size(), item.getTitle());
            if (!isCurrentGeneration(generation)) return;
            this.vod = vod;
            tmdbDetail = detail;
            tmdbCast = cast;
            recommendations = new ArrayList<>();
            recommendationPage = 1;
            recommendationHasMore = false;
            PersonalRecommendationService.RecommendationPages personalPages = PersonalRecommendationService.RecommendationPages.empty();
            personalTmdbPage = personalPages.getTmdb();
            personalDoubanPage = personalPages.getDouban();
            personalAiPage = personalPages.getAi();
            personalTmdbRecommendations = personalTmdbPage.getItems();
            personalDoubanRecommendations = personalDoubanPage.getItems();
            personalAiRecommendations = personalAiPage.getItems();
            loaded = true;
            if (vod != null) {
                long enrichStart = System.currentTimeMillis();
                enrichVod(vod, item, detail);
                SpiderDebug.log("tmdb", "detail core enrichVod cost=%dms title=%s", System.currentTimeMillis() - enrichStart, item.getTitle());
                if (!isCurrentGeneration(generation)) return;
                notifyVodChanged(vod, generation);
                SpiderDebug.log("tmdb", "detail core first refresh queued cost=%dms title=%s", System.currentTimeMillis() - start, item.getTitle());
            }
            loadEpisodeTitlesAsync(vod, item, generation);
            loadRelatedRecommendationsAsync(vod, item, detail, generation);
            loadPersonalRecommendationsAsync(vod, item, detail, generation);
            SpiderDebug.log("tmdb", "detail core loaded title=%s cast=%d total=%dms", item.getTitle(), getCast().size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "detail load failed cost=%dms error=%s", System.currentTimeMillis() - start, e.getMessage());
            notifyLoadComplete(vod, generation);
        }
    }

    private void notifyVodChanged(Vod vod, int generation) {
        if (vod == null || !isCurrentGeneration(generation)) return;
        activity.runOnUiThread(() -> {
            if (isCurrentGeneration(generation)) RefreshEvent.vod(vod);
        });
    }

    private void loadEpisodeTitlesAsync(Vod vod, TmdbItem item, int generation) {
        if (vod == null || item == null || !item.isTv()) return;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            boolean changed = applyEpisodeTitles(vod, item);
            SpiderDebug.log("tmdb", "episode titles async cost=%dms changed=%s title=%s", System.currentTimeMillis() - start, changed, item.getTitle());
            if (changed) notifyVodChanged(vod, generation);
        });
    }

    private void loadRelatedRecommendationsAsync(Vod vod, TmdbItem item, JsonObject detail, int generation) {
        if (item == null || detail == null) return;
        recommendationLoading = true;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            List<TmdbItem> ranked = new ArrayList<>();
            boolean more = false;
            int recommendationCount = 0;
            int similarCount = 0;
            try {
                long recommendationsStart = System.currentTimeMillis();
                List<TmdbItem> pageRecommendations = tmdbService.recommendations(item, tmdbConfig, 1);
                SpiderDebug.log("tmdb", "related recommendations request cost=%dms count=%d title=%s", System.currentTimeMillis() - recommendationsStart, pageRecommendations.size(), item.getTitle());
                long similarStart = System.currentTimeMillis();
                List<TmdbItem> pageSimilar = tmdbService.similar(item, tmdbConfig, 1);
                SpiderDebug.log("tmdb", "related similar request cost=%dms count=%d title=%s", System.currentTimeMillis() - similarStart, pageSimilar.size(), item.getTitle());
                recommendationCount = pageRecommendations.size();
                similarCount = pageSimilar.size();
                ranked = PersonalRecommendationService.rankTmdbItemsForContext(detail, pageRecommendations, pageSimilar, Integer.MAX_VALUE);
                more = !pageRecommendations.isEmpty() || !pageSimilar.isEmpty();
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial recommendations failed error=%s", e.getMessage());
            }
            List<TmdbItem> loadedItems = ranked;
            boolean hasMore = more;
            SpiderDebug.log("tmdb", "related recommendations async cost=%dms recommendations=%d similar=%d ranked=%d title=%s", System.currentTimeMillis() - start, recommendationCount, similarCount, loadedItems.size(), item.getTitle());
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                recommendationLoading = false;
                recommendations = loadedItems;
                recommendationPage = 1;
                recommendationHasMore = hasMore;
                if (vod != null && !loadedItems.isEmpty()) RefreshEvent.vod(vod);
            });
        });
    }

    private void loadPersonalRecommendationsAsync(Vod vod, TmdbItem item, JsonObject detail, int generation) {
        if (vod == null || !Setting.isPersonalRecommendation()) return;
        personalRefreshLoading = true;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService.RecommendationPages pages = PersonalRecommendationService.RecommendationPages.empty();
            try {
                PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                pages = service.loadPage(vod, item, detail, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial personal recommendations failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPages loadedPages = pages;
            SpiderDebug.log("tmdb", "personal recommendations async cost=%dms tmdb=%d douban=%d title=%s", System.currentTimeMillis() - start, loadedPages.getTmdb().getItems().size(), loadedPages.getDouban().getItems().size(), item == null ? "" : item.getTitle());
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                personalRefreshLoading = false;
                personalTmdbPage = loadedPages.getTmdb();
                personalDoubanPage = loadedPages.getDouban();
                personalTmdbRecommendations = personalTmdbPage.getItems();
                personalDoubanRecommendations = personalDoubanPage.getItems();
                if (vod != null && (!personalTmdbRecommendations.isEmpty() || !personalDoubanRecommendations.isEmpty())) RefreshEvent.vod(vod);
            });
        });
        loadPersonalAiRecommendationsAsync(vod, item, generation);
    }

    private void loadPersonalAiRecommendationsAsync(Vod vod, TmdbItem item, int generation) {
        if (vod == null || !Setting.isPersonalRecommendation() || personalAiLoading) return;
        personalAiLoading = true;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            AiRecommendationService.CachedPage cached = service.loadCachedAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            if (cached.hasItems()) {
                SpiderDebug.log("tmdb", "personal ai cache hit exact=%s resolved=%s count=%d title=%s", cached.isExact(), cached.isResolved(), cached.getPage().getItems().size(), item == null ? "" : item.getTitle());
                applyPersonalAiPage(cached.getPage(), generation, false, false);
            }
            PersonalRecommendationService.RecommendationPage page = cached.getPage();
            String mode = "cache";
            try {
                if (!cached.hasItems() || !cached.isExact() || !cached.isResolved()) {
                    mode = cached.isExact() ? "resolve-cache" : "refresh";
                    page = cached.isExact()
                            ? service.resolveCachedAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                            : service.refreshAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                }
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial personal ai recommendations failed error=%s", e.getMessage());
                page = PersonalRecommendationService.RecommendationPage.empty("");
            }
            PersonalRecommendationService.RecommendationPage loadedPage = page;
            SpiderDebug.log("tmdb", "personal ai recommendations async mode=%s cost=%dms count=%d title=%s", mode, System.currentTimeMillis() - start, loadedPage.getItems().size(), item == null ? "" : item.getTitle());
            applyPersonalAiPage(loadedPage, generation, !cached.hasItems(), true);
        });
    }

    private void applyPersonalAiPage(PersonalRecommendationService.RecommendationPage page, int generation, boolean allowEmpty, boolean finishLoading) {
        activity.runOnUiThread(() -> {
            if (!isCurrentGeneration(generation)) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            if (page == null) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            List<TmdbItem> items = page.getItems();
            if (!allowEmpty && items.isEmpty()) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            boolean changed = !TmdbRecommendationRows.sameDisplayList(personalAiRecommendations, items);
            personalAiPage = page;
            personalAiRecommendations = items;
            if (changed) notifyPersonalAiRecommendationsUpdated();
            if (finishLoading) personalAiLoading = false;
        });
    }

    private void notifyPersonalAiRecommendationsUpdated() {
        if (personalAiUpdateListener != null) personalAiUpdateListener.onPersonalAiRecommendationsUpdated();
    }

    private void notifyLoadComplete(Vod vod, int generation) {
        // TMDB 加载失败或跳过时，仍然发送 RefreshEvent 让 UI 继续
        if (vod != null && isCurrentGeneration(generation)) {
            activity.runOnUiThread(() -> RefreshEvent.vod(vod));
        }
    }

    private TmdbItem getCachedMatch(Vod vod) {
        if (vod == null) return null;
        return Setting.getTmdbMatchCache().find(cacheSiteKey(vod), cacheVodId(vod));
    }

    private boolean isCachedSplitSeasonMismatch(String videoName, Vod vod, TmdbItem item) {
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            return TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(videoName, vod), detail);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String matchSourceText(String videoName, Vod vod) {
        StringBuilder builder = new StringBuilder(videoName == null ? "" : videoName);
        if (vod != null) {
            builder.append(' ').append(vod.getName());
            builder.append(' ').append(vod.getRemarks());
        }
        return builder.toString();
    }

    private void saveMatch(Vod vod, TmdbItem item) {
        if (vod == null || item == null || item.getTmdbId() <= 0) return;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        cache.put(cacheSiteKey(vod), cacheVodId(vod), item);
        Setting.putTmdbMatchCache(cache);
    }

    private String cacheSiteKey(Vod vod) {
        String siteKey = vod == null ? "" : vod.getSiteKey();
        if (!TextUtils.isEmpty(siteKey)) return siteKey;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    private String cacheVodId(Vod vod) {
        String vodId = vod == null ? "" : vod.getId();
        if (!TextUtils.isEmpty(vodId)) return vodId;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("id");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    /**
     * 把 TMDB 详情写回 Vod。
     */
    public void enrichVod(Vod vod) {
        enrichVod(vod, tmdbItem, tmdbDetail);
    }

    private void enrichVod(Vod vod, TmdbItem item, JsonObject detail) {
        if (vod == null || item == null || detail == null) return;

        applyTmdbTitle(vod, item);

        // 简介：优先使用 TMDB 翻译后的简介
        String overview = tmdbService.translatedOverview(detail, tmdbConfig);
        if (!TextUtils.isEmpty(overview) && overview.length() > vod.getContent().length()) {
            vod.setContent(overview);
        }

        // 海报：源站缺失时使用 TMDB 海报
        if (TextUtils.isEmpty(vod.getPic()) && !TextUtils.isEmpty(item.getPosterUrl())) {
            vod.setPic(item.getPosterUrl());
        }

        // 演员：源站缺失时使用 TMDB 演员表（无法直接设置，Vod 无 setter）
        // 可通过扩展 Vod 添加 setActor() 或在 VideoActivity 显示时从 adapter 获取

        // 导演 / 主创：源站缺失时使用 TMDB 主创
        if (TextUtils.isEmpty(vod.getDirector())) {
            List<TmdbPerson> creators = tmdbService.creators(detail, tmdbConfig);
            List<String> names = new ArrayList<>();
            for (TmdbPerson person : creators) {
                if (!TextUtils.isEmpty(person.getName())) names.add(person.getName());
                if (names.size() >= 5) break;
            }
            if (!names.isEmpty()) vod.setDirector(TextUtils.join(" / ", names));
        }
    }

    static boolean applyTmdbTitle(Vod vod, TmdbItem item) {
        if (vod == null || item == null) return false;
        String title = item.getTitle();
        if (title == null || title.length() == 0) return false;
        vod.setName(title);
        return true;
    }

    /**
     * 获取并应用 TMDB 集数标题到 Vod（仅针对电视剧）。
     */
    private boolean applyEpisodeTitles(Vod vod, TmdbItem item) {
        if (vod == null || item == null || vod.getFlags() == null) return false;
        try {
            // 尝试获取第1季
            JsonObject season = null;
            int seasonNumber = 1;
            try {
                season = tmdbService.season(item, 1, tmdbConfig);
            } catch (Exception ignored) {
            }

            // 第1季失败，尝试第0季（特别篇）
            if (season == null) {
                try {
                    seasonNumber = 0;
                    season = tmdbService.season(item, 0, tmdbConfig);
                } catch (Exception ignored) {
                }
            }

            if (season == null) return false;

            List<TmdbEpisode> episodes = tmdbService.episodes(season, tmdbConfig, item.getTmdbId(), seasonNumber);
            if (episodes.isEmpty()) return false;

            // 先排序集数
            com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(vod);

            // 应用标题到每个 Episode
            boolean changed = false;
            for (Flag flag : vod.getFlags()) {
                for (Episode episode : flag.getEpisodes()) {
                    TmdbEpisode tmdbEp = findEpisodeByNumber(episodes, episode.getNumber());
                    if (tmdbEp != null) {
                        if (episode.getTmdbEpisode() == null) changed = true;
                        episode.setTmdbEpisode(tmdbEp);
                        if (!tmdbEp.getTitle().isEmpty()) {
                            String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(episode.getNumber(), tmdbEp.getTitle()), Setting.isTmdbEpisodeFileSize());
                            if (TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                            episode.setDisplayName(displayName);
                            changed = true;
                        }
                    }
                }
            }
            SpiderDebug.log("tmdb", "应用集数标题: %d 集", episodes.size());
            return changed;
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "获取集数信息失败: %s", e.getMessage());
            return false;
        }
    }

    private TmdbEpisode findEpisodeByNumber(List<TmdbEpisode> episodes, int number) {
        for (TmdbEpisode ep : episodes) {
            if (ep.getNumber() == number) return ep;
        }
        return null;
    }

    /**
     * 评分文本，形如 "8.6"，无评分返回空串。
     */
    public String getRatingText() {
        if (tmdbDetail == null) return "";
        if (!tmdbDetail.has("vote_average") || tmdbDetail.get("vote_average").isJsonNull()) return "";
        double vote = tmdbDetail.get("vote_average").getAsDouble();
        return vote <= 0 ? "" : String.format(Locale.US, "%.1f", vote);
    }

    /**
     * 类型文本，形如 "剧情 / 动作"，无类型返回空串。
     */
    public String getGenresText() {
        if (tmdbDetail == null || !tmdbDetail.has("genres")) return "";
        JsonElement element = tmdbDetail.get("genres");
        if (!element.isJsonArray()) return "";
        JsonArray genres = element.getAsJsonArray();
        List<String> names = new ArrayList<>();
        for (JsonElement g : genres) {
            if (!g.isJsonObject()) continue;
            JsonObject obj = g.getAsJsonObject();
            if (obj.has("name") && !obj.get("name").isJsonNull()) names.add(obj.get("name").getAsString());
        }
        return TextUtils.join(" / ", names);
    }

    /**
     * 年份文本，取首播/上映日期的年份，无则返回空串。
     */
    public String getYear() {
        if (tmdbDetail == null) return "";
        String date = readString(tmdbDetail, "first_air_date");
        if (TextUtils.isEmpty(date)) date = readString(tmdbDetail, "release_date");
        if (TextUtils.isEmpty(date) || date.length() < 4) return "";
        return date.substring(0, 4);
    }

    /**
     * 地区文本，优先取制片国家名称，其次原产国代码，无则返回空串。
     */
    public String getArea() {
        if (tmdbDetail == null) return "";
        if (tmdbDetail.has("production_countries") && tmdbDetail.get("production_countries").isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("production_countries")) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                String name = readString(obj, "name");
                if (!TextUtils.isEmpty(name)) names.add(name);
                if (names.size() >= 2) break;
            }
            if (!names.isEmpty()) return TextUtils.join(" / ", names);
        }
        if (tmdbDetail.has("origin_country") && tmdbDetail.get("origin_country").isJsonArray()) {
            List<String> codes = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("origin_country")) {
                if (e.isJsonNull()) continue;
                String code = e.getAsString();
                if (!TextUtils.isEmpty(code)) codes.add(code);
                if (codes.size() >= 2) break;
            }
            if (!codes.isEmpty()) return TextUtils.join(" / ", codes);
        }
        return "";
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    /**
     * 获取剧照列表（backdrops）。
     */
    public List<String> getPhotos() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.photos(tmdbDetail, tmdbConfig, preferLandscapeBackground());
    }

    private boolean preferLandscapeBackground() {
        return !Util.isMobile() || ResUtil.isPad() || ResUtil.getScreenWidth(activity) >= ResUtil.getScreenHeight(activity);
    }

    /**
     * 获取主创团队（导演、编剧、制片）。
     */
    public List<TmdbPerson> getCreators() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.creators(tmdbDetail, tmdbConfig);
    }

    /**
     * 获取推荐影片（recommendations + similar 合并去重）。
     */
    public List<TmdbItem> getRecommendations() {
        return recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
    }

    public boolean hasMoreRecommendations() {
        return recommendationHasMore;
    }

    public boolean hasMorePersonalTmdbRecommendations() {
        return personalTmdbPage != null && personalTmdbPage.hasMore();
    }

    public boolean hasMorePersonalDoubanRecommendations() {
        return personalDoubanPage != null && personalDoubanPage.hasMore();
    }

    public boolean hasMorePersonalAiRecommendations() {
        return personalAiPage != null && personalAiPage.hasMore();
    }

    public void loadMoreRecommendations(LoadMoreCallback callback) {
        if (recommendationLoading || !recommendationHasMore || tmdbItem == null || tmdbDetail == null) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        int nextPage = recommendationPage + 1;
        recommendationLoading = true;
        Task.execute(() -> {
            List<TmdbItem> next = new ArrayList<>();
            boolean more = false;
            try {
                List<TmdbItem> pageRecommendations = tmdbService.recommendations(tmdbItem, tmdbConfig, nextPage);
                List<TmdbItem> pageSimilar = tmdbService.similar(tmdbItem, tmdbConfig, nextPage);
                next = PersonalRecommendationService.rankTmdbItemsForContext(tmdbDetail, pageRecommendations, pageSimilar, Integer.MAX_VALUE);
                more = !pageRecommendations.isEmpty() || !pageSimilar.isEmpty();
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "load more recommendations failed page=%d error=%s", nextPage, e.getMessage());
            }
            List<TmdbItem> loadedItems = next;
            boolean hasMore = more;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                recommendationLoading = false;
                recommendationPage = nextPage;
                recommendationHasMore = hasMore;
                boolean changed = appendUnique(recommendations, loadedItems);
                if (callback != null) callback.onLoaded(changed);
            });
        });
    }

    public void loadMorePersonalTmdbRecommendations(LoadMoreCallback callback) {
        loadMorePersonalRecommendations(true, callback);
    }

    public void loadMorePersonalDoubanRecommendations(LoadMoreCallback callback) {
        loadMorePersonalRecommendations(false, callback);
    }

    public void loadMorePersonalAiRecommendations(LoadMoreCallback callback) {
        if (callback != null) callback.onLoaded(false);
    }

    public void refreshPersonalRecommendations(LoadMoreCallback callback) {
        if (personalRefreshLoading || tmdbDetail == null) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        personalRefreshLoading = true;
        Task.execute(() -> {
            boolean changed = false;
            PersonalRecommendationService.RecommendationPages pages = PersonalRecommendationService.RecommendationPages.empty();
            boolean aiChanged = false;
            try {
                PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                String tmdbFingerprint = service.historyFingerprint(vod, true);
                String doubanFingerprint = service.historyFingerprint(vod, false);
                String aiFingerprint = service.aiFingerprint(vod, tmdbItem);
                boolean sameTmdb = personalTmdbPage != null && personalTmdbPage.getHistoryFingerprint().equals(tmdbFingerprint);
                boolean sameDouban = personalDoubanPage != null && personalDoubanPage.getHistoryFingerprint().equals(doubanFingerprint);
                boolean sameAi = personalAiPage != null && personalAiPage.getHistoryFingerprint().equals(aiFingerprint);
                if (!sameTmdb || !sameDouban) {
                    pages = service.loadPage(vod, tmdbItem, tmdbDetail, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                    changed = true;
                }
                aiChanged = !sameAi;
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "refresh personal recommendations failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPages loadedPages = pages;
            boolean hasChanged = changed;
            boolean hasAiChanged = aiChanged;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                personalRefreshLoading = false;
                if (hasChanged) {
                    personalTmdbPage = loadedPages.getTmdb();
                    personalDoubanPage = loadedPages.getDouban();
                    personalTmdbRecommendations = personalTmdbPage.getItems();
                    personalDoubanRecommendations = personalDoubanPage.getItems();
                }
                if (callback != null) callback.onLoaded(hasChanged);
                if (hasAiChanged) loadPersonalAiRecommendationsAsync(vod, tmdbItem, generation);
            });
        });
    }

    private void loadMorePersonalRecommendations(boolean tmdb, LoadMoreCallback callback) {
        PersonalRecommendationService.RecommendationPage page = tmdb ? personalTmdbPage : personalDoubanPage;
        if (page == null || !page.hasMore() || (tmdb ? personalTmdbLoading : personalDoubanLoading)) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        if (tmdb) personalTmdbLoading = true;
        else personalDoubanLoading = true;
        Task.execute(() -> {
            PersonalRecommendationService.RecommendationPage nextPage;
            try {
                PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                nextPage = tmdb
                        ? service.loadTmdbPage(vod, tmdbItem, tmdbDetail, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                        : service.loadDoubanPage(vod, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "load more personal recommendations failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                if (tmdb) {
                    personalTmdbLoading = false;
                    personalTmdbPage = loadedPage;
                    boolean changed = appendUnique(personalTmdbRecommendations, loadedPage.getItems());
                    if (callback != null) callback.onLoaded(changed);
                } else {
                    personalDoubanLoading = false;
                    personalDoubanPage = loadedPage;
                    boolean changed = appendUnique(personalDoubanRecommendations, loadedPage.getItems());
                    if (callback != null) callback.onLoaded(changed);
                }
            });
        });
    }

    private boolean hasMoreTmdbRelatedPages(JsonObject detail, String key) {
        JsonObject object = detail != null && detail.has(key) && detail.get(key).isJsonObject() ? detail.getAsJsonObject(key) : null;
        if (object == null || !object.has("total_pages") || object.get("total_pages").isJsonNull()) return false;
        try {
            return object.get("total_pages").getAsInt() > 1;
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean appendUnique(List<TmdbItem> target, List<TmdbItem> source) {
        if (target == null || source == null || source.isEmpty()) return false;
        boolean changed = false;
        for (TmdbItem item : source) {
            if (item == null || containsRecommendation(target, item)) continue;
            target.add(item);
            changed = true;
        }
        return changed;
    }

    private boolean containsRecommendation(List<TmdbItem> items, TmdbItem target) {
        if (items == null || target == null) return false;
        for (TmdbItem item : items) if (sameRecommendation(item, target)) return true;
        return false;
    }

    private boolean sameRecommendation(TmdbItem first, TmdbItem second) {
        if (first == null || second == null) return false;
        if (first.getTmdbId() > 0 && second.getTmdbId() > 0) {
            return first.getTmdbId() == second.getTmdbId() && TextUtils.equals(first.getMediaType(), second.getMediaType());
        }
        String firstTitle = normalizeRecommendationTitle(first.getTitle());
        String secondTitle = normalizeRecommendationTitle(second.getTitle());
        return !TextUtils.isEmpty(firstTitle) && firstTitle.equals(secondTitle);
    }

    private String normalizeRecommendationTitle(String text) {
        return TextUtils.isEmpty(text) ? "" : text.replaceAll("[\\s·•・._\\-/\\\\|()（）\\[\\]【】《》<>:：,，.。]+", "").trim().toLowerCase(Locale.ROOT);
    }
}
