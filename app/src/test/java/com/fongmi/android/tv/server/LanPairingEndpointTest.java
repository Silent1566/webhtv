package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LanPairingEndpointTest {

    private static final class Memory implements LanPeerPersist.SecretBox, LanPeerPersist.ValueStore, LanPairingController.Switch {

        private String sealed = "";
        private boolean enabled;

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
            return sealed;
        }

        @Override
        public void write(String value) {
            sealed = value;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean value) {
            enabled = value;
        }
    }

    private LanPairingController controller(Memory memory) {
        return new LanPairingController(new LanPeerAuthorization(() -> 0), new LanPeerPersist(memory, memory), memory, () -> {
        });
    }

    @Test
    public void pairingIsRefusedWhileLanControlIsOff() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        LanPairingEndpoint.Result result = LanPairingEndpoint.handle(controller, "phone-a", "12345678");
        assertEquals(403, result.status());
        assertEquals("LAN access disabled", result.body());
    }

    @Test
    public void missingDeviceOrCodeIsABadRequest() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        assertEquals(400, LanPairingEndpoint.handle(controller, "", controller.issuePairingCode()).status());
        assertEquals(400, LanPairingEndpoint.handle(controller, "phone-a", "  ").status());
        assertEquals(400, LanPairingEndpoint.handle(controller, "phone-a", null).status());
    }

    @Test
    public void aValidCodeHandsOutOneCredentialThatImmediatelyAuthorizesTheDevice() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String code = controller.issuePairingCode();
        LanPairingEndpoint.Result result = LanPairingEndpoint.handle(controller, "phone-a", code);
        assertEquals(200, result.status());
        assertFalse(result.body().isEmpty());
        assertEquals(LanPeerAuthorization.Decision.ALLOW,
                controller.authorize("push", "phone-a", result.body(), false));
    }

    @Test
    public void theSameCodeCannotPairASecondDevice() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String code = controller.issuePairingCode();
        assertNotNull(LanPairingEndpoint.handle(controller, "phone-a", code));
        LanPairingEndpoint.Result second = LanPairingEndpoint.handle(controller, "phone-b", code);
        assertEquals(403, second.status());
        assertEquals("pairing code rejected", second.body());
    }

    @Test
    public void aWrongCodeIsRejectedWithoutIssuingACredential() {
        Memory memory = new Memory();
        LanPairingController controller = controller(memory);
        controller.setEnabled(true);
        String code = controller.issuePairingCode();
        String wrong = code.equals("00000000") ? "11111111" : "00000000";
        LanPairingEndpoint.Result result = LanPairingEndpoint.handle(controller, "phone-a", wrong);
        assertEquals(403, result.status());
        assertTrue(controller.partners().isEmpty());
    }
}
