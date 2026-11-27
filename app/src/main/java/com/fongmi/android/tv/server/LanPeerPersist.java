package com.fongmi.android.tv.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composes the paired-device registry with an encrypted value store.
 *
 * <p>Pure JVM: sealing, value storage and the wire format are injected, so the corruption and
 * clearing rules are testable without Android. Loading never throws — an unreadable or corrupt
 * value is treated as "no paired devices", which keeps the LAN gate closed instead of crashing.
 */
public final class LanPeerPersist {

    /** Encrypts the registry so the stored value never contains device identifiers. */
    public interface SecretBox {
        String seal(String plain) throws Exception;

        String open(String sealed) throws Exception;
    }

    /** Where the sealed value lives (shared preferences, file, …). */
    public interface ValueStore {
        String read();

        void write(String value) throws Exception;
    }

    private final SecretBox box;
    private final ValueStore store;

    public LanPeerPersist(SecretBox box, ValueStore store) {
        this.box = box;
        this.store = store;
    }

    public Map<String, byte[]> load() {
        String sealed = store.read();
        if (sealed == null || sealed.trim().isEmpty()) return new LinkedHashMap<>();
        try {
            return LanPeerRegistry.decode(box.open(sealed));
        } catch (Throwable ignored) {
            return new LinkedHashMap<>();
        }
    }

    /** An empty registry clears the stored value instead of sealing an empty payload. */
    public void save(Map<String, byte[]> partners) throws Exception {
        String encoded = LanPeerRegistry.encode(partners);
        store.write(encoded.isEmpty() ? "" : box.seal(encoded));
    }
}
