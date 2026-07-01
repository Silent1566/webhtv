package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeCardPolicyTest {

    @Test
    public void shouldShowCard_whenTmdbEpisodeMatches() {
        assertTrue(EpisodeCardPolicy.shouldShowCard(true, true, false));
    }

    @Test
    public void shouldShowCard_whenTmdbEpisodeMissingButFallbackStillExists() {
        assertTrue(EpisodeCardPolicy.shouldShowCard(true, false, true));
    }

    @Test
    public void shouldUseText_whenTmdbCardModeIsDisabledOrNoImageExists() {
        assertFalse(EpisodeCardPolicy.shouldShowCard(false, true, true));
        assertFalse(EpisodeCardPolicy.shouldShowCard(true, false, false));
    }
}
