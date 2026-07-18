package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

public class RealtimeSubtitleAudioProcessorTest {

    @Test
    public void pcm16StereoIsDownmixedWithoutMovingInput() {
        ByteBuffer input = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        input.putShort(Short.MAX_VALUE).putShort(Short.MIN_VALUE);
        input.putShort((short) 16384).putShort((short) 16384).flip();

        float[] mono = RealtimeSubtitleAudioProcessor.toMono(input, 2, 2);

        assertArrayEquals(new float[]{-1f / 65536f, 0.5f}, mono, 0.0001f);
        assertArrayEquals(new int[]{0, 8}, new int[]{input.position(), input.limit()});
    }

    @Test
    public void disabledTapPassesAudioWithoutCallback() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        RealtimeSubtitleAudioProcessor processor = new RealtimeSubtitleAudioProcessor(new RealtimeSubtitleAudioProcessor.Listener() {
            @Override
            public boolean isListening() {
                return false;
            }

            @Override
            public void onAudio(float[] samples, int sampleRate) {
                callbacks.incrementAndGet();
            }

            @Override
            public void onFlush() {
            }
        });
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        ByteBuffer input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        input.putInt(0x12345678).flip();

        processor.queueInput(input);
        ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());

        assertEquals(0, callbacks.get());
        assertEquals(input.limit(), input.position());
        assertEquals(0x12345678, output.getInt());
    }

    @Test
    public void outputSurvivesDecoderInputBufferReuse() throws Exception {
        RealtimeSubtitleAudioProcessor processor = new RealtimeSubtitleAudioProcessor(new RealtimeSubtitleAudioProcessor.Listener() {
            @Override
            public boolean isListening() {
                return false;
            }

            @Override
            public void onAudio(float[] samples, int sampleRate) {
            }

            @Override
            public void onFlush() {
            }
        });
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        ByteBuffer input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        input.putInt(0x12345678).flip();

        processor.queueInput(input);
        ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        input.clear();
        input.putInt(0x76543210).flip();

        assertEquals(0x12345678, output.getInt());
    }

    @Test
    public void resampleUsesLinearInterpolation() {
        float[] output = RealtimeSubtitleAudioProcessor.resample(new float[]{0f, 1f, 0f}, 3, 6);

        assertArrayEquals(new float[]{0f, 0.5f, 1f, 0.5f, 0f, 0f}, output, 0.0001f);
    }

    @Test
    public void resampleRejectsInvalidRates() {
        assertEquals(0, RealtimeSubtitleAudioProcessor.resample(new float[]{1f}, 0, 16000).length);
        assertEquals(0, RealtimeSubtitleAudioProcessor.resample(new float[]{1f}, 48000, 0).length);
    }
}
