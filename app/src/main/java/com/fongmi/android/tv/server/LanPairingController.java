package com.fongmi.android.tv.server;

import java.util.Map;

/**
 * Single entry point for LAN pairing state: the master switch, the one-time pairing code and the
 * paired-device registry.
 *
 * <p>Every mutation writes through to storage immediately, so a crash or restart never leaves the
 * UI showing a pairing the device will not honour. Loading happens once in the constructor.
 */
public final class LanPairingController {

    /** Persists the master switch; kept outside the sealed registry because it is not a secret. */
    public interface Switch {
        boolean isEnabled();

        void setEnabled(boolean enabled);
    }

    private final LanPeerAuthorization kernel;
    private final LanPeerPersist persist;
    private final Switch toggle;
    private final Runnable onChanged;

    public LanPairingController(LanPeerAuthorization kernel, LanPeerPersist persist, Switch toggle, Runnable onChanged) {
        this.kernel = kernel;
        this.persist = persist;
        this.toggle = toggle;
        this.onChanged = onChanged;
        kernel.setEnabled(toggle.isEnabled());
        kernel.restore(persist.load());
    }

    public synchronized boolean isEnabled() {
        return kernel.isEnabled();
    }

    public synchronized void setEnabled(boolean enabled) {
        toggle.setEnabled(enabled);
        kernel.setEnabled(enabled);
        onChanged.run();
    }

    public synchronized String issuePairingCode() {
        return kernel.issuePairingCode();
    }

    public synchronized String pair(String uuid, String code) {
        String credential = kernel.pair(uuid, code);
        if (credential == null) return null;
        persistQuietly();
        onChanged.run();
        return credential;
    }

    public synchronized boolean revoke(String uuid) {
        if (!kernel.revoke(uuid)) return false;
        persistQuietly();
        onChanged.run();
        return true;
    }

    public synchronized Map<String, byte[]> partners() {
        return kernel.partners();
    }

    public synchronized LanPeerAuthorization.Decision authorize(String action, String uuid, String credential, boolean sensitiveEnabled) {
        return kernel.authorize(action, uuid, credential, sensitiveEnabled);
    }

    private void persistQuietly() {
        try {
            persist.save(kernel.partners());
        } catch (Exception ignored) {
            // Storage failure must not resurrect a revoked pairing in memory; the next successful
            // write repairs the file, and load() treats an unreadable value as "no devices".
        }
    }
}
