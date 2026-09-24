package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import org.junit.Test;


public class CompatFfmpegAudioRendererTest {

    @Test
    public void supportedMultichannelPcm_isPreserved() {
        assertEquals(6, CompatFfmpegAudioRenderer.resolveOutputChannelCount(6, true, true));
    }

    @Test
    public void unsupportedMultichannelPcm_downmixesToStereo() {
        assertEquals(2, CompatFfmpegAudioRenderer.resolveOutputChannelCount(6, false, true));
    }

    @Test
    public void noSupportedPcmOutput_rejectsFormat() {
        assertEquals(Format.NO_VALUE, CompatFfmpegAudioRenderer.resolveOutputChannelCount(6, false, false));
    }

    @Test
    public void av3aManifestMime_isRecognizedByMedia3() {
        assertEquals(MimeTypes.AUDIO_AV3A, MimeTypes.getMediaMimeType("av3a.02"));
    }

    @Test
    public void alacInitializationData_stripsFullAtomHeader() {
        byte[] cookie = new byte[24];
        cookie[0] = 0;
        cookie[1] = 0;
        cookie[2] = 0x10;
        cookie[3] = 0;
        byte[] atom = new byte[36];
        atom[3] = 36;
        atom[4] = 'a';
        atom[5] = 'l';
        atom[6] = 'a';
        atom[7] = 'c';
        System.arraycopy(cookie, 0, atom, 12, cookie.length);

        assertArrayEquals(cookie,
                CompatFfmpegAudioRenderer.normalizeAlacInitializationData(atom));
    }

    @Test
    public void alacInitializationData_keepsMagicCookie() {
        byte[] cookie = new byte[24];
        assertEquals(cookie, CompatFfmpegAudioRenderer.normalizeAlacInitializationData(cookie));
    }
}
