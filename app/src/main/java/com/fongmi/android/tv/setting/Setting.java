package com.fongmi.android.tv.setting;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

public class Setting {

    public static final int TMDB_MODEL_NATIVE = 0;
    public static final int DETAIL_OPEN_FUSION = 0;
    public static final int DETAIL_OPEN_ENHANCED = 1;
    public static final int DETAIL_OPEN_DIRECT = 2;
    public static final int DETAIL_OPEN_CINEMA = 3;
    public static final int DETAIL_OPEN_PLAYER = 4;
    public static final int DETAIL_OPEN_ORIGINAL_ENHANCED = 5;
    public static final int DETAIL_STYLE_PROFILE = 0;
    public static final int DETAIL_STYLE_CINEMA = 1;
    public static final int DETAIL_STYLE_NATIVE = 2;
    public static final int TMDB_MATCH_STRICT = 0;
    public static final int TMDB_MATCH_SMART = 1;
    public static final int TMDB_MATCH_STRICT_DIALOG = 2;
    public static final int TMDB_MATCH_SMART_DIALOG = 3;
    public static final int DETAIL_INTERACTION_SYSTEM = 0;
    public static final int DETAIL_INTERACTION_ORIGINAL = 1;
    public static final int DETAIL_THEME_CURRENT = DETAIL_STYLE_NATIVE;
    public static final int UI_SCALE_FOLLOW_SYSTEM = 0;
    public static final int UI_SCALE_STANDARD = 1;
    public static final int UI_SCALE_COMPACT = 2;
    public static final int UI_SCALE_SMALLER = 3;
    public static final int UI_SCALE_MILD_COMPACT = 4;
    public static final int UI_SCALE_MORE_COMPACT = 5;
    private static final int[] UI_SCALE_OPTIONS = {UI_SCALE_FOLLOW_SYSTEM, UI_SCALE_STANDARD, UI_SCALE_MILD_COMPACT, UI_SCALE_COMPACT, UI_SCALE_MORE_COMPACT, UI_SCALE_SMALLER};

    public static final int WALL_CINEMA = 5;
    public static final int WALL_CINEMA_WARM = 6;
    public static final int WALL_CINEMA_MOSS = 7;
    public static final int WALL_CINEMA_BLUE = 8;
    public static final int WALL_CINEMA_CLAY = 9;
    public static final int WALL_AURORA_GLASS = 10;
    public static final int WALL_SUNSET_PRISM = 11;
    public static final int WALL_MINT_GLACIER = 12;
    public static final int WALL_LIQUID_CHROME = 13;
    public static final int WALL_NEON_BERRY = 14;
    public static final int WALL_CHAMPAGNE_MIST = 15;
    public static final int WALL_GLASS_GRADIENT = 16;
    public static final int WALL_DEEP_SPACE_GLASS = 17;
    public static final int WALL_POLAR_LIGHT_GLASS = 18;
    public static final int WALL_NEON_CYBER = 19;
    public static final int WALL_WARM_MOON_GLASS = 20;
    public static final int WALL_CRYSTAL_SKY = 21;
    public static final int WALL_DREAM_PURPLE = 22;
    public static final int WALL_SKY_MINT = 23;
    public static final int WALL_FOREST_MIST = 24;
    public static final int WALL_DAYLIGHT_MINIMAL = 25;
    public static final int WALL_DEEP_SEA = 26;
    public static final int WALL_VIOLET_SMOKE = 27;
    public static final int WALL_ROSE_VEIL = 28;
    public static final int WALL_EMERALD_AURORA = 29;
    public static final int WALL_BLUE_SILK = 30;
    public static final int WALL_PEACH_DAWN = 31;
    public static final int WALL_GRAPHITE_SMOKE = 32;
    public static final int WALL_PASTEL_PRISM = 33;
    public static final int WALL_MIDNIGHT_MOON = 34;
    public static final int WALL_CYAN_CRYSTAL = 35;
    public static final int WALL_LAVENDER_CRYSTAL = 36;
    public static final int WALL_GREEN = 1;

    private static final int[] DEFAULT_WALLS = {
            WALL_DREAM_PURPLE, WALL_LAVENDER_CRYSTAL, WALL_PASTEL_PRISM, WALL_ROSE_VEIL, WALL_VIOLET_SMOKE,
            WALL_NEON_BERRY, WALL_MIDNIGHT_MOON, WALL_NEON_CYBER, WALL_DEEP_SPACE_GLASS, WALL_GRAPHITE_SMOKE,
            WALL_DAYLIGHT_MINIMAL, WALL_SKY_MINT, WALL_POLAR_LIGHT_GLASS, WALL_GLASS_GRADIENT, WALL_CRYSTAL_SKY,
            WALL_BLUE_SILK, WALL_CYAN_CRYSTAL, WALL_MINT_GLACIER, WALL_AURORA_GLASS, WALL_DEEP_SEA,
            WALL_LIQUID_CHROME, WALL_FOREST_MIST, WALL_EMERALD_AURORA, WALL_WARM_MOON_GLASS, WALL_PEACH_DAWN,
            WALL_CHAMPAGNE_MIST, WALL_SUNSET_PRISM
    };

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getHotTv() {
        return Prefers.getString("hot_tv");
    }

