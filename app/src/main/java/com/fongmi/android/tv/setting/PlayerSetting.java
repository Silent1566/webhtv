package com.fongmi.android.tv.setting;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static final int EXO = 0;
    public static final int IJK = 1;
    public static final int SYSTEM = 2;
    public static final int NONE = -1;
    public static final int RENDER_SURFACE = 0;
    public static final int RENDER_TEXTURE = 1;
    private static final int DEFAULT_PLAY_CACHE_OPTION = 0;
    private static final String KEY_DISPLAY_TIME = "display_time";
    private static final String KEY_DISPLAY_TRAFFIC = "display_traffic";
    private static final String KEY_DISPLAY_SIZE = "display_size";
    private static final String KEY_DISPLAY_PROGRESS = "display_progress";
    private static final String KEY_DISPLAY_MINI = "display_mini";
    private static final String KEY_DISPLAY_TITLE = "display_title";
    private static final String KEY_OSD_TITLE = "player_osd_title";
    private static final String KEY_OSD_RESOLUTION = "player_osd_resolution";
    private static final String KEY_OSD_TIME = "player_osd_time";
    private static final String KEY_OSD_PROGRESS = "player_osd_progress";
    private static final String KEY_OSD_TRAFFIC = "player_osd_traffic";
    private static final String KEY_OSD_MINI = "player_osd_mini";
    private static boolean legacyOsdMigrated;

    public static int getPlayer() {
        int player = Prefers.getInt("player", EXO);
        if (isPlayer(player)) return player;
        putPlayer(EXO);
        return EXO;
    }

    public static void putPlayer(int player) {
        Prefers.put("player", sanitizePlayer(player));
    }

    public static boolean isPlayer(int player) {
        return player == EXO || player == IJK || player == SYSTEM;
    }

    public static int sanitizePlayer(int player) {
        return isPlayer(player) ? player : EXO;
    }

    public static int resolvePlayer(int player) {
        return isPlayer(player) ? player : getPlayer();
    }

    public static boolean isPlayerAvailable(int player) {
        return player != IJK || isIjkAvailable();
    }

    public static boolean isIjkAvailable() {
        return true;
    }

    public static int getRender() {
        return Math.min(Math.max(Prefers.getInt("render", RENDER_SURFACE), RENDER_SURFACE), RENDER_TEXTURE);
    }

    public static int getRender(int player) {
        return useNativeVideoOutput(player) ? 0 : getRender();
    }

    public static boolean useNativeVideoOutput(int player) {
        return player == IJK || player == SYSTEM;
    }

    public static void putRender(int render) {
        int value = Math.min(Math.max(render, RENDER_SURFACE), RENDER_TEXTURE);
        Prefers.put("render", value);
        if (isTunnel() && value == RENDER_TEXTURE) Prefers.put("tunnel", false);
        if (isExoEnhanced() && value == RENDER_TEXTURE) Prefers.put("exo_4k_compat", false);
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getScale() {
        return Prefers.getInt("scale");
    }

    public static void putScale(int scale) {
        Prefers.put("scale", scale);
    }

    public static int getEpisodeColumn() {
        return Math.min(Math.max(Prefers.getInt("episode_column", 2), 1), 2);
    }

    public static void putEpisodeColumn(int column) {
        Prefers.put("episode_column", column == 1 ? 1 : 2);
    }

    public static int getBuffer() {
        return Math.min(Math.max(Prefers.getInt("buffer"), 1), 10);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("buffer", buffer);
    }

    public static int getBufferBytesOption() {
        return Math.min(Math.max(Prefers.getInt("buffer_bytes"), 0), 3);
    }

    public static void putBufferBytesOption(int option) {
        Prefers.put("buffer_bytes", Math.min(Math.max(option, 0), 3));
    }

    public static int getBufferBytes() {
        return switch (getBufferBytesOption()) {
            case 1 -> 64 * 1024 * 1024;
            case 2 -> 128 * 1024 * 1024;
            case 3 -> 256 * 1024 * 1024;
            default -> 0;
        };
    }

    public static int getBackBufferOption() {
        return Math.min(Math.max(Prefers.getInt("back_buffer"), 0), 3);
    }

    public static void putBackBufferOption(int option) {
        Prefers.put("back_buffer", Math.min(Math.max(option, 0), 3));
    }

    public static int getBackBufferMs() {
        return switch (getBackBufferOption()) {
            case 1 -> 15_000;
            case 2 -> 30_000;
            case 3 -> 60_000;
            default -> 0;
        };
    }

    public static int getPlayCacheOption() {
        return Math.min(Math.max(Prefers.getInt("play_cache", DEFAULT_PLAY_CACHE_OPTION), 0), 4);
    }

    public static void putPlayCacheOption(int option) {
        Prefers.put("play_cache", Math.min(Math.max(option, 0), 4));
    }

    public static long getPlayCacheSize() {
        return switch (getPlayCacheOption()) {
            case 1 -> 256L * 1024 * 1024;
            case 2 -> 512L * 1024 * 1024;
            case 3 -> 1024L * 1024 * 1024;
            case 4 -> 2L * 1024 * 1024 * 1024;
            default -> 128L * 1024 * 1024;
        };
    }

    public static boolean isAutoChange() {
        return Prefers.getBoolean("player_auto_change", true);
    }

    public static void putAutoChange(boolean autoChange) {
        Prefers.put("player_auto_change", autoChange);
    }

    public static int getBackground() {
        return Prefers.getInt("background", 2);
    }

    public static void putBackground(int background) {
        Prefers.put("background", background);
    }

    public static boolean isMusicNotification() {
        return Prefers.getBoolean("audio_music_notification", true);
    }

    public static void putMusicNotification(boolean notification) {
        Prefers.put("audio_music_notification", notification);
    }

    public static boolean isAudioBookNotification() {
        return Prefers.getBoolean("audio_book_notification", true);
    }

    public static void putAudioBookNotification(boolean notification) {
        Prefers.put("audio_book_notification", notification);
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static float getSpeed() {
        return Math.min(Math.max(Prefers.getFloat("speed", 3), 2), 5);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", speed);
    }

    public static float getDefaultSpeed() {
        return Math.min(Math.max(Prefers.getFloat("play_speed", 1), 0.5f), 5);
    }

    public static void putDefaultSpeed(float speed) {
        Prefers.put("play_speed", Math.min(Math.max(speed, 0.5f), 5));
    }

    public static boolean isDisplayTime() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TIME);
    }

    public static void putDisplayTime(boolean displayTime) {
        Prefers.put(KEY_DISPLAY_TIME, displayTime);
    }

    public static boolean isDisplayTraffic() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TRAFFIC);
    }

    public static void putDisplayTraffic(boolean displayTraffic) {
        Prefers.put(KEY_DISPLAY_TRAFFIC, displayTraffic);
    }

    public static boolean isDisplaySize() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_SIZE, true);
    }

    public static void putDisplaySize(boolean displaySize) {
        Prefers.put(KEY_DISPLAY_SIZE, displaySize);
    }

    public static boolean isDisplayProgress() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_PROGRESS, true);
    }

    public static void putDisplayProgress(boolean displayProgress) {
        Prefers.put(KEY_DISPLAY_PROGRESS, displayProgress);
    }

    public static boolean isDisplayMini() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_MINI);
    }

    public static void putDisplayMini(boolean displayMini) {
        Prefers.put(KEY_DISPLAY_MINI, displayMini);
    }

    public static boolean isDisplayTitle() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TITLE, true);
    }

    public static void putDisplayTitle(boolean displayTitle) {
        Prefers.put(KEY_DISPLAY_TITLE, displayTitle);
    }

    public static boolean[] getDisplayChecked() {
        return new boolean[]{isDisplayTime(), isDisplayTraffic(), isDisplaySize(), isDisplayProgress(), isDisplayMini(), isDisplayTitle(), isOsdDiagnostics()};
    }

    public static void putDisplayChecked(boolean[] checked) {
        putDisplayTime(valueAt(checked, 0, isDisplayTime()));
        putDisplayTraffic(valueAt(checked, 1, isDisplayTraffic()));
        putDisplaySize(valueAt(checked, 2, isDisplaySize()));
        putDisplayProgress(valueAt(checked, 3, isDisplayProgress()));
        putDisplayMini(valueAt(checked, 4, isDisplayMini()));
        putDisplayTitle(valueAt(checked, 5, isDisplayTitle()));
        putOsdDiagnostics(valueAt(checked, 6, isOsdDiagnostics()));
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
        if (tunnel) Prefers.put("render", RENDER_SURFACE);
    }

    public static boolean isTunnelingEnabled() {
        return isTunnel() && getRender() == RENDER_SURFACE;
    }

    public static boolean isExo4KCompat() {
        return isExoEnhanced();
    }

    public static boolean isExoEnhanced() {
        return Prefers.getBoolean("exo_4k_compat");
    }

    public static void putExo4KCompat(boolean value) {
        putExoEnhanced(value);
    }

    public static void putExoEnhanced(boolean value) {
        Prefers.put("exo_4k_compat", value);
        if (value) Prefers.put("render", RENDER_SURFACE);
    }

    public static boolean isAudioPrefer() {
        return Prefers.getBoolean("audio_prefer");
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        Prefers.put("audio_prefer", audioPrefer);
    }

    public static boolean isAudioPassThrough() {
        return Prefers.getBoolean("audio_pass_through", true);
    }

    public static void putAudioPassThrough(boolean audioPassThrough) {
        Prefers.put("audio_pass_through", audioPassThrough);
    }

    public static boolean isVideoPrefer() {
        return Prefers.getBoolean("video_prefer");
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        Prefers.put("video_prefer", videoPrefer);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("prefer_aac");
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("prefer_aac", preferAAC);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    public static boolean isOsdTitle() {
        return isDisplayTitle();
    }

    public static void putOsdTitle(boolean value) {
        putDisplayTitle(value);
    }

    public static boolean isOsdResolution() {
        return isDisplaySize();
    }

    public static void putOsdResolution(boolean value) {
        putDisplaySize(value);
    }

    public static boolean isOsdTime() {
        return isDisplayTime();
    }

    public static void putOsdTime(boolean value) {
        putDisplayTime(value);
    }

    public static boolean isOsdProgress() {
        return isDisplayProgress();
    }

    public static void putOsdProgress(boolean value) {
        putDisplayProgress(value);
    }

    public static boolean isOsdTraffic() {
        return isDisplayTraffic();
    }

    public static void putOsdTraffic(boolean value) {
        putDisplayTraffic(value);
    }

    public static boolean isOsdMini() {
        return isDisplayMini();
    }

    public static void putOsdMini(boolean value) {
        putDisplayMini(value);
    }

    public static boolean isOsdSize() {
        return isDisplaySize();
    }

    public static void putOsdSize(boolean value) {
        putDisplaySize(value);
    }

    public static boolean isOsdDiagnostics() {
        return Prefers.getBoolean("player_osd_diagnostics");
    }

    public static void putOsdDiagnostics(boolean value) {
        Prefers.put("player_osd_diagnostics", value);
    }

    public static boolean isOsdEnabled() {
        return isOsdTitle() || isOsdTime() || isOsdSize() || isOsdProgress() || isOsdTraffic() || isOsdMini() || isOsdDiagnostics();
    }

    private static boolean valueAt(boolean[] checked, int index, boolean fallback) {
        return checked != null && checked.length > index ? checked[index] : fallback;
    }

    private static void migrateLegacyOsd() {
        if (legacyOsdMigrated) return;
        legacyOsdMigrated = true;
        if (hasAny(KEY_DISPLAY_TIME, KEY_DISPLAY_TRAFFIC, KEY_DISPLAY_SIZE, KEY_DISPLAY_PROGRESS, KEY_DISPLAY_MINI, KEY_DISPLAY_TITLE)) return;
        if (!hasAny(KEY_OSD_TITLE, KEY_OSD_RESOLUTION, KEY_OSD_TIME, KEY_OSD_PROGRESS, KEY_OSD_TRAFFIC, KEY_OSD_MINI)) return;
        putDisplayTime(Prefers.getBoolean(KEY_OSD_TIME));
        putDisplayTraffic(Prefers.getBoolean(KEY_OSD_TRAFFIC));
        putDisplaySize(Prefers.getPrefers().contains(KEY_OSD_RESOLUTION) ? Prefers.getBoolean(KEY_OSD_RESOLUTION) : Prefers.getBoolean(KEY_OSD_TITLE));
        putDisplayProgress(Prefers.getBoolean(KEY_OSD_PROGRESS));
        putDisplayMini(Prefers.getBoolean(KEY_OSD_MINI));
        putDisplayTitle(Prefers.getBoolean(KEY_OSD_TITLE));
    }

    private static boolean hasAny(String... keys) {
        for (String key : keys) if (Prefers.getPrefers().contains(key)) return true;
        return false;
    }
}
