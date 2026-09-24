package androidx.media3.mpvplayer;

import androidx.media3.common.MimeTypes;

import java.util.Locale;

final class MpvAudioMimeTypes {

    private MpvAudioMimeTypes() {
    }

    static String fromCodec(String codec) {
        String value = codec == null ? "" : codec.toLowerCase(Locale.US);
        if (value.contains("av3a") || value.contains("avs3a")) return MimeTypes.AUDIO_AV3A;
        if (value.contains("aac")) return MimeTypes.AUDIO_AAC;
        if (value.contains("ac3")) return MimeTypes.AUDIO_AC3;
        if (value.contains("eac3") || value.contains("e-ac-3")) return MimeTypes.AUDIO_E_AC3;
        if (value.contains("opus")) return MimeTypes.AUDIO_OPUS;
        if (value.contains("vorbis")) return MimeTypes.AUDIO_VORBIS;
        if (value.contains("flac")) return MimeTypes.AUDIO_FLAC;
        if (value.contains("mp3")) return MimeTypes.AUDIO_MPEG;
        return MimeTypes.BASE_TYPE_AUDIO + "/" + (value.isEmpty() ? "unknown" : value);
    }
}
