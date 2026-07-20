package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvStartupBufferPolicyTest {

    @Test
    public void startupDoesNotWaitForRebufferWatermark() {
        assertEquals("yes", MpvStartupBufferPolicy.CACHE_PAUSE);
        assertEquals("no", MpvStartupBufferPolicy.CACHE_PAUSE_INITIAL);
    }

    @Test
    public void performancePriorityReappliesStartupPolicyAfterConfigLoad() {
        assertTrue(MpvStartupBufferPolicy.shouldApplyPerformanceOverlay(true));
    }

    @Test
    public void configPriorityLeavesPostInitValueToMpvConf() {
        assertFalse(MpvStartupBufferPolicy.shouldApplyPerformanceOverlay(false));
    }
}
