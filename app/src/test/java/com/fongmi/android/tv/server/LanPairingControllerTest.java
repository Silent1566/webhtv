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

        @Override
        public String seal(String plain) {
            return "s:" + java.util.Base64.getEncoder().encodeToString(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String open(String value) {
            return new String(java.util.Base64.getDecoder().decode(value.substring(2)), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String read() {
            return sealedRegistry;
        }

        @Override
        public void write(String value) {
            sealedRegistry = value;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean value) {
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
}