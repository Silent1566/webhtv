package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebThemeManifestLoaderTest {

    @Test
    public void boundedReaderAcceptsLimitAndRejectsOneExtraByte() throws Exception {
        assertEquals("1234", WebThemeManifestLoader.read(stream("1234"), 4));
        assertThrows(IOException.class, () -> WebThemeManifestLoader.read(stream("12345"), 4));
    }

    @Test
    public void boundedReaderAvoidsJava10OnlyByteArrayOutputStreamApi() throws Exception {
        String source = source();

        assertTrue(source.contains("new String(output.toByteArray(), StandardCharsets.UTF_8)"));
        assertFalse(source.contains("output.toString(StandardCharsets.UTF_8)"));
    }

    @Test
    public void remoteManifestUsesIsolatedPlatformTlsClient() throws Exception {
        String source = source();

        assertTrue(source.contains("new OkHttpClient.Builder()"));
        assertTrue(source.contains("Dns.SYSTEM.lookup(hostname)"));
        assertFalse(source.contains("OkHttp.client().newBuilder()"));
        assertFalse(source.contains("com.github.catvod.net.OkHttp"));
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String source() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(
                "src/main/java/com/fongmi/android/tv/web/WebThemeManifestLoader.java"), StandardCharsets.UTF_8);
    }
}
