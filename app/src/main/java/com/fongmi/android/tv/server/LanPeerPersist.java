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

        /** Returns only after a durable write; failures must throw. */
        void write(String value) throws Exception;

        default boolean isInhibited() { return false; }

        default void inhibit() throws Exception { }

        default void clearInhibit() throws Exception { }
    }

    private final SecretBox box;
    private final ValueStore store;
    private boolean loadHealthy;

    public LanPeerPersist(SecretBox box, ValueStore store) {
        this.box = box;
        this.store = store;
    }

    boolean isInhibited() {
        return store.isInhibited();
    }

    void inhibit() throws Exception {
        store.inhibit();
    }

    void clearInhibit() throws Exception {
        store.clearInhibit();
    }

    public synchronized Map<String, byte[]> load() {
        loadHealthy = false;
        try {
            String sealed = store.read();
            if (sealed == null || sealed.trim().isEmpty()) {
                loadHealthy = true;
                return new LinkedHashMap<>();
            }
            if (sealed.length() > LanPeerRegistry.MAX_SEALED_CHARS) return new LinkedHashMap<>();
            Map<String, byte[]> partners = LanPeerRegistry.decode(box.open(sealed));
            // The writer represents legitimate empty state as an unsealed empty value.
            loadHealthy = !partners.isEmpty();
            return partners;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    synchronized boolean isLoadHealthy() {
        return loadHealthy;
    }

    /** An empty registry clears the stored value instead of sealing an empty payload. */
    public void save(Map<String, byte[]> partners) throws Exception {
        String encoded = LanPeerRegistry.encode(partners);
        store.write(encoded.isEmpty() ? "" : seal(box, encoded));
    }

    static String seal(SecretBox box, String plain) throws Exception {
        String sealed = box.seal(plain);
        if (sealed == null || sealed.length() > LanPeerRegistry.MAX_SEALED_CHARS || sealed.trim().isEmpty()) {
            throw new IllegalStateException("Invalid sealed LAN state");
        }
        return sealed;
    }
}
