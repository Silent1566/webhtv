package com.fongmi.android.tv.ui.activity;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoActivityLayoutTest {

    private static final List<String> REQUIRED_EPISODE_IDS = Arrays.asList(
            "episodeTitleBar",
            "episodeViewMode"
    );
    private static final List<String> REQUIRED_TMDB_MOVABLE_IDS = Arrays.asList(
            "flagTitleBar",
            "flag",
            "quality_text",
            "quality",
            "episodeTitleBar",
            "episode"
    );
    private static final List<String> REQUIRED_FULLSCREEN_CONTROL_IDS = Arrays.asList(
            "cast",
            "keep",
            "display",
            "info"
    );

    @Test
    public void mobileActivityVideoLayoutsExposeEpisodeModeControls() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Set<String> ids = collectAndroidIds(layoutFile.toFile());
            for (String requiredId : REQUIRED_EPISODE_IDS) {
                assertTrue(layoutFile + " is missing @+id/" + requiredId, ids.contains(requiredId));
            }
        }
    }

    @Test
    public void mobileActivityVideoLayoutsExposeTmdbMovableContainers() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Set<String> ids = collectAndroidIds(layoutFile.toFile());
            for (String requiredId : REQUIRED_TMDB_MOVABLE_IDS) {
                assertTrue(layoutFile + " is missing @+id/" + requiredId, ids.contains(requiredId));
            }
        }
    }

    @Test
    public void mobileActivityVideoLayoutsHaveFusionChromeHost() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Element video = findAndroidId(layoutFile.toFile(), "video");
            assertTrue(layoutFile + " is missing @+id/video", video != null);
            Node parent = video.getParentNode();
            String parentName = parent == null ? "" : parent.getNodeName();
            assertTrue(layoutFile + " must keep @+id/video inside a RelativeLayout-compatible host",
                    "RelativeLayout".equals(parentName) || "com.fongmi.android.tv.ui.custom.ProgressLayout".equals(parentName));
        }
    }

    @Test
    public void mobileVodControlLayoutExposesFullscreenTopActions() throws Exception {
        Path controlLayout = findMobileResPath().resolve(Path.of("layout", "view_control_vod.xml"));
        Set<String> ids = collectAndroidIds(controlLayout.toFile());
        for (String requiredId : REQUIRED_FULLSCREEN_CONTROL_IDS) {
            assertTrue(controlLayout + " is missing @+id/" + requiredId, ids.contains(requiredId));
        }
    }

    @Test
    public void mobileVideoTmdbMovableViewsKeepQualityBetweenFlagsAndEpisodes() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private View[] getTmdbMovableViews()");
        int flag = source.indexOf("mBinding.flag,", method);
        int qualityText = source.indexOf("mBinding.qualityText,", method);
        int quality = source.indexOf("mBinding.quality,", method);
        int episodeTitle = source.indexOf("mBinding.episodeTitleBar,", method);
        int episode = source.indexOf("mBinding.episode,", method);

        assertTrue(sourcePath + " is missing getTmdbMovableViews", method >= 0);
        assertTrue("TMDB movable views must include flag", flag > method);
        assertTrue("TMDB movable views must include quality title", qualityText > method);
        assertTrue("TMDB movable views must include quality list", quality > method);
        assertTrue("TMDB movable views must include episode title", episodeTitle > method);
        assertTrue("TMDB movable views must include episode list", episode > method);
        assertTrue("quality title must move after flag list", flag < qualityText);
        assertTrue("quality list must move after quality title", qualityText < quality);
        assertTrue("episode title must move after quality list", quality < episodeTitle);
        assertTrue("episode list must move after episode title", episodeTitle < episode);
    }

    @Test
    public void mobileOriginalEnhancedHidesShortDisplayButKeepsDetailActionRow() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setNativeDetailInfoVisible(boolean visible)");
        int nextMethod = source.indexOf("private void setText(TextView view", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int visibilityMethod = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");

        assertTrue(sourcePath + " is missing setNativeDetailInfoVisible", method >= 0);
        assertTrue("native enhanced mode must not hide the whole detail action row",
                !methodBody.contains("mBinding.actionRow.setVisibility(visibility)"));
        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", visibilityMethod >= 0);
        assertTrue("native enhanced mode must hide the short display button",
                source.indexOf("mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE)", visibilityMethod) > visibilityMethod);
    }

    @Test
    public void leanbackOriginalEnhancedHidesShortDisplayAndSourceActions() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");

        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("native enhanced mode must hide the short display button",
                source.indexOf("mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
        assertTrue("native enhanced mode must hide the source change button",
                source.indexOf("mBinding.change1.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
    }

    @Test
    public void tmdbHeaderHidesChangeSourceInOriginalEnhancedMode() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateOriginalEnhancedActionVisibility()");

        assertTrue(sourcePath + " is missing updateOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("TMDB header must hide source change in original enhanced mode",
                source.indexOf("changeSource.setVisibility(Setting.isOriginalEnhancedDetailPage() ? View.GONE : View.VISIBLE)", method) > method);
    }

    @Test
    public void leanbackNativeActionButtonsShareMinimumWidth() throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        for (String id : Arrays.asList("content", "shortDisplay", "search", "keep", "change1", "tmdbRematch")) {
            Element action = findAndroidId(layoutPath.toFile(), id);
            assertTrue(layoutPath + " is missing @+id/" + id, action != null);
            assertTrue(id + " must use the shared native action width",
                    "96dp".equals(action.getAttribute("android:minWidth")));
        }
    }

    @Test
    public void tmdbHeaderActionButtonsShareMinimumWidth() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        for (String id : Arrays.asList("tmdbChangeSource", "tmdbKeep", "tmdbRematch")) {
            Element action = findAndroidId(layoutPath.toFile(), id);
            assertTrue(layoutPath + " is missing @+id/" + id, action != null);
            assertTrue(id + " must use the shared TMDB header action width",
                    "72dp".equals(action.getAttribute("android:minWidth")));
        }
    }

    @Test
    public void mobileVideoDirectTmdbCarriesDetailThemeIntoPlayback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("direct TMDB playback must persist the selected detail theme in its intent",
                source.contains("EXTRA_TMDB_DETAIL_THEME") && source.contains("intent.putExtra(EXTRA_TMDB_DETAIL_THEME, Setting.getTmdbDetailTheme())"));
        assertTrue("Fusion playback theme resolution must follow the current detail theme preference",
                source.contains("return Setting.getTmdbDetailTheme() == 1 ? 1 : 2;"));
        assertTrue("TMDB header view must receive the playback theme override before it draws the source panel",
                source.contains("mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode())"));
    }

    @Test
    public void mobileVideoFusionThemeToggleActuallyChangesThemeMode() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("fusion theme button must switch light to dark and dark to light",
                source.contains("int theme = isFusionLightTheme() ? 2 : 1;"));
    }

    @Test
    public void mobileVideoFusionPlaybackControlsRefreshAfterBeingMoved() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void moveFlagAndEpisodeToTmdb()");
        int updateVisibility = source.indexOf("updateEpisodeGroupVisibility();", method);
        int refreshHeader = source.indexOf("mTmdbHeaderView.refreshTheme();", updateVisibility);
        int refreshSurface = source.indexOf("applyFusionThemeSurface();", refreshHeader);

        assertTrue(sourcePath + " is missing moveFlagAndEpisodeToTmdb", method >= 0);
        assertTrue("fusion playback controls must update visibility before final theme sync", updateVisibility > method);
        assertTrue("fusion playback controls must refresh header theme after all source and episode views are moved", refreshHeader > updateVisibility);
        assertTrue("fusion playback surface must sync after header playback controls are re-themed", refreshSurface > refreshHeader);
    }

    @Test
    public void mobileVideoFusionPlaybackControlsRetintAfterHeaderBind() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("mTmdbHeaderView.bind(mTmdbUIAdapter);");
        int sourceStyle = source.indexOf("styleTmdbSourceInFlagTitle();", bind);
        int controlStyle = source.indexOf("applyTmdbPlaybackControlColors();", sourceStyle);
        int method = source.indexOf("private void applyTmdbPlaybackControlColors()");
        int nextMethod = source.indexOf("private boolean isTmdbPlaybackLightTheme()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int viewModeButton = source.indexOf("private void updateEpisodeViewModeButton()");
        int viewModeIcon = source.indexOf("mBinding.episodeViewMode.setImageResource", viewModeButton);
        int viewModeRetint = source.indexOf("applyTmdbPlaybackControlColors();", viewModeIcon);

        assertTrue(sourcePath + " is missing header bind", bind >= 0);
        assertTrue("fusion playback controls must retint after source text is restyled following header bind", controlStyle > sourceStyle);
        assertTrue(sourcePath + " is missing applyTmdbPlaybackControlColors", method >= 0);
        assertTrue("fusion playback control retint must cover the moved line title", source.indexOf("mBinding.flagTitleBar", method) > method);
        assertTrue("fusion playback control retint must cover the moved episode title", source.indexOf("mBinding.episodeTitleBar", method) > method);
        assertTrue("fusion playback control retint must cover reverse icon", source.indexOf("mBinding.reverse", method) > method);
        assertTrue("fusion playback control retint must cover grid/list icon", source.indexOf("mBinding.episodeViewMode", method) > method);
        assertTrue("tmdb playback control retint must not depend solely on the global fusion setting",
                source.indexOf("if (!Setting.isFusionDetailPage()) return;", method) < 0);
        assertTrue("TMDB playback controls must not force non-fusion cinema pages into the light palette",
                source.indexOf("if (!Setting.isFusionDetailPage()) return true;", method) < 0);
        assertTrue("TMDB playback labels and icons must use the header's current detail theme",
                source.indexOf("mTmdbHeaderView.isCurrentDetailLightTheme()", method) > method);
        assertTrue("TMDB playback labels and icons must match the header section title color",
                source.indexOf("mTmdbHeaderView.getFusionSectionTitleColor()", method) > method);
        assertTrue("TMDB flag chips must use the same resolved playback theme as the moved labels",
                methodBody.contains("mFlagAdapter.setTmdbLight(light)"));
        assertTrue("fusion playback icon retint must use a color filter", source.indexOf("setColorFilter(color)", method) > method);
        assertTrue("light fusion playback labels must clear inherited video shadows", source.indexOf("setShadowLayer(0, 0, 0, 0)", method) > method);
        assertTrue("episode view mode icon must be retinted after changing its drawable", viewModeRetint > viewModeIcon);
    }

    @Test
    public void mobileVideoEpisodeViewportUsesStableCapInsideScrollPage() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateEpisodeViewportHeight()");
        int nextMethod = source.indexOf("private boolean isTmdbEpisodeCardMode()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);

        assertTrue(sourcePath + " is missing updateEpisodeViewportHeight", method >= 0);
        assertTrue("episode viewport must keep a stable dp cap for scrollable detail pages",
                methodBody.contains("int height = limit;"));
        assertTrue("episode viewport must not collapse based on current remaining screen height",
                !methodBody.contains("available ="));
        assertTrue("episode viewport must not depend on root height after the method starts",
                !methodBody.contains("mBinding.getRoot().getHeight()"));
    }

    @Test
    public void leanbackVideoContextWallIsCoveredByBackdropMask() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element contextWall = findAndroidId(layoutFile.toFile(), "contextWall");
        Element backdropMask = findAndroidId(layoutFile.toFile(), "backdropMask");

        assertTrue(layoutFile + " is missing @+id/contextWall", contextWall != null);
        assertTrue(layoutFile + " is missing @+id/backdropMask", backdropMask != null);
        assertTrue("context wall and backdrop mask must share a parent type so z-order protects playback text",
                contextWall.getParentNode().getNodeName().equals(backdropMask.getParentNode().getNodeName()));
        assertTrue("context wall must draw below backdrop mask", isAndroidIdBefore(layoutFile, "contextWall", "backdropMask"));
    }

    @Test
    public void mobileVideoContextWallHasFullScreenScrimAboveIt() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Element contextWall = findAndroidId(layoutFile.toFile(), "contextWall");
            Element scrim = findAndroidId(layoutFile.toFile(), "videoContextScrim");

            assertTrue(layoutFile + " is missing @+id/contextWall", contextWall != null);
            assertTrue(layoutFile + " is missing @+id/videoContextScrim", scrim != null);
            assertTrue("context wall and scrim must share a parent type so z-order protects playback text",
                    contextWall.getParentNode().getNodeName().equals(scrim.getParentNode().getNodeName()));
            assertTrue(layoutFile + " must draw the scrim above the context wall", isAndroidIdBefore(layoutFile, "contextWall", "videoContextScrim"));
            assertTrue(layoutFile + " scrim must cover the full playback detail surface",
                    "match_parent".equals(scrim.getAttribute("android:layout_width"))
                            && "match_parent".equals(scrim.getAttribute("android:layout_height")));
        }
    }

    @Test
    public void leanbackTmdbRecommendationPresenterReportsAlreadyFocusedAiCards() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "TmdbRecommendationPresenter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("AI recommendation reason must update when a rebound card is already focused",
                source.contains("holder.view.hasFocus() && mFocusListener != null") && source.contains("mFocusListener.onItemFocus(tmdbItem, true)"));
        assertTrue("AI recommendation reason must clear when a focused card is unbound",
                source.contains("viewHolder.view.hasFocus() && holder.item != null && mFocusListener != null")
                        && source.contains("mFocusListener.onItemFocus(holder.item, false)"));
    }

    @Test
    public void tmdbHeaderRefreshesThemeEvenWhenModeValueDidNotChange() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void setDetailThemeMode(int detailThemeMode)");
        int assign = source.indexOf("detailThemeModeOverride = normalized;", method);
        int apply = source.indexOf("applyTheme();", method);
        int earlyReturn = source.indexOf("return;", method);

        assertTrue(sourcePath + " is missing setDetailThemeMode", method >= 0);
        assertTrue("TMDB header must remember the normalized detail theme mode", assign > method);
        assertTrue("TMDB header must apply theme after receiving the detail theme mode", apply > assign);
        assertTrue("TMDB header must not skip theme refresh just because the numeric mode is unchanged",
                earlyReturn < 0 || earlyReturn > apply);
    }

    @Test
    public void mobileFusionBackdropFillsBehindTopChrome() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyFusionBackdropLayout()");

        assertTrue(sourcePath + " is missing applyFusionBackdropLayout", method >= 0);
        assertTrue("Fusion backdrop must clear below-video anchoring and align to the top of the root",
                source.indexOf("wallParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)", method) > method);
        assertTrue("Fusion backdrop must keep covering the bottom of the root",
                source.indexOf("wallParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)", method) > method);
        assertTrue("Fusion status bar spacer must be transparent so the backdrop reaches the status icons",
                source.indexOf("mBinding.statusBar.setBackgroundColor(Color.TRANSPARENT)", method) > method);
        assertTrue("Fusion context wall scrim must follow the same full-screen layout as the backdrop",
                source.indexOf("mBinding.videoContextScrim.setLayoutParams(", method) > method);
        assertTrue("Fusion context wall scrim must remain visible over the full-screen artwork",
                source.indexOf("mBinding.videoContextScrim.setVisibility(View.VISIBLE)", method) > method);
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
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

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Set<String> collectAndroidIds(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Set<String> ids = new HashSet<>();
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("android:id");
            int slash = id.indexOf('/');
            if (slash >= 0 && slash + 1 < id.length()) ids.add(id.substring(slash + 1));
        }
        return ids;
    }

    private static Element findAndroidId(File file, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("android:id");
            if (id.endsWith("/" + value)) return element;
        }
        return null;
    }

    private static boolean isAndroidIdBefore(Path file, String firstId, String secondId) throws Exception {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int first = source.indexOf("android:id=\"@+id/" + firstId + "\"");
        int second = source.indexOf("android:id=\"@+id/" + secondId + "\"");
        return first >= 0 && second >= 0 && first < second;
    }
}
