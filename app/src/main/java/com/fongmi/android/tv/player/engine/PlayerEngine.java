package com.fongmi.android.tv.player.engine;

import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.lut.MpvLutShader;

import java.util.Collections;
import java.util.List;

public interface PlayerEngine {

    int SOFT = 0;
    int HARD = 1;

    Player getPlayer();

    void release();

    Player rebuild(Player.Listener listener);

    int getDecode();

    void setDecode(int decode);

    boolean isHard();

    String getDecodeText();

    void start(PlaySpec spec);

    default void start(PlaySpec spec, boolean playWhenReady) {
        start(spec);
    }

    default void start(PlaySpec spec, long position, boolean playWhenReady) {
        start(spec, playWhenReady);
    }

    default void restart(PlaySpec spec, long position, boolean playWhenReady) {
        start(spec, position, playWhenReady);
    }

    default void stop() {
        getPlayer().stop();
    }

    void setMetadata(MediaMetadata data);

    boolean isLive();

    boolean isVod();

    void setTrack(List<Track> tracks);

    void resetTrack();

    default void resetTrack(int type) {
        resetTrack();
    }

    boolean haveTrack(int type);

    Tracks getCurrentTracks();

    default boolean supportsVideoEffects() {
        return false;
    }

    default void setVideoEffects(List<Effect> effects) {
    }

    default boolean supportsNativeLut() {
        return false;
    }

    default boolean supportsLut() {
        return supportsVideoEffects() || supportsNativeLut();
    }

    default void setNativeLutShader(MpvLutShader shader) {
    }

    default Format getVideoFormat() {
        return null;
    }

    default PlayerCacheState getCacheState() {
        return PlayerCacheState.empty();
    }

    default String getRenderDiagnostics() {
        return "";
    }

    default String getRuntimeDiagnostics() {
        return "";
    }

    default long getDroppedFrames() {
        return 0;
    }

    default boolean supportsSubtitleStyle() {
        return false;
    }

    default String getAudioSpdifCodecs() {
        return "";
    }

    default void setSubtitleStyle(float textSize, float position) {
    }

    default boolean supportsSecondarySubtitle() {
        return false;
    }

    default boolean isSecondarySubtitleSelected(Format format) {
        return false;
    }

    default void setSecondarySubtitleTrack(Track track) {
    }

    default boolean haveTitle() {
        return false;
    }

    default boolean isRepeatOne() {
        return false;
    }

    default void setRepeatOne(boolean repeat) {
    }

    default List<MediaEdition> getCurrentMediaEditions() {
        return Collections.emptyList();
    }

    default boolean selectEdition(MediaEdition edition) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        RELOAD,
        DECODE,
        FATAL
    }
}
