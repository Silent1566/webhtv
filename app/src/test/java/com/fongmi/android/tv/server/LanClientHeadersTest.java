package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanClientHeadersTest {

    @Test
    public void identityAndCredentialAreBothSentWhenPaired() {
        Map<String, String> headers = LanClientHeaders.of("phone-a", "credential-a");
        assertEquals("phone-a", headers.get(LanActionGate.HEADER_DEVICE));
        assertEquals("credential-a", headers.get(LanActionGate.HEADER_CREDENTIAL));
        assertEquals(2, headers.size());
    }

    @Test
    public void anUnpairedDeviceStillIdentifiesItselfSoTheTargetCanRefuseClearly() {
        Map<String, String> headers = LanClientHeaders.of("phone-a", "");
        assertEquals("phone-a", headers.get(LanActionGate.HEADER_DEVICE));
        assertFalse(headers.containsKey(LanActionGate.HEADER_CREDENTIAL));
    }

    @Test
    public void withoutADeviceIdNothingIsSent() {
        assertTrue(LanClientHeaders.of("", "credential-a").isEmpty());
        assertTrue(LanClientHeaders.of(null, "credential-a").isEmpty());
        assertTrue(LanClientHeaders.of("   ", "credential-a").isEmpty());
    }

    @Test
    public void valuesAreTrimmed() {
        Map<String, String> headers = LanClientHeaders.of(" phone-a ", " credential-a ");
        assertEquals("phone-a", headers.get(LanActionGate.HEADER_DEVICE));
        assertEquals("credential-a", headers.get(LanActionGate.HEADER_CREDENTIAL));
    }
}
