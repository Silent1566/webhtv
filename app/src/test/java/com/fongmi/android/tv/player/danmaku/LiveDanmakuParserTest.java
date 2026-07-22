package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuParserTest {

    @Test
    public void parsesNormalAndSuperChatMessages() {
        LiveDanmakuParser.Result normal = LiveDanmakuParser.parse("{\"type\":\"chat\",\"message\":\"hello\",\"color\":\"#12ABef\"}", 7L, 100L);
        LiveDanmakuParser.Result superChat = LiveDanmakuParser.parse("{\"type\":\"superChat\",\"message\":\"thanks\"}", 8L, 200L);

        assertEquals(LiveDanmakuParser.Kind.MESSAGE, normal.kind());
        assertEquals(LiveDanmakuMessage.Type.NORMAL, normal.message().type());
        assertEquals("hello", normal.message().text());
        assertEquals(0xFF12ABEF, normal.message().colorArgb());
        assertEquals(7L, normal.message().generation());
        assertEquals(LiveDanmakuMessage.Type.SUPER_CHAT, superChat.message().type());
        assertEquals(0xFFFFFFFF, superChat.message().colorArgb());
    }

    @Test
    public void parsesOnlineWithoutCreatingRenderableMessage() {
        LiveDanmakuParser.Result result = LiveDanmakuParser.parse("{\"type\":\"online\",\"data\":12345}", 1L, 10L);

        assertTrue(result.isAccepted());
        assertEquals(LiveDanmakuParser.Kind.ONLINE, result.kind());
        assertEquals(12345L, result.online());
        assertEquals(null, result.message());
    }

    @Test
    public void rejectsMalformedUnknownAndMissingMessagePayloads() {
        assertFalse(LiveDanmakuParser.parse("not-json", 1L, 1L).isAccepted());
        assertFalse(LiveDanmakuParser.parse("[]", 1L, 1L).isAccepted());
        assertFalse(LiveDanmakuParser.parse("{\"type\":\"gift\",\"message\":\"x\"}", 1L, 1L).isAccepted());
        assertFalse(LiveDanmakuParser.parse("{\"type\":\"chat\"}", 1L, 1L).isAccepted());
        assertFalse(LiveDanmakuParser.parse("{\"type\":\"superChat\",\"data\":{\"message\":\"nested only\"}}", 1L, 1L).isAccepted());
    }

    @Test
    public void normalizesWhitespaceControlsAndInvalidColors() {
        LiveDanmakuParser.Result result = LiveDanmakuParser.parse("{\"type\":\"chat\",\"message\":\"  a\\n\\t\\u0000  b  \",\"color\":\"red\"}", 1L, 1L);

        assertNotNull(result.message());
        assertEquals("a b", result.message().text());
        assertEquals(0xFFFFFFFF, result.message().colorArgb());
    }

    @Test
    public void truncatesByUnicodeCodePointWithoutSplittingEmoji() {
        String message = "😀".repeat(121);
        LiveDanmakuParser.Result result = LiveDanmakuParser.parse("{\"type\":\"chat\",\"message\":\"" + message + "\"}", 1L, 1L);

        assertEquals(120, result.message().text().codePointCount(0, result.message().text().length()));
        assertEquals("😀", result.message().text().substring(result.message().text().length() - 2));
    }

    @Test
    public void rejectsFramesOverUtf8ByteLimit() {
        String oversized = "你".repeat(LiveDanmakuParser.MAX_FRAME_BYTES / 3 + 1);
        assertFalse(LiveDanmakuParser.parse(oversized, 1L, 1L).isAccepted());
    }
}
