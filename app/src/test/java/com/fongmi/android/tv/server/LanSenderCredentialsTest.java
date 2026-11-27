package com.fongmi.android.tv.server;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanSenderCredentialsTest {

    private static final class Memory implements LanPeerPersist.SecretBox, LanPeerPersist.ValueStore {

        private String sealed = "";

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
    }

    @Test
    public void aCredentialIsKeptPerTargetDeviceOnly() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "credential-a");
        credentials.put("tv-b", "credential-b");
        assertEquals("credential-a", credentials.get("tv-a"));
        assertEquals("credential-b", credentials.get("tv-b"));
        assertEquals("", credentials.get("tv-c"));
    }

    @Test
    public void credentialsSurviveARestartAndAreNotStoredInClearText() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials first = new LanSenderCredentials(memory, memory);
        first.put("tv-a", "credential-a");
        assertFalse(memory.read().contains("credential-a"));

        LanSenderCredentials restarted = new LanSenderCredentials(memory, memory);
        assertEquals("credential-a", restarted.get("tv-a"));
    }

    @Test
    public void removingATargetForgetsItImmediately() throws Exception {
        Memory memory = new Memory();
        LanSenderCredentials credentials = new LanSenderCredentials(memory, memory);
        credentials.put("tv-a", "credential-a");
        credentials.remove("tv-a");
        assertEquals("", credentials.get("tv-a"));
        assertEquals("", new LanSenderCredentials(memory, memory).get("tv-a"));
    }

    @Test
    public void corruptOrBlankStorageYieldsNoCredentials() {
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
        credentials.put("", "credential-a");
        credentials.put("tv-a", "");
        assertTrue(credentials.isEmpty());
    }
}
