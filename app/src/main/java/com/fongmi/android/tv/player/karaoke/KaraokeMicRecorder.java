package com.fongmi.android.tv.player.karaoke;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;

import java.util.concurrent.atomic.AtomicBoolean;

public class KaraokeMicRecorder {

    private static final int SAMPLE_RATE = 44_100;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private AudioRecord recorder;
    private YinPitchDetector detector;
    private VoiceBandpassFilter filter;
    private AdaptiveVoiceGate gate;
    private AudioEffect echoCanceler;
    private AudioEffect noiseSuppressor;
    private AudioEffect automaticGainControl;
    private Listener listener;

    public interface Listener {
        void onPitch(KaraokePitchSample sample);

        default void onError(Throwable error) {
        }
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(App.get(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean start(Listener listener) {
        if (isRunning() || !hasPermission()) return false;
        this.listener = listener;
        try {
            detector = new YinPitchDetector(SAMPLE_RATE);
            filter = new VoiceBandpassFilter(SAMPLE_RATE);
            gate = new AdaptiveVoiceGate();
            recorder = createRecorder(detector.getSampleSize());
            if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecorder();
                return false;
            }
            disableAudioEffects(recorder.getAudioSessionId());
            running.set(true);
            Task.execute(this::recordLoop);
            return true;
        } catch (Throwable e) {
            notifyError(e);
            stop();
            return false;
        }
    }

    public void stop() {
        running.set(false);
        releaseRecorder();
    }

    private void recordLoop() {
        short[] pcm = new short[detector.getSampleSize()];
        float[] buffer = new float[detector.getSampleSize()];
        try {
            recorder.startRecording();
            while (running.get()) {
                int read = recorder.read(pcm, 0, pcm.length);
                if (read <= 0) continue;
                for (int i = 0; i < read; i++) buffer[i] = pcm[i] / 32768f;
                double rawVolume = rms(buffer, read);
                if (filter != null) filter.process(buffer, read);
                KaraokePitchSample sample = detector.detect(buffer, read, System.currentTimeMillis());
                if (gate != null) sample = gate.apply(sample, rawVolume);
                KaraokePitchSample output = sample;
                if (listener != null) App.post(() -> listener.onPitch(output));
            }
        } catch (Throwable e) {
            notifyError(e);
        } finally {
            running.set(false);
            releaseRecorder();
        }
    }

    private AudioRecord createRecorder(int sampleSize) {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(sampleSize * Short.BYTES * 2, minBuffer);
        int[] sources = {MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT};
        for (int source : sources) {
            AudioRecord record = createRecorder(source, bufferSize);
            if (record != null && record.getState() == AudioRecord.STATE_INITIALIZED) return record;
            if (record != null) record.release();
        }
        return null;
    }

    private AudioRecord createRecorder(int source, int bufferSize) {
        return new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
    }

    private void releaseRecorder() {
        AudioRecord current = recorder;
        recorder = null;
        releaseAudioEffects();
        if (current == null) return;
        try {
            if (current.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) current.stop();
        } catch (Exception ignored) {
        }
        current.release();
    }

    private void notifyError(Throwable error) {
        if (listener != null) App.post(() -> listener.onError(error));
    }

    private void disableAudioEffects(int sessionId) {
        echoCanceler = disableEffect(AcousticEchoCanceler.isAvailable() ? AcousticEchoCanceler.create(sessionId) : null);
        noiseSuppressor = disableEffect(NoiseSuppressor.isAvailable() ? NoiseSuppressor.create(sessionId) : null);
        automaticGainControl = disableEffect(AutomaticGainControl.isAvailable() ? AutomaticGainControl.create(sessionId) : null);
    }

    private AudioEffect disableEffect(AudioEffect effect) {
        if (effect == null) return null;
        try {
            effect.setEnabled(false);
            return effect;
        } catch (Throwable e) {
            effect.release();
            return null;
        }
    }

    private void releaseAudioEffects() {
        releaseEffect(echoCanceler);
        releaseEffect(noiseSuppressor);
        releaseEffect(automaticGainControl);
        echoCanceler = null;
        noiseSuppressor = null;
        automaticGainControl = null;
    }

    private static void releaseEffect(AudioEffect effect) {
        if (effect == null) return;
        try {
            effect.release();
        } catch (Throwable ignored) {
        }
    }

    private static double rms(float[] input, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) sum += input[i] * input[i];
        return Math.min(1, Math.sqrt(sum / Math.max(1, length)));
    }

    private static class AdaptiveVoiceGate {

        private static final int WARMUP_FRAMES = 24;
        private double filteredFloor;
        private double rawFloor;
        private int frames;

        private KaraokePitchSample apply(KaraokePitchSample sample, double rawVolume) {
            if (sample == null) return new KaraokePitchSample(0, 0, rawVolume, 0);
            double filteredVolume = sample.getVolume();
            updateFloor(filteredVolume, rawVolume, sample.getConfidence());
            boolean warmup = frames < WARMUP_FRAMES;
            double filteredGate = Math.max(0.006, filteredFloor * (warmup ? 1.08 : 1.32));
            double rawGate = Math.max(0.012, rawFloor * (warmup ? 1.05 : 1.18));
            boolean quiet = filteredVolume < filteredGate && rawVolume < rawGate;
            boolean weak = sample.getConfidence() < 0.12 && filteredVolume < filteredGate * 1.15;
            if (quiet || weak) return new KaraokePitchSample(sample.getTimestampMs(), 0, rawVolume, 0);
            return new KaraokePitchSample(sample.getTimestampMs(), sample.getFrequencyHz(), rawVolume, sample.getConfidence());
        }

        private void updateFloor(double filteredVolume, double rawVolume, double confidence) {
            frames++;
            filteredFloor = nextFloor(filteredFloor, filteredVolume, confidence);
            rawFloor = nextFloor(rawFloor, rawVolume, confidence);
        }

        private static double nextFloor(double floor, double value, double confidence) {
            if (value <= 0) return floor;
            if (floor <= 0) return value;
            double alpha = value < floor ? 0.18 : (confidence < 0.2 ? 0.035 : 0.006);
            return floor + (value - floor) * alpha;
        }
    }

    private static class VoiceBandpassFilter {

        private final Biquad highPass;
        private final Biquad lowPass;

        private VoiceBandpassFilter(double sampleRate) {
            highPass = Biquad.highPass(200, sampleRate);
            lowPass = Biquad.lowPass(3500, sampleRate);
        }

        private void process(float[] samples, int length) {
            for (int i = 0; i < length; i++) {
                double value = highPass.process(samples[i]);
                value = lowPass.process(value);
                samples[i] = (float) Math.max(-1, Math.min(1, value));
            }
        }
    }

    private static class Biquad {

        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        private Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        private static Biquad highPass(double cutoff, double sampleRate) {
            double w0 = 2 * Math.PI * cutoff / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * 0.707);
            double a0 = 1 + alpha;
            return new Biquad((1 + cos) / 2 / a0, -(1 + cos) / a0, (1 + cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
        }

        private static Biquad lowPass(double cutoff, double sampleRate) {
            double w0 = 2 * Math.PI * cutoff / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * 0.707);
            double a0 = 1 + alpha;
            return new Biquad((1 - cos) / 2 / a0, (1 - cos) / a0, (1 - cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
        }

        private double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
