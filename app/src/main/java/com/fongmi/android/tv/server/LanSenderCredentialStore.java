package com.fongmi.android.tv.server;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;

/**
 * Storage for the credentials this device presents to targets it has paired with.
 *
 * <p>Reuses the LAN key material of {@link LanPeerStore} (alias {@code webhtv_lan_peer} and its
 * {@code local-v1:} fallback) in a separate preference namespace, so a third AES/GCM copy is not
 * needed while cloud account tokens keep their own, separate key.
 */
public final class LanSenderCredentialStore {

    private static final String PREF = "lan_sender_credentials";
    private static final String KEY = "credentials";

    private static volatile LanSenderCredentials credentials;

    private LanSenderCredentialStore() {
    }

    public static LanSenderCredentials get() {
        LanSenderCredentials current = credentials;
        if (current != null) return current;
        synchronized (LanSenderCredentialStore.class) {
            if (credentials == null) credentials = create();
            return credentials;
        }
    }

    /** Empty when this device has not paired with the target yet. */
    public static String credentialFor(String target) {
        return get().get(target);
    }

    public static String fingerprintFor(String target) {
        return get().fingerprint(target);
    }

    /** Test seam: drops the cache so the next call reloads from storage. */
    static void reset() {
        synchronized (LanSenderCredentialStore.class) {
            credentials = null;
        }
    }

    private static LanSenderCredentials create() {
        LanPeerPersist.SecretBox box = new LanPeerPersist.SecretBox() {
            @Override
            public String seal(String plain) throws Exception {
                return LanPeerStore.encrypt(plain);
            }

            @Override
            public String open(String sealed) throws Exception {
                return LanPeerStore.decrypt(sealed);
            }
        };
        LanPeerPersist.ValueStore store = new LanPeerPersist.ValueStore() {
            @Override
            public String read() {
                return prefs().getString(KEY, "");
            }

            @Override
            public void write(String value) {
                if (!prefs().edit().putString(KEY, value).commit()) throw new IllegalStateException("Could not persist LAN sender credentials");
            }
        };
        return new LanSenderCredentials(box, store);
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
