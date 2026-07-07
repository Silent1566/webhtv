package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class HistoryAdapterTest {

    @Test
    public void tvHistoryCardsShowPlaybackProgress() throws Exception {
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_vod.xml")));
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "HistoryAdapter.java")));
        String presenter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "HistoryPresenter.java")));
        String keepAdapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "KeepAdapter.java")));

        assertTrue("TV shared vod card must expose a playback progress bar",
                layout.contains("android:id=\"@+id/progress\"")
                        && layout.contains("android:layout_below=\"@+id/image\"")
                        && layout.contains("android:layout_below=\"@+id/progress\""));
        assertBindsPlaybackProgress("TV history page", adapter);
        assertBindsPlaybackProgress("TV home recent row", presenter);
        assertTrue("TV keep page reuses the card layout and must hide the history-only progress bar",
                keepAdapter.contains("holder.binding.progress.setVisibility(View.GONE);"));
    }

    private static void assertBindsPlaybackProgress(String owner, String source) {
        assertTrue(owner + " must calculate duration from history",
                source.contains("Math.max(0, item.getDuration())"));
        assertTrue(owner + " must calculate position from history",
                source.contains("Math.max(0, item.getPosition())"));
        assertTrue(owner + " must set the progress max from duration",
                source.contains("binding.progress.setMax(duration > 0 ? duration : 1);"));
        assertTrue(owner + " must clamp progress to the duration",
                source.contains("binding.progress.setProgress(duration > 0 ? Math.min(progress, duration) : 0"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }
}
