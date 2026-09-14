package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThemeResolverTest {

    @Test
    public void resolvesExplicitLightRolesAndDerivesReadableText() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.seedSource = ThemeProfile.SEED_CUSTOM;
        profile.seedColor = "#155DFC";
        profile.colors.light.primary = "#155DFC";
        profile.colors.light.appBackground = "#F8FAFC";
        profile.colors.light.surface = "#FFFFFF";
        ThemeTokens tokens = ThemeResolver.resolve(profile, false, 0);
        assertEquals(0xFF155DFC, tokens.primary());
        assertTrue(ThemeColorUtil.contrast(tokens.onSurface(), tokens.surface()) >= 4.5);
        assertTrue(ThemeColorUtil.contrast(tokens.onPrimary(), tokens.primary()) >= 4.5);
    }

    @Test
    public void systemAndWallpaperSeedAffectResolvedModeAndAccent() {
        ThemeProfile profile = ThemeProfileStore.migrateLegacy(0);
        ThemeTokens dark = ThemeResolver.resolve(profile, true, 0xFF00897B);
        assertEquals(ThemeProfile.MODE_DARK, dark.mode());
        assertTrue(dark.primary() != 0xFF6750A4);
    }
}
