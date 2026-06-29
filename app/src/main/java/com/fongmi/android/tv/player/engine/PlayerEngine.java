package com.fongmi.android.tv.player.engine;

import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaTitle;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Track;

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

    default Format getVideoFormat() {
        return null;
    }

    default boolean haveTitle() {
        return false;
    }

    default boolean isRepeatOne() {
        return false;
    }

    default void setRepeatOne(boolean repeat) {
    }

    default List<MediaTitle> getCurrentMediaTitles() {
        return Collections.emptyList();
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
