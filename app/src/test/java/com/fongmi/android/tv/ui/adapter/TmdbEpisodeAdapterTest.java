package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeAdapterTest {

    @Test
    public void nativeEnhancedPhoneGridUsesReadableCleanTitle() {
        assertEquals("2. 觉醒", TmdbEpisodeAdapter.nativeEnhancedIndexTitle("完美世界_02 2. 觉醒", "2. 觉醒", true, TmdbEpisodeAdapter.Mode.GRID));
        assertEquals(14f, TmdbEpisodeAdapter.nativeEnhancedIndexTextSize(true, TmdbEpisodeAdapter.Mode.GRID), 0f);
    }

    @Test
    public void nativeEnhancedLargePagesKeepSharedFallbackArtwork() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("private boolean shouldSuppressSharedFallbackVisuals()");

        assertTrue("native-enhanced TMDB episode cards must keep fallback artwork visible",
                method >= 0
                        && source.indexOf("return false;", method) > method
                        && source.indexOf("String imageUrl = !TextUtils.isEmpty(stillUrl) ? stillUrl : (allowFallback && !suppressSharedFallback ? fallbackStillUrl : \"\");") > method);
    }

    @Test
    public void episodeCardStyleChangesRebindWithoutRepeatedLayoutRequests() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int setMode = source.indexOf("public void setMode(Mode mode)");
        int setGridSpan = source.indexOf("public void setGridSpanCount(int gridSpanCount)");
        int applyCardSize = source.indexOf("private void applyCardSize(ViewHolder holder, boolean compact)");

        assertTrue("TMDB episode adapter must rebind visible cards when grid/list style changes",
                setMode >= 0
                        && source.indexOf("if (this.mode == value) return;", setMode) > setMode
                        && source.indexOf("if (!items.isEmpty()) notifyDataSetChanged();", setMode) > setMode
                        && setGridSpan > setMode
                        && source.indexOf("if (!items.isEmpty() && mode == Mode.GRID) notifyDataSetChanged();", setGridSpan) > setGridSpan);
        assertTrue("TMDB episode card binding must avoid setLayoutParams when size and margins are unchanged",
                applyCardSize >= 0
                        && source.indexOf("boolean layoutChanged", applyCardSize) > applyCardSize
                        && source.indexOf("if (layoutChanged) root.setLayoutParams(params);", applyCardSize) > applyCardSize
                        && source.indexOf("if (scrimParams.height != scrimHeight)", applyCardSize) > applyCardSize);
    }

    @Test
    public void unchangedEpisodeViewportDoesNotNotifyWholePageAgain() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected)");
        int clear = source.indexOf("items.clear();", method);
        int notify = source.indexOf("notifyDataSetChanged();", clear);

        assertTrue("TMDB episode adapter must skip full rebinds when the page data is unchanged",
                source.indexOf("if (sameItems(episodes, tmdbEpisodes, numbers))", method) > method
                        && source.indexOf("setSelected(selected);", method) > method
                        && source.indexOf("private boolean sameItems(", notify) > notify);
    }

    private static String tmdbEpisodeAdapterSource() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java"));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
