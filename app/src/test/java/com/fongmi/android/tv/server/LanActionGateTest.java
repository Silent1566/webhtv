package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanActionGateTest {

    @Test
    public void everyDenialMapsToItsOwnStatusAndMessage() {
        assertEquals(200, LanActionGate.decide(LanPeerAuthorization.Decision.ALLOW).status());
        assertTrue(LanActionGate.decide(LanPeerAuthorization.Decision.ALLOW).allowed());

        LanActionGate.Verdict disabled = LanActionGate.decide(LanPeerAuthorization.Decision.DISABLED);
        assertEquals(403, disabled.status());
        assertEquals("LAN access disabled", disabled.message());

        LanActionGate.Verdict unauthorized = LanActionGate.decide(LanPeerAuthorization.Decision.UNAUTHORIZED);
        assertEquals(403, unauthorized.status());
        assertEquals("peer not authorized", unauthorized.message());

        LanActionGate.Verdict sensitive = LanActionGate.decide(LanPeerAuthorization.Decision.SENSITIVE_DISABLED);
        assertEquals(403, sensitive.status());
        assertEquals("local API write disabled", sensitive.message());

        LanActionGate.Verdict unknown = LanActionGate.decide(LanPeerAuthorization.Decision.UNKNOWN_ACTION);
        assertEquals(400, unknown.status());
        assertEquals("unknown action", unknown.message());
    }

    @Test
    public void onlyAllowProceeds() {
        for (LanPeerAuthorization.Decision decision : LanPeerAuthorization.Decision.values()) {
            assertEquals(decision.name(), decision == LanPeerAuthorization.Decision.ALLOW,
                    LanActionGate.decide(decision).allowed());
        }
    }

    @Test
    public void requestHeadersAreReadCaseInsensitivelyAndTrimmed() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-WebHTV-Device", "  phone-a ");
        headers.put("x-webhtv-credential", "abc");
        assertEquals("phone-a", LanActionGate.header(headers, LanActionGate.HEADER_DEVICE));
        assertEquals("abc", LanActionGate.header(headers, LanActionGate.HEADER_CREDENTIAL));
        assertEquals("", LanActionGate.header(headers, "x-missing"));
        assertEquals("", LanActionGate.header(null, LanActionGate.HEADER_DEVICE));
    }

    @Test
    public void blankActionMeansNoActionAndIsNotAnError() {
        assertFalse(LanActionGate.hasAction(null));
        assertFalse(LanActionGate.hasAction(""));
        assertFalse(LanActionGate.hasAction("   "));
        assertTrue(LanActionGate.hasAction("push"));
    }
}
