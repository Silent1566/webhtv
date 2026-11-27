package com.fongmi.android.tv.server;

/**
 * Process-wide LAN pairing state.
 *
 * <p>Created lazily on first use so app start-up pays nothing when the feature is never opened.
 * All writes go through {@link LanPairingController}, which persists immediately.
 */
public final class LanPeers {

    private static volatile LanPairingController controller;

    private LanPeers() {
    }

    public static LanPairingController get() {
        LanPairingController current = controller;
        if (current != null) return current;
        synchronized (LanPeers.class) {
            if (controller == null) controller = create();
            return controller;
        }
    }

    /** Test seam: drops the cached controller so the next call reloads from storage. */
    static void reset() {
        synchronized (LanPeers.class) {
            controller = null;
        }
    }

    private static LanPairingController create() {
        LanPairingController.Switch toggle = new LanPairingController.Switch() {
            @Override
            public boolean isEnabled() {
                return LanPeerStore.isControlEnabled();
            }

            @Override
            public void setEnabled(boolean enabled) {
                LanPeerStore.setControlEnabled(enabled);
            }
        };
        return new LanPairingController(new LanPeerAuthorization(System::currentTimeMillis),
                LanPeerStore.persist(), toggle, () -> {
                });
    }
}
