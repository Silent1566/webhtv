package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AudioPlaybackDiagnosticsTest {

    @Test
    public void formatsDtsHdCoreDowngradeAsCurrentPassthrough() {
        AudioPlaybackDiagnostics.Track source = track("DTS-HD MA", 6, 48_000, 4_000_000);
        AudioPlaybackDiagnostics.Snapshot snapshot = new AudioPlaybackDiagnostics.Snapshot(
                source, source.withCodec("DTS Core"),
                AudioPlaybackDiagnostics.DecodeMode.NONE, "",
                AudioPlaybackDiagnostics.OutputMode.PASSTHROUGH,
                6, 48_000, false, "dts-hd-core");

        assertEquals("DTS-HD MA 5.1/降级DTS Core 5.1 · 直通 · 48kHz · 4.0Mbps",
                AudioPlaybackDiagnostics.format(snapshot));
    }

    @Test
    public void formatsAutomaticStereoTrackDowngradeAndSoftwarePcm() {
        AudioPlaybackDiagnostics.Snapshot snapshot = new AudioPlaybackDiagnostics.Snapshot(
                track("DTS-HD MA", 6, 48_000, 4_000_000),
                track("AAC", 2, 48_000, 192_000),
                AudioPlaybackDiagnostics.DecodeMode.SOFTWARE, "aac",
                AudioPlaybackDiagnostics.OutputMode.PCM,
                2, 48_000, false, "same-language-stereo");

        assertEquals("DTS-HD MA 5.1/降级AAC 2.0 · 软解 · PCM 2.0 · 48kHz · 192kbps",
                AudioPlaybackDiagnostics.format(snapshot));
    }

    @Test
    public void formatsObservedHardwarePcm() {
        AudioPlaybackDiagnostics.Track track = track("E-AC-3", 6, 48_000, 768_000);
        AudioPlaybackDiagnostics.Snapshot snapshot = new AudioPlaybackDiagnostics.Snapshot(
                track, track, AudioPlaybackDiagnostics.DecodeMode.HARDWARE,
                "c2.vendor.eac3.decoder", AudioPlaybackDiagnostics.OutputMode.PCM,
                6, 48_000, false, "");

        assertEquals("E-AC-3 5.1 · 硬解 · PCM 5.1 · 48kHz · 768kbps",
                AudioPlaybackDiagnostics.format(snapshot));
    }

    @Test
    public void formatsOffloadWithoutClaimingPassthrough() {
        AudioPlaybackDiagnostics.Track track = track("E-AC-3", 6, 48_000, 768_000);
        AudioPlaybackDiagnostics.Snapshot snapshot = new AudioPlaybackDiagnostics.Snapshot(
                track, track, AudioPlaybackDiagnostics.DecodeMode.NONE, "",
                AudioPlaybackDiagnostics.OutputMode.OFFLOAD,
                6, 48_000, false, "");

        assertEquals("E-AC-3 5.1 · 硬解 · 卸载 · 48kHz · 768kbps",
                AudioPlaybackDiagnostics.format(snapshot));
    }

    @Test
    public void mapsMpvOutputAndDtsCoreTrack() {
        AudioPlaybackDiagnostics.Track source = track("DTS-HD MA", 6, 48_000, 0);

        assertEquals(AudioPlaybackDiagnostics.OutputMode.PASSTHROUGH,
                AudioPlaybackDiagnostics.mpvOutputMode("spdif-dts"));
        assertEquals("DTS Core",
                AudioPlaybackDiagnostics.passthroughTrack(source, "spdif-dts").codec());
        assertEquals(AudioPlaybackDiagnostics.OutputMode.PCM,
                AudioPlaybackDiagnostics.mpvOutputMode("float"));
        assertEquals(AudioPlaybackDiagnostics.OutputMode.COMPRESSED_DIRECT,
                AudioPlaybackDiagnostics.mpvOutputMode("aac"));
        assertEquals(AudioPlaybackDiagnostics.OutputMode.COMPRESSED_DIRECT,
                AudioPlaybackDiagnostics.mpvOutputMode("mp3"));
        assertEquals(AudioPlaybackDiagnostics.OutputMode.OFFLOAD,
                AudioPlaybackDiagnostics.mpvOutputMode("aac", "offload"));
        assertEquals(AudioPlaybackDiagnostics.OutputMode.COMPRESSED_DIRECT,
                AudioPlaybackDiagnostics.mpvOutputMode("aac", "compressed-direct"));
    }

    @Test
    public void mapsConsumerChannelLabels() {
        assertEquals("2.0", AudioPlaybackDiagnostics.channelLabel(2));
        assertEquals("5.1", AudioPlaybackDiagnostics.channelLabel(6));
        assertEquals("7.1", AudioPlaybackDiagnostics.channelLabel(8));
    }

    @Test
    public void separatesRuntimeStatesFromDecisionLevels() {
        AudioPlaybackDiagnostics.Snapshot empty = AudioPlaybackDiagnostics.Snapshot.empty();
        assertEquals(AudioPlaybackDiagnostics.RuntimeState.UNKNOWN, empty.runtimeState());
        assertNull(empty.decisionLevel());

        AudioPlaybackDiagnostics.Track aac = track("AAC", 2, 48_000, 192_000);
        AudioPlaybackDiagnostics.Snapshot pending = new AudioPlaybackDiagnostics.Snapshot(
                aac, aac, AudioPlaybackDiagnostics.DecodeMode.UNKNOWN, "",
                AudioPlaybackDiagnostics.OutputMode.UNKNOWN, 0, 0, false, "");
        assertEquals(AudioPlaybackDiagnostics.RuntimeState.PENDING, pending.runtimeState());
        assertNull(pending.decisionLevel());
        assertEquals("AAC 2.0 · 输出初始化中 · 48kHz · 192kbps",
                AudioPlaybackDiagnostics.format(pending));

        AudioPlaybackDiagnostics.Snapshot active = new AudioPlaybackDiagnostics.Snapshot(
                aac, aac, AudioPlaybackDiagnostics.DecodeMode.HARDWARE,
                "c2.vendor.aac.decoder", AudioPlaybackDiagnostics.OutputMode.PCM,
                2, 48_000, false, "");
        assertEquals(AudioPlaybackDiagnostics.RuntimeState.ACTIVE, active.runtimeState());
        assertEquals(AudioPlaybackDiagnostics.DecisionLevel.HARDWARE_PCM,
                active.decisionLevel());

        AudioPlaybackDiagnostics.Snapshot failed = new AudioPlaybackDiagnostics.Snapshot(
                aac, aac, AudioPlaybackDiagnostics.DecodeMode.HARDWARE,
                "c2.vendor.aac.decoder", AudioPlaybackDiagnostics.OutputMode.UNKNOWN,
                0, 0, false, "",
                AudioPlaybackDiagnostics.DecisionLevel.HARDWARE_PCM,
                AudioPlaybackDiagnostics.RuntimeState.FAILED,
                AudioPlaybackDiagnostics.FailureReason.DECODER_INIT);
        assertEquals("AAC 2.0 · 输出失败 · decoder-init · 48kHz · 192kbps",
                AudioPlaybackDiagnostics.format(failed));
    }

    @Test
    public void mapsDecisionAndStableFailureCodes() {
        assertEquals(AudioPlaybackDiagnostics.DecisionLevel.EXACT_PASSTHROUGH,
                AudioPlaybackDiagnostics.decisionLevel(
                        AudioPlaybackDiagnostics.OutputMode.COMPRESSED_DIRECT,
                        AudioPlaybackDiagnostics.DecodeMode.NONE, ""));
        assertEquals(AudioPlaybackDiagnostics.DecisionLevel.COMPRESSED_OFFLOAD,
                AudioPlaybackDiagnostics.decisionLevel(
                        AudioPlaybackDiagnostics.OutputMode.OFFLOAD,
                        AudioPlaybackDiagnostics.DecodeMode.NONE, ""));
        assertEquals(AudioPlaybackDiagnostics.DecisionLevel.SAME_TRACK_COMPATIBLE,
                AudioPlaybackDiagnostics.decisionLevel(
                        AudioPlaybackDiagnostics.OutputMode.PASSTHROUGH,
                        AudioPlaybackDiagnostics.DecodeMode.NONE, "dts-hd-core"));
        assertEquals(AudioPlaybackDiagnostics.DecisionLevel.LANGUAGE_OR_STEREO_FALLBACK,
                AudioPlaybackDiagnostics.decisionLevel(
                        AudioPlaybackDiagnostics.OutputMode.PCM,
                        AudioPlaybackDiagnostics.DecodeMode.SOFTWARE,
                        "same-language-stereo"));
        assertEquals(AudioPlaybackDiagnostics.FailureReason.DIRECT_OUTPUT_INIT,
                AudioPlaybackDiagnostics.failureReason(
                        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED));
        assertEquals(AudioPlaybackDiagnostics.FailureReason.OFFLOAD_WRITE,
                AudioPlaybackDiagnostics.failureReason(
                        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED));
        assertEquals(AudioPlaybackDiagnostics.FailureReason.DECODER_RUNTIME,
                AudioPlaybackDiagnostics.failureReason(
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED));
        assertEquals(AudioPlaybackDiagnostics.FailureReason.UNKNOWN,
                AudioPlaybackDiagnostics.failureReason(123456));
    }

    private static AudioPlaybackDiagnostics.Track track(
            String codec, int channels, int sampleRate, int bitrate) {
        return new AudioPlaybackDiagnostics.Track(codec, channels, sampleRate, "", bitrate);
    }
}
