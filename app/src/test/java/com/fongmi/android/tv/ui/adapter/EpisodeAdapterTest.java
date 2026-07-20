package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpisodeAdapterTest {

    @Test
    public void tmdbCardTitleSeparatesSourceFileSize() {
        Episode episode = Episode.create("[5.32G] 01.mkv", "https://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "楚云嫁入齐府成...", "2026-06-20", "", "", 0, 47);
        episode.setTmdbEpisode(tmdbEpisode);

        String title = EpisodeAdapter.getCardTitle(episode);

        assertEquals("1. 楚云嫁入齐府成...", title);
        assertEquals("[5.32G]", EpisodeAdapter.getCardFileSize(episode, title, true));
        assertEquals("", EpisodeAdapter.getCardFileSize(episode, title, false));
    }

    @Test
    public void mobileTmdbEpisodeCardsBindFileSizeBadge() throws Exception {
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String horiHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        String gridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String horiLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml")));

        assertTrue("mobile grid TMDB cards must expose a fileSize badge",
                gridLayout.contains("android:id=\"@+id/fileSize\"")
                        && gridHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && gridHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle), showMeta);"));
        assertTrue("mobile horizontal TMDB cards must expose a fileSize badge",
                horiLayout.contains("android:id=\"@+id/fileSize\"")
                        && horiHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && horiHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle));"));
    }

    @Test
    public void leanbackTmdbEpisodeCardsBindFileSizeBadge() throws Exception {
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_card.xml")));

        assertTrue("TV TMDB episode cards must expose a fileSize badge",
                layout.contains("android:id=\"@+id/fileSize\"")
                        && adapter.contains("String cardTitle = getCardTitle(item, tmdbEpisode);")
                        && adapter.contains("binding.cardTitle.setText(cardTitle);")
                        && adapter.contains("bindFileSize(binding, getCardFileSize(item, cardTitle), showMeta);"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
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
