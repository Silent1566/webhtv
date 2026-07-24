package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveActivityLayoutTest {

    @Test
    public void explicitLivePiPPreparesVideoBeforeEnteringSystemPiP() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int onPip = source.indexOf("private void onPiP()");
        int onPipEnd = source.indexOf("private void prepareLivePiPView()", onPip);
        String onPipBody = onPip >= 0 && onPipEnd > onPip ? source.substring(onPip, onPipEnd) : "";
        assertTrue(sourcePath + " is missing onPiP", onPip >= 0);
        assertTrue("explicit live PiP must prepare the entry path before scheduling system PiP",
                onPipBody.contains("preparePiP(\"panel\")")
                        && onPipBody.contains("prepareLivePiPView();")
                        && onPipBody.contains("scheduleLivePiPEntry();"));
        assertTrue("explicit live PiP preparation must run before the deferred entry is scheduled",
                onPipBody.indexOf("preparePiP(\"panel\")")
                        < onPipBody.indexOf("prepareLivePiPView();")
                        && onPipBody.indexOf("prepareLivePiPView();")
                        < onPipBody.indexOf("pipEntryPending = true;")
                        && onPipBody.indexOf("pipEntryPending = true;")
                        < onPipBody.indexOf("scheduleLivePiPEntry();"));
        assertFalse("explicit live PiP must not enter before the video layout is prepared",
                onPipBody.contains("mPiP.enter("));

        int prepare = source.indexOf("private void prepareLivePiPView()");
        int prepareEnd = source.indexOf("private void scheduleLivePiPEntry()", prepare);
        String prepareBody = prepare >= 0 && prepareEnd > prepare ? source.substring(prepare, prepareEnd) : "";
        assertTrue("live PiP preparation must expand the video container before entry",
                prepareBody.contains("setVideoView(true);")
                        && prepareBody.contains("mBinding.video.requestLayout();"));
        assertTrue("live PiP preparation must hide the embedded live chrome before entry",
                prepareBody.contains("mBinding.recycler.setVisibility(View.GONE);")
                        && prepareBody.contains("mBinding.navigation.setVisibility(View.GONE);"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP entry must wait for a completed layout pass",
                scheduleBody.contains("OneShotPreDrawListener.add"));

        int enterEnd = source.indexOf("private void restoreAfterFailedLivePiP()", enter);
        String enterBody = enter >= 0 && enterEnd > enter ? source.substring(enter, enterEnd) : "";
        assertTrue("executing the deferred entry must release its completed listener handle",
                enterBody.contains("pipEntryListener = null;")
                        && enterBody.indexOf("pipEntryListener = null;")
                        < enterBody.indexOf("pipEntryPending = false;"));
        assertTrue("live PiP must update the source view before entering",
                enterBody.contains("mPiP.update(this, mBinding.video)")
                        && enterBody.indexOf("mPiP.update(this, mBinding.video)")
                        < enterBody.indexOf("mPiP.enter("));
        assertTrue("failed live PiP entry must restore the pre-entry layout",
                enterBody.contains("restoreAfterFailedLivePiP();"));
    }

    @Test
    public void pendingLivePiPEntryIsCancelledWhenTheActivityStops() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("the pending pre-draw listener must be retained so it can be cancelled",
                source.contains("private OneShotPreDrawListener pipEntryListener;"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP scheduling must retain the one-shot listener",
                scheduleBody.contains("pipEntryListener = OneShotPreDrawListener.add"));

        int cancel = source.indexOf("private void cancelPendingLivePiP(boolean restoreUi)");
        int cancelEnd = source.indexOf("private void enterFullscreenLive()", cancel);
        String cancelBody = cancel >= 0 && cancelEnd > cancel ? source.substring(cancel, cancelEnd) : "";
        assertTrue("live PiP cancellation must remove the listener and clear pending state",
                cancelBody.contains("pipEntryListener.removeListener();")
                        && cancelBody.contains("pipEntryListener = null;")
                        && cancelBody.contains("pipEntryPending = false;"));
        assertTrue("cancelled live PiP must restore the prepared layout when requested",
                cancelBody.contains("if (restoreUi && !isInPictureInPictureMode()) restoreAfterFailedLivePiP();"));

        int onStop = source.indexOf("protected void onStop()");
        int onStopEnd = source.indexOf("protected void onBackInvoked()", onStop);
        String onStopBody = onStop >= 0 && onStopEnd > onStop ? source.substring(onStop, onStopEnd) : "";
        assertTrue("stopping the activity must cancel and restore a pending live PiP entry",
                onStopBody.contains("cancelPendingLivePiP(true);"));

        int onDestroy = source.indexOf("protected void onDestroy()");
        String onDestroyBody = onDestroy >= 0 ? source.substring(onDestroy) : "";
        assertTrue("destroying the activity must remove any pending live PiP listener without restoring UI",
                onDestroyBody.contains("cancelPendingLivePiP(false);"));

        int onPipChanged = source.indexOf("public void onPictureInPictureModeChanged(");
        int onPipChangedEnd = source.indexOf("private void setVideoView(", onPipChanged);
        String onPipChangedBody = onPipChanged >= 0 && onPipChangedEnd > onPipChanged
                ? source.substring(onPipChanged, onPipChangedEnd) : "";
        assertTrue("a successful system PiP transition must cancel any still-pending manual entry",
                onPipChangedBody.contains("if (isInPictureInPictureMode) cancelPendingLivePiP(false);"));
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }
}
