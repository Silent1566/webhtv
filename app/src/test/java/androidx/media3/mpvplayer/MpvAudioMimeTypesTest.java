package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.MimeTypes;

import org.junit.Test;

public class MpvAudioMimeTypesTest {

    @Test
    public void mapsAv3aCodecToAudioMime() {
        assertEquals(MimeTypes.AUDIO_AV3A, MpvAudioMimeTypes.fromCodec("av3a.02"));
        assertEquals(MimeTypes.AUDIO_AV3A, MpvAudioMimeTypes.fromCodec("AVS3A"));
    }

    @Test
    public void keepsMp3CodecOnMpegMime() {
        assertEquals(MimeTypes.AUDIO_MPEG, MpvAudioMimeTypes.fromCodec("mp3float"));
    }
}
