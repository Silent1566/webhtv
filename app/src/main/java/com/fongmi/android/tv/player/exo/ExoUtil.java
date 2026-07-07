package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Range;
import android.view.Display;
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
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
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
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.CompatFfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

public class ExoUtil {

    private static final int ENHANCED_MIN_BUFFER_MS = 30_000;
    private static final int ENHANCED_MAX_BUFFER_MS = 120_000;
    private static final int ENHANCED_BUFFER_FOR_PLAYBACK_MS = 1_500;
    private static final int ENHANCED_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000;
    private static final int ENHANCED_TARGET_BUFFER_BYTES = 256 * 1024 * 1024;
    private static final long ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US = 5_000L;
    private static final long ENHANCED_ADAPT_COOLDOWN_MS = 15_000L;
    private static final int ENHANCED_DROPPED_FRAMES_THRESHOLD = 24;
    private static final int ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD = 4;
    private static final int ENHANCED_BANDWIDTH_SAFETY_NUMERATOR = 4;
    private static final int ENHANCED_BANDWIDTH_SAFETY_DENOMINATOR = 5;
    private static final int FFMPEG_SKIP_FRAME_NONREF = 8;
    private static final int FFMPEG_SKIP_LOOP_FILTER_ALL = 48;
    private static final int FFMPEG_LOWRES_HALF = 1;
    private static volatile EnhancedVideoProfile enhancedVideoProfile;

