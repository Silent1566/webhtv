package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FusionDetailControllerTest {

    @Test
    public void fusionMode_showsInlinePlayerWithoutAutoPlay() {
        TmdbDetailModeController controller = new FusionDetailController(null);

        assertTrue(controller.shouldShowInlinePlayer());
        assertFalse(controller.shouldAutoPlay());
    }
}
