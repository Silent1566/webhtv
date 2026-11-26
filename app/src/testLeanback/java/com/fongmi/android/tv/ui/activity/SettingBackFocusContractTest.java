package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** 设置子页返回焦点与集数缓存进度条遥控操作的源码契约。 */
public class SettingBackFocusContractTest {

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void subSettingsReturnFocusToTheirOwnTopItem() throws Exception {
        String[][] pages = {
                {"SettingEnhanceActivity", "customCsp"},
                {"SettingPlayerActivity", "decode"},
                {"SettingAdActivity", "adblock"},
                {"SettingSubtitleActivity", "subtitleAutoMatch"},
                {"SettingTmdbActivity", "tmdbSource"},
                {"SettingAiActivity", "aiRecommendation"},
                {"SettingAppearanceActivity", "uiScale"},
                {"SettingPersonalActivity", "homeVodAutoLoad"},
        };
        for (String[] page : pages) {
            String file = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/" + page[0] + ".java";
            String source = read(file);
            assertTrue(file, source.contains("protected int settingTopFocusId()"));
            assertTrue(file, source.contains("return R.id." + page[1] + ";"));
        }
    }

    @Test
    public void playerSettingsTopFocusIsDecodeRow() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java");
        assertTrue(source, source.contains("return R.id.decode;"));
        assertTrue(source, source.contains("mBinding.decode.requestFocus();"));
    }

    @Test
    public void baseActivityFocusesTopBeforeScrollingOrFinishing() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(source.contains("if (focusSettingTopOnBack()) return;"));
        assertTrue(source.contains("return top.requestFocus();"));
        assertTrue(source.contains("protected int settingTopFocusId()"));
    }

    @Test
    public void dohAndAppearanceDialogsFocusFirstItemOnBack() throws Exception {
        String doh = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/DohDialog.java");
        assertTrue(doh.contains("ChoiceDialog.showSingleWithBackTop"));
        String choice = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/ChoiceDialog.java");
        assertTrue(choice.contains("backToTopOnBack"));
        assertTrue(choice.contains("focusFirstChoiceItemOnBack()"));
        assertTrue(choice.contains("KeyEvent.KEYCODE_BACK"));
        String appearance = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/AppearanceDialog.java");
        assertTrue(appearance.contains("dialog.setOnKeyListener"));
        assertTrue(appearance.contains("rows.get(0).root()"));
    }

    @Test
    public void episodeCacheSeekBarAcceptsDpadKeys() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java");
        assertTrue(source.contains("mBinding.preloadSeamlessBar.setOnKeyListener"));
        assertTrue(source.contains("KeyEvent.KEYCODE_DPAD_LEFT"));
        assertTrue(source.contains("KeyEvent.KEYCODE_DPAD_RIGHT"));
        assertTrue(source.contains("setNextFocusDownId(expanded ? R.id.preloadSeamlessBar : View.NO_ID)"));
    }
}
