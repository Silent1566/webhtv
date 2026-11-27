package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LanPeerPersistTest {

    /** Records what actually lands in storage and can be told to fail on open. */
    private static final class MemoryBox implements LanPeerPersist.SecretBox, LanPeerPersist.ValueStore {

        private String stored = "";
        private boolean broken;

        @Override
        public String seal(String plain) {
            return "sealed:" + java.util.Base64.getEncoder().encodeToString(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String open(String sealed) throws Exception {
            if (broken) throw new IllegalStateException("keystore unavailable");
            if (sealed == null || !sealed.startsWith("sealed:")) throw new IllegalStateException("corrupt value");
            return new String(java.util.Base64.getDecoder().decode(sealed.substring(7)), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String read() {
            return stored;
        }

        @Override
        public void write(String value) {
            stored = value;
        }
    }

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
    public void pairedDevicesSurviveARestartThroughSealedStorage() throws Exception {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, box);
        LanPeerAuthorization first = kernel();
        String credential = pair(first, "phone-a");
        persist.save(first.partners());

        LanPeerAuthorization afterRestart = kernel();
        afterRestart.restore(persist.load());
        assertEquals(LanPeerAuthorization.Decision.ALLOW, afterRestart.authorize("push", "phone-a", credential, false));
    }

    @Test
    public void theStoredFormIsNotThePlainRegistry() throws Exception {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, box);
        LanPeerAuthorization kernel = kernel();
        pair(kernel, "phone-a");
        persist.save(kernel.partners());
        assertFalse(box.stored.contains("phone-a"));
        assertTrue(box.stored.startsWith("sealed:"));
    }

    @Test
    public void unreadableStorageYieldsAnEmptyRegistryWithoutThrowing() throws Exception {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, box);
        LanPeerAuthorization kernel = kernel();
        pair(kernel, "phone-a");
        persist.save(kernel.partners());
        box.broken = true;
        assertTrue(persist.load().isEmpty());
        box.broken = false;
        assertFalse(persist.load().isEmpty());
    }

    @Test
    public void savingAnEmptyRegistryClearsStorage() throws Exception {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, box);
        LanPeerAuthorization kernel = kernel();
        pair(kernel, "phone-a");
        persist.save(kernel.partners());
        assertFalse(box.stored.isEmpty());
        persist.save(new HashMap<>());
        assertEquals("", box.stored);
        assertTrue(persist.load().isEmpty());
    }

    @Test
    public void blankOrGarbageStorageLoadsAsEmptyOrThrowsFree() throws Exception {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, box);
        assertTrue(persist.load().isEmpty());
        box.stored = "sealed:12:999";
        Map<String, byte[]> loaded = persist.load();
        assertTrue(loaded.isEmpty());
    }

    @Test
    public void valueStoreReadFailureLoadsAsEmpty() {
        MemoryBox box = new MemoryBox();
        LanPeerPersist persist = new LanPeerPersist(box, new LanPeerPersist.ValueStore() {
            public String read() { throw new IllegalStateException("read failed"); }
            public void write(String value) { }
        });
        assertTrue(persist.load().isEmpty());
    }

    @Test
    public void oversizedCiphertextIsRejectedBeforeOpeningIt() {
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        LanPeerPersist.SecretBox box = new LanPeerPersist.SecretBox() {
            public String seal(String plain) { return plain; }
            public String open(String sealed) { opened.incrementAndGet(); return ""; }
        };
        LanPeerPersist.ValueStore store = new LanPeerPersist.ValueStore() {
            public String read() { return "x".repeat(100_000); }
            public void write(String value) { }
        };
        assertTrue(new LanPeerPersist(box, store).load().isEmpty());
        assertEquals(0, opened.get());
    }

}
