package com.fongmi.android.tv.subtitle;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RealtimeSubtitleAudioProcessor implements AudioProcessor {

    public interface Listener {
        boolean isListening();

        void onAudio(float[] samples, int sampleRate);

        void onFlush();
    }

    private final Listener listener;
    private AudioFormat pendingFormat = AudioFormat.NOT_SET;
    private AudioFormat inputFormat = AudioFormat.NOT_SET;
    private ByteBuffer buffer = EMPTY_BUFFER;
    private ByteBuffer outputBuffer = EMPTY_BUFFER;
    private boolean inputEnded;

    public RealtimeSubtitleAudioProcessor(Listener listener) {
        this.listener = listener;
    }

    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) {
        int encoding = inputAudioFormat.encoding;
        pendingFormat = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT ? inputAudioFormat : AudioFormat.NOT_SET;
        return pendingFormat;
    }

    @Override
    public boolean isActive() {
        return pendingFormat != AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;
        if (listener.isListening()) {
            float[] samples = toMono(inputBuffer, inputFormat.channelCount, inputFormat.encoding == C.ENCODING_PCM_FLOAT ? 4 : 2);
            if (samples.length > 0) listener.onAudio(samples, inputFormat.sampleRate);
        }
        int size = inputBuffer.remaining();
        if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        else buffer.clear();
        buffer.put(inputBuffer).flip();
        outputBuffer = buffer;
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer output = outputBuffer;
        outputBuffer = EMPTY_BUFFER;
        return output;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && !outputBuffer.hasRemaining();
    }

    @Override
    public void flush(StreamMetadata streamMetadata) {
        inputFormat = pendingFormat;
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        listener.onFlush();
    }

    @Override
    public void reset() {
        pendingFormat = AudioFormat.NOT_SET;
        inputFormat = AudioFormat.NOT_SET;
        buffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        listener.onFlush();
    }

    static float[] toMono(ByteBuffer input, int channels, int bytesPerSample) {
        if (channels <= 0 || (bytesPerSample != 2 && bytesPerSample != 4)) return new float[0];
        ByteBuffer source = input.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        int frames = source.remaining() / (channels * bytesPerSample);
        float[] output = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int channel = 0; channel < channels; channel++) sum += bytesPerSample == 2 ? source.getShort() / 32768f : source.getFloat();
            output[frame] = sum / channels;
        }
        return output;
    }

    static float[] resample(float[] input, int fromRate, int toRate) {
        if (input == null || input.length == 0 || fromRate <= 0 || toRate <= 0) return new float[0];
        if (fromRate == toRate) return input;
        int outputLength = Math.max(1, (int) ((long) input.length * toRate / fromRate));
        float[] output = new float[outputLength];
        double step = fromRate / (double) toRate;
        for (int i = 0; i < outputLength; i++) {
            double source = i * step;
            int left = Math.min((int) source, input.length - 1);
            int right = Math.min(left + 1, input.length - 1);
            float fraction = (float) (source - left);
            output[i] = input[left] + (input[right] - input[left]) * fraction;
        }
        return output;
    }
}
