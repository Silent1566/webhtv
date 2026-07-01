package com.fongmi.android.tv.ui.custom;

import android.net.Uri;
import android.net.TrafficStats;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlayerOsdController {

    public interface Source {
        PlayerManager getPlayer();

        String getTitle();
    }

    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("#.0");
    private static final int UID = App.get().getApplicationInfo().uid;

    private final SimpleDateFormat timeFormat;
    private final TextView topLeft;
    private final TextView topRight;
    private final TextView bottomLeft;
    private final TextView bottomRight;
    private final TextView diagnostics;
    private final MiniProgressView miniProgress;
    private final Runnable update;
    private final Source source;
    private final View root;
    private final float miniSp;

    private boolean suppressed;
    private final DecimalFormat frameFormat;
    private final DecimalFormat refreshFormat;
    private final DecimalFormat bitrateFormat;
    private long lastTotalRxBytes;
    private long lastTimeStamp;
    private long lastSpeedKBps;
    private String lastSpeedText;
    private boolean controlsVisible;
    private boolean diagnosticsVisible;
    private boolean started;

    public PlayerOsdController(View root, TextView topLeft, TextView topRight, TextView bottomLeft, TextView bottomRight, TextView diagnostics, MiniProgressView miniProgress, Source source, float miniSp) {
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.bitrateFormat = new DecimalFormat("#.0");
        this.refreshFormat = new DecimalFormat("#.##");
        this.frameFormat = new DecimalFormat("#.###");
        this.miniProgress = miniProgress;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
        this.diagnostics = diagnostics;
        this.topRight = topRight;
        this.topLeft = topLeft;
        this.miniSp = miniSp;
        this.source = source;
        this.root = root;
        this.update = this::update;
    }

    public void start() {
        started = true;
        if (suppressed || !PlayerSetting.isOsdEnabled()) {
            root.setVisibility(View.GONE);
            return;
        }
        resetSpeed();
        App.removeCallbacks(update);
        App.post(update, 0);
    }

    public void stop() {
        started = false;
        App.removeCallbacks(update);
        root.setVisibility(View.GONE);
    }

    public void release() {
        stop();
    }

    public void setSuppressed(boolean suppressed) {
        if (this.suppressed == suppressed) return;
        this.suppressed = suppressed;
        App.removeCallbacks(update);
        if (suppressed) root.setVisibility(View.GONE);
        else if (started) start();
    }

    public void setControlsVisible(boolean controlsVisible) {
        if (this.controlsVisible == controlsVisible) return;
        this.controlsVisible = controlsVisible;
        if (started) render();
    }

    public boolean isDiagnosticsVisible() {
        return diagnosticsVisible;
    }

    public void setDiagnosticsVisible(boolean visible) {
        boolean next = visible && PlayerSetting.isOsdDiagnostics();
        if (diagnosticsVisible == next) return;
        diagnosticsVisible = next;
        if (started) render();
    }

    public void toggleDiagnostics() {
        if (!PlayerSetting.isOsdDiagnostics()) return;
        diagnosticsVisible = !diagnosticsVisible;
        if (started) render();
    }

    private void update() {
        if (render()) App.post(update, 1000);
    }

    private boolean render() {
        if (suppressed) {
            root.setVisibility(View.GONE);
            return false;
        }
        boolean enabled = PlayerSetting.isOsdEnabled();
        if (!enabled) {
            root.setVisibility(View.GONE);
            return false;
        }
        root.setVisibility(controlsVisible ? View.GONE : View.VISIBLE);
        if (controlsVisible) return true;
        setTextSize(miniSp);
        PlayerManager player = source.getPlayer();
        updateSpeed();
        setTopLeft(player);
        setTopRight();
        setBottomLeft(player);
        setBottomRight();
        setDiagnosticsPanel(player);
        setMiniProgress(player);
        return true;
    }

    private void setTopLeft(PlayerManager player) {
        boolean showTitle = PlayerSetting.isOsdTitle();
        boolean showResolution = PlayerSetting.isOsdResolution();
        if ((!showTitle && !showResolution) || diagnosticsVisible) {
            topLeft.setVisibility(View.GONE);
            return;
        }
        String title = showTitle ? source.getTitle() : "";
        String size = showResolution && player != null ? player.getSizeText() : "";
        topLeft.setText(join("\n", title, size));
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopRight() {
        topRight.setVisibility(PlayerSetting.isOsdTime() ? View.VISIBLE : View.GONE);
        if (PlayerSetting.isOsdTime()) topRight.setText(timeFormat.format(new Date()));
    }

    private void setBottomLeft(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdProgress() || player == null || player.isLive()) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        long position = Math.max(0, player.getPosition());
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        bottomLeft.setText(Util.timeMs(position) + " / " + Util.timeMs(duration));
        bottomLeft.setVisibility(View.VISIBLE);
    }

    private void setBottomRight() {
        bottomRight.setVisibility(PlayerSetting.isOsdTraffic() ? View.VISIBLE : View.GONE);
        if (!PlayerSetting.isOsdTraffic()) return;
        bottomRight.setText(lastSpeedText);
        bottomRight.setVisibility(TextUtils.isEmpty(lastSpeedText) ? View.GONE : View.VISIBLE);
    }

    private void setDiagnosticsPanel(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdDiagnostics() || !diagnosticsVisible || player == null) {
            diagnostics.setVisibility(View.GONE);
            return;
        }
        String text = getDiagnostics(player);
        diagnostics.setText(text);
        diagnostics.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private void setMiniProgress(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdMini() || player == null || player.isLive()) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        miniProgress.setProgress(player.getPosition(), duration);
        miniProgress.setVisibility(View.VISIBLE);
    }

    private void setTextSize(float sp) {
        topLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        diagnostics.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private void updateSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        if (total == TrafficStats.UNSUPPORTED) {
            lastSpeedKBps = 0;
            lastSpeedText = "";
            return;
        }
        long now = System.currentTimeMillis();
        long rxKb = total / 1024;
        long speed = (rxKb - lastTotalRxBytes) * 1000 / Math.max(now - lastTimeStamp, 1);
        lastTimeStamp = now;
        lastTotalRxBytes = rxKb;
        lastSpeedKBps = Math.max(0, speed);
        lastSpeedText = formatSpeed(lastSpeedKBps);
    }

    private String getDiagnostics(PlayerManager player) {
        PlaybackAnalyticsListener.Snapshot snapshot = player.isIjk() ? PlaybackAnalyticsListener.Snapshot.empty() : PlaybackAnalyticsListener.getSnapshot();
        Format video = snapshot.videoFormat() != null ? snapshot.videoFormat() : player.getVideoFormat();
        Format audio = snapshot.audioFormat();
        String state = stateText(player.getPlaybackState()) + (player.isLoading() ? " / 正在加载" : "");
        String buffer = join(" / ", formatDuration(player.getBufferedDuration()), player.getBufferedPercentage() > 0 ? player.getBufferedPercentage() + "%" : "");
        String rebuffer = snapshot.rebufferCount() <= 0 ? "0 次" : snapshot.rebufferCount() + " 次 / " + formatDuration(snapshot.rebufferTotalMs());
        String network = join(" / ", "当前 " + emptyDash(lastSpeedText), "估算带宽 " + emptyDash(formatBitrate(snapshot.bandwidthEstimate())), snapshot.lastLoadBytes() > 0 ? "最近加载 " + formatBytes(snapshot.lastLoadBytes()) + " / " + snapshot.lastLoadTimeMs() + " ms" : "");
        String videoText = summarizeVideo(video, player, snapshot.videoDecoderName());
        AudioTrackState audioTrack = getAudioTrackState(player);
        String audioText = summarizeAudio(audio, audioTrack, snapshot.audioDecoderName());
        String render = PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "Surface" : "Texture";
        String tunnel = switchText(PlayerSetting.isTunnelingEnabled());
        String enhance = switchText(PlayerSetting.isExoEnhanced());
        String passThrough = switchText(PlayerSetting.isAudioPassThrough());
        String playerText = join(" / ", player.getPlayerText(), player.getDecodeText(), render, "隧道 " + tunnel, "EXO增强 " + enhance, "音频直通 " + passThrough, player.isIjk() ? "" : "FFmpeg音频兜底 开");
        String error = join(" ", snapshot.errorCode(), shortText(snapshot.errorMessage(), 72));
        String display = getDisplayRefreshText();
        return join("\n",
                row("结论", getDiagnosis(player, snapshot, video, audioTrack)),
                row("网络", network),
                row("缓冲", join(" / ", state, buffer, "重缓冲 " + rebuffer)),
                row("视频", videoText),
                row("音频", audioText),
                row("掉帧", String.valueOf(snapshot.droppedFrames())),
                row("播放", playerText),
                row("来源", summarizeSource(player.getUrl())),
                row("屏幕", TextUtils.isEmpty(display) ? "-" : display),
                TextUtils.isEmpty(error) ? "" : row("错误", error));
    }

    private String getDiagnosis(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot, Format video, AudioTrackState audioTrack) {
        if (!TextUtils.isEmpty(snapshot.errorCode())) return "播放器报错，先看错误行";
        if (audioTrack.hasTracks() && audioTrack.isUnsupported()) return "音频轨不支持：" + summarizeAudioFormat(audioTrack.format()) + " / " + supportText(audioTrack.support());
        if (audioTrack.hasTracks() && !audioTrack.selected() && snapshot.audioFormat() == null && player.getPlaybackState() == androidx.media3.common.Player.STATE_READY) return "已发现音轨但未选中，可能无声";
        if (audioTrack.hasTracks() && TextUtils.isEmpty(snapshot.audioDecoderName()) && player.getPlaybackState() == androidx.media3.common.Player.STATE_READY) return "已发现音轨但 decoder 未初始化，可能无声";
        long mediaBitrate = getMediaBitrate(video, snapshot.audioFormat());
        long availableBitrate = snapshot.bandwidthEstimate() > 0 ? snapshot.bandwidthEstimate() : lastSpeedKBps * 1024 * 8;
        if (availableBitrate > 0 && mediaBitrate > 0 && availableBitrate < mediaBitrate * 13 / 10) return "网速可能低于资源码率";
        if (player.isLoading() && player.getBufferedDuration() < 3000) return "缓冲偏少，可能是网络或源响应慢";
        if (snapshot.droppedFrames() >= 60) return "掉帧较多，可能是解码或渲染压力";
        if (video != null && video.bitrate >= 30_000_000) return "资源码率较高，对网络和解码要求高";
        if (audioTrack.hasTracks() && snapshot.audioFormat() == null) return "正在等待音频轨信息";
        return "正常";
    }

    private String summarizeVideo(Format format, PlayerManager player, String decoder) {
        String size = getSize(format, player);
        String fps = getFrameRate(format);
        String bitrate = getBitrate(format);
        return join(" ", getMime(format), size, TextUtils.isEmpty(fps) ? "" : "@" + fps, bitrate, TextUtils.isEmpty(decoder) ? "" : "decoder " + decoder);
    }

    private String summarizeAudio(Format format, AudioTrackState audioTrack, String decoder) {
        if (format == null) {
            if (!audioTrack.hasTracks()) return "未发现音轨";
            return join(" / ", summarizeAudioFormat(audioTrack.format()), supportText(audioTrack.support()), audioTrack.supportSummary(), audioTrack.selected() ? "已选中" : "未选中");
        }
        String support = audioTrack.hasTracks() && !audioTrack.isHandled() ? supportText(audioTrack.support()) : "";
        return join(" ", summarizeAudioFormat(format), getBitrate(format), TextUtils.isEmpty(decoder) ? "" : "decoder " + decoder, support);
    }

    private String summarizeAudioFormat(Format format) {
        if (format == null) return "";
        String channels = format.channelCount <= 0 ? "" : format.channelCount + "ch";
        String sampleRate = format.sampleRate <= 0 ? "" : format.sampleRate % 1000 == 0 ? (format.sampleRate / 1000) + "kHz" : bitrateFormat.format(format.sampleRate / 1000f) + "kHz";
        return join(" ", getAudioMime(format), channels, sampleRate, TextUtils.isEmpty(format.language) ? "" : format.language);
    }

    private AudioTrackState getAudioTrackState(PlayerManager player) {
        if (player == null || player.isIjk()) return AudioTrackState.empty();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return AudioTrackState.empty();
        AudioTrackCandidate selected = null;
        AudioTrackCandidate handled = null;
        AudioTrackCandidate unsupported = null;
        AudioTrackCandidate first = null;
        int total = 0;
        int supported = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                total++;
                int support = group.getTrackSupport(i);
                boolean isSelected = group.isTrackSelected(i);
                if (support == C.FORMAT_HANDLED || support == C.FORMAT_EXCEEDS_CAPABILITIES) supported++;
                AudioTrackCandidate candidate = new AudioTrackCandidate(group.getTrackFormat(i), support, isSelected);
                if (first == null) first = candidate;
                if (isSelected) selected = candidate;
                if (handled == null && support == C.FORMAT_HANDLED) handled = candidate;
                if (unsupported == null && isUnsupportedSupport(support)) unsupported = candidate;
            }
        }
        AudioTrackCandidate candidate = selected != null ? selected : handled != null ? handled : unsupported != null ? unsupported : first;
        return candidate == null ? new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, total, supported) : new AudioTrackState(candidate.format(), candidate.support(), candidate.selected(), total, supported);
    }

    private boolean isUnsupportedSupport(int support) {
        return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
    }

    private String supportText(int support) {
        return switch (support) {
            case C.FORMAT_HANDLED -> "支持";
            case C.FORMAT_EXCEEDS_CAPABILITIES -> "支持但超规格: EXCEEDS_CAPABILITIES";
            case C.FORMAT_UNSUPPORTED_DRM -> "不支持: NO_UNSUPPORTED_DRM";
            case C.FORMAT_UNSUPPORTED_SUBTYPE -> "不支持: NO_UNSUPPORTED_SUBTYPE";
            case C.FORMAT_UNSUPPORTED_TYPE -> "不支持: NO_UNSUPPORTED_TYPE";
            default -> "支持状态 " + support;
        };
    }

    private String getSize(Format format, PlayerManager player) {
        int width = format == null || format.width <= 0 ? player.getVideoWidth() : format.width;
        int height = format == null || format.height <= 0 ? player.getVideoHeight() : format.height;
        return width <= 0 || height <= 0 ? "" : width + "x" + height;
    }

    private String getFrameRate(Format format) {
        if (format == null || format.frameRate <= 0) return "";
        return frameFormat.format(format.frameRate) + "fps";
    }

    private String getBitrate(Format format) {
        return format == null ? "" : formatBitrate(format.bitrate);
    }

    private long getMediaBitrate(Format video, Format audio) {
        long bitrate = 0;
        if (video != null && video.bitrate > 0) bitrate += video.bitrate;
        if (audio != null && audio.bitrate > 0) bitrate += audio.bitrate;
        return bitrate;
    }

    private String getMime(Format format) {
        if (format == null) return "";
        if (!TextUtils.isEmpty(format.sampleMimeType)) {
            int index = format.sampleMimeType.indexOf('/');
            return index >= 0 && index + 1 < format.sampleMimeType.length() ? format.sampleMimeType.substring(index + 1) : format.sampleMimeType;
        }
        return TextUtils.isEmpty(format.codecs) ? "" : format.codecs;
    }

    private String getAudioMime(Format format) {
        if (format == null) return "";
        String mime = format.sampleMimeType;
        if (MimeTypes.AUDIO_DTS_X.equals(mime)) return "DTS:X";
        if (MimeTypes.AUDIO_DTS_MA.equals(mime)) return "DTS-HD MA";
        if (MimeTypes.AUDIO_DTS_EXPRESS.equals(mime)) return "DTS-HD LBR";
        if (MimeTypes.AUDIO_DTS_UHD_P2.equals(mime)) return "DTS-UHD P2";
        if (MimeTypes.AUDIO_DTS_HD.equals(mime)) return "DTS-HD";
        if (MimeTypes.AUDIO_DTS.equals(mime)) return "DTS";
        if (MimeTypes.AUDIO_TRUEHD.equals(mime)) return "TrueHD";
        if (MimeTypes.AUDIO_E_AC3_JOC.equals(mime)) return "E-AC3 JOC";
        if (MimeTypes.AUDIO_E_AC3.equals(mime)) return "E-AC3";
        if (MimeTypes.AUDIO_AC3.equals(mime)) return "AC3";
        if (MimeTypes.AUDIO_AAC.equals(mime)) return "AAC";
        if (MimeTypes.AUDIO_FLAC.equals(mime)) return "FLAC";
        if (MimeTypes.AUDIO_MPEG.equals(mime)) return "MP3";
        if (MimeTypes.AUDIO_OPUS.equals(mime)) return "Opus";
        if (MimeTypes.AUDIO_AMR.equals(mime) || MimeTypes.AUDIO_AMR_NB.equals(mime)) return "AMR-NB";
        if (MimeTypes.AUDIO_AMR_WB.equals(mime)) return "AMR-WB";
        return getMime(format);
    }

    private String formatBitrate(long bitrate) {
        if (bitrate <= 0) return "";
        float mbps = bitrate / 1_000_000f;
        if (mbps < 1) return Math.round(bitrate / 1000f) + "Kbps";
        return bitrateFormat.format(mbps) + "Mbps";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "";
        float kb = bytes / 1024f;
        if (kb < 1024) return Math.round(kb) + "KB";
        return bitrateFormat.format(kb / 1024f) + "MB";
    }

    private String formatSpeed(long kbps) {
        return kbps < 1000 ? kbps + " KB/s" : SPEED_FORMAT.format(kbps / 1024f) + " MB/s";
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "";
        if (ms >= 60_000) return Util.timeMs(ms);
        return bitrateFormat.format(ms / 1000f) + " s";
    }

    private String getDisplayRefreshText() {
        if (root.getDisplay() == null || root.getDisplay().getRefreshRate() <= 0) return "";
        return refreshFormat.format(root.getDisplay().getRefreshRate()) + " Hz";
    }

    private String summarizeSource(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String type = sourceType(scheme, host, path, url);
            String ext = extension(path);
            return join(" ", type, TextUtils.isEmpty(host) ? emptyDash(scheme) : scheme + "://" + host, ext);
        } catch (Throwable ignored) {
            return shortText(url, 80);
        }
    }

    private String sourceType(String scheme, String host, String path, String url) {
        String lower = url.toLowerCase(Locale.US);
        if ("file".equals(scheme) || "content".equals(scheme)) return "local";
        if ("127.0.0.1".equals(host) || "localhost".equals(host)) return "local-proxy";
        if (lower.contains(".m3u8")) return "hls";
        if (lower.contains(".mpd")) return "dash";
        if (lower.startsWith("rtsp")) return "rtsp";
        if (lower.startsWith("rtp")) return "rtp";
        if (path != null && path.contains(".")) return "file";
        return TextUtils.isEmpty(scheme) ? "unknown" : scheme;
    }

    private String extension(String path) {
        if (TextUtils.isEmpty(path)) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot + 1 >= path.length()) return "";
        String ext = path.substring(dot + 1);
        return ext.length() > 8 ? "" : ext;
    }

    private String stateName(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "IDLE";
            case androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING";
            case androidx.media3.common.Player.STATE_READY -> "READY";
            case androidx.media3.common.Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private String stateText(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "空闲(IDLE)";
            case androidx.media3.common.Player.STATE_BUFFERING -> "缓冲中(BUFFERING)";
            case androidx.media3.common.Player.STATE_READY -> "就绪(READY)";
            case androidx.media3.common.Player.STATE_ENDED -> "结束(ENDED)";
            default -> stateName(state);
        };
    }

    private String join(String separator, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private String row(String label, String value) {
        return label + "  " + (TextUtils.isEmpty(value) ? "-" : value);
    }

    private String switchText(boolean enabled) {
        return enabled ? "开" : "关";
    }

    private String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private String shortText(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "...";
    }

    private void resetSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        lastTotalRxBytes = total == TrafficStats.UNSUPPORTED ? 0 : total / 1024;
        lastTimeStamp = System.currentTimeMillis();
        lastSpeedKBps = 0;
        lastSpeedText = "";
    }

    private record AudioTrackCandidate(Format format, int support, boolean selected) {
    }

    private record AudioTrackState(Format format, int support, boolean selected, int total, int supported) {

        static AudioTrackState empty() {
            return new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, 0, 0);
        }

        boolean hasTracks() {
            return total > 0;
        }

        boolean isHandled() {
            return support == C.FORMAT_HANDLED;
        }

        boolean isUnsupported() {
            return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
        }

        String supportSummary() {
            return total <= 1 ? "" : "音轨 " + supported + "/" + total + " 支持";
        }
    }
}
