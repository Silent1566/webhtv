package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ExoAudioCodecSelector implements MediaCodecSelector {

    private static final ExoAudioCodecSelector DEFAULT =
            new ExoAudioCodecSelector(MediaCodecSelector.DEFAULT);

    private final MediaCodecSelector delegate;
    private final Map<Query, List<MediaCodecInfo>> cache = new HashMap<>();

    ExoAudioCodecSelector(MediaCodecSelector delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    static MediaCodecSelector hardwareFirst(MediaCodecSelector delegate) {
        if (delegate instanceof ExoAudioCodecSelector) return delegate;
        return delegate == MediaCodecSelector.DEFAULT
                ? DEFAULT
                : new ExoAudioCodecSelector(delegate);
    }

    @Override
    public List<MediaCodecInfo> getDecoderInfos(
            String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder)
            throws MediaCodecUtil.DecoderQueryException {
        if (!isAudio(mimeType)) {
            return delegate.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        }
        Query query = new Query(
                mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        synchronized (cache) {
            List<MediaCodecInfo> cached = cache.get(query);
            if (cached != null) return cached;
            List<MediaCodecInfo> ordered = requiresFfmpeg(mimeType)
                    ? List.of()
                    : orderHardwareFirst(delegate.getDecoderInfos(
                            mimeType,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder));
            cache.put(query, ordered);
            return ordered;
        }
    }

    static boolean requiresFfmpeg(String mimeType) {
        // The target vendor ALAC codec can report READY without emitting PCM. Keep ALAC on the
        // bundled FFmpeg renderer so decoder progress and AudioTrack initialization are coupled.
        return MimeTypes.AUDIO_ALAC.equals(mimeType);
    }

    static List<MediaCodecInfo> orderHardwareFirst(List<MediaCodecInfo> infos) {
        if (infos.size() < 2) return List.copyOf(infos);
        List<MediaCodecInfo> ordered = new ArrayList<>(infos);
        ordered.sort(Comparator.comparingInt(ExoAudioCodecSelector::priority));
        return List.copyOf(ordered);
    }

    private static int priority(MediaCodecInfo info) {
        if (info.hardwareAccelerated && !info.softwareOnly) return 0;
        if (!info.softwareOnly) return 1;
        return 2;
    }

    private static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    private record Query(
            String mimeType,
            boolean requiresSecureDecoder,
            boolean requiresTunnelingDecoder) {
    }
}
