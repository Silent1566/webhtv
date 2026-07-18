package com.fongmi.android.tv.player.karaoke;

import java.util.Locale;

public class KaraokePitch {

    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private KaraokePitch() {
    }

    public static double frequencyToMidi(double frequencyHz) {
        if (frequencyHz <= 0) return Double.NaN;
        return 69.0 + 12.0 * log2(frequencyHz / 440.0);
    }

    public static int frequencyToNearestMidi(double frequencyHz) {
        double midi = frequencyToMidi(frequencyHz);
        return Double.isNaN(midi) ? -1 : (int) Math.round(midi);
    }

    public static double midiToFrequency(double midi) {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    public static String midiToName(int midi) {
        if (midi < 0) return "";
        int pitchClass = floorMod(midi, 12);
        int octave = midi / 12 - 1;
        return String.format(Locale.US, "%s%d", NOTE_NAMES[pitchClass], octave);
    }

    public static double semitoneDistance(double sungMidi, double targetPitch, boolean ignoreOctave) {
        if (Double.isNaN(sungMidi)) return Double.NaN;
        double distance = sungMidi - targetPitch;
        return ignoreOctave ? foldToNearestOctave(distance) : distance;
    }

    public static boolean matches(double sungMidi, double targetPitch, double toleranceSemitones, boolean ignoreOctave) {
        double distance = semitoneDistance(sungMidi, targetPitch, ignoreOctave);
        return !Double.isNaN(distance) && Math.abs(distance) <= Math.max(0, toleranceSemitones);
    }

    private static double foldToNearestOctave(double distance) {
        return distance - 12.0 * Math.floor((distance + 6.0) / 12.0);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static int floorMod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }
}
