package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerSettingTest {

    @Test
    public void nativeVideoOutput_includesNativePlayers() {
        assertFalse(PlayerSetting.useNativeVideoOutput(PlayerSetting.EXO));
        assertTrue(PlayerSetting.useNativeVideoOutput(PlayerSetting.IJK));
        assertTrue(PlayerSetting.useNativeVideoOutput(PlayerSetting.SYSTEM));
        assertTrue(PlayerSetting.useNativeVideoOutput(PlayerSetting.MPV));
    }

    @Test
    public void nativeVideoOutput_forcesSurfaceRender() {
        assertEquals(0, PlayerSetting.getRender(PlayerSetting.IJK));
        assertEquals(0, PlayerSetting.getRender(PlayerSetting.SYSTEM));
        assertEquals(0, PlayerSetting.getRender(PlayerSetting.MPV));
    }

    @Test
    public void nextPlayer_cyclesThroughAllAvailablePlayers() {
        assertEquals(PlayerSetting.IJK, PlayerSetting.nextPlayer(PlayerSetting.EXO));
        assertEquals(PlayerSetting.SYSTEM, PlayerSetting.nextPlayer(PlayerSetting.IJK));
        assertEquals(PlayerSetting.MPV, PlayerSetting.nextPlayer(PlayerSetting.SYSTEM));
        assertEquals(PlayerSetting.EXO, PlayerSetting.nextPlayer(PlayerSetting.MPV));
    }

    @Test
    public void sanitizeFFmpegMode_allowsKnownModes() {
        assertEquals(PlayerSetting.FFMPEG_MODE_NEXTLIB, PlayerSetting.sanitizeFFmpegMode(PlayerSetting.FFMPEG_MODE_NEXTLIB, PlayerSetting.FFMPEG_MODE_SIMPLE));
        assertEquals(PlayerSetting.FFMPEG_MODE_OFFICIAL, PlayerSetting.sanitizeFFmpegMode(PlayerSetting.FFMPEG_MODE_OFFICIAL, PlayerSetting.FFMPEG_MODE_SIMPLE));
        assertEquals(PlayerSetting.FFMPEG_MODE_SIMPLE, PlayerSetting.sanitizeFFmpegMode(PlayerSetting.FFMPEG_MODE_SIMPLE, PlayerSetting.FFMPEG_MODE_NEXTLIB));
    }

    @Test
    public void sanitizeFFmpegMode_fallsBackForUnknownMode() {
        assertEquals(PlayerSetting.FFMPEG_MODE_NEXTLIB, PlayerSetting.sanitizeFFmpegMode(-1, PlayerSetting.FFMPEG_MODE_NEXTLIB));
        assertEquals(PlayerSetting.FFMPEG_MODE_SIMPLE, PlayerSetting.sanitizeFFmpegMode(3, PlayerSetting.FFMPEG_MODE_SIMPLE));
    }
}
