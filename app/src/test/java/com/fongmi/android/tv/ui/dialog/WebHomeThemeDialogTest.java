package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebHomeThemeDialogTest {

    @Test
    public void remoteConfirmationIsRequiredUntilTheExactUrlWasTrustedLocally() {
        assertTrue(WebHomeThemeDialog.requiresRemoteConfirmation("",
                "https://theme.example/one"));
        assertTrue(WebHomeThemeDialog.requiresRemoteConfirmation("https://theme.example/one",
                "https://theme.example/two"));
        assertFalse(WebHomeThemeDialog.requiresRemoteConfirmation("https://theme.example/one",
                " https://theme.example/one "));
    }
}
