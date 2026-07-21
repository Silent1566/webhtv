package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvConfigGpuProcessingTest {

    @Test
    public void ignoresCommentsAndOrdinaryPlaybackOptions() {
        assertFalse(MpvConfigStore.containsGpuVideoProcessing("# vf=scale=1280:720\ncache=yes\nprofile=fast\ninterpolation=no\nvf=\n"));
    }

    @Test
    public void detectsFiltersShadersAndGpuScaling() {
        assertTrue(MpvConfigStore.containsGpuVideoProcessing("vf=scale=1280:720\n"));
        assertTrue(MpvConfigStore.containsGpuVideoProcessing("glsl-shaders=~~/shaders/a.glsl\n"));
        assertTrue(MpvConfigStore.containsGpuVideoProcessing("scale=ewa_lanczos\n"));
    }
}
