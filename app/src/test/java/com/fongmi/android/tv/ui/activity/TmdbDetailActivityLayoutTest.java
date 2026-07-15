package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class TmdbDetailActivityLayoutTest {

    @Test
    public void automaticTmdbMatchUsesResolvedMediaTitleBeforeSearching() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private TmdbLoadResult loadTmdbResult()");
        int helper = source.indexOf("private AutoTmdbMatch searchResolvedTmdbMatch()");
        int queryFilter = source.indexOf("private List<String> automaticTmdbQueries");
        int exactTie = source.indexOf("private boolean shouldAcceptFirstExactTmdbCandidate");

        assertTrue(sourcePath + " is missing loadTmdbResult", load >= 0);
        assertTrue("automatic TMDB detail matching must use resolved title candidates before search",
                source.indexOf("AutoTmdbMatch autoMatch = searchResolvedTmdbMatch();", load) > load);
        assertTrue("automatic TMDB detail matching must run MediaTitleResolver for ai-title diagnostics",
                helper > load && source.indexOf("MediaTitleResolver resolver = new MediaTitleResolver();", helper) > helper);
        assertTrue("automatic TMDB detail matching must not fall back to obfuscated raw titles when parser cleaned them",
                queryFilter > helper && source.indexOf("shouldSkipRawTmdbQuery(rawTitle, resolution)", queryFilter) > queryFilter);
        int originalSearch = source.indexOf("AutoTmdbMatch match = searchResolvedTmdbMatch(rawTitle, resolution, attempted);", helper);
        int cleaned = source.indexOf("resolver.queryCleanedTitles(request, 4)", originalSearch);
        int aiFallback = source.indexOf("resolver.resolveWithAiFallback(request)", originalSearch);
        assertTrue("automatic TMDB detail matching must try code-cleaned title candidates before AI fallback",
                originalSearch > helper && cleaned > originalSearch && aiFallback > cleaned);
        assertTrue("automatic TMDB detail matching must accept exact same-title ties from TMDB search order",
                exactTie > 0 && source.indexOf("shouldAcceptFirstExactTmdbCandidate(best, second, keyword, sourceVod)", load) > load);
    }

    @Test
    public void automaticTmdbMatchSkipsStaleCacheWhenParsedTitleDiffers() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cached = source.indexOf("private TmdbItem getCachedTmdbMatch()");
        int compatible = source.indexOf("private boolean isCachedTmdbMatchCompatible");

        assertTrue(sourcePath + " is missing getCachedTmdbMatch", cached >= 0);
        assertTrue("cached TMDB matches must be checked against the current parsed title",
                compatible > cached && source.indexOf("if (!isCachedTmdbMatchCompatible(item)) return null;", cached) > cached);
        assertTrue("stale cached title F must not override parsed title 凡人修仙传",
                source.indexOf("new MediaTitleParser().cleanTitle(getTmdbRawTitle())", compatible) > compatible
                        && source.indexOf("normalize(item.getTitle()).equals(normalize(parsedTitle))", compatible) > compatible);
    }

    @Test
    public void playbackTmdbItemKeepsMatchedTitleForNativeEnhancedHeader() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private TmdbItem playbackTmdbItem()");
        int end = source.indexOf("private Vod playbackTmdbVod()", method);
        String body = source.substring(method, end);

        assertTrue("native enhanced playback must pass the matched TMDB title, not the noisy source title",
                body.contains("matchedTmdbTitle()"));
        assertTrue("native enhanced playback item must not replace TMDB title with vod.getName()",
                !body.contains("TextUtils.isEmpty(vod.getName()) ? matchedTmdbItem.getTitle() : vod.getName()"));
    }

    @Test
    public void tmdbDetailNormalizesCachedTitleBeforeNativeEnhancedPlayback() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int loadBundle = source.indexOf("private TmdbBundle loadTmdbBundle(TmdbItem item)");
        int normalize = source.indexOf("private TmdbItem normalizeTmdbItemTitle", loadBundle);
        int detailTitle = source.indexOf("private String tmdbDetailTitle", normalize);
        int playbackName = source.indexOf("private String playbackHistoryName()");
        int enrichVod = source.indexOf("private void enrichVod()");
        int manual = source.indexOf("private void applyManualTmdb(TmdbItem item)");

        assertTrue("TMDB detail loading must normalize stale cached item titles from the detail payload",
                loadBundle >= 0 && source.indexOf("item = normalizeTmdbItemTitle(item, detail);", loadBundle) > loadBundle);
        assertTrue("title normalization must prefer detail name/title over cached item title",
                normalize > loadBundle && detailTitle > normalize
                        && source.indexOf("tmdbDetailTitle(item, detail)", normalize) > normalize
                        && source.indexOf("string(detail, \"name\")", detailTitle) > detailTitle
                        && source.indexOf("string(detail, \"title\")", detailTitle) > detailTitle);
        assertTrue("native enhanced playback history name must use normalized TMDB title",
                playbackName >= 0 && source.indexOf("coalesce(matchedTmdbTitle()", playbackName) > playbackName);
        assertTrue("detail page vod title must use normalized TMDB title",
                enrichVod >= 0 && source.indexOf("String title = matchedTmdbTitle();", enrichVod) > enrichVod);
        assertTrue("manual matching must save the normalized bundle item, not the pre-detail item",
                manual >= 0 && source.indexOf("saveTmdbMatch(bundle.item());", manual) > manual);
    }

    @Test
    public void fusionDetailBackdropDrawsBehindSystemBars() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyDetailEdgeToEdge()");
        int init = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int theme = source.indexOf("private void applyDetailTheme()");

        assertTrue(sourcePath + " is missing applyDetailEdgeToEdge", method >= 0);
        assertTrue("TMDB detail must draw the backdrop behind the system bars",
                source.indexOf("WindowCompat.setDecorFitsSystemWindows(window, false)", method) > method);
        assertTrue("TMDB detail status bar must stay transparent over the backdrop",
                source.indexOf("window.setStatusBarColor(Color.TRANSPARENT)", method) > method);
        assertTrue("TMDB detail navigation bar must stay transparent over the backdrop",
                source.indexOf("window.setNavigationBarColor(Color.TRANSPARENT)", method) > method);
        assertTrue("TMDB detail must keep system bar icon contrast in sync with the detail theme",
                source.indexOf("setAppearanceLightStatusBars", method) > method);
        assertTrue("TMDB detail must configure edge-to-edge during initialization",
                source.indexOf("applyDetailEdgeToEdge();", init) > init);
        assertTrue("TMDB detail must re-apply edge-to-edge after theme changes",
                source.indexOf("applyDetailEdgeToEdge();", theme) > theme);
    }

    @Test
    public void fusionInlinePlayerButtonsUsePlayerButtonSettings() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyInlinePlayerButtonSettings()");
        int update = source.indexOf("private void updateInlineButtons(boolean playing)");
        int call = source.indexOf("applyInlinePlayerButtonSettings();", update);

        assertTrue(sourcePath + " is missing applyInlinePlayerButtonSettings", method >= 0);
        assertTrue("inline player buttons must apply settings after dynamic visibility is recalculated", call > update);
        assertTrue("wide fusion buttons must use PlayerButtonSetting order and visibility",
                source.indexOf("PlayerButtonSetting.applyOrder((ViewGroup) binding.playerActionRow.getChildAt(0)", method) > method);
        assertTrue("fusion fullscreen button must be mapped to player button settings",
                source.indexOf("buttons.put(PlayerButtonSetting.FULLSCREEN, binding.playerFullscreenAction)", method) > method);
        assertTrue("fusion refresh button must be mapped so hiding reset hides refresh",
                source.indexOf("buttons.put(PlayerButtonSetting.RESET, binding.playerRefresh)", method) > method);
        assertTrue("fusion source button must be mapped to the change setting",
                source.indexOf("buttons.put(PlayerButtonSetting.CHANGE, binding.playerChangeSource)", method) > method);
    }

    @Test
    public void fusionInlineSettingsButtonOpensFullPlayerControls() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int setup = source.indexOf("private void setupMobileInlineControl()");
        int setupEnd = source.indexOf("private void setupMobileInlineParse()", setup);
        String body = source.substring(setup, setupEnd);

        assertTrue("fusion settings button must open the full player control dialog",
                body.contains("detailControlView(R.id.setting, View.class).setOnClickListener(guarded(this::showInlineControlDialog));"));
        assertTrue("fusion settings button must not open the display-only dialog",
                !body.contains("detailControlView(R.id.setting, View.class).setOnClickListener(guarded(this::showInlineDisplay));"));

        Path dialogPath = Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java");
        if (!Files.exists(dialogPath)) dialogPath = Path.of("app").resolve(dialogPath);
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int inline = dialog.indexOf("public ControlDialog inline(TmdbDetailActivity activity)");
        int inlineEnd = dialog.indexOf("public ControlDialog history(History history)", inline);
        String inlineBody = dialog.substring(inline, inlineEnd);

        assertTrue("fusion control dialog must resolve duplicate button IDs from the inline action root",
                inlineBody.contains("activity.inlineControlDialogAction(R.id.danmaku)")
                        && !inlineBody.contains("activity.findViewById(R.id.danmaku)"));
    }

    @Test
    public void fusionInlinePlayerButtonOrderMatchesNativeLeanbackPlayer() throws Exception {
        String nativeLayout = readLeanbackLayout("view_control_vod_action.xml");
        String fusionLayout = readLayout("activity_tmdb_detail.xml");
        List<String> nativeOrder = List.of("next", "prev", "episodes", "reset", "search", "fullscreen", "player", "decode", "playParams", "speed", "scale", "actionQuality", "lut", "text", "audio", "video", "opening", "ending", "danmaku", "title", "repeat");
        List<String> fusionOrder = List.of("playerNext", "playerPrev", "playerEpisodes", "playerRefresh", "playerChangeSource", "playerFullscreenAction", "playerExternal", "playerDecode", "playerPlayParams", "playerSpeed", "playerScale", "playerQuality", "playerLut", "playerTextTrack", "playerAudioTrack", "playerVideoTrack", "playerOpening", "playerEnding", "playerDanmaku", "playerChapter", "playerRepeat");

        assertAndroidIdOrder("native leanback player control order", nativeLayout, nativeOrder);
        assertAndroidIdOrder("fusion inline player control order", fusionLayout, fusionOrder);

        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private void setupHorizontalFocusChain()");
        int scale = source.indexOf("binding.playerScale", method);
        int quality = source.indexOf("binding.playerQuality", method);
        int lut = source.indexOf("binding.playerLut", method);

        assertTrue("fusion inline focus chain must keep 画质 before LUT like native leanback player",
                method >= 0 && scale > method && scale < quality && quality < lut);
    }

    @Test
    public void tvDetailActionButtonsUseUnifiedSourceKeepTmdbThemeOrder() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");

        assertAndroidIdOrder("fusion detail action order", layout, List.of("changeSource", "keepFusion", "rematchFusion", "themeMode"));
        assertAndroidIdOrder("panel detail action order", layout, List.of("changeSourceDetail", "keep", "rematch", "themeModeDetail"));
    }

    @Test
    public void themeActionButtonsHaveFallbackTextBeforeRuntimeThemeRefresh() throws Exception {
        String detailLayout = readLayout("activity_tmdb_detail.xml");
        String headerLayout = readLayout("view_tmdb_header.xml");
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int init = source.indexOf("private void initPage()");
        int labelRefresh = source.indexOf("updateThemeModeButtonLabels();", init);
        int visibilityRefresh = source.indexOf("updateDetailThemeButtonVisibility();", init);

        assertAndroidIdHasAttribute("top theme action", detailLayout, "themeModeTop", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("fusion theme action", detailLayout, "themeMode", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("panel theme action", detailLayout, "themeModeDetail", "android:text=\"@string/detail_theme_light\"");
        assertAndroidIdHasAttribute("TMDB header theme action", headerLayout, "tmdbThemeToggle", "android:text=\"@string/detail_theme_light\"");
        assertTrue("TMDB detail must set theme action labels before applying their initial visibility",
                init >= 0 && labelRefresh > init && visibilityRefresh > labelRefresh);
    }

    @Test
    public void playbackPageHidesThemeActionsWhileEnhancedDetailKeepsThem() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int method = source.indexOf("private void updateDetailThemeButtonVisibility()");
        int methodEnd = source.indexOf("private void applyTemplateCardChrome", method);
        String body = source.substring(method, methodEnd);

        assertTrue("playback page must be detected independently from the detail style",
                body.contains("boolean playbackPage = isAutoPlayMode() || detailPlayerActive;"));
        assertTrue("playback page must hide the fusion-row theme action",
                body.contains("binding.themeMode.setVisibility(playbackPage ? View.GONE : (fusionMode ? (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE) : (showLargeScreenButton ? View.VISIBLE : View.GONE)));"));
        assertTrue("enhanced detail page must keep its theme action, but playback pages must hide it",
                body.contains("binding.themeModeDetail.setVisibility(fusionMode || playbackPage ? View.GONE : (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE));"));
    }

    @Test
    public void fusionInlineFullscreenConsoleMatchesNativeLeanbackStructure() throws Exception {
        String layout = readLayout("activity_tmdb_detail.xml");
        int bottom = layout.indexOf("android:id=\"@+id/playerBottom\"");
        int actionRow = layout.indexOf("android:id=\"@+id/playerActionRow\"", bottom);
        int seek = layout.indexOf("android:id=\"@+id/seek\"", actionRow);
        int detailHost = layout.indexOf("android:id=\"@+id/detailControlHost\"", bottom);

        assertTrue("fusion inline fullscreen console must keep native bottom scrim", bottom >= 0
                && layout.indexOf("android:background=\"@drawable/shape_controller_scrim\"", bottom) > bottom
                && layout.indexOf("android:paddingStart=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingTop=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingEnd=\"24dp\"", bottom) > bottom
                && layout.indexOf("android:paddingBottom=\"16dp\"", bottom) > bottom);
        assertTrue("fusion inline fullscreen console must place the action row before the seek row",
                bottom >= 0 && actionRow > bottom && seek > actionRow && detailHost > seek);
        assertTrue("fusion inline fullscreen seek row must be a full-width native CustomSeekView child",
                containsViewAttribute(layout, seek, "android:layout_width=\"match_parent\"")
                        && containsViewAttribute(layout, seek, "android:layout_marginTop=\"12dp\"")
                        && !containsViewAttribute(layout, seek, "android:layout_weight=\"1\"")
                        && !containsViewAttribute(layout, seek, "android:paddingEnd=\"8dp\""));
        int fullscreenIcon = layout.indexOf("android:id=\"@+id/playerFullscreen\"", bottom);
        assertTrue("fusion inline fullscreen console must not keep the extra fullscreen icon beside the seek bar",
                detailHost > bottom && (fullscreenIcon < 0 || fullscreenIcon > detailHost));
        assertNativeControlButton(layout, "playerNext", "8dp");
        assertNativeControlButton(layout, "playerPrev", "8dp");
        assertNativeControlButton(layout, "playerEpisodes", "12dp");
        assertNativeControlButton(layout, "playerRefresh", "8dp");
        assertNativeControlButton(layout, "playerChangeSource", "8dp");
        assertNativeControlButton(layout, "playerFullscreenAction", "8dp");
        assertNativeControlButton(layout, "playerQuality", "12dp");
        assertNativeControlButton(layout, "playerLut", "8dp");
    }

    @Test
    public void fusionInlineQualityAndVideoTrackLogicMatchesNativeLeanbackPlayer() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int canQuality = source.indexOf("private boolean canChangeInlineQuality()");
        int showQuality = source.indexOf("private void showInlineQuality()");
        int changeQuality = source.indexOf("private void changeInlineQuality(int position)");
        int updateButtons = source.indexOf("private void updateInlineButtons(boolean playing)");
        int updateMobileButtons = source.indexOf("private void updateMobileInlineButtons(boolean playing, boolean hasPlayer, int episodeCount, boolean hasTitle)");

        assertTrue("fusion quality button must only follow native URL multi-quality availability",
                canQuality >= 0
                        && source.indexOf("return hasInlineUrlQuality();", canQuality) > canQuality
                        && source.indexOf("isInlineVideoTrackAsQuality", canQuality) < 0);
        assertTrue("fusion quality dialog must not open the video-track dialog",
                showQuality >= 0
                        && changeQuality > showQuality
                        && source.indexOf("TrackDialog.create().type(C.TRACK_TYPE_VIDEO)", showQuality) < 0);
        assertTrue("fusion video-track button must stay independent from URL quality on TV",
                updateButtons >= 0
                        && source.indexOf("binding.playerVideoTrack.setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE)", updateButtons) > updateButtons);
        assertTrue("fusion video-track button must stay independent from URL quality on mobile",
                updateMobileButtons >= 0
                        && source.indexOf("detailActionView(R.id.video, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE)", updateMobileButtons) > updateMobileButtons);
    }

    @Test
    public void fusionInlineLutQuickFocusMatchesNativeLeanbackPlayer() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String layout = readLayout("activity_tmdb_detail.xml");
        int dispatch = source.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int focus = source.indexOf("private boolean focusInlineLutQuickContent()");
        int recyclerItem = source.indexOf("private boolean focusRecyclerItem(RecyclerView recycler)");
        int firstChild = source.indexOf("private boolean focusFirstChild(View view)");
        int controls = layout.indexOf("android:id=\"@+id/playerControls\"");
        int detailControls = layout.indexOf("android:id=\"@+id/detailControlHost\"");
        int lutQuick = layout.indexOf("android:id=\"@+id/lutQuick\"");

        assertTrue("fusion LUT quick panel must consume back and remote keys before detail/player controls",
                dispatch >= 0
                        && source.indexOf("binding.lutQuick.hideIfVisible()", dispatch) > dispatch
                        && source.indexOf("dispatchInlineLutQuickKey(event)", dispatch) > dispatch);
        assertTrue("fusion LUT quick panel must be layered above fullscreen control overlays",
                controls >= 0 && detailControls > controls && lutQuick > detailControls);
        assertTrue("fusion LUT quick panel must focus selected entry before falling back like native leanback",
                focus >= 0
                        && source.indexOf("binding.lutQuick.focusSelectedEntry()", focus) > focus
                        && source.indexOf("focusRecyclerItem(recycler)", focus) > focus
                        && source.indexOf("focusFirstChild(binding.lutQuick)", focus) > focus);
        assertTrue("fusion LUT quick panel must include native recycler focus fallback helpers",
                recyclerItem > focus && firstChild > recyclerItem);
    }

    @Test
    public void mobileFusionInlinePlayerActionLayoutExposesConfigContainer() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_control_vod_action_tmdb.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue("mobile fusion action row must expose @id/container for PlayerButtonSetting.applyOrder",
                layout.contains("android:id=\"@+id/container\""));
    }

    @Test
    public void lockedInlineFullscreenCanStillShowControlsWhileLoading() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int helper = source.indexOf("private boolean shouldBlockInlineControlsForLoading()");
        int method = source.indexOf("private void showInlineControls(boolean show, boolean focus)");
        int guard = source.indexOf("if (shouldBlockInlineControlsForLoading())", method);
        int helperGuard = source.indexOf("return isInlineLoadingVisible() && !(isLock() && inlineFullscreen);", helper);

        assertTrue(sourcePath + " is missing shouldBlockInlineControlsForLoading", helper >= 0);
        assertTrue("inline controls should consult the loading guard helper before hiding controls", guard > method);
        assertTrue("locked fullscreen loading must still allow the controls overlay to appear for unlock/exit", helperGuard > helper);
    }

    @Test
    public void inlinePlayerPersistentDisplayUsesPlayerOsdOnTvAndLegacyPanelOnMobile() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String controller = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java");
        int method = source.indexOf("private void updateInlineDisplayPanel()");
        int end = source.indexOf("private void setButtonEnabled", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";
        int initOsd = controller.indexOf("this.osd = new PlayerOsdController(");

        assertTrue(sourcePath + " is missing updateInlineDisplayPanel", method >= 0);
        assertTrue("mobile inline playback must suppress PlayerOsdController persistent corner labels to avoid duplicate display",
                initOsd >= 0
                        && controller.indexOf("this.osd.setPersistentSuppressed(host.suppressPersistentOsd());", initOsd) > initOsd
                        && source.contains("public boolean suppressPersistentOsd()")
                        && source.contains("return Util.isMobile();"));
        assertTrue("TV inline playback should clear the legacy panel and let PlayerOsdController render persistent OSD",
                body.contains("if (!Util.isMobile()) {") && body.contains("hideInlineDisplayPanel();") && body.contains("return;"));
        assertTrue("mobile inline playback should keep the legacy display panel for persistent screen display",
                body.contains("PlayerSetting.isDisplayTime()")
                        && body.contains("Traffic.setSpeed(binding.playerDisplayTraffic)")
                        && body.contains("binding.playerDisplayTopLeft.setVisibility")
                        && body.contains("binding.playerDisplayBottomProgress.setVisibility")
                        && body.contains("binding.playerDisplayMini.setVisibility"));
        assertTrue("mobile legacy display text should be tinted before being shown", body.contains("tintInlineDisplay();"));
    }

    @Test
    public void mobileInlineCastReflectionKeepsR8MethodNames() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        Path proguardPath = findAppModulePath().resolve("proguard-rules.pro");
        String proguard = new String(Files.readAllBytes(proguardPath), StandardCharsets.UTF_8);
        int inlineCast = source.indexOf("protected void onInlineCast()");
        int keepRule = proguard.indexOf("-keepclassmembernames class com.fongmi.android.tv.ui.dialog.CastDialog");

        assertTrue(sourcePath + " is missing onInlineCast", inlineCast >= 0);
        assertTrue("inline cast reflects CastDialog.create", source.indexOf("getMethod(\"create\")", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.history", source.indexOf("getMethod(\"history\", History.class)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.video", source.indexOf("getMethod(\"video\", videoClass)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.fm", source.indexOf("getMethod(\"fm\", boolean.class)", inlineCast) > inlineCast);
        assertTrue("inline cast reflects CastDialog.show", source.indexOf("getMethod(\"show\", androidx.fragment.app.FragmentActivity.class)", inlineCast) > inlineCast);
        assertTrue("release R8 must keep CastDialog method names used by inline cast reflection", keepRule >= 0);
        assertTrue("release R8 must keep CastDialog.create name", proguard.indexOf("public static com.fongmi.android.tv.ui.dialog.CastDialog create();", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.history name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog history(com.fongmi.android.tv.bean.History);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.video name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog video(com.fongmi.android.tv.bean.CastVideo);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.fm name", proguard.indexOf("public com.fongmi.android.tv.ui.dialog.CastDialog fm(boolean);", keepRule) > keepRule);
        assertTrue("release R8 must keep CastDialog.show name", proguard.indexOf("public void show(androidx.fragment.app.FragmentActivity);", keepRule) > keepRule);
    }

    @Test
    public void mobileFusionDetailKeepsInlinePlayerActionsInsideOverlay() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int player = layout.indexOf("android:id=\"@+id/playerPanel\"");
        int dock = layout.indexOf("android:id=\"@+id/mobileFusionPlayerActionDock\"");
        int fusionActions = layout.indexOf("android:id=\"@+id/fusionActions\"");

        assertTrue("mobile fusion detail may keep a hidden legacy action dock for binding compatibility", dock >= 0);
        assertTrue("mobile fusion player action dock must remain hidden between player and detail actions", player < dock && dock < fusionActions);

        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int update = source.indexOf("private void updateMobileInlineButtons(boolean playing");
        int dockMethod = source.indexOf("private void hideMobileFusionPlayerActionDock()");
        int restoreMethod = source.indexOf("private void restoreMobileInlinePlayerAction()");

        assertTrue(sourcePath + " is missing hideMobileFusionPlayerActionDock", dockMethod >= 0);
        assertTrue(sourcePath + " is missing restoreMobileInlinePlayerAction", restoreMethod >= 0);
        assertTrue("mobile inline buttons must hide the below-player action dock before choosing overlay visibility",
                source.indexOf("hideMobileFusionPlayerActionDock();", update) > update);
        assertTrue("non-fullscreen fusion detail must not move the shared action row into a visible below-player dock",
                !source.contains("binding.mobileFusionPlayerActionDock.addView(detailActionRoot"));
        assertTrue("source switches must restore the action row to the control overlay and hide the dock",
                source.indexOf("hideMobileFusionPlayerActionDock();", source.indexOf("private void resetDetailState()")) > source.indexOf("private void resetDetailState()"));
        assertTrue("fullscreen and non-fusion modes must keep the action row in the control overlay",
                source.indexOf("restoreMobileInlinePlayerAction();", dockMethod) > dockMethod);
    }

    @Test
    public void fusionDetailBackdropCropsToFillScreen() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean shouldCropBackdrop()");
        assertTrue(sourcePath + " is missing shouldCropBackdrop", method >= 0);

        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("Fusion detail must center-crop artwork so portrait screens do not show top/bottom background bars",
                body.contains("return true;"));
    }

    @Test
    public void unmatchedTmdbDetailUsesAppWallpaperBackdrop() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void bindBackdrop()");
        int surface = source.indexOf("private void applyBackdropSurface(ThemeColors colors)");
        int wallpaper = source.indexOf("private boolean useAppWallpaperBackdrop()");
        int customWall = source.indexOf("protected boolean customWall()");

        assertTrue(sourcePath + " is missing bindBackdrop", bind >= 0);
        assertTrue(sourcePath + " is missing applyBackdropSurface", surface >= 0);
        assertTrue(sourcePath + " is missing useAppWallpaperBackdrop", wallpaper >= 0);
        assertTrue(sourcePath + " is missing customWall", customWall >= 0);
        int customWallEnd = source.indexOf("\n    }", customWall);
        assertTrue("unmatched TMDB detail needs the app wallpaper layer behind its transparent fallback",
                source.substring(customWall, customWallEnd).contains("return true;"));
        assertTrue("unmatched TMDB detail must not use the source poster as the large backdrop fallback",
                source.indexOf("bindBackdropImage(vod.getName(), wallpaperBackdrop ? \"\" : tmdbBackdropUrl(), wallpaperBackdrop ? \"\" : vod.getPic());", bind) > bind);
        assertTrue("unmatched TMDB detail must keep the hero layer visible so the wallpaper can be shaded",
                source.indexOf("TextUtils.isEmpty(image) && !useAppWallpaperBackdrop() ? View.GONE : View.VISIBLE") > bind);
        assertTrue("unmatched TMDB detail must make the detail background transparent over the app wallpaper",
                source.indexOf("useAppWallpaperBackdrop() ? Color.TRANSPARENT : backdropFallbackBackground(colors)", surface) > surface);
        assertTrue("wallpaper fallback must only apply after source detail has loaded and no TMDB detail matched",
                source.indexOf("return vod != null && matchedTmdbDetail == null;", wallpaper) > wallpaper);
    }

    @Test
    public void detailLoadsPersonalAiCacheBeforeSlowMediaBlocksFinish() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void loadTmdbMediaBlocks(TmdbBundle bundle)");
        int bind = source.indexOf("bindTmdbSection();", method);
        int earlyCache = source.indexOf("loadTmdbPersonalAiCache(bundle, currentVod, generation);", method);
        int task = source.indexOf("Task.execute(() ->", method);
        int merge = source.indexOf("relatedItems.clear();", method);
        int fullAi = source.indexOf("loadTmdbPersonalAi(bundle, currentVod", method);

        assertTrue(sourcePath + " is missing loadTmdbMediaBlocks", method >= 0);
        assertTrue("TMDB detail must bind the loading section before early AI cache lookup", bind > method);
        assertTrue("TMDB detail must read AI cache before slow media block loading starts", earlyCache > bind && earlyCache < task);
        assertTrue("TMDB detail must keep the early AI row while merging slow media blocks",
                merge > method && fullAi > merge && !source.substring(merge, fullAi).contains("personalAiItems.clear();"));
    }

    @Test
    public void standaloneDetailAppliesInitialTmdbResultInSinglePass() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int helper = source.indexOf("private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass");
        int apply = source.indexOf("private void applyTmdbResult(TmdbLoadResult result)");

        assertTrue(sourcePath + " is missing standalone single-pass initial TMDB loading", load >= 0 && helper > load && apply > helper);
        assertTrue("standalone TMDB detail should wait for the initial TMDB bundle before the first page bind",
                source.indexOf("boolean singlePassStandaloneTmdb = shouldLoadInitialStandaloneTmdbDetailInSinglePass(reusableBundle, tmdbFuture);", load) > load
                        && source.indexOf("if (!singlePassStandaloneTmdb || finalVod == null)", load) > load
                        && source.indexOf("applyLoaded(finalVod, finalResult == null ? null : finalResult.bundle(), finalResult == null ? new ArrayList<>() : finalResult.searchItems(), finalError, true);", load) > load);
        assertTrue("single-pass loading must only apply to standalone TMDB detail without a reusable bundle",
                source.indexOf("return reusableBundle == null && tmdbFuture != null && activeTmdbBundle == null && Setting.isStandaloneTmdbDetailMode(getDetailMode());", helper) > helper);
        assertTrue("standalone initial loading must not use a delayed second TMDB rebind",
                !source.contains("INITIAL_STANDALONE_TMDB_RESULT_DEFER_MS")
                        && !source.contains("postDelayed(this::flushInitialStandaloneTmdbResult"));
    }

    @Test
    public void standaloneDetailPreloadsInitialSeasonBeforeFirstBind() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int load = source.indexOf("private void loadContent(@Nullable TmdbBundle reusableBundle)");
        int preload = source.indexOf("private TmdbLoadResult preloadInitialStandaloneSeason");
        int fetch = source.indexOf("private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh)");

        assertTrue(sourcePath + " is missing initial standalone season preload", load >= 0 && preload > load && fetch > preload);
        assertTrue("standalone initial detail should preload the current season before the first UI bind",
                source.indexOf("if (singlePassStandaloneTmdb) loadedResult = preloadInitialStandaloneSeason(loadedResult, finalVod);", load) > load
                        && source.indexOf("tmdbService.season(bundle.item(), seasonNumber, tmdbConfig, bundle.detail(), false);", preload) > preload
                        && source.indexOf("seasonEpisodes.put(seasonNumber, episodes);", preload) > preload
                        && source.indexOf("return new TmdbLoadResult(withSeason, result.searchItems());", preload) > preload);
        assertTrue("initial season preload must use the same current-season data shape that fetchSeasonIfNeeded would later fill",
                source.indexOf("seasonCounts.put(seasonNumber, episodes.size());", preload) > preload
                        && source.indexOf("seasonCast.put(seasonNumber, tmdbService.seasonCast(season, tmdbConfig));", preload) > preload
                        && source.indexOf("seasonPhotos.put(seasonNumber, tmdbService.seasonPhotos(season, tmdbConfig));", preload) > preload);
    }

    @Test
    public void standaloneEpisodeModeToggleDoesNotForceSelectedScroll() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void toggleEpisodeViewMode()");
        int nextMethod = source.indexOf("private void updateEpisodeViewModeButton()", method);

        assertTrue(sourcePath + " is missing toggleEpisodeViewMode", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("standalone TMDB episode mode toggle should preserve scroll instead of forcing selected-item alignment",
                body.contains("rerenderEpisodeViewportOnly(false);")
                        && !body.contains("rerenderEpisodeViewportOnly(true);"));
    }

    @Test
    public void detailThemeToggleRestylesDynamicViewsWithoutRebuildingEpisodes() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cycle = source.indexOf("private void cycleThemeMode()");
        int cycleEnd = source.indexOf("private void applyDetailTheme()", cycle);
        int refresh = source.indexOf("private void refreshDetailThemeDynamicViews()");
        int refreshEnd = source.indexOf("private void clearExternalLinks()", refresh);

        assertTrue(sourcePath + " is missing cycleThemeMode", cycle >= 0 && cycleEnd > cycle);
        assertTrue(sourcePath + " is missing refreshDetailThemeDynamicViews", refresh >= 0 && refreshEnd > refresh);
        String cycleBody = source.substring(cycle, cycleEnd);
        String refreshBody = source.substring(refresh, refreshEnd);

        assertTrue("theme toggle should preserve episode scroll and card data instead of rebuilding the detail sections",
                cycleBody.contains("applyDetailTheme();")
                        && cycleBody.contains("refreshDetailThemeDynamicViews();")
                        && !cycleBody.contains("bindMeta();")
                        && !cycleBody.contains("bindExternalLinks();")
                        && !cycleBody.contains("renderSeasonSelection();")
                        && !cycleBody.contains("renderEpisodes();"));
        assertTrue("theme toggle still needs to restyle dynamic chips and external links in place",
                refreshBody.contains("styleMetaChips();")
                        && refreshBody.contains("styleExternalLinks();")
                        && refreshBody.contains("renderFlagSelection();")
                        && refreshBody.contains("updateSeasonButtonStates();")
                        && refreshBody.contains("updateEpisodeRangeButtonStates();"));
    }

    @Test
    public void standaloneEpisodeViewportRerenderOnlyNumbersCurrentPage() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void rerenderEpisodeViewportOnly(boolean scrollToSelection)");
        int nextMethod = source.indexOf("private void updateEpisodeRangeButtonStates()", method);

        assertTrue(sourcePath + " is missing rerenderEpisodeViewportOnly", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("episode mode toggles must number only the current card page, not every visible episode",
                body.contains("List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, ranges.get(episodeRangeIndex)) : displayEpisodes;")
                        && body.contains("Map<Episode, Integer> numbers = episodeNumbers(pageItems, episodes);")
                        && body.indexOf("Map<Episode, Integer> numbers = episodeNumbers(pageItems, episodes);") > body.indexOf("List<Episode> pageItems ="));
        assertTrue("episode mode toggles must not rebuild episode-number maps for every visible episode",
                !body.contains("episodeNumbers(visibleEpisodes, episodes);"));
    }

    @Test
    public void standaloneEpisodeReverseUsesViewportOnlyRefreshForLargeLists() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int toggle = source.indexOf("private void toggleEpisodeReverse()");
        int nextToggle = source.indexOf("private void toggleEpisodeViewMode()", toggle);
        int rerender = source.indexOf("private void rerenderEpisodeViewportOnly(boolean scrollToSelection, boolean rebuildRanges)");
        int updateStates = source.indexOf("private void updateEpisodeRangeButtonStates()", rerender);

        assertTrue(sourcePath + " is missing reverse episode viewport helpers", toggle >= 0 && nextToggle > toggle && rerender >= 0 && updateStates > rerender);
        String toggleBody = source.substring(toggle, nextToggle);
        String rerenderBody = source.substring(rerender, updateStates);

        assertTrue("episode reverse should keep the long-list render path lightweight instead of rebinding seasons/TMDB metadata",
                toggleBody.contains("resetEpisodeRange();")
                        && toggleBody.contains("rerenderEpisodeViewportOnly(true, true);")
                        && !toggleBody.contains("renderEpisodes();"));
        assertTrue("episode reverse still needs to rebuild range labels because the visible order changes",
                rerenderBody.contains("if (rebuildRanges) renderEpisodeRanges(ranges);")
                        && rerenderBody.contains("else updateEpisodeRangeButtonStates();")
                        && rerenderBody.contains("List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, ranges.get(episodeRangeIndex)) : displayEpisodes;")
                        && rerenderBody.indexOf("List<Episode> pageItems =") > rerenderBody.indexOf("if (rebuildRanges) renderEpisodeRanges(ranges);"));
    }

    @Test
    public void standaloneMobileEpisodeCardPagesUseLargerButBoundedGroups() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int constant = source.indexOf("private static final int STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE = 36;");
        int method = source.indexOf("private int episodeCardPageMaxSize()");
        int nextMethod = source.indexOf("private boolean shouldRefreshEpisodeMediaSection", method);

        assertTrue(sourcePath + " is missing standalone mobile episode card page sizing", constant >= 0 && method > constant && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("standalone mobile TMDB detail should use the larger bounded page size while other modes keep the default",
                body.contains("Util.isMobile() && Setting.isStandaloneTmdbDetailMode(getDetailMode()) ? STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE : EpisodeRangePolicy.CARD_PAGE_MAX_SIZE"));
    }

    @Test
    public void openingDetailBindsTmdbEpisodesWithoutRepeatedIndexOf() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void bindTmdbEpisodes(List<Episode> sourceEpisodes, int tmdbSeason)");
        int nextMethod = source.indexOf("private int tmdbEpisodeDataSeason", method);

        assertTrue(sourcePath + " is missing bindTmdbEpisodes", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("opening detail should index source episodes once before binding TMDB episode metadata",
                body.contains("Map<Episode, Integer> indices = episodeIndices(sourceEpisodes);")
                        && body.contains("EpisodePosition position = episodePosition(episode, sourceEpisodes, index);"));
        assertTrue("opening detail must not call indexOf for every episode unless the identity index misses",
                body.contains("if (index < 0) index = sourceEpisodes.indexOf(episode);")
                        && !body.contains("EpisodePosition position = episodePosition(episode, sourceEpisodes);"));
    }

    @Test
    public void switchingLongStandaloneEpisodeFlagsReusesEpisodeRenderCaches() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int indices = source.indexOf("private Map<Episode, Integer> episodeIndices(List<Episode> episodes)");
        int clear = source.indexOf("private void clearEpisodeRenderCaches()", indices);
        int explicit = source.indexOf("private boolean hasExplicitSeasonNumbers(List<Episode> episodes)");
        int next = source.indexOf("private int sourceEpisodeNumber", explicit);
        int visible = source.indexOf("private List<Episode> visibleEpisodes(List<Episode> episodes)");
        int visibleEnd = source.indexOf("private List<Episode> computeVisibleEpisodes", visible);

        assertTrue(sourcePath + " is missing episode render cache methods", indices >= 0 && clear > indices && explicit > clear && next > explicit && visible > next && visibleEnd > visible);
        String indexBody = source.substring(indices, clear);
        String clearBody = source.substring(clear, explicit);
        String explicitBody = source.substring(explicit, next);
        String visibleBody = source.substring(visible, visibleEnd);
        assertTrue("long episode flag switches should reuse the identity index for the same episode list",
                indexBody.contains("if (episodes == episodeIndexSource) return episodeIndexCache;")
                        && indexBody.contains("episodeIndexSource = episodes;")
                        && indexBody.contains("episodeIndexCache = indices;"));
        assertTrue("long episode flag switches should not rescan every title for explicit season numbers on each episode",
                explicitBody.contains("if (episodes == explicitSeasonSource) return explicitSeasonCache;")
                        && explicitBody.contains("explicitSeasonSource = episodes;")
                        && explicitBody.contains("explicitSeasonCache = true;"));
        assertTrue("long episode order/view toggles should reuse the current season's visible episode list",
                visibleBody.contains("if (episodes == visibleEpisodeSource && selectedSeasonNumber == visibleEpisodeSeason) return visibleEpisodeCache;")
                        && visibleBody.contains("visibleEpisodeCache = computeVisibleEpisodes(episodes);")
                        && clearBody.contains("clearVisibleEpisodeCache();"));
        assertTrue("new detail loads must clear cached episode-list render state",
                source.indexOf("resetEpisodeRange();", source.indexOf("clearEpisodeRenderCaches();")) > 0
                        && source.indexOf("clearEpisodeRenderCaches();", source.indexOf("TmdbEpisodeSorter.sort(vod);")) > 0
                        && source.indexOf("clearEpisodeRenderCaches();", source.indexOf("enrichVod();")) > 0);
        int seasonCountUpdate = source.indexOf("seasonEpisodeCounts.put(seasonNumber, episodes.size());");
        int visibleCacheClear = source.indexOf("clearVisibleEpisodeCache();", seasonCountUpdate);
        int seasonRender = source.indexOf("if (seasonNumber == tmdbEpisodeDataSeason", visibleCacheClear);
        assertTrue("season count updates must invalidate cached visible episode slices before rerendering",
                seasonCountUpdate >= 0 && visibleCacheClear > seasonCountUpdate && seasonRender > visibleCacheClear);
    }

    @Test
    public void compactCinemaDetailKeepsPosterVisible() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyCinemaDetailTemplate()");
        int nextMethod = source.indexOf("private boolean isCompactWidth()", method);

        assertTrue(sourcePath + " is missing applyCinemaDetailTemplate", method >= 0 && nextMethod > method);
        String body = source.substring(method, nextMethod);
        assertTrue("compact immersive/cinema detail must not hide the poster again",
                body.contains("binding.posterCard.setVisibility(compact ? View.VISIBLE : View.GONE);"));
        assertTrue("compact immersive/cinema poster should use a stable small size beside the title",
                body.contains("new LinearLayout.LayoutParams(ResUtil.dp2px(92), ResUtil.dp2px(138))")
                        && body.contains("binding.posterCard.setLayoutParams(posterParams);"));
        assertTrue("compact immersive/cinema title area must share the row with the poster instead of occupying full width",
                body.contains("new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)")
                        && body.contains("infoParams.setMarginStart(compact ? ResUtil.dp2px(14) : 0);")
                        && body.contains("if (compact)")
                        && containsMethodCallIgnoringReceiver(body, "setWidthMatch(binding.detailActions)"));
    }

    @Test
    public void fusionDetailShowsFocusedPersonalAiReason() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRailAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        int aiList = layout.indexOf("android:id=\"@+id/personalAiList\"");
        int aiReason = layout.indexOf("android:id=\"@+id/personalAiReason\"");
        int tmdbStatus = layout.indexOf("android:id=\"@+id/tmdbStatus\"");
        assertTrue("TMDB detail must keep the AI reason directly below the smart recommendation row",
                aiList >= 0 && aiReason > aiList && tmdbStatus > aiReason);
        assertTrue("TMDB detail must listen for smart recommendation card focus",
                activity.contains("personalAiAdapter.setOnItemFocusListener(this::showAiRecommendationReason);"));
        assertTrue("TMDB detail must render the focused card overview as the recommendation reason",
                activity.contains("binding.personalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, reason));"));
        assertTrue("TMDB detail must hide stale recommendation reasons when the smart row is absent",
                activity.contains("showAiRecommendationReason(null, false);"));
        assertTrue("TMDB detail must scroll the reason into view when the focused card sits near the bottom of the wide layout",
                activity.contains("scrollAiRecommendationReasonIntoView();")
                        && activity.contains("offsetDescendantRectToMyCoords(binding.personalAiReason, rect)")
                        && activity.contains("binding.scroll.smoothScrollBy(0, bottomGap);"));
        assertTrue("TMDB rail cards must report focus changes to the detail screen",
                adapter.contains("public interface FocusListener")
                        && adapter.contains("public void setOnItemFocusListener(FocusListener listener)")
                        && adapter.contains("focusListener.onItemFocus(item, focused)")
                        && adapter.contains("holder.root.hasFocus() && focusListener != null"));
    }

    @Test
    public void keepStateShowsAddedLabelWhenAlreadyKept() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateKeepState()");
        assertTrue(sourcePath + " is missing updateKeepState", method >= 0);

        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("TMDB detail must show the current favorite state, not the removal result label",
                body.contains("TmdbDetailLabels.keepLabel(kept)") && !body.contains("R.string.keep_del"));
        assertTrue("TMDB detail must keep all favorite buttons visually selected together",
                body.contains("binding.keep.setSelected(kept)")
                        && body.contains("binding.keepTop.setSelected(kept)")
                        && body.contains("binding.keepFusion.setSelected(kept)"));
    }

    @Test
    public void lightActionButtonsStayReadableOnBackdropAndPanels() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int theme = source.indexOf("private void applyDetailTheme()");
        int themeEnd = source.indexOf("private void styleSourceValue()", theme);
        int helper = source.indexOf("private void setDetailActionButton(MaterialButton button, ThemeColors colors)");
        assertTrue(sourcePath + " is missing applyDetailTheme", theme >= 0 && themeEnd > theme);
        assertTrue(sourcePath + " is missing setDetailActionButton", helper >= 0);

        String themeBody = source.substring(theme, themeEnd);
        assertTrue("light detail actions must use the readable action button helper",
                themeBody.contains("setDetailActionButton(binding.keep, colors);")
                        && themeBody.contains("setDetailActionButton(binding.keepTop, colors);")
                        && themeBody.contains("setDetailActionButton(binding.keepFusion, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematch, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematchTop, colors);")
                        && themeBody.contains("setDetailActionButton(binding.rematchFusion, colors);")
                        && themeBody.contains("setDetailActionButton(binding.changeSource, colors);")
                        && themeBody.contains("setDetailActionButton(binding.changeSourceDetail, colors);"));

        int helperEnd = source.indexOf("private void setButton(MaterialButton button, int background, int stroke, int text)", helper);
        assertTrue("setDetailActionButton must be placed before setButton", helperEnd > helper);
        String helperBody = source.substring(helper, helperEnd);
        assertTrue("light action buttons need an opaque surface instead of the translucent control color",
                helperBody.contains("if (lightTheme)")
                        && helperBody.contains("button.setAlpha(1f);")
                        && helperBody.contains("0xFFFFFFFF")
                        && helperBody.contains("colors.chipActive")
                        && helperBody.contains("colors.lineStrong")
                        && helperBody.contains("colors.primary"));
    }

    @Test
    public void detailRatingChipsKeepReadableBrandColorsAfterThemeTint() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int theme = source.indexOf("private void applyDetailTheme()");
        int add = source.indexOf("private void addRatingChip");
        int style = source.indexOf("private void styleDetailRatingChip");
        int readable = source.indexOf("private int readableDetailRatingColor");

        assertTrue(sourcePath + " is missing detail rating chip styling helpers",
                theme >= 0 && add >= 0 && style >= 0 && readable >= 0);
        assertTrue("theme refresh must restyle existing rating chips after tintTextTree recolors text",
                source.indexOf("tintTextTree(binding.getRoot(), colors);", theme) > theme
                        && source.indexOf("styleDetailRatingChips();", theme) > source.indexOf("tintTextTree(binding.getRoot(), colors);", theme));
        assertTrue("rating chips must keep their source color so async ratings and theme refreshes can restyle them",
                source.indexOf("new RatingChipTag(platform, color)", add) > add
                        && source.indexOf("styleDetailRatingChip(chip, color);", add) > add);
        assertTrue("dark detail rating chips need a dark glass surface instead of translucent theme chips over bright artwork",
                source.indexOf("background.setColor(lightTheme ? ratingChipBackground(colors) : 0x6610141A);", style) > style
                        && source.indexOf("background.setStroke(ResUtil.dp2px(1), lightTheme ? colors.line : 0x33FFFFFF);", style) > style);
        assertTrue("light detail rating chips must darken yellow green and red source colors for contrast",
                source.indexOf("return 0xFF0F7A4A;", readable) > readable
                        && source.indexOf("return 0xFF8A5A00;", readable) > readable
                        && source.indexOf("return 0xFFB42318;", readable) > readable);
    }

    @Test
    public void detailEpisodeToolsAndCardsKeepDistinctFocusChrome() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        Path selectorPath = findMainResPath().resolve(Path.of("drawable", "selector_episode_card.xml"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String selector = new String(Files.readAllBytes(selectorPath), StandardCharsets.UTF_8);
        int focusDetailEpisode = activity.indexOf("private boolean focusDetailEpisode(int position)");
        int focusDetailEpisodeEnd = activity.indexOf("private int detailEpisodeSpanCount()", focusDetailEpisode);
        int align = activity.indexOf("private void alignDetailEpisodeFocusedRow");
        int alignEnd = activity.indexOf("private void toggleEpisodeReverse", align);
        String focusBody = focusDetailEpisode >= 0 && focusDetailEpisodeEnd > focusDetailEpisode ? activity.substring(focusDetailEpisode, focusDetailEpisodeEnd) : "";
        String alignBody = align >= 0 && alignEnd > align ? activity.substring(align, alignEnd) : "";

        assertTrue("episode reverse/list tools should refresh focus chrome without inheriting episode-card selected state",
                activity.contains("setEpisodeToolButton(binding.episodeReverse, colors);")
                        && activity.contains("setEpisodeToolButton(binding.episodeViewMode, colors);")
                        && activity.contains("private void applyEpisodeToolButtonsFocus()")
                        && activity.contains("applyEpisodeToolButtonFocus(binding.episodeReverse, colors);")
                        && activity.contains("applyEpisodeToolButtonFocus(binding.episodeViewMode, colors);")
                        && activity.contains("button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : colors.lineStrong));"));
        assertTrue("episode tool delayed refocus must not steal focus back from the sibling tool",
                activity.contains("isEpisodeToolFocusedOtherThan(button)")
                        && activity.contains("retryDetailButtonFocus(button, previousFocus)")
                        && activity.contains("if (focus != null && previousFocus != null && focus != previousFocus) return;"));
        assertTrue("episode-card selector must let focused state override current-playing selected state",
                selector.indexOf("android:state_focused=\"true\"") >= 0
                        && selector.indexOf("android:state_focused=\"true\"") < selector.indexOf("android:state_selected=\"true\""));
        assertTrue("episode DPAD movement should focus an already visible card without forcing RecyclerView to re-scroll and rebind",
                focusBody.contains("RecyclerView.ViewHolder visibleHolder = binding.episodeContainer.findViewHolderForAdapterPosition(target);")
                        && focusBody.contains("if (visibleHolder != null)")
                        && focusBody.indexOf("findViewHolderForAdapterPosition(target)") < focusBody.indexOf("scrollEpisodeToPosition(rowStart, ResUtil.dp2px(8));"));
        assertTrue("outer detail scroll alignment should wait for stable focus before moving the page",
                alignBody.contains("focusedView.post(() ->")
                        && alignBody.contains("if (binding == null || getCurrentFocus() != focusedView) return;")
                        && alignBody.contains("binding.episodeContainer.getChildAdapterPosition(focusedView) != position"));
        assertTrue("focused episode cards should be minimally scrolled fully into view instead of top-aligning the whole row",
                alignBody.contains("private void alignDetailEpisodeFocusedCardNow(View focusedView)")
                        && alignBody.contains("if (rect.bottom > bottom) targetY += rect.bottom - bottom;")
                        && alignBody.contains("else if (rect.top < top) targetY += rect.top - top;")
                        && !alignBody.contains("isDetailEpisodeRowFullyVisible"));
    }

    @Test
    public void inlineEpisodesReuseSharedNativeEnhancedAdaptivePanel() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path policyPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbEpisodeGridPolicy.java"));
        String policy = new String(Files.readAllBytes(policyPath), StandardCharsets.UTF_8);

        int method = activity.indexOf("private void showInlineEpisodes()");
        int sharedMethod = activity.indexOf("private void showNativeEnhancedInlineEpisodes()", method);
        int sharedMethodEnd = activity.indexOf("private TextView createNativeEnhancedInlineSectionTitle", sharedMethod);

        assertTrue(activityPath + " is missing showInlineEpisodes", method >= 0);
        assertTrue(activityPath + " is missing showNativeEnhancedInlineEpisodes", sharedMethod >= 0 && sharedMethodEnd > sharedMethod);

        String dispatchBody = activity.substring(method, sharedMethod);
        String panelBody = activity.substring(sharedMethod, sharedMethodEnd);

        assertTrue("mobile standalone TMDB detail modes should use the shared native-enhanced episode panel",
                dispatchBody.contains("if (!Util.isMobile() || Setting.isStandaloneTmdbDetailMode(getDetailMode()))")
                        && dispatchBody.contains("showNativeEnhancedInlineEpisodes();"));
        assertTrue("native-enhanced inline episode panel should include line chips, page chips, and TMDB episode cards",
                panelBody.contains("createNativeEnhancedInlineChipButton(flag.getShow())")
                        && panelBody.contains("createNativeEnhancedInlineChipButton(ranges.get(i).label())")
                        && panelBody.contains("new TmdbEpisodeAdapter")
                        && panelBody.contains("adapter.setNativeEnhanced(true);"));
        assertTrue("native-enhanced inline episode panel should use one responsive layout policy instead of forked dialogs",
                panelBody.contains("NativeEnhancedInlineEpisodeLayout layout = nativeEnhancedInlineEpisodeLayout();")
                        && panelBody.contains("NestedScrollView scroll = new NestedScrollView(this);")
                        && panelBody.contains("scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));")
                        && panelBody.contains("scroll.addView(panel, new NestedScrollView.LayoutParams")
                        && panelBody.contains("recycler.setNestedScrollingEnabled(false);")
                        && panelBody.contains("updateNativeEnhancedInlineEpisodeLayoutManager(recycler, layout.spanCount())")
                        && panelBody.contains("adapter.setDisplayMode(TmdbEpisodeAdapter.Mode.GRID, layout.spanCount())")
                        && panelBody.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)")
                        && panelBody.contains("setView(scroll)")
                        && panelBody.contains("window.getDecorView().setPadding(0, 0, 0, 0)")
                        && panelBody.contains("window.setGravity(layout.gravity())")
                        && panelBody.contains("window.setLayout(layout.windowWidth(), WindowManager.LayoutParams.MATCH_PARENT)"));
        assertTrue("native-enhanced inline episode panel should move focus by whole grid rows so remote down never lands on a clipped row",
                panelBody.contains("int target = position + layout.spanCount();")
                        && panelBody.contains("position - layout.spanCount()")
                        && activity.contains("private void alignNativeEnhancedInlineEpisodeRow(NestedScrollView scroll, RecyclerView recycler, int position, int spanCount)")
                        && activity.contains("int rowStart = Math.max(0, position - position % span);")
                        && activity.contains("scroll.scrollTo(0, Math.max(0, targetY));"));
        assertTrue("native-enhanced inline episode chips should use the current detail theme instead of the old white video selector",
                activity.contains("private TextView createNativeEnhancedInlineChipButton(String text)")
                        && activity.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(34))")
                        && activity.contains("button.setMinWidth(ResUtil.dp2px(64));")
                        && activity.contains("ThemeColors colors = currentThemeColors();")
                        && activity.contains("background.setColor(focused ? colors.control : selected ? colors.chipActive : colors.chip);")
                        && activity.contains("background.setStroke(ResUtil.dp2px(focused ? 2 : CHIP_STROKE_DP), focused ? colors.accent : selected ? colors.accent : colors.line);")
                        && activity.contains("button.setTextColor(colors.primary);")
                        && activity.contains("button.setBackground(background);")
                        && activity.contains("button.setActivated(selected);")
                        && !activity.contains("button.setBackgroundResource(R.drawable.shape_video_item);")
                        && !activity.contains("ContextCompat.getColorStateList(this, R.color.selector_video_text);"));
        assertTrue("native-enhanced inline episodes should keep mobile native columns and adaptive TV columns",
                activity.contains("private int nativeEnhancedInlineEpisodeSpanCount()")
                        && activity.contains("TmdbEpisodeGridPolicy.nativeEnhancedSpanCount(Util.isMobile(), ResUtil.isPad(), ResUtil.isLand(this), getResources().getConfiguration().screenWidthDp)")
                        && policy.contains("public static int nativeEnhancedSpanCount(boolean mobile, boolean pad, boolean landscape, int screenWidthDp)")
                        && policy.contains("if (mobile) return pad ? landscape ? 4 : 3 : landscape ? 3 : 2;")
                        && policy.contains("public static int tvAdaptiveSpanCount(int screenWidthDp)")
                        && policy.contains("if (screenWidthDp >= 1100) return 5;")
                        && policy.contains("if (screenWidthDp >= 600) return 4;")
                        && policy.contains("return 3;")
                        && activity.contains("WindowManager.LayoutParams.MATCH_PARENT")
                        && !activity.contains("Gravity.END | Gravity.CENTER_VERTICAL")
                        && !activity.contains("0.50f"));
        assertTrue("detail-page episode cards should use the same native-enhanced adaptive card mechanism",
                activity.contains("episodeAdapter.setNativeEnhanced(true);")
                        && activity.contains("private int episodeSpanCount()")
                        && activity.contains("return nativeEnhancedInlineEpisodeSpanCount();")
                        && activity.contains("binding.episodeViewMode.setVisibility(View.VISIBLE);")
                        && activity.contains("Setting.putTmdbEpisodeGridMode(episodeGridMode);")
                        && !activity.contains("if (shouldForceAdaptiveEpisodeGrid()) episodeGridMode = true;")
                        && !activity.contains("if (shouldForceAdaptiveEpisodeGrid()) return;"));
        assertTrue("native-enhanced card styling must not be disabled on mobile fusion overlays",
                adapter.contains("private boolean isNativeEnhanced()")
                        && adapter.contains("return nativeEnhanced;")
                        && !adapter.contains("return nativeEnhanced && !Util.isMobile();"));
        assertTrue("mobile native-enhanced cards should use the same compact TMDB card proportions as original enhanced",
                adapter.contains("private int nativeEnhancedGridCardHeight(View view)")
                        && adapter.contains("return TmdbEpisodeGridPolicy.nativeGridCardHeightDp(isPhoneWidth(view));")
                        && adapter.contains("private int nativeEnhancedGridScrimHeight(View view)")
                        && adapter.contains("return TmdbEpisodeGridPolicy.nativeGridScrimHeightDp(isPhoneWidth(view));")
                        && policy.contains("public static final int NATIVE_GRID_CARD_HEIGHT_DP = 248;")
                        && policy.contains("public static final int NATIVE_MOBILE_GRID_CARD_HEIGHT_DP = 190;")
                        && policy.contains("public static final int NATIVE_GRID_SCRIM_HEIGHT_DP = 148;")
                        && policy.contains("public static final int NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP = 104;"));
        assertTrue("detail-page episode cards should keep focused grid cards fully visible inside the outer scroll view",
                activity.contains("episodeAdapter.setOnFocusChangeListener(this::onDetailEpisodeFocusChange);")
                        && activity.contains("episodeAdapter.setOnKeyListener(this::onDetailEpisodeKey);")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(view, keyCode, event));")
                        && activity.contains("private boolean onDetailEpisodeKey(View view, int keyCode, KeyEvent event)")
                        && activity.contains("TmdbEpisodeGridPolicy.verticalFocusTarget(position, span, episodeAdapter.getItemCount(), down)")
                        && activity.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && activity.contains("private boolean focusDetailEpisodeRangeButton()")
                        && activity.contains("private boolean focusDetailEpisode(int position)")
                        && activity.contains("private void alignDetailEpisodeFocusedRow(View focusedView, int position)")
                        && activity.contains("private void alignDetailEpisodeFocusedCardNow(View focusedView)")
                        && activity.contains("binding.scroll.scrollTo(0, targetY);")
                        && adapter.contains("private View.OnFocusChangeListener focusChangeListener;")
                        && adapter.contains("public void setOnFocusChangeListener(View.OnFocusChangeListener focusChangeListener)")
                        && adapter.contains("holder.binding.getRoot().setOnFocusChangeListener((view, focused) -> {")
                        && adapter.contains("if (focusChangeListener != null) focusChangeListener.onFocusChange(view, focused);"));
    }

    @Test
    public void detailEpisodeBottomRowDpadDownFocusesFirstVisibleTmdbRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int flagFocus = activity.indexOf("private boolean focusDetailFlagButton()", episodeKey);
        String episodeKeyBody = flagFocus > episodeKey ? activity.substring(episodeKey, flagFocus) : "";
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", episodeKey);
        int focusRecycler = activity.indexOf("private boolean focusTmdbRecycler(RecyclerView recycler)", firstTmdb);
        String firstTmdbBody = focusRecycler > firstTmdb ? activity.substring(firstTmdb, focusRecycler) : "";

        assertTrue(activityPath + " is missing onDetailEpisodeKey", episodeKey >= 0);
        assertTrue("detail episode bottom row DPAD_DOWN must leave the episode grid instead of consuming the key",
                episodeKeyBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && episodeKeyBody.contains("return focusFirstVisibleTmdbRow();"));
        assertTrue(activityPath + " is missing focusFirstVisibleTmdbRow", firstTmdb >= 0);
        assertTrue("TMDB photo row should be the first focus target below episodes",
                firstTmdbBody.indexOf("binding.episodePhotoList") >= 0
                        && firstTmdbBody.indexOf("binding.episodePhotoList") < firstTmdbBody.indexOf("binding.castList"));
        assertTrue("TMDB row focusing must request focus on a concrete RecyclerView item",
                activity.indexOf("holder.itemView.requestFocus();", focusRecycler) > focusRecycler);
    }

    @Test
    public void detailEpisodeGridDpadUpDownUsesFullHeightRecyclerViewUntilBoundary() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int moveEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        int focus = activity.indexOf("private boolean focusDetailEpisode(int position)");
        int focusEnd = activity.indexOf("private int detailEpisodeSpanCount()", focus);
        int focusChange = activity.indexOf("private void onDetailEpisodeFocusChange");
        int flagKey = activity.indexOf("private boolean onDetailFlagKey", focusChange);
        int viewport = activity.indexOf("private void updateEpisodeViewport");
        int viewportEnd = activity.indexOf("private void updateEpisodeLayoutForCurrentItems", viewport);

        assertTrue(activityPath + " is missing detail episode focus helpers", move >= 0 && moveEnd > move && focus > moveEnd && focusEnd > focus && focusChange >= 0 && flagKey > focusChange && viewport >= 0 && viewportEnd > viewport);
        String moveBody = activity.substring(move, moveEnd);
        String focusBody = activity.substring(focus, focusEnd);
        String focusChangeBody = activity.substring(focusChange, flagKey);
        String viewportBody = activity.substring(viewport, viewportEnd);

        assertTrue("card-to-card DPAD_UP should let RecyclerView keep its native focus and scroll behavior",
                moveBody.contains("TmdbEpisodeGridPolicy.verticalFocusTarget(position, span, episodeAdapter.getItemCount(), down)")
                        && moveBody.contains("return false;")
                        && !moveBody.contains("return focusDetailEpisode(position - span"));
        assertTrue("card-to-card DPAD_DOWN should let RecyclerView keep its native focus and scroll behavior",
                moveBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && moveBody.contains("return focusFirstVisibleTmdbRow();")
                        && !moveBody.contains("return focusDetailEpisode(target"));
        assertTrue("boundary DPAD_DOWN should still leave the episode grid only when no next row exists",
                moveBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET)")
                        && moveBody.contains("return focusFirstVisibleTmdbRow();"));
        assertTrue("manual episode entry should still focus and align a concrete card",
                focusBody.contains("RecyclerView.ViewHolder visibleHolder = binding.episodeContainer.findViewHolderForAdapterPosition(target);")
                        && focusBody.contains("visibleHolder.itemView.requestFocus();")
                        && focusBody.contains("alignDetailEpisodeFocusedRow(visibleHolder.itemView, target);"));
        assertTrue("card-to-card moves must not use viewport-preserve state that can fight RecyclerView focus restoration",
                !activity.contains("preserveDetailEpisodeViewportOnce")
                        && !focusBody.contains("preserveOuterScroll")
                        && !focusChangeBody.contains("consumeDetailEpisodeViewportPreserve"));
        assertTrue("nested episode grids should keep outer detail scroll fixed while RecyclerView handles internal row focus",
                focusChangeBody.contains("if (binding.episodeContainer.isNestedScrollingEnabled()) return;"));
        assertTrue("detail episode grid should expand to all rows instead of creating a nested 3-row scroll window",
                viewportBody.contains("params.height = ViewGroup.LayoutParams.WRAP_CONTENT;")
                        && viewportBody.contains("binding.episodeContainer.setNestedScrollingEnabled(false);")
                        && !viewportBody.contains("TmdbEpisodeGridPolicy.layout("));
    }

    @Test
    public void detailEpisodeHorizontalFocusSkipsSameRowOuterAlignment() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusChange = activity.indexOf("private void onDetailEpisodeFocusChange");
        int flagKey = activity.indexOf("private boolean onDetailFlagKey", focusChange);
        int clearFocusRow = activity.indexOf("private void clearDetailEpisodeFocusRowIfNeeded", focusChange);
        int rowStart = activity.indexOf("private int detailEpisodeRowStart", clearFocusRow);
        String focusChangeBody = focusChange >= 0 && flagKey > focusChange ? activity.substring(focusChange, flagKey) : "";

        assertTrue(activityPath + " is missing detail episode focus row tracking", focusChange >= 0 && flagKey > focusChange && clearFocusRow > focusChange && rowStart > clearFocusRow);
        assertTrue("same-row DPAD_LEFT/RIGHT should keep the outer detail scroll anchored to avoid edge-row flicker",
                activity.contains("private int lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;")
                        && focusChangeBody.contains("int rowStart = detailEpisodeRowStart(position);")
                        && focusChangeBody.contains("boolean sameFocusedRow = rowStart == lastDetailEpisodeFocusRowStart;")
                        && focusChangeBody.contains("lastDetailEpisodeFocusRowStart = rowStart;")
                        && focusChangeBody.contains("if (sameFocusedRow) return;"));
        assertTrue("leaving the episode grid must reset row tracking so the next entry can align normally",
                focusChangeBody.contains("if (!focused) {")
                        && focusChangeBody.contains("clearDetailEpisodeFocusRowIfNeeded(view);")
                        && activity.contains("lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;"));
    }

    @Test
    public void detailEpisodeGridModeDpadLeftRightStaysInsideGridRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        String moveBody = move >= 0 && firstTmdb > move ? activity.substring(move, firstTmdb) : "";

        assertTrue(activityPath + " is missing moveDetailEpisodeFocus", move >= 0 && firstTmdb > move);
        assertTrue("grid-mode DPAD_LEFT should move within the row and consume the row-start boundary",
                moveBody.contains("if (KeyUtil.isLeftKey(event))")
                        && moveBody.contains("if (position % span == 0) return true;")
                        && moveBody.contains("return focusDetailEpisode(position - 1);"));
        assertTrue("grid-mode DPAD_RIGHT should move within the row and consume row/end boundaries",
                moveBody.contains("if (KeyUtil.isRightKey(event))")
                        && moveBody.contains("position >= episodeAdapter.getItemCount() - 1 || position % span == span - 1")
                        && moveBody.contains("return focusDetailEpisode(position + 1);")
                        && !moveBody.contains("KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return false;"));
    }

    @Test
    public void detailEpisodeRangeDpadDownUsesButtonPositionInsteadOfSelectedEpisode() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int toolKey = activity.indexOf("private boolean onDetailEpisodeToolKey", rangeKey);
        int focusBelow = activity.indexOf("private boolean focusDetailEpisodeBelow", toolKey);
        int nearest = activity.indexOf("private int nearestVisibleDetailEpisodePositionBelow", focusBelow);
        int focusSelected = activity.indexOf("private boolean focusDetailEpisode()", nearest);

        assertTrue(activityPath + " is missing detail episode range spatial focus helpers",
                rangeKey >= 0 && toolKey > rangeKey && focusBelow > toolKey && nearest > focusBelow && focusSelected > nearest);
        String rangeBody = activity.substring(rangeKey, toolKey);
        String focusBelowBody = activity.substring(focusBelow, nearest);
        String nearestBody = activity.substring(nearest, focusSelected);

        assertTrue("episode range button key listeners should pass the focused button into DPAD handling",
                activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(view, keyCode, event));")
                        && activity.contains("return onDetailEpisodeRangeKey(focus, event.getKeyCode(), event);"));
        assertTrue("DPAD_DOWN from an episode range button should use the button's screen position, not the selected episode",
                rangeBody.contains("return focusDetailEpisodeBelow(view);")
                        && !rangeBody.contains("return focusDetailEpisode();"));
        assertTrue("spatial range-to-card focus should fall back to the first visible episode before using position 0",
                focusBelowBody.contains("int target = nearestVisibleDetailEpisodePositionBelow(source);")
                        && focusBelowBody.contains("if (target == RecyclerView.NO_POSITION) target = firstVisibleDetailEpisodePosition();")
                        && focusBelowBody.contains("return focusDetailEpisode(target);"));
        assertTrue("spatial range-to-card focus should compare visible episode cards in outer scroll coordinates",
                nearestBody.contains("binding.scroll.offsetDescendantRectToMyCoords(source, sourceRect);")
                        && nearestBody.contains("binding.episodeContainer.getChildCount()")
                        && nearestBody.contains("binding.episodeContainer.getChildAdapterPosition(child)")
                        && nearestBody.contains("Math.abs(rect.centerX() - sourceRect.centerX())"));
    }

    @Test
    public void detailEpisodeRangeFocusActivatesPageEvenWhenIndexStateAlreadyMatches() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusChange = activity.indexOf("private void setEpisodeRangeFocusChange");
        int activate = activity.indexOf("private void activateFocusedEpisodeRange", focusChange);
        int restore = activity.indexOf("private void restoreEpisodeRangeFocus", activate);
        int rerender = activity.indexOf("private void rerenderEpisodeViewportOnly");
        int updateStates = activity.indexOf("private void updateEpisodeRangeButtonStates", rerender);
        int selectRange = activity.indexOf("private void selectEpisodeRange", updateStates);
        int resolveRange = activity.indexOf("private int resolveEpisodeRangeIndex", selectRange);
        String focusBody = focusChange >= 0 && activate > focusChange ? activity.substring(focusChange, activate) : "";
        String activateBody = activate >= 0 && restore > activate ? activity.substring(activate, restore) : "";
        String rerenderBody = rerender >= 0 && updateStates > rerender ? activity.substring(rerender, updateStates) : "";
        String updateStatesBody = updateStates >= 0 && selectRange > updateStates ? activity.substring(updateStates, selectRange) : "";
        String selectBody = selectRange >= 0 && resolveRange > selectRange ? activity.substring(selectRange, resolveRange) : "";

        assertTrue(activityPath + " is missing episode range focus activation helpers",
                focusChange >= 0 && activate > focusChange && restore > activate && rerender >= 0 && updateStates > rerender && selectRange > updateStates && resolveRange > selectRange);
        assertTrue("episode range focus should activate the focused page instead of waiting for click",
                focusBody.contains("if (!focused) return;")
                        && focusBody.contains("activateFocusedEpisodeRange(index);")
                        && !focusBody.contains("index == episodeRangeIndex) return"));
        assertTrue("focused range activation should only skip work when both selected index and rendered page already match",
                activateBody.contains("if (index == episodeRangeIndex && index == renderedEpisodeRangeIndex) return;")
                        && activateBody.contains("pendingEpisodeRangeFocus = index;")
                        && activateBody.contains("binding.episodeRangeContainer.post(() ->")
                        && activateBody.contains("if (binding == null || pendingEpisodeRangeFocus != index) return;")
                        && activateBody.contains("selectEpisodeRange(index, false);"));
        assertTrue("episode viewport rendering should remember which range page is actually displayed",
                activity.contains("private int renderedEpisodeRangeIndex = -1;")
                        && rerenderBody.contains("renderedEpisodeRangeIndex = ranges.size() > 1 ? episodeRangeIndex : -1;"));
        assertTrue("episode range selection must not notify the adapter while RecyclerView is laying out or scrolling",
                selectBody.contains("binding.episodeContainer.isComputingLayout()")
                        && selectBody.contains("binding.episodeContainer.post(() -> selectEpisodeRange(index, scrollToSelection));"));
        assertTrue("updating selected range state must restore the range focus listener that setChipState replaces",
                updateStatesBody.contains("setChipState(button, i == episodeRangeIndex);")
                        && updateStatesBody.contains("setEpisodeRangeFocusChange(button, i);")
                        && updateStatesBody.indexOf("setChipState(button, i == episodeRangeIndex);") < updateStatesBody.indexOf("setEpisodeRangeFocusChange(button, i);"));
    }

    @Test
    public void detailPhotoCardsUseUnifiedMaterialFocusStrokeAndAlignedCorners() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_photo.xml"));
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPhotoAdapter.java"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);

        assertTrue("photo card root should own both clipping and focus stroke so rounded corners line up",
                layout.contains("<com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"8dp\"")
                        && layout.contains("app:strokeWidth=\"1dp\"")
                        && layout.contains("app:strokeColor=\"#33FFFFFF\""));
        assertTrue("photo cards should not stack the old selector or platform focus highlight over the card radius",
                layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                        && layout.contains("android:stateListAnimator=\"@null\"")
                        && !layout.contains("@drawable/selector_tmdb_card")
                        && !layout.contains("?attr/selectableItemBackground"));
        assertTrue("photo adapter should use the shared TMDB card focus helper for the yellow focus stroke",
                adapter.contains("private final MaterialCardView card;")
                        && adapter.contains("card = (MaterialCardView) itemView;")
                        && adapter.contains("TmdbCardFocusHelper.bind(card")
                        && adapter.contains("light ? 0x33647480 : 0x33FFFFFF"));
    }

    @Test
    public void detailTmdbHorizontalCardsDoNotUseGrayStateOverlays() throws Exception {
        String[] layouts = {
                "adapter_tmdb_cast.xml",
                "adapter_tmdb_person.xml",
                "adapter_tmdb_person_photo.xml",
                "adapter_tmdb_rail_item.xml",
                "adapter_tmdb_rail_landscape.xml",
                "adapter_tmdb_recommendation_landscape.xml",
                "adapter_tmdb_work.xml",
                "item_tmdb_person_photo.xml",
                "item_tmdb_person_work.xml"
        };

        for (String file : layouts) {
            String layout = readLayout(file);
            assertTrue(file + " should use a Material card root so focus is drawn by stroke",
                    layout.contains("<com.google.android.material.card.MaterialCardView"));
            assertTrue(file + " should disable platform focus/state overlays",
                    layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                            && layout.contains("android:stateListAnimator=\"@null\"")
                            && layout.contains("app:rippleColor=\"@android:color/transparent\""));
            assertTrue(file + " should not put selector/ripple drawables over card content",
                    !layout.contains("?attr/selectableItemBackground")
                            && !layout.contains("@drawable/selector_tmdb_card")
                            && !layout.contains("@drawable/selector_tmdb_cast_focus"));
        }

        String helper = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbCardFocusHelper.java");
        assertTrue("shared TMDB card focus helper should clear gray state overlays before applying visible foreground focus",
                helper.contains("card.setSelected(false);")
                        && helper.contains("card.setActivated(false);")
                        && helper.contains("card.setChecked(false);")
                        && helper.contains("card.setForeground(null);")
                        && helper.contains("card.setRippleColor(ColorStateList.valueOf(0x00000000));"));
        assertTrue("shared TMDB card focus helper should draw a transparent foreground border above card content",
                helper.contains("private static final int FOCUS_STROKE = 0xFFFFD166;")
                        && helper.contains("card.setStrokeColor(focused ? FOCUS_STROKE : strokeColor);")
                        && helper.contains("card.setForeground(focused ? foregroundBorder(card, FOCUS_STROKE, FOCUS_STROKE_DP) : null);")
                        && helper.contains("drawable.setColor(Color.TRANSPARENT);")
                        && !helper.contains("FOCUS_SCALE")
                        && !helper.contains("scaleX(")
                        && !helper.contains("scaleY("));

        String castAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbCastAdapter.java");
        assertTrue("cast/creator cards should use the same stroke-only helper instead of foreground activation",
                castAdapter.contains("TmdbCardFocusHelper.bind(card")
                        && !castAdapter.contains("setForeground(")
                        && !castAdapter.contains("setActivated(focused)"));

        String personPhotoAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPersonPhotoAdapter.java");
        String personWorkAdapter = readJava("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPersonWorkAdapter.java");
        assertTrue("person photo cards should also use stroke-only focus",
                personPhotoAdapter.contains("TmdbCardFocusHelper.bind(card"));
        assertTrue("person work cards should also use stroke-only focus",
                personWorkAdapter.contains("TmdbCardFocusHelper.bind(card"));
    }

    @Test
    public void detailEpisodeListModeDpadUpDownLeavesHorizontalEpisodeRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int move = activity.indexOf("private boolean moveDetailEpisodeFocus");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", move);
        String moveBody = move >= 0 && firstTmdb > move ? activity.substring(move, firstTmdb) : "";
        int listBranch = moveBody.indexOf("if (!episodeGridMode) return moveDetailEpisodeListFocus(position, event);");
        int gridBranch = moveBody.indexOf("int span = detailEpisodeSpanCount()");
        int listHelper = activity.indexOf("private boolean moveDetailEpisodeListFocus");
        int listHelperEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", listHelper);
        String listBody = listHelper >= 0 && listHelperEnd > listHelper ? activity.substring(listHelper, listHelperEnd) : "";

        assertTrue(activityPath + " is missing moveDetailEpisodeFocus", move >= 0);
        assertTrue("detail list-mode episodes are a horizontal row, so DPAD handling must happen before grid span math",
                listBranch >= 0 && gridBranch > listBranch);
        assertTrue("list-mode DPAD_UP should leave the horizontal episode row toward range/tools/lines",
                listBody.contains("if (focusDetailEpisodeRangeButton()) return true;")
                        && listBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;")
                        && listBody.contains("return focusDetailFlagButton();"));
        assertTrue("list-mode DPAD_DOWN should leave the horizontal episode row toward the first visible TMDB row",
                listBody.contains("return focusFirstVisibleTmdbRow();"));
    }

    @Test
    public void detailEpisodeListModeDpadLeftRightStaysInsideHorizontalEpisodeRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int episodeKeyEnd = activity.indexOf("private boolean moveDetailEpisodeFocus", episodeKey);
        int containerKey = activity.indexOf("private boolean onDetailEpisodeContainerKey");
        int containerKeyEnd = activity.indexOf("private boolean isFocusInside", containerKey);
        int listHelper = activity.indexOf("private boolean moveDetailEpisodeListFocus");
        int listHelperEnd = activity.indexOf("private boolean focusFirstVisibleTmdbRow()", listHelper);
        String episodeKeyBody = episodeKey >= 0 && episodeKeyEnd > episodeKey ? activity.substring(episodeKey, episodeKeyEnd) : "";
        String containerKeyBody = containerKey >= 0 && containerKeyEnd > containerKey ? activity.substring(containerKey, containerKeyEnd) : "";
        String listBody = listHelper >= 0 && listHelperEnd > listHelper ? activity.substring(listHelper, listHelperEnd) : "";

        assertTrue(activityPath + " is missing detail episode list focus helpers",
                episodeKey >= 0 && episodeKeyEnd > episodeKey && containerKey >= 0 && containerKeyEnd > containerKey && listHelper >= 0 && listHelperEnd > listHelper);
        assertTrue("episode card key handling must capture DPAD_LEFT/RIGHT before Android focus search can leave the row",
                episodeKeyBody.contains("KeyUtil.isLeftKey(event)")
                        && episodeKeyBody.contains("KeyUtil.isRightKey(event)")
                        && containerKeyBody.contains("KeyUtil.isLeftKey(event)")
                        && containerKeyBody.contains("KeyUtil.isRightKey(event)"));
        assertTrue("list-mode DPAD_LEFT should move to the previous episode and consume the first-card boundary",
                listBody.contains("if (KeyUtil.isLeftKey(event))")
                        && listBody.contains("if (position <= 0) return true;")
                        && listBody.contains("return focusDetailEpisode(position - 1);"));
        assertTrue("list-mode DPAD_RIGHT should move to the next episode and consume the last-card boundary",
                listBody.contains("if (KeyUtil.isRightKey(event))")
                        && listBody.contains("position >= episodeAdapter.getItemCount() - 1")
                        && listBody.contains("return focusDetailEpisode(position + 1);"));
    }

    @Test
    public void detailTmdbHorizontalRowsMoveWithinRowAndConsumeBoundaries() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int navigation = activity.indexOf("private boolean handleDetailEpisodeNavigationKey");
        int detailRows = activity.indexOf("private RecyclerView detailTmdbRecyclerContainingFocus", navigation);
        int rowKey = activity.indexOf("private boolean onDetailTmdbRowKey", detailRows);
        int focusItem = activity.indexOf("private boolean focusTmdbRecyclerItem", rowKey);
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeContainerKey", rowKey);
        String navigationBody = navigation >= 0 && detailRows > navigation ? activity.substring(navigation, detailRows) : "";
        String detailRowsBody = detailRows >= 0 && rowKey > detailRows ? activity.substring(detailRows, rowKey) : "";
        String rowKeyBody = rowKey >= 0 && episodeKey > rowKey ? activity.substring(rowKey, episodeKey) : "";

        assertTrue(activityPath + " is missing TMDB horizontal row key helpers",
                navigation >= 0 && detailRows > navigation && rowKey > detailRows && focusItem > rowKey && episodeKey > focusItem);
        assertTrue("detail navigation should route TMDB horizontal card rows through the shared boundary guard",
                navigationBody.contains("RecyclerView tmdbRow = detailTmdbRecyclerContainingFocus(focus);")
                        && navigationBody.contains("if (tmdbRow != null) return onDetailTmdbRowKey(tmdbRow, focus, event);"));
        assertTrue("TMDB horizontal rows should include stills, people, related, and personal recommendation rails",
                detailRowsBody.contains("binding.episodePhotoList")
                        && detailRowsBody.contains("binding.castList")
                        && detailRowsBody.contains("binding.creatorList")
                        && detailRowsBody.contains("binding.relatedList")
                        && detailRowsBody.contains("binding.personalTmdbList")
                        && detailRowsBody.contains("binding.personalDoubanList")
                        && detailRowsBody.contains("binding.personalAiList"));
        assertTrue("TMDB horizontal row DPAD_LEFT/RIGHT should explicitly move to adjacent cards and consume first/last boundaries",
                rowKeyBody.contains("if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;")
                        && rowKeyBody.contains("if (!KeyUtil.isActionDown(event)) return true;")
                        && rowKeyBody.contains("int target = KeyUtil.isLeftKey(event) ? position - 1 : position + 1;")
                        && rowKeyBody.contains("if (target < 0 || target >= adapter.getItemCount()) return true;")
                        && rowKeyBody.contains("focusTmdbRecyclerItem(recycler, target);")
                        && rowKeyBody.contains("RecyclerView.ViewHolder visibleHolder = recycler.findViewHolderForAdapterPosition(target);")
                        && rowKeyBody.contains("visibleHolder.itemView.requestFocus();")
                        && rowKeyBody.contains("recycler.scrollToPosition(target);")
                        && rowKeyBody.contains("holder.itemView.requestFocus();"));
    }

    @Test
    public void detailFocusableButtonGroupsUseExplicitDpadNavigation() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int navigation = activity.indexOf("private boolean handleDetailEpisodeNavigationKey");
        int detailRows = activity.indexOf("private RecyclerView detailTmdbRecyclerContainingFocus", navigation);
        int flagKey = activity.indexOf("private boolean onDetailFlagKey");
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int toolKey = activity.indexOf("private boolean onDetailEpisodeToolKey");
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        int seasonKey = activity.indexOf("private boolean onDetailSeasonKey");
        int horizontal = activity.indexOf("private boolean onDetailHorizontalButtonGroupKey");
        int vertical = activity.indexOf("private boolean onDetailExternalLinksKey");
        int focusTarget = activity.indexOf("private View horizontalFocusTarget");
        String navigationBody = navigation >= 0 && detailRows > navigation ? activity.substring(navigation, detailRows) : "";
        String flagKeyBody = flagKey >= 0 && rangeKey > flagKey ? activity.substring(flagKey, rangeKey) : "";
        String rangeKeyBody = rangeKey >= 0 && toolKey > rangeKey ? activity.substring(rangeKey, toolKey) : "";
        String toolKeyBody = toolKey >= 0 && episodeKey > toolKey ? activity.substring(toolKey, episodeKey) : "";
        String seasonKeyBody = seasonKey >= 0 && horizontal > seasonKey ? activity.substring(seasonKey, horizontal) : "";
        String horizontalBody = horizontal >= 0 && vertical > horizontal ? activity.substring(horizontal, vertical) : "";
        String verticalBody = vertical >= 0 && focusTarget > vertical ? activity.substring(vertical, focusTarget) : "";

        assertTrue(activityPath + " is missing explicit detail button navigation helpers",
                navigation >= 0 && detailRows > navigation && horizontal >= 0 && vertical > horizontal && focusTarget > vertical);
        assertTrue("activity-level dispatch should guard every focusable detail button group before Android focus search runs",
                navigationBody.contains("isFocusInside(focus, binding.headerBar)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.headerBar, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.fusionActions)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.fusionActions, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.detailActions)")
                        && navigationBody.contains("onDetailHorizontalButtonGroupKey(binding.detailActions, null, focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.seasonContainer)")
                        && navigationBody.contains("onDetailSeasonKey(focus, event)")
                        && navigationBody.contains("isFocusInside(focus, binding.externalLinksContainer)")
                        && navigationBody.contains("onDetailExternalLinksKey(focus, event)"));
        assertTrue("line, episode-page, episode-tool, and season buttons should handle DPAD_LEFT/RIGHT inside their own row",
                flagKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.flagContainer, binding.flagScroll, focus, event)")
                        && rangeKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.episodeRangeContainer, binding.episodeRangeScroll, view, event)")
                        && toolKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.episodeHeader, null, view, event)")
                        && seasonKeyBody.contains("onDetailHorizontalButtonGroupKey(binding.seasonContainer, null, focus, event)"));
        assertTrue("season buttons should move vertically inside the season grid before leaving it",
                seasonKeyBody.contains("if (KeyUtil.isUpKey(event)) return focusDetailSeasonSibling(focus, true) || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();")
                        && seasonKeyBody.contains("return focusDetailSeasonSibling(focus, false) || focusDetailEpisodeRangeButton() || focusDetailEpisode();")
                        && seasonKeyBody.contains("private boolean focusDetailSeasonSibling(View focus, boolean up)")
                        && seasonKeyBody.contains("View target = FocusFinder.getInstance().findNextFocus(binding.seasonContainer, focus, direction);")
                        && seasonKeyBody.contains("return target.requestFocus(direction);"));
        assertTrue("horizontal button groups should move only to same-row neighbors and consume row boundaries",
                horizontalBody.contains("if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;")
                        && horizontalBody.contains("if (!KeyUtil.isActionDown(event)) return true;")
                        && horizontalBody.contains("View target = horizontalFocusTarget(group, focus, KeyUtil.isLeftKey(event));")
                        && horizontalBody.contains("if (target == null) return true;")
                        && horizontalBody.contains("target.requestFocus(KeyUtil.isLeftKey(event) ? View.FOCUS_LEFT : View.FOCUS_RIGHT);")
                        && horizontalBody.contains("scrollHorizontalChildIntoView(scroll, target);"));
        assertTrue("external link buttons should move vertically inside the list and consume horizontal keys",
                verticalBody.contains("if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return true;")
                        && verticalBody.contains("return moveDetailFocusVertically(binding.externalLinksContainer, focus, KeyUtil.isUpKey(event));"));
    }

    @Test
    public void detailExternalLinkFirstRowDpadUpReturnsToRecommendationCardRow() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int externalKey = activity.indexOf("private boolean onDetailExternalLinksKey");
        int horizontalTarget = activity.indexOf("private View horizontalFocusTarget", externalKey);
        int lastTmdb = activity.indexOf("private boolean focusLastVisibleTmdbRow");
        int firstTmdb = activity.indexOf("private boolean focusFirstVisibleTmdbRow", lastTmdb);
        String externalBody = externalKey >= 0 && horizontalTarget > externalKey ? activity.substring(externalKey, horizontalTarget) : "";
        String lastTmdbBody = lastTmdb >= 0 && firstTmdb > lastTmdb ? activity.substring(lastTmdb, firstTmdb) : "";

        assertTrue(activityPath + " is missing external-link upward focus helpers",
                externalKey >= 0 && horizontalTarget > externalKey && lastTmdb >= 0 && firstTmdb > lastTmdb);
        assertTrue("first external link DPAD_UP should leave the link list and focus the card row above it",
                externalBody.contains("if (KeyUtil.isUpKey(event) && detailFocusableIndex(binding.externalLinksContainer, focus) == 0) {")
                        && externalBody.contains("if (focusLastVisibleTmdbRow()) return true;")
                        && externalBody.contains("return false;")
                        && externalBody.contains("return moveDetailFocusVertically(binding.externalLinksContainer, focus, KeyUtil.isUpKey(event));"));
        assertTrue("external-link upward fallback should prefer the last visible TMDB row before the external links",
                lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalAiList)") >= 0
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalAiList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalDoubanList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalDoubanList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalTmdbList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.personalTmdbList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.relatedList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.relatedList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.creatorList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.creatorList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.castList)")
                        && lastTmdbBody.indexOf("focusTmdbRecycler(binding.castList)") < lastTmdbBody.indexOf("focusTmdbRecycler(binding.episodePhotoList)"));
    }

    @Test
    public void leanbackDetailOverviewRendersAsPlainFullText() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int init = activity.indexOf("protected void initView(Bundle savedInstanceState)");
        int setup = activity.indexOf("private void setupOverviewInteraction()");
        int setupEnd = activity.indexOf("private void setupInlineControlFocus()", setup);
        int shouldFull = activity.indexOf("private boolean shouldShowFullOverview()");
        int overflow = activity.indexOf("private boolean isOverviewOverflowing()", shouldFull);
        String initBody = init >= 0 && setup > init ? activity.substring(init, setup) : "";
        String setupBody = setup >= 0 && setupEnd > setup ? activity.substring(setup, setupEnd) : "";
        String shouldFullBody = shouldFull >= 0 && overflow > shouldFull ? activity.substring(shouldFull, overflow) : "";

        assertTrue(activityPath + " is missing overview interaction setup helpers",
                init >= 0 && setup > init && setupEnd > setup && shouldFull >= 0 && overflow > shouldFull);
        assertTrue("TMDB detail initialization should route overview click/focus setup through one helper",
                initBody.contains("setupOverviewInteraction();")
                        && !initBody.contains("binding.overview.setOnClickListener(view -> toggleOverview());")
                        && !initBody.contains("binding.overviewToggle.setOnClickListener(view -> toggleOverview());"));
        assertTrue("mobile can keep tap-to-expand, but leanback overview must stay plain non-focusable text",
                setupBody.contains("if (Util.isMobile())")
                        && setupBody.contains("binding.overview.setOnClickListener(view -> toggleOverview());")
                        && setupBody.contains("binding.overviewToggle.setOnClickListener(view -> toggleOverview());")
                        && setupBody.contains("binding.overview.setOnClickListener(null);")
                        && setupBody.contains("binding.overview.setClickable(false);")
                        && setupBody.contains("binding.overview.setFocusable(false);")
                        && setupBody.contains("binding.overview.setFocusableInTouchMode(false);")
                        && setupBody.contains("binding.overviewToggle.setOnClickListener(null);")
                        && setupBody.contains("binding.overviewToggle.setClickable(false);")
                        && setupBody.contains("binding.overviewToggle.setFocusable(false);")
                        && setupBody.contains("binding.overviewToggle.setFocusableInTouchMode(false);"));
        assertTrue("leanback detail overview should show all text instead of exposing a fold/unfold focus target",
                shouldFullBody.contains("return !Util.isMobile();"));
    }

    @Test
    public void detailEpisodeDownToTmdbRowsUsesImmediateFocusWithoutScrollFlicker() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int focusRecycler = activity.indexOf("private boolean focusTmdbRecycler(RecyclerView recycler)");
        int scrollHelper = activity.indexOf("private void scrollDetailChildIntoView", focusRecycler);
        String body = focusRecycler >= 0 && scrollHelper > focusRecycler ? activity.substring(focusRecycler, scrollHelper) : "";

        assertTrue(activityPath + " is missing focusTmdbRecycler", focusRecycler >= 0);
        assertTrue("DPAD_DOWN from episode cards should stop row scrolling before moving into TMDB rows",
                body.contains("recycler.stopScroll();"));
        assertTrue("DPAD_DOWN from episode cards should align the TMDB row immediately instead of animating the outer scroll",
                body.contains("scrollDetailChildIntoViewNow(recycler, 12);")
                        && !body.contains("scrollDetailChildIntoView(recycler, 12);"));
        assertTrue("DPAD_DOWN from episode cards should not delay focus long enough to show a visual blink",
                body.contains("recycler.post(() ->")
                        && !body.contains("postDelayed"));
    }

    @Test
    public void detailEpisodeToolButtonsUseSharedFocusStroke() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int setup = activity.indexOf("private void setEpisodeToolButton(MaterialButton button, ThemeColors colors)");
        int setupEnd = activity.indexOf("private void applyEpisodeToolButtonsFocus()", setup);
        int apply = activity.indexOf("private void applyEpisodeToolButtonFocus(MaterialButton button, ThemeColors colors)");
        int applyEnd = activity.indexOf("private void tintTextTree", apply);
        String setupBody = setup >= 0 && setupEnd > setup ? activity.substring(setup, setupEnd) : "";
        String applyBody = apply >= 0 && applyEnd > apply ? activity.substring(apply, applyEnd) : "";

        assertTrue(activityPath + " is missing episode tool button setup", setup >= 0 && apply >= 0);
        assertTrue("episode tool buttons should clear selected/activated state so the current-playing accent cannot bleed into reverse/grid",
                setupBody.contains("button.setSelected(false);")
                        && setupBody.contains("button.setActivated(false);"));
        assertTrue("episode tool buttons should suppress Material ripple color so focus does not flash with the generic accent",
                setupBody.contains("button.setRippleColor(ColorStateList.valueOf(0x00000000));"));
        assertTrue("episode tool focus refresh should keep text and icons on the neutral detail theme color",
                applyBody.contains("button.setTextColor(colors.primary);")
                        && applyBody.contains("button.setIconTint(ColorStateList.valueOf(colors.primary));"));
        assertTrue("episode tool focus refresh should use the shared yellow focus stroke and themed idle stroke",
                applyBody.contains("focused ? FOCUS_STROKE : colors.lineStrong")
                        && !applyBody.contains("focused ? colors.accent : colors.lineStrong"));
    }

    @Test
    public void detailEpisodeHeaderToolsStayVisibleAndDpadReachable() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int flagKey = activity.indexOf("private boolean onDetailFlagKey");
        int rangeKey = activity.indexOf("private boolean onDetailEpisodeRangeKey");
        int episodeKey = activity.indexOf("private boolean onDetailEpisodeKey");
        String flagKeyBody = flagKey >= 0 && rangeKey > flagKey ? activity.substring(flagKey, rangeKey) : "";
        String rangeKeyBody = rangeKey >= 0 && episodeKey > rangeKey ? activity.substring(rangeKey, episodeKey) : "";
        int helper = activity.indexOf("private boolean focusDetailEpisodeToolButton(int direction)");
        int nextHelper = activity.indexOf("private boolean focusDetailFlagButton()", helper);
        String episodeKeyBody = episodeKey >= 0 && helper > episodeKey ? activity.substring(episodeKey, helper) : "";
        String helperBody = helper >= 0 && nextHelper > helper ? activity.substring(helper, nextHelper) : "";
        int dispatch = activity.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int inlineKey = activity.indexOf("private boolean handleInlineKey(KeyEvent event)", dispatch);
        String dispatchBody = dispatch >= 0 && inlineKey > dispatch ? activity.substring(dispatch, inlineKey) : "";

        assertTrue("detail layout must include reverse, filename, and grid/list controls",
                layout.indexOf("android:id=\"@+id/episodeReverse\"") >= 0
                        && layout.indexOf("android:id=\"@+id/episodeReverse\"") < layout.indexOf("android:id=\"@+id/episodeFileName\"")
                        && layout.indexOf("android:id=\"@+id/episodeFileName\"") < layout.indexOf("android:id=\"@+id/episodeViewMode\""));
        assertTrue("detail episode view-mode button must stay visible on TV detail pages",
                activity.contains("binding.episodeViewMode.setVisibility(View.VISIBLE);")
                        && !activity.contains("binding.episodeViewMode.setVisibility(shouldForceAdaptiveEpisodeGrid() ? View.GONE : View.VISIBLE);"));
        assertTrue("line-row DPAD_DOWN should still reach the episode header tool button before episode pages",
                flagKeyBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_DOWN)) return true;")
                        && flagKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_DOWN)") < flagKeyBody.indexOf("focusDetailEpisodeRangeButton()"));
        assertTrue("episode-page DPAD_UP should return to seasons before header tools or lines",
                rangeKeyBody.contains("if (KeyUtil.isUpKey(event)) return focusDetailSeasonButton() || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();"));
        assertTrue("top-row episode DPAD_UP should fall back to the header tools before lines",
                episodeKeyBody.contains("if (focusDetailEpisodeRangeButton()) return true;")
                        && episodeKeyBody.contains("if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;")
                        && episodeKeyBody.indexOf("focusDetailEpisodeRangeButton()") < episodeKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_UP)")
                        && episodeKeyBody.indexOf("focusDetailEpisodeToolButton(View.FOCUS_UP)") < episodeKeyBody.indexOf("focusDetailFlagButton()"));
        assertTrue("episode header tool focusing should include every visible header tool",
                helperBody.contains("return focusDetailButton(binding.episodeReverse, direction)")
                        && helperBody.contains("|| focusDetailButton(binding.episodeFileName, direction)")
                        && helperBody.contains("|| focusDetailButton(binding.episodeViewMode, direction);")
                        && helperBody.contains("button.requestFocus(direction);"));
        assertTrue("episode header tools should move down to seasons or episode ranges without returning to lines",
                activity.contains("binding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("binding.episodeFileName.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("binding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));")
                        && activity.contains("if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.episodeHeader, null, view, event);")
                        && activity.contains("return focusDetailSeasonButton() || focusDetailEpisodeRangeButton() || focusDetailEpisode();"));
        assertTrue("activity-level key dispatch should guard the detail episode focus chain when child listeners are bypassed",
                dispatchBody.contains("if (handleDetailEpisodeNavigationKey(event)) return true;")
                        && dispatchBody.contains("isFocusInside(focus, binding.flagScroll)") && dispatchBody.contains("onDetailFlagKey(event.getKeyCode(), event)")
                        && dispatchBody.contains("isEpisodeToolButton(focus)") && dispatchBody.contains("onDetailEpisodeToolKey(focus, event.getKeyCode(), event)")
                        && dispatchBody.contains("isFocusInside(focus, binding.episodeRangeScroll)") && dispatchBody.contains("onDetailEpisodeRangeKey(focus, event.getKeyCode(), event)")
                        && dispatchBody.contains("isFocusInside(focus, binding.episodeContainer)") && dispatchBody.contains("onDetailEpisodeContainerKey(focus, event)")
                        && activity.contains("binding.episodeContainer.findContainingViewHolder(focus)")
                        && activity.contains("return moveDetailEpisodeFocus(position, event);"));
    }

    @Test
    public void inlineEpisodeModeToggleClicksImmediatelyOnMobileWhileTvKeepsFocusNavigation() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int mobileMethod = activity.indexOf("private MaterialButton createInlineEpisodeModeButton()");
        int mobileMethodEnd = activity.indexOf("private void updateInlineEpisodeModeButton(MaterialButton button)", mobileMethod);
        int sharedMethod = activity.indexOf("private void showNativeEnhancedInlineEpisodes()");
        int sharedMethodEnd = activity.indexOf("private boolean moveEpisodeDialogPageFocus(", sharedMethod);

        assertTrue(activityPath + " is missing createInlineEpisodeModeButton", mobileMethod >= 0 && mobileMethodEnd > mobileMethod);
        assertTrue(activityPath + " is missing showNativeEnhancedInlineEpisodes", sharedMethod >= 0 && sharedMethodEnd > sharedMethod);

        String mobileBody = activity.substring(mobileMethod, mobileMethodEnd);
        String sharedBody = activity.substring(sharedMethod, sharedMethodEnd);

        assertTrue("mobile inline episode mode toggle should switch on the first tap instead of becoming touch-focusable",
                !mobileBody.contains("button.setFocusableInTouchMode(true);"));
        assertTrue("native-enhanced inline episode panel should keep remote-driven focus navigation",
                sharedBody.contains("button.setOnKeyListener(flagKeyListener);")
                        && sharedBody.contains("adapter.setOnKeyListener")
                        && sharedBody.contains("focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount())"));
    }

    @Test
    public void leanbackFusionEpisodeFocusTakesPriorityOverInlineSeekKeys() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int dispatch = activity.indexOf("public boolean dispatchKeyEvent(KeyEvent event)");
        int dispatchEnd = activity.indexOf("private boolean handleDetailEpisodeNavigationKey(KeyEvent event)", dispatch);
        int seek = activity.indexOf("private boolean canInlineKeySeek(KeyEvent event)");
        int seekEnd = activity.indexOf("private boolean canInlineSeek()", seek);

        assertTrue(activityPath + " is missing dispatchKeyEvent", dispatch >= 0 && dispatchEnd > dispatch);
        assertTrue(activityPath + " is missing canInlineKeySeek", seek >= 0 && seekEnd > seek);

        String dispatchBody = activity.substring(dispatch, dispatchEnd);
        String seekBody = activity.substring(seek, seekEnd);
        int detailNavigation = dispatchBody.indexOf("if (handleDetailEpisodeNavigationKey(event)) return true;");
        int inlineKey = dispatchBody.indexOf("if (handleInlineKey(event)) return true;");

        assertTrue("detail episode focus must handle DPAD before inline playback maps LEFT/RIGHT to seek",
                detailNavigation >= 0 && inlineKey > detailNavigation);
        assertTrue("DPAD LEFT/RIGHT seek must only run when the player panel owns focus, so episode dialogs can navigate horizontally",
                seekBody.contains("if (isInlineMediaSeekKey(event)) return true;")
                        && seekBody.contains("if (isInlineControlsVisible()) return false;")
                        && seekBody.contains("View focus = getCurrentFocus();")
                        && seekBody.contains("focus == binding.playerPanel")
                        && seekBody.contains("inlineFullscreen && (focus == null || isFocusInside(focus, binding.playerPanel))")
                        && !seekBody.contains("inlineFullscreen || getCurrentFocus() == binding.playerPanel"));
    }

    @Test
    public void leanbackInlinePlayerConfirmEntersFullscreenBeforeShowingControls() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int confirm = activity.indexOf("private void onInlinePanelConfirm()");
        int helper = activity.indexOf("private void enterInlineFullscreenOrShowControlsOnConfirm()", confirm);
        int nextMethod = activity.indexOf("private void toggleInlinePlayback()", helper);

        assertTrue(activityPath + " is missing onInlinePanelConfirm", confirm >= 0);
        assertTrue(activityPath + " is missing enterInlineFullscreenOrShowControlsOnConfirm", helper > confirm && nextMethod > helper);

        String confirmBody = activity.substring(confirm, helper);
        String helperBody = activity.substring(helper, nextMethod);

        assertTrue("embedded inline confirm should delegate the non-fullscreen/no-controls case",
                confirmBody.contains("enterInlineFullscreenOrShowControlsOnConfirm();")
                        && confirmBody.indexOf("enterInlineFullscreenOrShowControlsOnConfirm();") > confirmBody.indexOf("toggleInlinePlayback();")
                        && !confirmBody.contains("else {\n            showInlineControls(true);"));
        assertTrue("TV inline confirm should enter fullscreen before falling back to the controls overlay",
                helperBody.contains("if (Util.isLeanback() && canEnterInlineFullscreenOnConfirm())")
                        && helperBody.contains("enterInlineFullscreen();")
                        && helperBody.contains("private boolean canEnterInlineFullscreenOnConfirm()")
                        && helperBody.contains("!inlinePiPLayout")
                        && helperBody.contains("!isInPictureInPictureMode()")
                        && helperBody.contains("showInlineControls(true);")
                        && helperBody.indexOf("enterInlineFullscreen();") < helperBody.indexOf("showInlineControls(true);"));
    }

    @Test
    public void tmdbEpisodeDataIsBoundBackToSourceEpisodesAndRefreshesByDataSeason() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path servicePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "TmdbService.java"));
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);

        int bindSeason = activity.indexOf("private void bindSeasonEpisodes(List<Episode> sourceEpisodes)");
        int dataSeason = activity.indexOf("private int tmdbEpisodeDataSeason(List<Episode> sourceEpisodes)", bindSeason);
        int fetchSeason = activity.indexOf("private void fetchSeasonIfNeeded(int seasonNumber)");
        int updateSkeleton = activity.indexOf("private void updateEpisodeSkeleton()");

        assertTrue(activityPath + " is missing bindSeasonEpisodes", bindSeason >= 0 && dataSeason > bindSeason);
        assertTrue(activityPath + " is missing fetchSeasonIfNeeded", fetchSeason >= 0 && updateSkeleton > fetchSeason);

        String bindBody = activity.substring(bindSeason, dataSeason);
        String fetchBody = activity.substring(fetchSeason, updateSkeleton);

        assertTrue("detail episodes should bind matched TMDB objects back onto source Episode items for playback cards and dialogs",
                bindBody.contains("bindTmdbEpisodes(sourceEpisodes, tmdbSeason);")
                        && activity.contains("TmdbEpisode tmdbEpisode = tmdbEpisodes.get(position.number());")
                        && activity.contains("episode.setTmdbEpisode(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode) ? tmdbEpisode : null);"));
        assertTrue("season fetch completion should refresh against the active TMDB data season, not only the selected source season",
                fetchBody.contains("seasonNumber == tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes())"));
        assertTrue("stale split-season TMDB caches should trigger a one-shot fresh first-season probe for long single-season shows",
                bindBody.contains("refreshFirstSeasonIfStaleSplit(sourceEpisodes);")
                        && activity.contains("List<TmdbEpisode> cachedEpisodes = tmdbSeasonEpisodes.get(firstSeason);")
                        && activity.contains("int cachedCount = cachedEpisodes == null ? 0 : cachedEpisodes.size();")
                        && activity.contains("if (cachedCount >= neededCount) return;")
                        && activity.contains("fetchSeasonIfNeeded(firstSeason, true);")
                        && activity.contains("seasonEpisodeCounts.put(seasonNumber, episodes.size());")
                        && service.contains("season(@NonNull TmdbItem item, int seasonNumber, @NonNull TmdbConfig config, JsonObject detail, boolean refresh)")
                        && service.contains("refresh ? null : readFirstCache(lookupFiles, ttl)")
                        && service.contains("readFirstCache(lookupFiles, Long.MAX_VALUE)"));
    }

    @Test
    public void inlineFullscreenExitRestoresEmbeddedPlayerLayout() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int restore = activity.indexOf("private void restoreInlinePlayerPanelAfterOverlay()");
        int exitFullscreen = activity.indexOf("private void exitInlineFullscreen()");
        int exitPiP = activity.indexOf("private void exitInlinePiPLayout()");
        int enterFullscreen = activity.indexOf("private void enterInlineFullscreen()");
        int enterPiP = activity.indexOf("private void enterInlinePiPLayout()");
        int focusPlayerPanel = activity.indexOf("private void focusInlinePlayerPanel()");
        int backFromFullscreen = activity.indexOf("private void backFromInlineFullscreen()");
        int handleInlineKey = activity.indexOf("private boolean handleInlineKey(KeyEvent event)");
        int onBackInvoked = activity.indexOf("protected void onBackInvoked()");

        assertTrue(activityPath + " is missing restoreInlinePlayerPanelAfterOverlay", restore >= 0);
        assertTrue(activityPath + " is missing exitInlineFullscreen", exitFullscreen >= 0);
        assertTrue(activityPath + " is missing exitInlinePiPLayout", exitPiP >= 0);
        assertTrue(activityPath + " is missing enterInlineFullscreen", enterFullscreen >= 0);
        assertTrue(activityPath + " is missing enterInlinePiPLayout", enterPiP >= 0);
        assertTrue(activityPath + " is missing focusInlinePlayerPanel", focusPlayerPanel >= 0);
        assertTrue(activityPath + " is missing backFromInlineFullscreen", backFromFullscreen >= 0);
        assertTrue(activityPath + " is missing handleInlineKey", handleInlineKey >= 0);
        assertTrue(activityPath + " is missing onBackInvoked", onBackInvoked >= 0);

        String restoreBody = activity.substring(restore, exitFullscreen);
        String focusBody = activity.substring(focusPlayerPanel, activity.indexOf("private void setDetailActionButton", focusPlayerPanel));
        String enterFullscreenBody = activity.substring(enterFullscreen, activity.indexOf("private void applyInlineShortDramaMode()", enterFullscreen));
        String fullscreenBody = activity.substring(exitFullscreen, exitPiP);
        String backFromFullscreenBody = activity.substring(backFromFullscreen, activity.indexOf("private void finishPlaybackToHome()", backFromFullscreen));
        String enterPiPBody = activity.substring(enterPiP, exitPiP);
        String pipBody = activity.substring(exitPiP, activity.indexOf("private void scheduleMobileInlineSideControlMarginUpdate()", exitPiP));
        String keyBody = activity.substring(handleInlineKey, activity.indexOf("private boolean handleInlineSeekKey", handleInlineKey));
        String backBody = activity.substring(onBackInvoked, activity.indexOf("private void saveInlineHistory()", onBackInvoked));

        assertTrue("fullscreen/PiP exits must reset the player surface back to embedded match-parent sizing",
                restoreBody.contains("setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);")
                        && restoreBody.contains("setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);")
                        && restoreBody.contains("binding.playerPanel.setTranslationZ(0f);")
                        && restoreBody.contains("setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());"));
        assertTrue("embedded player restore must force a layout pass for the PlayerView, SurfaceView, danmaku, and detail scroll",
                restoreBody.contains("binding.playerPanel.requestLayout();")
                        && restoreBody.contains("binding.exo.requestLayout();")
                        && restoreBody.contains("View surface = binding.exo.getVideoSurfaceView();")
                        && restoreBody.contains("if (surface != null) surface.requestLayout();")
                        && restoreBody.contains("binding.danmaku.requestLayout();")
                        && restoreBody.contains("binding.scroll.requestLayout();"));
        assertTrue("embedded player restore must invalidate stale fullscreen measurements on the whole detail hierarchy",
                activity.contains("private void requestEmbeddedInlinePlayerLayout(ViewGroup parent)")
                        && activity.contains("binding.playerPanel.forceLayout();")
                        && activity.contains("binding.exo.forceLayout();")
                        && activity.contains("binding.danmaku.forceLayout();")
                        && activity.contains("parent.forceLayout();")
                        && activity.contains("parent.requestLayout();")
                        && activity.contains("binding.pageContent.forceLayout();")
                        && activity.contains("binding.pageContent.requestLayout();")
                        && activity.contains("binding.scroll.forceLayout();")
                        && activity.contains("binding.scroll.requestLayout();")
                        && activity.contains("binding.root.requestLayout();"));
        assertTrue("embedded player restore must synchronously remeasure the detail content when Android keeps the stale fullscreen layout",
                activity.contains("private void layoutEmbeddedInlinePageContent(ViewGroup parent)")
                        && activity.contains("if (parent != binding.pageContent || binding.scroll.getWidth() <= 0) return;")
                        && activity.contains("View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)")
                        && activity.contains("View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)")
                        && activity.contains("binding.pageContent.measure(widthSpec, heightSpec);")
                        && activity.contains("int height = Math.max(binding.pageContent.getMeasuredHeight(), binding.scroll.getHeight());")
                        && activity.contains("binding.pageContent.layout(0, 0, width, height);"));
        assertTrue("fullscreen/PiP entry must keep a defensive copy of embedded layout params",
                activity.contains("private ViewGroup.LayoutParams copyInlinePlayerLayoutParams(ViewGroup.LayoutParams params)")
                        && activity.contains("private ViewGroup.LayoutParams embeddedInlinePlayerLayoutParams(ViewGroup parent, ViewGroup.LayoutParams fallback)")
                        && activity.contains("private void restoreEmbeddedInlinePlayerLayout()")
                        && activity.contains("private void restoreInlineDetailScrollAfterOverlay()")
                        && activity.contains("if (binding == null || !isInlinePlayerMode()) return;")
                        && activity.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(252))")
                        && activity.contains("params.setMargins(ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 22 : 14), ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 20 : 16));")
                        && activity.contains("params.height = ResUtil.dp2px(252);")
                        && activity.contains("binding.playerPanel.setLayoutParams(params);")
                        && activity.contains("binding.scroll.scrollTo(0, 0);")
                        && enterFullscreenBody.contains("playerLayoutParams = copyInlinePlayerLayoutParams(binding.playerPanel.getLayoutParams());")
                        && enterPiPBody.contains("inlinePiPLayoutParams = copyInlinePlayerLayoutParams(binding.playerPanel.getLayoutParams());"));
        assertTrue("fullscreen exit should reuse the embedded player restore path after reattaching the shared player panel",
                fullscreenBody.contains("playerParent.addView(binding.playerPanel, index, embeddedInlinePlayerLayoutParams(playerParent, playerLayoutParams));")
                        && fullscreenBody.contains("binding.playerPanel.setLayoutParams(embeddedInlinePlayerLayoutParams(playerParent, playerLayoutParams));")
                        && fullscreenBody.contains("resetInlineShortDramaMode();")
                        && fullscreenBody.contains("restoreInlinePlayerPanelAfterOverlay();")
                        && !fullscreenBody.contains("closeDetailFullscreenPlayer();")
                        && fullscreenBody.contains("scheduleInlinePlayerPanelRestoreAfterOverlay();")
                        && activity.contains("private void scheduleInlinePlayerPanelRestoreAfterOverlay()")
                        && activity.contains("binding.playerPanel.post(() -> {")
                        && activity.contains("binding.root.postDelayed(() -> {")
                        && activity.contains("}, 180);"));
        assertTrue("leanback detail-player fullscreen Back must close playback back to the detail page, while fusion keeps embedded exit",
                backFromFullscreenBody.contains("if (Util.isLeanback() && isPlayerMode())")
                        && backFromFullscreenBody.indexOf("exitInlineFullscreen();") < backFromFullscreenBody.indexOf("closeDetailFullscreenPlayer();")
                        && backFromFullscreenBody.contains("return;")
                        && !backFromFullscreenBody.contains("finishPlaybackToHome();")
                        && !backFromFullscreenBody.contains("Setting.isPlayBackToDetail()")
                        && focusBody.contains("if (!isInlinePlayerMode()) return;")
                        && !focusBody.contains("if (!isFusionMode()) return;"));
        assertTrue("PiP layout exit should reuse the same embedded player restore path",
                pipBody.contains("inlinePiPParent.addView(binding.playerPanel, index, embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));")
                        && pipBody.contains("binding.playerPanel.setLayoutParams(embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));")
                        && pipBody.contains("restoreInlinePlayerPanelAfterOverlay();"));
        assertTrue("leanback fullscreen Back should hide visible controls before exiting fullscreen",
                keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen") >= 0
                        && keyBody.indexOf("KeyUtil.isBackKey(event) && isInlineControlsVisible()") < keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen")
                        && keyBody.contains("if (KeyUtil.isActionUp(event)) backFromInlineFullscreen();")
                        && backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)") >= 0
                        && backBody.indexOf("if (isInlineControlsVisible())") < backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)")
                        && backBody.contains("backFromInlineFullscreen();"));
    }

    @Test
    public void nativeEnhancedEpisodeCardsUseUnifiedTvFocusAndPlayingState() throws Exception {
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path selectorPath = findMainResPath().resolve(Path.of("drawable", "selector_episode_card.xml"));
        String selector = new String(Files.readAllBytes(selectorPath), StandardCharsets.UTF_8);

        int method = adapter.indexOf("private void applyNativeEnhancedCardFocus");
        assertTrue(adapterPath + " is missing native enhanced card focus styling", method >= 0);
        assertTrue("native enhanced episode focus must use the same yellow stroke as TV buttons",
                adapter.contains("private static final int FOCUS_STROKE = 0xFFFFD166;")
                        && adapter.indexOf("holder.binding.getRoot().setStrokeColor(focused ? FOCUS_STROKE : activated ? activeStrokeColor : 0x00000000);", method) > method
                        && adapter.indexOf("Drawable foreground = focused", method) > method
                        && adapter.indexOf("TmdbCardFocusHelper.foregroundBorder(holder.binding.getRoot(), FOCUS_STROKE, FOCUS_STROKE_DP)", method) > method
                        && adapter.indexOf("holder.binding.getRoot().setForeground(foreground);", method) > method);
        assertTrue("currently playing episode cards must keep the green active border when not focused",
                adapter.contains("private int activeStrokeColor = 0xFF2CC56F;")
                        && adapter.indexOf("activated ? ACTIVE_STROKE_DP : 0", method) > method);
        assertTrue("focused episode cards must avoid scale focus because detail rows clip enlarged cards",
                !adapter.contains("FOCUS_SCALE")
                        && adapter.indexOf("scaleX(", method) < 0
                        && adapter.indexOf("scaleY(", method) < 0);
        assertTrue("legacy episode foreground selector must also keep focus yellow and playing green",
                selector.contains("android:color=\"#FFD166\"")
                        && selector.contains("android:color=\"#2CC56F\""));
    }

    @Test
    public void currentInlineEpisodeCardEntersFullscreenWithoutReloading() throws Exception {
        String source = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        int onPlay = source.indexOf("private void onPlay()");
        int playDetailFullscreen = source.indexOf("private void playDetailFullscreen()");
        int playInline = source.indexOf("private void playInline()");
        int stop = source.indexOf("private void stopInlinePlayerForReload()");
        int start = source.indexOf("private void startInlinePlayer(Result result)");
        String onPlayBody = source.substring(onPlay, playDetailFullscreen);
        String detailBody = source.substring(playDetailFullscreen, playInline);
        String stopBody = source.substring(stop, start);
        String startBody = source.substring(start, source.indexOf("private void searchInlineDanmaku", start));

        assertTrue("current inline episode clicks must reuse playback before fusion reloads",
                onPlayBody.indexOf("enterInlineFullscreenIfCurrentInlinePlayback(selectedEpisode)") < onPlayBody.indexOf("if (isFusionMode()) playInline();"));
        assertTrue("detail-player fullscreen entry must not reload the already playing episode",
                detailBody.contains("boolean current = isCurrentInlinePlayback(selectedEpisode);")
                        && detailBody.contains("if (!current) playInline();"));
        assertTrue("current inline playback identity must include episode, site key, and line flag",
                source.contains("private Episode inlinePlaybackEpisode;")
                        && source.contains("private String inlinePlaybackKey = \"\";")
                        && source.contains("private String inlinePlaybackFlag = \"\";")
                        && source.contains("TextUtils.equals(getKeyText(), inlinePlaybackKey)")
                        && source.contains("TextUtils.equals(selectedFlag.getFlag(), inlinePlaybackFlag)"));
        assertTrue("current inline playback identity must be cleared before a real reload",
                stopBody.contains("inlinePlaybackEpisode = null;")
                        && stopBody.contains("inlinePlaybackKey = \"\";")
                        && stopBody.contains("inlinePlaybackFlag = \"\";"));
        assertTrue("current inline playback identity must be recorded when playback starts",
                startBody.contains("inlinePlaybackEpisode = selectedEpisode;")
                        && startBody.contains("inlinePlaybackKey = getKeyText();")
                        && startBody.contains("inlinePlaybackFlag = selectedFlag == null ? \"\" : selectedFlag.getFlag();"));
    }

    @Test
    public void fusionInlinePlayerDelegatesSharedUiSetup() throws Exception {
        String activity = readJava("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String chrome = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerChrome.java");
        String controller = readJava("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java");
        int method = activity.indexOf("private void initFusionPlayer()");
        int mobile = activity.indexOf("private void setupMobileInlineControl()", method);
        String body = activity.substring(method, mobile);

        assertTrue("fusion inline player should delegate shared UI setup to VodPlayerUiController",
                body.contains("inlinePlayerUi = new VodPlayerUiController"));
        assertTrue("fusion inline player should pass a chrome object instead of wiring OSD views inline",
                body.contains("VodPlayerChrome.fromTmdbDetail(binding)"));
        assertTrue("fusion inline player should keep legacy fields backed by the shared controller during migration",
                body.contains("inlineControlController = inlinePlayerUi.controlController();")
                        && body.contains("inlinePiP = inlinePlayerUi.pip();")
                        && body.contains("inlineClock = inlinePlayerUi.clock();")
                        && body.contains("inlineOsd = inlinePlayerUi.osd();"));
        assertTrue("fusion inline player should delegate reusable TV control bindings to VodPlayerUiController",
                body.contains("inlinePlayerUi.bindInlineActions();")
                        && !body.contains("binding.playerPrev.setOnClickListener")
                        && !body.contains("binding.playerControls.setOnTouchListener(this::onInlineControlTouch);"));
        assertTrue("shared chrome must expose the reusable TV control views",
                chrome.contains("binding.playerPrev")
                        && chrome.contains("binding.playerQuality")
                        && chrome.contains("binding.playerDanmaku")
                        && chrome.contains("binding.playerFullscreenAction")
                        && chrome.contains("binding.playerControls"));
        assertTrue("shared player UI controller must bind reusable TV control actions through the host contract",
                controller.contains("public void bindInlineActions()")
                        && controller.contains("chrome.prev.setOnClickListener(view -> host.playPrevious());")
                        && controller.contains("chrome.quality.setOnClickListener(view -> host.showQuality());")
                        && controller.contains("chrome.speed.setOnLongClickListener(view -> host.resetSpeed());")
                        && controller.contains("chrome.textTrack.setOnClickListener(host::showTrack);")
                        && controller.contains("chrome.danmaku.setOnLongClickListener(view -> host.onDanmakuLongClick());")
                        && controller.contains("chrome.fullscreen.setOnClickListener(view -> host.toggleFullscreen());")
                        && controller.contains("chrome.controls.setOnTouchListener(host::onControlsTouch);"));
        assertTrue("shared player UI controller must own the reusable playback UI helpers",
                controller.contains("new VodPlayerControlController")
                        && controller.contains("new PlayerOsdController")
                        && controller.contains("Clock.create()")
                        && controller.contains("new PiP()"));
        assertTrue("shared player UI lifecycle must own OSD start/stop/release",
                controller.contains("osd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics())")
                        && controller.contains("osd.start();")
                        && controller.contains("osd.stop();")
                        && controller.contains("osd.release();"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static String readLayout(String file) throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", file));
        return new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
    }

    private static String readLeanbackLayout(String file) throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", file));
        return new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
    }

    private static String readJava(String first, String... more) throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of(first, more));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findAppModulePath() {
        if (Files.exists(Path.of("proguard-rules.pro"))) return Path.of(".");
        return Path.of("app");
    }

    private static void assertAndroidIdOrder(String label, String layout, List<String> ids) {
        int previous = -1;
        for (String id : ids) {
            int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
            assertTrue(label + " is missing @+id/" + id, index >= 0);
            assertTrue(label + " should keep @+id/" + id + " after the previous mapped control", index > previous);
            previous = index;
        }
    }

    private static void assertAndroidIdHasAttribute(String label, String layout, String id, String attribute) {
        int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue(label + " is missing @+id/" + id, index >= 0);
        assertTrue(label + " must include " + attribute, containsViewAttribute(layout, index, attribute));
    }

    private static void assertNativeControlButton(String layout, String id, String marginEnd) {
        int index = layout.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("fusion inline control @+id/" + id + " must use the native material text control tag",
                layout.lastIndexOf("<com.google.android.material.textview.MaterialTextView", index) > 0);
        assertTrue("fusion inline control @+id/" + id + " must use @style/Control",
                containsViewAttribute(layout, index, "style=\"@style/Control\""));
        assertTrue("fusion inline control @+id/" + id + " must match native margin " + marginEnd,
                containsViewAttribute(layout, index, "android:layout_marginEnd=\"" + marginEnd + "\""));
    }

    private static boolean containsViewAttribute(String layout, int idIndex, String attribute) {
        if (idIndex < 0) return false;
        int tagEnd = layout.indexOf("/>", idIndex);
        if (tagEnd < 0) tagEnd = layout.indexOf(">", idIndex);
        return tagEnd > idIndex && layout.substring(idIndex, tagEnd).contains(attribute);
    }

    /**
     * 检查源码中是否包含方法调用,忽略接收者前缀(this. / TmdbDetailLayoutUtils. / 等)
     *
     * 让文本断言只关心"做了什么"(结果),不关心"怎么调用的"(实现细节)。
     * 例如: containsMethodCallIgnoringReceiver(body, "setWidthMatch(binding.detailActions)")
     * 会匹配:
     *   - setWidthMatch(binding.detailActions)
     *   - this.setWidthMatch(binding.detailActions)
     *   - TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions)
     *
     * 方案 1 核心:让重构搬方法时,只要行为不变,测试就不误伤。
     */
    private static boolean containsMethodCallIgnoringReceiver(String source, String methodCallWithArgs) {
        // 从输入里剥掉接收者前缀,只保留 "方法名(参数...)"。
        // 例如输入 "TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions)"
        // 或 "this.setWidthMatch(binding.detailActions)" 都归一化成
        // "setWidthMatch(binding.detailActions)"。
        int openParen = methodCallWithArgs.indexOf('(');
        if (openParen < 0) return false;

        String receiverAndName = methodCallWithArgs.substring(0, openParen);
        String argsAndRest = methodCallWithArgs.substring(openParen);
        int lastDot = receiverAndName.lastIndexOf('.');
        String methodName = lastDot < 0 ? receiverAndName : receiverAndName.substring(lastDot + 1);

        // 源码侧的接收者(this. / ClassName.)只是方法名前面的前缀,
        // 子串匹配 "methodName(args)" 天然忽略它 —— 无需正则、无需截断。
        return source.contains(methodName + argsAndRest);
    }
}
