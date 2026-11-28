package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;

import com.github.catvod.Init;
import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LocalFileEndpointStatusTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();
    private final Local local = new Local();
    private File privateDirectory;
    private File privateFile;

    @Before
    public void setUp() throws Exception {
        Init.set(RuntimeEnvironment.getApplication());
        Path.setRootOverride(files.newFolder("root"));
        privateDirectory = files.newFolder("root-private");
        privateFile = new File(privateDirectory, "secret.srt");
        Files.write(privateFile.toPath(), "private subtitle".getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        Path.setRootOverride(null);
    }

    @Test
    public void missingLocalFileReportsNotFoundInsteadOfServerError() throws Exception {
        try (Response response = serve("/file/missing.srt")) {
            assertEquals(Response.Status.NOT_FOUND, response.getStatus());
        }
    }

    @Test
    public void existingLocalFileStillServesItsContent() throws Exception {
        File file = new File(Path.root(), "present.srt");
        Files.write(file.toPath(), "subtitle".getBytes(StandardCharsets.UTF_8));

        try (Response response = serve("/file/present.srt")) {
            assertEquals(Response.Status.OK, response.getStatus());
            assertEquals("subtitle", read(response));
        }
    }

    @Test
    public void directoryStillReturnsItsListing() throws Exception {
        File folder = new File(Path.root(), "folder");
        assertTrue(folder.mkdirs());

        try (Response response = serve("/file/folder")) {
            assertEquals(Response.Status.OK, response.getStatus());
            assertNotNull(read(response));
        }
    }

    @Test
    public void remoteAbsolutePathCannotReadPrivateFile() throws Exception {
        try (Response response = serve("192.0.2.10", "/file" + privateFile.getAbsolutePath(), Map.of(), Map.of())) {
            assertEquals(Response.Status.FORBIDDEN, response.getStatus());
        }
    }

    @Test
    public void parentTraversalAndSamePrefixSiblingAreForbiddenToRemotePeers() throws Exception {
        try (Response response = serve("/file/../root-private/secret.srt")) {
            assertEquals(Response.Status.FORBIDDEN, response.getStatus());
        }
    }

    @Test
    public void remotePrivateDirectoryDoesNotExposeItsListing() throws Exception {
        try (Response response = serve("/file" + privateDirectory.getAbsolutePath())) {
            assertEquals(Response.Status.FORBIDDEN, response.getStatus());
        }
    }

    @Test
    public void forgedForwardingHeadersNeverGrantLoopbackAccess() throws Exception {
        Map<String, String> forged = Map.of("x-forwarded-for", "127.0.0.1", "forwarded", "for=\"[::1]\"",
                "http-client-ip", "::1", "remote-addr", "127.0.0.1", "host", "localhost");
        try (Response response = serve("192.0.2.10", "/file" + privateFile.getAbsolutePath(), forged, Map.of())) {
            assertEquals(Response.Status.FORBIDDEN, response.getStatus());
        }
    }

    @Test
    public void missingUnknownAndNonLoopbackPeersCannotReadPrivateFiles() throws Exception {
        for (String peer : new String[]{null, "", "localhost", "127.attacker.invalid", "0.0.0.0", "::", "2001:db8::1"}) {
            try (Response response = serve(peer, "/file" + privateFile.getAbsolutePath(), Map.of(), Map.of())) {
                assertEquals("untrusted peer: " + peer, Response.Status.FORBIDDEN, response.getStatus());
            }
        }
    }

    @Test
    public void ipv4AndIpv6LoopbackKeepReadingInternalSubtitleFiles() throws Exception {
        for (String peer : new String[]{"127.0.0.1", "127.0.0.2", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"}) {
            try (Response response = serve(peer, "/file" + privateFile.getAbsolutePath(), Map.of(), Map.of())) {
                assertEquals("local peer: " + peer, Response.Status.OK, response.getStatus());
                assertEquals("private subtitle", read(response));
            }
        }
    }

    @Test
    public void symbolicLinkOutsideRootIsForbiddenRemotelyButStillUsableInternally() throws Exception {
        java.nio.file.Path link = new File(Path.root(), "cache-link").toPath();
        Files.createSymbolicLink(link, privateDirectory.toPath());
        try {
            try (Response response = serve("/file/cache-link/secret.srt")) {
                assertEquals(Response.Status.FORBIDDEN, response.getStatus());
            }
            try (Response response = serve("127.0.0.1", "/file/cache-link/secret.srt", Map.of(), Map.of())) {
                assertEquals(Response.Status.OK, response.getStatus());
                assertEquals("private subtitle", read(response));
            }
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    public void sharedFileRangeEtagAndDownloadHeadersRemainUnchanged() throws Exception {
        File file = new File(Path.root(), "中文 sample.bin");
        Files.write(file.toPath(), "0123456789".getBytes(StandardCharsets.UTF_8));
        String etag;
        try (Response response = serve("192.0.2.10", "/file/中文 sample.bin", Map.of(), Map.of("download", "1"))) {
            assertEquals(Response.Status.OK, response.getStatus());
            assertEquals("0123456789", read(response));
            assertEquals("bytes", response.getHeader("accept-ranges"));
            assertEquals("10", response.getHeader("content-length"));
            assertEquals("nosniff", response.getHeader("x-content-type-options"));
            assertTrue(response.getHeader("content-disposition").contains("filename*=UTF-8''%E4%B8%AD%E6%96%87%20sample.bin"));
            etag = response.getHeader("etag");
            assertNotNull(etag);
        }
        try (Response response = serve("192.0.2.10", "/file/中文 sample.bin", Map.of("range", "bytes=2-4"), Map.of())) {
            assertEquals(Response.Status.PARTIAL_CONTENT, response.getStatus());
            assertEquals("bytes 2-4/10", response.getHeader("content-range"));
            assertEquals(etag, response.getHeader("etag"));
            assertEquals("234", read(response));
        }

        try (Response response = serve("192.0.2.10", "/file/中文 sample.bin", Map.of("range", "bytes=8-"), Map.of())) {
            assertEquals(Response.Status.PARTIAL_CONTENT, response.getStatus());
            assertEquals("89", read(response));
        }
        try (Response response = serve("192.0.2.10", "/file/中文 sample.bin", Map.of("if-none-match", etag), Map.of())) {
            assertEquals(Response.Status.NOT_MODIFIED, response.getStatus());
        }
        try (Response response = serve("192.0.2.10", "/file/中文 sample.bin", Map.of("range", "bytes=10-"), Map.of())) {
            assertEquals(Response.Status.RANGE_NOT_SATISFIABLE, response.getStatus());
            assertEquals("bytes */10", response.getHeader("content-range"));
        }
    }

    private Response serve(String url) {
        return serve("192.0.2.10", url, Map.of(), Map.of());
    }

    private Response serve(String peer, String url, Map<String, String> headers, Map<String, String> params) {
        IHTTPSession session = (IHTTPSession) Proxy.newProxyInstance(
                IHTTPSession.class.getClassLoader(), new Class<?>[]{IHTTPSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeaders")) return headers;
                    if (method.getName().equals("getParms")) return params;
                    if (method.getName().equals("getRemoteIpAddress")) return peer;
                    throw new AssertionError("Unexpected session call: " + method.getName());
                });
        return local.doResponse(session, url, Collections.emptyMap());
    }

    private static String read(Response response) throws Exception {
        if (response.getData() == null) return "";
        try (InputStream input = response.getData()) {
            String length = response.getHeader("content-length");
            byte[] bytes = length == null ? input.readAllBytes() : input.readNBytes(Integer.parseInt(length));
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
