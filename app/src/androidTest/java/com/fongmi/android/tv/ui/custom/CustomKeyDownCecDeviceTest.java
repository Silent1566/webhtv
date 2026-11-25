package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Device-only framework/Looper evidence for #582. Run against a Leanback debug APK.
 * Direct tests use a real existing CastActivity as the attached Activity, but replace
 * the two listener references with a recording proxy. They prove input callbacks,
 * NOT playback. zRealPage uses unmodified Activity dispatch/listeners and a local MP4.
 * Reflection avoids linking Leanback-only classes from the shared androidTest source.
 * No physical HDMI transport, remote, or OEM event generation is exercised.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CustomKeyDownCecDeviceTest {
    private static final String TAG = "CEC582Device";
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP;
    private static final Instrumentation INSTR = InstrumentationRegistry.getInstrumentation();
    private static Activity activity;
    private static Class<?> inputClass;
    private static final String RUNTIME_KEY = "perf_exo_decoder_runtime_v1";
    private static SharedPreferences preferences;
    private static boolean runtimePresent;
    private static String runtimeValue;
    private static File runtimeBaseline;
    private Object input;
    private Recorder recorder;

    @BeforeClass public static void launchExistingActivity() throws Exception {
        preferences = INSTR.getTargetContext().getSharedPreferences(
                INSTR.getTargetContext().getPackageName() + "_preferences", 0);
        runtimePresent = preferences.contains(RUNTIME_KEY);
        Object value = preferences.getAll().get(RUNTIME_KEY);
        assertTrue("runtime key expected String or absent", !runtimePresent || value instanceof String);
        runtimeValue = (String) value;
        String baselineName = InstrumentationRegistry.getArguments().getString("cecRuntimeBaseline");
        if (baselineName != null) {
            assertEquals("ISSUE-582-CEC-DEVICE-runtime-baseline.json", baselineName);
            runtimeBaseline = new File(INSTR.getTargetContext().getCacheDir(), baselineName);
            JSONObject saved = new JSONObject(new String(Files.readAllBytes(runtimeBaseline.toPath()), StandardCharsets.UTF_8));
            runtimePresent = saved.getBoolean("present");
            assertEquals("string", saved.getString("type"));
            runtimeValue = runtimePresent ? saved.getString("value") : null;
        }
        inputClass = Class.forName("com.fongmi.android.tv.ui.custom.CustomKeyDownVod");
        activity = launch(null);
        Log.i(TAG, "runtime sdk=" + android.os.Build.VERSION.SDK_INT + " model=" + android.os.Build.MODEL
                + " abi=" + Arrays.toString(android.os.Build.SUPPORTED_ABIS)
                + " productionLoader=" + inputClass.getClassLoader());
    }

    @AfterClass public static void finishActivity() {
        try {
            if (activity != null) main(() -> activity.finish());
            INSTR.waitForIdleSync();
        } finally {
            if (preferences != null) {
                SharedPreferences.Editor edit = preferences.edit();
                if (runtimePresent) edit.putString(RUNTIME_KEY, runtimeValue);
                else edit.remove(RUNTIME_KEY);
                assertTrue("single runtime key restored to disk", edit.commit());
                assertEquals(runtimePresent, preferences.contains(RUNTIME_KEY));
                if (runtimePresent) assertEquals(runtimeValue, preferences.getString(RUNTIME_KEY, null));
                evidence("DATA_RESTORE", "runtimePresent=" + runtimePresent + " equality=true");
                if (runtimeBaseline != null) assertTrue("task baseline removed", runtimeBaseline.delete());
            }
        }
    }

    @Before public void freshDirectInput() {
        if (input != null) main(() -> invoke(input, "setFull", false));
        recorder = new Recorder();
        main(() -> {
            input = invokeStatic(inputClass, "create", activity);
            Class<?> listener = classFor("com.fongmi.android.tv.ui.custom.CustomKeyDownVod$Listener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                    (p, m, args) -> recorder.callback(m.getName(), args));
            setField(input, "listener", proxy);
            setField(getField(input, "seek"), "listener", proxy);
            invoke(input, "setFull", true);
        });
    }

    @After public void clearDirectInput() {
        if (input != null) main(() -> invoke(input, "setFull", false));
    }

    @Test public void aHdmiShortLeftAndRightCommitAfter250ms() throws Exception {
        for (int code : new int[]{LEFT, RIGHT}) {
            freshDirectInput();
            send(code, KeyEvent.ACTION_DOWN, 0);
            send(code, KeyEvent.ACTION_UP, 0);
            long release = SystemClock.uptimeMillis();
            assertFalse("no early commit", recorder.commit.await(150, TimeUnit.MILLISECONDS));
            assertTrue("commit by bounded deadline", recorder.commit.await(1500, TimeUnit.MILLISECONDS));
            assertEquals(Arrays.asList(code == LEFT ? -10000L : 10000L), recorder.seeks);
            assertTrue("App Handler delay >=250ms", recorder.commitAt - release >= 245);
            waitMain(300);
            assertEquals(1, recorder.seeks.size());
            evidence("HDMI_SHORT", "key=" + code + " seeks=" + recorder.seeks + " delayMs=" + (recorder.commitAt - release));
        }
    }

    @Test public void bHdmiHoldAndRapidBurstsUseLastRelease() throws Exception {
        for (int code : new int[]{LEFT, RIGHT}) {
            freshDirectInput();
            send(code, KeyEvent.ACTION_DOWN, 0);
            send(code, KeyEvent.ACTION_DOWN, 1);
            send(code, KeyEvent.ACTION_DOWN, 2);
            waitMain(300);
            assertTrue(recorder.seeks.isEmpty());
            assertEquals(Arrays.asList(code == LEFT ? -10000L : 10000L,
                    code == LEFT ? -20000L : 20000L, code == LEFT ? -30000L : 30000L), recorder.previews);
            send(code, KeyEvent.ACTION_UP, 0);
            waitMain(80);
            send(code, KeyEvent.ACTION_DOWN, 0);
            waitMain(300);
            assertTrue("new hold removes previous release deadline", recorder.seeks.isEmpty());
            send(code, KeyEvent.ACTION_UP, 0);
            assertTrue(recorder.commit.await(1500, TimeUnit.MILLISECONDS));
            assertEquals(Arrays.asList(code == LEFT ? -40000L : 40000L), recorder.seeks);
        }
        freshDirectInput();
        for (int n = 0; n < 4; n++) {
            send(RIGHT, KeyEvent.ACTION_DOWN, 0);
            send(RIGHT, KeyEvent.ACTION_UP, 0);
            if (n < 3) waitMain(40);
        }
        assertTrue(recorder.commit.await(1500, TimeUnit.MILLISECONDS));
        assertEquals(Arrays.asList(40000L), recorder.seeks);
        evidence("HDMI_HOLD_BURSTS", "left/right hold +/-40s; four rapid pairs one +40s commit");
    }

    @Test public void cStaleWrongDeviceSourceOrphanAndReplacementRejected() throws Exception {
        send(RIGHT, KeyEvent.ACTION_DOWN, 1);
        send(RIGHT, KeyEvent.ACTION_UP, 0);
        assertTrue(recorder.previews.isEmpty());
        long start = send(RIGHT, KeyEvent.ACTION_DOWN, 0);
        long last = send(RIGHT, KeyEvent.ACTION_DOWN, 1);
        raw(event(last + 1, last + 1, KeyEvent.ACTION_DOWN, RIGHT, 2, 7, InputDevice.SOURCE_HDMI, 0));
        raw(event(last + 1, last + 1, KeyEvent.ACTION_UP, RIGHT, 0, 7, InputDevice.SOURCE_HDMI, 0));
        raw(event(start, last + 1, KeyEvent.ACTION_DOWN, RIGHT, 2, -1, InputDevice.SOURCE_KEYBOARD, 0));
        raw(event(start, last + 1, KeyEvent.ACTION_UP, RIGHT, 0, -1, InputDevice.SOURCE_KEYBOARD, 0));
        raw(event(start - 1, start - 1, KeyEvent.ACTION_DOWN, RIGHT, 2, -1, InputDevice.SOURCE_HDMI, 0));
        raw(event(start - 1, start - 1, KeyEvent.ACTION_UP, RIGHT, 0, -1, InputDevice.SOURCE_HDMI, 0));
        send(LEFT, KeyEvent.ACTION_UP, 0);
        waitMain(300);
        assertTrue(recorder.seeks.isEmpty());
        assertEquals(Arrays.asList(10000L, 20000L), recorder.previews);
        send(RIGHT, KeyEvent.ACTION_UP, 0);
        send(RIGHT, KeyEvent.ACTION_DOWN, 0);
        raw(event(start, start, KeyEvent.ACTION_UP, RIGHT, 0, -1, InputDevice.SOURCE_HDMI, 0));
        raw(event(start, start, KeyEvent.ACTION_DOWN, RIGHT, 2, -1, InputDevice.SOURCE_HDMI, 0));
        waitMain(300);
        assertTrue(recorder.seeks.isEmpty());
        send(RIGHT, KeyEvent.ACTION_UP, 0);
        assertTrue(recorder.commit.await(1500, TimeUnit.MILLISECONDS));
        assertEquals(Arrays.asList(30000L), recorder.seeks);
        evidence("IDENTITY_STALE", "orphan/source/device/key/old replacement events rejected");
    }

    @Test public void dCancellationAndSessionReplacementRejectDeferredSeek() throws Exception {
        for (int mode = 0; mode < 5; mode++) {
            freshDirectInput();
            send(RIGHT, KeyEvent.ACTION_DOWN, 0);
            if (mode == 4) raw(hdmi(RIGHT, KeyEvent.ACTION_UP, 0, KeyEvent.FLAG_CANCELED));
            else send(RIGHT, KeyEvent.ACTION_UP, 0);
            final int clear = mode;
            main(() -> {
                if (clear == 0) recorder.session = "replacement";
                if (clear == 1) invoke(input, "cancelSeek");
                if (clear == 2) invoke(input, "setFull", false);
                if (clear == 3) recorder.canSeek = false;
                if (clear == 4) invoke(input, "onKeyDown", hdmi(RIGHT, KeyEvent.ACTION_DOWN, 1, KeyEvent.FLAG_CANCELED));
            });
            waitMain(350);
            assertTrue("clear mode=" + mode, recorder.seeks.isEmpty());
            assertEquals(1, recorder.cancels);
        }
        evidence("SEEK_CLEAR", "session/cancel/leave-full/canSeek/canceled-repeat all rejected");
    }

    @Test public void eHdmiIndependentUpPairsNavigateNormally() throws Exception {
        send(UP, KeyEvent.ACTION_DOWN, 1);
        send(UP, KeyEvent.ACTION_UP, 0);
        assertEquals(0, recorder.ups);
        for (int n = 0; n < 3; n++) {
            send(UP, KeyEvent.ACTION_DOWN, 0);
            send(UP, KeyEvent.ACTION_UP, 0);
        }
        assertEquals(3, recorder.ups);
        assertEquals(0, recorder.speedUps);
        evidence("UP_SHORT", "3 independent pairs=3 navigation callbacks; orphan ignored");
    }

    @Test public void fHdmiRepeatWithoutLongPressBoostsOnceAndReleaseRestores() throws Exception {
        send(UP, KeyEvent.ACTION_DOWN, 0);
        send(UP, KeyEvent.ACTION_DOWN, 1);
        assertEquals(0, recorder.speedUps);
        waitMain(ViewConfiguration.getLongPressTimeout() + 20L);
        send(UP, KeyEvent.ACTION_DOWN, 2);
        send(UP, KeyEvent.ACTION_DOWN, 3);
        assertEquals(1, recorder.speedUps);
        assertTrue((Boolean) onMain(() -> invoke(input, "isChangingSpeed")));
        send(UP, KeyEvent.ACTION_UP, 0);
        assertEquals(1, recorder.speedEnds);
        assertEquals(0, recorder.ups);
        assertFalse((Boolean) onMain(() -> invoke(input, "isChangingSpeed")));
        evidence("UP_HOLD", "no LONG_PRESS; systemTimeout=" + ViewConfiguration.getLongPressTimeout() + " speedUp=1 speedEnd=1 normalUp=0");
    }

    @Test public void gHdmiUpIdentityAndStaleEventsCannotBoostOrRelease() throws Exception {
        long start = send(UP, KeyEvent.ACTION_DOWN, 0);
        waitMain(ViewConfiguration.getLongPressTimeout() + 20L);
        long now = SystemClock.uptimeMillis();
        raw(event(now, now, KeyEvent.ACTION_DOWN, UP, 1, 7, InputDevice.SOURCE_HDMI, 0));
        raw(event(start, now, KeyEvent.ACTION_DOWN, UP, 1, -1, InputDevice.SOURCE_KEYBOARD, KeyEvent.FLAG_LONG_PRESS));
        raw(event(start - 1, start - 1, KeyEvent.ACTION_DOWN, UP, 2, -1, InputDevice.SOURCE_HDMI, 0));
        assertEquals(0, recorder.speedUps);
        send(UP, KeyEvent.ACTION_DOWN, 3);
        raw(event(now, now, KeyEvent.ACTION_UP, UP, 0, 7, InputDevice.SOURCE_HDMI, 0));
        assertEquals(0, recorder.speedEnds);
        send(UP, KeyEvent.ACTION_UP, 0);
        assertEquals(1, recorder.speedUps);
        assertEquals(1, recorder.speedEnds);
        evidence("UP_IDENTITY", "wrong source/device/stale rejected; valid release ends once");
    }

    @Test public void hHdmiUpClearsWithoutLateRestart() throws Exception {
        for (boolean boosted : new boolean[]{false, true}) {
            for (int mode = 0; mode < 5; mode++) {
                freshDirectInput();
                send(UP, KeyEvent.ACTION_DOWN, 0);
                if (boosted) {
                    waitMain(ViewConfiguration.getLongPressTimeout() + 10L);
                    send(UP, KeyEvent.ACTION_DOWN, 1);
                }
                final int clear = mode;
                main(() -> {
                    if (clear == 0) invoke(input, "onKeyDown", hdmi(UP, KeyEvent.ACTION_UP, 0, KeyEvent.FLAG_CANCELED));
                    if (clear == 1) invoke(input, "onKeyDown", hdmi(UP, KeyEvent.ACTION_DOWN, 2, KeyEvent.FLAG_CANCELED));
                    if (clear == 2) invoke(input, "onKeyDown", hdmi(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0, 0));
                    if (clear == 3) invoke(input, "releaseSpeed");
                    if (clear == 4) invoke(input, "setFull", false);
                });
                send(UP, KeyEvent.ACTION_DOWN, 3);
                send(UP, KeyEvent.ACTION_UP, 0);
                assertEquals(boosted ? 1 : 0, recorder.speedUps);
                assertEquals(boosted ? 1 : 0, recorder.speedEnds);
                assertEquals(0, recorder.ups);
                assertFalse((Boolean) onMain(() -> invoke(input, "isChangingSpeed")));
            }
        }
        evidence("UP_CLEAR", "cancel DOWN/UP, other key, releaseSpeed, leave-full before/after boost");
    }

    @Test public void iNativeDirectionDownTimeAndFlaggedUpRemainCompatible() throws Exception {
        for (int code : new int[]{LEFT, RIGHT}) {
            freshDirectInput();
            long down = SystemClock.uptimeMillis();
            raw(event(down, down, KeyEvent.ACTION_DOWN, code, 0, 1, InputDevice.SOURCE_DPAD, 0));
            raw(event(down, down + 1, KeyEvent.ACTION_DOWN, code, 1, 1, InputDevice.SOURCE_DPAD, 0));
            raw(event(down + 2, down + 2, KeyEvent.ACTION_UP, code, 0, 1, InputDevice.SOURCE_DPAD, 0));
            waitMain(300);
            assertTrue("native mismatched downTime rejected", recorder.seeks.isEmpty());
            raw(event(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 1, InputDevice.SOURCE_DPAD, 0));
            assertTrue(recorder.commit.await(1500, TimeUnit.MILLISECONDS));
            assertEquals(Arrays.asList(code == LEFT ? -20000L : 20000L), recorder.seeks);
        }
        freshDirectInput();
        long down = SystemClock.uptimeMillis();
        raw(event(down, down, KeyEvent.ACTION_DOWN, UP, 1, 1, InputDevice.SOURCE_KEYBOARD, KeyEvent.FLAG_LONG_PRESS));
        raw(event(down, down + 1, KeyEvent.ACTION_UP, UP, 0, 1, InputDevice.SOURCE_KEYBOARD, 0));
        assertEquals(1, recorder.speedUps);
        assertEquals(1, recorder.speedEnds);
        assertEquals(0, recorder.ups);
        evidence("NATIVE", "DPAD strict downTime hold +/-20s; native flagged boost compatible");
    }

    @Test public void zRealPageLocalPlayerSeekBoostAndControlFocus() throws Exception {
        File fixture = new File(INSTR.getTargetContext().getCacheDir(), "ISSUE-582-CEC-DEVICE.mp4");
        assertTrue("push deterministic fixture to target cache before this test", fixture.isFile());
        main(() -> invoke(input, "setFull", false));
        main(() -> activity.finish());
        Object action = classFor("com.fongmi.android.tv.dlna.CastAction")
                .getConstructor(String.class, String.class, java.util.Map.class)
                .newInstance(fixture.toURI().toString(), "", new HashMap<String, String>());
        activity = launch((Parcelable) action);
        await(() -> {
            Object p = invoke(activity, "player");
            return p != null && (Boolean) invoke(p, "isPlaying") && ((Number) invoke(p, "getDuration")).longValue() > 60000;
        }, 45000, "local player ready");
        Object player = onMain(() -> invoke(activity, "player"));
        main(() -> { invoke(player, "seekTo", 30000L); invoke(activity, "hideControl"); });
        await(() -> Math.abs(((Number) invoke(player, "getPosition")).longValue() - 30000) < 3000, 10000, "initial local position");
        long before = ((Number) onMain(() -> invoke(player, "getPosition"))).longValue();
        capture("page-before-seek");
        dispatch(RIGHT, KeyEvent.ACTION_DOWN, 0);
        dispatch(RIGHT, KeyEvent.ACTION_DOWN, 1);
        capture("page-seek-preview");
        long preview = ((Number) onMain(() -> invoke(player, "getPosition"))).longValue();
        assertTrue("preview does not seek player before release", preview - before < 4000);
        dispatch(RIGHT, KeyEvent.ACTION_UP, 0);
        await(() -> ((Number) invoke(player, "getPosition")).longValue() >= before + 19000, 10000, "real +20s seek");
        long after = ((Number) onMain(() -> invoke(player, "getPosition"))).longValue();
        assertTrue("one accumulated seek", after < before + 25000);
        capture("page-after-seek");
        main(() -> invoke(activity, "hideControl"));
        float speedBefore = ((Number) onMain(() -> invoke(player, "getSpeed"))).floatValue();
        dispatch(UP, KeyEvent.ACTION_DOWN, 0);
        waitMain(ViewConfiguration.getLongPressTimeout() + 30L);
        dispatch(UP, KeyEvent.ACTION_DOWN, 1);
        float speedDuring = ((Number) onMain(() -> invoke(player, "getSpeed"))).floatValue();
        float configured = ((Number) invokeStatic(classFor("com.fongmi.android.tv.setting.PlayerSetting"), "getSpeed")).floatValue();
        assertEquals(configured, speedDuring, 0.01f);
        assertEquals(View.VISIBLE, (int) onMain(() -> ((View) getField(getField(getField(activity, "mBinding"), "widget"), "speed")).getVisibility()));
        waitMain(100);
        main(() -> assertVisible("speed", (View) getField(getField(getField(activity, "mBinding"), "widget"), "speed")));
        capture("page-boost-during");
        dispatch(UP, KeyEvent.ACTION_UP, 0);
        float speedAfter = ((Number) onMain(() -> invoke(player, "getSpeed"))).floatValue();
        assertEquals(speedBefore, speedAfter, 0.01f);
        Object binding = getField(activity, "mBinding");
        Object widget = getField(binding, "widget");
        assertEquals(View.GONE, (int) onMain(() -> ((View) getField(widget, "speed")).getVisibility()));
        capture("page-boost-after");
        dispatch(UP, KeyEvent.ACTION_DOWN, 0);
        dispatch(UP, KeyEvent.ACTION_UP, 0);
        waitMain(150);
        main(() -> {
            View control = (View) invoke(getField(getField(activity, "mBinding"), "control"), "getRoot");
            assertVisible("control", control);
            View focus = activity.getCurrentFocus();
            assertNotNull("controls have focus", focus);
            View ancestor = focus;
            while (ancestor != null && ancestor != control)
                ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
            assertSame("focus belongs to control root", control, ancestor);
            assertVisible("controlFocus", focus);
        });
        capture("page-control-focus");
        evidence("REAL_PAGE", "position before=" + before + " preview=" + preview + " after=" + after
                + " speed=" + speedBefore + "/" + speedDuring + "/" + speedAfter
                + " focus=" + onMain(() -> activity.getCurrentFocus().getClass().getSimpleName()));
    }

    private static Activity launch(Parcelable action) {
        Intent intent = new Intent().setClassName(INSTR.getTargetContext(), "com.fongmi.android.tv.ui.activity.CastActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (action != null) intent.putExtra("cast_action", action);
        return INSTR.startActivitySync(intent);
    }

    private long send(int key, int action, int repeat) {
        AtomicReference<Long> time = new AtomicReference<>();
        main(() -> { KeyEvent e = hdmi(key, action, repeat, 0); time.set(e.getEventTime()); invoke(input, "onKeyDown", e); });
        return time.get();
    }
    private void raw(KeyEvent e) { main(() -> invoke(input, "onKeyDown", e)); }
    private static void dispatch(int key, int action, int repeat) { main(() -> assertTrue(activity.dispatchKeyEvent(hdmi(key, action, repeat, 0)))); }
    private static KeyEvent hdmi(int key, int action, int repeat, int flags) {
        long now = SystemClock.uptimeMillis();
        return event(now, now, action, key, repeat, -1, InputDevice.SOURCE_HDMI, flags);
    }
    private static KeyEvent event(long down, long time, int action, int key, int repeat, int device, int source, int flags) {
        return new KeyEvent(down, time, action, key, repeat, 0, device, 0, flags | KeyEvent.FLAG_FROM_SYSTEM, source);
    }
    private static void waitMain(long delay) throws Exception {
        CountDownLatch marker = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).postDelayed(marker::countDown, delay);
        assertTrue("bounded main Looper marker", marker.await(delay + 3000, TimeUnit.MILLISECONDS));
    }
    private interface Value { Object get(); }
    private interface Condition { boolean get(); }
    private static void await(Condition condition, long timeout, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < end) {
            if ((Boolean) onMain(condition::get)) return;
            waitMain(100);
        }
        fail(label + " timed out after " + timeout + "ms");
    }
    private static void main(Runnable run) { INSTR.runOnMainSync(run); }
    private static Object onMain(Value value) {
        AtomicReference<Object> result = new AtomicReference<>();
        main(() -> result.set(value.get()));
        return result.get();
    }
    private static Object getField(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception e) { throw new AssertionError(name, e); }
    }
    private static void setField(Object target, String name, Object value) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
        catch (Exception e) { throw new AssertionError(name, e); }
    }
    private static Class<?> classFor(String name) {
        try { return Class.forName(name); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static Object invoke(Object target, String name, Object... args) { return method(target.getClass(), target, name, args); }
    private static Object invokeStatic(Class<?> type, String name, Object... args) { return method(type, null, name, args); }
    private static Object method(Class<?> type, Object target, String name, Object... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
                try { m.setAccessible(true); return m.invoke(target, args); }
                catch (IllegalArgumentException ignored) { }
                catch (Exception e) { throw new AssertionError(name, e); }
            }
        }
        throw new AssertionError("Missing method " + type + "." + name);
    }
    private static void assertVisible(String label, View view) {
        Rect rect = new Rect();
        boolean global = view.getGlobalVisibleRect(rect);
        evidence("VIEW_GEOMETRY", label + " visibility=" + view.getVisibility() + " shown=" + view.isShown()
                + " alpha=" + view.getAlpha() + " attached=" + view.isAttachedToWindow()
                + " windowFocus=" + view.hasWindowFocus() + " global=" + global + " rect=" + rect);
        assertEquals(label, View.VISIBLE, view.getVisibility());
        assertTrue(label + " shown", view.isShown());
        assertTrue(label + " attached", view.isAttachedToWindow());
        assertTrue(label + " alpha", view.getAlpha() > 0);
        assertTrue(label + " visible bounds", global && !rect.isEmpty());
        assertTrue(label + " window focus", view.hasWindowFocus());
    }

    private static void capture(String name) throws Exception {
        Bitmap image = INSTR.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File out = new File(INSTR.getTargetContext().getCacheDir(), "ISSUE-582-CEC-DEVICE-" + name + ".png");
        try (FileOutputStream stream = new FileOutputStream(out)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        image.recycle();
        evidence("SCREENSHOT", out.getAbsolutePath());
    }
    private static void evidence(String scenario, String message) { Log.i(TAG, scenario + " " + message); System.out.println(TAG + " " + scenario + " " + message); }

    private static final class Recorder {
        final List<Long> previews = new CopyOnWriteArrayList<>();
        final List<Long> seeks = new CopyOnWriteArrayList<>();
        final CountDownLatch commit = new CountDownLatch(1);
        String session = "device-session";
        boolean canSeek = true;
        volatile long commitAt;
        int cancels, speedUps, speedEnds, ups;
        Object callback(String name, Object[] args) {
            if (name.equals("canSeek")) return canSeek;
            if (name.equals("getSeekSession")) return session;
            if (name.equals("onSeeking")) previews.add((Long) args[0]);
            if (name.equals("onSeekEnd")) { seeks.add((Long) args[0]); commitAt = SystemClock.uptimeMillis(); commit.countDown(); }
            if (name.equals("onSeekCancel")) cancels++;
            if (name.equals("onSpeedUp")) speedUps++;
            if (name.equals("onSpeedEnd")) speedEnds++;
            if (name.equals("onKeyUp")) ups++;
            return null;
        }
    }
}
