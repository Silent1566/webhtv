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
    public void mobileFusionDetailDocksInlinePlayerActionsBelowPlayer() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "activity_tmdb_detail.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int player = layout.indexOf("android:id=\"@+id/playerPanel\"");
        int dock = layout.indexOf("android:id=\"@+id/mobileFusionPlayerActionDock\"");
        int fusionActions = layout.indexOf("android:id=\"@+id/fusionActions\"");

        assertTrue("mobile fusion detail must expose a dock for the shared player action row", dock >= 0);
        assertTrue("mobile fusion player action dock must sit between player and detail actions", player < dock && dock < fusionActions);

        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int update = source.indexOf("private void updateMobileInlineButtons(boolean playing");
        int dockMethod = source.indexOf("private boolean updateMobileFusionPlayerActionDock(boolean show)");
        int restoreMethod = source.indexOf("private void restoreMobileInlinePlayerAction()");

        assertTrue(sourcePath + " is missing updateMobileFusionPlayerActionDock", dockMethod >= 0);
        assertTrue(sourcePath + " is missing restoreMobileInlinePlayerAction", restoreMethod >= 0);
        assertTrue("mobile inline buttons must dock the action row before falling back to fullscreen visibility",
                source.indexOf("boolean docked = updateMobileFusionPlayerActionDock(hasPlayer && !locked);", update) > update);
        assertTrue("non-fullscreen fusion detail must move the shared action row into the visible dock",
                source.indexOf("binding.mobileFusionPlayerActionDock.addView(detailActionRoot", dockMethod) > dockMethod);
        assertTrue("fullscreen and non-fusion modes must restore the action row to the control overlay",
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
    public void inlineEpisodeDialogKeepsTallViewportAndReadableLightThemeStates() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path adapterPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "InlineEpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);

        int method = activity.indexOf("private void showInlineEpisodes()");
        int nextMethod = activity.indexOf("private void showTvInlineEpisodes()", method);
        String body = activity.substring(method, nextMethod);

        assertTrue(activityPath + " is missing showInlineEpisodes", method >= 0);
        assertTrue("mobile inline episode dialog should reserve a taller viewport so paged grids do not collapse",
                body.contains("ResUtil.dp2px(620)") && body.contains("0.78f"));
        assertTrue("mobile inline episode dialog should tint its container from the resolved detail theme",
                body.contains("ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();")
                        && body.contains("content.setBackground(background);")
                        && body.contains("title.setTextColor(colors.primary);")
                        && body.contains("adapter.setLight(lightTheme);"));
        assertTrue("inline episode page chips should stay readable in light theme",
                activity.contains("button.setTextColor(lightTheme ? colors.secondary : 0xFFC6D0D9);")
                        && activity.contains("background.setColor(lightTheme ? 0x1F20B866 : 0x332196F3);")
                        && activity.contains("button.setTextColor(lightTheme ? colors.accent : 0xFF85C7FF);"));
        assertTrue("inline episode adapter should expose a light-theme palette for playable items",
                adapter.contains("public void setLight(boolean light)")
                        && adapter.contains("int normalText = light ? 0xFF1E2A36 : COLOR_TEXT;")
                        && adapter.contains("int normalBg = light ? 0xFFF1F5F9 : COLOR_NORMAL;"));
    }

    @Test
    public void inlineEpisodeModeToggleClicksImmediatelyOnMobileWhileTvKeepsFocusNavigation() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        int mobileMethod = activity.indexOf("private MaterialButton createInlineEpisodeModeButton()");
        int mobileMethodEnd = activity.indexOf("private void updateInlineEpisodeModeButton(MaterialButton button)", mobileMethod);
        int tvMethod = activity.indexOf("private void showTvInlineEpisodes()");
        int tvMethodEnd = activity.indexOf("private boolean moveEpisodeDialogPageFocus(", tvMethod);

        assertTrue(activityPath + " is missing createInlineEpisodeModeButton", mobileMethod >= 0 && mobileMethodEnd > mobileMethod);
        assertTrue(activityPath + " is missing showTvInlineEpisodes", tvMethod >= 0 && tvMethodEnd > tvMethod);

        String mobileBody = activity.substring(mobileMethod, mobileMethodEnd);
        String tvBody = activity.substring(tvMethod, tvMethodEnd);

        assertTrue("mobile inline episode mode toggle should switch on the first tap instead of becoming touch-focusable",
                !mobileBody.contains("button.setFocusableInTouchMode(true);"));
        assertTrue("TV inline episode mode toggle should stay touch-focusable for remote-driven focus navigation",
                tvBody.contains("mode.setFocusableInTouchMode(true);"));
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
