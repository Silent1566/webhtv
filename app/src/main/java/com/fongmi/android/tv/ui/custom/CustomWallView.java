package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.palette.graphics.Palette;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewWallBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.crawler.SpiderDebug;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class CustomWallView extends FrameLayout implements DefaultLifecycleObserver {

    private static final int DEFAULT_WALL_COLOR = Setting.getBuiltInWallColor(Setting.WALL_DREAM_PURPLE);
    private static final int GREEN_WALL_COLOR = 0xFF40C090;
    private static final int MAX_WALL_BITMAP_SIDE = 1920;
    private static final int TYPE_RES = 0;
    private static final int TYPE_GIF = 1;
    private static final int TYPE_VIDEO = 2;
    private ViewWallBinding binding;
    private GifDrawable drawable;
    private PlayerView video;
    private ExoPlayer player;
    private final Runnable refreshRunnable = this::refresh;
    private boolean observerAdded;
    private boolean motionEnabled = true;

    public CustomWallView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomWallView setMotionEnabled(boolean motionEnabled) {
        this.motionEnabled = motionEnabled;
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) return;
        boolean loadedPlaceholder = false;
        if (binding == null) {
            binding = ViewWallBinding.inflate(LayoutInflater.from(getContext()), this, true);
            loadPlaceholder();
            loadedPlaceholder = true;
        }
        if (!observerAdded) {
            ((ComponentActivity) getContext()).getLifecycle().addObserver(this);
            observerAdded = true;
        }
        removeCallbacks(refreshRunnable);
        if (loadedPlaceholder && isStaticBuiltInWall()) theme();
        else post(refreshRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(refreshRunnable);
        super.onDetachedFromWindow();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) refresh();
    }

    private void refresh() {
        if (!isReady()) return;
        long start = System.currentTimeMillis();
        stop();
        load();
        theme();
        SpiderDebug.log("startup", "wall refresh cost=%sms", System.currentTimeMillis() - start);
    }

    private boolean isReady() {
        return binding != null && binding.image != null && isAttachedToWindow();
    }

    private void stop() {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.clearMediaItems();
        }
        if (video != null) {
            video.setPlayer(null);
            video.setVisibility(GONE);
        }
        if (drawable != null) {
            drawable.stop();
            drawable.recycle();
            drawable = null;
        }
    }

    private void load() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        if (isBuiltInColor(wall, type)) loadColor(Setting.getBuiltInWallColor(wall));
        else if (isBuiltInDesign(wall, type)) loadDesign(wall);
        else if (isGreen(wall, type)) loadRes(R.drawable.wallpaper_1);
        else if (motionEnabled && type == TYPE_VIDEO) loadVideo(FileUtil.getWall(wall));
        else if (motionEnabled && type == TYPE_GIF) loadGif(FileUtil.getWall(wall));
        else loadImage();
    }

    private void theme() {
        int newColor = getWallColor();
        int oldColor = Setting.getWallColor();
        if (newColor == oldColor) return;
        Setting.putWallColor(newColor);
        if (Setting.getThemeColor() == 0) RefreshEvent.theme();
    }

    private void loadRes(int resId) {
        if (!isReady()) return;
        binding.image.setImageResource(resId);
    }

    private void loadColor(int color) {
        if (!isReady()) return;
        binding.image.setImageDrawable(new ColorDrawable(color));
    }

    private void loadDesign(int wall) {
        if (!isReady()) return;
        int resId = getDesignResId(wall);
        if (resId != 0) binding.image.setImageResource(resId);
        else loadColor(Setting.getBuiltInWallColor(wall));
    }

    private void loadImage() {
        if (!isReady()) return;
        Drawable cache = cache();
        if (cache != null) binding.image.setImageDrawable(cache);
        else loadPlaceholder();
    }

    private void loadPlaceholder() {
        if (binding == null || binding.image == null) return;
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        Drawable cache = cache();
        if (isBuiltInColor(wall, type)) binding.image.setImageDrawable(new ColorDrawable(Setting.getBuiltInWallColor(wall)));
        else if (isBuiltInDesign(wall, type)) loadDesign(wall);
        else if (isGreen(wall, type)) binding.image.setImageResource(R.drawable.wallpaper_1);
        else if (cache != null) binding.image.setImageDrawable(cache);
        else binding.image.setImageDrawable(new ColorDrawable(DEFAULT_WALL_COLOR));
    }

    private int getDesignResId(int wall) {
        return switch (wall) {
            case Setting.WALL_AURORA_GLASS -> R.drawable.wallpaper_design_10_aurora_glass;
            case Setting.WALL_SUNSET_PRISM -> R.drawable.wallpaper_design_11_sunset_prism;
            case Setting.WALL_MINT_GLACIER -> R.drawable.wallpaper_design_12_mint_glacier;
            case Setting.WALL_LIQUID_CHROME -> R.drawable.wallpaper_design_13_liquid_chrome;
            case Setting.WALL_NEON_BERRY -> R.drawable.wallpaper_design_14_neon_berry;
            case Setting.WALL_CHAMPAGNE_MIST -> R.drawable.wallpaper_design_15_champagne_mist;
            case Setting.WALL_GLASS_GRADIENT -> R.drawable.wallpaper_design_16_glass_gradient;
            case Setting.WALL_DEEP_SPACE_GLASS -> R.drawable.wallpaper_design_17_deep_space_glass;
            case Setting.WALL_POLAR_LIGHT_GLASS -> R.drawable.wallpaper_design_18_polar_light_glass;
            case Setting.WALL_NEON_CYBER -> R.drawable.wallpaper_design_19_neon_cyber;
            case Setting.WALL_WARM_MOON_GLASS -> R.drawable.wallpaper_design_20_warm_moon_glass;
            case Setting.WALL_CRYSTAL_SKY -> R.drawable.wallpaper_design_21_crystal_sky;
            case Setting.WALL_DREAM_PURPLE -> R.drawable.wallpaper_design_22_dream_purple;
            case Setting.WALL_SKY_MINT -> R.drawable.wallpaper_design_23_sky_mint;
            case Setting.WALL_FOREST_MIST -> R.drawable.wallpaper_design_24_forest_mist;
            case Setting.WALL_DAYLIGHT_MINIMAL -> R.drawable.wallpaper_design_25_daylight_minimal;
            case Setting.WALL_DEEP_SEA -> R.drawable.wallpaper_design_26_deep_sea;
            case Setting.WALL_VIOLET_SMOKE -> R.drawable.wallpaper_design_27_violet_smoke;
            case Setting.WALL_ROSE_VEIL -> R.drawable.wallpaper_design_28_rose_veil;
            case Setting.WALL_EMERALD_AURORA -> R.drawable.wallpaper_design_29_emerald_aurora;
            case Setting.WALL_BLUE_SILK -> R.drawable.wallpaper_design_30_blue_silk;
            case Setting.WALL_PEACH_DAWN -> R.drawable.wallpaper_design_31_peach_dawn;
            case Setting.WALL_GRAPHITE_SMOKE -> R.drawable.wallpaper_design_32_graphite_smoke;
            case Setting.WALL_PASTEL_PRISM -> R.drawable.wallpaper_design_33_pastel_prism;
            case Setting.WALL_MIDNIGHT_MOON -> R.drawable.wallpaper_design_34_midnight_moon;
            case Setting.WALL_CYAN_CRYSTAL -> R.drawable.wallpaper_design_35_cyan_crystal;
            case Setting.WALL_LAVENDER_CRYSTAL -> R.drawable.wallpaper_design_36_lavender_crystal;
            default -> 0;
        };
    }

    private void loadVideo(File file) {
        if (!isReady()) return;
        ensurePlayer();
        ensureVideoView();
        video.setPlayer(player);
        video.setVisibility(VISIBLE);
        binding.image.setImageDrawable(cache());
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
    }

    private void loadGif(File file) {
        if (!isReady()) return;
        drawable = gif(file);
        if (drawable != null) binding.image.setImageDrawable(drawable);
        else loadImage();
    }

    private Drawable cache() {
        File file = FileUtil.getWallCache();
        Bitmap bitmap = file.exists() ? decodeWallBitmap(file) : null;
        return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
    }

    private Bitmap decodeWallBitmap(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = getWallSampleSize(bounds);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inDither = true;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError | RuntimeException e) {
            SpiderDebug.log("startup", "wall bitmap decode fallback file=%s error=%s", file.getName(), e.getClass().getSimpleName());
            return null;
        }
    }

    private int getWallSampleSize(BitmapFactory.Options bounds) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int reqWidth = Math.max(1, Math.min(metrics.widthPixels, MAX_WALL_BITMAP_SIDE));
        int reqHeight = Math.max(1, Math.min(metrics.heightPixels, MAX_WALL_BITMAP_SIDE));
        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > reqWidth || bounds.outHeight / sampleSize > reqHeight) sampleSize *= 2;
        return sampleSize;
    }

    private GifDrawable gif(File file) {
        try {
            return new GifDrawable(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        player.mute();
    }

    private void ensureVideoView() {
        if (video != null) return;
        video = (PlayerView) LayoutInflater.from(getContext()).inflate(R.layout.view_wall_video, this, false);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private boolean hasVideo() {
        return player != null && video != null && video.getVisibility() == VISIBLE && player.getMediaItemCount() > 0;
    }

    private int getWallColor() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        if (type == TYPE_RES && Setting.isBuiltInWall(wall)) return Setting.getBuiltInWallColor(wall);
        if (isGreen(wall, type)) return GREEN_WALL_COLOR;
        File file = FileUtil.getWallCache();
        return file.exists() ? paletteColor(file) : DEFAULT_WALL_COLOR;
    }

    private int paletteColor(File file) {
        Bitmap bitmap = decodeBitmap(file);
        if (bitmap == null) return DEFAULT_WALL_COLOR;
        Palette palette = Palette.from(bitmap).maximumColorCount(8).generate();
        bitmap.recycle();
        return swatchColor(palette);
    }

    private Bitmap decodeBitmap(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 8;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private int swatchColor(Palette palette) {
        Palette.Swatch swatch = palette.getVibrantSwatch();
        if (swatch == null) swatch = palette.getDominantSwatch();
        return swatch != null ? swatch.getRgb() : DEFAULT_WALL_COLOR;
    }

    private boolean isBuiltInColor(int wall, int type) {
        return type == TYPE_RES && Setting.isBuiltInColorWall(wall);
    }

    private boolean isBuiltInDesign(int wall, int type) {
        return type == TYPE_RES && Setting.isBuiltInDesignWall(wall);
    }

    private boolean isGreen(int wall, int type) {
        return type == TYPE_RES && wall == Setting.WALL_GREEN;
    }

    private boolean isStaticBuiltInWall() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        return isBuiltInColor(wall, type) || isBuiltInDesign(wall, type) || isGreen(wall, type);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.start();
        if (!hasVideo()) return;
        video.setPlayer(player);
        player.play();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.pause();
        if (!hasVideo()) return;
        video.setPlayer(null);
        player.pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        removeCallbacks(refreshRunnable);
        EventBus.getDefault().unregister(this);
        if (drawable != null) drawable.recycle();
        if (video != null) removeView(video);
        if (player != null) player.release();
        observerAdded = false;
        drawable = null;
        binding = null;
        player = null;
        video = null;
    }
}
