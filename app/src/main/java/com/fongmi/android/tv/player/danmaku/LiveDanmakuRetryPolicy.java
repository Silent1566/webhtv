package com.fongmi.android.tv.player.danmaku;

import java.io.IOException;
import java.net.ProtocolException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

final class LiveDanmakuRetryPolicy {

    static final long MIN_DELAY_MS = 250L;
    static final long MAX_DELAY_MS = 30_000L;

    private LiveDanmakuRetryPolicy() {
    }

    static boolean shouldRetryClose(int code) {
        return code == 1001 || code == 1006 || code == 1011 || code == 1012 || code == 1013;
    }

    static boolean shouldRetryFailure(Throwable error, int httpCode) {
        if (httpCode == 401 || httpCode == 403 || httpCode == 404 || (httpCode >= 400 && httpCode < 429)) return false;
        if (httpCode == 429 || httpCode >= 500) return true;
        if (hasCause(error, CertificateException.class) || hasCause(error, SSLPeerUnverifiedException.class) || hasCause(error, SSLHandshakeException.class)) return false;
        if (hasCause(error, ProtocolException.class)) return false;
        return error instanceof IOException;
    }

    static long nextDelayMs(int attempt, double randomUnit) {
        int shift = Math.max(0, Math.min(20, attempt));
        long cap = Math.min(MAX_DELAY_MS, 1_000L << shift);
        if (cap <= MIN_DELAY_MS) return MIN_DELAY_MS;
        double unit = Math.max(0d, Math.min(Math.nextDown(1d), randomUnit));
        return MIN_DELAY_MS + (long) (unit * (cap - MIN_DELAY_MS + 1L));
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }
}
