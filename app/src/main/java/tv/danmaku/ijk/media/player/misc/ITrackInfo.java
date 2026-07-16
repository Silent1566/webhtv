package tv.danmaku.ijk.media.player.misc;

/* loaded from: classes3.dex */
public interface ITrackInfo {
    public static final int MEDIA_TRACK_TYPE_AUDIO = 1;
    public static final int MEDIA_TRACK_TYPE_TEXT = 3;
    public static final int MEDIA_TRACK_TYPE_UNKNOWN = 0;
    public static final int MEDIA_TRACK_TYPE_VIDEO = 2;

    int getBitrate();

    int getChannelCount();

    float getFps();

    String getColorPrimaries();

    String getColorRange();

    String getColorSpace();

    String getColorTransfer();

    int getHeight();

    String getLanguage();

    String getMimeType();

    int getTrackType();

    int getWidth();
}
