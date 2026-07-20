package com.fongmi.android.tv.setting;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static final int EXO = 0;
    public static final int IJK = 1;
    public static final int SYSTEM = 2;
    public static final int MPV = 3;
    public static final int NONE = -1;
    public static final int RENDER_SURFACE = 0;
    public static final int RENDER_TEXTURE = 1;
    public static final int FFMPEG_MODE_NEXTLIB = 0;
    public static final int FFMPEG_MODE_OFFICIAL = 1;
    public static final int FFMPEG_MODE_SIMPLE = 2;
    public static final int MPV_RENDER_OPENGL = 0;
    public static final int MPV_RENDER_VULKAN = 1;
    public static final int AUDIO_BACKGROUND_ARTWORK = 0;
    public static final int AUDIO_BACKGROUND_DARK_NEON = 1;
    public static final int AUDIO_BACKGROUND_BLACK_GOLD = 2;
    public static final int AUDIO_BACKGROUND_SUNSET = 3;
    public static final int AUDIO_BACKGROUND_MINT = 4;
    public static final int AUDIO_BACKGROUND_CANDY = 5;
    public static final int AUDIO_BACKGROUND_SKY = 6;
    public static final int AUDIO_BACKGROUND_ROSE = 7;
    public static final int AUDIO_BACKGROUND_CYBER = 8;
    public static final int AUDIO_BACKGROUND_FOREST = 9;
    public static final int AUDIO_BACKGROUND_LEMON = 10;
    public static final int AUDIO_BACKGROUND_DUSK = 11;
    public static final int AUDIO_BACKGROUND_RANDOM = 12;
    public static final int AUDIO_PLAYBACK_STYLE_BUILT_IN = 0;
    public static final int AUDIO_PLAYBACK_STYLE_IMMERSIVE = 1;
    public static final int PAD_LIVE_FULLSCREEN = 0;
    public static final int PAD_LIVE_STANDARD = 1;
    public static final int FALLBACK_FULL = 0;
    public static final int FALLBACK_DECODE_ONLY = 1;
    public static final int FALLBACK_PLAYER_ONLY = 2;
    public static final int FALLBACK_DISABLED = 3;
    public static final int NIGHT_MODE_OFF = 0;
    public static final int NIGHT_MODE_LOW = 1;
    public static final int NIGHT_MODE_MEDIUM = 2;
    public static final int NIGHT_MODE_HIGH = 3;
    public static final int NIGHT_MODE_AUTO = 0;
    public static final int NIGHT_MODE_ALWAYS_OFF = 1;
    public static final int NIGHT_MODE_ALWAYS_ON = 2;
    private static final int DEFAULT_PLAY_CACHE_OPTION = 0;
    private static final String KEY_FAILURE_FALLBACK = "player_failure_fallback";
    private static final String KEY_FFMPEG_MODE = "ffmpeg_mode";
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

    public static boolean isImmersiveAudioMode() {
        return Prefers.getBoolean("immersive_audio_mode", true);
    }

    public static void putImmersiveAudioMode(boolean enabled) {
        Prefers.put("immersive_audio_mode", enabled);
    }

    public static int getAudioPlaybackStyle() {
        return isImmersiveAudioMode() ? AUDIO_PLAYBACK_STYLE_IMMERSIVE : AUDIO_PLAYBACK_STYLE_BUILT_IN;
    }

    public static void putAudioPlaybackStyle(int style) {
        putImmersiveAudioMode(style != AUDIO_PLAYBACK_STYLE_BUILT_IN);
    }

    public static int getAudioBackground() {
        return Math.min(Math.max(Prefers.getInt("audio_background", AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_RANDOM);
    }

    public static void putAudioBackground(int background) {
        Prefers.put("audio_background", Math.min(Math.max(background, AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_RANDOM));
    }

    public static int getAudioBackgroundSeed() {
        return Prefers.getInt("audio_background_seed", 0x5A17B3);
    }

    public static void putAudioBackgroundSeed(int seed) {
        Prefers.put("audio_background_seed", seed);
    }

    public static int getAudioBackgroundDecorationSeed() {
        return Prefers.getInt("audio_background_decoration_seed", getAudioBackgroundSeed());
    }

    public static void putAudioBackgroundDecorationSeed(int seed) {
        Prefers.put("audio_background_decoration_seed", seed);
    }

    public static boolean isAudioBackgroundDecorated() {
        return Prefers.getBoolean("audio_background_decorated", true);
    }

    public static void putAudioBackgroundDecorated(boolean decorated) {
        Prefers.put("audio_background_decorated", decorated);
    }

    public static boolean isAudioBackgroundLightEffect() {
        return Prefers.getBoolean("audio_background_light_effect", false);
    }

    public static void putAudioBackgroundLightEffect(boolean enabled) {
        Prefers.put("audio_background_light_effect", enabled);
    }

    public static boolean isPlayer(int player) {
        return player == EXO || player == IJK || player == SYSTEM || player == MPV;
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

    public static int nextPlayer(int player) {
        return switch (sanitizePlayer(player)) {
            case EXO -> IJK;
            case IJK -> SYSTEM;
            case SYSTEM -> MPV;
            default -> EXO;
        };
    }

    public static int getRender() {
        return Math.min(Math.max(Prefers.getInt("render", RENDER_SURFACE), RENDER_SURFACE), RENDER_TEXTURE);
    }

    public static int getRender(int player) {
        return useNativeVideoOutput(player) ? 0 : getRender();
    }

    public static boolean useNativeVideoOutput(int player) {
        return player == IJK || player == SYSTEM || player == MPV;
    }

    public static void putRender(int render) {
        int value = Math.min(Math.max(render, RENDER_SURFACE), RENDER_TEXTURE);
        Prefers.put("render", value);
        if (isTunnel() && value == RENDER_TEXTURE) Prefers.put("tunnel", false);
        if (isExoEnhanced() && value == RENDER_TEXTURE) Prefers.put("exo_4k_compat", false);
    }

    public static int getMpvRender() {
        int render = Prefers.getInt("mpv_render", MPV_RENDER_OPENGL);
        return render == MPV_RENDER_VULKAN ? MPV_RENDER_VULKAN : MPV_RENDER_OPENGL;
    }

    public static void putMpvRender(int render) {
        Prefers.put("mpv_render", render == MPV_RENDER_VULKAN ? MPV_RENDER_VULKAN : MPV_RENDER_OPENGL);
    }

    public static int getPadLiveMode() {
        return Prefers.getInt("pad_live_mode", PAD_LIVE_FULLSCREEN) == PAD_LIVE_STANDARD ? PAD_LIVE_STANDARD : PAD_LIVE_FULLSCREEN;
    }

    public static void putPadLiveMode(int mode) {
        Prefers.put("pad_live_mode", mode == PAD_LIVE_STANDARD ? PAD_LIVE_STANDARD : PAD_LIVE_FULLSCREEN);
    }

    public static boolean isPadLiveFullscreen() {
        return getPadLiveMode() == PAD_LIVE_FULLSCREEN;
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
        return getBuffer(getPlayer());
    }

    public static int getBuffer(int kernel) {
        return KernelPerformanceSetting.getBuffer(sanitizePlayer(kernel));
    }

    public static void putBuffer(int buffer) {
        KernelPerformanceSetting.putBuffer(getPlayer(), buffer);
    }

    public static int getBufferBytesOption() {
        return KernelPerformanceSetting.getBufferBytesOption(getPlayer());
    }

    public static void putBufferBytesOption(int option) {
        KernelPerformanceSetting.putBufferBytesOption(getPlayer(), option);
    }

    public static int getBufferBytes() {
        return getBufferBytes(getPlayer());
    }

    public static int getBufferBytes(int kernel) {
        return switch (KernelPerformanceSetting.getBufferBytesOption(sanitizePlayer(kernel))) {
            case 1 -> 64 * 1024 * 1024;
            case 2 -> 128 * 1024 * 1024;
            case 3 -> 256 * 1024 * 1024;
            default -> 0;
        };
    }

    public static int getBackBufferOption() {
        return getBackBufferOption(getPlayer());
    }

    public static int getBackBufferOption(int kernel) {
        return KernelPerformanceSetting.getBackBufferOption(sanitizePlayer(kernel));
    }

    public static void putBackBufferOption(int option) {
        KernelPerformanceSetting.putBackBufferOption(getPlayer(), option);
    }

    public static int getBackBufferMs() {
        return getBackBufferMs(getPlayer());
    }

    public static int getBackBufferMs(int kernel) {
        return switch (KernelPerformanceSetting.getBackBufferOption(sanitizePlayer(kernel))) {
            case 1 -> 15_000;
            case 2 -> 30_000;
            case 3 -> 60_000;
            default -> 0;
        };
    }

    public static int getPlayCacheOption() {
        return KernelPerformanceSetting.getPlayCacheOption(getPlayer());
    }

    public static void putPlayCacheOption(int option) {
        KernelPerformanceSetting.putPlayCacheOption(getPlayer(), option);
    }

    public static long getPlayCacheSize() {
        return getPlayCacheSize(getPlayer());
    }

    public static long getPlayCacheSize(int kernel) {
        return switch (KernelPerformanceSetting.getPlayCacheOption(sanitizePlayer(kernel))) {
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

    public static int getFailureFallback() {
        int mode = Prefers.getInt(KEY_FAILURE_FALLBACK, FALLBACK_FULL);
        return mode >= FALLBACK_FULL && mode <= FALLBACK_DISABLED ? mode : FALLBACK_FULL;
    }

    public static void putFailureFallback(int mode) {
        Prefers.put(KEY_FAILURE_FALLBACK, mode >= FALLBACK_FULL && mode <= FALLBACK_DISABLED ? mode : FALLBACK_FULL);
    }

    public static boolean isAutoPlay() {
        return Prefers.getBoolean("player_auto_play", true);
    }

    public static void putAutoPlay(boolean autoPlay) {
        Prefers.put("player_auto_play", autoPlay);
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

    public static float getBrightness() {
        return Math.min(Math.max(Prefers.getFloat("player_brightness", -1), -1), 1);
    }

    public static void putBrightness(float brightness) {
        Prefers.put("player_brightness", Math.min(Math.max(brightness, 0), 1));
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
        return isAudioPrefer(getPlayer());
    }

    public static boolean isAudioPrefer(int kernel) {
        return KernelPerformanceSetting.isAudioPrefer(sanitizePlayer(kernel));
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        KernelPerformanceSetting.putAudioPrefer(getPlayer(), audioPrefer);
    }

    public static boolean isAudioPassThrough() {
        return isAudioPassThrough(getPlayer());
    }

    public static boolean isAudioPassThrough(int kernel) {
        return KernelPerformanceSetting.isAudioPassThrough(sanitizePlayer(kernel));
    }

    public static void putAudioPassThrough(boolean audioPassThrough) {
        putAudioPassThrough(getPlayer(), audioPassThrough);
    }

    public static void putAudioPassThrough(int kernel, boolean audioPassThrough) {
        KernelPerformanceSetting.putAudioPassThrough(sanitizePlayer(kernel), audioPassThrough);
    }

    public static boolean isVideoPrefer() {
        return isVideoPrefer(getPlayer());
    }

    public static boolean isVideoPrefer(int kernel) {
        return KernelPerformanceSetting.isVideoPrefer(sanitizePlayer(kernel));
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        KernelPerformanceSetting.putVideoPrefer(getPlayer(), videoPrefer);
    }

    public static int getFFmpegMode() {
        int defaultMode = getDefaultFFmpegMode();
        return sanitizeFFmpegMode(Prefers.getInt(KEY_FFMPEG_MODE, defaultMode), defaultMode);
    }

    public static void putFFmpegMode(int mode) {
        Prefers.put(KEY_FFMPEG_MODE, sanitizeFFmpegMode(mode, getDefaultFFmpegMode()));
    }

    static int getDefaultFFmpegMode() {
        return sanitizeFFmpegMode(App.get().getResources().getInteger(R.integer.default_ffmpeg_mode), FFMPEG_MODE_SIMPLE);
    }

    static int sanitizeFFmpegMode(int mode, int defaultMode) {
        if (isFFmpegMode(mode)) return mode;
        return isFFmpegMode(defaultMode) ? defaultMode : FFMPEG_MODE_SIMPLE;
    }

    private static boolean isFFmpegMode(int mode) {
        return mode == FFMPEG_MODE_NEXTLIB || mode == FFMPEG_MODE_OFFICIAL || mode == FFMPEG_MODE_SIMPLE;
    }

    public static boolean useNextLibFFmpeg() {
        return getFFmpegMode() == FFMPEG_MODE_NEXTLIB;
    }

    public static void putUseNextLibFFmpeg(boolean useNextLib) {
        putFFmpegMode(useNextLib ? FFMPEG_MODE_NEXTLIB : FFMPEG_MODE_OFFICIAL);
    }

    public static boolean isPreferAAC() {
        return isPreferAAC(getPlayer());
    }

    public static boolean isPreferAAC(int kernel) {
        return KernelPerformanceSetting.isPreferAac(sanitizePlayer(kernel));
    }

    public static void putPreferAAC(boolean preferAAC) {
        KernelPerformanceSetting.putPreferAac(getPlayer(), preferAAC);
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

    public static boolean isDesktopLyrics() {
        return isImmersiveAudioMode() && Prefers.getBoolean("desktop_lyrics");
    }

    public static void putDesktopLyrics(boolean value) {
        Prefers.put("desktop_lyrics", value);
    }

    public static int getDesktopLyricsX(int defaultValue) {
        return Prefers.getInt("desktop_lyrics_x", defaultValue);
    }

    public static int getDesktopLyricsY(int defaultValue) {
        return Prefers.getInt("desktop_lyrics_y", defaultValue);
    }

    public static void putDesktopLyricsPosition(int x, int y) {
        Prefers.put("desktop_lyrics_x", x);
        Prefers.put("desktop_lyrics_y", y);
    }

    public static void resetDesktopLyricsPosition() {
        Prefers.remove("desktop_lyrics_x");
        Prefers.remove("desktop_lyrics_y");
    }

    public static long getLyricsTimeOffsetMs() {
        return Math.min(Math.max(Prefers.getLong("lyrics_time_offset", 0L), -5000L), 5000L);
    }

    public static void putLyricsTimeOffsetMs(long value) {
        Prefers.put("lyrics_time_offset", Math.min(Math.max(value, -5000L), 5000L));
    }

    public static int getLyricsRows() {
        return Math.min(Math.max(Prefers.getInt("lyrics_rows", 5), 1), 5);
    }

    public static void putLyricsRows(int value) {
        Prefers.put("lyrics_rows", Math.min(Math.max(value, 1), 5));
    }

    public static int getLyricsTextSizeOption() {
        return Math.min(Math.max(Prefers.getInt("lyrics_text_size", 1), 0), 3);
    }

    public static void putLyricsTextSizeOption(int value) {
        Prefers.put("lyrics_text_size", Math.min(Math.max(value, 0), 3));
    }

    public static float getLyricsTextSizeScale() {
        return switch (getLyricsTextSizeOption()) {
            case 0 -> 0.85f;
            case 2 -> 1.15f;
            case 3 -> 1.3f;
            default -> 1f;
        };
    }

    public static boolean isKaraokeMode() {
        return isImmersiveAudioMode() && Prefers.getBoolean("karaoke_mode");
    }

    public static void putKaraokeMode(boolean value) {
        Prefers.put("karaoke_mode", value);
    }

    public static int getKaraokeDifficulty() {
        return Math.min(Math.max(Prefers.getInt("karaoke_difficulty", 0), 0), 2);
    }

    public static void putKaraokeDifficulty(int value) {
        Prefers.put("karaoke_difficulty", Math.min(Math.max(value, 0), 2));
    }

    public static double getKaraokeToleranceSemitones() {
        return switch (getKaraokeDifficulty()) {
            case 1 -> 1.5;
            case 2 -> 1.0;
            default -> 2.0;
        };
    }

    public static long getKaraokeMicDelayMs() {
        return Math.min(Math.max(Prefers.getLong("karaoke_mic_delay", 0L), -1000L), 1000L);
    }

    public static void putKaraokeMicDelayMs(long value) {
        Prefers.put("karaoke_mic_delay", Math.min(Math.max(value, -1000L), 1000L));
    }

    public static boolean isKaraokeBasicPitchTflite() {
        return Prefers.getBoolean("karaoke_basic_pitch_tflite");
    }

    public static void putKaraokeBasicPitchTflite(boolean value) {
        Prefers.put("karaoke_basic_pitch_tflite", value);
    }

    public static String getKaraokeGithubSources() {
        return Prefers.getString("karaoke_github_sources");
    }

    public static void putKaraokeGithubSources(String value) {
        Prefers.put("karaoke_github_sources", value == null ? "" : value.trim());
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

    public static int getNightModeLevel() {
        int level = Prefers.getInt("night_mode_level", NIGHT_MODE_OFF);
        return level >= NIGHT_MODE_OFF && level <= NIGHT_MODE_HIGH ? level : NIGHT_MODE_OFF;
    }

    public static void putNightModeLevel(int level) {
        Prefers.put("night_mode_level", Math.min(Math.max(level, NIGHT_MODE_OFF), NIGHT_MODE_HIGH));
    }

    public static int getNightModeDefault() {
        int mode = Prefers.getInt("night_mode_default", NIGHT_MODE_AUTO);
        return mode >= NIGHT_MODE_AUTO && mode <= NIGHT_MODE_ALWAYS_ON ? mode : NIGHT_MODE_AUTO;
    }

    public static void putNightModeDefault(int mode) {
        Prefers.put("night_mode_default", Math.min(Math.max(mode, NIGHT_MODE_AUTO), NIGHT_MODE_ALWAYS_ON));
    }
}
