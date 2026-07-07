package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChannelUserAgentTest {

    @Test
    public void liveHeadersUsePlaybackUserAgentFallback() throws Exception {
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Channel.java")));

        assertTrue(source.contains("DEFAULT_LIVE_UA = \"Lavf/59.27.100\""));
        assertTrue(source.contains("PlayerHelper.resolveUa(Setting.getUa(), () -> DEFAULT_LIVE_UA)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
