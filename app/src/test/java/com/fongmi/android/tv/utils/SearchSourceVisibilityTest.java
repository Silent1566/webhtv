package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchSourceVisibilityTest {

    @Test
    public void sourceWithVisibleResultsIsAlwaysShown() {
        assertTrue(SearchSourceVisibility.shouldShow(0, false, true));
        assertTrue(SearchSourceVisibility.shouldShow(80, false, true));
        assertTrue(SearchSourceVisibility.shouldShow(100, true, true));
    }

    @Test
    public void unlimitedModePreservesExistingSourceOrderBehavior() {
        assertTrue(SearchSourceVisibility.shouldShow(0, true, false));
        assertFalse(SearchSourceVisibility.shouldShow(0, false, false));
        assertTrue(SearchSourceVisibility.shouldShow(-1, true, false));
        assertFalse(SearchSourceVisibility.shouldShow(-1, false, false));
    }

    @Test
    public void everyFilteredModeHidesSourcesWithoutMatches() {
        assertFalse(SearchSourceVisibility.shouldShow(1, true, false));
        assertFalse(SearchSourceVisibility.shouldShow(80, false, false));
        assertFalse(SearchSourceVisibility.shouldShow(100, true, false));
    }

    @Test
    public void hiddenSourceAppearsAsSoonAsItGetsAMatch() {
        assertFalse(SearchSourceVisibility.shouldShow(60, true, false));
        assertTrue(SearchSourceVisibility.shouldShow(60, true, true));
    }
}