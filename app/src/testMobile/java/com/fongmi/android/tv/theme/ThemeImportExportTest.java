package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeImportExportTest {

    @Test
    public void parsesTweakCnRegistryLightAndDarkColors() {
        String json = "{\"name\":\"demo\",\"cssVars\":{\"theme\":{\"radius\":\"1rem\"},"
                + "\"light\":{\"primary\":\"oklch(0.6723 0.1606 244.9955)\",\"background\":\"#fff\",\"font-sans\":\"sans\"},"
                + "\"dark\":{\"primary\":\"#FF0000\",\"foreground\":\"hsl(0 0% 100%)\"}}}";
        ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse(json);
        assertEquals("#FFFFFF", result.profile().colors.light.appBackground);
        assertNotNull(result.profile().colors.light.primary);
        assertEquals("#FF0000", result.profile().colors.dark.primary);
        assertEquals("#FFFFFF", result.profile().colors.dark.onSurface);
        assertTrue(result.warnings().stream().anyMatch(item -> item.contains("font-sans")));
    }

    @Test
    public void parsesFlatTokensAndCopiesMissingDarkRoles() {
        ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse("{\"primary\":\"#123456\",\"secondary\":\"#eeeeee\"}");
        assertEquals("#123456", result.profile().colors.light.primary);
        assertEquals("#123456", result.profile().colors.dark.primary);
        assertEquals("#EEEEEE", result.profile().colors.dark.surfaceElevated);
    }

    @Test
    public void exportRoundTripsThroughImportAdapter() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.colors.light.primary = "#123456";
        profile.seedSource = ThemeProfile.SEED_CUSTOM;
        profile.seedColor = "#123456";
        String json = ThemeProfileCodec.encode(profile);
        ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse(json);
        assertEquals("#123456", result.profile().colors.light.primary);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void unsupportedOnlyColorSetFailsWithoutChangingAnyStore() {
        try {
            ThemeTweakCnAdapter.parse("{\"font-sans\":\"sans\",\"radius\":\"1rem\"}");
            fail("expected unsupported-only import to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no supported"));
        }
    }

    @Test
    public void transferRejectsUnsafeUrlsAndLimitsStreams() throws Exception {
        assertTrue(ThemeTransfer.isHttps("https://example.com/theme.json"));
        assertFalse(ThemeTransfer.isHttps("http://example.com/theme.json"));
        assertFalse(ThemeTransfer.isHttps("https://user:pass@example.com/theme.json"));
        assertFalse(ThemeTransfer.isHttps("https://127.0.0.1/theme.json"));
        assertEquals("example.com", ThemeTransfer.host("https://example.com/theme.json"));
        assertEquals("theme", ThemeTransfer.read(new ByteArrayInputStream("theme".getBytes(StandardCharsets.UTF_8))));
        byte[] oversized = new byte[ThemeTransfer.MAX_BYTES + 1];
        try {
            ThemeTransfer.read(new ByteArrayInputStream(oversized));
            fail("expected size limit");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("too large"));
        }
    }
}
