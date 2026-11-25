package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlaybackTrace;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = CustomKeyDownCecInputTest.TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomKeyDownCecInputTest {
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP;

    @Test
    public void hdmiShortLeftAndRightCommitTenSecondsAfter250ms() {
        for (int code : new int[]{LEFT, RIGHT}) {
            VodHost host = vod();
            burst(host, 100, code);
            drain(249);
            assertTrue(host.seeks.isEmpty());
            drain(1);
            assertEquals(Arrays.asList(code == LEFT ? -10_000L : 10_000L), host.seeks);
            drain(250);
            assertEquals(1, host.seeks.size());
        }
    }

    @Test
    public void hdmiHeldDirectionsAccumulateChangedDownTimesWithoutEarlyCommit() {
        for (int code : new int[]{LEFT, RIGHT}) {
            VodHost host = vod();
            send(host, 100, KeyEvent.ACTION_DOWN, code, 0);
            send(host, 200, KeyEvent.ACTION_DOWN, code, 1);
            send(host, 300, KeyEvent.ACTION_DOWN, code, 2);
            drain(500);
            assertTrue(host.seeks.isEmpty());
            assertEquals(Arrays.asList(code == LEFT ? -10_000L : 10_000L,
                    code == LEFT ? -20_000L : 20_000L,
                    code == LEFT ? -30_000L : 30_000L), host.previews);
            send(host, 400, KeyEvent.ACTION_UP, code, 0);
            drain(250);
            assertEquals(Arrays.asList(code == LEFT ? -30_000L : 30_000L), host.seeks);
        }
    }

    @Test
    public void hdmiBurstsAndNewHoldMoveDeadlineToLastRelease() {
        VodHost host = vod();
        burst(host, 100, RIGHT);
        drain(100);
        send(host, 200, KeyEvent.ACTION_DOWN, RIGHT, 0);
        drain(300);
        assertTrue(host.seeks.isEmpty());
        send(host, 300, KeyEvent.ACTION_UP, RIGHT, 0);
        drain(249);
        assertTrue(host.seeks.isEmpty());
        drain(1);
        assertEquals(Arrays.asList(20_000L), host.seeks);
    }

    @Test
    public void wrongKeyDeviceSourceAndOlderEventsCannotReleaseOrExtendSeek() {
        VodHost host = vod();
        send(host, 100, KeyEvent.ACTION_DOWN, RIGHT, 0);
        send(host, 300, KeyEvent.ACTION_DOWN, RIGHT, 1);
        host.input.onKeyDown(event(350, 350, KeyEvent.ACTION_DOWN, RIGHT, 2, 2, InputDevice.SOURCE_HDMI, 0));
        host.input.onKeyDown(event(350, 350, KeyEvent.ACTION_DOWN, RIGHT, 2, -1, InputDevice.SOURCE_KEYBOARD, 0));
        host.input.onKeyDown(event(350, 350, KeyEvent.ACTION_UP, RIGHT, 0, 2, InputDevice.SOURCE_HDMI, 0));
        host.input.onKeyDown(event(350, 350, KeyEvent.ACTION_UP, RIGHT, 0, -1, InputDevice.SOURCE_KEYBOARD, 0));
        send(host, 350, KeyEvent.ACTION_UP, LEFT, 0);
        send(host, 200, KeyEvent.ACTION_DOWN, RIGHT, 2);
        send(host, 200, KeyEvent.ACTION_UP, RIGHT, 0);
        send(host, 50, KeyEvent.ACTION_DOWN, RIGHT, 0);
        drain(250);
        assertTrue(host.seeks.isEmpty());
        send(host, 400, KeyEvent.ACTION_UP, RIGHT, 0);
        drain(250);
        assertEquals(Arrays.asList(20_000L), host.seeks);
    }

    @Test
    public void orphanCanceledAndLateHdmiSeekEventsNeverResurrectBurst() {
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            VodHost host = vod();
            send(host, 50, KeyEvent.ACTION_DOWN, RIGHT, 1);
            send(host, 60, KeyEvent.ACTION_UP, RIGHT, 0);
            send(host, 100, KeyEvent.ACTION_DOWN, RIGHT, 0);
            host.input.onKeyDown(event(200, 200, action, RIGHT, 1, -1, InputDevice.SOURCE_HDMI, KeyEvent.FLAG_CANCELED));
            send(host, 300, KeyEvent.ACTION_DOWN, RIGHT, 2);
            send(host, 400, KeyEvent.ACTION_UP, RIGHT, 0);
            drain(250);
            assertTrue(host.seeks.isEmpty());
            assertEquals(1, host.cancels);
        }
    }

    @Test
    public void oldReleaseAndRepeatDoNotOwnReplacementPress() {
        VodHost host = vod();
        burst(host, 100, RIGHT);
        send(host, 300, KeyEvent.ACTION_DOWN, RIGHT, 0);
        send(host, 120, KeyEvent.ACTION_UP, RIGHT, 0);
        send(host, 150, KeyEvent.ACTION_DOWN, RIGHT, 2);
        drain(250);
        assertTrue(host.seeks.isEmpty());
        send(host, 400, KeyEvent.ACTION_UP, RIGHT, 0);
        drain(250);
        assertEquals(Arrays.asList(20_000L), host.seeks);
    }

    @Test
    public void hdmiSessionReplacementAndExplicitCancellationRejectLateInput() {
        VodHost host = vod();
        send(host, 100, KeyEvent.ACTION_DOWN, RIGHT, 0);
        host.trace.begin();
        send(host, 200, KeyEvent.ACTION_DOWN, RIGHT, 1);
        send(host, 300, KeyEvent.ACTION_UP, RIGHT, 0);
        drain(250);
        assertTrue(host.seeks.isEmpty());
        burst(host, 400, RIGHT);
        host.trace.begin();
        drain(250);
        assertTrue(host.seeks.isEmpty());
        send(host, 500, KeyEvent.ACTION_DOWN, RIGHT, 0);
        host.input.cancelSeek();
        send(host, 600, KeyEvent.ACTION_DOWN, RIGHT, 1);
        send(host, 700, KeyEvent.ACTION_UP, RIGHT, 0);
        drain(250);
        assertTrue(host.seeks.isEmpty());
        burst(host, 800, RIGHT);
        drain(250);
        assertEquals(Arrays.asList(10_000L), host.seeks);
    }

    @Test
    public void nonHdmiStillRequiresIdenticalDownTimeAndSource() {
        VodHost host = vod();
        host.input.onKeyDown(event(100, 100, KeyEvent.ACTION_DOWN, RIGHT, 0, 1, InputDevice.SOURCE_KEYBOARD, 0));
        host.input.onKeyDown(event(200, 200, KeyEvent.ACTION_DOWN, RIGHT, 1, 1, InputDevice.SOURCE_KEYBOARD, 0));
        host.input.onKeyDown(event(100, 250, KeyEvent.ACTION_UP, RIGHT, 0, 1, InputDevice.SOURCE_HDMI, 0));
        host.input.onKeyDown(event(200, 300, KeyEvent.ACTION_UP, RIGHT, 0, 1, InputDevice.SOURCE_KEYBOARD, 0));
        drain(250);
        assertTrue(host.seeks.isEmpty());
        host.input.onKeyDown(event(100, 400, KeyEvent.ACTION_UP, RIGHT, 0, 1, InputDevice.SOURCE_KEYBOARD, 0));
        drain(250);
        assertEquals(Arrays.asList(10_000L), host.seeks);
    }

    @Test
    public void ordinaryHdmiLiveNavigatesOnceWithoutSeekingOrFalseLineSwitch() {
        CustomKeyDownSeekOwnershipTest.LiveHost host = Robolectric.buildActivity(CustomKeyDownSeekOwnershipTest.LiveHost.class).setup().get();
        host.trace.begin();
        host.seekable = false;
        host.input = CustomKeyDownLive.create(host);
        host.input.onKeyDown(hdmi(100, KeyEvent.ACTION_DOWN, RIGHT, 0));
        host.input.onKeyDown(hdmi(200, KeyEvent.ACTION_DOWN, RIGHT, 1));
        host.input.onKeyDown(hdmi(150, KeyEvent.ACTION_UP, RIGHT, 0));
        assertEquals(0, host.rights);
        host.input.onKeyDown(hdmi(300, KeyEvent.ACTION_UP, RIGHT, 0));
        assertEquals(1, host.rights);
        host.input.onKeyDown(hdmi(400, KeyEvent.ACTION_DOWN, LEFT, 0));
        host.input.onKeyDown(event(500, 500, KeyEvent.ACTION_UP, LEFT, 0, -1, InputDevice.SOURCE_HDMI, KeyEvent.FLAG_CANCELED));
        host.input.onKeyDown(hdmi(600, KeyEvent.ACTION_UP, LEFT, 0));
        host.input.onKeyDown(hdmi(700, KeyEvent.ACTION_UP, RIGHT, 0));
        drain(250);
        assertEquals(1, host.rights);
        assertEquals(0, host.lefts);
        assertTrue(host.seeks.isEmpty());
    }

    @Test
    public void hdmiUpShortAndIndependentShortPairsRemainNavigation() {
        VodHost host = vod();
        for (int i = 0; i < 3; i++) burst(host, 100 + i * 1000, UP);
        assertEquals(3, host.ups);
        assertEquals(0, host.speedUps);
    }

    @Test
    public void hdmiUpBoostsAtExactTimeoutOnceAndReleaseNeverNavigates() {
        VodHost host = vod();
        long threshold = 100 + ViewConfiguration.getLongPressTimeout();
        send(host, 100, KeyEvent.ACTION_DOWN, UP, 0);
        host.input.cancelSeek(); // VideoActivity does this on every UP-key DOWN.
        send(host, threshold - 1, KeyEvent.ACTION_DOWN, UP, 1);
        assertEquals(0, host.speedUps);
        host.input.cancelSeek();
        send(host, threshold, KeyEvent.ACTION_DOWN, UP, 2);
        assertEquals(1, host.speedUps);
        assertTrue(host.input.isChangingSpeed());
        send(host, threshold + 100, KeyEvent.ACTION_DOWN, UP, 3);
        assertEquals(1, host.speedUps);
        send(host, threshold + 200, KeyEvent.ACTION_UP, UP, 0);
        assertEquals(1, host.speedEnds);
        assertEquals(0, host.ups);
        assertFalse(host.input.isChangingSpeed());
        send(host, threshold + 300, KeyEvent.ACTION_DOWN, UP, 4);
        send(host, threshold + 400, KeyEvent.ACTION_UP, UP, 0);
        assertEquals(1, host.speedUps);
        assertEquals(0, host.ups);
    }

    @Test
    public void hdmiUpRequiresOwnedInitialDownAndMatchingOrderedEvents() {
        VodHost host = vod();
        long timeout = ViewConfiguration.getLongPressTimeout();
        send(host, 1000, KeyEvent.ACTION_DOWN, UP, 1);
        send(host, 1100, KeyEvent.ACTION_UP, UP, 0);
        assertEquals(0, host.speedUps);
        assertEquals(0, host.ups);
        send(host, 2000, KeyEvent.ACTION_DOWN, UP, 0);
        host.input.onKeyDown(event(2001, 2001, KeyEvent.ACTION_DOWN, UP, 0, -1, InputDevice.SOURCE_KEYBOARD, 0));
        host.input.onKeyDown(event(2001, 2001 + timeout, KeyEvent.ACTION_DOWN, UP, 1, -1, InputDevice.SOURCE_KEYBOARD, 0));
        assertEquals(0, host.speedUps);
        host.input.onKeyDown(event(2000 + timeout, 2000 + timeout, KeyEvent.ACTION_DOWN, UP, 1, 2, InputDevice.SOURCE_HDMI, 0));
        host.input.onKeyDown(event(2000 + timeout, 2000 + timeout, KeyEvent.ACTION_DOWN, UP, 1, -1, InputDevice.SOURCE_KEYBOARD, 0));
        send(host, 1900, KeyEvent.ACTION_DOWN, UP, 1);
        send(host, 1900, KeyEvent.ACTION_UP, UP, 0);
        assertEquals(0, host.speedUps);
        assertEquals(0, host.ups);
        send(host, 2000 + timeout, KeyEvent.ACTION_DOWN, UP, 1);
        assertEquals(1, host.speedUps);
        host.input.onKeyDown(event(2001 + timeout, 2001 + timeout, KeyEvent.ACTION_UP, UP, 0, 2, InputDevice.SOURCE_HDMI, 0));
        send(host, 2000, KeyEvent.ACTION_UP, UP, 0);
        assertTrue(host.input.isChangingSpeed());
        send(host, 2100 + timeout, KeyEvent.ACTION_UP, UP, 0);
        assertEquals(1, host.speedEnds);
        assertEquals(0, host.ups);
    }

    @Test
    public void canceledHdmiUpBeforeAndAfterBoostClearsHold() {
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            for (boolean boost : new boolean[]{false, true}) {
                VodHost host = vod();
                send(host, 100, KeyEvent.ACTION_DOWN, UP, 0);
                long time = 100 + ViewConfiguration.getLongPressTimeout();
                if (boost) send(host, time, KeyEvent.ACTION_DOWN, UP, 1);
                host.input.onKeyDown(event(time + 1, time + 1, action, UP, 2, -1, InputDevice.SOURCE_HDMI, KeyEvent.FLAG_CANCELED));
                send(host, time + 2, KeyEvent.ACTION_DOWN, UP, 3);
                send(host, time + 3, KeyEvent.ACTION_UP, UP, 0);
                assertEquals(boost ? 1 : 0, host.speedUps);
                assertEquals(boost ? 1 : 0, host.speedEnds);
                assertEquals(0, host.ups);
                assertFalse(host.input.isChangingSpeed());
            }
        }
    }

    @Test
    public void otherKeyReleaseSpeedAndFullscreenExitPreventHdmiBoostRestart() {
        for (int clear = 0; clear < 3; clear++) {
            for (boolean boost : new boolean[]{false, true}) {
                VodHost host = vod();
                send(host, 100, KeyEvent.ACTION_DOWN, UP, 0);
                long time = 100 + ViewConfiguration.getLongPressTimeout();
                if (boost) send(host, time, KeyEvent.ACTION_DOWN, UP, 1);
                if (clear == 0) send(host, time + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0);
                else if (clear == 1) host.input.releaseSpeed();
                else host.input.setFull(false);
                send(host, time + 2, KeyEvent.ACTION_DOWN, UP, 2);
                send(host, time + 3, KeyEvent.ACTION_UP, UP, 0);
                assertEquals(boost ? 1 : 0, host.speedUps);
                assertEquals(boost ? 1 : 0, host.speedEnds);
                assertEquals(0, host.ups);
                assertFalse(host.input.isChangingSpeed());
            }
        }
    }

    @Test
    public void ordinaryFlaggedLongPressStillBoostsWithoutInitialDown() {
        VodHost host = vod();
        host.input.onKeyDown(event(100, 600, KeyEvent.ACTION_DOWN, UP, 1, 1, InputDevice.SOURCE_KEYBOARD, KeyEvent.FLAG_LONG_PRESS));
        host.input.onKeyDown(event(100, 700, KeyEvent.ACTION_UP, UP, 0, 1, InputDevice.SOURCE_KEYBOARD, 0));
        assertEquals(1, host.speedUps);
        assertEquals(1, host.speedEnds);
        assertEquals(0, host.ups);
    }

    private static VodHost vod() {
        VodHost host = Robolectric.buildActivity(VodHost.class).setup().get();
        host.trace.begin();
        host.input = CustomKeyDownVod.create(host);
        host.input.setFull(true);
        return host;
    }

    private static void burst(VodHost host, long time, int code) {
        send(host, time, KeyEvent.ACTION_DOWN, code, 0);
        send(host, time + 10, KeyEvent.ACTION_UP, code, 0);
    }

    private static void send(VodHost host, long time, int action, int code, int repeat) {
        host.input.onKeyDown(hdmi(time, action, code, repeat));
    }

    private static KeyEvent hdmi(long time, int action, int code, int repeat) {
        return event(time, time, action, code, repeat, -1, InputDevice.SOURCE_HDMI, 0);
    }

    private static KeyEvent event(long downTime, long eventTime, int action, int code, int repeat, int device, int source, int flags) {
        return new KeyEvent(downTime, eventTime, action, code, repeat, 0, device, 0, flags | KeyEvent.FLAG_FROM_SYSTEM, source);
    }

    private static void drain(long ms) {
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(ms));
    }

    public static class TestApp extends App { @Override public void onCreate() { } }

    public static class VodHost extends Activity implements CustomKeyDownVod.Listener {
        final PlaybackTrace trace = new PlaybackTrace();
        final List<Long> seeks = new ArrayList<>();
        final List<Long> previews = new ArrayList<>();
        CustomKeyDownVod input;
        int cancels, speedUps, speedEnds, ups;
        @Override public boolean canSeek() { return true; }
        @Override public String getSeekSession() { return trace.current(); }
        @Override public void onSeeking(long time) { previews.add(time); }
        @Override public void onSeekEnd(long time) { seeks.add(time); input.reset(); }
        @Override public void onSeekCancel() { cancels++; }
        @Override public void onSpeedUp() { speedUps++; }
        @Override public void onSpeedEnd() { speedEnds++; }
        @Override public void onKeyUp() { ups++; }
        @Override public void onKeyDown() { }
        @Override public void onKeyCenter() { }
        @Override public void onSingleTap() { }
        @Override public void onDoubleTap() { }
    }
}