    public static void putHotTv(String hot) {
        Prefers.put("hot_tv", hot);
    }

    public static String getHotMovie() {
        return Prefers.getString("hot_movie");
    }

    public static void putHotMovie(String hot) {
        Prefers.put("hot_movie", hot);
    }

    public static String getHotVariety() {
        return Prefers.getString("hot_variety");
    }

    public static void putHotVariety(String hot) {
        Prefers.put("hot_variety", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        int wall = Prefers.getInt("wall", WALL_DREAM_PURPLE);
        return wall == WALL_GREEN || isLegacyColorWall(wall) ? WALL_DREAM_PURPLE : wall;
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static int nextDefaultWall() {
        int wall = getWall();
        for (int i = 0; i < DEFAULT_WALLS.length; i++) {
            if (DEFAULT_WALLS[i] == wall) return DEFAULT_WALLS[(i + 1) % DEFAULT_WALLS.length];
        }
        return WALL_DREAM_PURPLE;
    }

    public static int[] getDefaultWalls() {
        return DEFAULT_WALLS.clone();
    }

    public static int getDefaultWallIndex(int wall) {
        for (int i = 0; i < DEFAULT_WALLS.length; i++) {
            if (DEFAULT_WALLS[i] == wall) return i;
        }
        return -1;
    }

    public static boolean isBuiltInWall(int wall) {
        return isBuiltInDesignWall(wall);
    }

    public static boolean isBuiltInColorWall(int wall) {
        return false;
    }

    private static boolean isLegacyColorWall(int wall) {
        return wall == WALL_CINEMA || wall == WALL_CINEMA_WARM || wall == WALL_CINEMA_MOSS || wall == WALL_CINEMA_BLUE || wall == WALL_CINEMA_CLAY;
    }

    public static boolean isBuiltInDesignWall(int wall) {
        return getDefaultWallIndex(wall) != -1;
    }

    public static int getBuiltInWallColor(int wall) {
        if (wall == WALL_AURORA_GLASS) return 0xFF2B8ECB;
        if (wall == WALL_SUNSET_PRISM) return 0xFFB65B88;
        if (wall == WALL_MINT_GLACIER) return 0xFF55BCA8;
        if (wall == WALL_LIQUID_CHROME) return 0xFF53657F;
        if (wall == WALL_NEON_BERRY) return 0xFF7B42CF;
        if (wall == WALL_CHAMPAGNE_MIST) return 0xFFB47692;
        if (wall == WALL_GLASS_GRADIENT) return 0xFF5E91B3;
        if (wall == WALL_DEEP_SPACE_GLASS) return 0xFF2E2B74;
        if (wall == WALL_POLAR_LIGHT_GLASS) return 0xFF6FA6B8;
        if (wall == WALL_NEON_CYBER) return 0xFF4B2BD8;
        if (wall == WALL_WARM_MOON_GLASS) return 0xFF9E7568;
        if (wall == WALL_CRYSTAL_SKY) return 0xFF7890C5;
        if (wall == WALL_DREAM_PURPLE) return 0xFF7560CA;
        if (wall == WALL_SKY_MINT) return 0xFF6DA6B1;
        if (wall == WALL_FOREST_MIST) return 0xFF4E8750;
        if (wall == WALL_DAYLIGHT_MINIMAL) return 0xFF7B8D9C;
        if (wall == WALL_DEEP_SEA) return 0xFF2F7290;
        if (wall == WALL_VIOLET_SMOKE) return 0xFF7C4BE2;
        if (wall == WALL_ROSE_VEIL) return 0xFFB27FAE;
        if (wall == WALL_EMERALD_AURORA) return 0xFF27B07D;
        if (wall == WALL_BLUE_SILK) return 0xFF5E9BB3;
        if (wall == WALL_PEACH_DAWN) return 0xFFC27863;
        if (wall == WALL_GRAPHITE_SMOKE) return 0xFF4B5360;
        if (wall == WALL_PASTEL_PRISM) return 0xFF8A84C8;
        if (wall == WALL_MIDNIGHT_MOON) return 0xFF4935B4;
        if (wall == WALL_CYAN_CRYSTAL) return 0xFF168BA6;
        if (wall == WALL_LAVENDER_CRYSTAL) return 0xFF8875D0;
        return 0xFF2B8ECB;
    }

    public static String getBuiltInWallName(int wall) {
        if (wall == WALL_AURORA_GLASS) return "蓝紫流光";
        if (wall == WALL_SUNSET_PRISM) return "珊瑚暮色";
        if (wall == WALL_MINT_GLACIER) return "薄荷星云";
        if (wall == WALL_LIQUID_CHROME) return "银色潮汐";
        if (wall == WALL_NEON_BERRY) return "莓果极光";
        if (wall == WALL_CHAMPAGNE_MIST) return "香槟晨雾";
        if (wall == WALL_GLASS_GRADIENT) return "玻璃渐变风";
        if (wall == WALL_DEEP_SPACE_GLASS) return "深空玻璃风";
        if (wall == WALL_POLAR_LIGHT_GLASS) return "极光轻玻璃风";
        if (wall == WALL_NEON_CYBER) return "暗夜霓虹";
        if (wall == WALL_WARM_MOON_GLASS) return "暖月玻璃风";
        if (wall == WALL_CRYSTAL_SKY) return "冰晶幻彩风";
        if (wall == WALL_DREAM_PURPLE) return "梦幻紫霞";
        if (wall == WALL_SKY_MINT) return "雾青薄荷";
        if (wall == WALL_FOREST_MIST) return "森林雾绿";
        if (wall == WALL_DAYLIGHT_MINIMAL) return "雾蓝极简";
        if (wall == WALL_DEEP_SEA) return "深海月影";
        if (wall == WALL_VIOLET_SMOKE) return "紫雾星旋";
        if (wall == WALL_ROSE_VEIL) return "玫瑰薄雾";
        if (wall == WALL_EMERALD_AURORA) return "翡翠极光";
        if (wall == WALL_BLUE_SILK) return "蓝绸流影";
        if (wall == WALL_PEACH_DAWN) return "暖桃晨光";
        if (wall == WALL_GRAPHITE_SMOKE) return "石墨烟岚";
        if (wall == WALL_PASTEL_PRISM) return "彩虹幻璃";
        if (wall == WALL_MIDNIGHT_MOON) return "午夜月影";
        if (wall == WALL_CYAN_CRYSTAL) return "水晶青蓝";
        if (wall == WALL_LAVENDER_CRYSTAL) return "薰衣水晶";
        return "梦幻紫霞";
    }

    public static String getWallDesc(String desc) {
        return getWallType() == 0 && isBuiltInWall(getWall()) ? getBuiltInWallName(getWall()) : desc;
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static String getSyncPaths() {
        return Prefers.getString("sync_paths", "TV\nTVBox\nTVData");
    }

    public static void putSyncPaths(String paths) {
        Prefers.put("sync_paths", paths);
    }

    public static String getLoginStatePaths() {
        return Prefers.getString("login_state_paths");
    }

    public static void putLoginStatePaths(String paths) {
        Prefers.put("login_state_paths", paths);
    }

    public static String getLoginStatePendingPaths() {
        return Prefers.getString("login_state_pending_paths");
    }

    public static void putLoginStatePendingPaths(String paths) {
        Prefers.put("login_state_pending_paths", paths);
    }

    public static String getLoginStateSnapshot() {
        return Prefers.getString("login_state_snapshot");
    }

    public static void putLoginStateSnapshot(String snapshot) {
        Prefers.put("login_state_snapshot", snapshot);
    }

    public static String getLoginStateFindings() {
        return Prefers.getString("login_state_findings");
    }

    public static void putLoginStateFindings(String findings) {
        Prefers.put("login_state_findings", findings);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static int getUiScale() {
        int scale = Prefers.getInt("ui_scale", UI_SCALE_FOLLOW_SYSTEM);
        return isUiScale(scale) ? scale : UI_SCALE_FOLLOW_SYSTEM;
    }

    public static void putUiScale(int scale) {
        Prefers.put("ui_scale", isUiScale(scale) ? scale : UI_SCALE_FOLLOW_SYSTEM);
    }

    public static int getUiScaleIndex() {
        int scale = getUiScale();
        for (int i = 0; i < UI_SCALE_OPTIONS.length; i++) if (UI_SCALE_OPTIONS[i] == scale) return i;
        return UI_SCALE_FOLLOW_SYSTEM;
    }

    public static void putUiScaleIndex(int index) {
        putUiScale(index >= 0 && index < UI_SCALE_OPTIONS.length ? UI_SCALE_OPTIONS[index] : UI_SCALE_FOLLOW_SYSTEM);
    }

    private static boolean isUiScale(int scale) {
        for (int option : UI_SCALE_OPTIONS) if (option == scale) return true;
        return false;
    }

    public static Context wrapUiScale(Context context) {
        int scale = getUiScale();
        if (scale == UI_SCALE_FOLLOW_SYSTEM) return context;
        float factor = getUiScaleFactor(scale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE > 0 ? DisplayMetrics.DENSITY_DEVICE_STABLE : metrics.densityDpi;
        int densityDpi = Math.max(DisplayMetrics.DENSITY_LOW, Math.round(stableDensity * factor));
        config.densityDpi = densityDpi;
        config.fontScale = 1.0f;
        config.screenWidthDp = pxToDp(metrics.widthPixels, densityDpi);
        config.screenHeightDp = pxToDp(metrics.heightPixels, densityDpi);
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        return context.createConfigurationContext(config);
    }

    private static float getUiScaleFactor(int scale) {
        return switch (scale) {
            case UI_SCALE_STANDARD -> 0.9f;
            case UI_SCALE_MILD_COMPACT -> 0.85f;
            case UI_SCALE_COMPACT -> 0.8f;
            case UI_SCALE_MORE_COMPACT -> 0.75f;
            case UI_SCALE_SMALLER -> 0.7f;
            default -> 1.0f;
        };
    }

    private static int pxToDp(int px, int densityDpi) {
        return Math.max(1, Math.round(px * (float) DisplayMetrics.DENSITY_DEFAULT / densityDpi));
    }

    public static boolean isDriveCheck() {
        return Prefers.getBoolean("drive_check", true);
    }

    public static void putDriveCheck(boolean driveCheck) {
        Prefers.put("drive_check", driveCheck);
    }

    public static int getSiteColumn() {
        return clampSiteColumn(Prefers.getInt("site_column", 1));
    }

    public static void putSiteColumn(int column) {
        Prefers.put("site_column", clampSiteColumn(column));
    }

    private static int clampSiteColumn(int column) {
        return column == 2 ? 2 : 1;
    }

    public static boolean isCompactEpisodeTitle() {
        return Prefers.getBoolean("compact_episode_title");
    }

    public static void putCompactEpisodeTitle(boolean compact) {
        Prefers.put("compact_episode_title", compact);
    }

    public static boolean isSiteHealthSort() {
        return Prefers.getBoolean("site_health_sort", true);
    }

    public static void putSiteHealthSort(boolean sort) {
        Prefers.put("site_health_sort", sort);
    }

    public static boolean isSiteHealthDialogSort() {
        return Prefers.getBoolean("site_health_dialog_sort");
    }

    public static void putSiteHealthDialogSort(boolean sort) {
        Prefers.put("site_health_dialog_sort", sort);
    }

    public static boolean isWebHomeExtension() {
        return Prefers.getBoolean("web_home_extension", true);
    }

    public static void putWebHomeExtension(boolean extension) {
        Prefers.put("web_home_extension", extension);
    }

    public static boolean isWebHomeFullscreen() {
        return Prefers.getBoolean("web_home_fullscreen", true);
    }

    public static void putWebHomeFullscreen(boolean fullscreen) {
        Prefers.put("web_home_fullscreen", fullscreen);
    }

    public static boolean isPlaybackArtworkWall() {
        return Prefers.getBoolean("playback_artwork_wall", true);
    }

    public static void putPlaybackArtworkWall(boolean artworkWall) {
        Prefers.put("playback_artwork_wall", artworkWall);
    }

    public static boolean isCspWarmup() {
        return Prefers.getBoolean("csp_warmup");
    }

    public static void putCspWarmup(boolean warmup) {
        Prefers.put("csp_warmup", warmup);
    }

    public static boolean isDebugLog() {
        return DebugLogStore.isEnabled();
    }

    public static void putDebugLog(boolean debugLog) {
        DebugLogStore.setEnabled(debugLog);
        if (debugLog) logDebugEnvironment("enable");
    }

    public static void logDebugEnvironment(String reason) {
        boolean hardwareAccelerated = (App.get().getApplicationInfo().flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0;
        SpiderDebug.log("env", "reason=%s app=%s(%s) mode=%s abi=%s debug=%s hardware=%s android=%s sdk=%s incremental=%s manufacturer=%s brand=%s model=%s device=%s product=%s supportedAbis=%s",
                reason,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR_mode,
                BuildConfig.FLAVOR_abi,
                BuildConfig.DEBUG,
                hardwareAccelerated,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.VERSION.INCREMENTAL,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.DEVICE,
                Build.PRODUCT,
                String.join(",", Build.SUPPORTED_ABIS));
        WebViewUtil.logProvider("debug-env");
    }

    public static boolean isShellProxy() {
        return Prefers.getBoolean("shell_proxy");
    }

    public static void putShellProxy(boolean shellProxy) {
        Prefers.put("shell_proxy", shellProxy);
        ProxySetting.apply();
    }

    public static String getShellProxyRules() {
        return Prefers.getString("shell_proxy_rules");
    }

    public static void putShellProxyRules(String rules) {
        Prefers.put("shell_proxy_rules", rules);
        ProxySetting.apply();
    }

    public static void putShellProxyConfig(String url, String rules) {
        Prefers.put("shell_proxy_url", url);
        Prefers.put("shell_proxy_rules", rules);
        Prefers.put("shell_proxy_hosts", "*");
        ProxySetting.apply();
    }

    public static String getShellProxyUrl() {
        return Prefers.getString("shell_proxy_url");
    }

    public static void putShellProxyUrl(String url) {
        Prefers.put("shell_proxy_url", url);
        ProxySetting.apply();
    }

    public static String getShellProxyHosts() {
        return Prefers.getString("shell_proxy_hosts", "*");
    }

    public static void putShellProxyHosts(String hosts) {
        Prefers.put("shell_proxy_hosts", hosts);
        ProxySetting.apply();
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static String getUpdateChannel() {
        String channel = Prefers.getString("update_channel", Update.CHANNEL_STABLE);
        return Update.CHANNEL_BETA.equals(channel) ? Update.CHANNEL_BETA : Update.CHANNEL_STABLE;
    }

    public static void putUpdateChannel(String channel) {
        Prefers.put("update_channel", Update.CHANNEL_BETA.equals(channel) ? Update.CHANNEL_BETA : Update.CHANNEL_STABLE);
    }

    public static String getGithubProxy() {
        return Prefers.getString("github_proxy", com.fongmi.android.tv.utils.GithubProxy.defaultSources());
    }

    public static void putGithubProxy(String value) {
        Prefers.put("github_proxy", com.fongmi.android.tv.utils.GithubProxy.normalizeConfig(value));
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(App.get(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(App.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFileManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        return new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + App.get().getPackageName())).resolveActivity(App.get().getPackageManager()) != null || new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static String getAudioConfig() {
        return Prefers.getString("audio_config");
    }

    public static void putAudioConfig(String value) {
        Prefers.put("audio_config", value);
    }

    public static boolean isAudioSiteEnabled(String key, String name) {
        return AudioConfig.objectFrom(getAudioConfig()).isSiteEnabled(key, name);
    }

    public static String getShortDramaConfig() {
        return Prefers.getString("short_drama_config");
    }

    public static void putShortDramaConfig(String value) {
        Prefers.put("short_drama_config", value);
    }

    public static boolean isShortDramaSiteEnabled(String key, String name) {
        return ShortDramaConfig.objectFrom(getShortDramaConfig()).isSiteEnabled(key, name);
    }

    public static String getTmdbConfig() {
        return Prefers.getString("tmdb_config");
    }

    public static void putTmdbConfig(String value) {
        Prefers.put("tmdb_config", value);
    }

    public static boolean isTmdbReady() {
        return com.fongmi.android.tv.bean.TmdbConfig.objectFrom(getTmdbConfig()).isReady();
    }

    public static boolean isTmdbSiteEnabled(String key, String name) {
        return com.fongmi.android.tv.bean.TmdbConfig.objectFrom(getTmdbConfig()).isSiteEnabled(key, name);
    }

    public static String getAiConfig() {
        return Prefers.getString("ai_config");
    }

    public static void putAiConfig(String value) {
        Prefers.put("ai_config", value);
    }

    public static boolean isAiConfigReady() {
        return AiConfig.objectFrom(getAiConfig()).isReady();
    }

    public static TmdbMatchCache getTmdbMatchCache() {
        return TmdbMatchCache.objectFrom(Prefers.getString("tmdb_match_cache"));
    }

    public static void putTmdbMatchCache(TmdbMatchCache cache) {
        Prefers.put("tmdb_match_cache", App.gson().toJson(cache));
    }

    public static boolean isTmdbEnabled() {
        if (!Prefers.getPrefers().contains("tmdb_enabled")) {
            if (Prefers.getPrefers().contains("detail_open_mode")) return isTmdbMode(clampDetailOpenMode(Prefers.getInt("detail_open_mode", DETAIL_OPEN_ENHANCED)));
            if (Prefers.getPrefers().contains("search_detail_page")) return Prefers.getBoolean("search_detail_page", true);
        }
        return Prefers.getBoolean("tmdb_enabled", false);
    }

    public static void putTmdbEnabled(boolean enabled) {
        Prefers.put("tmdb_enabled", enabled);
    }

    public static int getTmdbModel() {
        return clampTmdbModel(Prefers.getInt("tmdb_model", TMDB_MODEL_NATIVE));
    }

    public static void putTmdbModel(int model) {
        Prefers.put("tmdb_model", clampTmdbModel(model));
    }

    private static int clampTmdbModel(int model) {
        return model == TMDB_MODEL_NATIVE ? TMDB_MODEL_NATIVE : TMDB_MODEL_NATIVE;
    }

    public static int getDetailInteractionMode() {
        if (Prefers.getPrefers().contains("detail_open_mode")) {
            return isTmdbMode(getDetailOpenMode()) ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
        }
        if (!Prefers.getPrefers().contains("detail_interaction_mode")) {
            return isTmdbEnabled() ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
        }
        int mode = clampDetailInteractionMode(Prefers.getInt("detail_interaction_mode", DETAIL_INTERACTION_ORIGINAL));
        return mode == DETAIL_INTERACTION_SYSTEM && !isTmdbEnabled() ? DETAIL_INTERACTION_ORIGINAL : mode;
    }

    public static void putDetailInteractionMode(int mode) {
        int value = clampDetailInteractionMode(mode);
        putDetailOpenMode(value == DETAIL_INTERACTION_ORIGINAL ? DETAIL_OPEN_DIRECT : DETAIL_OPEN_ORIGINAL_ENHANCED);
    }

    private static int clampDetailInteractionMode(int mode) {
        return mode == DETAIL_INTERACTION_SYSTEM ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL;
    }

    public static int getDetailThemeMode() {
        if (Prefers.getPrefers().contains("detail_theme_mode")) {
            int theme = Prefers.getInt("detail_theme_mode", DETAIL_THEME_CURRENT);
            if (theme == DETAIL_STYLE_PROFILE && isCurrentThemePreference()) return DETAIL_STYLE_NATIVE;
            return clampDetailThemeMode(theme);
        }
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return clampDetailThemeMode(Prefers.getInt("tmdb_detail_style", DETAIL_STYLE_PROFILE));
        return getDetailOpenMode() == DETAIL_OPEN_ORIGINAL_ENHANCED ? DETAIL_STYLE_NATIVE : DETAIL_STYLE_PROFILE;
    }

    public static void putDetailThemeMode(int mode) {
        int value = clampDetailThemeMode(mode);
        Prefers.put("detail_theme_mode", value);
        Prefers.put("tmdb_detail_style", value);
    }

    private static int clampDetailThemeMode(int mode) {
        if (mode == DETAIL_STYLE_PROFILE || mode == DETAIL_STYLE_CINEMA || mode == DETAIL_STYLE_NATIVE) return mode;
        return DETAIL_STYLE_NATIVE;
    }

    private static boolean isCurrentThemePreference() {
        if (Prefers.getPrefers().contains("detail_open_mode")) return false;
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return false;
        return Prefers.getPrefers().contains("detail_interaction_mode") && Prefers.getInt("detail_interaction_mode", DETAIL_INTERACTION_ORIGINAL) == DETAIL_INTERACTION_SYSTEM && isTmdbEnabled();
    }

    public static int getTmdbMatchMode() {
        if (Prefers.getPrefers().contains("tmdb_match_mode")) return clampTmdbMatchMode(Prefers.getInt("tmdb_match_mode", TMDB_MATCH_SMART));
        if (Prefers.getPrefers().contains("tmdb_match_dialog")) return Prefers.getBoolean("tmdb_match_dialog", true) ? TMDB_MATCH_STRICT_DIALOG : TMDB_MATCH_STRICT;
        return TMDB_MATCH_SMART;
    }

    public static void putTmdbMatchMode(int mode) {
        Prefers.put("tmdb_match_mode", clampTmdbMatchMode(mode));
    }

    public static boolean isTmdbSmartMatch() {
        int mode = getTmdbMatchMode();
        return mode == TMDB_MATCH_SMART || mode == TMDB_MATCH_SMART_DIALOG;
    }

    public static boolean isTmdbMatchDialog() {
        int mode = getTmdbMatchMode();
        return mode == TMDB_MATCH_STRICT_DIALOG || mode == TMDB_MATCH_SMART_DIALOG;
    }

    public static boolean isPersonalRecommendation() {
        if (Prefers.getPrefers().contains("personal_recommendation")) return Prefers.getBoolean("personal_recommendation", false);
        return Prefers.getBoolean("ai_recommendation", false);
    }

    public static void putPersonalRecommendation(boolean enabled) {
        Prefers.put("personal_recommendation", enabled);
        Prefers.put("ai_recommendation", enabled);
    }

    @Deprecated
    public static boolean isAiRecommendation() {
        return isPersonalRecommendation();
    }

    @Deprecated
    public static void putAiRecommendation(boolean enabled) {
        putPersonalRecommendation(enabled);
    }

    private static int clampTmdbMatchMode(int mode) {
        if (mode == TMDB_MATCH_STRICT || mode == TMDB_MATCH_SMART || mode == TMDB_MATCH_STRICT_DIALOG || mode == TMDB_MATCH_SMART_DIALOG) return mode;
        return TMDB_MATCH_SMART;
    }

    public static boolean isTmdbDetailBackdropSlide() {
        return Prefers.getBoolean("tmdb_detail_backdrop_slide", true);
    }

    public static void putTmdbDetailBackdropSlide(boolean enabled) {
        Prefers.put("tmdb_detail_backdrop_slide", enabled);
    }

    public static boolean isTmdbDetailPage() {
        return isTmdbMode(getDetailOpenMode()) && getTmdbModel() == TMDB_MODEL_NATIVE && TmdbConfig.objectFrom(getTmdbConfig()).isReady();
    }

    public static int getDetailOpenMode() {
        int mode;
        if (Prefers.getPrefers().contains("detail_open_mode")) {
            int stored = Prefers.getInt("detail_open_mode", DETAIL_OPEN_ENHANCED);
            if (stored == DETAIL_OPEN_CINEMA) {
                if (!Prefers.getPrefers().contains("detail_theme_mode") && !Prefers.getPrefers().contains("tmdb_detail_style")) putDetailThemeMode(DETAIL_STYLE_CINEMA);
                mode = DETAIL_OPEN_ENHANCED;
                Prefers.put("detail_open_mode", mode);
            } else {
                mode = clampDetailOpenMode(stored);
            }
        } else if (Prefers.getPrefers().contains("detail_interaction_mode")) {
            mode = getDetailInteractionMode() == DETAIL_INTERACTION_SYSTEM ? DETAIL_OPEN_ORIGINAL_ENHANCED : DETAIL_OPEN_DIRECT;
            Prefers.put("detail_open_mode", mode);
            migrateCurrentDetailTheme(mode);
        } else if (Prefers.getPrefers().contains("search_detail_page")) {
            mode = Prefers.getBoolean("search_detail_page") ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT;
        } else {
            mode = isTmdbEnabled() ? DETAIL_OPEN_ORIGINAL_ENHANCED : DETAIL_OPEN_DIRECT;
            migrateCurrentDetailTheme(mode);
        }
        return isTmdbMode(mode) && !isTmdbReady() ? DETAIL_OPEN_DIRECT : mode;
    }

    public static void putDetailOpenMode(int mode) {
        if (mode == DETAIL_OPEN_CINEMA) {
            putDetailThemeMode(DETAIL_STYLE_CINEMA);
            mode = DETAIL_OPEN_ENHANCED;
        } else if (mode == DETAIL_OPEN_FUSION) {
            putDetailThemeMode(DETAIL_STYLE_PROFILE);
        } else if (mode == DETAIL_OPEN_ORIGINAL_ENHANCED) {
            putDetailThemeMode(DETAIL_STYLE_NATIVE);
        }
        int value = clampDetailOpenMode(mode);
        Prefers.put("detail_open_mode", value);
        Prefers.put("detail_interaction_mode", isTmdbMode(value) ? DETAIL_INTERACTION_SYSTEM : DETAIL_INTERACTION_ORIGINAL);
        putTmdbEnabled(isTmdbMode(value));
    }

    public static boolean isTmdbMode(int mode) {
        return mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_PLAYER || mode == DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    public static boolean isStandaloneTmdbDetailMode(int mode) {
        return mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_PLAYER;
    }

    public static boolean isFusionDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_FUSION;
    }

    public static boolean isPlayerDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_PLAYER;
    }

    public static boolean isDirectDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_DIRECT;
    }

    public static boolean isSearchDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_ENHANCED;
    }

    public static boolean isOriginalEnhancedDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    public static boolean isCinemaDetailPage() {
        return isTmdbDetailPage() && isTmdbCinemaStyle();
    }

    public static void putSearchDetailPage(boolean enabled) {
        putDetailOpenMode(enabled ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT);
    }

    public static int nextDetailOpenMode() {
        int[] modes = {DETAIL_OPEN_ORIGINAL_ENHANCED, DETAIL_OPEN_FUSION, DETAIL_OPEN_ENHANCED, DETAIL_OPEN_PLAYER, DETAIL_OPEN_DIRECT};
        int mode = getDetailOpenMode();
        for (int i = 0; i < modes.length; i++) if (modes[i] == mode) return modes[(i + 1) % modes.length];
        return DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    private static int clampDetailOpenMode(int mode) {
        if (mode == DETAIL_OPEN_CINEMA) return DETAIL_OPEN_ENHANCED;
        if (mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_DIRECT || mode == DETAIL_OPEN_PLAYER || mode == DETAIL_OPEN_ORIGINAL_ENHANCED) return mode;
        return DETAIL_OPEN_ORIGINAL_ENHANCED;
    }

    private static void migrateCurrentDetailTheme(int mode) {
        if (mode != DETAIL_OPEN_ORIGINAL_ENHANCED) return;
        if (Prefers.getPrefers().contains("tmdb_detail_style")) return;
        if (!Prefers.getPrefers().contains("detail_theme_mode") || Prefers.getInt("detail_theme_mode", DETAIL_STYLE_PROFILE) == DETAIL_STYLE_PROFILE) putDetailThemeMode(DETAIL_STYLE_NATIVE);
    }

    public static int getTmdbDetailStyle() {
        return getDetailThemeMode();
    }

    public static void putTmdbDetailStyle(int style) {
        putDetailThemeMode(style);
    }

    public static boolean isTmdbCinemaStyle() {
        return getTmdbDetailStyle() == DETAIL_STYLE_CINEMA;
    }

    public static boolean isTmdbNativeStyle() {
        return getTmdbDetailStyle() == DETAIL_STYLE_NATIVE;
    }

    public static int getTmdbDetailTheme() {
        return clampTmdbDetailTheme(Prefers.getInt("tmdb_detail_theme", 2));
    }

    public static void putTmdbDetailTheme(int theme) {
        Prefers.put("tmdb_detail_theme", clampTmdbDetailTheme(theme));
    }

    public static boolean getTmdbEpisodeGridMode() {
        return Prefers.getBoolean("tmdb_episode_grid_mode", false);
    }

    public static void putTmdbEpisodeGridMode(boolean gridMode) {
        Prefers.put("tmdb_episode_grid_mode", gridMode);
    }

    public static boolean isTmdbEpisodeFileSize() {
        return Prefers.getBoolean("tmdb_episode_file_size", false);
    }

    public static void putTmdbEpisodeFileSize(boolean enabled) {
        Prefers.put("tmdb_episode_file_size", enabled);
    }

    public static int nextTmdbDetailTheme(int theme) {
        return clampTmdbDetailTheme(theme) == 2 ? 1 : 2;
    }

    public static boolean resolveTmdbDetailLightTheme(int theme, boolean systemNight) {
        int value = clampTmdbDetailTheme(theme);
        return value != 1;
    }

    static int clampTmdbDetailTheme(int theme) {
        return theme == 1 ? 1 : 2;
    }

    public static boolean isHomeHistory() {
        return Prefers.getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean homeHistory) {
        Prefers.put("home_history", homeHistory);
    }

    public static boolean isHomeVodAutoLoad() {
        return Prefers.getBoolean("home_vod_auto_load", true);
    }

    public static void putHomeVodAutoLoad(boolean autoLoad) {
        Prefers.put("home_vod_auto_load", autoLoad);
    }

    public static int getFullscreenMenuKey() {
        return Prefers.getInt("fullscreen_menu_key", 0);
    }

    public static void putFullscreenMenuKey(int menuKey) {
        Prefers.put("fullscreen_menu_key", menuKey);
    }

    public static int getHomeMenuKey() {
        int menuKey = Prefers.getInt("home_menu_key", 0);
        return menuKey < 0 || menuKey > 9 ? 0 : menuKey;
    }

    public static void putHomeMenuKey(int menuKey) {
        Prefers.put("home_menu_key", menuKey);
    }

    public static boolean isPlayBackToDetail() {
        return Prefers.getBoolean("play_back_to_detail");
    }

    public static void putPlayBackToDetail(boolean backToDetail) {
        Prefers.put("play_back_to_detail", backToDetail);
    }

    public static boolean isSubtitleAutoMatchEnabled() {
        return Prefers.getBoolean("subtitle_auto_match", false);
    }

    public static void putSubtitleAutoMatchEnabled(boolean enabled) {
        Prefers.put("subtitle_auto_match", enabled);
    }

    public static String getSubtitlePreferredLanguage() {
        return Prefers.getString("subtitle_preferred_language", "zh");
    }

    public static void putSubtitlePreferredLanguage(String language) {
        Prefers.put("subtitle_preferred_language", language == null || language.isEmpty() ? "zh" : language);
    }

    public static String getSubtitleAssrtToken() {
        return Prefers.getString("subtitle_assrt_token");
    }

    public static void putSubtitleAssrtToken(String token) {
        Prefers.put("subtitle_assrt_token", token);
    }

    public static boolean isAutoSkipIntroOutro() {
        return Prefers.getBoolean("auto_skip_intro_outro", false);
    }

    public static void putAutoSkipIntroOutro(boolean enabled) {
        Prefers.put("auto_skip_intro_outro", enabled);
    }

    public static int getSearchUi() {
        return Prefers.getInt("search_ui", 1) == 0 ? 0 : 1;
    }

    public static void putSearchUi(int ui) {
        Prefers.put("search_ui", ui == 0 ? 0 : 1);
    }

    public static int getSearchThread() {
        return clampSearchThread(Prefers.getInt("search_thread", 10));
    }

    public static void putSearchThread(int thread) {
        Prefers.put("search_thread", clampSearchThread(thread));
    }

    private static int clampSearchThread(int thread) {
        return Math.max(1, Math.min(thread, 32));
    }

    public static int getSearchColumn() {
        return clampSearchColumn(Prefers.getInt("search_column", 0));
    }

    public static void putSearchColumn(int column) {
        Prefers.put("search_column", clampSearchColumn(column));
    }

    private static int clampSearchColumn(int column) {
        return column < 0 || column > 2 ? 0 : column;
    }
}
