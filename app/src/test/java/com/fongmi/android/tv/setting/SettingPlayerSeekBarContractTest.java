package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 契约测试：播放设置页档位滑动条的遥控左右键与预载项常驻布局。 */
public class SettingPlayerSeekBarContractTest {

    private static final String ACTIVITY = "src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java";
    private static final String LAYOUT = "src/leanback/res/layout/activity_setting_player.xml";

    @Test
    public void allFourSeekBarsBindDpadSeekBar() throws Exception {
        String source = read(ACTIVITY);
        String[] ids = {"preloadSeamlessBar", "preloadThreadBar", "preloadSizeBar", "preloadDurationBar"};
        String[] texts = {"preloadSeamlessText", "preloadThreadText", "preloadSizeText", "preloadDurationText"};
        for (int i = 0; i < ids.length; i++) {
            assertTrue(ids[i] + " 必须绑定 D-pad 调节", source.contains("bindDpadSeekBar(mBinding." + ids[i] + ", mBinding." + texts[i] + ")"));
        }
        // 统一监听器消费左右键并按档位步进
        assertTrue(source.contains("KEYCODE_DPAD_LEFT"));
        assertTrue(source.contains("KEYCODE_DPAD_RIGHT"));
        assertTrue(source.contains("bar.getProgress() + step"));
    }

    @Test
    public void valueTextViewsAreNotFocusableInLayout() throws Exception {
        String layout = read(LAYOUT).replaceAll("\\s+", "");
        String[] ids = {"preloadSeamlessText", "preloadThreadText", "preloadSizeText", "preloadDurationText"};
        for (String id : ids) {
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
        assertTrue(source.contains("mBinding.preloadSeamlessSwitch.setNextFocusDownId(seamless ? R.id.preloadExpand : R.id.preloadThreadBar);"));
        assertTrue(source.contains("mBinding.preloadExpand.setNextFocusDownId(expanded ? R.id.preloadSeamlessBar : R.id.preloadThreadBar);"));
        assertTrue(source.contains("mBinding.preloadSeamlessBar.setNextFocusDownId(R.id.preloadSizeBar);"));
        assertTrue(source.contains("mBinding.preloadSizeBar.setNextFocusUpId(R.id.preloadSeamlessBar);"));
        assertTrue(source.contains("mBinding.preloadThreadBar.setNextFocusUpId(expanded ? R.id.preloadSizeBar : seamless ? R.id.preloadExpand : R.id.preloadSeamlessSwitch);"));
    }

    private static String read(String relative) throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }
}
