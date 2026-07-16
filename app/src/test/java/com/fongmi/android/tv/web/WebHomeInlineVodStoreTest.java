package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebHomeInlineVodStoreTest {

    @Test
    public void shouldRefreshYoutubeEpisode_acceptsFirst403Or410() {
        assertTrue(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://WWW.YouTube.COM/watch?v=test", 403, false));
        assertTrue(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://youtu.be/test", 410, false));
    }

    @Test
    public void shouldRefreshYoutubeEpisode_rejectsRepeatedOrUnrelatedErrors() {
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://www.youtube.com/watch?v=test", 403, true));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://www.youtube.com/watch?v=test", 404, false));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                "other", "https://www.youtube.com/watch?v=test", 403, false));
        assertFalse(WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(
                WebHomeInlineVodStore.KEY, "https://example.com/video", 403, false));
    }
}
