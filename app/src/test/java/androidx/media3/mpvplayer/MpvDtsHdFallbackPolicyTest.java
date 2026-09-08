package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvDtsHdFallbackPolicyTest {

    @Test
    public void retriesDtsHdAudioTrackConstructorFailureAsCore() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "ac3,eac3,dts,dts-hd,truehd", "spdif-dtshd",
                        "DTS-HD MA", "ao/audiotrack: AudioTrack Init failed",
                        false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void retriesDtsHdAudioTrackStateFailureAsCore() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "dts-hd", "spdif-dtshd",
                        "DTS-HD HRA",
                        "ao/audiotrack: AudioTrack.getState failed", false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void ignoresRepeatedFailure() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack Init failed", true).retry());
    }

    @Test
    public void ignoresUnrelatedAudioTrackFailure() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack.write failed with -6",
                false).retry());
    }

    @Test
    public void ignoresDtsCoreAndPcmFormats() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dts",
                "DTS", "AudioTrack Init failed", false).retry());
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "s16",
                "DTS", "AudioTrack Init failed", false).retry());
    }

    @Test
    public void usesDtsHdProfileWhenMpvHasAlreadyMovedToPcmFallback() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "dts,dts-hd", "s16", "DTS-HD MA",
                        "AudioTrack Init failed", false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void ignoresConfigurationWithoutDtsHd() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "ac3,dts,truehd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack Init failed", false).retry());
    }
}
