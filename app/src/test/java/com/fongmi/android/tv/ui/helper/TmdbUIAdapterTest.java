package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbUIAdapterTest {

    @Test
    public void applyTmdbTitle_updatesVodNameToScrapedTitle() {
        Vod vod = new FakeVod();
        vod.setName("源站标题");
        TmdbItem item = new TmdbItem(123, "tv", "刮削后的标题", "", "", "", "");

        assertTrue(TmdbUIAdapter.applyTmdbTitle(vod, item));

        assertEquals("刮削后的标题", vod.getName());
    }

    @Test
    public void episodeLineMappingFallsBackToPositionWhenSourceNumbersRepeat() {
        List<TmdbEpisode> tmdbEpisodes = new ArrayList<>();
        for (int number = 1; number <= 8; number++) {
            tmdbEpisodes.add(new TmdbEpisode(number, "Episode " + number, "", "", "https://image.test/" + number + ".jpg", 0, 0));
        }
        List<Episode> repeatedNumberLine = List.of(
                Episode.create("S01E01", "https://source.test/1"),
                Episode.create("S01E02", "https://source.test/2"),
                Episode.create("S01E03", "https://source.test/3"),
                Episode.create("S01E04", "https://source.test/4"),
                Episode.create("S01E04", "https://source.test/5"),
                Episode.create("S01E05", "https://source.test/6"),
                Episode.create("S01E06", "https://source.test/7"),
                Episode.create("S01E06", "https://source.test/8"));
        List<Episode> reliableLine = List.of(
                Episode.create("E01", "https://other.test/1"),
                Episode.create("E02", "https://other.test/2"),
                Episode.create("E03", "https://other.test/3"),
                Episode.create("E04", "https://other.test/4"),
                Episode.create("E05", "https://other.test/5"),
                Episode.create("E06", "https://other.test/6"),
                Episode.create("E07", "https://other.test/7"),
                Episode.create("E08", "https://other.test/8"));

        assertTrue(TmdbUIAdapter.shouldUseEpisodePosition(repeatedNumberLine, tmdbEpisodes));
        assertEquals(5, TmdbUIAdapter.resolveEpisodeNumber(repeatedNumberLine.get(4), 4, true));
        assertFalse(TmdbUIAdapter.shouldUseEpisodePosition(reliableLine, tmdbEpisodes));
        assertEquals(5, TmdbUIAdapter.resolveEpisodeNumber(reliableLine.get(4), 4, false));

        TmdbEpisode cachedWithoutStill = new TmdbEpisode(5, "Episode 5", "", "", "", 0, 0);
        TmdbEpisode refreshedWithStill = new TmdbEpisode(5, "Episode 5", "", "", "https://image.test/5.jpg", 0, 0);
        assertTrue(TmdbUIAdapter.hasEpisodeMetadataChanged(cachedWithoutStill, refreshedWithStill));
        assertFalse(TmdbUIAdapter.hasEpisodeMetadataChanged(refreshedWithStill, refreshedWithStill));
    }

    @Test
    public void autoMatchSkipsCachedSplitSeasonVariantBeforeSearching() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cacheHit = source.indexOf("auto match cache hit");
        int skipCheck = source.indexOf("isCachedSplitSeasonMismatch(videoName, vod, matched)", cacheHit);
        int search = source.indexOf("tmdbMatcher.searchAndMatch(title, vod)", cacheHit);

        assertTrue("TMDB UI adapter must check cached matches for split-season duplicates", skipCheck > cacheHit);
        assertTrue("split-season cache check must run before falling back to TMDB search", search > skipCheck);
        assertTrue("split-season cache check must use TMDB detail original_name/name fields",
                source.contains("TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(videoName, vod), detail)"));
    }

    @Test
    public void autoMatchTriesCleanedTitleCandidatesBeforeAiFallback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private TmdbItem searchResolvedMatch");
        int originalSearch = source.indexOf("tmdbMatcher.searchAndMatch(title, vod)", helper);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", originalSearch);
        int aiFallback = source.indexOf("resolver.resolveWithAiFallback(request)", originalSearch);

        assertTrue(sourcePath + " is missing searchResolvedMatch", helper >= 0);
        assertTrue("TMDB auto match must try code-cleaned title candidates after original candidates",
                cleaned > originalSearch);
        assertTrue("TMDB auto match must try code-cleaned title candidates before AI fallback",
                aiFallback > cleaned);
    }

    @Test
    public void loadDetailNormalizesCachedOrPassedTitleFromTmdbDetail() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void loadDetailSync");
        int detail = source.indexOf("tmdbService.detail(item, tmdbConfig, false)", method);
        int normalize = source.indexOf("item = normalizeLoadedItem(item, detail);", detail);
        int assign = source.indexOf("tmdbItem = item;", normalize);
        int save = source.indexOf("saveMatch(vod, item);", assign);

        assertTrue(sourcePath + " is missing loadDetailSync", method >= 0);
        assertTrue("TMDB detail load must normalize noisy cached/passed titles from detail response",
                detail > method && normalize > detail && assign > normalize);
        assertTrue("normalized TMDB title must be written back to the match cache",
                save > assign);
    }

    @Test
    public void directTmdbLoadConsumesMemoryDetailCacheBeforeServiceRequest() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("public void load(TmdbItem item, Vod vod)");
        int call = source.indexOf("TmdbDetailCache.Entry cached = takeTmdbDetailCache(item);", load);
        int loadCached = source.indexOf("Task.execute(() -> loadDetailSync(vod, cached.getItem(), cached.getDetail(), cached.getCast(), generation))", call);
        int helper = source.indexOf("private TmdbDetailCache.Entry takeTmdbDetailCache", loadCached);
        int take = source.indexOf("TmdbDetailCache.take", helper);
        int sync = source.indexOf("private void loadDetailSync(Vod vod, TmdbItem item, JsonObject cachedDetail", helper);
        int service = source.indexOf("tmdbService.detail(item, tmdbConfig, false)", sync);

        assertTrue(sourcePath + " is missing TMDB direct load method", load >= 0);
        assertTrue("direct TMDB playback should consume memory detail cache asynchronously before disk/network detail load",
                call > load && loadCached > call && take > helper && service > sync);
    }

    @Test
    public void tmdbVodRefreshesAreCoalescedAndStartupWorkIsDeferred() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int constants = source.indexOf("VOD_REFRESH_COALESCE_MS");
        int backgroundDelay = source.indexOf("TMDB_STARTUP_BACKGROUND_DELAY_MS", constants);
        int firstRefresh = source.indexOf("notifyVodChanged(vod, generation);", backgroundDelay);
        int deferredLoads = source.indexOf("scheduleStartupBackgroundLoads(vod, item, detail, generation);", firstRefresh);
        int scheduler = source.indexOf("private void scheduleStartupBackgroundLoads", deferredLoads);
        int episode = source.indexOf("loadEpisodeTitlesAsync(vod, item, generation);", scheduler);
        int related = source.indexOf("loadRelatedRecommendationsAsync(vod, item, detail, generation);", episode);
        int personal = source.indexOf("loadPersonalRecommendationsAsync(vod, item, detail, generation);", related);
        int notify = source.indexOf("private void notifyVodChanged");
        int pending = source.indexOf("pendingVodRefresh", notify);
        int post = source.indexOf("App.post(pendingVodRefresh, VOD_REFRESH_COALESCE_MS);", pending);
        int relatedMethod = source.indexOf("private void loadRelatedRecommendationsAsync");
        int relatedNotify = source.indexOf("notifyVodChanged(vod, generation);", relatedMethod);
        int personalMethod = source.indexOf("private void loadPersonalRecommendationsAsync");
        int personalNotify = source.indexOf("notifyVodChanged(vod, generation);", personalMethod);

        assertTrue(sourcePath + " is missing TMDB playback refresh throttle constants", constants >= 0 && backgroundDelay > constants);
        assertTrue("TMDB detail should queue one lightweight VOD refresh before deferred background work",
                firstRefresh > backgroundDelay && deferredLoads > firstRefresh);
        assertTrue("episode titles and recommendation loads should start after the first-frame delay",
                scheduler > deferredLoads && episode > scheduler && related > episode && personal > related);
        assertTrue("VOD refreshes should be coalesced on the main thread instead of posting every async result",
                notify >= 0 && pending > notify && post > pending);
        assertTrue("related and personal recommendation completion should reuse the coalesced VOD refresh path",
                relatedNotify > relatedMethod && personalNotify > personalMethod);
    }

    @Test
    public void videoActivityAppliesCachedVodDetailAfterFirstFrameOpportunity() throws Exception {
        assertVideoActivityDefersCachedDetail("mobile");
        assertVideoActivityDefersCachedDetail("leanback");
    }

    @Test
    public void leanbackDirectTmdbPlaybackStartsBeforeFullCachedDetailBind() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean setCachedTmdbDetail()");
        int fastBranch = source.indexOf("if (tryStartFastTmdbPlayback(cached))", method);
        int fastReturn = source.indexOf("return true;", fastBranch);
        int waitService = source.indexOf("shouldWaitForPlaybackService()", fastReturn);
        int queueService = source.indexOf("queueFastTmdbPlaybackUntilServiceReady(cached);", waitService);
        int service = source.indexOf("protected void onServiceConnected()");
        int flush = source.indexOf("flushPendingFastTmdbPlayback();", service);
        int flushMethod = source.indexOf("private void flushPendingFastTmdbPlayback()", method);
        int flushFast = source.indexOf("if (tryStartFastTmdbPlayback(item)) return;", flushMethod);
        int fallbackPost = source.indexOf("mBinding.getRoot().postDelayed(() ->", waitService);
        int fallbackBind = source.indexOf("setDetail(Result.vod(item));", fallbackPost);
        int fastMethod = source.indexOf("private boolean tryStartFastTmdbPlayback(Vod item)", method);
        int reveal = source.indexOf("showFastTmdbPlaybackContent();", fastMethod);
        int serviceWaitInFastPath = source.indexOf("if (shouldWaitForPlaybackService())", reveal);
        int queueInFastPath = source.indexOf("queueFastTmdbPlaybackUntilServiceReady(item);", serviceWaitInFastPath);
        int postStart = source.indexOf("App.post(mPendingFastTmdbPlaybackStart, TMDB_FAST_PLAYBACK_START_DELAY_MS);", reveal);
        int startPending = source.indexOf("private void startPendingFastTmdbPlayback()", postStart);
        int sort = source.indexOf("TmdbEpisodeSorter.sort(item)", startPending);
        int flag = source.indexOf("findFastTmdbPlaybackFlag(item)", startPending);
        int episode = source.indexOf("findFastTmdbPlaybackEpisode(flag)", flag);
        int firstPrepare = source.indexOf("prepareFastTmdbPlaybackHistory(item, flag, episode);", fastMethod);
        int prepare = source.indexOf("prepareFastTmdbPlaybackHistory(item, flag, episode);", startPending);
        int firstHistoryLookup = source.indexOf("History.findPlayback", fastMethod);
        int player = source.indexOf("mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());", prepare);
        int fullBind = source.indexOf("applyFastTmdbPlaybackFullDetailNextFrame(item);", player);
        int canApply = source.indexOf("private boolean canApplyPlayerResult()");
        int fastCanApply = source.indexOf("mFastPlaybackFlag != null && mFastPlaybackEpisode != null && mHistory != null", canApply);
        int actionVisibility = source.indexOf("setOriginalEnhancedActionVisibility(loadTmdbDetail && (Setting.isOriginalEnhancedDetailPage() || isIntentTmdbPlayback()));");
        int checkFlag = source.indexOf("private void checkFlag(Vod item)");
        int fastCheckFlag = source.indexOf("mFastTmdbPlaybackStarted && mFastPlaybackFlag != null && mFastPlaybackEpisode != null", checkFlag);
        int bindEpisodes = source.indexOf("setEpisodeAdapter(mFastPlaybackFlag.getEpisodes(), false);", fastCheckFlag);
        int normalClick = source.indexOf("onItemClick(mHistory.getFlag());", bindEpisodes);

        assertTrue(sourcePath + " is missing direct TMDB fast playback cache branch", method >= 0 && fastBranch > method && fastReturn > fastBranch);
        assertTrue("cached direct TMDB playback should wait for the playback service instead of falling back to full detail binding",
                waitService > fastReturn && queueService > waitService && service >= 0 && flush > service && flushMethod > method && flushFast > flushMethod);
        assertTrue("cached source detail fallback bind should stay behind the fast playback branch", fallbackPost > waitService && fallbackBind > fallbackPost);
        assertTrue("direct TMDB playback should reveal the playback page before episode/history/player startup work",
                fastMethod > method && reveal > fastMethod && postStart > reveal && startPending > postStart && flag > startPending && episode > flag && firstPrepare == prepare && prepare > startPending && player > prepare);
        assertTrue("direct TMDB playback content reveal should not wait for playback service connection",
                serviceWaitInFastPath > reveal && queueInFastPath > serviceWaitInFastPath);
        assertTrue("direct TMDB playback should not sort or format the episode list before requesting playback",
                sort > player && source.indexOf("applyTmdbEpisodeTitles(item);", startPending) > player);
        assertTrue("direct TMDB playback should not query playback history before revealing content",
                firstHistoryLookup > startPending);
        assertTrue("direct TMDB playback should bind the full native-enhanced layout only after requesting playback",
                fullBind > player && fastCheckFlag > checkFlag && bindEpisodes > fastCheckFlag && normalClick > bindEpisodes);
        assertTrue("direct TMDB playback should use the native-enhanced action button set",
                actionVisibility > method);
        assertTrue("player results from the fast path must be accepted before adapters are fully bound",
                canApply > player && fastCanApply > canApply);
    }

    @Test
    public void leanbackDirectTmdbPlaybackHydratesSynopsisWithoutFullDetailBind() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int startPending = source.indexOf("private void startPendingFastTmdbPlayback()");
        int prepareItem = source.indexOf("prepareFastTmdbPlaybackItem(item);", startPending);
        int player = source.indexOf("mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());", prepareItem);
        int postHydrate = source.indexOf("mBinding.getRoot().post(() -> hydrateFastTmdbPlaybackDetail(item));", player);
        int hydrate = source.indexOf("private void hydrateFastTmdbPlaybackDetail(Vod item)");
        int hydrateEnd = source.indexOf("private String firstNonEmpty", hydrate);
        String hydrateBody = source.substring(hydrate, hydrateEnd);

        assertTrue(sourcePath + " is missing direct TMDB playback summary hydration", startPending >= 0 && prepareItem > startPending && player > prepareItem && postHydrate > player);
        assertTrue("direct TMDB playback should hydrate the synopsis from source detail or TMDB intent cache",
                hydrateBody.contains("applyFastTmdbDetailCache(item)")
                        && hydrateBody.contains("getTmdbVodContent()")
                        && hydrateBody.contains("mBinding.content.setTag(content);"));
        assertTrue("direct TMDB playback should consume colorful detail's cached TMDB detail for synopsis",
                source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem())")
                        && source.contains("cachedTmdbOverview(detail)")
                        && source.contains("cachedTmdbOverviewForLanguage(translations, \"zh-CN\")"));
        assertTrue("direct TMDB playback should restore the visible TMDB overview without rebuilding detail adapters",
                hydrateBody.contains("mBinding.tmdbOverview.setSingleLine(false);")
                        && hydrateBody.contains("mBinding.tmdbOverview.setHorizontallyScrolling(false);")
                        && hydrateBody.contains("mBinding.tmdbOverview.setText(getString(R.string.detail_content, content));"));
        assertTrue("direct TMDB playback should restore the cached backdrop as the playback page background",
                hydrateBody.contains("String wall = firstNonEmpty(cachedFastTmdbBackdrop(), getWallPic(), mHistory == null ? \"\" : mHistory.getWallPic());")
                        && hydrateBody.contains("if (!TextUtils.isEmpty(wall)) setContextWall(wall);")
                        && hydrateBody.contains("if (!TextUtils.isEmpty(wall)) mHistory.setWallPic(wall);")
                        && source.contains("cachedTmdbImage(cached.getDetail(), \"backdrop_path\", true)")
                        && source.contains("base = backdrop ? config.getBackdropBase() : config.getImageBase();"));
        assertTrue("direct TMDB launch should carry the matched TMDB backdrop into the playback page",
                source.contains("String wallPic = item == null ? \"\" : item.getBackdropUrl();")
                        && source.contains("intent.putExtra(\"wallPic\", wallPic);"));
        assertTrue("fast summary hydration must stay lightweight",
                hydrateBody.indexOf("setDetail(Result.vod(item))") < 0
                        && hydrateBody.indexOf("updateVod(item)") < 0
                        && hydrateBody.indexOf("updateFlag(") < 0
                        && hydrateBody.indexOf("setPartAdapter(") < 0
                        && hydrateBody.indexOf("applyTmdbEpisodeTitles(item)") < 0
                        && source.indexOf("tmdbService.detail(item", hydrate) < 0);
    }

    @Test
    public void leanbackVideoActivityReusesBackdropSlideshowForSamePhotos() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int field = source.indexOf("private String mBackdropSignature");
        int method = source.indexOf("private void setupBackdropSlideshow");
        int signature = source.indexOf("String signature = backdropSignature(photos);", method);
        int same = source.indexOf("if (TextUtils.equals(mBackdropSignature, signature))", signature);
        int setItems = source.indexOf("mBackdropAdapter.setItems(photos);", same);
        int save = source.indexOf("mBackdropSignature = signature;", setItems);
        int helper = source.indexOf("private String backdropSignature", method);

        assertTrue(sourcePath + " is missing cached backdrop signature state", field >= 0);
        assertTrue("same TMDB photo list should not rebuild backdrop adapter or restart auto-scroll",
                method >= 0 && signature > method && same > signature && setItems > same && save > setItems && helper > method);
    }

    @Test
    public void leanbackVideoActivityDefersInitialTmdbBindUntilContentReveal() throws Exception {
        Path sourcePath = findFlavorJavaPath("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int revealDelay = source.indexOf("TMDB_BIND_AFTER_REVEAL_DELAY_MS");
        int pending = source.indexOf("private final Runnable mPendingTmdbBind");
        int deferred = source.indexOf("private final Runnable mDeferredTmdbDataBind");
        int refresh = source.indexOf("public void onRefreshEvent(RefreshEvent event)");
        int immediateUpdate = source.indexOf("updateVod(event.getVod());", refresh);
        int queue = source.indexOf("queueTmdbBind(event.getVod());", refresh);
        int player = source.indexOf("private void setPlayer(Result result)");
        int quality = source.indexOf("mQualityAdapter.addAll(result);", player);
        int queueMethod = source.indexOf("private void queueTmdbBind(Vod item)");
        int schedule = source.indexOf("schedulePendingTmdbBindAfterContentReveal();", queueMethod);
        int scheduleMethod = source.indexOf("private void schedulePendingTmdbBindAfterContentReveal()");
        int postAfterReveal = source.indexOf("App.post(mPendingTmdbBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);", scheduleMethod);
        int flush = source.indexOf("private void flushPendingTmdbBind()");
        int flushUpdate = source.indexOf("updateVod(item);", flush);
        int finish = source.indexOf("finishTmdbDetail();", flush);
        int finishEpisode = source.indexOf("finishEpisodeLoading();", finish);
        int dataPending = source.indexOf("mTmdbDataBindPending = true;", finishEpisode);
        int postDeferred = source.indexOf("App.post(mDeferredTmdbDataBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);", dataPending);
        int deferredMethod = source.indexOf("private void applyDeferredTmdbDataBind()", postDeferred);
        int bind = source.indexOf("bindTmdbData();", deferredMethod);
        int reset = source.indexOf("private void resetPendingTmdbBind()");
        int removeCallbacks = source.indexOf("App.removeCallbacks(mPendingTmdbBind, mDeferredTmdbDataBind);", reset);

        assertTrue(sourcePath + " is missing TMDB bind defer constant", revealDelay >= 0);
        assertTrue(sourcePath + " is missing pending TMDB bind runnables", pending >= 0 && deferred > pending);
        assertTrue("VOD refresh should queue TMDB binding instead of updating the detail UI immediately",
                refresh >= 0 && queue > refresh && (immediateUpdate < 0 || immediateUpdate > queueMethod) && flushUpdate > flush);
        assertTrue("player result should not control queued TMDB binding release",
                player >= 0 && quality > player && source.indexOf("schedulePendingTmdbBindAfterPlayerReady") < 0 && source.indexOf("TMDB_BIND_AFTER_PLAYER_READY_DELAY_MS") < 0);
        assertTrue("TMDB binding should be released after content reveal, independent of player ready",
                queueMethod > refresh && schedule > queueMethod && scheduleMethod > queueMethod && postAfterReveal > scheduleMethod);
        assertTrue("queued TMDB binding should reveal detail before posting heavier TMDB grids",
                flush > queueMethod && finish > flush && finishEpisode > finish && dataPending > finishEpisode && postDeferred > dataPending);
        assertTrue("deferred TMDB data binding must still run the original bind path after reveal",
                deferredMethod > postDeferred && bind > deferredMethod);
        assertTrue("pending TMDB bind must be reset when changing or destroying playback detail",
                reset > flush && removeCallbacks > reset);
    }

    @Test
    public void tmdbDetailActivityPassesMemoryDetailCacheKeyToDirectPlayback() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void playDefaultPlayback()");
        int put = source.indexOf("TmdbDetailCache.put(playbackTmdbItem(), matchedTmdbDetail, detailCastItems)", method);
        if (put < 0) put = source.indexOf("TmdbDetailCache.put(item, matchedTmdbDetail, detailCastItems)", method);
        int start = source.indexOf("VideoActivity.startDirectTmdb", put);
        int keyArg = source.indexOf("tmdbDetailCacheKey", start);
        int fastTitles = source.indexOf("fastPlaybackEpisodeTitles()", start);
        int allTitles = source.indexOf("selectedTmdbEpisodeTitles()", method);

        assertTrue(sourcePath + " is missing playDefaultPlayback", method >= 0);
        assertTrue("TMDB detail page should hand in-memory detail to direct playback",
                put > method && start > put && keyArg > start);
        assertTrue("TMDB detail play click should pass only the selected episode title instead of rebuilding every TMDB episode title before launch",
                fastTitles > start && (allTitles < 0 || allTitles > source.indexOf("private ArrayList<String> selectedTmdbEpisodeTitles()")));
    }

    @Test
    public void tmdbDetailActivityProfilesStandaloneSinglePassLoading() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int loadContent = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int loadStart = source.indexOf("load start mode=%d", loadContent);
        int singlePass = source.indexOf("shouldLoadInitialStandaloneTmdbDetailInSinglePass", loadStart);
        int taskLog = source.indexOf("load tasks mode=%d", singlePass);
        int sourceStart = source.indexOf("long sourceStart = System.currentTimeMillis();", taskLog);
        int sourceLog = source.indexOf("source detail cost=%dms", sourceStart);
        int waitStart = source.indexOf("long tmdbWaitStart = System.currentTimeMillis();", sourceLog);
        int waitLog = source.indexOf("tmdb wait cost=%dms", waitStart);
        int singlePassApply = source.indexOf("if (singlePassStandaloneTmdb)", waitLog);
        int applyLoaded = source.indexOf("applyLoaded(finalVod", singlePassApply);
        int applyMethod = source.indexOf("private void applyLoaded(Vod loadedVod", applyLoaded);
        int applyLog = source.indexOf("apply loaded cost=%dms", applyMethod);
        int applyTmdb = source.indexOf("private void applyTmdbResultNow", applyLog);
        int applyTmdbLog = source.indexOf("apply tmdb result cost=%dms", applyTmdb);
        int bundleMethod = source.indexOf("private TmdbBundle loadTmdbBundle", applyTmdbLog);
        int bundleLog = source.indexOf("tmdb bundle cost=%dms", bundleMethod);

        assertTrue(sourcePath + " is missing standalone detail load profiling", loadContent >= 0 && loadStart > loadContent && taskLog > singlePass);
        assertTrue("standalone detail load should measure source detail and TMDB wait separately",
                sourceStart > taskLog && sourceLog > sourceStart && waitStart > sourceLog && waitLog > waitStart);
        assertTrue("standalone TMDB modes should still apply source detail and TMDB bundle together in the single-pass branch",
                singlePassApply > waitLog && applyLoaded > singlePassApply);
        assertTrue("detail page UI binding and TMDB bundle loading must stay observable for emulator verification",
                applyLog > applyMethod && applyTmdbLog > applyTmdb && bundleLog > bundleMethod);
    }

    @Test
    public void tmdbDetailActivityIgnoresPlaybackCallbacksOutsideInlineMode() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int playing = source.indexOf("protected void onPlayingChanged(boolean isPlaying)");
        int playingGuard = source.indexOf("if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;", playing);
        int pip = source.indexOf("updateInlinePiPActions(isPlaying);", playingGuard);
        int size = source.indexOf("protected void onSizeChanged(VideoSize size)");
        int sizeGuard = source.indexOf("if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;", size);
        int sizeUpdate = source.indexOf("updateInlineButtons(service() != null", sizeGuard);

        assertTrue(sourcePath + " is missing TMDB detail playback callback guards", playing >= 0 && size >= 0);
        assertTrue("standalone playback navigation should not let the detail page update PiP actions",
                playingGuard > playing && pip > playingGuard);
        assertTrue("standalone playback navigation should not let the detail page update inline sizing controls",
                sizeGuard > size && sizeUpdate > sizeGuard);
    }

    @Test
    public void tmdbDetailActivityGatesInlineSystemPipUpdatesOnMobileCapability() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int canUse = source.indexOf("private boolean canUseInlineSystemPiP()");
        int mobileGate = source.indexOf("return Util.isMobile() && !PiP.noPiP();", canUse);
        int sourceHelper = source.indexOf("private void updateInlinePiPSource(View view)");
        int sourceGuard = source.indexOf("if (!canUseInlineSystemPiP() || inlinePiP == null || view == null) return;", sourceHelper);
        int sourceUpdate = source.indexOf("inlinePiP.update(this, view);", sourceGuard);
        int actionHelper = source.indexOf("private void updateInlinePiPActions(boolean playing)");
        int actionGuard = source.indexOf("if (!canUseInlineSystemPiP() || inlinePiP == null) return;", actionHelper);
        int actionUpdate = source.indexOf("inlinePiP.update(this, playing);", actionGuard);
        int canEnter = source.indexOf("private boolean canEnterInlinePiP()");
        int enterGate = source.indexOf("return canUseInlineSystemPiP() &&", canEnter);

        assertTrue(sourcePath + " is missing inline system PiP capability gate", canUse >= 0 && mobileGate > canUse);
        assertTrue("inline PiP source-rect updates should be skipped on leanback where TmdbDetailActivity does not support system PiP",
                sourceHelper > canUse && sourceGuard > sourceHelper && sourceUpdate > sourceGuard);
        assertTrue("inline PiP action updates should be skipped on leanback where TmdbDetailActivity does not support system PiP",
                actionHelper > sourceUpdate && actionGuard > actionHelper && actionUpdate > actionGuard);
        assertTrue("inline system PiP entry should share the same capability gate",
                canEnter >= 0 && enterGate > canEnter);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findFlavorJavaPath(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", flavor, "java");
    }

    private static void assertVideoActivityDefersCachedDetail(String flavor) throws Exception {
        Path sourcePath = findFlavorJavaPath(flavor).resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean setCachedTmdbDetail()");
        int take = source.indexOf("VodDetailCache.take(getTmdbVodCacheKey())", method);
        int loading = source.indexOf("mBinding.progressLayout.showProgress();", take);
        int queued = source.indexOf("detail cache hit queued", loading);
        int post = source.indexOf("mBinding.getRoot().postDelayed(() ->", queued);
        int apply = source.indexOf("setDetail(Result.vod(cached));", post);
        if (apply < 0) apply = source.indexOf("setDetail(Result.vod(item));", post);
        int delay = source.indexOf("TMDB_CACHED_DETAIL_APPLY_DELAY_MS", apply);

        assertTrue(sourcePath + " is missing cached TMDB source detail path", method >= 0);
        assertTrue("cached source detail should show playback page loading before applying detail on the next frame",
                take > method && loading > take && queued > loading && post > queued && apply > post && delay > apply);
    }

    private static final class FakeVod extends Vod {

        private String name;

        @Override
        public String getName() {
            return name == null ? "" : name;
        }

        @Override
        public void setName(String vodName) {
            name = vodName;
        }
    }
}
