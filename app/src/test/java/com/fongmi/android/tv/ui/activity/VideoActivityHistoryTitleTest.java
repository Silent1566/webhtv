package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class VideoActivityHistoryTitleTest {

    @Test
    public void videoActivitiesPersistDisplayedEpisodeTitlesAndRefreshAsyncEnrichment() throws Exception {
        for (Path sourcePath : List.of(videoActivity("mobile"), videoActivity("leanback"))) {
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String intentSelection = methodBody(source, "private void applyIntentPlaybackSelection(Vod item)");
            String saveHistory = methodBody(source, "private void saveHistory(boolean exit)");
            String updateHistory = methodBody(source, "private void updateHistory(Episode item)");
            String updateVod = methodBody(source, "private void updateVod(Vod item)");
            String refreshTitle = methodBody(source, "private boolean refreshCurrentHistoryEpisodeTitle()");

            assertTrue(sourcePath + " must persist the displayed/scraped title for intent-selected episodes",
                    intentSelection.contains("mHistory.setVodRemarks(getHistoryEpisodeName(episode));"));
            assertTrue(sourcePath + " must compare episodes by URL before falling back to source names",
                    updateHistory.contains("item.matches(mHistory.getEpisode())"));
            assertTrue(sourcePath + " must persist the displayed/scraped title whenever playback changes episodes",
                    updateHistory.contains("mHistory.setVodRemarks(getHistoryEpisodeName(item));"));
            assertTrue(sourcePath + " must keep position-cache keys on stable source episode names when saving",
                    saveHistory.contains("getCurrentHistoryEpisodeCacheName()"));
            assertTrue(sourcePath + " must keep position-cache keys stable when switching episodes",
                    updateHistory.contains("getCurrentHistoryEpisodeCacheName()"));
            String cacheName = methodBody(source, "private String getCurrentHistoryEpisodeCacheName()");
            assertTrue(sourcePath + " must skip cache writes instead of falling back to a scraped display title",
                    cacheName.contains("return \"\";") && !cacheName.contains("return mHistory.getVodRemarks();"));
            assertTrue(sourcePath + " must refresh the current history title after asynchronous TMDB enrichment",
                    updateVod.contains("boolean episodeTitleChanged = refreshCurrentHistoryEpisodeTitle();"));
            assertTrue(sourcePath + " must save a history-only TMDB title refresh",
                    updateVod.contains("keyChanged || pic || name || episodeTitleChanged"));
            assertTrue(sourcePath + " must resolve the enriched episode against the current history URL",
                    refreshTitle.contains("flag.find(mHistory.getEpisode(), true)"));
            assertTrue(sourcePath + " must copy the resolved TMDB history title into history",
                    refreshTitle.contains("getHistoryEpisodeName(episode)"));
            String historyTitle = methodBody(source, "private String getHistoryEpisodeName(Episode episode)");
            assertTrue(sourcePath + " must resolve history titles from durable TMDB metadata/title tables",
                    historyTitle.contains("EpisodeHistoryTitleResolver.resolve(")
                            && historyTitle.contains("getEpisodeTitles()"));
            assertTrue(sourcePath + " must not depend on the mutable presentation displayName",
                    !historyTitle.contains("getDisplayName()") && !historyTitle.contains("getHistoryName()"));
            if (source.contains("private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)")) {
                String fastPlaybackHistory = methodBody(source, "private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)");
                assertTrue(sourcePath + " must also preserve scraped titles on the TV fast-playback path",
                        fastPlaybackHistory.contains("mHistory.setVodRemarks(getHistoryEpisodeName(episode));"));
            }
        }
    }

    @Test
    public void allDetailPlaybackModesKeepTheirScrapedHistoryPath() throws Exception {
        Path sourcePath = mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        String onPlay = methodBody(source, "private void onPlay()");
        String inlineHistory = methodBody(source, "private void updateInlineHistory(Episode item)");
        String defaultPlayback = methodBody(source, "private void playDefaultPlayback()");
        String fastTitles = methodBody(source, "private ArrayList<String> fastPlaybackEpisodeTitles()");

        assertTrue("fusion/player/colorful modes must update the TMDB-formatted history title before branching",
                onPlay.contains("updateInlineHistory(selectedEpisode);"));
        assertTrue("fusion mode must keep inline playback", onPlay.contains("if (isFusionMode()) playInline();"));
        assertTrue("player-detail mode must keep fullscreen inline playback", onPlay.contains("else if (isPlayerMode()) playDetailFullscreen();"));
        assertTrue("colorful detail mode must keep the external VideoActivity path", onPlay.contains("else playDefaultPlayback();"));
        assertTrue("inline modes must store the formatted scraped episode title",
                inlineHistory.contains("history.setVodRemarks(historyEpisodeTitle(item));"));
        assertTrue("external colorful playback must forward the scraped episode title table",
                defaultPlayback.contains("fastPlaybackEpisodeTitles()"));
        assertTrue("the colorful fast-playback payload must contain the TMDB title rather than the raw source name",
                fastTitles.contains("tmdbEpisodeTitle(number)")
                        && !fastTitles.contains("playbackEpisodeName()"));
    }

    private static Path videoActivity(String sourceSet) {
        Path moduleRelative = Path.of("src", sourceSet, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app").resolve(moduleRelative);
    }

    private static Path mainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app").resolve(moduleRelative);
    }

    private static String methodBody(String source, String startToken) {
        int start = source.indexOf(startToken);
        assertTrue("Missing source token: " + startToken, start >= 0);
        int open = source.indexOf('{', start + startToken.length());
        assertTrue("Missing method body for: " + startToken, open > start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method body for: " + startToken);
    }
}
