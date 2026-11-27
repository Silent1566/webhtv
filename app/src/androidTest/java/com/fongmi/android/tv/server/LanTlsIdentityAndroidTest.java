package com.fongmi.android.tv.server;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class LanTlsIdentityAndroidTest {
    private static final String ALIAS = "webhtv_lan_tls_receiver";

    @Test public void receiverKeyCanSignThePrehashedTlsTranscript() throws Exception {
        LanTlsIdentity.load();
        KeyStore store = receiverStore();
        PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        assertNotNull(key);
        Signature signer = Signature.getInstance("NONEwithRSA");
        signer.initSign(key);
        signer.update(new byte[51]);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("NONEwithRSA");
        verifier.initVerify(store.getCertificate(ALIAS));
        verifier.update(new byte[51]);
        assertTrue(verifier.verify(signature));
    }

    @Test public void resetOnlyUnreleasedTlsFixture() throws Exception {
        assumeTrue("explicit fixture cleanup opt-in required", "5558-unreleased-tls-fixture".equals(
                InstrumentationRegistry.getArguments().getString("kn17TlsFixtureReset")));
        java.io.File fixture = new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "kn17-unreleased-tls-fixture");
        assertTrue("explicit test-owned package marker required", fixture.isFile());
        assumeTrue("run this test alone", (LanTlsIdentityAndroidTest.class.getName() + "#resetOnlyUnreleasedTlsFixture")
                .equals(InstrumentationRegistry.getArguments().getString("class")));
        KeyStore store = receiverStore();
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        assertTrue(!store.containsAlias(ALIAS));
        assertTrue("test-only marker consumed", fixture.delete());
    }

    private static KeyStore receiverStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
}
