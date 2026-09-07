package com.fongmi.android.tv.player.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MpvHardwareDecodePolicyTest {

    @Test
    public void disablesAutomaticSoftwareFallback() {
        assertEquals("hwdec-software-fallback",
                MpvPlayerEngine.HWDEC_SOFTWARE_FALLBACK_OPTION);
        assertEquals("no", MpvPlayerEngine.hardwareDecodeSoftwareFallbackOption());
    }
}
