package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpisodeRangePolicyTest {

    @Test
    public void build_usesNativeGroupSizeBreakpoints() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(501, 0, false);

        assertEquals(6, ranges.size());
        assertEquals("1-100", ranges.get(0).label());
        assertEquals(0, ranges.get(0).start());
        assertEquals(100, ranges.get(0).end());
        assertEquals("501", ranges.get(5).label());
    }

    @Test
    public void build_selectsRangeContainingSelectedIndex() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(120, 72, false);

        assertEquals("41-80", ranges.get(1).label());
        assertTrue(ranges.get(1).selected());
    }

    @Test
    public void build_formatsReverseLabelsAgainstOriginalEpisodeNumbers() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(120, 0, true);

        assertEquals("120-81", ranges.get(0).label());
        assertEquals("40-1", ranges.get(2).label());
    }

    @Test
    public void build_capsCardPagesAtFiftyEpisodes() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(501, 0, false, 50);

        assertEquals(11, ranges.size());
        assertEquals("1-50", ranges.get(0).label());
        assertEquals("451-500", ranges.get(9).label());
        assertEquals("501", ranges.get(10).label());
    }

    @Test
    public void gridFallbackImagesRemainAvailableForCardPagesWithoutEpisodeArtwork() {
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 50, false));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 50, true));
    }

    @Test
    public void slice_clampsRangeToItems() {
        List<Integer> items = List.of(1, 2, 3, 4);

        assertEquals(List.of(2, 3), EpisodeRangePolicy.slice(items, new EpisodeRangePolicy.Range("2-3", 1, 3, true)));
        assertEquals(List.of(), EpisodeRangePolicy.slice(items, null));
    }
}
