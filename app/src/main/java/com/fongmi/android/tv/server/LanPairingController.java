package com.fongmi.android.tv.server;

import java.util.Map;

/**
 * Single entry point for LAN pairing state: the master switch, the one-time pairing code and the
 * paired-device registry.
 *
 * <p>Mutations complete only after durable storage succeeds. Storage failures are reported to the
 * caller; failed revocation also disables reception. Loading happens once in the constructor.
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
    private boolean recoveryRequired;

    public LanPairingController(LanPeerAuthorization kernel, LanPeerPersist persist, Switch toggle, Runnable onChanged) {
        this.kernel = kernel;
        this.persist = persist;
        this.toggle = toggle;
        this.onChanged = onChanged;
        recoveryRequired = persist.isInhibited();
        kernel.restore(recoveryRequired ? Map.of() : persist.load());
        recoveryRequired |= !persist.isLoadHealthy();
        kernel.setEnabled(!recoveryRequired && toggle.isEnabled());
    }

    public synchronized boolean isEnabled() {
        return kernel.isEnabled();
    }

    public synchronized void setEnabled(boolean enabled) {
        synchronized (kernel) {
            if (enabled && !recoveryRequired) {
                toggle.setEnabled(true);
                kernel.setEnabled(true);
            } else {
                kernel.setEnabled(false);
                try {
                    persist.inhibit();
                    if (enabled) {
                        // Recovery discards stale or unreadable pairing state before enabling reception.
                        persist.save(Map.of());
                        kernel.restore(Map.of());
                    }
                    toggle.setEnabled(enabled);
                    if (enabled || !recoveryRequired) persist.clearInhibit();
                    recoveryRequired = !enabled && recoveryRequired;
                    kernel.setEnabled(enabled);
                } catch (Exception failure) {
                    throw failClosed("Could not persist LAN control change", failure);
                }
            }
        }
        onChanged.run();
    }

    public synchronized String issuePairingCode() {
        return kernel.issuePairingCode();
    }

    public synchronized String pair(String uuid, String code) {
        String credential;
        synchronized (kernel) {
            Map<String, byte[]> previous = kernel.partners();
            credential = kernel.pair(uuid, code);
            if (credential == null) return null;
            try {
                persist.save(kernel.partners());
            } catch (Exception failure) {
                kernel.restore(previous);
                throw new IllegalStateException("Could not save LAN pairing", failure);
            }
        }
        onChanged.run();
        return credential;
    }

    public synchronized boolean revoke(String uuid) {
        synchronized (kernel) {
            if (!kernel.partners().containsKey(uuid)) return false;
            try {
                persist.inhibit();
                kernel.revoke(uuid);
                persist.save(kernel.partners());
                if (!recoveryRequired) persist.clearInhibit();
            } catch (Exception failure) {
                kernel.revoke(uuid);
                throw failClosed("Could not persist LAN revocation", failure);
            }
        }
        onChanged.run();
        return true;
    }

    private IllegalStateException failClosed(String message, Exception failure) {
        recoveryRequired = true;
        kernel.setEnabled(false);
        IllegalStateException error = new IllegalStateException(message + "; reception disabled", failure);
        try {
            toggle.setEnabled(false);
        } catch (Exception disablingFailure) {
            if (disablingFailure != failure) error.addSuppressed(disablingFailure);
        }
        return error;
    }

    public synchronized Map<String, byte[]> partners() {
        return kernel.partners();
    }

    public synchronized LanPeerAuthorization.Decision authorize(String action, String uuid, String credential, boolean sensitiveEnabled) {
        return kernel.authorize(action, uuid, credential, sensitiveEnabled);
    }
}
