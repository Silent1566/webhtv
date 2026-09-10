package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TV Exo 的加载圈必须由同一条状态闭环控制：seek 未完成时不能被旧 READY 读数收掉，
 * 而遗漏归属回调时仍要能从真实播放器状态自愈收口。
 */
public class VideoActivityLoadingProgressSourceTest {

    private static final String SOURCE =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    @Test
    public void seekMarksPendingBeforeTheTrafficTickerCanRun() throws Exception {
        String body = methodBody(read(), "protected void onSeekStarted()", "public void onSubtitleClick()");

        assertTrue("seek must enter the pending state", body.contains("mSeekProgressPending = true;"));
        assertTrue("seek start time must be recorded before showing progress",
                body.contains("mSeekProgressStartedAtMs = SystemClock.elapsedRealtime();"));
        assertTrue("pending state must be recorded before the ticker is armed",
                body.indexOf("mSeekProgressPending = true;") < body.indexOf("showProgress();"));
    }

    @Test
    public void trafficFallbackCannotHideAnOutstandingSeek() throws Exception {
        String source = read();
        String body = methodBody(source, "private void hidePlaybackProgressIfStale()", "protected void onSeekStarted()");

        assertTrue("traffic fallback must defer while seek is pending",
                body.contains("canHideSeekProgress()"));
        assertTrue("TV fallback must remain scoped to the active owner",
                body.contains("if (!isOwner()) return;"));
    }

    @Test
    public void seekFallbackRequiresReadyAndHandlesActiveLoadingAndCanRetry() throws Exception {
        String source = read();
        String body = methodBody(source, "private void hideSeekProgressIfReady()", "private void hidePlaybackProgressIfStale()");
        String gate = methodBody(source, "private boolean canHideSeekProgress()", "private void scheduleSeekProgressFallback()");

        assertTrue("seek fallback must inspect the actual loading state",
                gate.contains("player().isLoading()"));
        assertTrue("active playback may close the seek spinner while background loading continues",
                gate.contains("player().isPlaying()"));
        assertTrue("seek fallback must retry while the player is still loading",
                body.contains("scheduleSeekProgressFallback("));
        assertTrue("seek fallback must eventually close the pending window",
                body.contains("mSeekProgressPending = false;") && body.contains("showPlaybackContent();"));
    }

    @Test
    public void readyStateUsesTheSameSeekCloseGate() throws Exception {
        String source = read();
        int state = source.indexOf("protected void onStateChanged(int state)");
        int ready = source.indexOf("case Player.STATE_READY:", state);
        int ended = source.indexOf("case Player.STATE_ENDED:", ready);
        assertTrue("must find TV state handler", state >= 0);
        assertTrue("must find READY branch", ready > state);
        assertTrue("must find ENDED branch", ended > ready);

        String readyBody = source.substring(ready, ended);
        assertTrue("READY must use the pending-seek close gate",
                readyBody.contains("canHideSeekProgress()"));
        assertTrue("READY must retain progress while the gate is closed",
                readyBody.contains("showProgress();"));
    }

    @Test
    public void hidingProgressClearsPendingSeekState() throws Exception {
        String body = methodBody(read(), "private void hideProgress()", "private void showPlaybackContent()");

        assertTrue("all explicit progress hides must cancel the seek window",
                body.contains("mSeekProgressPending = false;")
                        && body.contains("mSeekProgressStartedAtMs = 0;"));
    }

    @Test
    public void lateControllerReadyReconciliationClosesTheStartupSpinner() throws Exception {
        String body = methodBody(read(), "protected void onControllerReadyReconciled()", "protected void onPrepare()");

        assertTrue("late READY delivery must close the TV playback spinner",
                body.contains("showPlaybackContent();"));
        assertTrue("late READY delivery must retain the seek gate",
                body.contains("canHideSeekProgress()"));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue("cannot locate " + startMarker, start >= 0);
        assertTrue("cannot locate end of " + startMarker, end > start);
        return source.substring(start, end);
    }

    private static String read() throws Exception {
        Path direct = Path.of(SOURCE);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(SOURCE.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
