package com.fongmi.android.tv.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

final class LanTlsPolicy {
    static final String PAIR_PATH = "/lan/pair";
    static final String ACTION_PATH = "/action";
    static final String INFO_PATH = "/lan/info";
    static final int PORT_OFFSET = 1000;
    static final int FIRST_HTTP_PORT = 9978;
    static final int LAST_HTTP_PORT = 9998;
    static final int HEADER_TIMEOUT_MS = 5000;
    static final int READ_TIMEOUT_MS = 15000;
    static final int TRANSFER_TIMEOUT_MS = 600000;
    static final long FORM_LIMIT = 16L * 1024 * 1024;
    static final long MULTIPART_LIMIT = 1024L * 1024 * 1024;
    private static final int HEAD_LIMIT = 8192;
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Pattern REQUEST_LINE = Pattern.compile("(?:GET|POST) /[!$&'()*+,;=:@/?A-Za-z0-9._~%-]* HTTP/1[.][01]");
    private static final Pattern LENGTH = Pattern.compile("[0-9]{1,19}");

    private LanTlsPolicy() {
    }

    static boolean rejectPlaintext(String path) {
        return path.startsWith(ACTION_PATH) || PAIR_PATH.equals(path)
                || "/manage/action".equals(path) || "/manage/sync/start".equals(path);
    }

    static String action(List<String> values) {
        return values != null && values.size() == 1 && !values.get(0).isEmpty() ? values.get(0) : null;
    }

    static boolean sameAction(String expected, List<String> values) {
        return expected.equals(action(values));
    }

    static String multipartWithUtf8(String contentType) {
        return "multipart/form-data; charset=utf-8" + contentType.substring(contentType.indexOf(';'));
    }

    static NanoHTTPD.ServerSocketFactory deferHandshake(NanoHTTPD.ServerSocketFactory factory) {
        return () -> new DeferredServerSocket(factory.create());
    }

    static Head readHead(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        int ending = 0;
        while (bytes.size() < HEAD_LIMIT) {
            int next = input.read();
            if (next < 0) return new Head(null, 400);
            bytes.write(next);
            ending = (ending << 8) | next;
            if (ending == 0x0d0a0d0a) {
                byte[] head = bytes.toByteArray();
                return new Head(head, validFraming(head) ? 0 : 400);
            }
        }
        return new Head(null, 400);
    }

    private static boolean validFraming(byte[] header) {
        String[] lines = new String(header, StandardCharsets.ISO_8859_1).split("\r\n");
        if (lines.length == 0 || !REQUEST_LINE.matcher(lines[0]).matches() || !validEscapes(lines[0])) return false;
        boolean lengthSeen = false;
        boolean typeSeen = false;
        boolean deviceSeen = false;
        boolean credentialSeen = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0 || !HEADER_NAME.matcher(line.substring(0, colon)).matches()) return false;
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            for (int j = colon + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if ((c < ' ' && c != '\t') || c == 127) return false;
            }
            if ("transfer-encoding".equals(name)) return false;
            if ("x-webhtv-device".equals(name)) {
                if (deviceSeen) return false;
                deviceSeen = true;
            }
            if ("x-webhtv-credential".equals(name)) {
                if (credentialSeen) return false;
                credentialSeen = true;
            }
            if ("content-length".equals(name)) {
                if (lengthSeen || !LENGTH.matcher(value).matches()) return false;
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return false;
                }
                lengthSeen = true;
            }
            if ("content-type".equals(name)) {
                if (typeSeen) return false;
                typeSeen = true;
            }
        }
        return true;
    }

    private static boolean validEscapes(String requestLine) {
        for (int i = 0; (i = requestLine.indexOf('%', i)) >= 0; i += 3) {
            if (i + 2 >= requestLine.length() || Character.digit(requestLine.charAt(i + 1), 16) < 0
                    || Character.digit(requestLine.charAt(i + 2), 16) < 0) return false;
        }
        return true;
    }

    private static final class DeferredServerSocket extends ServerSocket {
        private final ServerSocket receiver;

        DeferredServerSocket(ServerSocket receiver) throws IOException {
            this.receiver = receiver;
        }

        @Override public void bind(SocketAddress endpoint, int backlog) throws IOException { receiver.bind(endpoint, backlog); }
        @Override public void bind(SocketAddress endpoint) throws IOException { receiver.bind(endpoint); }
        @Override public void setReuseAddress(boolean reuse) throws SocketException { receiver.setReuseAddress(reuse); }
        @Override public Socket accept() throws IOException { return new DeferredInputSocket(receiver.accept()); }
        @Override public int getLocalPort() { return receiver.getLocalPort(); }
        @Override public boolean isClosed() { return receiver.isClosed(); }
        @Override public void close() throws IOException { receiver.close(); }
    }

    private static final class DeferredInputSocket extends Socket {
        private final Socket receiver;
        private final InputStream lazy = new InputStream() {
            private InputStream input;

            private InputStream resolve() throws IOException {
                if (input == null) input = receiver.getInputStream();
                return input;
            }

            @Override public int read() throws IOException { return resolve().read(); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                return resolve().read(bytes, offset, length);
            }
            @Override public int available() throws IOException { return input == null ? 0 : input.available(); }
            @Override public void close() throws IOException { receiver.close(); }
        };

        DeferredInputSocket(Socket receiver) {
            this.receiver = receiver;
        }

        @Override public void setSoTimeout(int timeout) throws SocketException { receiver.setSoTimeout(timeout); }
        @Override public InputStream getInputStream() { return lazy; }
        @Override public OutputStream getOutputStream() throws IOException { return receiver.getOutputStream(); }
        @Override public InetAddress getInetAddress() { return receiver.getInetAddress(); }
        @Override public boolean isClosed() { return receiver.isClosed(); }
        @Override public void close() throws IOException { receiver.close(); }
    }

    static final class Head {
        final byte[] bytes;
        final int status;

        Head(byte[] bytes, int status) {
            this.bytes = bytes;
            this.status = status;
        }
    }
}
