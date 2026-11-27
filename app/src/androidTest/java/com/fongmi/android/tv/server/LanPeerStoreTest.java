package com.fongmi.android.tv.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/** Device test for the real Keystore-backed registry store. */
@RunWith(AndroidJUnit4.class)
public class LanPeerStoreTest {

    private LanPeerAuthorization kernel;

    @Before
    public void setUp() {
        LanPeerStore.clear();
        kernel = new LanPeerAuthorization(() -> 0);
        kernel.setEnabled(true);
    }

    @After
    public void tearDown() {
        LanPeerStore.clear();
    }

    private String pair(LanPeerAuthorization target, String uuid) {
        String credential = target.pair(uuid, target.issuePairingCode());
        assertNotNull(credential);
        return credential;
    }

    @Test
    public void pairedDeviceSurvivesThroughTheRealKeystore() throws Exception {
        String credential = pair(kernel, "phone-a");
        LanPeerStore.save(kernel.partners());

        String stored = LanPeerStore.inspect();
        assertFalse(stored.isEmpty());
        assertFalse("stored value must not contain the device id", stored.contains("phone-a"));

        LanPeerAuthorization restarted = new LanPeerAuthorization(() -> 0);
        restarted.setEnabled(true);
        restarted.restore(LanPeerStore.load());
        assertEquals(LanPeerAuthorization.Decision.ALLOW, restarted.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void corruptStoredValueLoadsAsEmptyWithoutThrowing() throws Exception {
        pair(kernel, "phone-a");
        LanPeerStore.save(kernel.partners());
        LanPeerStore.overwrite("corrupted-not-ciphertext");
        assertTrue(LanPeerStore.load().isEmpty());
        LanPeerStore.clear();
        assertTrue(LanPeerStore.load().isEmpty());
    }

    @Test
    public void savingAnEmptyRegistryClearsTheStoredValue() throws Exception {
        pair(kernel, "phone-a");
        LanPeerStore.save(kernel.partners());
        assertFalse(LanPeerStore.inspect().isEmpty());
        LanPeerStore.save(new HashMap<>());
        assertTrue(LanPeerStore.inspect().isEmpty());
        assertTrue(LanPeerStore.load().isEmpty());
    }

    @Test
    public void revokedDeviceIsGoneAfterReload() throws Exception {
        String credential = pair(kernel, "phone-a");
        pair(kernel, "phone-b");
        LanPeerStore.save(kernel.partners());
        kernel.revoke("phone-a");
        LanPeerStore.save(kernel.partners());

        LanPeerAuthorization restarted = new LanPeerAuthorization(() -> 0);
        restarted.setEnabled(true);
        Map<String, byte[]> loaded = LanPeerStore.load();
        restarted.restore(loaded);
        assertEquals(1, loaded.size());
        assertEquals(LanPeerAuthorization.Decision.UNAUTHORIZED, restarted.authorize("push", "phone-a", credential, false));
    }
}
