package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerDetailControllerTest {

    @Test
    public void playerMode_autoPlaysWithoutInitialInlinePlayer() {
        TmdbDetailModeController controller = new PlayerDetailController(null);

        assertFalse(controller.shouldShowInlinePlayer());
        assertTrue(controller.shouldAutoPlay());
    }
}
