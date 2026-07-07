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
    public void nativeEnhancedFileSizeUsesBadgeWithoutDuplicatingTitle() {
        assertEquals("2. 觉醒", TmdbEpisodeAdapter.nativeEnhancedIndexTitle("[5.37G] 2. 觉醒", "2. 觉醒", "[5.37G]", false, TmdbEpisodeAdapter.Mode.LIST));
        assertEquals("[5.37G]", TmdbEpisodeAdapter.nativeEnhancedFileSizeBadge("[5.37G]", "2. 觉醒"));
        assertEquals("", TmdbEpisodeAdapter.nativeEnhancedFileSizeBadge("[5.37G]", "[5.37G] 2. 觉醒"));
    }

    @Test
    public void nativeEnhancedLargePagesKeepSharedFallbackArtwork() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int method = source.indexOf("private boolean shouldSuppressSharedFallbackVisuals()");

        assertTrue("native-enhanced TMDB episode cards must keep fallback artwork visible",
                method >= 0
                        && source.indexOf("return false;", method) > method
                        && source.contains("allowFallback && !suppressSharedFallback ? fallbackStillUrl : \"\""));
    }

    @Test
    public void nativeEnhancedEpisodeCardsBindFileSizeBadge() throws Exception {
        String source = tmdbEpisodeAdapterSource();
        int nativeBranch = source.indexOf("if (isNativeEnhanced())");
        int nextBranch = source.indexOf("} else if (mode == Mode.GRID)", nativeBranch);
        String nativeBody = nativeBranch >= 0 && nextBranch > nativeBranch ? source.substring(nativeBranch, nextBranch) : "";

        assertTrue("native-enhanced TMDB episode cards should bind the file-size badge instead of always hiding it",
                nativeBody.contains("boolean showDate = !TextUtils.isEmpty(holder.binding.date.getText()) && mode == Mode.GRID;")
                        && nativeBody.contains("bindFileSize(holder, nativeEnhancedFileSizeBadge(fileSize, cleanTitle), showDate);")
                        && !nativeBody.contains("holder.binding.fileSize.setVisibility(View.GONE);"));
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
                source.indexOf("if (!forceRefresh && sameItems(episodes, tmdbEpisodes, numbers))", method) > method
                        && source.indexOf("setSelected(selected);", method) > method
                        && source.indexOf("private boolean sameItems(", notify) > notify);
    }

    @Test
    public void themeRefreshUpdatesLightAndAccentWithSingleRebind() throws Exception {
        String adapter = tmdbEpisodeAdapterSource();
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int method = adapter.indexOf("public void setTheme(boolean light, int activeStrokeColor)");
        int methodEnd = adapter.indexOf("public void setFallbackStillUrl", method);
        String body = methodEnd > method ? adapter.substring(method, methodEnd) : adapter.substring(method);

        assertTrue("theme changes should update light and active stroke together",
                method >= 0
                        && body.contains("boolean changed = this.light != light || this.activeStrokeColor != activeStrokeColor;")
                        && body.contains("this.light = light;")
                        && body.contains("this.activeStrokeColor = activeStrokeColor;")
                        && body.contains("if (changed) notifyDataSetChanged();"));
        assertTrue("detail theme refresh should avoid separate full rebinds for light and active stroke",
                activity.contains("episodeAdapter.setTheme(lightTheme, colors.accent);")
                        && !activity.contains("episodeAdapter.setLight(lightTheme);\n            episodeAdapter.setActiveStrokeColor(colors.accent);"));
    }

    @Test
    public void nativeEnhancedEpisodeCardsDoNotShowSelectedGrayOverlay() throws Exception {
        String adapter = tmdbEpisodeAdapterSource();
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_episode.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int nativeFocus = adapter.indexOf("private void applyNativeEnhancedCardFocus");
        int nativeFocusEnd = adapter.indexOf("private boolean isNativeEnhanced()", nativeFocus);
        String nativeFocusBody = nativeFocus >= 0 && nativeFocusEnd > nativeFocus ? adapter.substring(nativeFocus, nativeFocusEnd) : "";

        assertTrue("episode cards should not stack platform ripple/focus overlays over the still image",
                layout.contains("android:defaultFocusHighlightEnabled=\"false\"")
                        && layout.contains("android:stateListAnimator=\"@null\"")
                        && layout.contains("app:rippleColor=\"@android:color/transparent\"")
                        && !layout.contains("?attr/selectableItemBackground"));
        assertTrue("native-enhanced selected episodes should use stroke only, not Material selected/checked state overlays",
                nativeFocusBody.contains("setSelected(false);")
                        && nativeFocusBody.contains("setActivated(false);")
                        && nativeFocusBody.contains("setChecked(false);")
                        && nativeFocusBody.contains("activated ? activeStrokeColor : 0x00000000")
                        && !nativeFocusBody.contains("setSelected(activated)"));
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

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
