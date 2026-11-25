package com.fongmi.android.tv.ui.activity;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobilePlayerErrorRecoveryTest {
    private static ClassLoader loader;

    @BeforeClass
    public static void compilePlaybackCallbacks() throws Exception {
        Path output = Files.createTempDirectory("mobile-player-error-recovery-");
        String[] arguments = new String[]{"-d", output.toString(), "", ""};
        for (int i = 0; i < 2; i++) {
            String activity = i == 0 ? "VideoActivity" : "LiveActivity";
            Path source = Path.of("src/mobile/java/com/fongmi/android/tv/ui/activity/" + activity + ".java");
            if (!Files.exists(source)) source = Path.of("app").resolve(source);
            String original = Files.readString(source, StandardCharsets.UTF_8);
            String methods = method(original, "protected void onStateChanged(int state)")
                    + method(original, "private void showError(String text)")
                    + method(original, "private void hideError()");
            if (i == 0) methods += method(original, "private void showPlaybackContent()");
            Path generated = output.resolve(activity + "Recovery.java");
            Files.writeString(generated, "public class " + activity + "Recovery {\n" + UI_BOUNDARY + methods + "\n}");
            arguments[i + 2] = generated.toString();
        }
        Object compiler = Class.forName("javax.tools.ToolProvider").getMethod("getSystemJavaCompiler").invoke(null);
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = (Integer) Class.forName("javax.tools.Tool")
                .getMethod("run", InputStream.class, OutputStream.class, OutputStream.class, String[].class)
                .invoke(compiler, null, diagnostics, diagnostics, arguments);
        assertEquals(diagnostics.toString(StandardCharsets.UTF_8), 0, result);
        loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, MobilePlayerErrorRecoveryTest.class.getClassLoader());
    }

    @Test
    public void videoReadyClearsThePreviousLineErrorWithoutBuffering() throws Exception {
        assertReadyClearsError("VideoActivity");
    }

    @Test
    public void liveReadyClearsThePreviousLineErrorWithoutBuffering() throws Exception {
        assertReadyClearsError("LiveActivity");
    }

    @Test
    public void videoReadyClearsTheErrorEvenWhileSeekProgressMustRemainVisible() throws Exception {
        Recovery video = new Recovery("VideoActivity");
        video.fail();
        video.target.getClass().getField("seekPending").setBoolean(video.target, true);
        video.target.getClass().getField("progressVisible").setBoolean(video.target, true);
        video.state(3);
        assertFalse("READY must clear the error independently of seek progress", video.errorVisible());
        assertEquals("", video.errorText());
        assertTrue("READY must preserve the pending seek spinner", video.progressVisible());
    }

    @Test
    public void videoFailureRemainsVisibleUntilPlaybackRecovers() throws Exception {
        assertIdlePreservesError("VideoActivity");
    }

    @Test
    public void liveFailureRemainsVisibleUntilPlaybackRecovers() throws Exception {
        assertIdlePreservesError("LiveActivity");
    }

    private static void assertReadyClearsError(String activity) throws Exception {
        Recovery recovery = new Recovery(activity);
        recovery.fail();
        assertTrue("The previous line failure must be visible before recovery", recovery.errorVisible());
        assertEquals("failed line", recovery.errorText());
        recovery.state(3);
        assertFalse(activity + " READY must hide the previous line error", recovery.errorVisible());
        assertEquals("READY must clear the previous failure text", "", recovery.errorText());
        assertFalse("READY must hide ordinary loading progress", recovery.progressVisible());
    }

    private static void assertIdlePreservesError(String activity) throws Exception {
        Recovery recovery = new Recovery(activity);
        recovery.fail();
        recovery.state(1);
        assertTrue("An unrecovered failure must remain visible", recovery.errorVisible());
        assertEquals("failed line", recovery.errorText());
        assertFalse("A failed player must not keep showing loading progress", recovery.progressVisible());
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing production callback: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, i + 1) + "\n";
        }
        throw new AssertionError("Unclosed production callback: " + signature);
    }

    private static final class Recovery {
        final Object target;

        Recovery(String activity) throws Exception {
            target = loader.loadClass(activity + "Recovery").getConstructor().newInstance();
        }

        void fail() throws Exception {
            target.getClass().getMethod("fail", String.class).invoke(target, "failed line");
        }

        void state(int state) throws Exception {
            target.getClass().getMethod("state", int.class).invoke(target, state);
        }

        boolean errorVisible() throws Exception {
            return (Boolean) target.getClass().getMethod("errorVisible").invoke(target);
        }

        String errorText() throws Exception {
            return (String) target.getClass().getMethod("errorText").invoke(target);
        }

        boolean progressVisible() throws Exception {
            return target.getClass().getField("progressVisible").getBoolean(target);
        }
    }

    // Execute the unchanged production callbacks with only Android UI and unrelated playback effects replaced.
    private static final String UI_BOUNDARY = """
            static class View { static final int VISIBLE = 0, GONE = 8; }
            static class TextView {
                int visibility = View.GONE;
                String text = "";
                void setVisibility(int value) { visibility = value; }
                void setText(String value) { text = value; }
            }
            static class Widget { TextView error = new TextView(), errorText = new TextView(); }
            static class Binding { Widget widget = new Widget(); }
            static class Player {
                static final int STATE_BUFFERING = 2, STATE_READY = 3, STATE_ENDED = 4;
                void reset() {}
            }
            static class ShortDramaQueue { boolean shouldSuppressLegacyAutoAdvance() { return false; } }
            Binding mBinding = new Binding();
            Object mPendingKaraokeResult;
            boolean mKaraokeResultShown, mShortDramaQueueSessionActive, mFailedThisSession;
            ShortDramaQueue mShortDramaQueue = new ShortDramaQueue();
            public boolean seekPending, progressVisible;
            public void fail(String message) { showError(message); }
            public void state(int state) { onStateChanged(state); }
            public boolean errorVisible() { return mBinding.widget.error.visibility == View.VISIBLE; }
            public String errorText() { return mBinding.widget.error.text + mBinding.widget.errorText.text; }
            void hideProgress() { progressVisible = false; }
            void showProgress() { throw new AssertionError("This recovery scenario must not require BUFFERING"); }
            boolean canHideSeekProgress() { return !seekPending; }
            Player player() { return new Player(); }
            void showDetailContent() {}
            void renewAutoDanmaku() {}
            boolean applyPendingResumeSeek() { return false; }
            void checkControl() {}
            void refreshLyrics() {}
            void applyShortDramaMode() {}
            void requestIntroSkipPlan() {}
            void applyAutoIntroSkip() {}
            void setAdFeedbackVisible() {}
            void beginShortDramaQueue() {}
            void scheduleShortDramaEndedFallback() {}
            void checkEnded(boolean ignored) {}
            void checkEnded() {}
            boolean syncPiPForPlaybackMode() { return false; }
            void updatePlayControl(boolean playing, boolean audio) {}
            void updatePlayControl(boolean playing) {}
            void resetLineFallback() {}
            """;
}
