package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;

import androidx.media3.exoplayer.DefaultRenderersFactory;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;

public class ExoUtilTest {

    @Test
    public void getRenderMode_keepsPlatformRendererFirstForHardDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, ExoUtil.getRenderMode(PlayerEngine.HARD));
    }

    @Test
    public void getRenderMode_prefersExtensionRendererForSoftDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, ExoUtil.getRenderMode(PlayerEngine.SOFT));
    }
}
