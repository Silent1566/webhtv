package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbUIAdapterTest {

    @Test
    public void applyTmdbTitle_updatesVodNameToScrapedTitle() {
        Vod vod = new FakeVod();
        vod.setName("源站标题");
        TmdbItem item = new TmdbItem(123, "tv", "刮削后的标题", "", "", "", "");

        assertTrue(TmdbUIAdapter.applyTmdbTitle(vod, item));

        assertEquals("刮削后的标题", vod.getName());
    }

    @Test
    public void autoMatchSkipsCachedSplitSeasonVariantBeforeSearching() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "TmdbUIAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cacheHit = source.indexOf("auto match cache hit");
        int skipCheck = source.indexOf("isCachedSplitSeasonMismatch(videoName, vod, matched)", cacheHit);
        int search = source.indexOf("tmdbMatcher.searchAndMatch(videoName, vod)", cacheHit);

        assertTrue("TMDB UI adapter must check cached matches for split-season duplicates", skipCheck > cacheHit);
        assertTrue("split-season cache check must run before falling back to TMDB search", search > skipCheck);
        assertTrue("split-season cache check must use TMDB detail original_name/name fields",
                source.contains("TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(videoName, vod), detail)"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static final class FakeVod extends Vod {

        private String name;

        @Override
        public String getName() {
            return name == null ? "" : name;
        }

        @Override
        public void setName(String vodName) {
            name = vodName;
        }
    }
}
