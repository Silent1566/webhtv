package com.fongmi.android.tv.utils;

public final class SearchSourceVisibility {

    private SearchSourceVisibility() {
    }

    /**
     * Keeps configured empty source rows only when filtering is disabled. Once a similarity filter
     * is active, a source appears dynamically as soon as it has at least one visible match.
     */
    public static boolean shouldShow(int similarity, boolean fixedOrder, boolean hasVisibleResults) {
        return hasVisibleResults || (similarity <= 0 && fixedOrder);
    }
}