package tv.danmaku.ijk.media.player.misc;

import android.os.Bundle;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import tv.danmaku.ijk.media.player.IjkMediaMeta;

/* loaded from: classes3.dex */
public class IjkTrackInfo implements ITrackInfo {
    private final IjkMediaMeta.IjkStreamMeta mStreamMeta;
    private int mTrackType = 0;

    private IjkTrackInfo(IjkMediaMeta.IjkStreamMeta ijkStreamMeta) {
        this.mStreamMeta = ijkStreamMeta;
        initTrackType(ijkStreamMeta);
    }

    public static List<ITrackInfo> fromMediaMeta(Bundle bundle) {
        return bundle == null ? Collections.emptyList() : fromMediaMeta(IjkMediaMeta.parse(bundle));
    }

    private static List<ITrackInfo> fromMediaMeta(IjkMediaMeta ijkMediaMeta) {
        if (ijkMediaMeta == null) {
            return Collections.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        Iterator<IjkMediaMeta.IjkStreamMeta> it = ijkMediaMeta.mStreams.iterator();
        while (it.hasNext()) {
            arrayList.add(new IjkTrackInfo(it.next()));
        }
        return arrayList;
    }

    private void initTrackType(IjkMediaMeta.IjkStreamMeta ijkStreamMeta) {
        if (ijkStreamMeta.mType.equalsIgnoreCase("video")) {
            setTrackType(2);
        } else if (ijkStreamMeta.mType.equalsIgnoreCase("audio")) {
            setTrackType(1);
        } else if (ijkStreamMeta.mType.equalsIgnoreCase("timedtext")) {
            setTrackType(3);
        }
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getBitrate() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        if (ijkStreamMeta == null) {
            return 0;
        }
        return (int) ijkStreamMeta.mBitrate;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getChannelCount() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        if (ijkStreamMeta == null) {
            return 0;
        }
        return ijkStreamMeta.getChannelCount();
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public float getFps() {
        int i8;
        int i9;
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        if (ijkStreamMeta == null || (i8 = ijkStreamMeta.mFpsNum) == 0 || (i9 = ijkStreamMeta.mFpsDen) == 0) {
            return 0.0f;
        }
        return (float) i8 / i9;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorPrimaries() {
        return getStreamString("color_primaries");
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorRange() {
        return getStreamString("color_range");
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorSpace() {
        String value = getStreamString("color_space");
        return TextUtils.isEmpty(value) ? getStreamString("colorspace") : value;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getColorTransfer() {
        return getStreamString("color_transfer");
    }

    private String getStreamString(String key) {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        return ijkStreamMeta == null || ijkStreamMeta.mMeta == null ? "" : ijkStreamMeta.getString(key);
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getHeight() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        if (ijkStreamMeta == null) {
            return 0;
        }
        return ijkStreamMeta.mHeight;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getLanguage() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        return (ijkStreamMeta == null || TextUtils.isEmpty(ijkStreamMeta.mLanguage)) ? "und" : this.mStreamMeta.mLanguage;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public String getMimeType() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        return ijkStreamMeta == null ? "" : ijkStreamMeta.getCodecName();
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getTrackType() {
        return this.mTrackType;
    }

    @Override // tv.danmaku.ijk.media.player.misc.ITrackInfo
    public int getWidth() {
        IjkMediaMeta.IjkStreamMeta ijkStreamMeta = this.mStreamMeta;
        if (ijkStreamMeta == null) {
            return 0;
        }
        return ijkStreamMeta.mWidth;
    }

    public void setTrackType(int i8) {
        this.mTrackType = i8;
    }
}
