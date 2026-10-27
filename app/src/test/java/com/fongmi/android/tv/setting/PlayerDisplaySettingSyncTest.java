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
     * 媒体信息徽标的唯一开关是「屏显设置 → 媒体信息」（默认关闭）：
     * 关闭时控制栏唤醒也不再渲染徽标（非全屏唤醒的控制栏本就拥挤），
     * 打开时控制栏隐藏同样作为常驻项展示，与标题 / 时间 / 进度 / 码率同级。
     */
    @Test
    public void mediaBadgesAreGatedByTheMediaInfoSwitchOnEveryPath() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int method = source.indexOf("private void setMediaBadges(PlayerManager player)");
        assertTrue("PlayerOsdController is missing setMediaBadges", method >= 0);
        int methodEnd = source.indexOf("\n    }", method);
        String body = source.substring(method, methodEnd);

        assertTrue("media badges must stay off every non-fullscreen surface",
                body.contains("!source.isFullscreen()"));
        assertTrue("the media info switch must gate badges on every path, including a woken control bar",
                body.contains("!PlayerSetting.isDisplayMediaBadges()")
                        && !body.contains("!controlsVisible && !PlayerSetting.isDisplayMediaBadges()"));

        int only = source.indexOf("private boolean renderMediaBadgesOnly()");
        assertTrue("PlayerOsdController is missing renderMediaBadgesOnly", only >= 0);
        String onlyBody = source.substring(only, source.indexOf("\n    }", only));
        assertTrue("the suppressed mobile control-bar path must honor both fullscreen and the switch",
                onlyBody.contains("if (!source.isFullscreen() || !PlayerSetting.isDisplayMediaBadges())"));

        String persistent = source.substring(source.indexOf("// 控制栏隐藏时，按用户设置显示常驻屏显"));
        String renderEnd = "private boolean renderMediaBadgesOnly()";
        int end = persistent.indexOf(renderEnd);
        assertTrue("the persistent render branch must exist before renderMediaBadgesOnly", end > 0);
        String persistentBody = persistent.substring(0, end);
        assertTrue("persistent OSD must render media badges like title/time/progress/traffic",
                persistentBody.contains("setMediaBadges(player);"));
        assertTrue("the persistent path must not force the badge strip hidden",
                !persistentBody.contains("mediaBadges.setVisibility(View.GONE)"));
        assertTrue("badges must clear the control bar only while it is on screen",
                source.contains("bottomMargin = controlsVisible ? dp(80) : (stacked ? clear : edgeInset);")
                        && source.contains("topMargin = controlsVisible ? dp(64) : (stacked ? clear : edgeInset);"));
        assertTrue("badges must hug the screen edge when nothing else occupies their corner",
                source.contains("int edgeInset = dp(2);") && source.contains("private View persistentNeighbour(int position)"));
    }

    /**
     * 「媒体信息位置」单选新增关闭项，且位置键始终保留用户最近一次选的四角：
     * 关闭只关 {@code player_osd_media_badges}，不改位置；再次开启回到那个角，
     * 从未设置过（默认关闭）时则回到默认的右下角（index 3）。
     */
    @Test
    public void mediaBadgePositionChoiceKeepsTheLastCornerAcrossOff() throws Exception {
        String setting = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));
        assertTrue("the Off choice must be the 5th item of the position list",
                setting.contains("MEDIA_BADGE_POSITION_OFF = 4"));

        int choice = setting.indexOf("public static int getMediaBadgeChoice()");
        assertTrue("PlayerSetting is missing getMediaBadgeChoice", choice >= 0);
        String choiceExpr = setting.substring(choice, setting.indexOf(';', choice));
        assertTrue("the selected choice must be Off while media info is disabled",
                choiceExpr.contains("isDisplayMediaBadges()") && choiceExpr.contains("MEDIA_BADGE_POSITION_OFF"));
        assertTrue("the selected choice must be the stored corner while media info is enabled",
                choiceExpr.contains("getMediaBadgePosition()"));
        assertTrue("an unset position key must still fall back to the bottom-right corner", setting.contains("KEY_MEDIA_BADGE_POSITION, 3"));

        int apply = setting.indexOf("public static void putMediaBadgeChoice(int which)");
        assertTrue("PlayerSetting is missing putMediaBadgeChoice", apply >= 0);
        String applyBody = setting.substring(apply, setting.indexOf("\n    }", apply));
        assertTrue("choosing Off must only switch media info off and keep the remembered corner",
                applyBody.contains("putDisplayMediaBadges(false)")
                        && !applyBody.contains("putMediaBadgePosition(MEDIA_BADGE_POSITION_OFF)"));
        assertTrue("choosing a corner must remember that corner without switching the persistent display on",
                applyBody.contains("putMediaBadgePosition(which)") && !applyBody.contains("putDisplayMediaBadges(true)"));

        Path mobileSettingPage = Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java");
        Path leanbackSettingPage = Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java");
        String[] flavors = {"mobile", "leanback"};
        Path[] settingPages = {mobileSettingPage, leanbackSettingPage};
        for (int i = 0; i < flavors.length; i++) {
            String flavor = flavors[i];
            String page = read(sourcePath(flavor, "java").resolve(settingPages[i]));
            assertTrue(flavor + " 媒体信息位置单选必须显示当前选项（含关闭）",
                    page.contains("PlayerSetting.getMediaBadgeChoice()"));
            assertTrue(flavor + " 媒体信息位置单选必须写回开启/关闭",
                    page.contains("PlayerSetting.putMediaBadgeChoice(which);"));
            assertFalse(flavor + " 媒体信息位置单选不得再用只改角位的旧入口",
                    page.contains("PlayerSetting.putMediaBadgePosition(which);"));
        }

        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = locale.equals("values")
                    ? read(mainResPath().resolve(Path.of("values", "strings.xml")))
                    : read(mainResPath().resolve(Path.of(locale, "strings.xml")));
            int arrayStart = strings.indexOf("<string-array name=\"select_media_badge_position\">");
            assertTrue(locale + " must keep the media info position list", arrayStart >= 0);
            String array = strings.substring(arrayStart, strings.indexOf("</string-array>", arrayStart));
            int items = array.split("<item>", -1).length - 1;
            assertTrue(locale + " media info position list must offer four corners plus Off, was " + items, items == 5);
        }
    }

    /**
     * 需求：「只有全屏播放才显示媒体信息」。徽标开关与全屏判定是两条独立约束：
     * Source 需要暴露 isFullscreen()，四个宿主分别接上（两个 VideoActivity 读自己的 fullscreen 字段、
     * 详情页内嵌小窗读 inlineFullscreen），未覆盖的宿主默认 false。
     */
    @Test
    public void mediaBadgesOnlyShowInFullscreenPlayback() throws Exception {
        String controller = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerOsdController.java")));
        int sourceIface = controller.indexOf("public interface Source");
        String sourceBody = controller.substring(sourceIface, controller.indexOf("private static volatile", sourceIface));
        assertTrue("the OSD source must expose the fullscreen state", sourceBody.contains("default boolean isFullscreen()"));

        String host = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiHost.java")));
        assertTrue("the shared player host must forward the fullscreen state", host.contains("default boolean isFullscreen()"));

        String ui = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java")));
        assertTrue("the OSD controller must be fed the host fullscreen state",
                ui.contains("public boolean isFullscreen()") && ui.contains("return host.isFullscreen();"));

        Path videoActivity = Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String page = read(sourcePath(flavor, "java").resolve(videoActivity));
            assertTrue(flavor + " 全屏状态必须传给 OSD", page.contains("return VideoActivity.this.isFullscreen();"));
        }

        String tmdb = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        assertTrue("详情页内嵌小窗只有真正全屏后才显示媒体信息", tmdb.contains("return inlineFullscreen;"));
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
