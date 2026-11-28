package com.github.catvod.net.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.catvod.Init;
import com.github.catvod.net.OkHttp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 设备侧验收：走真实生产客户端（{@code OkHttp.client()} 内置 {@link AuthInterceptor}），
 * 用同一主机 127.0.0.1 上的不同端口构造不同来源，断言跨来源不继承凭据。
 */
@RunWith(AndroidJUnit4.class)
public class AuthInterceptorOriginDeviceTest {

    private final List<Fixture> fixtures = new ArrayList<>();

    @Before
    public void prepare() {
        Init.set(InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext());
        OkHttp.authInterceptor().clear();
    }

    @After
    public void stop() {
        for (Fixture fixture : fixtures) fixture.server.stop();
        OkHttp.authInterceptor().clear();
    }

    @Test
    public void credentialFromOnePortNeverAnswersAnotherPortsChallenge() throws Exception {
        Fixture source = fixture();
        Fixture other = fixture();
        source.respond(200);
        other.respond(401).challenge();

        assertEquals(200, call(withUserInfo(source, "/seed")));
        assertEquals(1, source.requests.size());
        assertTrue("userinfo must become an Authorization header", source.lastAuthorization() != null);

        assertEquals("another origin's challenge must not be answered with the remembered credential",
                401, call(other.url("/protected")));
        assertEquals("no retry may be attempted on the other origin", 1, other.requests.size());
        assertNull(other.lastAuthorization());
    }

    @Test
    public void callerSuppliedAuthorizationIsNotReplacedAfterAChallenge() throws Exception {
        Fixture source = fixture();
        source.respond(200);
        assertEquals(200, call(withUserInfo(source, "/seed")));
        assertEquals(1, source.requests.size());

        source.respond(401).challenge();
        Request explicit = new Request.Builder()
                .url(source.url("/protected"))
                .header("Authorization", "Bearer explicit")
                .build();
        try (Response response = OkHttp.client().newCall(explicit).execute()) {
            assertEquals("explicit credential must win over the remembered one", 401, response.code());
        }
        assertEquals("explicit credential must not trigger a retry", 2, source.requests.size());
        assertEquals("Bearer explicit", source.lastAuthorization());
    }

    @Test
    public void challengeBehindCrossPortRedirectIsNotRetriedOnTheOriginalUrl() throws Exception {
        Fixture origin = fixture();
        Fixture target = fixture();
        origin.redirectTo(target.url("/protected"));
        target.respond(401).challenge();

        assertEquals("a cross-origin redirect must not inherit the original credentials",
                401, call(withUserInfo(origin, "/start")));
        assertEquals(1, target.requests.size());
        assertNull("the redirect target must never receive the original origin's credential", target.lastAuthorization());
        assertEquals("the original URL must not be retried", 1, origin.requests.size());
    }

    private Fixture fixture() throws IOException {
        Fixture fixture = new Fixture();
        fixtures.add(fixture);
        fixture.server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        return fixture;
    }

    private static int call(String url) throws IOException {
        try (Response response = OkHttp.client().newCall(new Request.Builder().url(url).build()).execute()) {
            return response.code();
        }
    }

    private static String withUserInfo(Fixture fixture, String path) {
        String base = fixture.url(path);
        int scheme = base.indexOf("://") + 3;
        return base.substring(0, scheme) + "probe:pass@" + base.substring(scheme);
    }

    private static final class Fixture {

        final NanoHTTPD server;
        final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<String> authorizations = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private volatile boolean challenge;
        private volatile String redirect;

        Fixture() {
            server = new NanoHTTPD("127.0.0.1", 0) {
                @Override
                public Response serve(IHTTPSession session) {
                    String authorization = session.getHeaders().get("authorization");
                    requests.add(session.getUri());
                    authorizations.add(authorization);
                    if (redirect != null) {
                        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_PLAINTEXT, "");
                        response.addHeader("Location", redirect);
                        return response;
                    }
                    Response response = newFixedLengthResponse(status == 200 ? Response.Status.OK : Response.Status.UNAUTHORIZED,
                            MIME_PLAINTEXT, "fixture");
                    if (challenge) response.addHeader("WWW-Authenticate", "Basic realm=\"fixture\"");
                    return response;
                }
            };
        }

        Fixture respond(int status) {
            this.status = status;
            return this;
        }

        Fixture challenge() {
            this.challenge = true;
            return this;
        }

        Fixture redirectTo(String location) {
            this.redirect = location;
            return this;
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getListeningPort() + path;
        }

        String lastAuthorization() {
            return authorizations.isEmpty() ? null : authorizations.get(authorizations.size() - 1);
        }
    }
}
