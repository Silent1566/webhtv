package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.fongmi.android.tv.server.LanPeerAuthorization.Decision.ALLOW;
import static com.fongmi.android.tv.server.LanPeerAuthorization.Decision.DISABLED;
import static com.fongmi.android.tv.server.LanPeerAuthorization.Decision.SENSITIVE_DISABLED;
import static com.fongmi.android.tv.server.LanPeerAuthorization.Decision.UNAUTHORIZED;
import static com.fongmi.android.tv.server.LanPeerAuthorization.Decision.UNKNOWN_ACTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LanPeerAuthorizationTest {

    private final AtomicLong elapsed = new AtomicLong();
    private final LanPeerAuthorization authorization = new LanPeerAuthorization(elapsed::get);

    @Test
    public void receivingAndPairingAreOffByDefault() {
        assertEquals(DISABLED, authorization.authorize("push", "phone-a", null, true));
        assertNull(authorization.issuePairingCode());
        assertNull(authorization.pair("phone-a", "12345678"));
    }

    @Test
    public void pairedDeviceMustProveItsOwnIdentityOnEveryAction() {
        authorization.setEnabled(true);
        String first = pair("phone-a");
        String second = pair("phone-b");
        assertEquals(43, first.length());
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-a", null, false));
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-b", first, false));
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-a", second, false));
        assertEquals(ALLOW, authorization.authorize("push", "phone-a", first, false));
    }

    @Test
    public void playbackActionsIncludingSearchAndDanmakuAreNotPublic() {
        authorization.setEnabled(true);
        String credential = pair("phone-a");
        for (String action : List.of("push", "cast", "control", "refresh", "search", "danmaku")) {
            assertEquals(action, UNAUTHORIZED, authorization.authorize(action, "phone-a", null, false));
            assertEquals(action, ALLOW, authorization.authorize(action, "phone-a", credential, false));
        }
    }

    @Test
    public void sensitiveActionsNeedBothPairingAndTheLocalPermission() {
        authorization.setEnabled(true);
        String credential = pair("phone-a");
        for (String action : List.of("file", "sync", "apk", "apk_url", "setting")) {
            assertEquals(action, UNAUTHORIZED, authorization.authorize(action, "phone-a", null, true));
            assertEquals(action, SENSITIVE_DISABLED, authorization.authorize(action, "phone-a", credential, false));
            assertEquals(action, ALLOW, authorization.authorize(action, "phone-a", credential, true));
        }
    }

    @Test
    public void invalidActionsAreRejectedAsBadRequests() {
        authorization.setEnabled(true);
        String credential = pair("phone-a");
        for (String unknown : List.of("", " push", "PUSH", "future_action")) {
            assertEquals(UNKNOWN_ACTION, authorization.authorize(unknown, "phone-a", credential, true));
        }
        assertEquals(UNKNOWN_ACTION, authorization.authorize(null, "phone-a", credential, true));
        assertEquals(400, UNKNOWN_ACTION.statusCode());
        for (LanPeerAuthorization.Decision denial : List.of(DISABLED, UNAUTHORIZED, SENSITIVE_DISABLED)) {
            assertEquals(403, denial.statusCode());
        }
    }

    @Test
    public void pairingCodeIsShortLivedAndIsNotADurableCredential() {
        authorization.setEnabled(true);
        String code = authorization.issuePairingCode();
        assertNotNull(code);
        assertTrue(code.matches("[0-9]{8}"));
        elapsed.set(119_999);
        assertNotNull(authorization.pair("phone-a", code));
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-a", code, false));
        assertNull(authorization.pair("phone-b", code));

        String nextCode = authorization.issuePairingCode();
        elapsed.addAndGet(120_000);
        assertNull(authorization.pair("phone-b", nextCode));
    }

    @Test
    public void fiveBadAttemptsInvalidateTheCode() {
        authorization.setEnabled(true);
        String code = authorization.issuePairingCode();
        String wrong = code.equals("00000000") ? "11111111" : "00000000";
        for (int i = 0; i < 5; i++) assertNull(authorization.pair("phone-a", wrong));
        assertNull(authorization.pair("phone-a", code));
        assertNotNull(pair("phone-a"));
    }

    @Test
    public void replacingThePairingCodeAndDisablingReceptionClearPendingCodes() {
        authorization.setEnabled(true);
        String previous = authorization.issuePairingCode();
        String current = authorization.issuePairingCode();
        assertNull(authorization.pair("phone-a", previous));
        authorization.setEnabled(false);
        authorization.setEnabled(true);
        assertNull(authorization.pair("phone-a", current));
    }

    @Test
    public void revocationAndRePairingDoNotAffectOtherDevices() {
        authorization.setEnabled(true);
        String old = pair("phone-a");
        String other = pair("phone-b");
        String replacement = pair("phone-a");
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-a", old, false));
        assertEquals(ALLOW, authorization.authorize("push", "phone-a", replacement, false));
        authorization.setEnabled(false);
        assertEquals(DISABLED, authorization.authorize("push", "phone-a", replacement, false));
        authorization.setEnabled(true);
        assertEquals(ALLOW, authorization.authorize("push", "phone-a", replacement, false));
        assertTrue(authorization.revoke("phone-a"));
        assertFalse(authorization.revoke("phone-a"));
        assertEquals(UNAUTHORIZED, authorization.authorize("push", "phone-a", replacement, false));
        assertEquals(ALLOW, authorization.authorize("push", "phone-b", other, false));
    }

    @Test
    public void malformedIdentityDoesNotCreateAuthorization() {
        authorization.setEnabled(true);
        String code = authorization.issuePairingCode();
        assertNull(authorization.pair(null, code));
        assertNull(authorization.pair("", code));
        assertNull(authorization.pair(" phone-a ", code));
        assertNull(authorization.pair("a".repeat(129), code));
        assertNotNull(authorization.pair("phone-a", code));
    }

    @Test
    public void sharedFallbackDeviceIdCannotPair() {
        authorization.setEnabled(true);
        String code = authorization.issuePairingCode();
        assertNull(authorization.pair("0000000000000000", code));
        assertNotNull(authorization.pair("phone-a", code));
    }

    @Test
    public void concurrentPairRequestsCanConsumeTheCodeOnlyOnce() throws Exception {
        authorization.setEnabled(true);
        String code = authorization.issuePairingCode();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> { start.await(); return authorization.pair("phone-a", code); });
            Future<String> second = executor.submit(() -> { start.await(); return authorization.pair("phone-b", code); });
            start.countDown();
            int accepted = (first.get(5, TimeUnit.SECONDS) == null ? 0 : 1)
                    + (second.get(5, TimeUnit.SECONDS) == null ? 0 : 1);
            assertEquals(1, accepted);
        } finally {
            executor.shutdownNow();
        }
    }

    private String pair(String uuid) {
        String credential = authorization.pair(uuid, authorization.issuePairingCode());
        assertNotNull(credential);
        return credential;
    }

    @Test
    public void admissionStopsAt64ButAllowsReplacingAnExistingPeer() {
        LanPeerAuthorization kernel = new LanPeerAuthorization(() -> 0);
        kernel.setEnabled(true);
        String first = "";
        for (int i = 0; i < 64; i++) {
            String credential = kernel.pair("phone-" + i, kernel.issuePairingCode());
            assertNotNull(credential);
            if (i == 0) first = credential;
        }
        assertNull(kernel.pair("phone-overflow", kernel.issuePairingCode()));
        assertEquals(64, kernel.partners().size());
        String replacement = kernel.pair("phone-0", kernel.issuePairingCode());
        assertNotNull(replacement);
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, kernel.authorize("push", "phone-0", first, false));
        assertEquals(LanPeerAuthorization.Decision.ALLOW, kernel.authorize("push", "phone-0", replacement, false));
    }

}
