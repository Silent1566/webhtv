package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbDetailActivityLayoutTest {

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
    public void mobileFusionInlinePlayerActionLayoutExposesConfigContainer() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_control_vod_action_tmdb.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue("mobile fusion action row must expose @id/container for PlayerButtonSetting.applyOrder",
                layout.contains("android:id=\"@+id/container\""));
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

        assertTrue(sourcePath + " is missing bindBackdrop", bind >= 0);
        assertTrue(sourcePath + " is missing applyBackdropSurface", surface >= 0);
        assertTrue(sourcePath + " is missing useAppWallpaperBackdrop", wallpaper >= 0);
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
                        && panelBody.contains("adapter.setGridSpanCount(layout.spanCount())")
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
        assertTrue("native-enhanced inline episode chips should use the shared original enhanced video chip treatment",
                activity.contains("private TextView createNativeEnhancedInlineChipButton(String text)")
                        && activity.contains("new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(34))")
                        && activity.contains("button.setMinWidth(ResUtil.dp2px(64));")
                        && activity.contains("button.setBackgroundResource(R.drawable.shape_video_item);")
                        && activity.contains("ContextCompat.getColorStateList(this, R.color.selector_video_text);")
                        && activity.contains("button.setActivated(selected);"));
        assertTrue("native-enhanced inline episodes should keep mobile native columns and adaptive TV columns",
                activity.contains("private int nativeEnhancedInlineEpisodeSpanCount()")
                        && activity.contains("TmdbEpisodeGridPolicy.nativeEnhancedSpanCount(Util.isMobile(), ResUtil.isPad(), ResUtil.isLand(this), getResources().getConfiguration().screenWidthDp)")
                        && policy.contains("public static int nativeEnhancedSpanCount(boolean mobile, boolean pad, boolean landscape, int screenWidthDp)")
                        && policy.contains("if (mobile) return pad ? landscape ? 4 : 3 : landscape ? 3 : 2;")
                        && policy.contains("public static int tvAdaptiveSpanCount(int screenWidthDp)")
                        && policy.contains("if (screenWidthDp >= 1600) return 5;")
                        && policy.contains("if (screenWidthDp >= 1200) return 4;")
                        && policy.contains("return 3;")
                        && activity.contains("WindowManager.LayoutParams.MATCH_PARENT")
                        && !activity.contains("Gravity.END | Gravity.CENTER_VERTICAL")
                        && !activity.contains("0.50f"));
        assertTrue("detail-page episode cards should use the same native-enhanced adaptive card mechanism",
                activity.contains("episodeAdapter.setNativeEnhanced(true);")
                        && activity.contains("private int episodeSpanCount()")
                        && activity.contains("return nativeEnhancedInlineEpisodeSpanCount();")
                        && activity.contains("private boolean shouldForceAdaptiveEpisodeGrid()")
                        && activity.contains("return !Util.isMobile();")
                        && activity.contains("if (shouldForceAdaptiveEpisodeGrid()) episodeGridMode = true;")
                        && activity.contains("if (shouldForceAdaptiveEpisodeGrid()) return;")
                        && activity.contains("private int nativeEnhancedEpisodeCardHeightDp()")
                        && activity.contains("TmdbEpisodeGridPolicy.nativeGridCardHeightDp(getResources().getConfiguration().smallestScreenWidthDp < 600)")
                        && activity.contains("TmdbEpisodeGridPolicy.NATIVE_GRID_CARD_BOTTOM_MARGIN_DP"));
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
        assertTrue("detail-page episode cards should align the focused grid row inside the outer scroll view",
                activity.contains("episodeAdapter.setOnFocusChangeListener(this::onDetailEpisodeFocusChange);")
                        && activity.contains("episodeAdapter.setOnKeyListener(this::onDetailEpisodeKey);")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));")
                        && activity.contains("button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(keyCode, event));")
                        && activity.contains("private boolean onDetailEpisodeKey(View view, int keyCode, KeyEvent event)")
                        && activity.contains("return focusDetailEpisode(position - span);")
                        && activity.contains("int target = position + span;")
                        && activity.contains("private boolean focusDetailEpisodeRangeButton()")
                        && activity.contains("private boolean focusDetailEpisode(int position)")
                        && activity.contains("private void alignDetailEpisodeFocusedRow(View focusedView, int position)")
                        && activity.contains("binding.episodeContainer.findViewHolderForAdapterPosition(rowStart)")
                        && activity.contains("binding.scroll.scrollTo(0, Math.max(0, targetY));")
                        && adapter.contains("private View.OnFocusChangeListener focusChangeListener;")
                        && adapter.contains("public void setOnFocusChangeListener(View.OnFocusChangeListener focusChangeListener)")
                        && adapter.contains("holder.binding.getRoot().setOnFocusChangeListener(focusChangeListener);")
                        && adapter.contains("if (focusChangeListener != null) focusChangeListener.onFocusChange(holder.binding.getRoot(), focused);"));
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
                        && activity.contains("episode.setTmdbEpisode(tmdbEpisodes.get(position.number()));"));
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
                        && service.contains("refresh ? null : readCache(file, ttl)"));
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
        assertTrue("native enhanced/detail-player fullscreen Back must use the same embedded-player exit path as fusion",
                backFromFullscreenBody.contains("exitInlineFullscreen();")
                        && !backFromFullscreenBody.contains("finishPlaybackToHome();")
                        && !backFromFullscreenBody.contains("Setting.isPlayBackToDetail()")
                        && focusBody.contains("if (!isInlinePlayerMode()) return;")
                        && !focusBody.contains("if (!isFusionMode()) return;"));
        assertTrue("PiP layout exit should reuse the same embedded player restore path",
                pipBody.contains("inlinePiPParent.addView(binding.playerPanel, index, embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));")
                        && pipBody.contains("binding.playerPanel.setLayoutParams(embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));")
                        && pipBody.contains("restoreInlinePlayerPanelAfterOverlay();"));
        assertTrue("leanback fullscreen Back should exit fullscreen before the generic hide-controls branch can consume it",
                keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen") >= 0
                        && keyBody.indexOf("KeyUtil.isBackKey(event) && isInlineControlsVisible()") > keyBody.indexOf("KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen")
                        && keyBody.contains("if (KeyUtil.isActionUp(event)) backFromInlineFullscreen();")
                        && backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)") >= 0
                        && backBody.indexOf("if (isInlineControlsVisible())") > backBody.indexOf("if (Util.isLeanback() && inlineFullscreen)")
                        && backBody.contains("backFromInlineFullscreen();"));
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
}
