package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.LanActionGate;
import com.fongmi.android.tv.server.LanPairingEndpoint;
import com.fongmi.android.tv.server.LanPeers;
import com.fongmi.android.tv.server.Nano;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

public final class LanPairingApi {
    public Response handle(IHTTPSession session) throws IOException {
        if (session.getMethod() != Method.POST) return Nano.error(Response.Status.METHOD_NOT_ALLOWED, "POST required");
        if (!LanPeers.get().isEnabled()) return Nano.error(Response.Status.FORBIDDEN, "LAN access disabled");
        if (!session.getParameters().isEmpty()) return Nano.error(Response.Status.BAD_REQUEST, "Query not allowed");
        if (!"text/plain".equalsIgnoreCase(session.getHeaders().get("content-type"))) {
            return Nano.error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "text/plain required");
        }
        if (!"8".equals(session.getHeaders().get("content-length"))) {
            return Nano.error(Response.Status.BAD_REQUEST, "Invalid pairing request");
        }
        String device = LanActionGate.header(session.getHeaders(), LanActionGate.HEADER_DEVICE);
        if (device.isEmpty()) return Nano.error(Response.Status.BAD_REQUEST, "Invalid pairing request");
        InputStream input = session.getInputStream();
        byte[] digits = new byte[8];
        for (int i = 0; i < digits.length; i++) {
            int digit = input.read();
            if (digit < '0' || digit > '9') return Nano.error(Response.Status.BAD_REQUEST, "Invalid pairing request");
            digits[i] = (byte) digit;
        }
        LanPairingEndpoint.Result result = LanPairingEndpoint.handle(LanPeers.get(), device, new String(digits, StandardCharsets.US_ASCII));
        Response.Status status = Response.Status.lookup(result.status());
        Response response = Nano.error(status == null ? Response.Status.INTERNAL_ERROR : status, result.body());
        response.addHeader("Cache-Control", "no-store");
        return response;
    }
}
