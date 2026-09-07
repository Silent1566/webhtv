package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class ExoCompressedAudioDirectPolicyTest {

    @Test
    public void standardOffload_isPreservedWithoutDirectProbe() {
        AtomicInteger directQueries = new AtomicInteger();
        AudioOffloadSupport standard = supported(true, true);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> standard,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });

        AudioOffloadSupport actual = policy.getAudioOffloadSupport(
                aacStereo(), AudioAttributes.DEFAULT);

        assertSame(standard, actual);
        assertTrue(actual.isFormatSupported);
        assertTrue(actual.isGaplessSupported);
        assertTrue(actual.isSpeedChangeSupported);
        assertTrue(directQueries.get() == 0);
    }

    @Test
    public void directBitstream_enablesEncodedBypassWithoutFakeOffload() {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(format));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);
        assertFalse(actual.isFormatSupportedForOffload);
        assertTrue(policy.usesVendorDirect(C.ENCODING_AAC_LC, 48_000,
                Util.getAudioTrackChannelConfig(format)));
    }

    @Test
    public void passthroughWithoutOffload_isOverriddenByVendorDirect() {
        Format format = aacStereo();
        AudioOutputProvider.FormatSupport passthrough =
                new AudioOutputProvider.FormatSupport.Builder()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .setIsFormatSupportedForOffload(false)
                        .build();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);

        AudioOutputProvider.FormatSupport actual = policy.wrapOutputProvider(
                new FixedFormatSupportAudioOutputProvider(passthrough))
                .getFormatSupport(formatConfig(format));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);
        assertFalse(actual.isFormatSupportedForOffload);
        assertTrue(policy.usesVendorDirect(C.ENCODING_AAC_LC, 48_000,
                Util.getAudioTrackChannelConfig(format)));
    }

    @Test
    public void directBitstream_buildsNonOffloadEncodedOutput() throws Exception {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        AudioOutputProvider provider = wrapped(policy);
        AudioOutputProvider.FormatConfig config = formatConfig(format);
        provider.getFormatSupport(config);

        AudioOutputProvider.OutputConfig output = provider.getOutputConfig(config);

        assertTrue(output.encoding == C.ENCODING_AAC_LC);
        assertTrue(output.sampleRate == 48_000);
        assertTrue(output.channelMask == Util.getAudioTrackChannelConfig(format));
        assertTrue(output.bufferSize == 256 * 1024);
        assertFalse(output.isOffload);
        assertFalse(output.isTunneling);
        assertTrue(output.audioSessionId == 0);
        assertTrue(output.virtualDeviceId == C.INDEX_UNSET);
    }

    @Test
    public void directBitstream_disablesTunnelingEvenWhenRequested() throws Exception {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        AudioOutputProvider provider = wrapped(policy);
        AudioOutputProvider.FormatConfig config = new AudioOutputProvider.FormatConfig.Builder(format)
                .setAudioAttributes(AudioAttributes.DEFAULT)
                .setAudioSessionId(1234)
                .setVirtualDeviceId(0)
                .setEnableTunneling(true)
                .build();
        provider.getFormatSupport(config);

        AudioOutputProvider.OutputConfig output = provider.getOutputConfig(config);

        assertFalse(output.isTunneling);
        assertTrue(output.audioSessionId == 0);
        assertTrue(output.virtualDeviceId == C.INDEX_UNSET);
    }

    @Test
    public void missingDirectSupport_keepsPcmFallback() {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> false);

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(aacStereo()));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
    }

    @Test
    public void failedDirectConfig_isNotRetriedInSamePlayerSession() {
        AtomicInteger directQueries = new AtomicInteger();
        Format format = aacStereo();
        int channelMask = Util.getAudioTrackChannelConfig(format);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        assertTrue(wrapped(policy).getFormatSupport(formatConfig(format))
                .supportLevel == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);

        policy.disableVendorDirect(C.ENCODING_AAC_LC, 48_000, channelMask);
        AudioOutputProvider.FormatSupport fallback = wrapped(policy)
                .getFormatSupport(formatConfig(format));

        assertTrue(fallback.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertFalse(policy.usesVendorDirect(
                C.ENCODING_AAC_LC, 48_000, channelMask));
        assertTrue(directQueries.get() == 1);
    }

    @Test
    public void failedDirectConfig_masksDelegatePassthroughForPcmFallback() {
        Format format = aacStereo();
        int channelMask = Util.getAudioTrackChannelConfig(format);
        AudioOutputProvider.FormatSupport passthrough =
                new AudioOutputProvider.FormatSupport.Builder()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .setIsFormatSupportedForOffload(false)
                        .build();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        AudioOutputProvider provider = policy.wrapOutputProvider(
                new FixedFormatSupportAudioOutputProvider(passthrough));
        assertTrue(provider.getFormatSupport(formatConfig(format)).supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);

        policy.disableVendorDirect(C.ENCODING_AAC_LC, 48_000, channelMask);

        AudioOutputProvider.FormatSupport fallback =
                provider.getFormatSupport(formatConfig(format));
        assertTrue(fallback.supportLevel == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(policy.consumePcmFallbackRequest());
        assertFalse(policy.consumePcmFallbackRequest());
    }

    @Test
    public void unsupportedEncodedFrameType_doesNotProbeDirectPlayback() {
        AtomicInteger directQueries = new AtomicInteger();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        Format flac = new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_FLAC)
                .setSampleRate(48_000)
                .setChannelCount(2)
                .build();

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(flac));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(directQueries.get() == 0);
    }

    @Test
    public void incompleteFormat_doesNotProbeDirectPlayback() {
        AtomicInteger directQueries = new AtomicInteger();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        Format incomplete = new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .build();

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(incomplete));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(directQueries.get() == 0);
    }

    private static AudioOutputProvider wrapped(
            ExoCompressedAudioDirectPolicy policy) {
        return policy.wrapOutputProvider(new UnsupportedAudioOutputProvider());
    }

    private static AudioOutputProvider.FormatConfig formatConfig(Format format) {
        return new AudioOutputProvider.FormatConfig.Builder(format)
                .setAudioAttributes(AudioAttributes.DEFAULT)
                .build();
    }

    private static Format aacStereo() {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .setSampleRate(48_000)
                .setChannelCount(2)
                .build();
    }

    private static AudioOffloadSupport supported(
            boolean gapless, boolean speedChange) {
        return new AudioOffloadSupport.Builder()
                .setIsFormatSupported(true)
                .setIsGaplessSupported(gapless)
                .setIsSpeedChangeSupported(speedChange)
                .build();
    }

    private static class UnsupportedAudioOutputProvider
            implements AudioOutputProvider {

        @Override
        public FormatSupport getFormatSupport(FormatConfig config) {
            return FormatSupport.UNSUPPORTED;
        }

        @Override
        public OutputConfig getOutputConfig(FormatConfig config)
                throws ConfigurationException {
            throw new ConfigurationException("unsupported");
        }

        @Override
        public AudioOutput getAudioOutput(OutputConfig config)
                throws InitializationException {
            throw new InitializationException();
        }

        @Override
        public void addListener(Listener listener) {
        }

        @Override
        public void removeListener(Listener listener) {
        }

        @Override
        public void release() {
        }
    }

    private static final class FixedFormatSupportAudioOutputProvider
            extends UnsupportedAudioOutputProvider {

        private final FormatSupport formatSupport;

        FixedFormatSupportAudioOutputProvider(FormatSupport formatSupport) {
            this.formatSupport = formatSupport;
        }

        @Override
        public FormatSupport getFormatSupport(FormatConfig config) {
            return formatSupport;
        }
    }
}
