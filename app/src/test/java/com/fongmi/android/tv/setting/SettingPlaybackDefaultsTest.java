package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingPlaybackDefaultsTest {

    @Test
    public void subtitleAutoMatch_defaultsOff() throws Exception {
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));

        assertTrue(source.contains("Prefers.getBoolean(\"subtitle_auto_match\", false)"));
    }

    @Test
    public void subtitleAiConcurrencyDefaultsToTwo() throws Exception {
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));

        assertTrue(source.contains("Prefers.getInt(\"subtitle_ai_max_concurrency\", 2)"));
        assertTrue(source.contains("Prefers.getInt(\"subtitle_ai_chunk_count\", 2)"));
    }

    @Test
    public void subtitleAiSettingsLiveUnderSubtitleSettings() throws Exception {
        Path root = moduleRoot();

        assertTrue(read(root.resolve(Path.of("src", "mobile", "res", "layout", "fragment_setting_subtitle.xml"))).contains("@+id/subtitleAiSettings"));
        assertTrue(read(root.resolve(Path.of("src", "leanback", "res", "layout", "activity_setting_subtitle.xml"))).contains("@+id/subtitleAiSettings"));
        assertTrue(read(root.resolve(Path.of("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "SettingSubtitleFragment.java"))).contains("Setting.isAiConfigReady()"));
        assertTrue(read(root.resolve(Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingSubtitleActivity.java"))).contains("Setting.isAiConfigReady()"));
    }

    @Test
    public void autoSkipIntroOutro_defaultsOff() throws Exception {
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));

        assertTrue(source.contains("Prefers.getInt(\"intro_skip_mode\", INTRO_SKIP_OFF)"));
    }

    @Test
    public void autoSkipIntroOutro_isUnderPlayerSettings() throws Exception {
        assertPlayerOwnsAutoSkip("leanback", "activity", "Activity");
        assertPlayerOwnsAutoSkip("mobile", "fragment", "Fragment");
    }

    @Test
    public void episodeHistory_defaultsOn() {
        assertTrue(Setting.isEpisodeHistory());
    }

    @Test
    public void episodeHistory_isUnderPersonalSettings() throws Exception {
        assertPersonalOwnsEpisodeHistory("leanback", "activity", "Activity");
        assertPersonalOwnsEpisodeHistory("mobile", "fragment", "Fragment");
    }

    private static void assertPlayerOwnsAutoSkip(String flavor, String layoutPrefix, String classSuffix) throws Exception {
        Path root = moduleRoot();
        assertTrue(read(root.resolve(Path.of("src", flavor, "res", "layout", layoutPrefix + "_setting_player.xml"))).contains("@+id/autoSkipIntroOutro"));
        assertTrue(read(root.resolve(Path.of("src", flavor, "java", "com", "fongmi", "android", "tv", "ui", classSuffix.equals("Activity") ? "activity" : "fragment", "SettingPlayer" + classSuffix + ".java"))).contains("autoSkipIntroOutro"));
        assertFalse(read(root.resolve(Path.of("src", flavor, "res", "layout", layoutPrefix + "_setting_personal.xml"))).contains("@+id/autoSkipIntroOutro"));
        assertFalse(read(root.resolve(Path.of("src", flavor, "java", "com", "fongmi", "android", "tv", "ui", classSuffix.equals("Activity") ? "activity" : "fragment", "SettingPersonal" + classSuffix + ".java"))).contains("autoSkipIntroOutro"));
    }

    private static void assertPersonalOwnsEpisodeHistory(String flavor, String layoutPrefix, String classSuffix) throws Exception {
        Path root = moduleRoot();
        String packageName = classSuffix.equals("Activity") ? "activity" : "fragment";
        String layout = read(root.resolve(Path.of("src", flavor, "res", "layout", layoutPrefix + "_setting_personal.xml")));
        String source = read(root.resolve(Path.of("src", flavor, "java", "com", "fongmi", "android", "tv", "ui", packageName, "SettingPersonal" + classSuffix + ".java")));

        assertTrue(layout.contains("@+id/episodeHistory"));
        assertTrue(source.contains("Setting.putEpisodeHistory(!Setting.isEpisodeHistory())"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path moduleRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return Path.of(".");
        return Path.of("app");
    }
}
