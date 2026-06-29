package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

public class ExoUtil {

    private static final int ENHANCED_MIN_BUFFER_MS = 30_000;
    private static final int ENHANCED_MAX_BUFFER_MS = 120_000;
    private static final int ENHANCED_BUFFER_FOR_PLAYBACK_MS = 1_500;
    private static final int ENHANCED_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000;
    private static final int ENHANCED_TARGET_BUFFER_BYTES = 256 * 1024 * 1024;
    private static final long ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US = 5_000L;

    public static void setPlayerView(PlayerView view) {
        view.setRender(PlayerSetting.getRender());
        view.getSubtitleView().setStyle(getCaptionStyle());
        view.getSubtitleView().setApplyEmbeddedStyles(true);
        view.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (PlayerSetting.getSubtitlePosition() != 0) view.getSubtitleView().setBottomPosition(PlayerSetting.getSubtitlePosition());
        if (PlayerSetting.getSubtitleTextSize() != 0) view.getSubtitleView().setFractionalTextSize(PlayerSetting.getSubtitleTextSize());
    }

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        ExoPlayer.Builder builder = new ExoPlayer.Builder(App.get()).setTrackSelector(buildTrackSelector()).setRenderersFactory(buildPlaybackRenderersFactory(decode)).setMediaSourceFactory(buildMediaSourceFactory());
        builder.setLoadControl(PlayerSetting.isExoEnhanced() ? buildEnhancedLoadControl() : buildLoadControl());
        ExoPlayer player = builder.build();
        PlaybackAnalyticsListener.reset();
        player.addAnalyticsListener(new PlaybackAnalyticsListener());
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static MediaItem getMediaItem(PlaySpec spec, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(spec.getUri());
        builder.setSubtitleConfigurations(buildSubtitleConfigs(spec.getSubs()));
        builder.setDrmConfiguration(buildDrmConfig(spec.getDrm()));
        builder.setRequestMetadata(buildRequestMetadata(spec));
        builder.setMediaMetadata(spec.getMetadata());
        builder.setAdblock(Setting.isAdblock());
        builder.setMimeType(spec.getFormat());
        builder.setImageDurationMs(15000);
        builder.setMediaId(spec.getKey());
        builder.setDecode(decode);
        return builder.build();
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return MimeTypes.APPLICATION_OCTET_STREAM;
        return null;
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    static int getRenderMode(int decode) {
        if (decode == PlayerEngine.HARD && PlayerSetting.isExoEnhanced()) return DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static CaptionStyleCompat getCaptionStyle() {
        return PlayerSetting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    private static LoadControl buildLoadControl() {
        DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder().setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * PlayerSetting.getBuffer(), DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * PlayerSetting.getBuffer(), DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
        int bufferBytes = PlayerSetting.getBufferBytes();
        int backBufferMs = PlayerSetting.getBackBufferMs();
        if (bufferBytes > 0) builder.setTargetBufferBytes(bufferBytes);
        if (backBufferMs > 0) builder.setBackBuffer(backBufferMs, true);
        return builder.build();
    }

    private static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        builder.setTunnelingEnabled(PlayerSetting.isTunnelingEnabled());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    private static DefaultLoadControl buildEnhancedLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(ENHANCED_MIN_BUFFER_MS, ENHANCED_MAX_BUFFER_MS, ENHANCED_BUFFER_FOR_PLAYBACK_MS, ENHANCED_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .setTargetBufferBytes(ENHANCED_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private static RenderersFactory buildPlaybackRenderersFactory(int decode) {
        return buildRenderersFactory(getRenderMode(decode), PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    private static RenderersFactory buildRenderersFactory(int renderMode, boolean audioPrefer, boolean videoPrefer) {
        DefaultRenderersFactory factory = new FfmpegRenderersFactory(App.get(), audioPrefer, videoPrefer) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        if (PlayerSetting.isExoEnhanced()) {
            factory.forceEnableMediaCodecAsynchronousQueueing();
            factory.experimentalSetLateThresholdToDropDecoderInputUs(ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US);
        }
        return factory.setEnableDecoderFallback(true).setExtensionRendererMode(renderMode);
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams);
        if (!PlayerSetting.isAudioPassThrough()) builder.setAudioOutputProvider(new AudioTrackAudioOutputProvider.Builder(null).build());
        return builder.build();
    }

    private static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    private static MediaItem.RequestMetadata buildRequestMetadata(PlaySpec spec) {
        return new MediaItem.RequestMetadata.Builder().setMediaUri(spec.getUri()).setExtras(PlayerHelper.toBundle(spec.getHeaders())).build();
    }

    private static List<MediaItem.SubtitleConfiguration> buildSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) for (Sub sub : subs) configs.add(buildSubConfig(sub));
        return configs;
    }

    private static MediaItem.SubtitleConfiguration buildSubConfig(Sub sub) {
        return new MediaItem.SubtitleConfiguration.Builder(Uri.parse(UrlUtil.convert(sub.getUrl()))).setLabel(sub.getName()).setMimeType(sub.getFormat()).setSelectionFlags(sub.getFlag()).setLanguage(sub.getLang()).build();
    }

    private static MediaItem.DrmConfiguration buildDrmConfig(Drm drm) {
        return drm == null ? null : new MediaItem.DrmConfiguration.Builder(drm.getUUID()).setMultiSession(!C.CLEARKEY_UUID.equals(drm.getUUID())).setForceDefaultLicenseUri(drm.isForceKey()).setLicenseRequestHeaders(drm.getHeader()).setLicenseUri(drm.getKey()).build();
    }

    private static class FfmpegRenderersFactory extends DefaultRenderersFactory {

        private final boolean audioPrefer;
        private final boolean videoPrefer;

        FfmpegRenderersFactory(Context context, boolean audioPrefer, boolean videoPrefer) {
            super(context);
            this.audioPrefer = audioPrefer;
            this.videoPrefer = videoPrefer;
        }

        @Override
        protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
            super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
            if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(extensionRendererMode, audioPrefer, out), new FfmpegAudioRenderer(eventHandler, eventListener, audioSink));
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
            super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
            if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(extensionRendererMode, videoPrefer, out), new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
            } catch (Throwable ignored) {
            }
        }

        private int getExtensionRendererIndex(int extensionRendererMode, boolean prefer, ArrayList<Renderer> out) {
            int index = out.size();
            if (index > 0 && (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER || prefer)) index--;
            return index;
        }
    }
}
