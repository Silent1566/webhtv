package com.fongmi.android.tv.server;

import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Util;

public class Server {

    private volatile PlaybackService service;
    private volatile Nano nano;
    private volatile LanTlsServer lan;
    private long lanGeneration;
    private boolean lanStarting;
    private volatile boolean manage;
    private PlaybackRouteRegistry.Registration routeRegistration;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public synchronized void setService(PlaybackService service) {
        this.service = service;
    }

    public synchronized void clearService(PlaybackService expected) {
        if (service == expected) service = null;
    }

    public boolean isRunning() {
        return nano != null;
    }

    public int getLanPort() {
        LanTlsServer ready = lan;
        return ready == null ? 0 : ready.getListeningPort();
    }

    public String getLanFingerprint() {
        LanTlsServer ready = lan;
        return ready == null ? "" : ready.fingerprint;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized void startManage() {
        manage = true;
        start();
    }

    public synchronized void stopManage() {
        manage = false;
        if (service == null) stop();
    }

    public synchronized void start() {
        if (nano != null) {
            if (lan == null && !lanStarting) startLan(nano, nano.getListeningPort() + LanTlsPolicy.PORT_OFFSET);
            return;
        }
        for (int i = LanTlsPolicy.FIRST_HTTP_PORT; i <= LanTlsPolicy.LAST_HTTP_PORT; i++) {
            try {
                nano = new Nano(i);
                nano.start(500);
                Proxy.set(i);
                if (routeRegistration != null) routeRegistration.close();
                routeRegistration = PlaybackRouteRegistry.registerAppService(i, PlaybackRouteRegistry.AppOwner.MAIN_SERVER);
                startLan(nano, i + LanTlsPolicy.PORT_OFFSET);
                break;
            } catch (Throwable e) {
                nano = null;
            }
        }
    }

    private void startLan(Nano expected, int port) {
        long generation = ++lanGeneration;
        lanStarting = true;
        Task.execute(() -> {
            LanTlsServer candidate = null;
            try {
                candidate = new LanTlsServer(port, LanTlsIdentity.load());
                synchronized (this) {
                    if (nano != expected || lanGeneration != generation) return;
                    candidate.start(LanTlsPolicy.HEADER_TIMEOUT_MS);
                    lan = candidate;
                    candidate = null;
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (candidate != null) candidate.stop();
                } finally {
                    synchronized (this) {
                        if (lanGeneration == generation) lanStarting = false;
                    }
                }
            }
        });
    }

    public void stop() {
        Nano expected = nano;
        Task.execute(() -> {
            synchronized (this) {
                if (manage || service != null || nano != expected) return;
                stopNow();
            }
        });
    }

    public synchronized void shutdown() {
        manage = false;
        service = null;
        stopNow();
    }

    private void stopNow() {
        lanGeneration++;
        lanStarting = false;
        LanTlsServer previousLan = lan;
        lan = null;
        if (previousLan != null) previousLan.stop();
        if (nano != null) nano.stop();
        nano = null;
        if (routeRegistration != null) routeRegistration.close();
        routeRegistration = null;
    }
}
