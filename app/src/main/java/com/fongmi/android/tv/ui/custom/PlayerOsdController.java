package com.fongmi.android.tv.ui.custom;

import android.net.TrafficStats;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.Format;

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
        boolean showSize = PlayerSetting.isOsdSize();
        if ((!showTitle && !showSize) || diagnosticsVisible) {
            topLeft.setVisibility(View.GONE);
            return;
        }
        String title = showTitle ? source.getTitle() : "";
        String size = showSize && player != null ? player.getSizeText() : "";
        if (TextUtils.isEmpty(title)) topLeft.setText(size);
        else if (TextUtils.isEmpty(size)) topLeft.setText(title);
        else topLeft.setText(title + "\n" + size);
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
        String speed = getSpeed();
        bottomRight.setText(speed);
        bottomRight.setVisibility(TextUtils.isEmpty(speed) ? View.GONE : View.VISIBLE);
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

    private String getSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        if (total == TrafficStats.UNSUPPORTED) return "";
        long now = System.currentTimeMillis();
        long rxKb = total / 1024;
        long speed = (rxKb - lastTotalRxBytes) * 1000 / Math.max(now - lastTimeStamp, 1);
        lastTimeStamp = now;
        lastTotalRxBytes = rxKb;
        return speed < 1000 ? speed + " KB/s" : SPEED_FORMAT.format(speed / 1024f) + " MB/s";
    }

    private String getDiagnostics(PlayerManager player) {
        PlaybackAnalyticsListener.Snapshot snapshot = player.isIjk() ? PlaybackAnalyticsListener.Snapshot.empty() : PlaybackAnalyticsListener.getSnapshot();
        Format format = snapshot.format() != null ? snapshot.format() : player.getVideoFormat();
        String size = getSize(format, player);
        String fps = getFrameRate(format);
        String bitrate = getBitrate(format);
        String state = stateName(player.getPlaybackState());
        String buffered = player.getBufferedDuration() > 0 ? player.getBufferedDuration() + " ms" : "";
        String decoder = TextUtils.isEmpty(snapshot.decoderName()) ? "-" : snapshot.decoderName();
        String drop = String.valueOf(snapshot.droppedFrames());
        String render = PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "Surface" : "Texture";
        String tunnel = PlayerSetting.isTunnelingEnabled() ? "on" : "off";
        String enhance = PlayerSetting.isExoEnhanced() ? "on" : "off";
        String display = getDisplayRefreshText();
        return join("\n",
                row("Video / Decoder", decoder),
                row("Format / FPS", join(" ", size, TextUtils.isEmpty(fps) ? "" : "@", fps, bitrate)),
                row("State / Buffer", join(" / ", state, buffered)),
                row("Dropped Frames", drop),
                row("Render/Tunnel/EXO+", render + " / " + tunnel + " / " + enhance),
                row("Display Refresh", TextUtils.isEmpty(display) ? "-" : display));
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
        if (format == null || format.bitrate <= 0) return "";
        float mbps = format.bitrate / 1_000_000f;
        return bitrateFormat.format(mbps) + "Mbps";
    }

    private String getDisplayRefreshText() {
        if (root.getDisplay() == null || root.getDisplay().getRefreshRate() <= 0) return "";
        return refreshFormat.format(root.getDisplay().getRefreshRate()) + " Hz";
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
        return String.format(Locale.US, "%-17s %s", label, TextUtils.isEmpty(value) ? "-" : value);
    }

    private void resetSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        lastTotalRxBytes = total == TrafficStats.UNSUPPORTED ? 0 : total / 1024;
        lastTimeStamp = System.currentTimeMillis();
    }
}