    public static void setPlayerView(PlayerView view) {
        view.setRender(PlayerSetting.getRender());
        view.getSubtitleView().setStyle(getCaptionStyle());
        view.getSubtitleView().setApplyEmbeddedStyles(true);
        view.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (PlayerSetting.getSubtitlePosition() != 0) view.getSubtitleView().setBottomPosition(PlayerSetting.getSubtitlePosition());
        if (PlayerSetting.getSubtitleTextSize() != 0) view.getSubtitleView().setFractionalTextSize(PlayerSetting.getSubtitleTextSize());
    }

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        EnhancedVideoProfile profile = getEnhancedVideoProfile(decode);
        List<EnhancedVideoProfile> profiles = getEnhancedVideoProfiles(decode);
        DefaultTrackSelector trackSelector = buildTrackSelector(decode);
        ExoPlayer.Builder builder = new ExoPlayer.Builder(App.get())
                .setTrackSelector(trackSelector)
                .setRenderersFactory(buildPlaybackRenderersFactory(decode))
                .setMediaSourceFactory(buildMediaSourceFactory())
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS);
        if (PlayerSetting.getFFmpegMode() != 2) builder.setLoadControl(PlaybackPerformanceSetting.isHighBufferEnabled() ? buildEnhancedLoadControl() : buildLoadControl());
        if (PlaybackPerformanceSetting.isBandwidthMeterEnabled()) builder.setBandwidthMeter(buildEnhancedBandwidthMeter(profile));
        if (PlaybackPerformanceSetting.isDynamicSchedulingEnabled()) {
            builder.experimentalSetDynamicSchedulingEnabled(true);
        }
        ExoPlayer player = builder.build();
        PlaybackAnalyticsListener.reset();
        player.addAnalyticsListener(new PlaybackAnalyticsListener());
        if (PlaybackPerformanceSetting.isAdaptiveDowngradeEnabled()) player.addAnalyticsListener(new AdaptiveVideoProfileController(trackSelector, profile, profiles));
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
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static int getVideoRenderMode(int decode) {
        return getRenderMode(decode);
    }

    private static int getAudioRenderMode() {
        return DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    }

    private static boolean isAudioPrefer(int decode) {
        return decode != PlayerEngine.SOFT && PlayerSetting.isAudioPrefer();
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

    private static DefaultTrackSelector buildTrackSelector(int decode) {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        builder.setTunnelingEnabled(PlayerSetting.isTunnelingEnabled());
        if (PlaybackPerformanceSetting.isTrackLimitEnabled()) {
            applyEnhancedVideoProfile(builder, getEnhancedVideoProfile(decode));
        } else {
            builder.setForceHighestSupportedBitrate(true);
        }
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    private static void applyEnhancedVideoProfile(DefaultTrackSelector.Parameters.Builder builder, EnhancedVideoProfile profile) {
        builder.setMaxVideoSize(profile.width(), profile.height());
        builder.setViewportSize(profile.width(), profile.height(), true);
        builder.setMaxVideoBitrate(profile.bitrate());
        builder.setMaxVideoFrameRate(profile.frameRate());
        builder.setExceedVideoConstraintsIfNecessary(true);
        builder.setAllowVideoNonSeamlessAdaptiveness(true);
        builder.setAllowVideoMixedMimeTypeAdaptiveness(true);
        builder.setForceHighestSupportedBitrate(false);
    }

    public static EnhancedVideoProfile getEnhancedVideoProfile() {
        return getEnhancedVideoProfile(PlayerEngine.HARD);
    }

    private static EnhancedVideoProfile getEnhancedVideoProfile(int decode) {
        if (decode == PlayerEngine.SOFT) return detectSoftVideoProfile(App.get());
        EnhancedVideoProfile profile = enhancedVideoProfile;
        if (profile != null) return profile;
        synchronized (ExoUtil.class) {
            profile = enhancedVideoProfile;
            if (profile == null) enhancedVideoProfile = profile = detectEnhancedVideoProfile(App.get());
        }
        return profile;
    }

    private static List<EnhancedVideoProfile> getEnhancedVideoProfiles(int decode) {
        return decode == PlayerEngine.SOFT ? EnhancedVideoProfile.softTargets() : EnhancedVideoProfile.targets();
    }

    private static EnhancedVideoProfile detectEnhancedVideoProfile(Context context) {
        DisplayProfile display = getDisplayProfile(context);
        CodecVideoProfile codec = chooseCodecVideoProfile(MediaFormat.MIMETYPE_VIDEO_HEVC, display);
        EnhancedVideoProfile profile = codec.supported() ? codec.profile() : EnhancedVideoProfile.low();
        return logEnhancedVideoProfile(profile, display, codec);
    }

    private static EnhancedVideoProfile detectSoftVideoProfile(Context context) {
        DisplayProfile display = getDisplayProfile(context);
        for (EnhancedVideoProfile target : EnhancedVideoProfile.softTargets()) {
            if (display.supports(target)) return logSoftVideoProfile(target, display);
        }
        return logSoftVideoProfile(EnhancedVideoProfile.low(), display);
    }

    private static EnhancedVideoProfile logSoftVideoProfile(EnhancedVideoProfile profile, DisplayProfile display) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-enhance", "soft profile=%dx%d@%d bitrate=%d display=%dx%d", profile.width(), profile.height(), profile.frameRate(), profile.bitrate(), display.width(), display.height());
        return profile;
    }

    private static CodecVideoProfile chooseCodecVideoProfile(String mimeType, DisplayProfile display) {
        for (EnhancedVideoProfile target : EnhancedVideoProfile.targets()) {
            if (!display.supports(target)) continue;
            CodecVideoProfile codec = getBestCodecVideoProfile(mimeType, target);
            if (codec.supported()) return codec;
        }
        return CodecVideoProfile.unsupported();
    }

    private static CodecVideoProfile getBestCodecVideoProfile(String mimeType, EnhancedVideoProfile target) {
        CodecVideoProfile best = CodecVideoProfile.unsupported();
        for (android.media.MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder() || !isHardwareCodec(info)) continue;
            android.media.MediaCodecInfo.VideoCapabilities caps = getVideoCapabilities(info, mimeType);
            if (caps == null) continue;
            EnhancedVideoProfile supported = getSupportedProfile(caps, target);
            if (supported == null) continue;
            CodecVideoProfile profile = new CodecVideoProfile(info.getName(), supported, hasPerformancePoint(caps, supported));
            if (profile.compareTo(best) > 0) best = profile;
        }
        return best;
    }

    private static EnhancedVideoProfile getSupportedProfile(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile target) {
        if (!supportsSize(caps, target.width(), target.height())) return null;
        if (supportsPerformance(caps, target) || supportsRate(caps, target)) return target.withBitrate(getSupportedBitrate(caps, target.bitrate()));
        return target.withFrameRate(30).withBitrate(getSupportedBitrate(caps, target.bitrate()));
    }

    private static android.media.MediaCodecInfo.VideoCapabilities getVideoCapabilities(android.media.MediaCodecInfo info, String mimeType) {
        try {
            return info.getCapabilitiesForType(mimeType).getVideoCapabilities();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean supportsSize(android.media.MediaCodecInfo.VideoCapabilities caps, int width, int height) {
        try {
            return caps.isSizeSupported(width, height) || caps.isSizeSupported(height, width);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean supportsRate(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        try {
            return caps.areSizeAndRateSupported(profile.width(), profile.height(), profile.frameRate()) || caps.areSizeAndRateSupported(profile.height(), profile.width(), profile.frameRate());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean supportsPerformance(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasPerformancePoint(caps, profile);
    }

    private static boolean hasPerformancePoint(android.media.MediaCodecInfo.VideoCapabilities caps, EnhancedVideoProfile profile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint target = new android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint(profile.width(), profile.height(), profile.frameRate());
            for (android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint point : caps.getSupportedPerformancePoints()) {
                if (point.covers(target)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int getSupportedBitrate(android.media.MediaCodecInfo.VideoCapabilities caps, int bitrate) {
        try {
            Range<Integer> range = caps.getBitrateRange();
            return range == null ? bitrate : Math.max(1_000_000, Math.min(bitrate, range.getUpper()));
        } catch (Exception e) {
            return bitrate;
        }
    }

    private static boolean isHardwareCodec(android.media.MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase();
        return !name.contains("google") && !name.contains("android") && !name.contains("ffmpeg") && !name.contains("software") && !name.startsWith("c2.android");
    }

    private static DisplayProfile getDisplayProfile(Context context) {
        int width = ResUtil.getScreenWidth(context);
        int height = ResUtil.getScreenHeight(context);
        Display display = ResUtil.getDisplay(context);
        if (display != null) {
            Display.Mode mode = display.getMode();
            if (mode != null) {
                width = Math.max(width, Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight()));
                height = Math.max(height, Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight()));
            }
            for (Display.Mode supported : display.getSupportedModes()) {
                width = Math.max(width, Math.max(supported.getPhysicalWidth(), supported.getPhysicalHeight()));
                height = Math.max(height, Math.min(supported.getPhysicalWidth(), supported.getPhysicalHeight()));
            }
        }
        return new DisplayProfile(Math.max(width, height), Math.min(width, height));
    }

    private static EnhancedVideoProfile logEnhancedVideoProfile(EnhancedVideoProfile profile, DisplayProfile display, CodecVideoProfile codec) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-enhance", "profile=%dx%d@%d bitrate=%d display=%dx%d codec=%s codecProfile=%s performancePoint=%s", profile.width(), profile.height(), profile.frameRate(), profile.bitrate(), display.width(), display.height(), codec.name(), codec.profileText(), codec.performancePoint());
        return profile;
    }

    private static DefaultLoadControl buildEnhancedLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(getMinBufferMs(), getMaxBufferMs(), ENHANCED_BUFFER_FOR_PLAYBACK_MS, ENHANCED_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .setTargetBufferBytes(getTargetBufferBytes())
                .setBackBuffer(PlayerSetting.getBackBufferMs(), true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private static int getMinBufferMs() {
        return Math.min(ENHANCED_MIN_BUFFER_MS, Math.max(15_000, PlayerSetting.getBuffer() * 3_000));
    }

    private static int getMaxBufferMs() {
        return Math.max(ENHANCED_MAX_BUFFER_MS / 2, Math.min(ENHANCED_MAX_BUFFER_MS, getMinBufferMs() * 4));
    }

    private static int getTargetBufferBytes() {
        int bytes = PlayerSetting.getBufferBytes();
        return bytes > 0 ? bytes : ENHANCED_TARGET_BUFFER_BYTES;
    }

    private static DefaultBandwidthMeter buildEnhancedBandwidthMeter(EnhancedVideoProfile profile) {
        return new DefaultBandwidthMeter.Builder(App.get())
                .setSlidingWindowMaxWeight(4_000)
                .setInitialBitrateSupplier(networkType -> getInitialBitrateEstimate(profile, networkType))
                .build();
    }

    private static long getInitialBitrateEstimate(EnhancedVideoProfile profile, int networkType) {
        return switch (networkType) {
            case C.NETWORK_TYPE_ETHERNET, C.NETWORK_TYPE_WIFI -> Math.max(profile.bitrate() * 2L, 20_000_000L);
            case C.NETWORK_TYPE_5G_SA, C.NETWORK_TYPE_5G_NSA -> Math.max(profile.bitrate() * 3L / 2L, 15_000_000L);
            case C.NETWORK_TYPE_4G -> Math.max(profile.bitrate(), 8_000_000L);
            case C.NETWORK_TYPE_3G -> 4_000_000L;
            case C.NETWORK_TYPE_2G -> 512_000L;
            default -> Math.max(profile.bitrate(), 8_000_000L);
        };
    }

    private static RenderersFactory buildPlaybackRenderersFactory(int decode) {
        return buildRenderersFactory(getAudioRenderMode(), getVideoRenderMode(decode), isAudioPrefer(decode), PlayerSetting.isVideoPrefer(), decode == PlayerEngine.SOFT && PlaybackPerformanceSetting.isSoftVideoTuneEnabled());
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer(), false);
    }

    private static RenderersFactory buildRenderersFactory(int audioRenderMode, int videoRenderMode, boolean audioPrefer, boolean videoPrefer, boolean softVideoTune) {
        int mode = PlayerSetting.getFFmpegMode();
        if (mode == 0) {
            return buildNextLibRenderersFactory(audioRenderMode, videoRenderMode, audioPrefer, videoPrefer, softVideoTune);
        } else if (mode == 1) {
            return buildOfficialRenderersFactory(audioRenderMode, videoRenderMode, audioPrefer, videoPrefer);
        } else {
            return buildSimpleRenderersFactory(audioRenderMode, videoRenderMode, audioPrefer, videoPrefer);
        }
    }

    private static RenderersFactory buildNextLibRenderersFactory(int audioRenderMode, int videoRenderMode, boolean audioPrefer, boolean videoPrefer, boolean softVideoTune) {
        DefaultRenderersFactory factory = new FfmpegRenderersFactory(App.get(), audioRenderMode, videoRenderMode, audioPrefer, videoPrefer, softVideoTune) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        if (PlaybackPerformanceSetting.isCodecAsyncQueueingEnabled()) factory.forceEnableMediaCodecAsynchronousQueueing();
        if (PlaybackPerformanceSetting.isVideoDurationProgressEnabled()) factory.setEnableMediaCodecVideoRendererDurationToProgressUs(true);
        if (PlaybackPerformanceSetting.isLateDropInputEnabled()) factory.experimentalSetLateThresholdToDropDecoderInputUs(ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US);
        return factory.setEnableDecoderFallback(PlaybackPerformanceSetting.isDecoderFallbackEnabled()).setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));
    }

    private static RenderersFactory buildOfficialRenderersFactory(int audioRenderMode, int videoRenderMode, boolean audioPrefer, boolean videoPrefer) {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        if (PlayerSetting.isExoEnhanced()) {
            factory.forceEnableMediaCodecAsynchronousQueueing();
            factory.experimentalSetLateThresholdToDropDecoderInputUs(ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US);
        }
        // Official 模式：只使用 Media3 自带的扩展渲染器，不依赖 NextLib
        // 通过 ExtensionRendererMode 控制硬解/软解优先级
        return factory
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));
    }

    private static RenderersFactory buildSimpleRenderersFactory(int audioRenderMode, int videoRenderMode, boolean audioPrefer, boolean videoPrefer) {
        // Simple 模式：完全模仿 TV 项目
        // 不使用 NextLib，不设置 ExoEnhanced 参数，保持最简单的配置
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        return factory
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(Math.max(audioRenderMode, videoRenderMode));
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

        private final int audioRenderMode;
        private final int videoRenderMode;
        private final boolean audioPrefer;
        private final boolean videoPrefer;
        private final boolean softVideoTune;

        FfmpegRenderersFactory(Context context, int audioRenderMode, int videoRenderMode, boolean audioPrefer, boolean videoPrefer, boolean softVideoTune) {
            super(context);
            this.audioRenderMode = audioRenderMode;
            this.videoRenderMode = videoRenderMode;
            this.audioPrefer = audioPrefer;
            this.videoPrefer = videoPrefer;
            this.softVideoTune = softVideoTune;
        }

        @Override
        protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
            super.buildAudioRenderers(context, audioRenderMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
            if (audioRenderMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(audioRenderMode, audioPrefer, out), new CompatFfmpegAudioRenderer(context, eventHandler, eventListener, audioSink, softVideoTune));
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
            super.buildVideoRenderers(context, videoRenderMode, getVideoCodecSelector(mediaCodecSelector), enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
            if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;
            try {
                out.add(getExtensionRendererIndex(videoRenderMode, videoPrefer, out), buildFfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener));
            } catch (Throwable ignored) {
            }
        }

        private FfmpegVideoRenderer buildFfmpegVideoRenderer(long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener) {
            if (!softVideoTune) return new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
            return new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, Runtime.getRuntime().availableProcessors(), 4, 4, FFMPEG_SKIP_FRAME_NONREF, FFMPEG_SKIP_LOOP_FILTER_ALL, FFMPEG_LOWRES_HALF);
        }

        private MediaCodecSelector getVideoCodecSelector(MediaCodecSelector mediaCodecSelector) {
            if (videoRenderMode != EXTENSION_RENDERER_MODE_OFF) return mediaCodecSelector;
            return (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                List<MediaCodecInfo> infos = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                if (mimeType == null || !mimeType.startsWith("video/")) return infos;
                List<MediaCodecInfo> hardwareInfos = new ArrayList<>();
                for (MediaCodecInfo info : infos) if (info.hardwareAccelerated) hardwareInfos.add(info);
                return hardwareInfos;
            };
        }

        private int getExtensionRendererIndex(int extensionRendererMode, boolean prefer, ArrayList<Renderer> out) {
            int index = out.size();
            if (index > 0 && (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER || prefer)) index--;
            return index;
        }
    }

    private static class AdaptiveVideoProfileController implements AnalyticsListener {

        private final DefaultTrackSelector trackSelector;
        private final List<EnhancedVideoProfile> profiles;
        private EnhancedVideoProfile profile;
        private int profileIndex;
        private boolean everReady;
        private long lastAdaptMs;

        AdaptiveVideoProfileController(DefaultTrackSelector trackSelector, EnhancedVideoProfile profile, List<EnhancedVideoProfile> profiles) {
            this.trackSelector = trackSelector;
            this.profiles = profiles;
            this.profile = profile;
            this.profileIndex = getProfileIndex(profile);
        }

        @Override
        public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
            if (state == Player.STATE_READY) {
                everReady = true;
            } else if (state == Player.STATE_BUFFERING && everReady) {
                maybeDowngrade("rebuffer", eventTime, 0);
            }
        }

        @Override
        public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            if (droppedFrames < ENHANCED_DROPPED_FRAMES_THRESHOLD && getDroppedFramesPerSecond(droppedFrames, elapsedMs) < ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD) return;
            maybeDowngrade("droppedFrames=" + droppedFrames + "/" + elapsedMs + "ms", eventTime, 0);
        }

        @Override
        public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
            if (bitrateEstimate <= 0 || bitrateEstimate * ENHANCED_BANDWIDTH_SAFETY_NUMERATOR >= (long) profile.bitrate() * ENHANCED_BANDWIDTH_SAFETY_DENOMINATOR) return;
            maybeDowngrade("bandwidth=" + bitrateEstimate, eventTime, bitrateEstimate);
        }

        private int getDroppedFramesPerSecond(int droppedFrames, long elapsedMs) {
            if (elapsedMs <= 0) return droppedFrames;
            return (int) (droppedFrames * 1000L / elapsedMs);
        }

        private void maybeDowngrade(String reason, EventTime eventTime, long bitrateEstimate) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (profileIndex >= profiles.size() - 1 || now - lastAdaptMs < ENHANCED_ADAPT_COOLDOWN_MS) return;
            EnhancedVideoProfile next = profiles.get(++profileIndex);
            if (bitrateEstimate > 0) next = capByBandwidth(next, bitrateEstimate);
            apply(next);
            lastAdaptMs = now;
            SpiderDebug.log("exo-enhance", "adaptive downgrade reason=%s profile=%dx%d@%d bitrate=%d position=%d", reason, next.width(), next.height(), next.frameRate(), next.bitrate(), eventTime.currentPlaybackPositionMs);
        }

        private EnhancedVideoProfile capByBandwidth(EnhancedVideoProfile profile, long bitrateEstimate) {
            int bitrate = (int) Math.max(1_000_000L, bitrateEstimate * ENHANCED_BANDWIDTH_SAFETY_NUMERATOR / ENHANCED_BANDWIDTH_SAFETY_DENOMINATOR);
            return profile.withBitrate(Math.min(profile.bitrate(), bitrate));
        }

        private void apply(EnhancedVideoProfile profile) {
            this.profile = profile;
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
            applyEnhancedVideoProfile(builder, profile);
            trackSelector.setParameters(builder.build());
        }

        private int getProfileIndex(EnhancedVideoProfile profile) {
            for (int i = 0; i < profiles.size(); i++) {
                EnhancedVideoProfile target = profiles.get(i);
                if (profile.width() >= target.width() && profile.height() >= target.height()) return i;
            }
            return profiles.size() - 1;
        }
    }

    public record EnhancedVideoProfile(int width, int height, int bitrate, int frameRate) {

        private static List<EnhancedVideoProfile> targets() {
            return List.of(
                    new EnhancedVideoProfile(3840, 2160, 20_000_000, 60),
                    new EnhancedVideoProfile(2560, 1440, 12_000_000, 60),
                    new EnhancedVideoProfile(1920, 1080, 8_000_000, 60),
                    new EnhancedVideoProfile(1280, 720, 4_000_000, 30),
                    low()
            );
        }

        private static List<EnhancedVideoProfile> softTargets() {
            return List.of(
                    new EnhancedVideoProfile(1920, 1080, 6_000_000, 30),
                    new EnhancedVideoProfile(1280, 720, 3_000_000, 30),
                    low()
            );
        }

        private static EnhancedVideoProfile low() {
            return new EnhancedVideoProfile(854, 480, 1_500_000, 30);
        }

        private EnhancedVideoProfile withFrameRate(int frameRate) {
            return new EnhancedVideoProfile(width, height, bitrate, frameRate);
        }

        private EnhancedVideoProfile withBitrate(int bitrate) {
            return new EnhancedVideoProfile(width, height, bitrate, frameRate);
        }
    }

    private record DisplayProfile(int width, int height) {

        private boolean supports(EnhancedVideoProfile profile) {
            return width >= profile.width() && height >= profile.height();
        }
    }

    private record CodecVideoProfile(String name, EnhancedVideoProfile profile, boolean performancePoint) implements Comparable<CodecVideoProfile> {

        private static CodecVideoProfile unsupported() {
            return new CodecVideoProfile("none", new EnhancedVideoProfile(0, 0, 0, 0), false);
        }

        private boolean supported() {
            return !"none".equals(name);
        }

        private String profileText() {
            return supported() ? profile.width() + "x" + profile.height() + "@" + profile.frameRate() : "unsupported";
        }

        @Override
        public int compareTo(CodecVideoProfile other) {
            int pixels = Integer.compare(profile.width() * profile.height(), other.profile.width() * other.profile.height());
            if (pixels != 0) return pixels;
            int frameRate = Integer.compare(profile.frameRate(), other.profile.frameRate());
            if (frameRate != 0) return frameRate;
            int bitrate = Integer.compare(profile.bitrate(), other.profile.bitrate());
            if (bitrate != 0) return bitrate;
            return Boolean.compare(performancePoint, other.performancePoint);
        }
    }
}
