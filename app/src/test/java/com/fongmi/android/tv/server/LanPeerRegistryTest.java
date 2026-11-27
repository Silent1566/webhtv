package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LanPeerRegistryTest {

    private long now;

    private LanPeerAuthorization kernel() {
        LanPeerAuthorization kernel = new LanPeerAuthorization(() -> now);
        kernel.setEnabled(true);
        return kernel;
    }

    private String pair(LanPeerAuthorization kernel, String uuid) {
        String credential = kernel.pair(uuid, kernel.issuePairingCode());
        assertNotNull(credential);
        return credential;
    }

    @Test
    public void restoredPartnersKeepWorkingWithTheirOriginalCredential() {
        LanPeerAuthorization first = kernel();
        String credential = pair(first, "phone-a");
        String encoded = LanPeerRegistry.encode(first.partners());

        LanPeerAuthorization second = kernel();
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, second.authorize("push", "phone-a", credential, false));
        second.restore(LanPeerRegistry.decode(encoded));
        assertEquals(LanPeerAuthorization.Decision.ALLOW, second.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void snapshotIsADefensiveCopy() {
        LanPeerAuthorization kernel = kernel();
        pair(kernel, "phone-a");
        Map<String, byte[]> snapshot = kernel.partners();
        snapshot.clear();
        assertEquals(1, kernel.partners().size());
    }

    @Test
    public void malformedPayloadsDecodeToAnEmptyRegistry() {
        for (String payload : new String[]{null, "", "   ", "not json", "[1,2,3]", "{\"devices\":\"nope\"}"}) {
            Map<String, byte[]> decoded = LanPeerRegistry.decode(payload);
            assertNotNull(payload, decoded);
            assertTrue("payload=" + payload, decoded.isEmpty());
        }
    }

    private static String registry(String... entries) {
        return "{\"version\":1,\"devices\":[" + String.join(",", entries) + "]}";
    }

    private static String entry(String id, String key) {
        return "{\"id\":\"" + id + "\",\"key\":\"" + key + "\"}";
    }

    @Test
    public void invalidEntriesAreDroppedButValidOnesSurvive() {
        String digest = "ab".repeat(32);
        String json = registry(
                entry("phone-a", digest),
                entry("phone-b", "zz"),
                entry("phone-c", "abab"),
                entry("bad id!", digest),
                entry("", digest),
                entry("0000000000000000", digest),
                entry("phone-a", "cd".repeat(32)));
        Map<String, byte[]> decoded = LanPeerRegistry.decode(json);
        assertEquals(1, decoded.size());
        assertTrue(decoded.containsKey("phone-a"));
        assertEquals(32, decoded.get("phone-a").length);
        assertEquals(digest, "ab".repeat(32));
    }

    @Test
    public void restoreRejectsAnOversizedRegistryAndDropsInvalidEntries() {
        LanPeerAuthorization kernel = kernel();
        String credential = pair(kernel, "phone-a");
        Map<String, byte[]> oversized = new HashMap<>();
        for (int i = 0; i < LanPeerRegistry.MAX_PARTNERS + 5; i++) oversized.put("device-" + i, new byte[32]);
        oversized.put("phone-a", kernel.partners().get("phone-a"));
        kernel.restore(oversized);
        assertTrue(kernel.partners().size() <= LanPeerRegistry.MAX_PARTNERS);
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, kernel.authorize("push", "phone-a", credential, false));

        kernel.restore(Map.of("phone-b", new byte[16]));
        assertFalse(kernel.partners().containsKey("phone-b"));
    }

    @Test
    public void encodeDropsNothingValidAndRoundTripsMultiplePartners() {
        LanPeerAuthorization kernel = kernel();
        pair(kernel, "phone-a");
        pair(kernel, "phone-b");
        String encoded = LanPeerRegistry.encode(kernel.partners());
        Map<String, byte[]> decoded = LanPeerRegistry.decode(encoded);
        assertEquals(2, decoded.size());
        assertTrue(decoded.containsKey("phone-a"));
        assertTrue(decoded.containsKey("phone-b"));
        assertEquals("", LanPeerRegistry.encode(Map.of()));
        assertTrue(LanPeerRegistry.decode(LanPeerRegistry.encode(Map.of())).isEmpty());
    }

    @Test
    public void malformedVersionsNeverLoadCredentialsOrThrow() {
        String devices = ",\"devices\":[" + entry("phone-a", "ab".repeat(32)) + "]}";
        for (String version : new String[]{"null", "{}", "[]", "true", "\"garbage\"", "\"1\"", "1.5", "4294967297", "2"}) {
            assertTrue(version, LanPeerRegistry.decode("{\"version\":" + version + devices).isEmpty());
        }
        assertTrue(LanPeerRegistry.decode("{\"devices\":[" + entry("phone-a", "ab".repeat(32)) + "]}").isEmpty());
    }

    @Test
    public void emptyAndOversizedRestoreClearPreviouslyAuthorizedPeers() {
        LanPeerAuthorization kernel = kernel();
        String credential = pair(kernel, "phone-a");
        kernel.restore(Map.of());
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, kernel.authorize("push", "phone-a", credential, false));
        pair(kernel, "phone-a");
        kernel.restore(null);
        assertTrue(kernel.partners().isEmpty());
    }

    @Test
    public void serializationCannotWriteAnUnloadableOversizedRegistry() {
        Map<String, byte[]> entries = new HashMap<>();
        for (int i = 0; i < 65; i++) entries.put("phone-" + i, new byte[32]);
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> LanPeerRegistry.encode(entries));
    }

    @Test
    public void importedAndExportedDigestArraysAreDefensiveCopies() {
        LanPeerAuthorization kernel = kernel();
        String credential = pair(kernel, "phone-a");
        Map<String, byte[]> exported = kernel.partners();
        kernel.restore(exported);
        java.util.Arrays.fill(exported.get("phone-a"), (byte) 0);
        assertEquals(LanPeerAuthorization.Decision.ALLOW, kernel.authorize("push", "phone-a", credential, false));
        java.util.Arrays.fill(kernel.partners().get("phone-a"), (byte) 0);
        assertEquals(LanPeerAuthorization.Decision.ALLOW, kernel.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void oversizedPlaintextIsRejectedBeforeParsing() {
        String payload = "{\"version\":1,\"devices\":[" + entry("phone-a", "ab".repeat(32)) + "],\"padding\":\"" + "x".repeat(100_000) + "\"}";
        assertTrue(LanPeerRegistry.decode(payload).isEmpty());
    }

}
