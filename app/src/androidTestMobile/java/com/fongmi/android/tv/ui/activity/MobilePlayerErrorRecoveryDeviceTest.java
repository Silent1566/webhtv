package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.Player;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class MobilePlayerErrorRecoveryDeviceTest {
    private static final String ERROR = "上一条线路播放失败，请重试或切换播放器";

    @Test
    public void videoRecoveryClearsRealOverlayWithoutBufferingAndPreservesSeekProgress() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File fixture = new File(context.getCacheDir(), "mobile-error-recovery.mp4");
        assertTrue("Push the local MP4 fixture into the application cache before this test", fixture.isFile());
        int originalPlayer = PlayerSetting.getPlayer();
        PlayerSetting.putPlayer(PlayerSetting.EXO);
        Intent intent = new Intent(context, VideoActivity.class)
                .putExtra("key", SiteApi.PUSH)
                .putExtra("id", fixture.toURI().toString())
                .putExtra("name", "Mobile error recovery fixture")
                .putExtra("detail_mode", 0)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<VideoActivity> scenario = ActivityScenario.launch(intent)) {
            waitForReadyVideo(scenario);
            scenario.onActivity(activity -> {
                call(activity, "showError", new Class<?>[]{String.class}, ERROR);
                activity.onStateChanged(Player.STATE_IDLE);
                assertErrorVisible(activity, R.id.errorText);
            });
            capture(scenario, "video-error-before.png");
            scenario.onActivity(activity -> {
                activity.onStateChanged(Player.STATE_READY);
                assertErrorCleared(activity, R.id.errorText);
            });
            capture(scenario, "video-ready-after.png");
            scenario.onActivity(activity -> {
                call(activity, "showError", new Class<?>[]{String.class}, ERROR);
                set(activity, "mSeekProgressPending", true);
                set(activity, "mSeekProgressStartedAtMs", SystemClock.elapsedRealtime());
                activity.onStateChanged(Player.STATE_READY);
                assertErrorCleared(activity, R.id.errorText);
                assertEquals("READY must not close the pending seek window", true, get(activity, "mSeekProgressPending"));
                set(activity, "mSeekProgressPending", false);
            });
        } finally {
            PlayerSetting.putPlayer(originalPlayer);
        }
    }

    @Test
    public void liveRecoveryClearsRealOverlayWithoutBufferingButIdlePreservesFailure() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, LiveActivity.class).putExtra("empty", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<LiveActivity> scenario = ActivityScenario.launch(intent)) {
            long deadline = SystemClock.uptimeMillis() + 15_000;
            AtomicBoolean connected = new AtomicBoolean();
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> connected.set(activity.isServiceReady()));
                if (connected.get()) break;
                SystemClock.sleep(50);
            }
            assertTrue("Live playback service must connect", connected.get());
            scenario.onActivity(activity -> {
                call(activity, "showError", new Class<?>[]{String.class}, ERROR);
                activity.onStateChanged(Player.STATE_IDLE);
                assertErrorVisible(activity, R.id.error);
            });
            capture(scenario, "live-error-before.png");
            scenario.onActivity(activity -> {
                activity.onStateChanged(Player.STATE_READY);
                assertErrorCleared(activity, R.id.error);
            });
            capture(scenario, "live-ready-after.png");
        }
    }

    private static void waitForReadyVideo(ActivityScenario<VideoActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 30_000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                if (!activity.isServiceReady()) return;
                PlayerManager manager = (PlayerManager) call(activity, "player", new Class<?>[0]);
                if (!manager.isEmpty() && manager.getPlaybackState() == Player.STATE_READY
                        && !manager.isPlaying()) manager.getPlayer().play();
                ready.set(!manager.isEmpty() && manager.getPlaybackState() == Player.STATE_READY && manager.isPlaying());
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("Local video did not reach real READY and playing state");
    }

    private static void assertErrorVisible(Activity activity, int textId) {
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error).getVisibility());
        assertEquals(ERROR, ((TextView) activity.findViewById(textId)).getText().toString());
    }

    private static void assertErrorCleared(Activity activity, int textId) {
        assertEquals("Recovered playback must hide the previous line error", View.GONE,
                activity.findViewById(R.id.error).getVisibility());
        assertEquals("Recovered playback must clear the previous line text", "",
                ((TextView) activity.findViewById(textId)).getText().toString());
    }

    private static void capture(ActivityScenario<? extends Activity> scenario, String name) throws Exception {
        CountDownLatch drawn = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            View decor = activity.getWindow().getDecorView();
            decor.postOnAnimation(() -> decor.postOnAnimation(drawn::countDown));
        });
        assertTrue("UI changes must draw before capture", drawn.await(5, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Device screenshot must be available", image);
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "mobile-error-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            image.recycle();
        }
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method method = type.getDeclaredMethod(name, types);
                    method.setAccessible(true);
                    return method.invoke(target, arguments);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new NoSuchMethodException(name);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception error) {
            throw new AssertionError(name, error);
        }
    }
}
