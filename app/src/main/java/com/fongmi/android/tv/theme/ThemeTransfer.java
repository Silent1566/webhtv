package com.fongmi.android.tv.theme;

import java.net.URI;
import java.net.URISyntaxException;

/** Boundary helpers shared by import/export UI and tests. */
public final class ThemeTransfer {

    private ThemeTransfer() {
    }

    public static boolean isHttps(String value) {
        return ThemeProfileValidator.isSafeHttps(value);
    }

    public static String host(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
