package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerDisplaySettingSyncTest {

    @Test
    public void settingPagesUsePlaybackDisplayPreferences() throws Exception {
        assertSettingPageUsesDisplayPreferences(read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java"))));
        assertSettingPageUsesDisplayPreferences(read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java"))));
    }

    @Test
    public void playerOsdArrayMatchesPlaybackDisplayOptions() throws Exception {
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rCN", "strings.xml"))));
        assertPlayerOsdArrayIncludesAllDisplayOptions(read(mainResPath().resolve(Path.of("values-zh-rTW", "strings.xml"))));
    }

    @Test
    public void tvScreenDisplayDialogRowsUseTheThemeAwareContrastWiring() throws Exception {
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String layout = read(sourcePath(flavor, "res").resolve(Path.of("layout", "adapter_player_osd.xml")));
            String dialog = read(sourcePath(flavor, "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "PlayerOsdDialog.java")));
            assertTrue(flavor + " 屏显行必须使用主题化行背景", layout.contains("@drawable/selector_player_settings_row"));
            assertTrue(flavor + " 屏显眼睛按钮必须使用主题化按钮背景", layout.contains("@drawable/selector_player_settings_switch"));
            assertTrue(flavor + " 屏显眼睛图标必须使用主题化前景", layout.contains("@color/player_settings_icon_tint"));
            assertFalse(flavor + " 屏显行不得回退到固定浅色卡片", layout.contains("selector_git_cloud_card"));
            assertTrue(flavor + " 屏显对话框必须应用活动主题行颜色", dialog.contains("PlayerSettingsRowTheme.apply(holder.binding.root, holder.binding.name"));
            assertTrue(flavor + " 屏显对话框必须应用活动主题图标颜色", dialog.contains("PlayerSettingsRowTheme.applySwitch(holder.binding.visible"));
        }
    }

    @Test
    public void playerOsdControllerHonorsResolutionDisplayToggle() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTopLeft(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setTopLeft", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);
        assertTrue("Player OSD should honor the shared resolution display switch", body.contains("PlayerSetting.isOsdResolution()"));
        assertTrue("Player OSD should allow title and resolution to be toggled independently", body.contains("showTitle") && body.contains("showResolution"));
    }

    @Test
    public void playerOsdControllerForcesPersistentTextWhite() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setTextSize(float sp)");
        assertTrue("PlayerOsdController is missing setTextSize", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("top-left OSD text must stay white over themed TMDB surfaces", body.contains("topLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("top-right OSD text must stay white over themed TMDB surfaces", body.contains("topRight.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-left OSD text must stay white over themed TMDB surfaces", body.contains("bottomLeft.setTextColor(0xFFFFFFFF)"));
        assertTrue("bottom-right OSD text must stay white over themed TMDB surfaces", body.contains("bottomRight.setTextColor(0xFFFFFFFF)"));
    }

    @Test
    public void playerOsdControllerCanSuppressPersistentCornerLabels() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("public void setPersistentSuppressed(boolean persistentSuppressed)");
        int render = source.indexOf("private boolean render()");

        assertTrue("PlayerOsdController must expose persistent display suppression for embedded mobile players", method >= 0);
        assertTrue("persistent suppression should hide title/time/progress/traffic/mini labels while keeping diagnostics available",
                render >= 0
                        && source.indexOf("if (persistentSuppressed)", render) > render
                        && source.indexOf("hidePersistent();", render) > render
                        && source.indexOf("setDiagnosticsPanel(player);", render) > render);
    }

    /**
     * 诊断面板的重缓冲计数必须按「有没有 Exo analytics 快照」分派，而不是按是否 MPV。
     *
     * <p>非 Exo 时 {@code snapshot} 是 {@code Snapshot.empty()}，重缓冲恒为 0；若只对 MPV 分支，
     * IJK 会落到快照分支而永远读到 0，排查时会被误导。
     */
    @Test
    public void playerOsdDiagnosticsReadRebufferCountPerEngine() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));

        int count = source.indexOf("int rebufferCount =");
        int total = source.indexOf("long rebufferTotalMs =");
        assertTrue("the diagnostics must compute a re-buffer count and total", count >= 0 && total > count);
        String countExpr = source.substring(count, source.indexOf(';', count));
        String totalExpr = source.substring(total, source.indexOf(';', total));

        assertTrue("re-buffer count must read the engine-agnostic tracker for every non-Exo engine, not only MPV",
                countExpr.contains("player.isExo()")
                        && countExpr.contains("snapshot.rebufferCount()")
                        && countExpr.contains("player.getRebufferCount()"));
        assertTrue("re-buffer total must read the engine-agnostic tracker for every non-Exo engine, not only MPV",
                totalExpr.contains("player.isExo()")
                        && totalExpr.contains("snapshot.rebufferTotalMs()")
                        && totalExpr.contains("player.getRebufferTotalMs()"));
    }

    /**
     * 媒体信息徽标的两条展示语义：
     * 控制栏唤醒时无条件展示；控制栏隐藏时与其它常驻屏显同级，只有在
     * 「屏显设置 → 媒体信息」开关打开时才展示（默认关闭）。
     */
    @Test
    public void mediaBadgesFollowTheMediaInfoSwitchOnlyWhenTheControlBarIsHidden() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setMediaBadges(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setMediaBadges", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("the control bar must show media badges regardless of the media info switch",
                !body.contains("!controlsVisible || !PlayerSetting.isDisplayMediaBadges()"));
        assertTrue("the media info switch must only gate the persistent (control-bar-hidden) path",
                body.contains("!controlsVisible && !PlayerSetting.isDisplayMediaBadges()"));

        String persistent = source.substring(source.indexOf("// 控制栏隐藏时，按用户设置显示常驻屏显"));
        String renderEnd = "private boolean renderMediaBadgesOnly()";
        int end = persistent.indexOf(renderEnd);
        assertTrue("the persistent render branch must exist before renderMediaBadgesOnly", end > 0);
        String persistentBody = persistent.substring(0, end);
        assertTrue("persistent OSD must render media badges like title/time/progress/traffic",
                persistentBody.contains("setMediaBadges(player);"));
        assertTrue("the persistent path must not force the badge strip hidden",
                !persistentBody.contains("mediaBadges.setVisibility(View.GONE)"));
        assertTrue("the persistent badges must raise the bottom corners to clear the persistent text line",
                source.contains("int bottomInset = controlsVisible ? dp(80) : dp(22);")
                        && source.contains("int topInset = controlsVisible ? dp(64) : 0;"));
    }

    @Test
    public void backupIncludesPlaybackDisplayPreferences() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));
        assertTrue(source.contains("\"display_time\""));
        assertTrue(source.contains("\"display_traffic\""));
        assertTrue(source.contains("\"display_size\""));
        assertTrue(source.contains("\"display_progress\""));
        assertTrue(source.contains("\"display_mini\""));
        assertTrue(source.contains("\"display_title\""));
    }

    private static void assertSettingPageUsesDisplayPreferences(String source) {
        assertTrue(source.contains("PlayerSetting.getDisplayChecked()"));
        assertTrue(source.contains("PlayerSetting.putDisplayChecked(checked)"));
        assertFalse(source.contains("PlayerSetting.isOsdTitle()"));
        assertFalse(source.contains("PlayerSetting.putOsdTitle("));
    }

    private static void assertPlayerOsdArrayIncludesAllDisplayOptions(String source) {
        String array = array(source, "select_player_osd");
        assertOrdered(array, "@string/display_time", "@string/display_traffic", "@string/display_size", "@string/display_progress", "@string/display_mini", "@string/display_title");
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int index = source.indexOf(token);
            assertTrue(token + " is missing", index >= 0);
            assertTrue(token + " is out of order", index > previous);
            previous = index;
        }
    }

    private static String array(String source, String name) {
        String startTag = "<string-array name=\"" + name + "\">";
        int start = source.indexOf(startTag);
        assertTrue(name + " array is missing", start >= 0);
        int end = source.indexOf("</string-array>", start);
        assertTrue(name + " array is not closed", end > start);
        return source.substring(start, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        return sourcePath("main", "java");
    }

    private static Path mainResPath() {
        return sourcePath("main", "res");
    }

    private static Path sourcePath(String sourceSet, String kind) {
        Path moduleRelative = Path.of("src", sourceSet, kind);
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", sourceSet, kind);
    }
}
