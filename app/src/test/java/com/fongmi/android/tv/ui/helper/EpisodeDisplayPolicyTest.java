package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeDisplayPolicyTest {

    @Test
    public void nativeMode_usesNativeEpisodeDisplayEvenIfEpisodeHasTmdbObject() {
        assertFalse(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(false, Collections.singletonList(tmdbEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(false, false, Collections.singletonList(tmdbEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(false, true, Collections.singletonList(tmdbEpisode())));
    }

    @Test
    public void tmdbModeWithoutMatchedEpisodeData_usesNativeEpisodeDisplayAfterLoadCompletes() {
        assertFalse(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(nativeEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, false, true, true, Collections.singletonList(nativeEpisode())));
        assertFalse(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Collections.singletonList(nativeEpisode())));
    }

    @Test
    public void tmdbModeWhileLoading_canShowTemporaryLoadingChrome() {
        assertTrue(EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, Collections.singletonList(nativeEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, true, Collections.singletonList(nativeEpisode())));
    }

    @Test
    public void tmdbModeWithMatchedEpisodeData_usesTmdbCardsAndChrome() {
        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(Arrays.asList(nativeEpisode(), tmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Arrays.asList(nativeEpisode(), tmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Arrays.asList(nativeEpisode(), tmdbEpisode())));
    }

    @Test
    public void originalEnhancedMode_keepsScrapedEpisodeDataAcrossDifferentlyNamedSourceLines() {
        assertTrue(EpisodeDisplayPolicy.hasTmdbEpisodeData(Collections.singletonList(mismatchedTmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(true, Collections.singletonList(mismatchedTmdbEpisode())));
        assertTrue(EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(true, false, Collections.singletonList(mismatchedTmdbEpisode())));
    }

    @Test
    public void episodeGroup_showsInTmdbDetailLayout() {
        assertTrue(EpisodeDisplayPolicy.shouldShowEpisodeGroup(2, false));
        assertTrue(EpisodeDisplayPolicy.shouldShowEpisodeGroup(2, true));
        assertFalse(EpisodeDisplayPolicy.shouldShowEpisodeGroup(1, false));
    }

    private static Episode nativeEpisode() {
        return Episode.create("第1集", "http://example.test/1");
    }

    private static Episode tmdbEpisode() {
        Episode episode = Episode.create("第2集", "http://example.test/2");
        episode.setTmdbEpisode(new TmdbEpisode(2, "Title", "", "", "", 0, 0));
        return episode;
    }

    private static Episode mismatchedTmdbEpisode() {
        Episode episode = Episode.create("2. Source Title", "http://example.test/2");
        episode.setTmdbEpisode(new TmdbEpisode(2, "Different Title", "", "", "", 0, 0));
        return episode;
    }
}
