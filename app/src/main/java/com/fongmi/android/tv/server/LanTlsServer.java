package com.fongmi.android.tv.server;

import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.process.Action;
import com.fongmi.android.tv.server.process.LanPairingApi;
import com.fongmi.android.tv.server.proxy.BoundedAsyncRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

final class LanTlsServer extends NanoHTTPD {
    private final Action action = new Action();
    private final LanPairingApi pairing = new LanPairingApi();
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "LAN-TLS-Deadline");
        thread.setDaemon(true);
        return thread;
    });
    final String fingerprint;

    LanTlsServer(int port, LanTlsIdentity identity) {
        super(port);
        fingerprint = identity.fingerprint;
        makeSecure(identity.sockets, new String[]{LanTlsIdentity.PROTOCOL});
        // Nano opens the stream on its acceptor; Conscrypt may handshake during that call.
        setServerSocketFactory(LanTlsPolicy.deferHandshake(getServerSocketFactory()));
        setAsyncRunner(new BoundedAsyncRunner(4, 8));
        deadlines.setRemoveOnCancelPolicy(true);
    }

    @Override public void stop() {
        super.stop();
        deadlines.shutdownNow();
    }

    @Override protected ClientHandler createClientHandler(Socket socket, InputStream incoming) {
        return new ClientHandler(incoming, socket) {
            @Override public void run() {
                try {
                    ScheduledFuture<?> header = deadlines.schedule(this::close, LanTlsPolicy.HEADER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    LanTlsPolicy.Head head;
                    try {
                        head = LanTlsPolicy.readHead(incoming);
                    } finally {
                        header.cancel(false);
                    }
                    if (head.status != 0) {
                        socket.getOutputStream().write(("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n"
                                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        return;
                    }
                    socket.setSoTimeout(LanTlsPolicy.READ_TIMEOUT_MS);
                    ScheduledFuture<?> transfer = deadlines.schedule(this::close, LanTlsPolicy.TRANSFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    try {
                        InputStream replay = new SequenceInputStream(new ByteArrayInputStream(head.bytes), incoming);
                        LanTlsServer.super.createClientHandler(socket, replay).run();
                    } finally {
                        transfer.cancel(false);
                    }
                } catch (IOException | RuntimeException ignored) {
                } finally {
                    close();
                }
            }
        };
    }

    @Override public Response serve(IHTTPSession session) {
        Response response;
        try {
            String path = session.getUri();
            if (LanTlsPolicy.PAIR_PATH.equals(path)) response = pairing.handle(session);
            else if (LanTlsPolicy.ACTION_PATH.equals(path)) response = handleAction(session);
            else if (LanTlsPolicy.INFO_PATH.equals(path)) response = info(session);
            else response = error(Response.Status.NOT_FOUND, "Not found");
        } catch (IOException | ResponseException ignored) {
            response = error(Response.Status.BAD_REQUEST, "Invalid LAN request");
        } catch (RuntimeException ignored) {
            response = error(Response.Status.INTERNAL_ERROR, "LAN request failed");
        }
        response.addHeader("Cache-Control", "no-store");
        response.closeConnection(true);
        return response;
    }

    private Response info(IHTTPSession session) {
        if (session.getMethod() != Method.GET) return error(Response.Status.METHOD_NOT_ALLOWED, "GET required");
        if (!session.getParameters().isEmpty() || !hasNoBody(session)) return error(Response.Status.BAD_REQUEST, "Invalid request");
        return Nano.ok(Device.identityUuid());
    }

    private Response handleAction(IHTTPSession session) throws IOException, ResponseException {
        if (session.getMethod() != Method.GET && session.getMethod() != Method.POST) {
            return error(Response.Status.METHOD_NOT_ALLOWED, "GET or POST required");
        }
        String selected = LanTlsPolicy.action(session.getParameters().get("do"));
        if (selected == null) return error(Response.Status.BAD_REQUEST, "Unknown LAN action");
        LanActionGate.Verdict verdict = action.authorize(session, selected);
        if (!verdict.allowed()) return verdict.status() == 400
                ? error(Response.Status.BAD_REQUEST, "Unknown LAN action")
                : error(Response.Status.FORBIDDEN, "LAN action denied");

        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) {
            String value = session.getHeaders().get("content-length");
            if (value == null) return error(Response.Status.LENGTH_REQUIRED, "Content length required");
            long length = Long.parseLong(value);
            if (length > 0) {
                String contentType = session.getHeaders().getOrDefault("content-type", "");
                String type = contentType.toLowerCase(Locale.ROOT);
                boolean form = type.equals("application/x-www-form-urlencoded") || type.startsWith("application/x-www-form-urlencoded;");
                boolean multipart = type.startsWith("multipart/form-data;") && ("sync".equals(selected) || "apk".equals(selected));
                if (!form && !multipart) return error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "Unsupported LAN body");
                long max = multipart || "sync".equals(selected) ? LanTlsPolicy.MULTIPART_LIMIT : LanTlsPolicy.FORM_LIMIT;
                if (length > max) return error(Response.Status.PAYLOAD_TOO_LARGE, "LAN body too large");
                if (multipart) {
                    session.getHeaders().put("content-type", LanTlsPolicy.multipartWithUtf8(contentType));
                }
                session.parseBody(files);
            }
        } else if (!hasNoBody(session)) {
            return error(Response.Status.BAD_REQUEST, "GET body not allowed");
        }
        if (files.containsKey("do") || !LanTlsPolicy.sameAction(selected, session.getParameters().get("do"))) {
            return error(Response.Status.BAD_REQUEST, "Invalid LAN action selector");
        }
        return action.dispatchAuthorized(session, selected, files);
    }

    private static boolean hasNoBody(IHTTPSession session) {
        String length = session.getHeaders().get("content-length");
        return length == null || Long.parseLong(length) == 0;
    }

    private static Response error(Response.Status status, String text) {
        return Nano.error(status, text);
    }
}
