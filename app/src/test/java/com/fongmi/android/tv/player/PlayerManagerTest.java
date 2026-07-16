package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class PlayerManagerTest {

    @Test
    public void nextFallbackAction_obeysConfiguredMode() {
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.SOFT));
    }

    @Test
    public void fallbackDecode_obeysConfiguredMode() {
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.EXO, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, 99));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_blocksFallbackWhileManualSwitchIsPending() {
        assertEquals(true, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsRecoveredManualErrors() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.RECOVERED));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsAutomaticFallbacks() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(false, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void findSubtitleSub_matchesSelectedExternalSubtitleByLabelAndMime() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Sub chinese = Sub.create("Chinese", "/tmp/chinese.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("English")
                .setLanguage("eng")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        Sub matched = PlayerManager.findSubtitleSub(List.of(chinese, english), selected);

        assertSame(english, matched);
    }

    @Test
    public void findSubtitleSub_ignoresInternalSubtitleWithoutMatchingSub() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("Embedded English")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        assertNull(PlayerManager.findSubtitleSub(List.of(english), selected));
    }

    @Test
    public void findSelectedSubtitleSub_usesFirstExternalSubtitleBeforeTracksLoad() {
        Sub manual = Sub.create("Manual", "/tmp/manual.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertSame(manual, PlayerManager.findSelectedSubtitleSub(List.of(manual), Tracks.EMPTY));
    }

    @Test
    public void httpStatus_findsNestedResponseCode() {
        IOException error = new IOException("source", new IOException("Response code: 403"));

        assertEquals(403, PlayerManager.httpStatus(error));
    }

    @Test
    public void httpStatus_returnsZeroWithoutResponseCode() {
        assertEquals(0, PlayerManager.httpStatus(new IOException("Socket closed")));
    }
}
