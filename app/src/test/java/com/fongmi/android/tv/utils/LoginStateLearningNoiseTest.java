package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoginStateLearningNoiseTest {

    @Test
    public void ignoresWebhtvDebugLogEvenWhenStoredAsText() {
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/cache/webhtv-debug-log.txt"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("sdcard/Download/webhtv-debug-log-20260720.txt"));
    }

    @Test
    public void ignoresLogExtensionsRotationsAndDiagnosticDirectories() {
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/player.log"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/player.log.3"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/player.log.3.gz"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/logs/network.txt"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/traces/playback.txt"));
        assertTrue(LoginStateSync.isIgnoredLearningPath("app/files/logcat-20260720.txt"));
    }

    @Test
    public void keepsPossibleLoginStateTextAndStructuredFiles() {
        assertFalse(LoginStateSync.isIgnoredLearningPath("app/files/cookies.txt"));
        assertFalse(LoginStateSync.isIgnoredLearningPath("app/files/token.json"));
        assertFalse(LoginStateSync.isIgnoredLearningPath("app/shared_prefs/login.xml"));
        assertFalse(LoginStateSync.isIgnoredLearningPath("app/app_webview/Default/Cookies"));
    }
}
