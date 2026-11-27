package com.fongmi.android.tv.server;

import org.junit.Test;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LanTlsPolicyTest {
    @Test public void plaintextControlPathsCannotReachLegacyHandlers() {
        for (String path : Arrays.asList("/action", "/action/old", "/actions", "/lan/pair", "/manage/action", "/manage/sync/start")) {
            assertTrue(path, LanTlsPolicy.rejectPlaintext(path));
        }
        assertFalse(LanTlsPolicy.rejectPlaintext("/local"));
        assertFalse(LanTlsPolicy.rejectPlaintext("/device"));
    }

    @Test public void duplicateContentLengthAndAnyTransferEncodingAreRejectedBeforeBodyRead() throws Exception {
        assertEquals(400, header("Content-Length: 8\r\ncOnTeNt-LeNgTh: 8\r\n"));
        assertEquals(400, header("Transfer-Encoding: chunked\r\n"));
        assertEquals(400, header("Content-Length: 8, 8\r\n"));
        assertEquals(400, header("X-WebHTV-Device: one\r\nx-webhtv-device: two\r\n"));
        assertEquals(0, header("Content-Length: 8\r\n"));
    }

    @Test public void bodyCannotReplaceOrRepeatAuthorizedAction() {
        assertEquals("push", LanTlsPolicy.action(List.of("push")));
        assertNull(LanTlsPolicy.action(List.of("push", "sync")));
        assertTrue(LanTlsPolicy.sameAction("push", List.of("push")));
        assertFalse(LanTlsPolicy.sameAction("push", List.of("sync")));
        assertFalse(LanTlsPolicy.sameAction("push", List.of("push", "push")));
    }

    @Test public void multipartUsesUtf8WithoutChangingTheBoundary() {
        String incoming = "Multipart/Form-Data; boundary=BoundaryCase123; charset=US-ASCII";
        ParserProbe.assertUtf8AndBoundary(LanTlsPolicy.multipartWithUtf8(incoming));
    }

    @Test public void acceptingSocketDoesNotGetTlsInputUntilWorkerReads() throws Exception {
        AtomicInteger inputOpens = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        Socket tls = new Socket() {
            @Override public InputStream getInputStream() {
                inputOpens.incrementAndGet();
                return new ByteArrayInputStream(new byte[]{7, 8});
            }

            @Override public void close() { closed.set(true); }
        };
        NanoHTTPD.ServerSocketFactory sockets = LanTlsPolicy.deferHandshake(() -> new ServerSocket() {
            @Override public Socket accept() { return tls; }
        });
        try (ServerSocket listening = sockets.create(); Socket accepted = listening.accept()) {
            accepted.setSoTimeout(5000);
            InputStream queued = accepted.getInputStream();
            assertEquals(0, inputOpens.get());
            assertEquals(7, queued.read());
            assertEquals(8, queued.read());
            assertEquals(1, inputOpens.get());
        }
        assertTrue(closed.get());
    }

    @Test public void closingQueuedSocketNeverStartsItsHandshake() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Socket tls = new Socket() {
            @Override public InputStream getInputStream() throws IOException {
                fail("closed queued socket must not start handshake");
                throw new IOException();
            }

            @Override public void close() { closed.set(true); }
        };
        NanoHTTPD.ServerSocketFactory sockets = LanTlsPolicy.deferHandshake(() -> new ServerSocket() {
            @Override public Socket accept() { return tls; }
        });
        try (ServerSocket listening = sockets.create(); Socket accepted = listening.accept()) {
            accepted.getInputStream();
        }
        assertTrue(closed.get());
    }

    private static final class ParserProbe extends NanoHTTPD {
        private ParserProbe() { super(0); }

        static void assertUtf8AndBoundary(String contentType) {
            ContentType parsed = new ContentType(contentType);
            assertTrue(parsed.isMultipart());
            assertEquals("BoundaryCase123", parsed.getBoundary());
            byte[] label = "中文标签".getBytes(StandardCharsets.UTF_8);
            assertEquals("中文标签", new String(label, java.nio.charset.Charset.forName(parsed.getEncoding())));
        }
    }

    private static int header(String headers) throws Exception {
        byte[] bytes = ("POST /lan/pair HTTP/1.1\r\nHost: 127.0.0.1\r\n" + headers + "\r\n99999999")
                .getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        LanTlsPolicy.Head head = LanTlsPolicy.readHead(input);
        assertEquals(8, input.available());
        return head.status;
    }
}
