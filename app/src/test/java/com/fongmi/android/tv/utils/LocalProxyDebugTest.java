package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalProxyDebugTest {

    @Test
    public void acceptsOnlyParsedHttpLoopbackHosts() {
        assertTrue(LocalProxyDebug.isLocalProxyUrl("http://127.0.0.1:8899/video"));
        assertTrue(LocalProxyDebug.isLocalProxyUrl("http://localhost:8899/video"));
        assertTrue(LocalProxyDebug.isLocalProxyUrl("http://[::1]:8899/video"));

        assertFalse(LocalProxyDebug.isLocalProxyUrl("http://127.0.0.10:8899/video"));
        assertFalse(LocalProxyDebug.isLocalProxyUrl("http://127.0.0.1.example.com/video"));
        assertFalse(LocalProxyDebug.isLocalProxyUrl("https://127.0.0.1:8899/video"));
        assertFalse(LocalProxyDebug.isLocalProxyUrl("https://cdn.example.com/video"));
    }
}
