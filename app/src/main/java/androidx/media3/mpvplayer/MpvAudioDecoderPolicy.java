package androidx.media3.mpvplayer;

import java.util.List;

final class MpvAudioDecoderPolicy {

    private static final List<String> HARDWARE_FIRST_DECODERS = List.of(
            "aac_mediacodec",
            "mp3_mediacodec",
            "amrnb_mediacodec",
            "amrwb_mediacodec");

    private MpvAudioDecoderPolicy() {
    }

    static String hardwareFirstDecoderList() {
        return String.join(",", HARDWARE_FIRST_DECODERS);
    }
}
