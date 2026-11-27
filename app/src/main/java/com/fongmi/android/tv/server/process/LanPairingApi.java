package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.LanActionGate;
import com.fongmi.android.tv.server.LanPairingEndpoint;
import com.fongmi.android.tv.server.LanPeers;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * {@code POST /lan/pair} — exchanges the on-screen pairing code for this device's credential.
 *
 * <p>Deliberately the only unauthenticated write-shaped endpoint: it must be reachable before a
 * device has any credential. It is still gated by the master switch and by the one-time code.
 */
public class LanPairingApi implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return "/lan/pair".equals(url);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        String device = LanActionGate.header(session.getHeaders(), LanActionGate.HEADER_DEVICE);
        String code = params.get("code");
        SpiderDebug.log("lan", "pair attempt keys=%s", params.keySet());
        LanPairingEndpoint.Result result = LanPairingEndpoint.handle(LanPeers.get(), device, code);
        return Nano.error(Response.Status.lookup(result.status()), result.body());
    }
}
