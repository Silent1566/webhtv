package com.fongmi.android.tv.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.os.Build;

import com.github.catvod.net.OkHttp;

import org.conscrypt.Conscrypt;
import org.junit.Test;

import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLSocket;

import okhttp3.OkHttpClient;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TodbTlsDeviceTest {

    @Test
    public void tls13ClientIsIsolatedAndReused() throws Exception {
        Provider[] providers = Security.getProviders();
        OkHttpClient base = OkHttp.client();
        OkHttpClient client = TodbHttpClient.get(5000);
        assertSame(client, TodbHttpClient.get(5000));
        assertSame(base, OkHttp.client());
        assertArrayEquals(providers, Security.getProviders());
        assertNotSame(base.sslSocketFactory(), client.sslSocketFactory());
        assertSame(base.dns(), client.dns());
        assertSame(base.proxySelector(), client.proxySelector());
        assertEquals(base.interceptors(), client.interceptors());
        assertEquals(5000, client.connectTimeoutMillis());
        assertEquals(5000, client.readTimeoutMillis());
        assertEquals(5000, client.writeTimeoutMillis());
        assertFalse(client.followSslRedirects());
        try (SSLSocket socket = (SSLSocket) client.sslSocketFactory().createSocket()) {
            assertTrue(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1.3"));
            assertEquals(Build.VERSION.SDK_INT < 29, Conscrypt.isConscrypt(socket));
        }
    }

    @Test
    public void missingPeerCertificateAndEmptyTrustChainAreRejected() throws Exception {
        OkHttpClient client = TodbHttpClient.get(5000);
        try (SSLSocket socket = (SSLSocket) client.sslSocketFactory().createSocket()) {
            assertFalse(client.hostnameVerifier().verify("playback.theotherdb.org", socket.getSession()));
        }
        try {
            client.x509TrustManager().checkServerTrusted(new X509Certificate[0], "RSA");
            fail("An empty certificate chain must not be trusted");
        } catch (CertificateException | IllegalArgumentException expected) {
        }
    }

    @Test(timeout = 20000)
    public void liveTodbQueryReturnsMarkers() {
        assumeTrue("Remote service smoke test is opt-in", "true".equals(
                InstrumentationRegistry.getArguments().getString("introSkipRemoteTlsSmoke")));
        // 静态缓存会把上一次查询的原始段直接复用，那样这条用例就不再证明本次真的连上了 TODB。
        IntroSkipService.clearCacheForTest();
        IntroSkipService.LoadResult result = new IntroSkipService().loadResult(
                new IntroSkipService.Query(307853, "", "tv", 1, 2, 3_162_000));
        assertTrue("TODB must return real markers over HTTPS on this device",
                result.getPlan().getAll().stream().anyMatch(segment -> "TODB".equals(segment.getProvider())));
        assertTrue("TheIntroDB 404 plus a successful TODB response must be cacheable", result.isCacheable());
    }
}
