package androidx.media3.mpvplayer;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvSurfaceLifecycleSourceTest {

    @Test
    public void sameSurfaceResizeUpdatesSizeWithoutDetachingNativeSurface() throws Exception {
        String method = methodBody(readMpvPlayer(), "private void bindVideoOutput()", "private void clearVideoOutput()");
        int fastPathStart = method.indexOf("if (surfaceAttached && attachedSurface == surface)");
        int replacementPathStart = method.indexOf("if (surfaceAttached) detachMpvSurface();", fastPathStart);

        assertTrue("Missing same-surface fast path", fastPathStart >= 0);
        assertTrue("Missing replacement-surface detach path", replacementPathStart > fastPathStart);

        String fastPath = method.substring(fastPathStart, replacementPathStart);
        assertTrue("Same-surface resize must refresh the holder dimensions", fastPath.contains("updateSurfaceSize(surfaceHolder)"));
        assertFalse("Same-surface resize must not race native VO teardown", fastPath.contains("detachMpvSurface()"));
    }

    private static String readMpvPlayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of("app", "src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "androidx", "media3", "mpvplayer", "MpvPlayer.java"));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }
}
