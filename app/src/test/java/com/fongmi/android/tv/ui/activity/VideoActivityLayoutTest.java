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
    public void mobileVodControlOverlayRoutesBlankTouchesToGestureDetector() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int initEvent = source.indexOf("protected void initEvent()");
        int rootTouch = source.indexOf("mBinding.control.getRoot().setOnTouchListener(this::onPlayerControlTouch);", initEvent);
        int touchHandler = source.indexOf("private boolean onPlayerControlTouch(View view, MotionEvent event)");
        int gestureDispatch = source.indexOf("return mKeyDown.onTouchEvent(event);", touchHandler);

        assertTrue(sourcePath + " is missing initEvent", initEvent >= 0);
        assertTrue("the fullscreen control overlay must route blank touches directly to the gesture detector", rootTouch > initEvent);
        assertTrue("the control-overlay touch handler must dispatch the full gesture sequence", touchHandler >= 0 && gestureDispatch > touchHandler);
    }

    @Test
    public void mobilePlayerGesturesUseVideoViewBoundsAfterFullscreen() throws Exception {
        List<Path> gestureFiles = Arrays.asList(
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomKeyDown.java")),
                findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerGesture.java"))
        );

        for (Path gestureFile : gestureFiles) {
            String source = new String(Files.readAllBytes(gestureFile), StandardCharsets.UTF_8);
            assertTrue(gestureFile + " must map raw touch coordinates into the actual player view",
                    source.contains("videoView.getLocationOnScreen(videoLocation);")
                            && source.contains("return e.getRawX() - videoLocation[0];")
                            && source.contains("return e.getRawY() - videoLocation[1];"));
            assertTrue(gestureFile + " must use the current player view dimensions for gesture regions",
                    source.contains("private int getVideoWidth()")
                            && source.contains("videoView.getWidth()")
                            && source.contains("videoView.getMeasuredWidth()")
                            && source.contains("private int getVideoHeight()")
                            && source.contains("videoView.getHeight()")
                            && source.contains("videoView.getMeasuredHeight()"));
            assertFalse(gestureFile + " must not use app screen metrics for fullscreen gesture regions",
                    source.contains("ResUtil.isEdge(App.get()")
                            || source.contains("ResUtil.getScreenWidth(App.get())"));
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
    public void mobileEpisodeNameToggleRestoresFallbackArtworkAfterAdapterRecreation() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void toggleEpisodeFileName()");
        int methodEnd = source.indexOf("private void updateEpisodeFileNameButton()", method);
        String methodBody = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";
        int recreateAdapter = methodBody.indexOf("mEpisodeAdapter = new EpisodeAdapter");
        int restoreFallback = methodBody.indexOf("updateEpisodeFallbackStillUrl();", recreateAdapter);
        int attachAdapter = methodBody.indexOf("mBinding.episode.setAdapter(mEpisodeAdapter);", recreateAdapter);

        assertTrue(sourcePath + " is missing toggleEpisodeFileName", method >= 0);
        assertTrue("name toggle must restore fallback artwork on the recreated episode adapter",
                recreateAdapter >= 0 && restoreFallback > recreateAdapter && restoreFallback < attachAdapter);
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
    public void mobilePlayerKernelClickOpensChooserBeforeSwitching() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int clickMethod = source.indexOf("private void onPlayerKernel()");
        int clickMethodEnd = source.indexOf("private boolean onPlayerKernelLong()", clickMethod);
        String clickBody = clickMethod >= 0 && clickMethodEnd > clickMethod ? source.substring(clickMethod, clickMethodEnd) : "";
        int chooseMethod = source.indexOf("private void onChoose()");
        int chooseMethodEnd = source.indexOf("private void onPlayerKernel()", chooseMethod);
        String chooseBody = chooseMethod >= 0 && chooseMethodEnd > chooseMethod ? source.substring(chooseMethod, chooseMethodEnd) : "";
        int invalidateInternalRefresh = chooseBody.indexOf("playerKernelSwitchRequestId++;");
        int launchExternalPlayer = chooseBody.indexOf("PlayerHelper.choose", invalidateInternalRefresh);

        assertTrue(sourcePath + " is missing onPlayerKernel", clickMethod >= 0);
        assertTrue("player kernel click must open the shared chooser", clickBody.contains("onChoose();"));
        assertFalse("player kernel click must not switch to the next core before user selection", clickBody.contains("refreshAndSwitchPlayerKernel"));
        assertTrue("the selected core must retain the refreshed-source switch path", chooseBody.contains("refreshAndSwitchPlayerKernel(which)"));
        assertFalse("a later core selection must not be discarded while an earlier refresh is running", source.contains("if (playerKernelSwitchRefreshing) return true;"));
        assertTrue("only the latest core selection may apply its refreshed result", source.contains("if (requestId != playerKernelSwitchRequestId) return;"));
        assertTrue("external playback selection must invalidate an in-flight internal core refresh", invalidateInternalRefresh >= 0 && launchExternalPlayer > invalidateInternalRefresh);
        assertTrue("an internal selection without refresh metadata must still invalidate an older request", source.indexOf("int requestId = ++playerKernelSwitchRequestId;") < source.indexOf("Flag currentFlag = getFlag();"));
    }

    @Test
    public void leanbackAudioModeReconcilesAfterTracksAndPlayerBecomeReady() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String tracksBody = methodBody(source, "protected void onTracksChanged()", "protected void onTitlesChanged()");
        String stateBody = methodBody(source, "protected void onStateChanged(int state)", "protected void onPlayingChanged(boolean isPlaying)");

        assertTrue("TV track changes must re-evaluate immersive audio after native players publish their video track",
                tracksBody.indexOf("refreshLyrics();") >= 0
                        && tracksBody.indexOf("refreshLyrics();") < tracksBody.indexOf("setTrackVisible();"));
        assertTrue("TV READY state must correct any early audio-only classification made during prepare",
                stateBody.contains("case Player.STATE_READY:")
                        && stateBody.indexOf("refreshLyrics();") > stateBody.indexOf("case Player.STATE_READY:"));
    }

    @Test
    public void leanbackImmersiveAudioModeUsesAudioContentGuard() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String policyBody = methodBody(source, "private boolean shouldUseImmersiveAudio()", "private void syncAudioStageSurface(boolean visible)");

        assertTrue("TV immersive audio must honor the selected mode for audio-only or music-like content",
                policyBody.contains("return PlayerSetting.isImmersiveAudioMode() && (isAudioOnly() || isMusicLike());"));
    }

    @Test
    public void leanbackAudioStageReconcilesRestoredViewVisibility() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String visibilityBody = methodBody(source, "private void setAudioStageVisible(boolean visible)", "private boolean shouldUseImmersiveAudio()");

        int reconcileView = visibilityBody.indexOf("mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);");
        int stateFastPath = visibilityBody.indexOf("if (mAudioStageVisible == visible)");
        assertTrue("TV audio stage must reconcile the real View before trusting its cached visibility flag",
                reconcileView >= 0 && stateFastPath > reconcileView);
    }

    @Test
    public void leanbackDisabledAudioStageHidesBeforeLyricsControllerEarlyReturn() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String refreshBody = methodBody(source, "private void refreshLyricsNow()", "private void scheduleRefreshKaraoke");

        int audioContent = refreshBody.indexOf("boolean audioContent = shouldUseImmersiveAudio();");
        int hideStage = refreshBody.indexOf("setAudioStageVisible(audioContent);");
        int earlyReturn = refreshBody.indexOf("if (mLyrics == null || service() == null) return;");
        assertTrue("TV disabled audio stage must be hidden even when lyrics controllers were never initialized",
                audioContent >= 0 && hideStage > audioContent && earlyReturn > hideStage);
    }

    @Test
    public void leanbackAudioStageOverlayStartsHiddenWhileAutoDetectionIsDisabled() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String setupBody = methodBody(source, "private void setupAudioStageOverlay()", "private void setupAudioStageFocusFeedback()");

        int reattach = setupBody.indexOf("addView(mBinding.audioStage, params);");
        int resetState = setupBody.indexOf("mAudioStageVisible = false;");
        int hideView = setupBody.indexOf("mBinding.audioStage.setVisibility(View.GONE);");
        assertTrue("TV audio stage overlay must not become visible merely because it was reattached to the root",
                reattach >= 0 && resetState > reattach && hideView > resetState);
    }

    @Test
    public void leanbackDisabledAudioStageStaysHiddenBeforeFirstFrame() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        Element audioStage = findAndroidId(findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml")).toFile(), "audioStage");

        assertTrue("TV audio stage must opt out of restoring a previously visible overlay",
                audioStage != null
                        && "false".equals(audioStage.getAttribute("android:saveEnabled"))
                        && source.contains("mBinding.audioStage.setSaveFromParentEnabled(false);"));
        int onResume = source.indexOf("protected void onResume()");
        int hideStage = source.indexOf("setAudioStageVisible(false);", onResume);
        int superResume = source.indexOf("super.onResume();", onResume);
        assertTrue("TV audio stage must be hidden in onResume before Android draws the first frame",
                onResume >= 0 && hideStage > onResume && superResume > hideStage);
    }

    @Test
    public void videoPlaybackStartKeepsMergedAudioPreparation() throws Exception {
        assertVideoPlaybackStartKeepsMergedAudioPreparation("TV",
                findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        assertVideoPlaybackStartKeepsMergedAudioPreparation("mobile",
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
    }

    private static void assertVideoPlaybackStartKeepsMergedAudioPreparation(String label, Path sourcePath) throws Exception {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void getPlayer(Flag flag, Episode episode)", "private void setPlayer(Result result)");

        assertTrue(label + " playback must preserve per-episode play flags", body.contains("String playFlag = getEpisodePlayFlag(flag, episode);"));
        assertTrue(label + " playback must update the audio episode identity before loading", body.contains("mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);"));
        assertTrue(label + " playback must load inline lyrics for the selected episode", body.contains("mInlineLyrics = getEpisodeInlineLyrics(episode);"));
        assertTrue(label + " playback must update artwork before the new item starts", body.contains("applyPlaybackArtwork(episode);"));
        assertTrue(label + " playback must clear lyrics and karaoke state between episodes",
                body.contains("clearLyrics();") && body.contains("clearKaraokeState();"));
        assertTrue(label + " playback must request content with the resolved per-episode flag",
                body.contains("mViewModel.playerContent(getKey(), playFlag, episode.getUrl());"));
    }

    @Test
    public void mobileAudioLifecycleKeepsStageStateInSync() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String tracksBody = methodBody(source, "protected void onTracksChanged()", "protected void onTitlesChanged()");
        String audioStateBody = methodBody(source, "private void updateAudioOnlyState()", "protected void onTitlesChanged()");
        String startBody = methodBody(source, "protected void onStart()", "protected void onStop()");
        String resumeBody = methodBody(source, "protected void onResume()", "private void finishIfPipClosed()");
        String configBody = methodBody(source, "public void onConfigurationChanged(@NonNull Configuration newConfig)", "public void onWindowFocusChanged(boolean hasFocus)");

        assertTrue("mobile track changes must refresh lyrics after reconciling the final track set",
                tracksBody.contains("updateAudioOnlyState();")
                        && tracksBody.contains("suppressPiPForAudio();")
                        && tracksBody.contains("refreshLyrics();"));
        assertTrue("mobile audio state must update desktop lyrics, stage visibility, and karaoke actions",
                audioStateBody.contains("LyricsController.isAudioOnly(player())")
                        && audioStateBody.contains("syncDesktopLyricsAudioContent();")
                        && audioStateBody.contains("setAudioStageVisible(shouldUseImmersiveAudio());")
                        && audioStateBody.contains("setKaraokeActionState();"));
        assertTrue("mobile start must reset stale audio state before refreshing the current player",
                startBody.indexOf("setAudioOnly(false);") >= 0
                        && startBody.indexOf("setAudioOnly(false);") < startBody.indexOf("refreshLyrics();")
                        && startBody.contains("syncLyricsPlaybackState();")
                        && startBody.contains("syncKaraokePosition();"));
        assertTrue("mobile resume must restore the audio stage and synchronize lyrics playback",
                resumeBody.contains("if (mAudioStageVisible) restorePlaybackArtwork();")
                        && resumeBody.contains("if (mAudioStageVisible) applyAudioBackground();")
                        && resumeBody.contains("syncLyricsPlaybackState();")
                        && resumeBody.contains("syncKaraokePosition();"));
        assertTrue("mobile rotation must recreate a visible audio stage before applying video fullscreen rules",
                configBody.contains("if (shouldRecreateAudioStageForOrientation(newConfig))")
                        && configBody.contains("setAudioOnly(true);")
                        && configBody.contains("recreate();"));
    }

    @Test
    public void videoAudioControllersAreReleasedWithTheirActivity() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackDestroy = methodBody(leanback, "protected void onDestroy()", "public String getSubtitlePlaybackKey()");
        assertTrue("TV destroy must invalidate lyric work and close lyric sheets",
                leanbackDestroy.contains("mLyricsSearchSeq++;")
                        && leanbackDestroy.contains("mLyricsRefreshSeq++;")
                        && leanbackDestroy.contains("dismissLyricsResultDialog();"));
        assertTrue("TV destroy must release lyric and karaoke controllers",
                leanbackDestroy.contains("mLyrics.release();") && leanbackDestroy.contains("mKaraoke.release();"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileDestroy = methodBody(mobile, "protected void onDestroy()", "public String getSubtitlePlaybackKey()");
        assertTrue("mobile destroy must dismiss configuration-owned karaoke UI and cancel pitch generation",
                mobileDestroy.contains("dismissKaraokeResultDialogForRecreation();")
                        && mobileDestroy.contains("cancelKaraokePitchGeneration(false);")
                        && mobileDestroy.contains("dismissLyricsResultDialog();"));
        assertTrue("mobile destroy must release lyric and karaoke controllers",
                mobileDestroy.contains("mLyrics.release();") && mobileDestroy.contains("mKaraoke.release();"));
    }

    @Test
    public void videoAudioCallbacksKeepPlaybackStateSynchronized() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackError = methodBody(leanback, "protected void onError(String msg)", "protected void onReclaim()");
        String leanbackPlaying = methodBody(leanback, "protected void onPlayingChanged(boolean isPlaying)", "protected void onSizeChanged(VideoSize size)");
        assertTrue("TV playback errors must clear stale lyrics and karaoke state",
                leanbackError.contains("clearLyrics();") && leanbackError.contains("clearKaraokeState();"));
        assertTrue("TV play/pause changes must update karaoke position and the immersive audio transport",
                leanbackPlaying.contains("syncKaraokePosition();") && leanbackPlaying.contains("checkAudioPlayImg(isPlaying);"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileError = methodBody(mobile, "protected void onError(String msg)", "protected void onReload(String msg)");
        String mobileState = methodBody(mobile, "protected void onStateChanged(int state)", "protected void onPlayingChanged(boolean isPlaying)");
        String mobilePlaying = methodBody(mobile, "protected void onPlayingChanged(boolean isPlaying)", "protected void onSizeChanged(VideoSize size)");
        assertTrue("mobile playback errors must clear stale lyrics and karaoke state",
                mobileError.contains("clearLyrics();") && mobileError.contains("clearKaraokeState();"));
        assertTrue("mobile READY state must refresh lyrics and reset the karaoke result for a new playback",
                mobileState.contains("mPendingKaraokeResult == null")
                        && mobileState.contains("mKaraokeResultShown = false;")
                        && mobileState.contains("refreshLyrics();"));
        assertTrue("mobile play/pause changes must synchronize lyrics, karaoke, PiP, and the audio transport",
                mobilePlaying.contains("syncLyricsPlaybackState(isPlaying);")
                        && mobilePlaying.contains("syncKaraokePosition();")
                        && mobilePlaying.contains("suppressPiPForAudio()")
                        && mobilePlaying.contains("checkAudioPlayImg("));
    }

    @Test
    public void videoPlayerResultsKeepMergedAudioMetadata() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackPlayer = methodBody(leanback, "private void setPlayer(Result result)", "private boolean redirectToContentHandler(Result result)");
        assertTrue("TV results without artwork must restore the selected episode artwork", leanbackPlayer.contains("else applyPlaybackArtwork(getPlaybackEpisode());"));
        assertTrue("TV result descriptions must remain available as inline lyrics", leanbackPlayer.contains("setPlaybackLyrics(result.getDesc());"));
        assertTrue("TV audio queue metadata must be applied before playback starts", leanbackPlayer.contains("applyAudioQueueMetadata(getPlaybackEpisode());"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobilePlayer = methodBody(mobile, "private void setPlayer(Result result)", "private boolean redirectToAudioIfNeeded(Result result)");
        assertTrue("mobile player results must wait until the playback service is connected",
                mobilePlayer.contains("if (service() == null)") && mobilePlayer.contains("mPendingPlayerResult = result;"));
        assertTrue("mobile results without artwork must restore the selected episode artwork", mobilePlayer.contains("else applyPlaybackArtwork(getPlaybackEpisode());"));
        assertTrue("mobile result descriptions must remain available as inline lyrics", mobilePlayer.contains("setPlaybackLyrics(result.getDesc());"));
        assertTrue("mobile audio queue metadata must be applied before playback starts", mobilePlayer.contains("applyAudioQueueMetadata(getPlaybackEpisode());"));
    }

    @Test
    public void mobilePlaybackServiceConsumesPendingMergedState() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String connected = methodBody(source, "protected void onServiceConnected()", "protected void onPlayerRebuilt()");
        String detail = methodBody(source, "private void setDetail(Vod item)", "private void setText(Vod item)");

        assertTrue("mobile service connection must restore desktop lyrics state before consuming queued playback",
                connected.contains("syncDesktopLyricsAudioContent();")
                        && connected.contains("if (consumePendingPlaybackResult()) return;")
                        && connected.indexOf("consumePendingPlaybackResult()") < connected.indexOf("checkId();"));
        assertTrue("mobile detail results must wait for the playback service just like player results",
                detail.contains("if (service() == null)") && detail.contains("mPendingDetailVod = item;"));
    }

    @Test
    public void detailSwitchClearsReusableAudioControllersInsteadOfReleasingThem() throws Exception {
        assertDetailSwitchKeepsReusableAudioControllers("TV",
                findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        assertDetailSwitchKeepsReusableAudioControllers("mobile",
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
    }

    private static void assertDetailSwitchKeepsReusableAudioControllers(String label, Path sourcePath) throws Exception {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void getDetail(Vod item)", "private void setDetail(Result result)");

        assertTrue(label + " detail switching must clear reusable lyrics and karaoke state",
                body.contains("clearLyrics();") && body.contains("clearKaraokeState();"));
        assertFalse(label + " detail switching must not release activity-owned audio controllers", body.contains(".release();"));
        assertTrue(label + " detail switching must invalidate lyrics searches exactly once", occurrences(body, "mLyricsSearchSeq++;") == 1);
    }

    @Test
    public void videoClockKeepsLyricsAndKaraokeAdvancing() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackClock = methodBody(leanback, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("TV clock ticks must advance lyrics and karaoke state",
                leanbackClock.contains("syncKaraokePosition();")
                        && leanbackClock.contains("mLyrics.update(player());")
                        && leanbackClock.contains("mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileClock = methodBody(mobile, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("mobile clock ticks must learn playlist metadata and advance lyrics and karaoke state",
                mobileClock.contains("syncCurrentAudioPlaylistMetadata();")
                        && mobileClock.contains("syncKaraokePosition();")
                        && mobileClock.contains("mLyrics.update(player());")
                        && mobileClock.contains("mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());"));
    }

    @Test
    public void videoDetailTextKeepsInlineLyricsMetadata() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackText = methodBody(leanback, "private void setText(Vod item)", "private void setText(TextView view");
        assertTrue("TV detail content must remain available as inline lyrics", leanbackText.contains("setDetailLyrics(item.getContent());"));
        assertTrue("TV detail binding must refresh immersive audio labels", leanbackText.contains("updateAudioStageText();"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileText = methodBody(mobile, "private void setText(Vod item)", "private boolean shouldUseTmdbTabletWideLayout()");
        assertTrue("mobile detail content must be captured before TMDB reveal can return early",
                mobileText.indexOf("setDetailLyrics(item.getContent());") >= 0
                        && mobileText.indexOf("setDetailLyrics(item.getContent());") < mobileText.indexOf("if (shouldWaitForTmdbDetailReveal())"));
        assertTrue("mobile detail binding must refresh immersive audio labels", mobileText.contains("updateAudioStageText();"));
    }

    @Test
    public void mobileConfigurationRestorePreservesPlaybackAndAudioMetadata() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String init = methodBody(source, "protected void initView(Bundle savedInstanceState)", "private void setupIntroSkipConfirmListener()");
        String checkFlag = methodBody(source, "private void checkFlag(Vod item)", "private void checkHistory(Vod item)");
        String episodeClick = methodBody(source, "public void onItemClick(Episode item)", "public void onItemClick(EpisodeGroupAdapter.Group item)");

        assertTrue("mobile recreation must remember that an existing service playback should be preserved",
                init.contains("mRestoringConfigurationPlayback = savedInstanceState != null;"));
        assertTrue("mobile detail restore must bind flags without starting the same episode again",
                checkFlag.contains("boolean preservePlayback = mRestoringConfigurationPlayback")
                        && checkFlag.contains("restoreFlagSelectionWithoutPlayback();")
                        && checkFlag.contains("mRestoringConfigurationPlayback = false;"));
        assertTrue("mobile episode switches must persist current playlist metadata before selecting the next item",
                episodeClick.contains("syncCurrentAudioPlaylistMetadata();")
                        && episodeClick.contains("applyAudioQueueMetadata(item);")
                        && episodeClick.indexOf("syncCurrentAudioPlaylistMetadata();") < episodeClick.indexOf("mFlagAdapter.toggle(item);"));
    }

    @Test
    public void refreshedPlayerKernelSwitchKeepsManualFailureSemantics() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void switchPlayer(int type, Result result");
        int methodEnd = source.indexOf("private void switchPlayer(int type, boolean persist)", method);
        String methodBody = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing refreshed-result player switching", method >= 0);
        assertTrue("a user-selected refreshed core must stop instead of auto-falling back on its first failure", methodBody.contains("manualPlayerSwitchPending = true;"));
    }

    @Test
    public void videoActivitiesDelegateSharedPlayerUiSetup() throws Exception {
        String leanback = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String mobile = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String host = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiHost.java"))), StandardCharsets.UTF_8);
        String controller = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java"))), StandardCharsets.UTF_8);
        String chrome = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerChrome.java"))), StandardCharsets.UTF_8);

        assertVideoActivityDelegatesSharedPlayerUiSetup("leanback VideoActivity", leanback, "VodPlayerChrome.fromVideo(mBinding, mBinding.widget.clock, 14f)");
        assertVideoActivityDelegatesSharedPlayerUiSetup("mobile VideoActivity", mobile, "VodPlayerChrome.fromVideo(mBinding, null, 12f)");
        assertTrue("shared video chrome factory must expose OSD, optional clock and optional diagnostics button",
                chrome.contains("fromVideo(ActivityVideoBinding binding, TextView clockView, float osdMiniSp)")
                        && chrome.contains("binding.osd.getRoot()")
                        && chrome.contains("clockView")
                        && chrome.contains("osdMiniSp"));
        assertTrue("shared player UI controller must create Clock with the optional chrome clock view",
                controller.contains("chrome.clockView == null ? Clock.create() : Clock.create(chrome.clockView)"));
        assertTrue("shared host should let activities preserve their previous diagnostics restore behavior",
                host.contains("default boolean restoreDiagnosticsOnStart()") && host.contains("return true;"));
        assertTrue("shared controller should only restore diagnostics visibility when the host opts in",
                controller.contains("if (host.restoreDiagnosticsOnStart()) osd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());"));
        int mobileRestore = mobile.indexOf("public boolean restoreDiagnosticsOnStart()");
        int mobileRestoreEnd = mobile.indexOf("}", mobileRestore);
        String mobileRestoreBody = mobileRestore >= 0 && mobileRestoreEnd > mobileRestore ? mobile.substring(mobileRestore, mobileRestoreEnd) : "";
        assertTrue("mobile VideoActivity must keep diagnostics as a manual transient overlay",
                mobileRestoreBody.contains("return false;"));
        assertTrue("leanback VideoActivity should keep persistent diagnostics restore via the host default",
                !leanback.contains("public boolean restoreDiagnosticsOnStart()"));
    }

    private static void assertVideoActivityDelegatesSharedPlayerUiSetup(String label, String source, String chromeFactory) {
        int init = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int initEnd = source.indexOf("private void setRecyclerView()", init);
        int start = source.indexOf("protected void onStart()");
        int startEnd = source.indexOf("protected void onStop()", start);
        int stopEnd = source.indexOf("\n    @Override", startEnd + 1);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyEnd = source.indexOf("private boolean isOwner()", destroy);
        if (stopEnd < 0) stopEnd = destroy;
        if (destroyEnd < 0) destroyEnd = source.length();
        String initBody = source.substring(init, initEnd);
        String startBody = source.substring(start, startEnd);
        String stopBody = source.substring(startEnd, stopEnd);
        String destroyBody = source.substring(destroy, destroyEnd);

        assertTrue(label + " must own a shared player UI controller field", source.contains("private VodPlayerUiController mPlayerUi;"));
        assertTrue(label + " must create shared player UI controller with the variant chrome", initBody.contains("mPlayerUi = new VodPlayerUiController") && initBody.contains(chromeFactory));
        assertTrue(label + " must backfill legacy playback UI fields during migration",
                initBody.contains("mClock = mPlayerUi.clock();")
                        && initBody.contains("mOsd = mPlayerUi.osd();")
                        && initBody.contains("mPiP = mPlayerUi.pip();"));
        assertTrue(label + " should not instantiate duplicate OSD/Clock/PiP helpers in initView",
                !initBody.contains("new PlayerOsdController")
                        && !initBody.contains("Clock.create(")
                        && !initBody.contains("new PiP()"));
        assertTrue(label + " must delegate shared UI lifecycle",
                startBody.contains("mPlayerUi.onStart();")
                        && stopBody.contains("mPlayerUi.onStop();")
                        && destroyBody.contains("mPlayerUi.release();"));
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
    public void mobileFullscreenButtonRevealsControlsAfterNativeEnhancedLayoutSettles() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int onFullscreen = source.indexOf("private void onFullscreen()");
        int onFullscreenEnd = source.indexOf("private void onPiP()", onFullscreen);
        int enterFullscreen = source.indexOf("private void enterFullscreen()");
        int schedule = source.indexOf("private void scheduleFullscreenControlReveal()", enterFullscreen);
        int guard = source.indexOf("private void showControlIfFullscreen()", schedule);
        int exitFullscreen = source.indexOf("private void exitFullscreen()", guard);
        int configChanged = source.indexOf("public void onConfigurationChanged(@NonNull Configuration newConfig)");
        int configEnd = source.indexOf("public void onWindowFocusChanged(boolean hasFocus)", configChanged);

        assertTrue(sourcePath + " is missing onFullscreen", onFullscreen >= 0 && onFullscreenEnd > onFullscreen);
        assertTrue(sourcePath + " is missing enterFullscreen", enterFullscreen >= 0 && schedule > enterFullscreen);
        assertTrue(sourcePath + " is missing scheduleFullscreenControlReveal", schedule >= 0 && guard > schedule);
        assertTrue(sourcePath + " is missing showControlIfFullscreen", guard >= 0 && exitFullscreen > guard);
        assertTrue(sourcePath + " is missing onConfigurationChanged", configChanged >= 0 && configEnd > configChanged);

        String onFullscreenBody = source.substring(onFullscreen, onFullscreenEnd);
        String enterFullscreenBody = source.substring(enterFullscreen, schedule);
        String scheduleBody = source.substring(schedule, guard);
        String guardBody = source.substring(guard, exitFullscreen);
        String configBody = source.substring(configChanged, configEnd);

        assertTrue("fullscreen button must reveal controls after the immediate showControl call",
                onFullscreenBody.contains("boolean exit = isFullscreen();")
                        && onFullscreenBody.contains("showControl();")
                        && onFullscreenBody.contains("if (!exit) scheduleFullscreenControlReveal();")
                        && onFullscreenBody.indexOf("showControl();") < onFullscreenBody.indexOf("scheduleFullscreenControlReveal();"));
        assertTrue("fullscreen player layer must sit above native enhanced detail content",
                enterFullscreenBody.contains("mBinding.video.bringToFront();"));
        assertTrue("fullscreen control reveal must run once after layout and once after orientation settles",
                scheduleBody.contains("mBinding.video.post(this::showControlIfFullscreen);")
                        && scheduleBody.contains("mBinding.video.postDelayed(this::showControlIfFullscreen, 300);"));
        assertTrue("delayed fullscreen control reveal must not run after exit, lock, or PiP",
                guardBody.contains("if (!isFullscreen() || isLock() || isInPictureInPictureMode()) return;")
                        && guardBody.contains("showControl();"));
        assertTrue("orientation changes must refresh visible fullscreen controls after landscape state is applied",
                configBody.contains("if (isFullscreen()) {")
                        && configBody.contains("Util.hideSystemUI(this);")
                        && configBody.contains("if (isVisible(mBinding.control.getRoot())) showControl();"));
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
    public void mobileTmdbDetailHidesNativeActionRowInColorfulMode() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setDetail(Vod item)");

        assertTrue(sourcePath + " is missing setDetail", method >= 0);
        assertTrue("TMDB detail playback must hide the native action row for colorful/native styled detail pages",
                source.indexOf("setOriginalEnhancedActionVisibility(tmdbMode);", method) > method);
    }

    @Test
    public void mobileReadyStateKeepsPlaybackInitializationAfterPendingResumeSeek() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("protected void onStateChanged(int state)");
        int ready = source.indexOf("case Player.STATE_READY:", method);
        int seek = source.indexOf("boolean pendingResumeSeekApplied = applyPendingResumeSeek();", ready);
        int reset = source.indexOf("player().reset();", ready);
        int shortDrama = source.indexOf("applyShortDramaMode();", ready);
        int introSkip = source.indexOf("requestIntroSkipPlan();", ready);
        int autoSkipGuard = source.indexOf("if (!pendingResumeSeekApplied) applyAutoIntroSkip();", ready);

        assertTrue(sourcePath + " is missing onStateChanged", method >= 0);
        assertTrue("ready playback must capture whether a pending resume seek was applied", seek > ready);
        assertTrue("pending resume seek must not skip playback reset", reset > seek);
        assertTrue("pending resume seek must not skip short-drama readiness", shortDrama > reset);
        assertTrue("pending resume seek must not skip intro-skip planning", introSkip > shortDrama);
        assertTrue("auto intro skip should wait for the deferred seek to settle", autoSkipGuard > introSkip);
    }

    @Test
    public void mobileTmdbImageReadyRebindsDeferredSummaryAndRevealsDetailContent() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int listener = source.indexOf("mTmdbHeaderView.setOnImagesLoadedListener");
        int callback = source.indexOf("onTmdbContentReady();", listener);
        int helper = source.indexOf("private void onTmdbContentReady()");
        int loaded = source.indexOf("mTmdbContentLoaded = true;", helper);
        int summary = source.indexOf("if (mVod != null) setText(mVod);", helper);
        int reveal = source.indexOf("showDetailContent();", helper);

        assertTrue("TMDB image completion must funnel through a dedicated content-ready callback", callback > listener);
        assertTrue(sourcePath + " is missing onTmdbContentReady", helper >= 0);
        assertTrue("TMDB content-ready callback must mark the TMDB page as loaded", loaded > helper);
        assertTrue("TMDB content-ready callback must rebind the deferred native summary text", summary > loaded);
        assertTrue("TMDB content-ready callback must reveal detail content independently from video readiness", reveal > summary);
    }

    @Test
    public void mobilePlaybackReadyAlwaysClearsVideoLoadingOverlay() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int playback = source.indexOf("private void showPlaybackContent()");
        int hide = source.indexOf("hideProgress();", playback);
        int detailCall = source.indexOf("showDetailContent();", hide);
        int detail = source.indexOf("private void showDetailContent()");
        int revealGuard = source.indexOf("if (!canRevealPlaybackContent()) return;", detail);
        int showContent = source.indexOf("mBinding.progressLayout.showContent();", detail);

        assertTrue(sourcePath + " is missing showPlaybackContent", playback >= 0);
        assertTrue("playback readiness must clear only the video loading overlay first", hide > playback && detailCall > hide);
        assertTrue("detail content reveal must remain independently gated by detail loading state", revealGuard > detail && showContent > revealGuard);
        assertTrue("detail content reveal must remain gated after the video overlay is cleared", showContent > revealGuard);
    }

    @Test
    public void mobileTmdbContentReadyDoesNotClearVideoLoadingBeforePlaybackReady() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onTmdbContentReady()");
        int end = source.indexOf("private void showError(String text)", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing onTmdbContentReady", method >= 0);
        assertTrue("TMDB completion must reveal detail content immediately", body.contains("showDetailContent();"));
        assertFalse("TMDB detail completion must not clear the independent video loading overlay", body.contains("hideProgress();"));
        assertFalse("TMDB detail completion must not wait for player READY", body.contains("Player.STATE_READY"));
        assertFalse("TMDB detail completion must not go through playback content helper", body.contains("showPlaybackContent();"));
    }

    @Test
    public void mobileTmdbPlaybackLoadingOnlyUsesProgressLayoutBeforeDetailContentLoads() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void showProgress()");
        int end = source.indexOf("private void hideProgress()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";
        int showOverlay = body.indexOf("mBinding.progress.getRoot().setVisibility(View.VISIBLE);");
        int tmdbGuard = body.indexOf("if (shouldLoadTmdbDetail() && !mTmdbContentLoaded) mBinding.progressLayout.showProgress();");
        int nativeFallback = body.indexOf("else if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.hideContent();");

        assertTrue(sourcePath + " is missing showProgress", method >= 0);
        assertTrue("video loading overlay must still be shown first", showOverlay >= 0);
        assertTrue("initial TMDB detail loading must use progressLayout until detail content is ready", tmdbGuard > showOverlay);
        assertTrue("non-TMDB playback should keep the existing content-preserving fallback", nativeFallback > tmdbGuard);
    }

    @Test
    public void mobileEpisodeSwitchDoesNotResetLoadedTmdbDetailContent() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void getPlayer(Flag flag, Episode episode)");
        int end = source.indexOf("private void setPlayer(Result result)", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing getPlayer", method >= 0);
        assertFalse("episode switches start playback but must not force the already-loaded TMDB detail area back to loading", body.contains("mTmdbContentLoaded = false"));
        assertTrue("episode switches should still show the video loading overlay", body.contains("showProgress();"));
    }

    @Test
    public void mobileDirectPlaybackUsesUpstreamNativeEpisodeGrid() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()");
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items)");
        int viewport = source.indexOf("private void updateEpisodeViewportHeight()");
        int nextViewportMethod = source.indexOf("private boolean isTmdbEpisodeCardMode()", viewport);
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items)");
        String viewportBody = nextViewportMethod > viewport ? source.substring(viewport, nextViewportMethod) : "";

        assertTrue(sourcePath + " is missing direct native episode predicate", predicate >= 0);
        assertTrue("direct native episode mode must be scoped to the 影视原生 setting",
                source.indexOf("return Setting.isDirectDetailPage() && !isTmdbMode();", predicate) > predicate);
        assertTrue("direct native playback must bypass enhanced episode grid binding",
                setEpisode >= 0 && source.indexOf("if (shouldUseUpstreamNativeEpisodeModule())", setEpisode) > setEpisode);
        assertTrue("direct native playback should restore the upstream episode grid",
                bind >= 0
                        && source.indexOf("mEpisodeGridMode = true;", bind) > bind
                        && source.indexOf("mEpisodeAdapter.setViewType(ViewType.GRID);", bind) > bind
                        && source.indexOf("mBinding.more.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort())", bind) > bind
                        && source.indexOf("mBinding.episodeGroup.setVisibility(groups.size() > 1 ? View.VISIBLE : View.GONE);", bind) > bind
                        && source.indexOf("setEpisodeItems(items);", bind) > bind);
        assertTrue("direct native episode grid should use the standard viewport cap",
                viewport >= 0 && !viewportBody.contains("shouldUseUpstreamNativeEpisodeModule()"));
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
    public void leanbackVodEventPageSuffixStripPreservesLeadingSlashIds() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private static String stripPageSuffix(String id)");
        int nextMethod = source.indexOf("private void loadNativePersonalRecommendations", method);
        String body = method >= 0 && nextMethod > method ? source.substring(method, nextMethod) : "";

        assertTrue(sourcePath + " is missing stripPageSuffix", method >= 0);
        assertTrue("leanback VOD event ids may start with /index.php and must not be stripped to empty",
                body.contains("slash > 0 ? id.substring(0, slash) : id"));
        assertFalse("only real page suffixes after the first character should be stripped",
                body.contains("slash >= 0 ? id.substring(0, slash) : id"));
    }

    @Test
    public void playbackControllerConnectionDoesNotReplayStaleState() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int handle = source.indexOf("private void handleControllerConnected()");
        int addListener = source.indexOf("mController.addListener(this);", handle);
        int reconcile = source.indexOf("reconcileControllerReadyState();", addListener);
        int seekListener = source.indexOf("getSeekView().setSeekListener(this::onSeekStarted);", handle);
        int controllerHook = source.indexOf("onControllerConnected();", addListener);
        int serviceConnected = source.indexOf("public void onServiceConnected");
        int serviceHook = source.indexOf("onServiceConnected();", serviceConnected);

        assertTrue(sourcePath + " is missing handleControllerConnected", handle >= 0);
        assertTrue("controller seek events must be bridged to playback activities", seekListener > handle && seekListener < addListener);
        assertTrue("controller listener must be registered before the controller hook", addListener > handle && addListener < controllerHook);
        assertTrue("current-item READY must be reconciled after listener registration", reconcile > addListener && reconcile < controllerHook);
        assertTrue("controller-specific hook must still run", controllerHook > addListener);
        assertTrue("service-specific hook must still run", serviceHook > serviceConnected);
        assertFalse("controller connection must not replay stale READY/playing state and hide loading early",
                source.contains("syncControllerPlaybackState()"));
        assertFalse("READY reconciliation must not replay the full state callback with side effects",
                source.contains("onStateChanged(mController.getPlaybackState())"));
    }

    @Test
    public void playbackActivityKeepsScreenOnFromPlaybackStateTransitions() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int sync = source.indexOf("private void syncKeepScreenOn()");
        int should = source.indexOf("private boolean shouldKeepScreenOn()", sync);
        int lifecycle = source.indexOf("private String lifecycleState()", should);
        String syncBody = sync >= 0 && should > sync ? source.substring(sync, should) : "";
        String shouldBody = should >= 0 && lifecycle > should ? source.substring(should, lifecycle) : "";
        int connected = source.indexOf("private void handleControllerConnected()");
        int connectedAddListener = source.indexOf("mController.addListener(this);", connected);
        int connectedSync = source.indexOf("syncKeepScreenOn();", connectedAddListener);
        int playing = source.indexOf("public void onIsPlayingChanged(boolean isPlaying)");
        int playingSync = source.indexOf("syncKeepScreenOn();", playing);
        int playWhenReady = source.indexOf("public void onPlayWhenReadyChanged(boolean playWhenReady, int reason)");
        int playWhenReadySync = source.indexOf("syncKeepScreenOn();", playWhenReady);
        int state = source.indexOf("public void onPlaybackStateChanged(int state)");
        int stateSync = source.indexOf("syncKeepScreenOn();", state);
        int service = source.indexOf("public void onServiceConnected(ComponentName name, IBinder binder)");
        int serviceSync = source.indexOf("syncKeepScreenOn();", service);
        int resume = source.indexOf("protected void onResume()");
        int resumeSync = source.indexOf("syncKeepScreenOn();", resume);

        assertTrue(sourcePath + " is missing syncKeepScreenOn", sync >= 0);
        assertTrue(sourcePath + " is missing shouldKeepScreenOn", should > sync);
        assertTrue("wake flag sync must add and clear the window flag from one place",
                syncBody.contains("addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)")
                        && syncBody.contains("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"));
        assertTrue("wake flag must stay on while playback is active or preparing to play",
                shouldBody.contains("active.isPlaying()")
                        && shouldBody.contains("active.getPlayWhenReady()")
                        && shouldBody.contains("state == Player.STATE_BUFFERING || state == Player.STATE_READY"));
        assertTrue("controller attachment must sync wake state in case playback was already active", connectedSync > connectedAddListener);
        assertTrue("playing changes must sync wake state", playingSync > playing);
        assertTrue("playWhenReady changes must sync wake state so pausing while buffering clears the flag", playWhenReady >= 0 && playWhenReadySync > playWhenReady);
        assertTrue("state changes must sync wake state so buffering/ready cannot miss the flag", stateSync > state);
        assertTrue("service connection must sync wake state before subclass hooks run", serviceSync > service);
        assertTrue("resume must resync wake state after returning to an active player", resumeSync > resume);
    }

    @Test
    public void playbackLoadingOnlyClearsFromReadyOrTmdbReady() throws Exception {
        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        assertNoPrematurePlaybackReveal(mobilePath, mobile);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        assertNoPrematurePlaybackReveal(leanbackPath, leanback);
    }

    @Test
    public void seekRequestsShowPlaybackLoadingOverlay() throws Exception {
        Path playbackPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String playback = new String(Files.readAllBytes(playbackPath), StandardCharsets.UTF_8);
        int hook = playback.indexOf("protected void onSeekStarted()");
        int seekTo = playback.indexOf("protected void seekTo(long time)");
        int seekHook = playback.indexOf("onSeekStarted();", seekTo);
        int controllerSeek = playback.indexOf("mController.seekTo", seekHook);

        Path seekPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomSeekView.java"));
        String seek = new String(Files.readAllBytes(seekPath), StandardCharsets.UTF_8);
        int listener = seek.indexOf("public interface SeekListener");
        int setListener = seek.indexOf("public void setSeekListener");
        int seekMethod = seek.indexOf("private void seekToTimeBarPosition");
        int notify = seek.indexOf("seekListener.onSeekStarted();", seekMethod);
        int playerSeek = seek.indexOf("player.seekTo(positionMs);", notify);

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        int mobileOverride = mobile.indexOf("protected void onSeekStarted()");
        int mobileShow = mobile.indexOf("showProgress();", mobileOverride);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        int leanbackOverride = leanback.indexOf("protected void onSeekStarted()");
        int leanbackShow = leanback.indexOf("showProgress();", leanbackOverride);

        assertTrue(playbackPath + " is missing onSeekStarted", hook >= 0);
        assertTrue("remote seek must show loading before seeking", seekHook > seekTo && seekHook < controllerSeek);
        assertTrue(seekPath + " is missing SeekListener", listener >= 0 && setListener > listener);
        assertTrue("drag seek must notify before player.seekTo", notify > seekMethod && notify < playerSeek);
        assertTrue("mobile video seek must show loading", mobileOverride >= 0 && mobileShow > mobileOverride);
        assertTrue("leanback video seek must show loading", leanbackOverride >= 0 && leanbackShow > leanbackOverride);
    }

    @Test
    public void seekLoadingHasReadyStateFallbackWhenPlaybackStateDoesNotChange() throws Exception {
        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        assertSeekProgressFallback(mobilePath, mobile);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        assertSeekProgressFallback(leanbackPath, leanback);
    }

    @Test
    public void mobileTmdbVodRefreshDoesNotClearVideoLoadingBeforePlaybackReady() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateVod(Vod item)");
        int loaded = source.indexOf("if (loaded) {", method);
        int fallback = source.indexOf("} else {", loaded);
        String loadedBody = loaded >= 0 && fallback > loaded ? source.substring(loaded, fallback) : "";
        int helper = source.indexOf("private void bindLoadedTmdbDetail()");
        int helperEnd = source.indexOf("private void updateFlag(", helper);
        String helperBody = helper >= 0 && helperEnd > helper ? source.substring(helper, helperEnd) : "";

        assertTrue(sourcePath + " is missing updateVod", method >= 0);
        assertTrue("TMDB VOD refresh must still bind the loaded header", loadedBody.contains("bindLoadedTmdbDetail();") && helperBody.contains("mTmdbHeaderView.bind(mTmdbUIAdapter);"));
        assertFalse("TMDB VOD refresh must not reveal detail content outside onTmdbContentReady", loadedBody.contains("mBinding.progressLayout.showContent();"));
        assertFalse("TMDB VOD refresh must not clear the independent video loading overlay", loadedBody.contains("hideProgress();"));
    }

    @Test
    public void mobileTmdbDetailLoadingHasTimeoutFallback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int getDetail = source.indexOf("private void getDetail()");
        int cancelStart = source.indexOf("cancelTmdbDetailFallback();", getDetail);
        int detailCall = source.indexOf("mViewModel.detailContent(getKey(), getId());", getDetail);
        int setDetail = source.indexOf("private void setDetail(Vod item)");
        int schedule = source.indexOf("scheduleTmdbDetailFallback();", setDetail);
        int ready = source.indexOf("private void onTmdbContentReady()");
        int cancelReady = source.indexOf("cancelTmdbDetailFallback();", ready);
        int fallback = source.indexOf("private void showTmdbDetailFallback()");
        int end = source.indexOf("private void showNativeDetailFallback(Vod item)", fallback);
        String fallbackBody = fallback >= 0 && end > fallback ? source.substring(fallback, end) : "";

        assertTrue("mobile TMDB detail loading must use the same timeout as TV", source.contains("TMDB_DETAIL_LOAD_TIMEOUT = 15000"));
        assertTrue("new detail requests must cancel stale TMDB loading timeouts", cancelStart > getDetail && cancelStart < detailCall);
        assertTrue("mobile TMDB detail loading must schedule a timeout after the source detail arrives", schedule > setDetail);
        assertTrue("TMDB content-ready must cancel the timeout", cancelReady > ready);
        assertTrue(sourcePath + " is missing showTmdbDetailFallback", fallback >= 0);
        assertTrue("timeout should first bind already-loaded TMDB data, covering missed VOD refresh events", fallbackBody.contains("bindLoadedTmdbDetail();"));
        assertTrue("timeout must fall back to native details instead of leaving the content area blank", fallbackBody.contains("showNativeDetailFallback(mVod);"));
    }

    @Test
    public void leanbackEpisodeFocusOrderReachesTmdbRowsAfterEpisodes() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int order = source.indexOf("private List<Integer> getEpisodeFocusOrders()");
        int updateFocus = source.indexOf("private void updateFocus()", order);
        String orderBody = updateFocus > order ? source.substring(order, updateFocus) : "";
        int episodeGrid = orderBody.indexOf("R.id.episodeGrid");
        int photos = orderBody.indexOf("R.id.tmdbPhotos");
        int part = orderBody.indexOf("R.id.part");
        int quick = orderBody.indexOf("R.id.quick");
        int bindTmdb = source.indexOf("private void bindTmdbData()");
        int bindRatings = source.indexOf("private void bindTmdbOmdbRatings()", bindTmdb);
        String bindBody = bindRatings > bindTmdb ? source.substring(bindTmdb, bindRatings) : "";

        assertTrue(sourcePath + " is missing getEpisodeFocusOrders", order >= 0);
        assertTrue("leanback episode focus order must include the TMDB photos row", photos >= 0);
        assertTrue("TMDB photos must be after episode rows so DPAD_DOWN can leave the selected episode card", episodeGrid >= 0 && episodeGrid < photos);
        assertTrue("TMDB rows must stay before quick search rows in the detail-page focus order", photos < part && part < quick);
        assertTrue("binding visible TMDB rows must refresh episode card nextFocusDown targets",
                bindBody.contains("updateFocus();") && bindBody.indexOf("updateFocus();") < bindBody.indexOf("finishTmdbDetail();"));
    }

    @Test
    public void leanbackTmdbOmdbRatingChipsUseReadableDarkGlass() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private View createOmdbRatingChip(String platform, String value, String color)");
        int next = source.indexOf("private void setupBackdropSlideshow", method);
        String body = method >= 0 && next > method ? source.substring(method, next) : "";

        assertTrue(sourcePath + " is missing createOmdbRatingChip", method >= 0);
        assertTrue("leanback OMDB chips should use dark glass so white/yellow text remains visible on light artwork",
                body.contains("background.setColor(0x6610141A);")
                        && body.contains("background.setStroke(ResUtil.dp2px(1), 0x33FFFFFF);")
                        && body.contains("platformView.setTextColor(0xE6FFFFFF);")
                        && !body.contains("background.setColor(0x26FFFFFF);")
                        && !body.contains("platformView.setTextColor(0xFF9AA7B4);"));
    }

    @Test
    public void leanbackEpisodeHeaderToolsTrapHorizontalDpadInsideToolPair() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean onEpisodeHeaderToolKey");
        int nextMethod = source.indexOf("private boolean isEpisodeListFocused()", method);
        String body = method >= 0 && nextMethod > method ? source.substring(method, nextMethod) : "";

        assertTrue("episode header reverse and grid/list buttons should handle DPAD horizontally themselves",
                source.contains("mBinding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));")
                        && source.contains("mBinding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));"));
        assertTrue(sourcePath + " is missing onEpisodeHeaderToolKey", method >= 0);
        assertTrue("reverse DPAD_RIGHT should focus the grid/list button",
                body.contains("KeyUtil.isRightKey(event) && view == mBinding.episodeReverse")
                        && body.contains("mBinding.episodeViewMode.requestFocus(View.FOCUS_RIGHT);"));
        assertTrue("grid/list DPAD_LEFT should focus the reverse button",
                body.contains("KeyUtil.isLeftKey(event) && view == mBinding.episodeViewMode")
                        && body.contains("mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);"));
        assertTrue("unused horizontal directions should be consumed so focus cannot jump to unrelated action buttons",
                body.lastIndexOf("return true;") > body.lastIndexOf("mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);"));
    }

    @Test
    public void leanbackHorizontalEpisodeRowExposesVerticalFocusFromEveryCard() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int top = source.indexOf("private boolean isTopEdge(int position)");
        int bottom = source.indexOf("private boolean isBottomEdge(int position)");
        int interfaces = source.indexOf("public interface OnClickListener", bottom);
        String topBody = bottom > top ? source.substring(top, bottom) : "";
        String bottomBody = interfaces > bottom ? source.substring(bottom, interfaces) : "";

        assertTrue(sourcePath + " is missing isTopEdge", top >= 0);
        assertTrue(sourcePath + " is missing isBottomEdge", bottom >= 0);
        assertTrue("single-row horizontal episode cards must all expose nextFocusUp",
                topBody.contains("return !verticalGridMode || position == 0;"));
        assertTrue("single-row horizontal episode cards must all expose nextFocusDown",
                bottomBody.contains("return !verticalGridMode || position == getItemCount() - 1;"));
    }

    @Test
    public void leanbackTmdbDetailUsesChangeSourceActionOutsideDirectNative() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");
        int event = source.indexOf("protected void initEvent()");

        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("native enhanced mode must hide the short display button",
                source.indexOf("mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
        assertTrue("TMDB detail modes must show the source change button instead of the search button",
                source.indexOf("mBinding.change1.setVisibility(hide ? View.VISIBLE : View.GONE)", method) > method
                        && source.indexOf("mBinding.searchDetail.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
        assertTrue("source change action must keep long-press global search",
                event >= 0 && source.indexOf("mBinding.change1.setOnLongClickListener(view -> {", event) > event
                        && source.indexOf("onGlobalSearch();", source.indexOf("mBinding.change1.setOnLongClickListener(view -> {", event)) > event);
    }

    @Test
    public void leanbackDetailActionOrderPutsChangeBeforeKeepAndTmdb() throws Exception {
        String layout = new String(Files.readAllBytes(findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"))), StandardCharsets.UTF_8);
        int search = layout.indexOf("android:id=\"@+id/searchDetail\"");
        int change = layout.indexOf("android:id=\"@+id/change1\"");
        int keep = layout.indexOf("android:id=\"@+id/keep\"");
        int tmdb = layout.indexOf("android:id=\"@+id/tmdbRematch\"");

        assertTrue("direct native search action must stay in the row", search >= 0);
        assertTrue("TMDB detail action order must be change source, favorite, TMDB", change >= 0 && change < keep && keep < tmdb);
    }

    @Test
    public void leanbackGlobalSearchLongPressCarriesCurrentTitle() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onGlobalSearch()");
        int methodEnd = source.indexOf("\n    }", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing onGlobalSearch", method >= 0);
        assertTrue("global search must use the current detail title as keyword",
                body.contains("String keyword = mBinding.name.getText().toString().trim();")
                        && body.contains("if (TextUtils.isEmpty(keyword)) keyword = getName();"));
        assertTrue("global search must open SearchActivity with the title instead of a blank search page",
                body.contains("SearchActivity.start(this, keyword);")
                        && !body.contains("SearchActivity.start(this);"));
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
    public void leanbackTmdbPlaybackOverviewWrapsWithinRightPane() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element overview = findAndroidId(layoutFile.toFile(), "tmdbOverview");

        assertTrue(layoutFile + " is missing @+id/tmdbOverview", overview != null);
        assertTrue("TMDB playback overview must be measured between the title column and screen edge so it wraps instead of ellipsizing as one long line",
                "match_parent".equals(overview.getAttribute("android:layout_width"))
                        && "@+id/name".equals(overview.getAttribute("android:layout_alignStart"))
                        && "true".equals(overview.getAttribute("android:layout_alignParentEnd")));
    }

    @Test
    public void leanbackDetailActionButtonsPreferSourceLineBeforeRecommendations() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setDetailButtonsNextFocus(int fallback)");
        int methodEnd = source.indexOf("\n    }", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing setDetailButtonsNextFocus fallback handling", method >= 0);
        assertTrue("detail action down focus must prefer the visible source line before TMDB recommendation rows",
                body.contains("int target = isVisible(mBinding.flag) ? R.id.flag : fallback;"));
        assertTrue("all visible/optional detail action buttons must share the same down-focus target",
                body.contains("mBinding.content.setNextFocusDownId(target);")
                        && body.contains("mBinding.shortDisplay.setNextFocusDownId(target);")
                        && body.contains("mBinding.searchDetail.setNextFocusDownId(target);")
                        && body.contains("mBinding.keep.setNextFocusDownId(target);")
                        && body.contains("mBinding.change1.setNextFocusDownId(target);")
                        && body.contains("mBinding.tmdbRematch.setNextFocusDownId(target);"));

        int bindTmdbData = source.indexOf("private void bindTmdbData()");
        int empty = source.indexOf("if (!hasTmdbContent)", bindTmdbData);
        int emptyEnd = source.indexOf("SpiderDebug.log(\"tmdb-tv\"", empty);
        String emptyBody = empty >= 0 && emptyEnd > empty ? source.substring(empty, emptyEnd) : "";
        assertTrue("empty TMDB detail rows must keep change and TMDB actions in the down-focus chain",
                emptyBody.contains("mBinding.change1.setNextFocusDownId(R.id.flag);")
                        && emptyBody.contains("mBinding.tmdbRematch.setNextFocusDownId(R.id.flag);"));
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
    public void leanbackPlaybackEpisodeKeyIgnoresMissingFocus() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean onEpisodeKey(KeyEvent event)");
        int directCrashPath = source.indexOf("findContainingViewHolder(getCurrentFocus())", method);
        int focus = source.indexOf("View focus = getCurrentFocus();", method);
        int guard = source.indexOf("if (focus == null) return false;", focus);
        int holder = source.indexOf("findContainingViewHolder(focus)", guard);

        assertTrue(sourcePath + " is missing onEpisodeKey", method >= 0);
        assertFalse("episode key handling must not pass a null current focus into RecyclerView", directCrashPath >= 0);
        assertTrue("episode key handling must read current focus before resolving the RecyclerView holder", focus > method);
        assertTrue("episode key handling must ignore key events when focus has already been cleared", guard > focus);
        assertTrue("episode key handling must only resolve a holder after the focus null guard", holder > guard);
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
    public void tmdbHeaderKeepsChangeSourceVisibleForEnhancedModes() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateOriginalEnhancedActionVisibility()");

        assertTrue(sourcePath + " is missing updateOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("TMDB header must keep source change available in enhanced modes",
                source.indexOf("changeSource.setVisibility(View.VISIBLE)", method) > method);
    }

    @Test
    public void leanbackNativeActionButtonsShareMinimumWidth() throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        for (String id : Arrays.asList("content", "shortDisplay", "searchDetail", "keep", "change1", "tmdbRematch")) {
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
                    "60dp".equals(action.getAttribute("android:minWidth")));
        }
    }

    @Test
    public void updateVodOnlyReplacesHistoryWhenKeyChangesAndAlwaysSyncsAfterReplace() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            int method = source.indexOf("private void updateVod(Vod item)");
            int end = source.indexOf("\n    private ", method + 1);
            String body = method >= 0 && end > method ? source.substring(method, end) : "";

            assertTrue(sourcePath + " is missing updateVod", method >= 0);
            assertTrue(sourcePath + " must only replace history when key actually changes",
                    body.contains("keyChanged = !TextUtils.equals(mHistory.getKey(), nextKey)")
                            && body.contains("if (keyChanged) mHistory.replace(nextKey)"));
            assertFalse(sourcePath + " must not unconditionally replace history on every id update",
                    body.contains("if (id) mHistory.replace(getHistoryKey())"));
            assertTrue(sourcePath + " must sync history after key migration even without pic/name",
                    body.contains("if (keyChanged || pic || name) syncHistory()"));
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
    public void mobileVideoDirectTmdbRevealsDetailAfterHeaderBindWithoutClearingVideoProgress() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateVod(Vod item)");
        int bind = source.indexOf("mTmdbHeaderView.bind(mTmdbUIAdapter);", method);
        int style = source.indexOf("styleTmdbSourceInFlagTitle();", bind);
        int contentReady = source.indexOf("private void onTmdbContentReady()");
        int reveal = source.indexOf("showDetailContent();", contentReady);

        assertTrue(sourcePath + " is missing updateVod", method >= 0);
        assertTrue("direct TMDB playback must bind the header when TMDB data is ready", bind > method);
        assertTrue("direct TMDB playback should continue styling after the header bind", style > bind);
        assertTrue("direct TMDB playback must reveal detail content from the TMDB content-ready callback", reveal > contentReady);
        assertFalse("direct TMDB header bind must not clear the independent video loading overlay",
                source.substring(bind, style).contains("hideProgress();"));
    }

    @Test
    public void mobileVideoFusionThemeToggleActuallyChangesThemeMode() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("fusion theme button must switch light to dark and dark to light",
                source.contains("int theme = isFusionLightTheme() ? 2 : 1;"));
    }

    @Test
    public void tmdbHeaderThemeToggleIsHiddenUntilVideoActivityAllowsFusionThemeSwitching() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        Element themeToggle = findAndroidId(layoutPath.toFile(), "tmdbThemeToggle");
        Path headerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String headerSource = new String(Files.readAllBytes(headerPath), StandardCharsets.UTF_8);
        Path videoPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String videoSource = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        int styleFusion = headerSource.indexOf("private void styleFusionActions()");
        int styleFusionEnd = headerSource.indexOf("private void clearFusionActionStyling()", styleFusion);
        String styleFusionBody = headerSource.substring(styleFusion, styleFusionEnd);

        assertTrue(layoutPath + " is missing @+id/tmdbThemeToggle", themeToggle != null);
        assertTrue("TMDB header theme toggle must start hidden so colorful playback pages do not show it",
                "gone".equals(themeToggle.getAttribute("android:visibility")));
        assertFalse("TmdbHeaderView must not force the theme toggle visible; VideoActivity owns that mode decision",
                styleFusionBody.contains("themeToggle.setVisibility(View.VISIBLE)"));
        assertTrue("VideoActivity must still show the header theme toggle for the real fusion detail mode",
                videoSource.contains("DetailThemeVisibility.showFusionThemeButton(Setting.isFusionDetailPage(), isFullscreen(), isInPictureInPictureMode())"));
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
        assertTrue("dynamic backdrop playback labels must force white text instead of profile dark text",
                methodBody.contains("boolean light = !shouldUseTmdbBackdropSurface() && isTmdbPlaybackLightTheme()")
                        && methodBody.contains("int color = shouldUseTmdbBackdropSurface() ? Color.WHITE : tmdbPlaybackControlColor(light)"));
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
                source.indexOf("mBinding.getRoot().setBackgroundColor(mTmdbFallbackToNative || shouldUseTmdbBackdropSurface() ? Color.TRANSPARENT", theme) > theme);
    }

    @Test
    public void mobileColorfulAndNativeStyledTmdbDetailUseDynamicBackdropSurface() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseTmdbBackdropSurface()");
        int setContextWall = source.indexOf("private void setContextWall(String url, boolean skipLock)");
        int init = source.indexOf("private void initTmdbMode()");
        int themeSurface = source.indexOf("private void applyFusionThemeSurface()");
        int move = source.indexOf("private void moveFlagAndEpisodeToTmdb()");

        assertTrue(sourcePath + " is missing shouldUseTmdbBackdropSurface", predicate >= 0);
        assertTrue("colorful and native styled TMDB detail must opt into the dynamic backdrop surface",
                source.indexOf("Setting.getDetailOpenMode() == Setting.DETAIL_OPEN_ENHANCED", predicate) > predicate
                        && source.indexOf("Setting.isTmdbNativeStyle()", predicate) > predicate);
        assertTrue("dynamic backdrop surface must be allowed even when playback artwork wall is disabled",
                source.indexOf("!shouldUseTmdbBackdropSurface()", setContextWall) > setContextWall);
        assertTrue("colorful and native styled TMDB detail must receive backdrop slideshow changes",
                source.indexOf("shouldUseTmdbBackdropSurface()", init) > init
                        && source.indexOf("setContextWall(imageUrl, true);", init) > init);
        assertTrue("colorful and native styled TMDB detail must use the transparent fullscreen backdrop layout",
                source.indexOf("applyOriginalEnhancedBackdropLayout();", init) > init
                        && source.indexOf("mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);", init) > init);
        assertTrue("colorful and native styled TMDB detail must not be covered by later theme surface refreshes",
                source.indexOf("mTmdbFallbackToNative || shouldUseTmdbBackdropSurface() ? Color.TRANSPARENT", themeSurface) > themeSurface);
        assertTrue("header theme refresh must re-hide the standalone hero artwork",
                source.indexOf("mTmdbHeaderView.refreshTheme();", move) > move
                        && source.indexOf("mTmdbHeaderView.hideNativeHeroBackdrop();", move) > move);
    }

    @Test
    public void tmdbPlaybackBackdropSurfaceHidesTopPosterArtwork() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void hideNativeHeroBackdrop()");

        assertTrue(sourcePath + " is missing hideNativeHeroBackdrop", method >= 0);
        assertTrue("dynamic backdrop surface must keep the hidden backdrop from reserving hero height",
                source.indexOf("params.height = 1;", method) > method);
        assertTrue("dynamic backdrop surface must hide the top poster card",
                source.indexOf("R.id.tmdbPoster", method) > method
                        && source.indexOf("posterCard.setVisibility(View.GONE);", method) > method);
        assertTrue("title text must align after the poster card is removed",
                source.indexOf("textParams.setMarginStart(0);", method) > method);
        assertTrue("dynamic backdrop surface must force header text to white with shadow",
                source.indexOf("backdropSurfaceMode = true;", method) > method
                        && source.indexOf("applyBackdropSurfaceTextColors();", method) > method
                        && source.indexOf("private void applyBackdropSurfaceTextColors()") > method
                        && source.indexOf("styleFusionBackdropText(textView, COLOR_FUSION_BACKDROP_TEXT)", method) > method);
        assertTrue("dynamic backdrop surface must not recolor action button text on light pills",
                source.indexOf("!(view instanceof MaterialButton)", method) > method);
        assertTrue("dynamic backdrop surface must style rating chips and action buttons as dark translucent glass",
                source.contains("COLOR_BACKDROP_SURFACE_CONTROL_BG = 0x6610141A")
                        && source.contains("styleBackdropSurfaceRatingChips();")
                        && source.contains("tintActions(Setting.DETAIL_STYLE_CINEMA);")
                        && source.contains("if (backdropSurfaceMode) {")
                        && source.contains("button.setTextColor(COLOR_FUSION_BACKDROP_TEXT);"));
        assertTrue("rating chips created after backdrop mode starts must keep white text instead of source colors",
                source.contains("chip.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT")
                        && source.contains("textView.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT"));
        assertTrue("dynamic backdrop surface must be reported as a dark readable surface",
                source.contains("if (backdropSurfaceMode) return false;")
                        && source.contains("if (backdropSurfaceMode) return COLOR_FUSION_BACKDROP_TEXT;"));
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
        int refresh = source.indexOf("refreshDetailThemeDynamicViews();", apply);
        int method = source.indexOf("private int addExternalLink(String name, String url)");
        int nextMethod = source.indexOf("private void openExternalLink(String url)", method);
        int style = source.indexOf("private void styleExternalLinkRow(View view, ThemeColors colors)");
        int styleEnd = source.indexOf("private void openExternalLink(String url)", style);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        String styleBody = styleEnd > style ? source.substring(style, styleEnd) : source.substring(style);

        assertTrue(sourcePath + " is missing cycleThemeMode", cycle >= 0);
        assertTrue(sourcePath + " is missing styleExternalLinkRow", style >= 0);
        assertTrue("theme toggle must restyle dynamic rows without rebuilding the whole detail section",
                refresh > apply);
        assertTrue("new external link rows must share the same styling path as theme refreshes",
                methodBody.contains("styleExternalLinkRow(row, colors);"));
        assertTrue("external link rows must repaint their chrome when remote focus changes",
                methodBody.contains("row.setOnFocusChangeListener((view, focused) -> styleExternalLinkRow(view, currentThemeColors()));"));
        assertTrue("direct detail external link labels must use resolved theme text color",
                styleBody.contains("label.setTextColor(colors.primary)"));
        assertTrue("direct detail external link icons must use resolved theme icon color",
                styleBody.contains("icon.setColorFilter(colors.secondary)"));
        assertTrue("focused direct detail external links must use the shared yellow focus stroke",
                styleBody.contains("boolean focused = row.hasFocus();")
                        && styleBody.contains("background.setStroke(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP), focused ? FOCUS_STROKE : colors.line);"));
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

    @Test
    public void mobileHistoryEntryPassesExactPlaybackSelection() throws Exception {
        Path historyPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "HistoryActivity.java"));
        Path videoPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String history = new String(Files.readAllBytes(historyPath), StandardCharsets.UTF_8);
        String video = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        String compactHistory = history.replaceAll("\\s+", " ");

        assertTrue("history clicks must pass flag, episode title, and episode url to direct playback",
                compactHistory.contains("VideoActivity.startDirect(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(), item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl())"));
        assertTrue("direct playback must preserve the requested episode selection in the intent",
                video.contains("putIntentPlaybackSelection(intent, playFlag, playEpisodeName, playEpisodeUrl);"));
    }

    @Test
    public void mobileSaveHistoryKeepsRecordWithoutMergeDeletingSource() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void saveHistory(boolean exit)");
        int end = source.indexOf("private void syncHistory()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing saveHistory(boolean)", method >= 0);
        assertTrue("saveHistory must treat an attached non-empty owner player as played content",
                body.contains("boolean hasPlayback = service() != null && isOwner() && !player().isEmpty();"));
        assertTrue("saveHistory must persist played content even before progress advances past zero",
                body.contains("if (!mHistory.canSave() && !hasPlayback) return;"));
        assertFalse("mobile playback save must not merge-delete existing history entries",
                body.contains("history.merge().save()"));
    }

    private static void assertNoPrematurePlaybackReveal(Path sourcePath, String source) {
        int playing = source.indexOf("protected void onPlayingChanged(boolean isPlaying)");
        int playingEnd = source.indexOf("protected void onSizeChanged", playing);
        String playingBody = playing >= 0 && playingEnd > playing ? source.substring(playing, playingEnd) : "";
        int time = source.indexOf("public void onTimeChanged(long time)");
        int timeEnd = source.indexOf("private void updatePlaybackHistoryPosition()", time);
        String timeBody = time >= 0 && timeEnd > time ? source.substring(time, timeEnd) : "";

        assertTrue(sourcePath + " is missing onPlayingChanged", playing >= 0);
        assertTrue(sourcePath + " is missing onTimeChanged", time >= 0);
        assertFalse("playing changes can arrive before first frame and must not hide loading", playingBody.contains("showPlaybackContent();"));
        assertFalse("time ticks can observe old playback and must not hide loading", timeBody.contains("showPlaybackContent();"));
    }

    private static void assertSeekProgressFallback(Path sourcePath, String source) {
        int field = source.indexOf("private Runnable mSeekProgressFallback;");
        int init = source.indexOf("mSeekProgressFallback = this::hideSeekProgressIfReady;");
        int started = source.indexOf("protected void onSeekStarted()");
        int show = source.indexOf("showProgress();", started);
        int remove = source.indexOf("App.removeCallbacks(mSeekProgressFallback);", show);
        int post = source.indexOf("App.post(mSeekProgressFallback, 500);", remove);
        int helper = source.indexOf("private void hideSeekProgressIfReady()");
        int readyGuard = source.indexOf("player().getPlaybackState() != Player.STATE_READY", helper);
        int reveal = source.indexOf("showPlaybackContent();", readyGuard);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyRemove = source.indexOf("mSeekProgressFallback", destroy);

        assertTrue(sourcePath + " is missing mSeekProgressFallback", field >= 0);
        assertTrue("seek fallback runnable must be initialized", init > field);
        assertTrue("seek must show loading before scheduling the READY fallback", show > started && remove > show && post > remove);
        assertTrue("seek fallback must only clear loading once playback is READY", readyGuard > helper && reveal > readyGuard);
        assertTrue("seek fallback callback must be removed on destroy", destroyRemove > destroy);
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

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
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
