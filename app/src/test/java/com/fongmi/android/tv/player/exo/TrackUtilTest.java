package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TrackUtilTest {

    @Test
    public void returnsTheOnlySelectedFormat() {
        Format selected = video("video/avc", 1920, 1080);

        assertEquals(selected, TrackUtil.onlySelectedFormat(List.of(selected)));
    }

    @Test
    public void adaptiveSelectionWithMultipleCandidatesStaysUnknown() {
        Format low = video("video/avc", 1280, 720);
        Format high = video("video/avc", 3840, 2160);

        assertNull(TrackUtil.onlySelectedFormat(List.of(low, high)));
    }

    private static Format video(String mimeType, int width, int height) {
        return new Format.Builder()
                .setSampleMimeType(mimeType)
                .setWidth(width)
                .setHeight(height)
                .build();
    }
}
