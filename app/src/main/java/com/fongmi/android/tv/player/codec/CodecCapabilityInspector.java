package com.fongmi.android.tv.player.codec;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Range;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import com.fongmi.android.tv.player.PlayerManager;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CodecCapabilityInspector {

    public static final int TYPE_ALL = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;

    private static final DecimalFormat DECIMAL = new DecimalFormat("#.###");
    private static volatile List<CodecEntry> cache;

    private CodecCapabilityInspector() {
    }

    public static String buildDeviceReport(String keyword, int type) {
        return buildDeviceReport(null, null, keyword, type);
    }

    public static String buildDeviceReport(Context context, PlayerManager player, String keyword, int type) {
        String query = normalize(keyword);
        TrackMatcher matcher = TrackMatcher.create(context, player);
        StringBuilder builder = new StringBuilder();
        int total = 0;
        int matched = 0;
        for (CodecEntry entry : getHardwareDecoders()) {
            if (!matchesType(entry, type)) continue;
            total++;
            String text = entry.text + matcher.describe(entry);
            if (!TextUtils.isEmpty(query) && !normalize(text).contains(query)) continue;
            if (matched++ > 0) builder.append("\n\n");
            builder.append(text);
        }
        if (matched == 0) {
            if (total == 0) return type == TYPE_AUDIO ? "未发现系统音频解码器" : "未发现匹配类型的硬件解码器";
            return TextUtils.isEmpty(query) ? "未发现解码能力" : "没有匹配关键词的解码能力";
        }
        String title = type == TYPE_AUDIO ? "系统音频解码器 " : type == TYPE_VIDEO ? "硬件视频解码器 " : "解码器 ";
        return title + matched + "/" + total + "\n\n" + builder;
    }

    public static String buildCurrentMediaReport(Context context, PlayerManager player, String keyword) {
        if (player == null) return "当前播放器不可用";
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return "当前还没有读取到媒体轨道，请开始播放后再查询";
        String query = normalize(keyword);
        StringBuilder builder = new StringBuilder();
        int total = 0;
        int matched = 0;
        int videoIndex = 0;
        int audioIndex = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            int type = group.getType();
            if (type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                String text = formatTrack(context, type, type == C.TRACK_TYPE_VIDEO ? ++videoIndex : ++audioIndex, format, group.getTrackSupport(i), group.isTrackSelected(i));
                total++;
                if (!TextUtils.isEmpty(query) && !normalize(text).contains(query)) continue;
                if (matched++ > 0) builder.append("\n\n");
                builder.append(text);
            }
        }
        if (matched == 0) {
            if (total == 0) return "当前媒体没有视频/音频轨道";
            return "当前媒体轨道没有匹配关键词";
        }
        return "当前媒体轨道 " + matched + "/" + total + "\n\n" + builder;
    }

    public static List<CodecEntry> getHardwareDecoders() {
        List<CodecEntry> value = cache;
        if (value != null) return value;
        List<CodecEntry> entries = new ArrayList<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String mime : info.getSupportedTypes()) {
                    CodecEntry entry = buildEntry(info, mime);
                    if (entry != null) entries.add(entry);
                }
            }
        } catch (Throwable ignored) {
        }
        entries.sort(Comparator.comparing((CodecEntry entry) -> entry.kind).thenComparing(entry -> entry.mime).thenComparing(entry -> entry.name));
        return cache = Collections.unmodifiableList(entries);
    }

    private static CodecEntry buildEntry(MediaCodecInfo info, String mime) {
        try {
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
            boolean video = mime != null && mime.startsWith("video/");
            boolean audio = mime != null && mime.startsWith("audio/");
            if (!video && !audio) return null;
            if (video && !isHardwareCodec(info)) return null;
            String text = video ? videoEntry(info, mime, caps) : audioEntry(info, mime, caps);
            return new CodecEntry(video ? TYPE_VIDEO : TYPE_AUDIO, video ? "视频" : "音频", info.getName(), mime, text, normalize(text));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String videoEntry(MediaCodecInfo info, String mime, MediaCodecInfo.CodecCapabilities caps) {
        StringBuilder builder = new StringBuilder();
        builder.append("视频 ").append(mime).append("\n");
        builder.append("decoder ").append(info.getName()).append("\n");
        try {
            MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
            builder.append("范围 ").append(range(video.getSupportedWidths())).append(" x ").append(range(video.getSupportedHeights()));
            builder.append(" / 对齐 ").append(video.getWidthAlignment()).append("x").append(video.getHeightAlignment()).append("\n");
            builder.append("帧率 ").append(range(video.getSupportedFrameRates())).append(" / 码率 ").append(formatBitrateRange(video.getBitrateRange())).append("\n");
            builder.append("检查点 ")
                    .append("5164x2160@60 ").append(yesNo(supports(video, 5164, 2160, 60))).append(" / ")
                    .append("5164x2160@30 ").append(yesNo(supports(video, 5164, 2160, 30))).append(" / ")
                    .append("4K60 ").append(yesNo(supports(video, 3840, 2160, 60))).append(" / ")
                    .append("4K30 ").append(yesNo(supports(video, 3840, 2160, 30))).append(" / ")
                    .append("1440p60 ").append(yesNo(supports(video, 2560, 1440, 60))).append(" / ")
                    .append("1080p60 ").append(yesNo(supports(video, 1920, 1080, 60)));
        } catch (Throwable e) {
            builder.append("视频能力读取失败 ").append(e.getClass().getSimpleName());
        }
        String profiles = profileLevels(caps);
        if (!TextUtils.isEmpty(profiles)) builder.append("\nprofile/level ").append(profiles);
        return builder.toString();
    }

    private static String audioEntry(MediaCodecInfo info, String mime, MediaCodecInfo.CodecCapabilities caps) {
        StringBuilder builder = new StringBuilder();
        builder.append("音频 ").append(mime).append(" / ").append(codecClass(info)).append("\n");
        builder.append("decoder ").append(info.getName()).append("\n");
        try {
            MediaCodecInfo.AudioCapabilities audio = caps.getAudioCapabilities();
            builder.append("声道 max ").append(audio.getMaxInputChannelCount());
            builder.append(" / 采样率 ").append(sampleRates(audio.getSupportedSampleRates()));
            builder.append(" / 码率 ").append(formatBitrateRange(audio.getBitrateRange()));
        } catch (Throwable e) {
            builder.append("音频能力读取失败 ").append(e.getClass().getSimpleName());
        }
        String profiles = profileLevels(caps);
        if (!TextUtils.isEmpty(profiles)) builder.append("\nprofile/level ").append(profiles);
        return builder.toString();
    }

    private static String formatTrack(Context context, int type, int index, Format format, int support, boolean selected) {
        StringBuilder builder = new StringBuilder();
        builder.append(type == C.TRACK_TYPE_VIDEO ? "视频轨 " : "音频轨 ").append(index);
        builder.append(selected ? " / 已选中" : " / 未选中").append("\n");
        builder.append("格式 ").append(type == C.TRACK_TYPE_VIDEO ? videoFormat(format) : audioFormat(format)).append("\n");
        builder.append("Media3轨道状态 ").append(supportText(support)).append("\n");
        builder.append(type == C.TRACK_TYPE_VIDEO ? "硬解查询 " : "音频解码 ").append(formatSupport(context, format));
        return builder.toString();
    }

    private static List<TrackRef> getTrackRefs(Context context, PlayerManager player) {
        if (context == null || player == null) return List.of();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return List.of();
        List<TrackRef> refs = new ArrayList<>();
        int videoIndex = 0;
        int audioIndex = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            int type = group.getType();
            if (type != C.TRACK_TYPE_VIDEO && type != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                String mime = getSampleMimeType(format);
                if (TextUtils.isEmpty(mime)) continue;
                int index = type == C.TRACK_TYPE_VIDEO ? ++videoIndex : ++audioIndex;
                refs.add(new TrackRef(type == C.TRACK_TYPE_VIDEO ? TYPE_VIDEO : TYPE_AUDIO, type == C.TRACK_TYPE_VIDEO ? "视频轨" : "音频轨", index, mime, format, group.isTrackSelected(i), decoderNames(context, mime, format)));
            }
        }
        return refs;
    }

    private static List<String> decoderNames(Context context, String mime, Format format) {
        boolean audio = mime.startsWith("audio/");
        List<String> names = new ArrayList<>();
        try {
            for (androidx.media3.exoplayer.mediacodec.MediaCodecInfo info : MediaCodecSelector.DEFAULT.getDecoderInfos(mime, false, false)) {
                if (!audio && !info.hardwareAccelerated) continue;
                if (info.isFormatSupported(context, format)) names.add(info.name);
                else if (!names.contains(info.name)) names.add(info.name);
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private static String formatSupport(Context context, Format format) {
        String mime = getSampleMimeType(format);
        if (TextUtils.isEmpty(mime)) return "无法识别 MIME，codecs=" + empty(format == null ? null : format.codecs);
        boolean audio = mime.startsWith("audio/");
        List<String> supported = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        try {
            for (androidx.media3.exoplayer.mediacodec.MediaCodecInfo info : MediaCodecSelector.DEFAULT.getDecoderInfos(mime, false, false)) {
                if (!audio && !info.hardwareAccelerated) continue;
                candidates.add(info.name);
                if (info.isFormatSupported(context, format)) supported.add(info.name);
            }
        } catch (MediaCodecUtil.DecoderQueryException e) {
            return "查询失败 " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (Throwable e) {
            return "查询失败 " + e.getClass().getSimpleName();
        }
        if (!supported.isEmpty()) return audio ? "当前音频可由系统解码 / " + TextUtils.join(", ", supported) : "当前规格可硬解 / " + TextUtils.join(", ", supported);
        if (!candidates.isEmpty()) return audio ? "有系统音频 decoder，但当前规格未声明支持 / 候选 " + TextUtils.join(", ", candidates) : "有硬解器支持该 MIME，但当前规格未声明可硬解 / 候选 " + TextUtils.join(", ", candidates);
        return audio ? "没有该 MIME 的系统音频 decoder" : "没有该 MIME 的硬件 video decoder";
    }

    private static String videoFormat(Format format) {
        if (format == null) return "-";
        List<String> parts = new ArrayList<>();
        parts.add(empty(getSampleMimeType(format)));
        if (format.width > 0 && format.height > 0) parts.add(format.width + "x" + format.height);
        if (format.frameRate > 0) parts.add("@" + DECIMAL.format(format.frameRate) + "fps");
        if (bitrateValue(format) > 0) parts.add(formatBitrate(bitrateValue(format)));
        if (!TextUtils.isEmpty(format.codecs)) parts.add("codecs " + format.codecs);
        if (format.colorInfo != null) parts.add("color " + format.colorInfo.toLogString());
        return TextUtils.join(" ", parts);
    }

    private static String audioFormat(Format format) {
        if (format == null) return "-";
        List<String> parts = new ArrayList<>();
        parts.add(empty(getSampleMimeType(format)));
        if (format.channelCount > 0) parts.add(format.channelCount + "ch");
        if (format.sampleRate > 0) parts.add(format.sampleRate + "Hz");
        if (bitrateValue(format) > 0) parts.add(formatBitrate(bitrateValue(format)));
        if (!TextUtils.isEmpty(format.language)) parts.add(format.language);
        if (!TextUtils.isEmpty(format.codecs)) parts.add("codecs " + format.codecs);
        return TextUtils.join(" ", parts);
    }

    private static String getSampleMimeType(Format format) {
        if (format == null) return null;
        if (!TextUtils.isEmpty(format.sampleMimeType)) return normalizeAudioMime(format.sampleMimeType);
        if (TextUtils.isEmpty(format.codecs)) return null;
        return MimeTypes.getMediaMimeType(format.codecs);
    }

    private static int bitrateValue(Format format) {
        if (format == null) return 0;
        if (format.bitrate > 0) return format.bitrate;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        return 0;
    }

    private static String normalizeAudioMime(String mime) {
        if (mime == null) return null;
        if (mime.startsWith(MimeTypes.AUDIO_DTS_HD + ";") || MimeTypes.AUDIO_MEDIA3_DTS_HD_MA_CORELESS.equals(mime)) return MimeTypes.AUDIO_DTS_HD;
        if (MimeTypes.AUDIO_AMR.equals(mime)) return MimeTypes.AUDIO_AMR_NB;
        return mime;
    }

    private static boolean supports(MediaCodecInfo.VideoCapabilities caps, int width, int height, double fps) {
        try {
            return caps.areSizeAndRateSupported(width, height, fps) || caps.areSizeAndRateSupported(height, width, fps);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean isHardwareCodec(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase(Locale.US);
        return !name.contains("google") && !name.contains("android") && !name.contains("ffmpeg") && !name.contains("software") && !name.startsWith("c2.android");
    }

    private static String codecClass(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (info.isHardwareAccelerated()) return "硬件";
            if (info.isSoftwareOnly()) return "平台软件";
        }
        return isHardwareCodec(info) ? "硬件" : "系统";
    }

    private static boolean matchesType(CodecEntry entry, int type) {
        return type == TYPE_ALL || entry.type == type;
    }

    private static String supportText(int support) {
        return switch (support) {
            case C.FORMAT_HANDLED -> "支持，当前轨道在声明能力内";
            case C.FORMAT_EXCEEDS_CAPABILITIES -> "超出设备声明能力，不应判定为可硬解";
            case C.FORMAT_UNSUPPORTED_DRM -> "不支持，DRM 不满足";
            case C.FORMAT_UNSUPPORTED_SUBTYPE -> "不支持，该编码子类型不支持";
            case C.FORMAT_UNSUPPORTED_TYPE -> "不支持，该媒体类型不支持";
            default -> "未知状态 " + support;
        };
    }

    private static String profileLevels(MediaCodecInfo.CodecCapabilities caps) {
        if (caps.profileLevels == null || caps.profileLevels.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        int count = Math.min(caps.profileLevels.length, 12);
        for (int i = 0; i < count; i++) {
            MediaCodecInfo.CodecProfileLevel level = caps.profileLevels[i];
            if (i > 0) builder.append(", ");
            builder.append(level.profile).append("/").append(level.level);
        }
        if (caps.profileLevels.length > count) builder.append("...");
        return builder.toString();
    }

    private static String sampleRates(int[] rates) {
        if (rates == null || rates.length == 0) return "-";
        StringBuilder builder = new StringBuilder();
        int count = Math.min(rates.length, 10);
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(",");
            builder.append(rates[i]);
        }
        if (rates.length > count) builder.append("...");
        return builder.toString();
    }

    private static String formatBitrateRange(Range<Integer> range) {
        if (range == null) return "-";
        return formatBitrate(range.getLower()) + "-" + formatBitrate(range.getUpper());
    }

    private static String formatBitrate(long bitrate) {
        if (bitrate <= 0) return "-";
        if (bitrate < 1_000_000) return Math.round(bitrate / 1000f) + "Kbps";
        return DECIMAL.format(bitrate / 1_000_000f) + "Mbps";
    }

    private static String range(Range<?> range) {
        if (range == null) return "-";
        return range.getLower() + "-" + range.getUpper();
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private static String empty(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    public record CodecEntry(int type, String kind, String name, String mime, String text, String searchText) {
    }

    private record TrackRef(int type, String label, int index, String mime, Format format, boolean selected, List<String> decoderNames) {

        boolean matches(CodecEntry entry) {
            return type == entry.type && mime.equals(entry.mime) && decoderNames.contains(entry.name);
        }

        String text() {
            return label + index + (selected ? "(已选中)" : "") + " " + shortFormat(format);
        }

        private String shortFormat(Format format) {
            if (format == null) return "";
            if (type == TYPE_VIDEO) {
                List<String> parts = new ArrayList<>();
                if (format.width > 0 && format.height > 0) parts.add(format.width + "x" + format.height);
                if (format.frameRate > 0) parts.add("@" + DECIMAL.format(format.frameRate) + "fps");
                if (bitrateValue(format) > 0) parts.add(formatBitrate(bitrateValue(format)));
                return TextUtils.join(" ", parts);
            }
            List<String> parts = new ArrayList<>();
            if (format.channelCount > 0) parts.add(format.channelCount + "ch");
            if (format.sampleRate > 0) parts.add(format.sampleRate + "Hz");
            if (bitrateValue(format) > 0) parts.add(formatBitrate(bitrateValue(format)));
            if (!TextUtils.isEmpty(format.language)) parts.add(format.language);
            return TextUtils.join(" ", parts);
        }
    }

    private static final class TrackMatcher {

        private final List<TrackRef> refs;

        private TrackMatcher(List<TrackRef> refs) {
            this.refs = refs;
        }

        static TrackMatcher create(Context context, PlayerManager player) {
            return new TrackMatcher(getTrackRefs(context, player));
        }

        String describe(CodecEntry entry) {
            if (refs.isEmpty()) return "";
            List<String> all = new ArrayList<>();
            List<String> selected = new ArrayList<>();
            for (TrackRef ref : refs) {
                if (!ref.matches(entry)) continue;
                all.add(ref.text());
                if (ref.selected) selected.add(ref.text());
            }
            if (all.isEmpty()) return "";
            StringBuilder builder = new StringBuilder();
            builder.append("\n可解当前媒体").append(entry.kind).append("轨 ").append(TextUtils.join(" / ", all));
            if (!selected.isEmpty()) builder.append("\n可解当前选中").append(entry.kind).append("轨 ").append(TextUtils.join(" / ", selected));
            return builder.toString();
        }
    }
}
