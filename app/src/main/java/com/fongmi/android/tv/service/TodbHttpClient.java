package com.fongmi.android.tv.service;

import android.os.Build;

import com.github.catvod.net.OkHttp;

import org.conscrypt.Conscrypt;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

final class TodbHttpClient {

    private static OkHttpClient baseClient;
    private static OkHttpClient client;
    private static long timeout;

    private TodbHttpClient() {
    }

    static synchronized OkHttpClient get(long timeoutMs) throws GeneralSecurityException {
        OkHttpClient base = OkHttp.client();
        if (client != null && baseClient == base && timeout == timeoutMs) return client;

        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        TrustManager[] managers = factory.getTrustManagers();
        if (managers.length != 1 || !(managers[0] instanceof X509TrustManager)) {
            throw new GeneralSecurityException("Unexpected default trust managers");
        }
        X509TrustManager trustManager = (X509TrustManager) managers[0];
        SSLContext context = Build.VERSION.SDK_INT < 29 ? LegacyTls.createContext() : SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, null);
        OkHttpClient configured = base.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .sslSocketFactory(context.getSocketFactory(), trustManager)
                .hostnameVerifier(new OkHttpClient().hostnameVerifier())
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .followSslRedirects(false)
                .build();
        baseClient = base;
        timeout = timeoutMs;
        return client = configured;
    }

    private static final class LegacyTls {

        private static SSLContext createContext() throws GeneralSecurityException {
            return SSLContext.getInstance("TLS", Conscrypt.newProvider());
        }
    }
}
