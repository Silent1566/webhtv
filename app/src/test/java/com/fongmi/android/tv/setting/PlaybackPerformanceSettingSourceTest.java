package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PlaybackPerformanceSettingSourceTest {

    @Test
    public void originalProfileKeepsPersistedCustomValueStable() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String method = methodBody(source, "public static void applyOriginal()", "public static void markCustom()");

        assertTrue(source.contains("PROFILE_CUSTOM = 2"));
        assertTrue(source.contains("PROFILE_ORIGINAL = 4"));
        assertTrue(source.contains("PROFILE_AUTO = 5"));
        assertTrue(source.contains("profile == PROFILE_ORIGINAL"));
        assertTrue(method.contains("putCurrentProfile(PROFILE_ORIGINAL)"));
    }

    @Test
    public void originalDefaultsMatchBehaviorBeforePerformancePresets() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String method = methodBody(source, "public static void applyOriginal()", "public static void markCustom()");

        assertContainsAll(method,
                "put(KEY_CODEC_ASYNC_QUEUEING, false)",
                "put(KEY_DYNAMIC_SCHEDULING, false)",
                "put(KEY_VIDEO_DURATION_PROGRESS, false)",
                "put(KEY_LATE_DROP_INPUT, false)",
                "put(KEY_TRACK_LIMIT, false)",
                "put(KEY_ADAPTIVE_DOWNGRADE, false)",
                "put(KEY_LOAD_ONLY_SELECTED_TRACKS, false)",
                "put(KEY_SURFACE_FIXED_SIZE, false)",
                "put(KEY_DECODER_FALLBACK, true)",
                "put(KEY_SOFT_VIDEO_TUNE, true)",
                "put(KEY_HIGH_BUFFER, false)",
                "put(KEY_BANDWIDTH_METER, false)",
                "Prefers.put(\"render\", PlayerSetting.RENDER_SURFACE)",
                "Prefers.put(\"tunnel\", false)",
                "Prefers.put(\"buffer\", 1)",
                "Prefers.put(\"buffer_bytes\", 0)",
                "Prefers.put(\"back_buffer\", 0)",
                "Prefers.put(\"play_cache\", 0)",
                "Prefers.put(\"preload\", false)",
                "Prefers.put(\"preload_threads\", 1)",
                "Prefers.put(\"preload_size\", 128)",
                "Prefers.put(\"preload_time\", 120)",
                "Prefers.put(\"audio_pass_through\", true)",
                "Prefers.put(\"prefer_aac\", false)",
                "Prefers.put(\"audio_prefer\", false)",
                "Prefers.put(\"video_prefer\", false)",
                "Prefers.put(\"exo_4k_compat\", false)",
                "putCurrentProfile(PROFILE_ORIGINAL)");
    }

    @Test
    public void performanceDialogExposesOriginalDefaultsPreset() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));

        assertTrue(source.contains("R.string.player_performance_original"));
        assertTrue(source.contains("PlaybackPerformanceSetting.applyOriginal()"));
        assertTrue(source.contains("case PlaybackPerformanceSetting.PROFILE_ORIGINAL -> 4"));

        String catalog = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceCatalog.java"));
        assertTrue(catalog.contains("option(PROFILE, BASIC, \"性能配置\", profileDescription(kernel))"));
        assertTrue(catalog.contains("自动档"));
    }

    @Test
    public void originalProfileUsesShortChineseLabel() throws Exception {
        String setting = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String strings = read(sourcePath("main", "res", "values-zh-rCN", "strings.xml"));

        assertTrue(setting.contains("case PROFILE_ORIGINAL -> \"原版\";"));
        assertTrue(strings.contains("<string name=\"player_performance_original\">原版</string>"));
    }

    @Test
    public void performanceDialogHighlightsSelectedProfile() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String createView = methodBody(source, "private View createView", "private void showHelpDialog");
        String refresh = methodBody(source, "private void refresh()", "private TabLayout createProfileTabs()");
        String sync = methodBody(source, "private void syncProfileTabs(TabLayout tabs)", "private int profileAt");

        assertContainsAll(source,
                "private TabLayout createProfileTabs()",
                "tabs.selectTab(position < 0 ? null : tabs.getTabAt(position))",
                "case PlaybackPerformanceSetting.PROFILE_ORIGINAL -> 4");
        assertTrue(createView.contains("syncProfileTabs();"));
        assertTrue(refresh.contains("syncProfileTabs();"));
        assertTrue(sync.contains("profilePosition(PlaybackPerformanceSetting.getProfile())"));
    }

    @Test
    public void performanceDialogButtonsKeepLabelsVisible() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));
        String actionButton = methodBody(source, "private MaterialButton actionButton", "private MaterialButton closeButton");

        assertContainsAll(actionButton,
                "button.setMaxLines(1)",
                "button.setIncludeFontPadding(false)",
                "button.setPadding(dp(6), 0, dp(6), 0)",
                "TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP)");
    }

    private static void assertContainsAll(String source, String... values) {
        for (String value : values) assertTrue("Missing: " + value, source.contains(value));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
