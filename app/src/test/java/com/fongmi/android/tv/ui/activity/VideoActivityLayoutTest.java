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
    public void mobileVideoRefreshesDanmakuControlsAfterLateDanmakuLoad() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void refreshDanmakuControls()");
        int action = source.indexOf("mBinding.control.action.danmaku.setVisibility", method);
        int quick = source.indexOf("mBinding.control.danmaku.setVisibility", method);
        int apiSearch = source.indexOf("DanmakuApi.search");
        int apiRefresh = source.indexOf("refreshDanmakuControls();", apiSearch);
        int event = source.indexOf("RefreshEvent.Type.DANMAKU");
        int eventRefresh = source.indexOf("refreshDanmakuControls();", event);

        assertTrue(sourcePath + " is missing refreshDanmakuControls", method >= 0);
        assertTrue("late danmaku refresh must update the fullscreen action button", action > method);
        assertTrue("late danmaku refresh must update the quick toggle button", quick > method);
        assertTrue("auto danmaku search must refresh controls after loading", apiRefresh > apiSearch);
        assertTrue("manual danmaku refresh event must refresh controls after loading", eventRefresh > event);
    }

    @Test
    public void mobileVideoKeepsParseRowHiddenInEmbeddedPlayerWhenPlaybackStarts() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setPlayer(Result result)");
        int setUseParse = source.indexOf("setUseParse(result.shouldUseParse());", method);
        int guardedParseRow = source.indexOf("mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);", setUseParse);
        int startPlayer = source.indexOf("startPlayer(getHistoryKey(), result, isUseParse()", setUseParse);

        assertTrue(sourcePath + " is missing setPlayer", method >= 0);
        assertTrue("parse row must only become visible in fullscreen during playback start", guardedParseRow > setUseParse && guardedParseRow < startPlayer);
    }

    @Test
    public void mobileShortDramaKeepsStandardSettingButtonVisible() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int showControl = source.indexOf("private void showControl()");
        int shortDrama = source.indexOf("boolean shortDrama = isShortDramaSource();", showControl);
        int setting = source.indexOf("mBinding.control.setting.setVisibility(mHistory == null || (isFullscreen() && !shortDrama) ? View.GONE : View.VISIBLE);", shortDrama);
        int shortDramaViews = source.indexOf("private View[] getShortDramaControlViews()");
        int dockedSetting = source.indexOf("mBinding.control.setting,", shortDramaViews);

        assertTrue(sourcePath + " is missing showControl", showControl >= 0);
        assertTrue("short drama mode must keep the standard setting button visible while fullscreen", setting > shortDrama);
        assertTrue("short drama floating controls must include the standard setting button", dockedSetting > shortDramaViews);
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
    public void mobileOriginalEnhancedHidesOriginalDetailActionRow() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setNativeDetailInfoVisible(boolean visible)");
        int nextMethod = source.indexOf("private void setText(TextView view", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int visibilityMethod = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");

        assertTrue(sourcePath + " is missing setNativeDetailInfoVisible", method >= 0);
        assertTrue("native detail info visibility must not control the detail action row",
                !methodBody.contains("mBinding.actionRow.setVisibility(visibility)"));
        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", visibilityMethod >= 0);
        assertTrue("native enhanced mode must hide the original detail action row",
                source.indexOf("mBinding.actionRow.setVisibility(hide ? View.GONE : View.VISIBLE)", visibilityMethod) > visibilityMethod);
    }

    @Test
    public void mobileDirectPlaybackUsesUpstreamNativeEpisodeStrip() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()");
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items)");
        int layout = source.indexOf("private void updateEpisodeLayoutForUpstreamNative()");
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items)");

        assertTrue(sourcePath + " is missing direct native episode predicate", predicate >= 0);
        assertTrue("direct native episode mode must be scoped to the 影视原生 setting",
                source.indexOf("return Setting.isDirectDetailPage() && !isTmdbMode();", predicate) > predicate);
        assertTrue("direct native playback must bypass enhanced episode grid binding",
                setEpisode >= 0 && source.indexOf("if (shouldUseUpstreamNativeEpisodeModule())", setEpisode) > setEpisode);
        assertTrue("direct native playback should restore the upstream horizontal episode strip",
                bind >= 0
                        && source.indexOf("mEpisodeAdapter.setViewType(ViewType.HORI);", bind) > bind
                        && source.indexOf("mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);", bind) > bind
                        && layout > bind
                        && source.indexOf("new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)", layout) > layout);
    }

    @Test
    public void leanbackDirectPlaybackUsesUpstreamNativeEpisodeModule() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()");
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items, boolean scrollToCurrent)");
        int viewport = source.indexOf("private void updateUpstreamNativeEpisodeGridViewport()");

        assertTrue(sourcePath + " is missing leanback direct native episode predicate", predicate >= 0);
        assertTrue("leanback direct native episode mode must be scoped to the 影视原生 setting",
                source.indexOf("return Setting.isDirectDetailPage() && !isTmdbMode();", predicate) > predicate);
        assertTrue("leanback direct native playback must bypass enhanced episode chrome",
                setEpisode >= 0 && source.indexOf("if (shouldUseUpstreamNativeEpisodeModule())", setEpisode) > setEpisode);
        assertTrue("leanback direct native playback should keep upstream grouping and vertical episode grid",
                bind >= 0
                        && source.indexOf("mBinding.episodeHeader.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("episodeGridMode = true;", bind) > bind
                        && source.indexOf("mBinding.episode.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("mBinding.episodeGrid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);", bind) > bind
                        && source.indexOf("mEpisodeGridAdapter.setVerticalGridMode(true);", bind) > bind
                        && source.indexOf("setArrayAdapter(items.size());", bind) > bind);
        assertTrue("leanback direct native grouping must scroll the episode grid internally so the focused group stays visible",
                viewport >= 0
                        && source.indexOf("params.height = height;", viewport) > viewport
                        && source.indexOf("getUpstreamNativeEpisodeGridHeight(spacing)", viewport) > viewport
                        && source.indexOf("ResUtil.dp2px(64) * rows", viewport) > viewport
                        && source.indexOf("new SpaceItemDecoration(spanCount, 12)", viewport) > viewport
                        && source.indexOf("mBinding.episodeGrid.setNestedScrollingEnabled(true);", viewport) > viewport
                        && source.indexOf("updateUpstreamNativeEpisodeGridViewport();", bind) > bind
                        && source.indexOf("mBinding.episodeGrid.post(this::updateUpstreamNativeEpisodeGridViewport);", bind) > bind);
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
    public void leanbackDetailActionRowScrollsHorizontally() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element row = findAndroidId(layoutFile.toFile(), "row2");

        assertTrue(layoutFile + " is missing @+id/row2", row != null);
        assertTrue("leanback detail action row must scroll instead of clipping overflow",
                "HorizontalScrollView".equals(row.getNodeName()));
        assertTrue("leanback detail action row must fill the remaining right side",
                "match_parent".equals(row.getAttribute("android:layout_width"))
                        && "true".equals(row.getAttribute("android:layout_alignParentEnd")));
        assertTrue("leanback detail action row should hide scrollbars",
                "none".equals(row.getAttribute("android:scrollbars")));
    }

    @Test
    public void leanbackTmdbEpisodeDialogUsesFullscreenAdaptiveCards() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int init = source.indexOf("protected void initView()");
        int width = source.indexOf("private int getPanelWidth()");
        int column = source.indexOf("private int getTmdbCardColumn()");
        int start = source.indexOf("public void onStart()");

        assertTrue(sourcePath + " is missing tmdb episode dialog hooks", init >= 0 && width > init && column > width && start > column);
        assertTrue("Episode dialog must use the full screen for both card and text modes",
                source.indexOf("return ResUtil.getScreenWidth(requireContext());", width) > width
                        && source.indexOf("int width = WindowManager.LayoutParams.MATCH_PARENT;", start) > start
                        && source.indexOf("int gravity = Gravity.CENTER;", start) > start);
        assertTrue("TMDB episode dialog must use the same adaptive TV card columns as TMDB detail",
                source.indexOf("return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);", column) > column);
        assertTrue("TMDB episode dialog should use fullscreen optimized padding and background",
                source.indexOf("binding.getRoot().setBackgroundColor(0x80111820);", init) > init
                        && source.indexOf("binding.getRoot().setPadding(ResUtil.dp2px(24), ResUtil.dp2px(20), ResUtil.dp2px(24), ResUtil.dp2px(16));", init) > init);

        Path activityPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int span = activity.indexOf("private int getEpisodeGridSpanCount()");
        int setEpisode = activity.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int toggle = activity.indexOf("private void toggleEpisodeViewMode()");
        assertTrue("native enhanced playback page episode grid must use the shared adaptive TV card columns",
                span >= 0
                        && activity.indexOf("return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);", span) > span
                        && setEpisode >= 0
                        && activity.indexOf("if (showTmdbEpisodeChrome && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();", setEpisode) > setEpisode
                        && activity.indexOf("mBinding.episodeViewMode.setVisibility(showTmdbEpisodeChrome && hasMultiple && useTmdbCards ? View.VISIBLE : View.GONE);", setEpisode) > setEpisode
                        && toggle >= 0
                        && activity.indexOf("if (mBinding.episodeViewMode.getVisibility() != View.VISIBLE) return;", toggle) > toggle
                        && activity.indexOf("Setting.putTmdbEpisodeGridMode(episodeGridMode);", toggle) > toggle);
    }

    @Test
    public void leanbackPlaybackEpisodeRangeButtonsApplyOnFocusAndHandleClick() throws Exception {
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "ArrayAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path activityPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);

        int bind = adapter.indexOf("public void onBindViewHolder");
        int listener = adapter.indexOf("public interface OnClickListener");
        assertTrue("leanback episode range buttons must forward click events",
                bind >= 0
                        && adapter.indexOf("mListener.onSegmentClick(position)", bind) > bind
                        && adapter.indexOf("mListener.onSegmentFocus(position)", bind) > bind
                        && listener >= 0
                        && adapter.indexOf("void onSegmentClick(int position);", listener) > listener
                        && adapter.indexOf("void onSegmentFocus(int position);", listener) > listener);

        int recycler = activity.indexOf("mBinding.array.addOnChildViewHolderSelectedListener");
        assertTrue("playback page range focus must apply the segment without moving focus into episodes",
                recycler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", recycler) > recycler);

        int selector = activity.indexOf("private void selectEpisodeSegment(int position, boolean requestEpisodeFocus)");
        assertTrue("playback page must share segment focus and click behavior",
                selector >= 0
                        && activity.indexOf("if (position <= 1) return;", selector) > selector
                        && activity.indexOf("mBinding.array.setSelectedPosition(position);", selector) > selector
                        && activity.indexOf("showEpisodeSegment(position);", selector) > selector);

        int showSegment = activity.indexOf("private void showEpisodeSegment(int position)");
        assertTrue("playback page range focus must replace the visible episode items",
                showSegment >= 0
                        && activity.indexOf("List<Episode> episodes = getFlag().getEpisodes();", showSegment) > showSegment
                        && activity.indexOf("List<Episode> items = episodes.subList(start, end);", showSegment) > showSegment
                        && activity.indexOf("mEpisodeAdapter.addAll(items);", showSegment) > showSegment
                        && activity.indexOf("mEpisodeGridAdapter.addAll(items);", showSegment) > showSegment);

        int selectedPosition = activity.indexOf("private int getSelectedEpisodePosition(List<Episode> episodes)");
        int adjacent = activity.indexOf("private Episode getAdjacentEpisode(int offset)");
        assertTrue("playback next/previous must follow the selected episode after reverse sorting",
                selectedPosition >= 0
                        && activity.indexOf("episodes.get(i).isSelected()", selectedPosition) > selectedPosition
                        && adjacent >= 0
                        && activity.indexOf("int position = getSelectedEpisodePosition(episodes);", adjacent) > adjacent
                        && activity.indexOf("flag.getPosition()", adjacent) == -1);

        int handler = activity.indexOf("public void onSegmentClick(int position)");
        assertTrue("playback page must not jump focus away from the clicked episode range",
                handler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", handler) > handler);

        int focusHandler = activity.indexOf("public void onSegmentFocus(int position)");
        assertTrue("playback page must apply the focused episode range without jumping focus",
                focusHandler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", focusHandler) > focusHandler);

        int dialogHandler = dialog.indexOf("public void onSegmentClick(int position)");
        assertTrue("episode dialog must keep satisfying the ArrayAdapter click contract",
                dialogHandler >= 0
                        && dialog.indexOf("selectSegment(position, true);", dialogHandler) > dialogHandler
                        && dialog.indexOf("public void onSegmentFocus(int position)") > dialogHandler);
    }

    @Test
    public void leanbackPlaybackEpisodeDialogUsesSourceDisplayMode() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onEpisodes()");
        int nextMethod = source.indexOf("private void onRepeat()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);

        assertTrue(sourcePath + " is missing onEpisodes", method >= 0);
        assertTrue("playback episode selector must keep TMDB/native-enhanced card mode when the source policy requires it",
                methodBody.contains("EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), flag.getEpisodes())")
                        && methodBody.contains(".tmdbCard(tmdbCard)"));
    }

    @Test
    public void leanbackNativeEnhancedEpisodeGridExpandsWithDetailScroll() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateEpisodeGridViewport()");
        int apply = source.indexOf("private void applyEpisodeViewMode(boolean scrollToCurrent)");
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int setR2 = source.indexOf("setR2Callback();", setEpisode);
        int nextMethod = source.indexOf("private void finishEpisodeLoading()", setEpisode);
        String setEpisodeBody = nextMethod > setEpisode ? source.substring(setEpisode, nextMethod) : source.substring(setEpisode);
        int setR2InBody = setEpisodeBody.indexOf("setR2Callback();");

        assertTrue(sourcePath + " is missing updateEpisodeGridViewport", method >= 0);
        assertTrue("leanback native enhanced episode grid must expand so source/page controls scroll away with it",
                source.indexOf("params.height = ViewGroup.LayoutParams.WRAP_CONTENT", method) > method);
        assertTrue("leanback native enhanced episode grid must leave vertical scrolling to the detail page",
                source.indexOf("mBinding.episodeGrid.setNestedScrollingEnabled(false)", method) > method);
        assertFalse("leanback native enhanced episode grid must not cap itself to an internal scroll viewport",
                source.substring(method, source.indexOf("private void scrollToCurrentEpisode()", method)).contains("TmdbEpisodeGridPolicy.layout("));
        assertTrue("episode view mode changes must refresh the grid viewport",
                apply >= 0 && source.indexOf("updateEpisodeGridViewport();", apply) > apply);
        assertTrue("episode binding must not schedule a second full adapter refresh after setup",
                setR2 > setEpisode && setR2InBody >= 0 && !setEpisodeBody.substring(setR2InBody).contains("notifyDataSetChanged()"));
    }

    @Test
    public void leanbackTmdbEpisodeCardsAvoidFocusJankHotspots() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void bindCardView(@NonNull ViewHolder holder, Episode item, int position)");
        int next = source.indexOf("private void applyCardSize(AdapterEpisodeCardBinding binding)", bind);
        String bindBody = next > bind ? source.substring(bind, next) : source.substring(bind);

        assertTrue(sourcePath + " is missing bindCardView", bind >= 0);
        assertFalse("TMDB episode card binding must not recreate the focus foreground on every bind",
                bindBody.contains("setForeground("));
        assertTrue("TMDB episode cards should keep long overviews out of the remote focus path",
                bindBody.contains("binding.overview.setText(\"\");")
                        && bindBody.contains("binding.overview.setVisibility(View.GONE);"));
    }

    @Test
    public void leanbackLightweightEpisodeSelectorsKeepTitlesReadable() throws Exception {
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        int width = adapter.indexOf("private int getWidth()");
        int column = adapter.indexOf("public static int getColumn(List<Episode> items, int maxWidth)");
        int text = adapter.indexOf("private void bindTextView(@NonNull ViewHolder holder, Episode item, int position)");
        int addAll = adapter.indexOf("public void addAll(List<Episode> items)");
        int setUseTmdbCard = adapter.indexOf("public void setUseTmdbCard(boolean useTmdbCard)");
        int isUsingTmdbCard = adapter.indexOf("public boolean isUsingTmdbCard()");
        String addAllBody = addAll >= 0 && setUseTmdbCard > addAll ? adapter.substring(addAll, setUseTmdbCard) : "";
        String setUseTmdbCardBody = setUseTmdbCard >= 0 && isUsingTmdbCard > setUseTmdbCard ? adapter.substring(setUseTmdbCard, isUsingTmdbCard) : "";

        assertTrue(adapterPath + " is missing lightweight episode sizing hooks", width >= 0 && column >= 0 && text >= 0);
        assertFalse("episode data refresh must not override the externally configured grid column count",
                addAllBody.contains("column ="));
        assertFalse("episode card mode changes must not override the externally configured grid column count",
                setUseTmdbCardBody.contains("column ="));
        assertTrue("vertical lightweight episode buttons must use the full column width instead of the compact 120dp cap",
                adapter.indexOf("return verticalGridMode ? width : Math.min(width, ResUtil.dp2px(TEXT_BUTTON_MAX_WIDTH_DP));", width) > width);
        assertTrue("lightweight episode columns must be measured from the actual displayed title",
                adapter.indexOf("ResUtil.getTextWidth(getTitle(item), 16)", column) > column);
        assertTrue("lightweight episode buttons must stay single-line so numeric episode labels do not wrap",
                adapter.indexOf("textView.setLayoutParams(params);", text) > text
                        && adapter.indexOf("textView.setSingleLine(true);", text) > text
                        && adapter.indexOf("textView.setMaxLines(1);", text) > text
                        && adapter.indexOf("TextUtils.TruncateAt.MARQUEE", text) > text);

        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int textColumn = dialog.indexOf("private int getTextColumn(List<Episode> episodes)");
        int setEpisodes = dialog.indexOf("private void setEpisodes(Flag flag)");
        assertTrue(dialogPath + " is missing text column policy", textColumn >= 0);
        assertTrue("playback lightweight selector should prefer wide two-column buttons for readable titles",
                dialog.indexOf("return Math.min(2, EpisodeAdapter.getColumn(episodes, getEpisodeContentWidth()));", textColumn) > textColumn);
        assertTrue("playback lightweight selector must set the Leanback grid column width, not only child view width",
                setEpisodes >= 0 && dialog.indexOf("if (!tmdbCard) binding.episode.setColumnWidth(getTextColumnWidth(column));", setEpisodes) > setEpisodes);

        Path nativeItemPath = findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_dialog.xml"));
        String nativeItem = new String(Files.readAllBytes(nativeItemPath), StandardCharsets.UTF_8);
        assertTrue("native episode dialog item should keep upstream single-line marquee behavior",
                nativeItem.contains("android:layout_height=\"42dp\"")
                        && nativeItem.contains("android:singleLine=\"true\"")
                        && nativeItem.contains("android:ellipsize=\"marquee\""));
        assertFalse("native episode dialog item must not wrap compact episode numbers",
                nativeItem.contains("android:maxLines=\"2\""));
    }

    @Test
    public void leanbackTmdbEpisodeDialogAvoidsAlignedCardGridScrolling() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int recycler = source.indexOf("private void setRecyclerView()");
        int key = source.indexOf("private boolean onEpisodeKey(KeyEvent event)");

        assertTrue(sourcePath + " is missing TMDB card focus setup", recycler >= 0 && key > recycler);
        assertTrue("TMDB episode dialog should avoid aligned smooth scrolling for card grids",
                source.indexOf("if (tmdbCard) binding.episode.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM);", recycler) > recycler);
        assertFalse("TMDB episode dialog must not swallow normal up/down focus navigation",
                source.contains("handleTmdbEpisodeGridKey"));
    }

    @Test
    public void leanbackEpisodeDialogLetsHeaderScrollWithEpisodes() throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "dialog_episode_list.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int recycler = source.indexOf("private void setRecyclerView()");
        int setSegmentEpisodes = source.indexOf("private void setSegmentEpisodes(int position)");
        int height = source.indexOf("private void updateEpisodeContentHeight()");
        int align = source.indexOf("private void alignEpisodeScroll(int position)");

        assertTrue("episode dialog must use a scroll root so line and segment controls move away with remote down",
                layout.contains("<androidx.core.widget.NestedScrollView")
                        && layout.contains("android:id=\"@+id/root\"")
                        && layout.contains("android:fillViewport=\"true\""));
        assertTrue("episode grid must expand inside the dialog scroll root instead of owning a fixed viewport",
                layout.contains("android:id=\"@+id/episode\"")
                        && layout.contains("android:layout_height=\"wrap_content\"")
                        && source.indexOf("binding.episode.setNestedScrollingEnabled(false);", recycler) > recycler);
        assertTrue("episode dialog must set an explicit content height because Leanback grids otherwise keep an internal viewport",
                height > setSegmentEpisodes
                        && source.indexOf("updateEpisodeContentHeight();", setSegmentEpisodes) > setSegmentEpisodes
                        && source.indexOf("params.height = getEpisodeContentHeight();", height) > height);
        assertTrue("episode focus changes must scroll the outer dialog instead of letting the inner grid flash under the header",
                align > recycler
                        && source.indexOf("binding.episode.addOnChildViewHolderSelectedListener", recycler) > recycler
                        && source.indexOf("alignEpisodeScroll(position);", recycler) > recycler
                        && source.indexOf("binding.getRoot().scrollTo(0, Math.max(0, targetY));", align) > align);
    }

    @Test
    public void leanbackFullscreenExitRestoresEmbeddedVideoLayout() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int exit = source.indexOf("private void exitFullscreen()");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int next = source.indexOf("private void onContent()", restore);

        assertTrue(sourcePath + " is missing exitFullscreen", exit >= 0);
        assertTrue(sourcePath + " is missing restoreEmbeddedVideoLayoutAfterFullscreen", restore >= 0 && next > restore);

        String exitBody = source.substring(exit, restore);
        String restoreBody = source.substring(restore, next);
        assertTrue("leanback fullscreen exit should reuse the embedded native player restore path",
                exitBody.contains("mBinding.video.setLayoutParams(mFrameParams);")
                        && exitBody.contains("restoreEmbeddedVideoLayoutAfterFullscreen();"));
        assertTrue("embedded native player restore must invalidate stale fullscreen layout measurements",
                restoreBody.contains("mBinding.video.forceLayout();")
                        && restoreBody.contains("mBinding.video.requestLayout();")
                        && restoreBody.contains("mBinding.exo.forceLayout();")
                        && restoreBody.contains("mBinding.exo.requestLayout();")
                        && restoreBody.contains("mBinding.scroll.forceLayout();")
                        && restoreBody.contains("mBinding.scroll.requestLayout();")
                        && restoreBody.contains("mBinding.progressLayout.requestLayout();")
                        && restoreBody.contains("mBinding.video.post(() -> {")
                        && restoreBody.contains("mBinding.progressLayout.postDelayed(() -> {")
                        && restoreBody.contains("}, 180);"));
    }

    @Test
    public void mobileFullscreenExitRestoresEmbeddedVideoLayout() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int exit = source.indexOf("private void exitFullscreen()");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int next = source.indexOf("private void setTransition()", restore);

        assertTrue(sourcePath + " is missing exitFullscreen", exit >= 0);
        assertTrue(sourcePath + " is missing restoreEmbeddedVideoLayoutAfterFullscreen", restore >= 0 && next > restore);

        String exitBody = source.substring(exit, restore);
        String restoreBody = source.substring(restore, next);
        assertTrue("mobile fullscreen exit should reuse the embedded native player restore path",
                exitBody.contains("mBinding.video.setLayoutParams(mFrameParams);")
                        && exitBody.contains("restoreEmbeddedVideoLayoutAfterFullscreen();"));
        assertTrue("mobile embedded native player restore must invalidate stale fullscreen layout measurements",
                restoreBody.contains("mBinding.video.forceLayout();")
                        && restoreBody.contains("mBinding.video.requestLayout();")
                        && restoreBody.contains("mBinding.exo.forceLayout();")
                        && restoreBody.contains("mBinding.exo.requestLayout();")
                        && restoreBody.contains("mBinding.scroll.forceLayout();")
                        && restoreBody.contains("mBinding.scroll.requestLayout();")
                        && restoreBody.contains("mBinding.progressLayout.requestLayout();")
                        && restoreBody.contains("mBinding.video.post(() -> {")
                        && restoreBody.contains("mBinding.progressLayout.postDelayed(() -> {")
                        && restoreBody.contains("}, 180);"));
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
    public void mobileVideoDirectTmdbReleasesBlockingProgressAfterHeaderBind() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateVod(Vod item)");
        int bind = source.indexOf("mTmdbHeaderView.bind(mTmdbUIAdapter);", method);
        int hide = source.indexOf("hideProgress();", bind);
        int style = source.indexOf("styleTmdbSourceInFlagTitle();", bind);

        assertTrue(sourcePath + " is missing updateVod", method >= 0);
        assertTrue("direct TMDB playback must bind the header when TMDB data is ready", bind > method);
        assertTrue("direct TMDB playback must stop the blocking player spinner immediately after the first header bind",
                hide > bind && hide < style);
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
    public void mobileVideoFusionUsesNativePlayerActionButtons() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int move = source.indexOf("private void moveFusionPlayerActionsToTmdb");
        int actionRoot = source.indexOf("mBinding.control.action.getRoot()", move);
        int settings = source.indexOf("applyActionButtonSettings();", actionRoot);
        int layoutParams = source.indexOf("new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)", actionRoot);
        int docked = source.indexOf("private boolean isFusionPlayerActionsDocked()");

        assertTrue(sourcePath + " is missing moveFusionPlayerActionsToTmdb", move >= 0);
        assertTrue("fusion must reuse the native player action root", actionRoot > move);
        assertTrue("fusion reused player buttons must honor player button order and visibility settings", settings > actionRoot);
        assertTrue("fusion reused player action row must fill the TMDB playback controls width on narrow screens", layoutParams > actionRoot);
        assertTrue("showControl must keep docked fusion player buttons visible", docked > move);
        assertTrue("fusion player button text should share the moved playback control theme",
                source.indexOf("tintFusionPlaybackTextTree(mBinding.control.action.getRoot()", source.indexOf("private void applyTmdbPlaybackControlColors()")) > 0);
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
        assertTrue("fullscreen player action buttons must stay white instead of inheriting light TMDB text",
                methodBody.contains("boolean playerOverlay = isFullscreen() || mBinding.control.action.getRoot().getParent() == mBinding.control.bottom")
                        && methodBody.contains("playerOverlay ? Color.WHITE : color"));
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
    public void mobileTmdbFallbackUsesAppWallpaperSurface() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int fallback = source.indexOf("private void showNativeDetailFallback(Vod item)");
        int surface = source.indexOf("private void applyNativeFallbackWallpaperSurface()");
        int theme = source.indexOf("private void applyFusionThemeSurface()");
        int scrim = source.indexOf("private void applyContextWallScrimTheme()");

        assertTrue(sourcePath + " is missing showNativeDetailFallback", fallback >= 0);
        assertTrue(sourcePath + " is missing applyNativeFallbackWallpaperSurface", surface >= 0);
        assertTrue("unmatched TMDB fallback must switch to the app wallpaper surface before native rows draw",
                source.indexOf("applyNativeFallbackWallpaperSurface();", fallback) > fallback);
        assertTrue("unmatched TMDB fallback must clear the opaque root background",
                source.indexOf("mBinding.getRoot().setBackgroundColor(Color.TRANSPARENT);", surface) > surface);
        assertTrue("unmatched TMDB fallback must clear the old TMDB scroll background",
                source.indexOf("mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);", surface) > surface);
        assertTrue("unmatched TMDB fallback must keep a readable dark scrim over the app wallpaper",
                source.indexOf("mBinding.videoContextScrim.setBackgroundResource(R.drawable.shape_video_context_scrim);", scrim) > scrim
                        && source.indexOf("mBinding.videoContextScrim.setVisibility(View.VISIBLE);", scrim) > scrim);
        assertTrue("fusion theme refresh must not cover unmatched fallback with a solid color",
                source.indexOf("mBinding.getRoot().setBackgroundColor(mTmdbFallbackToNative ? Color.TRANSPARENT", theme) > theme);
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
    public void tmdbDetailThemeToggleRestylesExternalLinks() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cycle = source.indexOf("private void cycleThemeMode()");
        int apply = source.indexOf("applyDetailTheme();", cycle);
        int external = source.indexOf("bindExternalLinks();", apply);
        int render = source.indexOf("renderFlagSelection();", apply);
        int method = source.indexOf("private int addExternalLink(String name, String url)");
        int nextMethod = source.indexOf("private void openExternalLink(String url)", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);

        assertTrue(sourcePath + " is missing cycleThemeMode", cycle >= 0);
        assertTrue("theme toggle must rebuild external link rows after theme colors change",
                external > apply && external < render);
        assertTrue("direct detail external link labels must use resolved theme text color",
                methodBody.contains("label.setTextColor(colors.primary)"));
        assertTrue("direct detail external link icons must use resolved theme icon color",
                methodBody.contains("icon.setColorFilter(colors.secondary)"));
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
