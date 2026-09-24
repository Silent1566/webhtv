package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExoAudioCodecSelectorTest {

    private static final String AUDIO_MIME = "audio/mp4a-latm";
    private static final String ALAC_MIME = "audio/alac";

    @Test
    public void audioDecoders_areStableAndHardwareFirst() throws Exception {
        MediaCodecInfo softwareA = codec("c2.android.aac.decoder", false, true);
        MediaCodecInfo hardwareA = codec("c2.vendor.aac.decoder", true, false);
        MediaCodecInfo unknown = codec("omx.vendor.unknown.decoder", false, false);
        MediaCodecInfo hardwareB = codec("omx.vendor.aac.decoder", true, false);
        MediaCodecInfo softwareB = codec("omx.google.aac.decoder", false, true);
        ExoAudioCodecSelector selector = new ExoAudioCodecSelector(
                fixed(List.of(softwareA, hardwareA, unknown, hardwareB, softwareB)));

        List<MediaCodecInfo> actual = selector.getDecoderInfos(
                AUDIO_MIME, false, false);

        assertEquals(List.of(hardwareA, hardwareB, unknown, softwareA, softwareB),
                actual);
    }

    @Test
    public void audioQuery_isCachedPerSecurityAndTunnelingKey() throws Exception {
        AtomicInteger queryCount = new AtomicInteger();
        List<MediaCodecInfo> infos = List.of(
                codec("c2.vendor.aac.decoder", true, false));
        MediaCodecSelector delegate = (mimeType, secure, tunneling) -> {
            queryCount.incrementAndGet();
            return infos;
        };
        ExoAudioCodecSelector selector = new ExoAudioCodecSelector(delegate);

        List<MediaCodecInfo> first = selector.getDecoderInfos(
                AUDIO_MIME, false, false);
        List<MediaCodecInfo> second = selector.getDecoderInfos(
                AUDIO_MIME, false, false);
        selector.getDecoderInfos(AUDIO_MIME, true, false);
        selector.getDecoderInfos(AUDIO_MIME, false, true);

        assertSame(first, second);
        assertEquals(3, queryCount.get());
    }

    @Test
    public void nonAudioQuery_isDelegatedWithoutSortingOrCaching() throws Exception {
        AtomicInteger queryCount = new AtomicInteger();
        List<MediaCodecInfo> infos = List.of(
                codec("c2.android.avc.decoder", false, true),
                codec("c2.vendor.avc.decoder", true, false));
        MediaCodecSelector delegate = (mimeType, secure, tunneling) -> {
            queryCount.incrementAndGet();
            return infos;
        };
        ExoAudioCodecSelector selector = new ExoAudioCodecSelector(delegate);

        List<MediaCodecInfo> first = selector.getDecoderInfos(
                "video/avc", false, false);
        List<MediaCodecInfo> second = selector.getDecoderInfos(
                "video/avc", false, false);

        assertSame(infos, first);
        assertSame(infos, second);
        assertEquals(2, queryCount.get());
    }

    @Test
    public void alacDecoders_areRoutedToFfmpeg() throws Exception {
        AtomicInteger queryCount = new AtomicInteger();
        MediaCodecSelector delegate = (mimeType, secure, tunneling) -> {
            queryCount.incrementAndGet();
            return List.of(codec("c2.vivo.alac.decoder", true, false));
        };
        ExoAudioCodecSelector selector = new ExoAudioCodecSelector(delegate);

        assertEquals(List.of(), selector.getDecoderInfos(ALAC_MIME, false, false));
        assertEquals(List.of(), selector.getDecoderInfos(ALAC_MIME, false, false));
        assertEquals(0, queryCount.get());
    }

    private static MediaCodecSelector fixed(List<MediaCodecInfo> infos) {
        return (mimeType, secure, tunneling) -> infos;
    }

    private static MediaCodecInfo codec(
            String name, boolean hardwareAccelerated, boolean softwareOnly) {
        return MediaCodecInfo.newInstance(
                name,
                name.contains("avc") ? "video/avc" : AUDIO_MIME,
                name.contains("avc") ? "video/avc" : AUDIO_MIME,
                null,
                hardwareAccelerated,
                softwareOnly,
                !softwareOnly,
                false,
                false);
    }
}
