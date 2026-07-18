package com.fongmi.android.tv.player.karaoke;

import java.util.Arrays;

public class YinPitchDetector {

    public static final int DEFAULT_SAMPLE_SIZE = 2048;

    private final int sampleRate;
    private final double threshold;
    private final double minConfidence;
    private final float[] yinBuffer;
    private final double minFrequencyHz;
    private final double maxFrequencyHz;
    private final int minTau;
    private final int maxTau;
    private final int fftSize;
    private final double[] signalReal;
    private final double[] signalImag;
    private final double[] kernelReal;
    private final double[] kernelImag;
    private final double[] squarePrefix;

    public YinPitchDetector(int sampleRate) {
        this(sampleRate, 0.1, 0.1, DEFAULT_SAMPLE_SIZE);
    }

    public YinPitchDetector(int sampleRate, double threshold, double minConfidence, int sampleSize) {
        this(sampleRate, threshold, minConfidence, sampleSize, 0, 0);
    }

    public YinPitchDetector(int sampleRate, double threshold, double minConfidence, int sampleSize, double minFrequencyHz, double maxFrequencyHz) {
        this.sampleRate = Math.max(1, sampleRate);
        this.threshold = Math.max(0.01, Math.min(0.9, threshold));
        this.minConfidence = Math.max(0, Math.min(1, minConfidence));
        this.yinBuffer = new float[Math.max(64, sampleSize / 2)];
        this.minFrequencyHz = Math.max(0, minFrequencyHz);
        this.maxFrequencyHz = Math.max(0, maxFrequencyHz);
        int defaultMaxTau = this.yinBuffer.length - 1;
        int candidateMinTau = this.maxFrequencyHz > 0 ? (int) Math.floor(this.sampleRate / this.maxFrequencyHz) : 2;
        int candidateMaxTau = this.minFrequencyHz > 0 ? (int) Math.ceil(this.sampleRate / this.minFrequencyHz) : defaultMaxTau;
        this.minTau = Math.max(2, Math.min(defaultMaxTau, candidateMinTau));
        this.maxTau = Math.max(this.minTau, Math.min(defaultMaxTau, candidateMaxTau));
        int sampleCapacity = getSampleSize();
        this.fftSize = nextPowerOfTwo(sampleCapacity * 2);
        this.signalReal = new double[fftSize];
        this.signalImag = new double[fftSize];
        this.kernelReal = new double[fftSize];
        this.kernelImag = new double[fftSize];
        this.squarePrefix = new double[sampleCapacity + 1];
    }

    public int getSampleSize() {
        return yinBuffer.length * 2;
    }

    public KaraokePitchSample detect(float[] input, int length, long timestampMs) {
        int bufferSize = Math.min(length, getSampleSize());
        if (input == null || bufferSize < 128) return new KaraokePitchSample(timestampMs, 0, 0, 0);
        double volume = rms(input, bufferSize);
        return detect(input, length, timestampMs, volume);
    }

    public KaraokePitchSample detect(float[] input, int length, long timestampMs, double volume) {
        int bufferSize = Math.min(length, getSampleSize());
        if (input == null || bufferSize < 128) return new KaraokePitchSample(timestampMs, 0, volume, 0);
        Detection detection = detectFrequency(input, bufferSize);
        if (detection.confidence < minConfidence) detection = Detection.EMPTY;
        return new KaraokePitchSample(timestampMs, detection.frequencyHz, volume, detection.confidence);
    }

    private Detection detectFrequency(float[] input, int bufferSize) {
        int safeMaxTau = Math.min(maxTau, bufferSize - 1);
        if (safeMaxTau < minTau) return Detection.EMPTY;
        int yinLength = Math.min(yinBuffer.length, safeMaxTau + 1);
        int analysisLength = Math.max(128, bufferSize - safeMaxTau);
        difference(input, yinLength, analysisLength, bufferSize);
        cumulativeMeanNormalizedDifference(yinLength);
        int tau = absoluteThreshold(Math.min(minTau, yinLength - 1), yinLength);
        if (tau < 0) return Detection.EMPTY;
        double betterTau = parabolicInterpolation(tau, yinLength);
        if (betterTau <= 0) return Detection.EMPTY;
        double frequencyHz = sampleRate / betterTau;
        if (minFrequencyHz > 0 && frequencyHz < minFrequencyHz) return Detection.EMPTY;
        if (maxFrequencyHz > 0 && frequencyHz > maxFrequencyHz) return Detection.EMPTY;
        double confidence = 1.0 - yinBuffer[tau];
        return new Detection(frequencyHz, confidence);
    }

