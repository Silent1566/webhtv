package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;

import org.junit.Test;

/**
 * A vendor compressed output is decoded by the device and bypasses the PCM processors that
 * implement rate change, so it may only be claimed at the native rate.
 */
public class ExoCompressedAudioDirectPolicySpeedTest {

    private static final float NATIVE = 1f;

    @Test
    public void nativeSpeed_keepsVendorDirectClaim() {
        ExoCompressedAudioDirectPolicy policy = policy();
        AudioOutputProvider wrapped = policy.wrapOutputProvider(unsupportedProvider());

        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                wrapped.getFormatSupport(formatConfig(format())).supportLevel);

        AudioOutputProvider.OutputConfig config = outputConfig(wrapped);
        assertEquals(C.ENCODING_AAC_LC, config.encoding);
        assertEquals(C.INDEX_UNSET, config.virtualDeviceId);
    }

    @Test
    public void fasterThanNative_refusesVendorDirectUpFront() {
        ExoCompressedAudioDirectPolicy policy = policy();
        AudioOutputProvider wrapped = policy.wrapOutputProvider(unsupportedProvider());
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                wrapped.getFormatSupport(formatConfig(format())).supportLevel);

        policy.setPlaybackSpeed(3f);

        assertEquals(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                wrapped.getFormatSupport(formatConfig(format())));
        // The direct admission is withdrawn, so configuration falls back to the delegate's
        // standard path instead of producing another offload/tunneling-free vendor config.
        assertThrows(AudioOutputProvider.ConfigurationException.class,
                () -> wrapped.getOutputConfig(formatConfig(format())));
    }

    @Test
    public void slowerThanNative_refusesVendorDirectToo() {
        ExoCompressedAudioDirectPolicy policy = policy();
        AudioOutputProvider wrapped = policy.wrapOutputProvider(unsupportedProvider());

        policy.setPlaybackSpeed(0.5f);

        assertEquals(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                wrapped.getFormatSupport(formatConfig(format())));
    }

    @Test
    public void directClaimIsDecidedByTheRateInForceAtSelectionTime() {
        ExoCompressedAudioDirectPolicy policy = policy();
        AudioOutputProvider wrapped = policy.wrapOutputProvider(unsupportedProvider());

        policy.setPlaybackSpeed(2.5f);
        assertEquals(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                wrapped.getFormatSupport(formatConfig(format())));
        assertThrows(AudioOutputProvider.ConfigurationException.class,
                () -> wrapped.getOutputConfig(formatConfig(format())));

        // Back at the native rate the vendor output is admissible again: the gate withdraws a
        // claim, it does not blacklist the configuration.
        policy.setPlaybackSpeed(NATIVE);
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                wrapped.getFormatSupport(formatConfig(format())).supportLevel);
        assertNotNull(outputConfig(wrapped));
    }

    @Test
    public void rateWithinToleranceOfNativeKeepsVendorDirectClaim() {
        ExoCompressedAudioDirectPolicy policy = policy();
        AudioOutputProvider wrapped = policy.wrapOutputProvider(unsupportedProvider());

        policy.setPlaybackSpeed(1.0005f);

        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                wrapped.getFormatSupport(formatConfig(format())).supportLevel);
    }

    @Test
    public void invalidRateFallsBackToNativeSoTheClaimIsNotLost() {
        ExoCompressedAudioDirectPolicy policy = policy();

        policy.setPlaybackSpeed(Float.NaN);

        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                policy.wrapOutputProvider(unsupportedProvider())
                        .getFormatSupport(formatConfig(format())).supportLevel);
    }

    private static AudioOutputProvider.OutputConfig outputConfig(AudioOutputProvider wrapped) {
        try {
            AudioOutputProvider.OutputConfig config =
                    wrapped.getOutputConfig(formatConfig(format()));
            assertNotNull(config);
            return config;
        } catch (AudioOutputProvider.ConfigurationException error) {
            throw new AssertionError("expected a vendor direct config", error);
        }
    }

    private static ExoCompressedAudioDirectPolicy policy() {
        return new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> true,
                Clock.DEFAULT,
                config -> {
                    throw new AudioOutputProvider.InitializationException();
                },
                new ExoAudioDirectFailureMemory(),
                new ExoCompressedAudioDirectPolicy.OutputEnvironment() {
                    @Override
                    public ExoAudioDirectFailureMemory.Route expectedRoute(
                            AudioAttributes attributes) {
                        return null;
                    }

                    @Override
                    public ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output) {
                        return null;
                    }
                });
    }

    private static AudioOutputProvider unsupportedProvider() {
        return new AudioOutputProvider() {
            @Override
            public FormatSupport getFormatSupport(FormatConfig config) {
                return FormatSupport.UNSUPPORTED;
            }

            @Override
            public OutputConfig getOutputConfig(FormatConfig config)
                    throws ConfigurationException {
                throw new ConfigurationException("standard path");
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
        };
    }

    private static AudioOutputProvider.FormatConfig formatConfig(Format format) {
        return new AudioOutputProvider.FormatConfig.Builder(format)
                .setAudioAttributes(AudioAttributes.DEFAULT)
                .build();
    }

    private static Format format() {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .setSampleRate(48_000)
                .setChannelCount(2)
                .build();
    }
}
