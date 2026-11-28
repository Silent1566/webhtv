package com.github.catvod.net.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 401 认证重试必须只作用于发起挑战的来源（协议 + 主机 + 有效端口）。
 *
 * <p>MockWebServer 都监听 127.0.0.1，只有端口不同，因此"同主机不同来源"在这里是真实网络条件，
 * 不依赖任何替身。
 */
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28)
public class AuthInterceptorOriginTest {

    private final List<MockWebServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        for (MockWebServer server : servers) server.close();
    }

    @Test
    public void credentialRememberedForOnePortIsNotReusedOnAnotherPort() throws Exception {
        MockWebServer source = server();
        MockWebServer other = server();
        source.enqueue(ok("source"));
        other.enqueue(challenge());
        other.enqueue(ok("retried"));
        OkHttpClient client = client();

        assertEquals(200, call(client, withUserInfo(source, "/seed")));
        RecordedRequest seeded = source.takeRequest();
        assertNotNull("userinfo must become an Authorization header", seeded.getHeaders().get("Authorization"));

        assertEquals("a credential learned on one origin must not answer another origin's challenge",
                401, call(client, other.url("/protected").toString()));
        assertEquals("no retry may be attempted with the other origin's credential", 1, other.getRequestCount());
        assertNull(other.takeRequest().getHeaders().get("Authorization"));
    }

    @Test
    public void explicitAuthorizationSurvivesChallengeWithoutRememberedCredentialOverride() throws Exception {
        MockWebServer server = server();
        server.enqueue(ok("seed"));
        server.enqueue(challenge());
        server.enqueue(ok("retried"));
        OkHttpClient client = client();

        assertEquals(200, call(client, withUserInfo(server, "/seed")));
        server.takeRequest();

        Request protectedRequest = new Request.Builder()
                .url(server.url("/protected").toString())
                .header("Authorization", "Bearer explicit")
                .build();
        assertEquals("a caller-supplied credential must not be replaced by a remembered one",
                401, client.newCall(protectedRequest).execute().code());
        assertEquals("explicit credential must not trigger a second request", 2, server.getRequestCount());
        assertEquals("Bearer explicit", server.takeRequest().getHeaders().get("Authorization"));
    }

    @Test
    public void challengeReachedThroughRedirectIsNotRetriedAgainstTheOriginalUrl() throws Exception {
        MockWebServer source = server();
        MockWebServer target = server();
        source.enqueue(new MockResponse.Builder().code(302)
                .addHeader("Location", target.url("/protected").toString()).build());
        source.enqueue(ok("unexpected-retry"));
        target.enqueue(challenge());
        OkHttpClient client = client();

        assertEquals("a cross-origin redirect must not inherit the original credentials",
                401, call(client, withUserInfo(source, "/start")));
        assertNull("the redirect target must never receive the original origin's credential",
                target.takeRequest().getHeaders().get("Authorization"));
    }

    private MockWebServer server() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();
        servers.add(server);
        return server;
    }

    private static OkHttpClient client() {
        return new OkHttpClient.Builder().addInterceptor(new AuthInterceptor()).build();
    }

    private static int call(OkHttpClient client, String url) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            return response.code();
        }
    }

    private static String withUserInfo(MockWebServer server, String path) {
        String base = server.url(path).toString();
        int scheme = base.indexOf("://") + 3;
        return base.substring(0, scheme) + "probe:" + "pass" + "@" + base.substring(scheme);
    }

    private static MockResponse ok(String body) {
        return new MockResponse.Builder().code(200).body(body).build();
    }

    private static MockResponse challenge() {
        return new MockResponse.Builder().code(401).addHeader("WWW-Authenticate", "Basic realm=\"fixture\"").build();
    }
}
