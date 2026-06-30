package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TmdbEpisodeAdapterTest {

    @Test
    public void nativeEnhancedPhoneGridUsesReadableCleanTitle() {
        assertEquals("2. 觉醒", TmdbEpisodeAdapter.nativeEnhancedIndexTitle("完美世界_02 2. 觉醒", "2. 觉醒", true, TmdbEpisodeAdapter.Mode.GRID));
        assertEquals(14f, TmdbEpisodeAdapter.nativeEnhancedIndexTextSize(true, TmdbEpisodeAdapter.Mode.GRID), 0f);
    }
}
