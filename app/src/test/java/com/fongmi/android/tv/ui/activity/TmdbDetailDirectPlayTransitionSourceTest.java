package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TmdbDetailDirectPlayTransitionSourceTest {

    private static final Path SOURCE = Paths.get("src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

    @Test
    public void firstDirectPlayEntersFullscreenBeforeStartingPlayback() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String playMethod = methodBody(source, "private void playDetailFullscreen()");

        int enterFullscreen = playMethod.indexOf("enterInlineFullscreen();");
        int startPlayback = playMethod.indexOf("if (!current) playInline();");
        assertTrue(enterFullscreen >= 0);
        assertTrue(startPlayback > enterFullscreen);
        assertTrue(playMethod.contains("detailPlayerFullscreenPending = false;"));
        assertFalse(playMethod.contains("detailPlayerFullscreenPending = !current;"));
        assertFalse(playMethod.contains("revealDetailPlayerFullscreen();"));
        assertTrue(source.contains("protected void onFirstFrameRendered()"));
    }

    @Test
    public void closingDirectPlayClearsPendingTransitionState() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String closeMethod = methodBody(source, "private void closeDetailFullscreenPlayer()");

        assertTrue(closeMethod.contains("detailPlayerFullscreenPending = false;"));
        assertTrue(closeMethod.contains("inlinePlaybackGeneration++;"));
        assertTrue(closeMethod.contains("currentInlineResult = null;"));
    }

    @Test
    public void directPlaybackSnapshotUsesLanguageIdentityAndUnifiedOverviewPolicy() throws Exception {
        String source = Files.readString(
                Paths.get("src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem(), currentTmdbLanguage())"));
        assertFalse(source.contains("TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem())"));
        assertTrue(source.contains("translatedOverview(detail, currentTmdbConfig())"));
        assertFalse(source.contains("cachedTmdbOverviewForLanguage(translations, \"zh-CN\")"));
    }

    /**
     * 作废在途取址请求时必须一并释放它留下的加载标记。
     *
     * <p>{@code refreshAndSwitchInlinePlayer} 与 {@code cancelPendingInlinePlayerSwitch} 都只自增代际，
     * 在途回调会因「不再是最新请求」直接返回、永不执行自身的 {@code inlinePlaybackPending = false}；
     * 标记残留会让详情页加载圈永久留在屏上（见 {@code updateInlineLoading} 的首个条件），
     * 并让 {@code isSamePendingInlinePlayback} 恒真，同一集无法再次起播。
     */
    @Test
    public void cancellingAnInlinePlaybackRequestReleasesItsLoadingFlag() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        String switchMethod = methodBody(source, "private boolean refreshAndSwitchInlinePlayer(int playerType)");
        assertFalse("refreshAndSwitchInlinePlayer must exist", switchMethod.isEmpty());
        int switchGeneration = switchMethod.indexOf("++inlinePlaybackGeneration");
        int switchRelease = switchMethod.indexOf("inlinePlaybackPending = false;");
        int switchSubmit = switchMethod.indexOf("detailTasks.submit(");
        assertTrue("refreshAndSwitchInlinePlayer must release the loading flag of the request it invalidates",
                switchGeneration >= 0 && switchRelease > switchGeneration);
        assertTrue("the release must happen synchronously, before the replacement request is submitted",
                switchSubmit > switchRelease);

        String cancelMethod = methodBody(source, "private void cancelPendingInlinePlayerSwitch()");
        assertFalse("cancelPendingInlinePlayerSwitch must exist", cancelMethod.isEmpty());
        assertTrue("cancelPendingInlinePlayerSwitch must release the loading flag of the request it invalidates",
                cancelMethod.contains("inlinePlaybackGeneration++;")
                        && cancelMethod.contains("inlinePlaybackPending = false;"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        return "";
    }
}
