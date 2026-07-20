package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerPlaybackRegressionSourceTest {

    @Test
    public void customPlayerButtonOrderPreservesSpacerAndRefreshesVisibleFocusChain() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "setting", "PlayerButtonSetting.java");
        int applyOrder = source.indexOf("public static void applyOrder(ViewGroup container, Map<String, View> views)");
        int applyVisibility = source.indexOf("public static void applyVisibility(Map<String, View> views)");
        int reorder = source.indexOf("private static void reorderViewsPreservingUnmanagedChildren");
        int focus = source.indexOf("private static void updateFocusNavigation(Map<String, View> views)");

        assertTrue("custom order must preserve the spacer and other unmanaged controls in their original child slots",
                applyOrder >= 0
                        && source.indexOf("reorderViewsPreservingUnmanagedChildren(container, ordered);", applyOrder) > applyOrder
                        && reorder > applyOrder
                        && source.indexOf("container.removeAllViews();", reorder) > reorder
                        && source.indexOf("if (managed.contains(child))", reorder) > reorder);
        assertTrue("visibility refreshes must rebuild focus from the container's currently visible children",
                applyVisibility >= 0
                        && source.indexOf("updateFocusNavigation(views);", applyVisibility) > applyVisibility
                        && focus >= 0
                        && source.indexOf("view.getVisibility() == View.VISIBLE && view.isFocusable()", focus) > focus
                        && source.indexOf("current.setNextFocusLeftId(prev == null ? View.NO_ID : prev.getId());", focus) > focus
                        && source.indexOf("current.setNextFocusRightId(next == null ? View.NO_ID : next.getId());", focus) > focus);
    }

    @Test
    public void mediaSessionStopStopsPlaybackWithoutExitingThePlaybackScreen() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "service", "PlaybackService.java");
        int wrap = source.indexOf("private ForwardingPlayer wrap(Player base)");
        int stop = source.indexOf("public void stop()", wrap);
        int nextOverride = source.indexOf("@NonNull", stop);

        assertTrue("PlaybackService must keep the MediaSession stop override available for regression checking",
                wrap >= 0 && stop > wrap && nextOverride > stop);
        String stopOverride = source.substring(stop, nextOverride);
        assertTrue("MediaSession STOP must stop and clear playback without invoking the navigation callback",
                stopOverride.contains("stopAndClear();"));
        assertFalse("MediaSession STOP must not be treated as an explicit request to exit the playback Activity",
                stopOverride.contains("dispatchStop();"));
    }

    @Test
    public void mobileFullscreenExitRebindsEpisodesAfterOrientationSettles() throws Exception {
        String source = readMobileJava("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int refresh = source.indexOf("private void refreshEpisodeLayoutAfterFullscreen()");
        int configuration = source.indexOf("public void onConfigurationChanged(@NonNull Configuration newConfig)");

        assertTrue("fullscreen exit must schedule an episode rebind after the embedded layout is restored",
                restore >= 0
                        && source.indexOf("mBinding.episode.post(this::refreshEpisodeLayoutAfterFullscreen);", restore) > restore
                        && source.indexOf("mBinding.episode.postDelayed(this::refreshEpisodeLayoutAfterFullscreen, 180);", restore) > restore);
        assertTrue("episode refresh must rebuild orientation-dependent spans and rebind stale first-screen holders",
                refresh > restore
                        && source.indexOf("updateEpisodeLayout(mEpisodeAdapter.getItems());", refresh) > refresh
                        && source.indexOf("mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());", refresh) > refresh
                        && source.indexOf("mBinding.episode.requestLayout();", refresh) > refresh);
        assertTrue("configuration completion must run the same refresh in case rotation settles after the delayed callback",
                configuration > refresh
                        && source.indexOf("if (!isFullscreen()) refreshEpisodeLayoutAfterFullscreen();", configuration) > configuration);
    }

    private static String readMainJava(String... parts) throws Exception {
        return read(Path.of("src", "main", "java"), Path.of("app", "src", "main", "java"), parts);
    }

    private static String readMobileJava(String... parts) throws Exception {
        return read(Path.of("src", "mobile", "java"), Path.of("app", "src", "mobile", "java"), parts);
    }

    private static String read(Path moduleRoot, Path workspaceRoot, String... parts) throws Exception {
        Path path = moduleRoot;
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = workspaceRoot;
            for (String part : parts) path = path.resolve(part);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
