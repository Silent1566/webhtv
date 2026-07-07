package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class FlagMergeEpisodesTest {

    @Test
    public void mergeEpisodes_updatesTmdbMetadataForExistingEpisode() {
        Flag target = new Flag();
        Episode existing = Episode.create("第1集", "http://example.test/1");
        target.getEpisodes().add(existing);

        Episode enriched = Episode.create("第1集", "http://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "Pilot", "", "", "https://image.test/still.jpg", 0, 0);
        enriched.setTmdbEpisode(tmdbEpisode);
        enriched.setDisplayName("第1集 Pilot");

        target.mergeEpisodes(Collections.singletonList(enriched), false);

        assertEquals(1, target.getEpisodes().size());
        assertSame(existing, target.getEpisodes().get(0));
        assertSame(tmdbEpisode, existing.getTmdbEpisode());
        assertEquals("第1集 Pilot", existing.getDisplayName());
    }
}
