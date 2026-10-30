package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveListScrimTest {

    @Test
    public void defaultOpacityReproducesTheShippedLiveListGradient() {
        // shape_live_list.xml is black_80 -> black_40 -> black_05, so an unset slot must
        // stay byte-identical to what shipped: #CC000000 / #66000000 / #0D000000.
        assertEquals(0.80f, LiveListScrim.DEFAULT_OPACITY, 0.0001f);
        int[] colors = LiveListScrim.gradientColors(LiveListScrim.DEFAULT_OPACITY);
        assertEquals(0xCC000000, colors[0]);
        assertEquals(0x66000000, colors[1]);
        assertEquals(0x0D000000, colors[2]);
    }

    @Test
    public void theSliderScalesTheWholeGradientUpToOneHundredPercent() {
        int[] full = LiveListScrim.gradientColors(1f);
        assertEquals(0xFF000000, full[0]);
        assertEquals(0x80000000, full[1]);
        assertEquals(0x10000000, full[2]);
    }

    @Test
    public void aLowSliderValueScalesTheGradientWithoutTouchingTheHue() {
        int[] dim = LiveListScrim.gradientColors(0.20f);
        assertEquals(0x33000000, dim[0]);
        assertEquals(0x1A000000, dim[1]);
        assertEquals(0x03000000, dim[2]);
        // Only alpha may move: the scrim is pure black at every strength.
        for (int color : dim) assertEquals(0, color & 0x00FFFFFF);
        for (int color : LiveListScrim.gradientColors(1f)) assertEquals(0, color & 0x00FFFFFF);
    }

    @Test
    public void zeroOpacityHidesTheScrimCompletely() {
        int[] off = LiveListScrim.gradientColors(0f);
        assertEquals(0x00000000, off[0]);
        assertEquals(0x00000000, off[1]);
        assertEquals(0x00000000, off[2]);
    }

    @Test
    public void unsetSlotFallsBackToTheShippedDefault() {
        assertEquals(0.80f, LiveListScrim.resolveOpacity(null), 0.0001f);
        assertEquals(0.35f, LiveListScrim.resolveOpacity(0.35f), 0.0001f);
        assertEquals(1f, LiveListScrim.resolveOpacity(2f), 0.0001f);
        assertEquals(0f, LiveListScrim.resolveOpacity(-1f), 0.0001f);
    }

    @Test
    public void withAlphaScalesOnlyTheBlackAlpha() {
        assertEquals(0xCC000000, LiveListScrim.withAlpha(0.80f));
        assertEquals(0xFF000000, LiveListScrim.withAlpha(1f));
        assertEquals(0x00000000, LiveListScrim.withAlpha(0f));
    }

    @Test
    public void theScrimFollowsTheThemeSlotInsteadOfAHardcodedDrawable() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/theme/LiveListScrim.java");
        assertTrue(source.contains("ThemeController.configuredLiveListScrimOpacity("));
        assertTrue(source.contains("GradientDrawable.Orientation.LEFT_RIGHT"));
        // The live drawer must never borrow the playback menu hue.
        assertFalse(source.contains("colorPlayerScrim"));
    }

    private static String read(String path) throws Exception {
        Path root = Files.isDirectory(Path.of("src", "main", "java")) ? Path.of(".") : Path.of("app");
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
}
