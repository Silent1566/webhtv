package com.fongmi.android.tv.player.engine;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MpvAudioCapabilities {

    private MpvAudioCapabilities() {
    }

    static String getAudioSpdifCodecs(Context context) {
        LinkedHashSet<String> codecs = new LinkedHashSet<>();
        AudioManager manager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (manager != null) {
            for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) addDeviceCodecs(codecs, device);
        }
        if (hasPassthroughOutputDevice(manager)) addDirectPlaybackCodecs(codecs);
        String value = TextUtils.join(",", codecs);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-audio", "spdif codecs=%s devices=%s", value, describeDevices(manager));
        return value;
    }

    private static void addDeviceCodecs(Set<String> codecs, AudioDeviceInfo device) {
        if (device == null || !device.isSink()) return;
        int[] encodings = device.getEncodings();
        if (encodings == null || encodings.length == 0) return;
        for (int encoding : encodings) addEncodingCodec(codecs, encoding);
    }

    private static boolean hasPassthroughOutputDevice(AudioManager manager) {
        if (manager == null) return false;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device != null && device.isSink() && isPassthroughOutputType(device.getType())) return true;
        }
        return false;
    }

    private static boolean isPassthroughOutputType(int type) {
        return type == AudioDeviceInfo.TYPE_HDMI
                || type == AudioDeviceInfo.TYPE_HDMI_ARC
                || type == AudioDeviceInfo.TYPE_HDMI_EARC
                || type == AudioDeviceInfo.TYPE_USB_DEVICE;
    }

    private static void addDirectPlaybackCodecs(Set<String> codecs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_AC3, "ac3");
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_E_AC3, "eac3");
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_E_AC3_JOC, "eac3");
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_DTS, "dts");
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_DTS_HD, "dts-hd");
        addDirectPlaybackCodec(codecs, AudioFormat.ENCODING_DOLBY_TRUEHD, "truehd");
    }

    private static void addDirectPlaybackCodec(Set<String> codecs, int encoding, String codec) {
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
                .build();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        if (AudioTrack.isDirectPlaybackSupported(format, attributes)) codecs.add(codec);
    }

    private static void addEncodingCodec(Set<String> codecs, int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_AC3 -> codecs.add("ac3");
            case AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC -> codecs.add("eac3");
            case AudioFormat.ENCODING_DTS -> codecs.add("dts");
            case AudioFormat.ENCODING_DTS_HD -> codecs.add("dts-hd");
            case AudioFormat.ENCODING_DOLBY_TRUEHD -> codecs.add("truehd");
            default -> {
            }
        }
    }

    private static String describeDevices(AudioManager manager) {
        if (manager == null) return "";
        List<String> devices = new ArrayList<>();
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device == null || !device.isSink()) continue;
            devices.add(deviceTypeName(device.getType()) + ":" + encodingText(device.getEncodings()));
        }
        return TextUtils.join(",", devices);
    }

    private static String encodingText(int[] encodings) {
        if (encodings == null || encodings.length == 0) return "";
        List<String> values = new ArrayList<>();
        for (int encoding : encodings) values.add(String.valueOf(encoding));
        return TextUtils.join("/", values);
    }

    private static String deviceTypeName(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco";
            case AudioDeviceInfo.TYPE_HDMI -> "hdmi";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "hdmi_arc";
            case AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi_earc";
            case AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device";
            case AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset";
            default -> "type_" + type;
        };
    }
}
