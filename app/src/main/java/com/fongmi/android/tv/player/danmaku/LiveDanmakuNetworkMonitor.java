package com.fongmi.android.tv.player.danmaku;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

final class LiveDanmakuNetworkMonitor {

    private final ConnectivityManager manager;
    private final Runnable onAvailable;
    private final ConnectivityManager.NetworkCallback callback;
    private boolean registered;

    LiveDanmakuNetworkMonitor(Context context, Runnable onAvailable) {
        manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.onAvailable = onAvailable;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                LiveDanmakuNetworkMonitor.this.onAvailable.run();
            }
        };
    }

    synchronized void start() {
        if (registered || manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(callback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
                manager.registerNetworkCallback(request, callback);
            }
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    synchronized void stop() {
        if (!registered || manager == null) return;
        registered = false;
        try {
            manager.unregisterNetworkCallback(callback);
        } catch (Throwable ignored) {
        }
    }
}
