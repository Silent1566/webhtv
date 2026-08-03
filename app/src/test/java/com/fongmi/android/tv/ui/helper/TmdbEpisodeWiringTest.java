package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbEpisodeWiringTest {

    @Test
    public void standaloneDetailModesShareEpisodeInfoAcrossDetailAndPlayback() throws Exception {
        String activity = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertTrue(activity.contains("TmdbEpisodeInfo episodeInfo = tmdbEpisodeInfo();"));
        assertTrue(activity.contains("addMetaChip(episodeInfo.detailText(this));"));
        assertTrue(activity.contains("if (!episodeInfo.isSeasonScoped())"));
        assertTrue(activity.contains("addMetaChip(currentSeasonContextLabel());"));
        assertTrue(activity.contains("String progress = tmdbEpisodeInfo().compactText(this);"));
        assertTrue(activity.contains("item.setRemarks(coalesce(tmdbEpisodeInfo().detailText(this), getMarkText(), vod.getRemarks()));"));
    }

    @Test
    public void embeddedTmdbHeaderShowsEpisodeInfoInNativeAndFusionLayouts() throws Exception {
        String header = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java")));
        String layout = read(mainRes().resolve(Path.of("layout", "view_tmdb_header.xml")));

        assertTrue(header.contains("String episodeInfo = adapter.getEpisodeDetailText();"));
        assertTrue(header.contains("buildFusionSubtitle(detail, adapter.getRatingText(), episodeInfo)"));
        assertTrue(header.contains("!adapter.getEpisodeInfo().isSeasonScoped()"));
        assertTrue(layout.contains("android:id=\"@+id/tmdbMeta\""));
        assertTrue(layout.contains("android:maxLines=\"2\""));
    }

    @Test
    public void bothVideoActivitiesAppendCompactEpisodeInfoToExistingOsdTitle() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("String episodeInfo = tmdbEpisodeCompactText();"));
        assertTrue(leanback.contains("setText(mBinding.remark, 0, episodeInfo);"));
        assertTrue(mobile.contains("String episodeInfo = tmdbEpisodeCompactText();"));
    }

    @Test
    public void detailDirectPlaybackConsumesPreloadedTmdbEpisodeRemarkInBothVideoActivities() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String mobile = read(flavorJava("mobile").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("private String getTmdbVodRemark()"));
        assertTrue(leanback.contains("applyIntentTmdbVodRemark(item);"));
        assertTrue(leanback.contains("item.setRemarks(remark);"));
        assertTrue(leanback.contains("getIntent().removeExtra(\"tmdb_vod_remark\");"));
        assertTrue(mobile.contains("private String getTmdbVodRemark()"));
        assertTrue(mobile.contains("applyIntentTmdbVodRemark(item);"));
        assertTrue(mobile.contains("item.setRemarks(remark);"));
        assertTrue(mobile.contains("getIntent().removeExtra(\"tmdb_vod_remark\");"));
    }

    @Test
    public void leanbackNativeEnhancedKeepsEpisodeInfoVisibleAfterPlayerRefresh() throws Exception {
        String leanback = read(flavorJava("leanback").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue(leanback.contains("mBinding.remark.setVisibility(shouldShowVideoDetailRemark(visible) ? View.VISIBLE : View.GONE);"));
        assertTrue(leanback.contains("private boolean shouldShowVideoDetailRemark(boolean visible)"));
        assertTrue(leanback.contains("String episodeInfo = mTmdbUIAdapter.getEpisodeDetailText();"));
        assertTrue(leanback.contains("TextUtils.equals(mBinding.remark.getText(), episodeInfo)"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path mainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "main", "java");
    }

    private static Path mainRes() {
        Path moduleRelative = Path.of("src", "main", "res");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "main", "res");
    }

    private static Path flavorJava(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", flavor, "java");
    }
}
