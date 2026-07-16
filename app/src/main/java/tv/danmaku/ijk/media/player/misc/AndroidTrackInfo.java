package tv.danmaku.ijk.media.player.misc;

import android.media.MediaPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tv.danmaku.ijk.media.player.IjkMediaMeta;

/* loaded from: classes3.dex */
public class AndroidTrackInfo implements ITrackInfo {
    private final MediaPlayer.TrackInfo mTrackInfo;
    private int mTrackType = 0;

    private AndroidTrackInfo(MediaPlayer.TrackInfo trackInfo) {
        this.mTrackInfo = trackInfo;
        initTrackType(trackInfo);
    }

    public static List<ITrackInfo> fromMediaPlayer(MediaPlayer mediaPlayer) {
        return fromTrackInfo(mediaPlayer.getTrackInfo());
    }

    private static List<ITrackInfo> fromTrackInfo(MediaPlayer.TrackInfo[] trackInfoArr) {
        if (trackInfoArr == null) {
            return Collections.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        for (MediaPlayer.TrackInfo trackInfo : trackInfoArr) {
            arrayList.add(new AndroidTrackInfo(trackInfo));
        }
        return arrayList;
    }

    private void initTrackType(MediaPlayer.TrackInfo trackInfo) {
        if (trackInfo == null) {
            setTrackType(0);
            return;
        }
        if (trackInfo.getTrackType() == 1) {
            setTrackType(2);
            return;
        }
        if (trackInfo.getTrackType() == 2) {
            setTrackType(1);
        } else if (trackInfo.getTrackType() == 3 || trackInfo.getTrackType() == 4) {
            setTrackType(3);
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getBitrate() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getInteger(IjkMediaMeta.IJKM_KEY_BITRATE);
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getChannelCount() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getInteger("channel-count");
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public float getFps() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getFloat("max-fps-to-encoder");
            }
            return 0.0f;
        } catch (Exception unused) {
            return 0.0f;
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorPrimaries() {
        return "";
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorRange() {
        return "";
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorSpace() {
        return "";
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorTransfer() {
        return "";
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getHeight() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getInteger(IjkMediaMeta.IJKM_KEY_HEIGHT);
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getLanguage() {
        MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
        return trackInfo == null ? "und" : trackInfo.getLanguage();
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getMimeType() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getString("mime");
            }
            return "";
        } catch (Exception unused) {
            return "";
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getTrackType() {
        return this.mTrackType;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getWidth() {
        try {
            MediaPlayer.TrackInfo trackInfo = this.mTrackInfo;
            if (trackInfo != null && trackInfo.getFormat() != null) {
                return this.mTrackInfo.getFormat().getInteger(IjkMediaMeta.IJKM_KEY_WIDTH);
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    public void setTrackType(int i8) {
        this.mTrackType = i8;
    }
}
