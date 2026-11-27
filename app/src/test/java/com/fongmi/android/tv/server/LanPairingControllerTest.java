package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LanPairingControllerTest {

    private static final class Memory implements LanPeerPersist.SecretBox, LanPeerPersist.ValueStore, LanPairingController.Switch {

        private String sealedRegistry = "";
        private boolean enabled;
        private int changes;
        private boolean failWrites;
        private boolean failRead;
        private boolean failOpen;
        private boolean failToggle;
        private boolean failInhibit;
        private boolean inhibited;

        public boolean isInhibited() { return inhibited; }

        public void inhibit() throws java.io.IOException {
            if (failInhibit) throw new java.io.IOException("journal unavailable");
            inhibited = true;
        }

        public void clearInhibit() { inhibited = false; }

        @Override
        public String seal(String plain) {
            return "s:" + java.util.Base64.getEncoder().encodeToString(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String open(String value) {
            if (failOpen) throw new IllegalStateException("key unavailable");
            return new String(java.util.Base64.getDecoder().decode(value.substring(2)), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String read() {
            if (failRead) throw new IllegalStateException("read unavailable");
            return sealedRegistry;
        }

        @Override
        public void write(String value) throws Exception {
            if (failWrites) throw new java.io.IOException("disk full");
            sealedRegistry = value;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean value) {
            if (failToggle) throw new IllegalStateException("toggle write failed");
            enabled = value;
        }

        int changes() {
            return changes;
        }

        void changed() {
            changes++;
        }
    }

    private LanPairingController controller(Memory memory) {
        return new LanPairingController(new LanPeerAuthorization(() -> 0), new LanPeerPersist(memory, memory), memory, memory::changed);
    }

    @Test
    public void lanControlStaysDisabledUntilTheUserEnablesIt() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        assertFalse(controller.isEnabled());
        assertNull(controller.issuePairingCode());
        assertNull(controller.pair("phone-a", "12345678"));
        controller.setEnabled(true);
        assertTrue(controller.isEnabled());
        assertTrue(memory.isEnabled());
        assertNotNull(controller.issuePairingCode());
        assertEquals(1, memory.changes());
    }

    @Test
    public void pairingImmediatelyPersistsTheNewDevice() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String credential = controller.pair("phone-a", controller.issuePairingCode());
        assertNotNull(credential);
        assertFalse(memory.read().isEmpty());

        LanPairingController restarted = controller(memory);
        assertTrue(restarted.isEnabled());
        assertEquals(LanPeerAuthorization.Decision.ALLOW, restarted.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void revocationPersistsToo() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String credential = controller.pair("phone-a", controller.issuePairingCode());
        assertNotNull(credential);
        assertTrue(controller.revoke("phone-a"));
        assertFalse(controller.revoke("phone-a"));

        LanPairingController restarted = controller(memory);
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, restarted.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void disablingKeepsPairedDevicesButBlocksThem() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String credential = controller.pair("phone-a", controller.issuePairingCode());
        controller.setEnabled(false);
        assertEquals(LanPeerAuthorization.Decision.DISABLED, controller.authorize("push", "phone-a", credential, false));
        controller.setEnabled(true);
        assertEquals(LanPeerAuthorization.Decision.ALLOW, controller.authorize("push", "phone-a", credential, false));
        assertEquals(1, controller.partners().size());
    }

    @Test
    public void partnerListExposesOnlyDeviceIdentifiers() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        controller.pair("phone-a", controller.issuePairingCode());
        controller.pair("phone-b", controller.issuePairingCode());
        Map<String, byte[]> partners = controller.partners();
        assertEquals(2, partners.size());
        assertTrue(partners.containsKey("phone-a"));
        assertTrue(memory.read().indexOf("phone-a") < 0);
    }

    @Test
    public void aFailedPairingAttemptRegistersAndPersistsNothing() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String code = controller.issuePairingCode();
        assertNull(controller.pair("phone-a", wrong(code)));
        assertTrue(controller.partners().isEmpty());
    }

    private static String wrong(String code) {
        return code.equals("00000000") ? "11111111" : "00000000";
    }

    @Test
    public void failedPairPublishesNothingAndConsumesItsCode() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String code = controller.issuePairingCode();
        memory.failWrites = true;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> controller.pair("phone-a", code));
        assertTrue(controller.partners().isEmpty());
        assertEquals("", memory.read());
        memory.failWrites = false;
        assertNull(controller.pair("phone-a", code));
    }

    @Test
    public void failedRePairPreservesThePreviouslyPersistedCredential() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String old = controller.pair("phone-a", controller.issuePairingCode());
        memory.failWrites = true;
        org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> controller.pair("phone-a", controller.issuePairingCode()));
        assertEquals(LanPeerAuthorization.Decision.ALLOW, controller.authorize("push", "phone-a", old, false));
    }

    @Test
    public void failedRevokeDisablesReceptionDurablyAndReportsFailure() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String credential = controller.pair("phone-a", controller.issuePairingCode());
        memory.failWrites = true;
        IllegalStateException failure = org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> controller.revoke("phone-a"));
        assertEquals("disk full", failure.getCause().getMessage());
        assertFalse(controller.isEnabled());
        assertFalse(memory.enabled);
        assertEquals(LanPeerAuthorization.Decision.DISABLED,
                controller(memory).authorize("push", "phone-a", credential, false));
    }

    @Test
    public void revokeStillDisablesMemoryWhenEmergencyMasterOffCannotPersist() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        controller.pair("phone-a", controller.issuePairingCode());
        memory.failWrites = true;
        memory.failToggle = true;
        IllegalStateException failure = org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> controller.revoke("phone-a"));
        assertEquals("disk full", failure.getCause().getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertFalse(controller.isEnabled());
    }

    @Test
    public void failedMasterOffStillClosesTheInMemoryGate() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        memory.failToggle = true;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> controller.setEnabled(false));
        assertFalse(controller.isEnabled());
    }


    @Test
    public void failedRegistryAndSwitchWritesDoNotResurrectAuthorizationAfterRestart() {
        Memory memory = new Memory();
        LanPairingController live = controller(memory);
        live.setEnabled(true);
        String credential = live.pair("phone-a", live.issuePairingCode());
        memory.failWrites = true;
        memory.failToggle = true;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> live.revoke("phone-a"));
        assertTrue("old enabled preference survives the failure", memory.isEnabled());
        assertFalse("old registry survives the failure", memory.read().isEmpty());
        LanPairingController restarted = controller(memory);
        assertFalse(restarted.isEnabled());
        assertNull(restarted.issuePairingCode());
        assertEquals(LanPeerAuthorization.Decision.DISABLED, restarted.authorize("push", "phone-a", credential, false));
        assertTrue(restarted.partners().isEmpty());

        memory.failToggle = false;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> restarted.setEnabled(true));
        assertFalse(restarted.isEnabled());
        memory.failWrites = false;
        restarted.setEnabled(true);
        LanPairingController recovered = controller(memory);
        assertTrue(recovered.isEnabled());
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, recovered.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void failedDisableStaysDisabledAfterRestartWithoutForgettingAfterSuccessfulDisable() {
        Memory memory = new Memory();
        LanPairingController live = controller(memory);
        live.setEnabled(true);
        String credential = live.pair("phone-a", live.issuePairingCode());
        live.setEnabled(false);
        LanPairingController restarted = controller(memory);
        restarted.setEnabled(true);
        assertEquals(LanPeerAuthorization.Decision.ALLOW, restarted.authorize("push", "phone-a", credential, false));
        memory.failToggle = true;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> restarted.setEnabled(false));
        assertFalse(controller(memory).isEnabled());
    }

    @Test
    public void unavailableInterlockReportsFailureAndStopsLivePairing() {
        Memory memory = new Memory();
        LanPairingController live = controller(memory);
        live.setEnabled(true);
        live.pair("phone-a", live.issuePairingCode());
        memory.failInhibit = true;
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> live.revoke("phone-a"));
        assertFalse(live.isEnabled());
        assertNull(live.issuePairingCode());
        assertFalse(controller(memory).isEnabled());
    }

    @Test
    public void unreadableRegistryBlocksCodesAndPairingUntilExplicitRecovery() {
        for (boolean readFailure : new boolean[]{true, false}) {
            Memory memory = new Memory();
            LanPairingController first = controller(memory);
            first.setEnabled(true);
            String credential = first.pair("phone-a", first.issuePairingCode());
            String original = memory.sealedRegistry;
            memory.failRead = readFailure;
            memory.failOpen = !readFailure;
            LanPairingController unreadable = controller(memory);
            assertFalse(unreadable.isEnabled());
            assertTrue(unreadable.partners().isEmpty());
            assertNull(unreadable.issuePairingCode());
            assertNull(unreadable.pair("phone-b", "12345678"));
            assertEquals("failed load must not overwrite ciphertext", original, memory.sealedRegistry);
            assertTrue(memory.enabled);

            memory.failRead = false;
            memory.failOpen = false;
            unreadable.setEnabled(true);
            assertEquals("", memory.sealedRegistry);
            LanPairingController recovered = controller(memory);
            assertTrue(recovered.isEnabled());
            assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, recovered.authorize("push", "phone-a", credential, false));
            assertNotNull(recovered.issuePairingCode());
        }
    }

    @Test
    public void corruptedRegistryPreservesOriginalBytesUntilExplicitRecovery() {
        Memory memory = new Memory();
        memory.enabled = true;
        memory.sealedRegistry = "corrupt-ciphertext";
        LanPairingController corrupted = controller(memory);
        assertFalse(corrupted.isEnabled());
        assertNull(corrupted.issuePairingCode());
        assertEquals("corrupt-ciphertext", memory.sealedRegistry);
    }
}
