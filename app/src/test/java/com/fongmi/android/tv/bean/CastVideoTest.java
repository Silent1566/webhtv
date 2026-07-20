package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CastVideoTest {

    @Test
    public void constructor_toleratesUnreadyPlaybackFields() {
        CastVideo video = new CastVideo(null, null, 0, null);

        assertEquals("", video.name());
        assertEquals("", video.url());
        assertNotNull(video.headers());
        assertTrue(video.headers().isEmpty());
    }
}
