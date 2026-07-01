package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import android.os.Handler;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

public final class CompatFfmpegAudioRenderer extends DecoderAudioRenderer<FfmpegAudioDecoder> {

    private static final int NUM_BUFFERS = 16;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 5760;

    public CompatFfmpegAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(eventHandler, eventListener, audioSink);
    }

    @Override
    public String getName() {
        return "CompatFfmpegAudioRenderer";
    }

    @Override
    protected int supportsFormatInternal(Format format) {
        Format decodeFormat = normalizeDecodeFormat(format);
        String sampleMimeType = decodeFormat.sampleMimeType;
        if (sampleMimeType == null) return C.FORMAT_UNSUPPORTED_TYPE;
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(sampleMimeType)) return C.FORMAT_UNSUPPORTED_TYPE;
        if (!FfmpegLibrary.supportsFormat(sampleMimeType)) return C.FORMAT_UNSUPPORTED_SUBTYPE;
        if (!sinkSupportsFormat(decodeFormat, C.ENCODING_PCM_16BIT) && !sinkSupportsFormat(decodeFormat, C.ENCODING_PCM_FLOAT)) return C.FORMAT_UNSUPPORTED_SUBTYPE;
        return decodeFormat.cryptoType == C.CRYPTO_TYPE_NONE ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_DRM;
    }

    @Override
    protected FfmpegAudioDecoder createDecoder(Format format, CryptoConfig cryptoConfig) throws FfmpegDecoderException {
        TraceUtil.beginSection("createCompatFfmpegAudioDecoder");
        Format decodeFormat = normalizeDecodeFormat(format);
        int initialInputBufferSize = decodeFormat.maxInputSize != Format.NO_VALUE ? decodeFormat.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(decodeFormat, NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, shouldOutputFloat(decodeFormat));
        TraceUtil.endSection();
        return decoder;
    }

    @Override
    protected Format getOutputFormat(FfmpegAudioDecoder decoder) {
        Assertions.checkNotNull(decoder);
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(decoder.getChannelCount())
                .setSampleRate(decoder.getSampleRate())
                .setPcmEncoding(decoder.getEncoding())
                .build();
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() {
        return RendererCapabilities.ADAPTIVE_NOT_SEAMLESS;
    }

    private static Format normalizeDecodeFormat(Format format) {
        String sampleMimeType = format.sampleMimeType;
        if (sampleMimeType == null) return format;
        if (sampleMimeType.startsWith(MimeTypes.AUDIO_DTS_HD + ";") || MimeTypes.AUDIO_DTS_X.equals(sampleMimeType)) return format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_DTS_HD).build();
        if (MimeTypes.AUDIO_AMR.equals(sampleMimeType)) return format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AMR_NB).build();
        return format;
    }

    private boolean sinkSupportsFormat(Format format, int pcmEncoding) {
        return sinkSupportsFormat(Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate));
    }

    private boolean shouldOutputFloat(Format format) {
        if (!sinkSupportsFormat(format, C.ENCODING_PCM_16BIT)) return true;
        int floatSupport = getSinkFormatSupport(Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate));
        return floatSupport == C.FORMAT_HANDLED && !MimeTypes.AUDIO_AC3.equals(format.sampleMimeType);
    }
}
