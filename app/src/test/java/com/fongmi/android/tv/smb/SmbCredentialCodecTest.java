package com.fongmi.android.tv.smb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SmbCredentialCodecTest {

    @Test
    public void roundTripKeepsUsernamePasswordAndDomain() {
        SmbCredential source = SmbCredential.of("nas-user", "p@ss:w/rd", "WORKGROUP");
        SmbCredential restored = SmbCredentialCodec.decode(SmbCredentialCodec.encode(source));

        assertEquals("nas-user", restored.username);
        assertEquals("p@ss:w/rd", restored.password);
        assertEquals("WORKGROUP", restored.domain);
    }

    @Test
    public void roundTripToleratesMissingDomainAndEmptyPassword() {
        SmbCredential restored = SmbCredentialCodec.decode(SmbCredentialCodec.encode(SmbCredential.of("user", "", "")));

        assertEquals("user", restored.username);
        assertEquals("", restored.password);
        assertEquals("", restored.domain);
    }

    @Test
    public void decodeRejectsMissingOrCorruptPayloads() {
        assertNull(SmbCredentialCodec.decode(null));
        assertNull(SmbCredentialCodec.decode(""));
        assertNull(SmbCredentialCodec.decode("not json"));
        assertNull("a payload without a username is useless", SmbCredentialCodec.decode("{\"password\":\"pw\"}"));
    }

    @Test
    public void decodeTrimsUsernameAndIgnoresUnknownFields() {
        SmbCredential restored = SmbCredentialCodec.decode("{\"username\":\"  user  \",\"password\":\"pw\",\"domain\":\"\",\"port\":445}");

        assertEquals("user", restored.username);
        assertEquals("pw", restored.password);
    }

    @Test
    public void guestCredentialIsEmptyAndAnonymisesToString() {
        assertTrue(SmbCredential.of("", "", "").isEmpty());
        assertFalse(SmbCredential.of("user", "pw", "").isEmpty());

        String text = SmbCredential.of("user", "topsecret", "WORKGROUP").toString();
        assertFalse("toString must never leak the password into logs", text.contains("topsecret"));
        assertTrue("toString stays useful for diagnostics", text.contains("user") && text.contains("WORKGROUP"));
    }
}
