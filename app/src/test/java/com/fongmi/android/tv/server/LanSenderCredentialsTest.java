package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanSenderCredentialsTest {

    private static final class Memory implements LanPeerPersist.SecretBox, LanPeerPersist.ValueStore {

        private volatile String sealed = "";
        private boolean failWrites;
        private boolean failSeal;
        private boolean failReads;

        @Override
        public String seal(String plain) throws Exception {
            if (failSeal) throw new java.io.IOException("seal failed");
            return "s:" + java.util.Base64.getEncoder().encodeToString(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String open(String value) {
            return new String(java.util.Base64.getDecoder().decode(value.substring(2)), java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String read() {
            if (failReads) throw new IllegalStateException("read failed");
            return sealed;
        }

        @Override
        public void write(String value) throws Exception {
            if (failWrites) throw new java.io.IOException("disk full");
            sealed = value;
        }
    }

    @Test
    public void aCredentialIsKeptPerTargetDeviceOnly() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "A".repeat(43));
        credentials.put("tv-b", "B".repeat(42) + "A");
        assertEquals("A".repeat(43), credentials.get("tv-a"));
        assertEquals("B".repeat(42) + "A", credentials.get("tv-b"));
        assertEquals("", credentials.get("tv-c"));
    }

    @Test
    public void credentialsSurviveARestartAndAreNotStoredInClearText() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials first = new LanSenderCredentials(memory, memory);
        first.put("tv-a", "A".repeat(43));
        assertFalse(memory.read().contains("A".repeat(43)));

        LanSenderCredentials restarted = new LanSenderCredentials(memory, memory);
        assertEquals("A".repeat(43), restarted.get("tv-a"));
    }

    @Test
    public void removingATargetForgetsItImmediately() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "A".repeat(43));
        credentials.remove("tv-a");
        assertEquals("", credentials.get("tv-a"));
        assertEquals("", new LanSenderCredentials(memory, memory).get("tv-a"));
    }

    @Test
    public void corruptOrBlankStorageYieldsNoCredentials() throws Exception {
        Memory memory = new Memory();
        assertTrue(new LanSenderCredentials(memory, memory).isEmpty());
        memory.write("not-cipher");
        LanSenderCredentials broken = new LanSenderCredentials(memory, memory);
        assertTrue(broken.isEmpty());
        assertEquals("", broken.get("tv-a"));
    }

    @Test
    public void blankIdentifiersAreIgnored() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("", "A".repeat(43));
        credentials.put("tv-a", "");
        assertTrue(credentials.isEmpty());
    }

    @Test
    public void failedPutDoesNotPublishOrReplaceAnyCredential() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "A".repeat(43));
        memory.failWrites = true;
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> credentials.put("tv-a", "B".repeat(42) + "A"));
        assertEquals("A".repeat(43), credentials.get("tv-a"));
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> credentials.put("tv-b", "A".repeat(43)));
        assertEquals("", credentials.get("tv-b"));
        memory.failWrites = false;
        memory.failSeal = true;
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> credentials.put("tv-b", "A".repeat(43)));
        assertEquals("", credentials.get("tv-b"));
    }

    @Test
    public void failedRemovalRetainsStateSoItCanBeRetried() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "A".repeat(43));
        memory.failWrites = true;
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> credentials.remove("tv-a"));
        assertEquals("A".repeat(43), credentials.get("tv-a"));
        memory.failWrites = false;
        credentials.remove("tv-a");
        assertEquals("", new LanSenderCredentials(memory, memory).get("tv-a"));
    }

    @Test
    public void admissionCapsAndStrictValuesKeepTheSavedRegistryReloadable() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        for (int i = 0; i < 64; i++) credentials.put("tv-" + i, "A".repeat(43));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> credentials.put("overflow", "A".repeat(43)));
        credentials.put("tv-0", "B".repeat(42) + "A");
        assertEquals("B".repeat(42) + "A", new LanSenderCredentials(memory, memory).get("tv-0"));
        for (String id : new String[]{"bad id", "0000000000000000", "x".repeat(129)}) {
            org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> credentials.put(id, "A".repeat(43)));
        }
        for (String value : new String[]{"short", "!".repeat(43), "A".repeat(44), "A".repeat(42) + "B"}) {
            org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> credentials.put("tv-0", value));
        }
    }

    @Test
    public void decodingRequiresAnExactSupportedVersionAndBoundedValues() throws Exception {
        Memory memory = new Memory();
        String target = ",\"targets\":[{\"id\":\"tv-a\",\"credential\":\"" + "A".repeat(43) + "\"}]}";
        for (String version : new String[]{"null", "{}", "true", "\"1\"", "1.5", "4294967297", "2"}) {
            memory.sealed = memory.seal("{\"version\":" + version + target);
            assertTrue(version, new LanSenderCredentials(memory, memory).isEmpty());
        }
        memory.sealed = memory.seal("{\"targets\":[{\"id\":\"tv-a\",\"credential\":\"" + "A".repeat(43) + "\"}]}");
        assertTrue(new LanSenderCredentials(memory, memory).isEmpty());
        memory.sealed = memory.seal("{\"version\":1,\"targets\":[{\"id\":\"tv-a\",\"credential\":\"" + "x".repeat(100_000) + "\"}]}");
        assertTrue(new LanSenderCredentials(memory, memory).isEmpty());
    }

    @Test
    public void failedStorageReadStartsWithNoCredentials() {
        Memory memory = new Memory();
        memory.failReads = true;
        assertTrue(new LanSenderCredentials(memory, memory).isEmpty());
    }

    @Test
    public void concurrentPutCannotOverwriteALaterSuccessfulRemoval() throws Exception {
        Memory memory = new Memory();
        java.util.concurrent.CountDownLatch sealing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        LanPeerPersist.SecretBox box = new LanPeerPersist.SecretBox() {
            public String seal(String plain) throws Exception {
                sealing.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("seal blocked");
                return memory.seal(plain);
            }
            public String open(String sealed) { return memory.open(sealed); }
        };
        LanSenderCredentials credentials = new LanSenderCredentials(box, memory);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> put = pool.submit(() -> { credentials.put("tv-a", "A".repeat(43)); return null; });
            assertTrue(sealing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.CountDownLatch removing = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Future<?> remove = pool.submit(() -> { removing.countDown(); credentials.remove("tv-a"); return null; });
            assertTrue(removing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            boolean completedWhileSealing;
            try { remove.get(200, java.util.concurrent.TimeUnit.MILLISECONDS); completedWhileSealing = true; }
            catch (java.util.concurrent.TimeoutException expected) { completedWhileSealing = false; }
            release.countDown();
            put.get(5, java.util.concurrent.TimeUnit.SECONDS);
            remove.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse("remove must serialize behind the uncommitted put", completedWhileSealing);
            assertEquals("", credentials.get("tv-a"));
            assertEquals("", new LanSenderCredentials(memory, memory).get("tv-a"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    public void fingerprintsAndCredentialsCommitTogetherAndLegacyWritesDoNotAdoptPins() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "A".repeat(43), "ab".repeat(32));
        LanSenderCredentials restarted = new LanSenderCredentials(memory, memory);
        assertEquals("A".repeat(43), restarted.get("tv-a"));
        assertEquals("ab".repeat(32), restarted.fingerprint("tv-a"));
        memory.failWrites = true;
        org.junit.Assert.assertThrows(java.io.IOException.class,
                () -> credentials.put("tv-a", "B".repeat(42) + "A", "cd".repeat(32)));
        assertEquals("A".repeat(43), credentials.get("tv-a"));
        assertEquals("ab".repeat(32), credentials.fingerprint("tv-a"));
        memory.failWrites = false;
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> credentials.put("tv-a", "A".repeat(43), "not-a-pin"));
        credentials.put("tv-a", "A".repeat(43));
        assertEquals("", credentials.fingerprint("tv-a"));
        assertEquals("", new LanSenderCredentials(memory, memory).fingerprint("tv-a"));
        credentials.remove("tv-a");
        assertEquals("", credentials.fingerprint("tv-a"));
    }

    @Test
    public void oversizedSenderCiphertextIsRejectedBeforeDecrypting() {
        Memory memory = new Memory();
        memory.sealed = "x".repeat(100_000);
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        LanPeerPersist.SecretBox box = new LanPeerPersist.SecretBox() {
            public String seal(String plain) { return plain; }
            public String open(String value) { opened.incrementAndGet(); return ""; }
        };
        assertTrue(new LanSenderCredentials(box, memory).isEmpty());
        assertEquals(0, opened.get());
    }
}
