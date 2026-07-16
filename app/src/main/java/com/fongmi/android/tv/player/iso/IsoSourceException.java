package com.fongmi.android.tv.player.iso;

import java.io.IOException;

public final class IsoSourceException extends IOException {

    public enum Reason {
        RANGE_UNSUPPORTED,
        UNAUTHORIZED,
        NOT_FOUND,
        RANGE_INVALID,
        SOURCE_CHANGED,
        CLOSED,
        NETWORK
    }

    private final Reason reason;
    private final int httpCode;

    public IsoSourceException(Reason reason, String message) {
        this(reason, 0, message, null);
    }

    public IsoSourceException(Reason reason, int httpCode, String message) {
        this(reason, httpCode, message, null);
    }

    public IsoSourceException(Reason reason, String message, Throwable cause) {
        this(reason, 0, message, cause);
    }

    private IsoSourceException(Reason reason, int httpCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.httpCode = httpCode;
    }

    public Reason reason() {
        return reason;
    }

    public int httpCode() {
        return httpCode;
    }
}