    private void difference(float[] input, int yinLength, int analysisLength, int bufferSize) {
        for (int tau = 0; tau < yinLength; tau++) yinBuffer[tau] = 0;
        int tauLimit = yinLength - 1;
        int window = Math.max(1, Math.min(analysisLength, bufferSize - tauLimit));
        if (window <= 0 || window + bufferSize - 1 > fftSize) {
            differenceDirect(input, yinLength, analysisLength);
            return;
        }
        for (int i = 0; i < bufferSize; i++) squarePrefix[i + 1] = squarePrefix[i] + input[i] * input[i];
        Arrays.fill(signalReal, 0);
        Arrays.fill(signalImag, 0);
        Arrays.fill(kernelReal, 0);
        Arrays.fill(kernelImag, 0);
        for (int i = 0; i < bufferSize; i++) signalReal[i] = input[i];
        for (int i = 0; i < window; i++) kernelReal[i] = input[window - 1 - i];
        fft(signalReal, signalImag, false);
        fft(kernelReal, kernelImag, false);
        for (int i = 0; i < fftSize; i++) {
            double real = signalReal[i] * kernelReal[i] - signalImag[i] * kernelImag[i];
            double imag = signalImag[i] * kernelReal[i] + signalReal[i] * kernelImag[i];
            signalReal[i] = real;
            signalImag[i] = imag;
        }
        fft(signalReal, signalImag, true);
        double basePower = squarePrefix[window];
        for (int tau = 1; tau < yinLength; tau++) {
            double shiftedPower = squarePrefix[tau + window] - squarePrefix[tau];
            double correlation = signalReal[window - 1 + tau];
            yinBuffer[tau] = (float) Math.max(0, basePower + shiftedPower - 2 * correlation);
        }
    }

    private void differenceDirect(float[] input, int yinLength, int analysisLength) {
        for (int tau = 1; tau < yinLength; tau++) {
            float sum = 0;
            for (int index = 0; index < analysisLength; index++) {
                float delta = input[index] - input[index + tau];
                sum += delta * delta;
            }
            yinBuffer[tau] = sum;
        }
    }

    private void cumulativeMeanNormalizedDifference(int yinLength) {
        yinBuffer[0] = 1;
        float runningSum = 0;
        for (int tau = 1; tau < yinLength; tau++) {
            runningSum += yinBuffer[tau];
            yinBuffer[tau] = runningSum <= 0 ? 1 : yinBuffer[tau] * tau / runningSum;
        }
    }

    private int absoluteThreshold(int startTau, int yinLength) {
        for (int tau = Math.max(2, startTau); tau < yinLength; tau++) {
            if (yinBuffer[tau] >= threshold) continue;
            while (tau + 1 < yinLength && yinBuffer[tau + 1] < yinBuffer[tau]) tau++;
            return tau;
        }
        return -1;
    }

    private double parabolicInterpolation(int tau, int yinLength) {
        int x0 = tau < 1 ? tau : tau - 1;
        int x2 = tau + 1 < yinLength ? tau + 1 : tau;
        if (x0 == tau) return yinBuffer[tau] <= yinBuffer[x2] ? tau : x2;
        if (x2 == tau) return yinBuffer[tau] <= yinBuffer[x0] ? tau : x0;
        double s0 = yinBuffer[x0];
        double s1 = yinBuffer[tau];
        double s2 = yinBuffer[x2];
        double denominator = 2 * (2 * s1 - s2 - s0);
        return Math.abs(denominator) < 1e-9 ? tau : tau + (s2 - s0) / denominator;
    }

    private static double rms(float[] input, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) sum += input[i] * input[i];
        return Math.min(1, Math.sqrt(sum / Math.max(1, length)));
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }

    private static void fft(double[] real, double[] imag, boolean inverse) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i >= j) continue;
            double tr = real[i];
            double ti = imag[i];
            real[i] = real[j];
            imag[i] = imag[j];
            real[j] = tr;
            imag[j] = ti;
        }
        for (int length = 2; length <= n; length <<= 1) {
            double angle = 2 * Math.PI / length * (inverse ? 1 : -1);
            double wLengthReal = Math.cos(angle);
            double wLengthImag = Math.sin(angle);
            for (int i = 0; i < n; i += length) {
                double wReal = 1;
                double wImag = 0;
                for (int j = 0; j < length / 2; j++) {
                    int even = i + j;
                    int odd = even + length / 2;
                    double oddReal = real[odd] * wReal - imag[odd] * wImag;
                    double oddImag = real[odd] * wImag + imag[odd] * wReal;
                    real[odd] = real[even] - oddReal;
                    imag[odd] = imag[even] - oddImag;
                    real[even] += oddReal;
                    imag[even] += oddImag;
                    double nextReal = wReal * wLengthReal - wImag * wLengthImag;
                    wImag = wReal * wLengthImag + wImag * wLengthReal;
                    wReal = nextReal;
                }
            }
        }
        if (!inverse) return;
        for (int i = 0; i < n; i++) {
            real[i] /= n;
            imag[i] /= n;
        }
    }

    private static class Detection {

        private static final Detection EMPTY = new Detection(0, 0);

        private final double frequencyHz;
        private final double confidence;

        private Detection(double frequencyHz, double confidence) {
            this.frequencyHz = frequencyHz;
            this.confidence = Math.max(0, Math.min(1, confidence));
        }
    }
}
