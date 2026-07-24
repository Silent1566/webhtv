package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateDialogLayoutTest {

    private static final int SCREEN_HEIGHT = 1080;
    private static final int NORMAL_MIN_HEIGHT = 220;
    private static final int NORMAL_MAX_HEIGHT = 320;
    private static final int DOWNLOAD_MIN_HEIGHT = 160;

    @Test
    public void normalStateKeepsOriginalScrollHeight() {
        int height = UpdateDialogLayout.calculateScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT,
                DOWNLOAD_MIN_HEIGHT,
                false,
                80);

        assertEquals(NORMAL_MAX_HEIGHT, height);
    }

    @Test
    public void downloadStateReservesProgressPanelHeight() {
        int height = UpdateDialogLayout.calculateScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT,
                DOWNLOAD_MIN_HEIGHT,
                true,
                80);

        assertEquals(240, height);
    }

    @Test
    public void downloadStateKeepsMinimumScrollHeight() {
        int height = UpdateDialogLayout.calculateScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT,
                DOWNLOAD_MIN_HEIGHT,
                true,
                200);

        assertEquals(DOWNLOAD_MIN_HEIGHT, height);
    }

    @Test
    public void unchangedProgressPanelHeightSkipsRelayout() {
        assertFalse(UpdateDialogLayout.hasProgressPanelHeightChanged(80, 80));
    }

    @Test
    public void changedProgressPanelHeightTriggersRelayoutAndRecalculatesScrollHeight() {
        int height = UpdateDialogLayout.calculateScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT,
                DOWNLOAD_MIN_HEIGHT,
                true,
                112);

        assertTrue(UpdateDialogLayout.hasProgressPanelHeightChanged(-1, 80));
        assertTrue(UpdateDialogLayout.hasProgressPanelHeightChanged(80, 112));
        assertEquals(208, height);
    }
}
