package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.proxy.MultiThreadProxy;
import com.fongmi.android.tv.cache.CacheScheduler;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.DanmakuSearchListFocusFixer;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PreviousProcessExitLogger;
import com.fongmi.android.tv.utils.WebViewDataDirectoryGuard;
import com.fongmi.hook.Hook;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private final Runnable backgroundServicesStarter = this::startBackgroundServicesNow;

    private volatile Activity activity;
    private volatile int foregroundActivities;
    private Hook hook;

    private final Object resourcesLock = new Object();
    private volatile Resources resources;
    private volatile int resourcesLanguage = Integer.MIN_VALUE;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static boolean isForeground() {
        App app = get();
        return app != null && app.foregroundActivities > 0;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (PlaybackRecoveryMonitor.isRecoveryProcess(base)) return;
        WebViewDataDirectoryGuard.clearStaleLock(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (PlaybackRecoveryMonitor.isRecoveryProcess(this)) return;
        PlaybackMemoryMonitor.process().initialize(this);
        PlaybackSystemConditionMonitor.process().initialize(this);
        Setting.applyLanguage();
        ThemeController.applyNightModeToApp();
        AppBranding.applyLauncherIcon(this);
        DebugLogStore.restoreEnabled();
        if (DebugLogStore.isEnabled()) {
            PlaybackRecoveryMonitor.logPreviousResult(this);
            Setting.logDebugEnvironment("restore");
            PreviousProcessExitLogger.log(this);
        }
        Notify.createChannel();
        ProxySetting.apply();
        registerActivityLifecycleCallbacks(this);
        registerContentHandlers();
        resumeBackgroundServices();
        post(() -> CacheScheduler.get().start(), 30_000L);
    }

    private void registerContentHandlers() {
        // 猫源动作项排最前：它的判定最便宜（只比字符串），且命中就该直接开网页，
        // 不该让音频/阅读器 handler 先按站点规则把它认走
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.CatActionContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.GameContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.AudioContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.ReaderContentHandler());
        registerReaderFallback();
    }

    private void registerReaderFallback() {
        Product.registerReaderFallback();
    }

    @Override
    public void onTrimMemory(int level) {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onLowMemory();
        super.onLowMemory();
    }

    private void startBackgroundServicesNow() {
        SpiderDebug.log("startup", "background services start cost=%sms", System.currentTimeMillis() - time);
        Server.get().start();
        startMultiThreadProxy();
        PlaybackRemoteSyncer.start();
        RemoteAgent.get().start();
        NsdDeviceDiscovery.register();
        com.fongmi.android.tv.lab.LabAutoStart.start(this);
        SpiderDebug.log("startup", "background services ready cost=%sms", System.currentTimeMillis() - time);
    }

    private void startMultiThreadProxy() {
        try {
            var snapshot = MultiThreadProxy.applyStored();
            SpiderDebug.log("proxy",
                    "multi-thread proxy enabled=%s ready=%s port=%s revision=%s",
                    snapshot.config().enabled(),
                    snapshot.ready(),
                    snapshot.actualPort(),
                    snapshot.configRevision());
        } catch (Exception e) {
            SpiderDebug.log("proxy", "multi-thread proxy start failed error=%s", e.getMessage());
        }
    }

    public static void resumeBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.start();
        post(get().backgroundServicesStarter, 1200);
    }

    public static void stopBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.stop();
        MultiThreadProxy.stop();
        PlaybackRemoteSyncer.stop();
        RemoteAgent.get().stop();
        NsdDeviceDiscovery.unregister();
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    /**
     * 全应用的语言资源入口：{@code ResUtil} 与几乎所有界面都经由这里取 Resources，
     * 并且调用方可能来自任意线程（包括 Task 线程池），所以这里必须是非阻塞安全的。
     * <p>
     * 语言切换会把新 Configuration 写进框架自己持有的 Resources，而
     * {@code updateConfiguration} 不是线程安全的：缓存未命中时若无锁，两个线程会同时改写同一个
     * Resources，WebView 在 Android 9 挂到这个 AssetManager 上的资源包就可能被写坏。
     * 另外这条路径一旦抛异常，异常会穿透每一个取资源的调用点（含系统回调），所以更新失败只记日志并
     * 继续返回框架 Resources，让界面以系统语言降级显示，而不是把异常散播到全应用。
     */
    @Override
    @SuppressWarnings("deprecation")
    public Resources getResources() {
        int language = Setting.getLanguage();
        Resources cached = resources;
        if (cached != null && resourcesLanguage == language) return cached;
        synchronized (resourcesLock) {
            if (resources != null && resourcesLanguage == language) return resources;
            Resources resources = super.getResources();
            try {
                Configuration configuration = Setting.wrapLanguage(getBaseContext()).getResources().getConfiguration();
                // WebView adds its resource package to the framework-owned AssetManager on Android 9.
                resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            } catch (Throwable e) {
                SpiderDebug.log("resources", "apply language failed language=%s error=%s", language, e.getMessage());
            }
            this.resources = resources;
            resourcesLanguage = language;
            return resources;
        }
    }

    public void invalidateResources() {
        synchronized (resourcesLock) {
            resources = null;
            resourcesLanguage = Integer.MIN_VALUE;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        foregroundActivities++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        foregroundActivities = Math.max(0, foregroundActivities - 1);
    }
}
