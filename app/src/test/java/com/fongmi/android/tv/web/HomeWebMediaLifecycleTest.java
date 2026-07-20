package com.fongmi.android.tv.web;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeWebMediaLifecycleTest {

    @Test
    public void controllerPausesPageMediaAtLifecycleBoundaries() throws Exception {
        String source = readMainSource("HomeWebController.java");
        String onPause = methodBody(source, "public void onPause()", "public boolean beginInlineEvaluation()");
        String endInlineEvaluation = methodBody(source, "public void endInlineEvaluation(boolean active)", "public void destroy()");

        assertTrue("Activity pause must pause HTML media before pausing WebView",
                ordered(onPause, "pausePageMedia();", "webView.onPause();"));
        assertTrue("Inline resolver cleanup must pause HTML media before pausing WebView",
                ordered(endInlineEvaluation, "pausePageMedia();", "webView.onPause();"));
        assertTrue("Page media cleanup must cover video and audio elements",
                source.contains("video,audio") && source.contains("media.pause()"));
    }

    @Test
    public void nativePlaybackRoutesPauseWebHomeBeforeLaunch() throws Exception {
        String source = readMainSource("HomeWebBridge.java");
        assertNativeRoutePausesMedia(source, "private String playUrl(JsonObject payload)", "private String playVod(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playVod(JsonObject payload)", "private String playVodInline(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playVodInline(JsonObject payload)", "private String preloadArtwork(JsonObject payload)");
        assertNativeRoutePausesMedia(source, "private String playPan(JsonObject payload)", "private String stripPush(String url)");
    }

    @Test
    public void nativePlaybackLaunchStateRejectsDestroyedAndDuplicateCallbacks() {
        HomeWebController.NativePlaybackLaunchState state = new HomeWebController.NativePlaybackLaunchState();
        HomeWebController.NativePlaybackLaunchState cancelled = new HomeWebController.NativePlaybackLaunchState();

        assertTrue(state.tryLaunch(false, false, false));
        assertFalse(state.tryLaunch(false, false, false));
        cancelled.cancel();
        assertFalse(cancelled.tryLaunch(false, false, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(true, false, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(false, true, false));
        assertFalse(new HomeWebController.NativePlaybackLaunchState().tryLaunch(false, false, true));
    }

    @Test
    public void controllerDestroyCancelsPendingNativePlaybackBeforeWebViewDestroy() throws Exception {
        String source = readMainSource("HomeWebController.java");
        String destroy = methodBody(source, "public void destroy()", "private void consumeExtensionReload()");

        assertTrue(ordered(destroy, "destroyed = true;", "cancelPendingNativePlaybacks();"));
        assertTrue(ordered(destroy, "cancelPendingNativePlaybacks();", "webView.destroy();"));
    }

    private static void assertNativeRoutePausesMedia(String source, String start, String end) {
        String body = methodBody(source, start, end);
        assertTrue(start + " must pause WebHome media before VideoActivity launch",
                ordered(body, "controller.prepareNativePlayback", "VideoActivity.start"));
    }

    private static boolean ordered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, Math.max(0, firstIndex + first.length()));
        return firstIndex >= 0 && secondIndex > firstIndex;
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }

    private static String readMainSource(String fileName) throws Exception {
        Path root = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "web");
        if (!Files.exists(root)) root = Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "web");
        return Files.readString(root.resolve(fileName), StandardCharsets.UTF_8);
    }
}
