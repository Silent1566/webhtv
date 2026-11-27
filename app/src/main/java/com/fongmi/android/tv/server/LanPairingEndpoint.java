package com.fongmi.android.tv.server;

/**
 * Pairing exchange: a sender presents its device id plus the code shown on this device and, on
 * success, receives the one credential it must present on every later request.
 *
 * <p>Pure JVM so the deny paths are testable without Android. The credential is returned in the
 * body exactly once; nothing here logs it.
 */
public final class LanPairingEndpoint {

    private LanPairingEndpoint() {
    }

    public static Result handle(LanPairingController controller, String device, String code) {
        if (!controller.isEnabled()) return new Result(403, "LAN access disabled");
        String trimmedDevice = device == null ? "" : device.trim();
        String trimmedCode = code == null ? "" : code.trim();
        if (trimmedDevice.isEmpty() || trimmedCode.isEmpty()) return new Result(400, "device and code required");
        String credential = controller.pair(trimmedDevice, trimmedCode);
        return credential == null ? new Result(403, "pairing code rejected") : new Result(200, credential);
    }

    public static final class Result {

        private final int status;
        private final String body;

        Result(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        public boolean allowed() {
            return status == 200;
        }
    }
}
