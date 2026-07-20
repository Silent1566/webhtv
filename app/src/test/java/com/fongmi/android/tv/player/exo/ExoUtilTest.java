package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultRenderersFactory;

import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Test;

public class ExoUtilTest {

    @Test
    public void getRenderMode_keepsPlatformRendererFirstForHardDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, ExoUtil.getRenderMode(PlayerEngine.HARD));
    }

    @Test
    public void getRenderMode_prefersExtensionRendererForSoftDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, ExoUtil.getRenderMode(PlayerEngine.SOFT));
    }

    @Test
    public void getFfmpegVideoRenderMode_keepsFfmpegAsFallbackForHardDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, ExoUtil.getFfmpegVideoRenderMode(ExoUtil.getRenderMode(PlayerEngine.HARD)));
    }

    @Test
    public void getFfmpegVideoRenderMode_prefersFfmpegForSoftDecode() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, ExoUtil.getFfmpegVideoRenderMode(ExoUtil.getRenderMode(PlayerEngine.SOFT)));
    }

    @Test
    public void ffmpegRendererPolicy_usesFullNextLibRenderersInNextLibMode() {
        assertTrue(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_NEXTLIB));
        assertTrue(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_NEXTLIB));
    }

    @Test
    public void ffmpegRendererPolicy_usesAudioAndVideoFallbackInSimpleMode() {
        assertTrue(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_SIMPLE));
        assertTrue(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_SIMPLE));
    }

    @Test
    public void ffmpegRendererPolicy_disablesNextLibInOfficialMode() {
        assertFalse(ExoUtil.useFfmpegAudioFallback(PlayerSetting.FFMPEG_MODE_OFFICIAL));
        assertFalse(ExoUtil.useFfmpegVideoRenderer(PlayerSetting.FFMPEG_MODE_OFFICIAL));
    }
}
