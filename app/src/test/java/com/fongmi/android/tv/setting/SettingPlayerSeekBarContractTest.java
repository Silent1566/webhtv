package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 契约测试：播放设置页档位滑动条的遥控焦点、左右键与预载项常驻布局。 */
public class SettingPlayerSeekBarContractTest {

    private static final String ACTIVITY = "src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java";
    private static final String LAYOUT = "src/leanback/res/layout/activity_setting_player.xml";

    private static final String[] ROWS = {"preloadSeamless", "preloadThread", "preloadSize", "preloadDuration"};
    private static final String[] BARS = {"preloadSeamlessBar", "preloadThreadBar", "preloadSizeBar", "preloadDurationBar"};
    private static final String[] TEXTS = {"preloadSeamlessText", "preloadThreadText", "preloadSizeText", "preloadDurationText"};

    @Test
    public void focusLandsOnRowContainerWithWhiteRing() throws Exception {
        String layout = read(LAYOUT).replaceAll("\\s+", "");
        for (String row : ROWS) {
            int at = layout.indexOf("android:id=\"@+id/" + row + "\"");
            assertTrue(row + " 不在布局中", at >= 0);
            String view = layout.substring(at, Math.min(layout.indexOf("android:orientation", at), at + 900));
            assertTrue(row + " 行容器必须可聚焦（承载 selector_item 白色焦点框）", view.contains("android:focusable=\"true\""));
            assertTrue(row + " 行容器必须有焦点背景", view.contains("android:background=\"@drawable/selector_item\""));
        }
        String source = read(ACTIVITY);
        for (int i = 0; i < BARS.length; i++) {
            assertTrue(BARS[i] + " 必须不可聚焦（焦点落在整行容器）", source.contains("bindDpadSeekBar(mBinding." + ROWS[i] + ", mBinding." + BARS[i] + ", mBinding." + TEXTS[i] + ")"));
        }
    }

    @Test
    public void seekBarsAreWhiteAndNotFocusable() throws Exception {
        String layout = read(LAYOUT).replaceAll("\\s+", "");
        for (String bar : BARS) {
            int at = layout.indexOf("android:id=\"@+id/" + bar + "\"");
            assertTrue(bar + " 不在布局中", at >= 0);
            String view = layout.substring(at, Math.min(layout.indexOf("/>", at), at + 900));
            assertTrue(bar + " 必须不可聚焦", view.contains("android:focusable=\"false\""));
            assertTrue(bar + " 进度条必须为白色", view.contains("android:progressTint=\"@color/white\""));
            assertTrue(bar + " 拇指必须为白色", view.contains("android:thumbTint=\"@color/white\""));
        }
    }

    @Test
    public void dpadHandledAtActivityLevelToAvoidIntermittentFailure() throws Exception {
        String source = read(ACTIVITY);
        assertTrue("必须在 Activity 级接管按键", source.contains("public boolean dispatchKeyEvent(KeyEvent event)"));
        assertTrue("必须处理左键", source.contains("keyCode == KeyEvent.KEYCODE_DPAD_LEFT"));
        assertTrue("必须处理右键", source.contains("keyCode == KeyEvent.KEYCODE_DPAD_RIGHT"));
        assertTrue("必须判定焦点所在行", source.contains("SeekBar focusedSeekBar()"));
        assertTrue("焦点行判定必须覆盖四个容器",
                source.contains("id == R.id.preloadSeamless") &&
                source.contains("id == R.id.preloadSize") &&
                source.contains("id == R.id.preloadThread") &&
                source.contains("id == R.id.preloadDuration"));
        assertTrue("必须按档步进并即时落盘", source.contains("bar.getProgress() + step") && source.contains("onStopTrackingTouch(bar)"));
        assertTrue("左右键必须消费", source.contains("return true;"));
        assertFalse("不应再依赖 View.OnKeyListener（偶发失效根因）", source.contains("setOnKeyListener"));
    }

    @Test
    public void valueTextViewsAreNotFocusableInLayout() throws Exception {
        String layout = read(LAYOUT).replaceAll("\\s+", "");
        for (String id : TEXTS) {
            int at = layout.indexOf("android:id=\"@+id/" + id + "\"");
            assertTrue(id + " 不在布局中", at >= 0);
            String view = layout.substring(at, Math.min(layout.indexOf("/>", at), at + 900));
            assertTrue(id + " 必须不可聚焦", view.contains("android:focusable=\"false\""));
            assertTrue(id + " 必须不可触摸聚焦", view.contains("android:focusableInTouchMode=\"false\""));
        }
    }

    @Test
    public void threadsAndDurationStayVisibleOutsideSeamlessGroup() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("mBinding.preloadThread.setVisibility(View.VISIBLE);"));
        assertTrue(source.contains("mBinding.preloadDuration.setVisibility(View.VISIBLE);"));
        assertFalse("预载并发不应再随展开组隐藏", source.contains("mBinding.preloadThread.setVisibility(seamlessVisibility);"));
        assertFalse("预加载时长不应再随展开组隐藏", source.contains("mBinding.preloadDuration.setVisibility(seamlessVisibility);"));
    }

    @Test
    public void layoutOrderIsSwitchThenSeamlessGroupThenResidentItemsThenPlayerButtons() throws Exception {
        String layout = read(LAYOUT);
        String[] order = {"preloadSeamlessSwitch", "preloadExpand", "preloadSeamless", "preloadSize", "preloadThread", "preloadDuration", "playerButtons"};
        int last = -1;
        for (String id : order) {
            int at = layout.indexOf("android:id=\"@+id/" + id + "\"");
            assertTrue(id + " 不在布局中", at > last);
            last = at;
        }
    }

    @Test
    public void focusChainNeverTargetsGoneExpandWhenSwitchOff() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("mBinding.preloadSeamlessSwitch.setNextFocusDownId(seamless ? R.id.preloadExpand : R.id.preloadThread);"));
        assertTrue(source.contains("mBinding.preloadExpand.setNextFocusDownId(expanded ? R.id.preloadSeamless : R.id.preloadThread);"));
        assertTrue(source.contains("mBinding.preloadSeamless.setNextFocusDownId(R.id.preloadSize);"));
        assertTrue(source.contains("mBinding.preloadSize.setNextFocusUpId(R.id.preloadSeamless);"));
        assertTrue(source.contains("mBinding.preloadThread.setNextFocusUpId(expanded ? R.id.preloadSize : seamless ? R.id.preloadExpand : R.id.preloadSeamlessSwitch);"));
        assertTrue(source.contains("mBinding.preloadDuration.setNextFocusUpId(R.id.preloadThread);"));
    }

    private static String read(String relative) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }
}
