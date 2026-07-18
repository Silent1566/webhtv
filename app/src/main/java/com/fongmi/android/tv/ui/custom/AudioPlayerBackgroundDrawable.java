package com.fongmi.android.tv.ui.custom;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.setting.PlayerSetting;

public class AudioPlayerBackgroundDrawable extends Drawable {

    private final Paint paint;
    private final Path path;
    private final int style;
    private final int artworkColor;
    private final boolean decorated;
    private final boolean lightEffect;
    private boolean animated;
    private final int backgroundSeed;
    private final int decorationSeed;
    private int alpha;
    private float recordHaloCenterX = Float.NaN;
    private float recordHaloCenterY = Float.NaN;
    private float recordHaloRadius = Float.NaN;
    private final Runnable frameCallback = this::invalidateSelf;

    public AudioPlayerBackgroundDrawable(int style, int artworkColor) {
        this(style, artworkColor, true);
    }

    public AudioPlayerBackgroundDrawable(int style, int artworkColor, boolean decorated) {
        this(style, artworkColor, decorated, 0);
    }

    public AudioPlayerBackgroundDrawable(int style, int artworkColor, boolean decorated, int seed) {
        this(style, artworkColor, decorated, seed, seed);
    }

    public AudioPlayerBackgroundDrawable(int style, int artworkColor, boolean decorated, int backgroundSeed, int decorationSeed) {
        this(style, artworkColor, decorated, false, false, backgroundSeed, decorationSeed);
    }

    public AudioPlayerBackgroundDrawable(int style, int artworkColor, boolean decorated, boolean lightEffect, int backgroundSeed, int decorationSeed) {
        this(style, artworkColor, decorated, lightEffect, false, backgroundSeed, decorationSeed);
    }

    public AudioPlayerBackgroundDrawable(int style, int artworkColor, boolean decorated, boolean lightEffect, boolean animated, int backgroundSeed, int decorationSeed) {
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.path = new Path();
        this.style = style;
        this.artworkColor = artworkColor;
        this.decorated = decorated;
        this.lightEffect = lightEffect;
        this.animated = animated;
        this.backgroundSeed = backgroundSeed;
        this.decorationSeed = decorationSeed;
        this.alpha = 255;
    }

    public void setRecordHaloAnchor(float centerX, float centerY, float radius) {
        if (centerX <= 0 || centerY <= 0 || radius <= 0) return;
        this.recordHaloCenterX = centerX;
        this.recordHaloCenterY = centerY;
        this.recordHaloRadius = radius;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.translate(b.left, b.top);
        drawOpaqueBase(canvas, w, h);
        switch (style) {
            case PlayerSetting.AUDIO_BACKGROUND_DARK_NEON -> drawDarkNeon(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_BLACK_GOLD -> drawBlackGold(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_SUNSET -> drawSunset(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_MINT -> drawMint(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_CANDY -> drawCandy(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_SKY -> drawSky(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_ROSE -> drawRose(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_CYBER -> drawCyber(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_FOREST -> drawForest(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_LEMON -> drawLemon(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_DUSK -> drawDusk(canvas, w, h);
            case PlayerSetting.AUDIO_BACKGROUND_RANDOM -> drawRandom(canvas, w, h);
            default -> drawArtwork(canvas, w, h);
        }
        drawReadability(canvas, w, h);
        if (lightEffect) drawLightEffect(canvas, w, h);
        canvas.restore();
        if (lightEffect && animated) scheduleSelf(frameCallback, SystemClock.uptimeMillis() + 66L);
    }

    private void drawOpaqueBase(Canvas canvas, int w, int h) {
        paint.setAlpha(255);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(opaqueBaseColor());
        canvas.drawRect(0, 0, w, h, paint);
    }

    private int opaqueBaseColor() {
        return switch (style) {
            case PlayerSetting.AUDIO_BACKGROUND_DARK_NEON -> 0xFF070A18;
            case PlayerSetting.AUDIO_BACKGROUND_BLACK_GOLD -> 0xFF060608;
            case PlayerSetting.AUDIO_BACKGROUND_SUNSET -> 0xFF7F3A5E;
            case PlayerSetting.AUDIO_BACKGROUND_MINT -> 0xFF21AFA7;
            case PlayerSetting.AUDIO_BACKGROUND_CANDY -> 0xFF8D43C8;
            case PlayerSetting.AUDIO_BACKGROUND_SKY -> 0xFF277BC4;
            case PlayerSetting.AUDIO_BACKGROUND_ROSE -> 0xFF9D3E7A;
            case PlayerSetting.AUDIO_BACKGROUND_CYBER -> 0xFF1435A8;
            case PlayerSetting.AUDIO_BACKGROUND_FOREST -> 0xFF1B7C6B;
            case PlayerSetting.AUDIO_BACKGROUND_LEMON -> 0xFF65B879;
            case PlayerSetting.AUDIO_BACKGROUND_DUSK -> 0xFF5C4AC4;
            case PlayerSetting.AUDIO_BACKGROUND_RANDOM -> randomBackgroundColor(backgroundSeed == 0 ? 0x5A17B3 : backgroundSeed, 1);
            default -> vivid(artworkColor, 1.0f, 0.82f);
        };
    }

    private void drawArtwork(Canvas canvas, int w, int h) {
        int c1 = vivid(artworkColor, 1.12f, 1.15f);
        int c2 = rotate(c1, 38f, 0.92f, 0.95f);
        int c3 = rotate(c1, 196f, 0.78f, 0.82f);
        fillLinear(canvas, w, h, c1, c2, c3, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.18f, h * 0.18f, Math.max(w, h) * 0.68f, withAlpha(Color.WHITE, 70), Color.TRANSPARENT);
        drawWave(canvas, w, h, h * 0.72f, h * 0.12f, withAlpha(c3, 130), withAlpha(c2, 30));
    }

    private void drawDarkNeon(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF070A18, 0xFF111B46, 0xFF070812, true);
        if (!decorated) return;
        drawRibbon(canvas, w, h, -0.08f, 0.24f, 0xCC19E6FF, 0x2219E6FF);
        drawRibbon(canvas, w, h, 0.28f, 0.56f, 0xCCFF3AE0, 0x22FF3AE0);
        drawGrid(canvas, w, h, 0x2248D8FF, Math.max(18, w / 18));
    }

    private void drawBlackGold(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF060608, 0xFF211808, 0xFF0A0704, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.78f, h * 0.18f, Math.max(w, h) * 0.62f, 0x99FFC857, Color.TRANSPARENT);
        drawVinyl(canvas, w * 0.26f, h * 0.3f, Math.min(w, h) * 0.5f, 0x44FFFFFF, 0x66FFC857);
        drawRibbon(canvas, w, h, 0.48f, 0.74f, 0xAAFFC857, 0x18FFC857);
    }

    private void drawSunset(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFFFF6A3D, 0xFFFFD36E, 0xFF7F63FF, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.72f, h * 0.28f, Math.min(w, h) * 0.28f, 0xDDFFF3A3, Color.TRANSPARENT);
        drawHorizon(canvas, w, h, 0x663A1D52, 0.62f, 0.1f);
        drawWave(canvas, w, h, h * 0.78f, h * 0.08f, 0x667B61FF, 0x11FFFFFF);
    }

    private void drawMint(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF21D4B4, 0xFFB7F66E, 0xFF35A7FF, true);
        if (!decorated) return;
        drawWave(canvas, w, h, h * 0.38f, h * 0.09f, 0x88FFFFFF, 0x2242E695);
        drawWave(canvas, w, h, h * 0.58f, h * 0.12f, 0x662DD4BF, 0x2235A7FF);
        drawFineLines(canvas, w, h, 0x33FFFFFF, false);
    }

    private void drawCandy(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFFFF4FA3, 0xFFAC5CFF, 0xFF25D8FF, false);
        if (!decorated) return;
        drawCandyStripes(canvas, w, h);
        fillRadial(canvas, w * 0.12f, h * 0.15f, Math.min(w, h) * 0.26f, 0x88FFFFFF, Color.TRANSPARENT);
    }

    private void drawSky(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF26B8FF, 0xFFFFCA45, 0xFFFF706A, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.22f, h * 0.2f, Math.min(w, h) * 0.26f, 0xDDFFF6B8, Color.TRANSPARENT);
        drawCloudBand(canvas, w, h, 0.34f, 0x70FFFFFF);
        drawHorizon(canvas, w, h, 0x55455DAA, 0.74f, 0.06f);
    }

    private void drawRose(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFFFF4D87, 0xFFFFB3C7, 0xFF817CFF, true);
        if (!decorated) return;
        drawWave(canvas, w, h, h * 0.34f, h * 0.16f, 0x88FFFFFF, 0x18FFFFFF);
        drawWave(canvas, w, h, h * 0.66f, h * 0.18f, 0x77D83378, 0x11817CFF);
        drawFineLines(canvas, w, h, 0x26FFFFFF, true);
    }

    private void drawCyber(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF00D4FF, 0xFFFF2FB3, 0xFFFFF25C, true);
        if (!decorated) return;
        drawGrid(canvas, w, h, 0x55FFFFFF, Math.max(22, w / 14));
        drawRibbon(canvas, w, h, 0.14f, 0.4f, 0xAA001B34, 0x22001B34);
        drawRibbon(canvas, w, h, 0.62f, 0.88f, 0x88FFFFFF, 0x18FFFFFF);
    }

    private void drawForest(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF1BA784, 0xFFFFD166, 0xFF3A86FF, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.2f, h * 0.16f, Math.min(w, h) * 0.24f, 0xCCFFF3A6, Color.TRANSPARENT);
        drawLeaf(canvas, w, h, 0.68f, 0.2f, 0x6642E695);
        drawLeaf(canvas, w, h, 0.82f, 0.48f, 0x5547C06B);
        drawHorizon(canvas, w, h, 0x77325F4A, 0.72f, 0.09f);
    }

    private void drawLemon(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFFF9F871, 0xFF42E695, 0xFF3BB2FF, false);
        if (!decorated) return;
        drawCitrus(canvas, w * 0.76f, h * 0.22f, Math.min(w, h) * 0.26f);
        drawCandyStripes(canvas, w, h);
        drawWave(canvas, w, h, h * 0.76f, h * 0.1f, 0x663BB2FF, 0x11FFFFFF);
    }

    private void drawDusk(Canvas canvas, int w, int h) {
        fillLinear(canvas, w, h, 0xFF6A5CFF, 0xFFFF7A59, 0xFFFFD166, false);
        if (!decorated) return;
        fillRadial(canvas, w * 0.68f, h * 0.22f, Math.min(w, h) * 0.22f, 0xAAFFE5A3, Color.TRANSPARENT);
        drawMountain(canvas, w, h, 0.62f, 0x77301954);
        drawMountain(canvas, w, h, 0.74f, 0x884A1841);
    }

    private void drawRandom(Canvas canvas, int w, int h) {
        int bg = backgroundSeed == 0 ? 0x5A17B3 : backgroundSeed;
        int deco = decorationSeed == 0 ? bg : decorationSeed;
        int start = randomBackgroundColor(bg, 0);
        int center = randomBackgroundColor(bg, 1);
        int end = randomBackgroundColor(bg, 2);
        boolean reverse = (bg & 1) == 0;
        fillLinear(canvas, w, h, start, center, end, reverse);
        if (!decorated) return;
        int motif = Math.floorMod(mixSeed(deco), 24);
        int accent = randomColor(deco, 3, 0.58f, 0.96f);
        int accent2 = randomColor(deco, 4, 0.5f, 0.88f);
        int glow = randomColor(deco, 5, 0.28f, 0.98f);
        float x1 = randomRange(deco, 6, 0.14f, 0.86f);
        float y1 = randomRange(deco, 7, 0.12f, 0.62f);
        float x2 = randomRange(deco, 8, 0.2f, 0.9f);
        float y2 = randomRange(deco, 9, 0.34f, 0.86f);
        switch (motif) {
            case 0 -> {
                fillRadial(canvas, w * 0.14f, h * 0.2f, Math.max(w, h) * 0.56f, withAlpha(accent, 118), Color.TRANSPARENT);
                fillRadial(canvas, w * 0.86f, h * 0.18f, Math.max(w, h) * 0.5f, withAlpha(accent2, 92), Color.TRANSPARENT);
                drawMemphisPop(canvas, w, h, deco, accent, accent2, glow);
            }
            case 1 -> {
                drawCheckerPoster(canvas, w, h, deco, accent, accent2);
                drawSoftBeam(canvas, w, h, deco + 9, withAlpha(Color.WHITE, 32));
                fillRadial(canvas, w * x1, h * y1, Math.max(w, h) * 0.42f, withAlpha(Color.WHITE, 30), Color.TRANSPARENT);
            }
            case 2 -> {
                fillRadial(canvas, w * x2, h * y1, Math.max(w, h) * randomRange(deco, 10, 0.42f, 0.68f), withAlpha(glow, 132), Color.TRANSPARENT);
                drawChromeShards(canvas, w, h, deco, accent, accent2, Color.WHITE);
            }
            case 3 -> {
                drawNeonFrame(canvas, w, h, deco, accent, accent2);
                drawDotMatrix(canvas, w, h, deco, withAlpha(Color.WHITE, 42), false);
                fillRadial(canvas, w * 0.18f, h * 0.82f, Math.max(w, h) * 0.4f, withAlpha(accent2, 58), Color.TRANSPARENT);
            }
            case 4 -> {
                drawBauhausBlocks(canvas, w, h, deco, accent, accent2, glow);
                fillRadial(canvas, w * 0.52f, h * 0.86f, Math.max(w, h) * 0.46f, withAlpha(glow, 64), Color.TRANSPARENT);
            }
            case 5 -> {
                drawPixelHalftone(canvas, w, h, deco, accent, accent2);
                drawSoftVeil(canvas, w, h, deco + 19, withAlpha(Color.WHITE, 24), withAlpha(accent2, 24));
            }
            case 6 -> {
                drawFashionStripes(canvas, w, h, deco, withAlpha(accent, 72), withAlpha(accent2, 54));
                fillRadial(canvas, w * x1, h * y2, Math.max(w, h) * 0.42f, withAlpha(Color.WHITE, 24), Color.TRANSPARENT);
            }
            case 7 -> {
                drawEditorialTape(canvas, w, h, deco, accent, accent2);
                drawStarDust(canvas, w, h, deco + 37, 36, withAlpha(Color.WHITE, 54), true);
                fillRadial(canvas, w * 0.5f, h * 0.5f, Math.max(w, h) * 0.36f, withAlpha(accent2, 38), Color.TRANSPARENT);
            }
            case 8 -> {
                drawPerspectivePosterGrid(canvas, w, h, deco, accent, accent2);
                drawSoftCurrent(canvas, w, h, deco, randomRange(deco, 10, 0.62f, 0.82f), withAlpha(accent2, 34));
            }
            case 9 -> {
                fillRadial(canvas, w * x2, h * y1, Math.max(w, h) * 0.44f, withAlpha(accent, 98), Color.TRANSPARENT);
                drawContourLines(canvas, w, h, deco, withAlpha(Color.WHITE, 54), withAlpha(accent2, 48));
                drawTopographicLabels(canvas, w, h, deco, withAlpha(accent, 48));
            }
            case 10 -> {
                fillRadial(canvas, w * 0.18f, h * 0.12f, Math.max(w, h) * 0.62f, withAlpha(Color.WHITE, 42), Color.TRANSPARENT);
                drawGlamStarburst(canvas, w, h, deco, accent, accent2);
            }
            case 11 -> {
                drawHoloShards(canvas, w, h, deco, accent, accent2, glow);
            }
            case 12 -> {
                drawGrid(canvas, w, h, withAlpha(Color.WHITE, 38), Math.max(34, w / 9));
                drawSoftVeil(canvas, w, h, deco, withAlpha(accent, 32), withAlpha(Color.WHITE, 22));
                drawStarDust(canvas, w, h, deco + 47, 36, withAlpha(Color.WHITE, 54), true);
            }
            case 13 -> {
                drawContourLines(canvas, w, h, deco, withAlpha(Color.WHITE, 46), withAlpha(accent2, 36));
                drawSoftBeam(canvas, w, h, deco + 11, withAlpha(accent, 46));
                fillRadial(canvas, w * 0.82f, h * 0.18f, Math.max(w, h) * 0.42f, withAlpha(Color.WHITE, 28), Color.TRANSPARENT);
            }
            case 14 -> {
                drawDotMatrix(canvas, w, h, deco, withAlpha(Color.WHITE, 54), true);
                drawSoftCurrent(canvas, w, h, deco, randomRange(deco, 10, 0.34f, 0.56f), withAlpha(accent, 38));
                fillRadial(canvas, w * 0.2f, h * 0.84f, Math.max(w, h) * 0.42f, withAlpha(accent2, 52), Color.TRANSPARENT);
            }
            case 15 -> {
                drawBentoWire(canvas, w, h, deco, withAlpha(Color.WHITE, 42), withAlpha(accent2, 30));
                drawCrystalSlabs(canvas, w, h, deco + 53, withAlpha(Color.WHITE, 20), withAlpha(accent, 24));
                fillRadial(canvas, w * x1, h * y1, Math.max(w, h) * 0.46f, withAlpha(glow, 62), Color.TRANSPARENT);
            }
            case 16 -> {
                drawDiagonalTexture(canvas, w, h, deco, withAlpha(Color.WHITE, 42));
                drawMistStreaks(canvas, w, h, deco + 17, 5, withAlpha(accent, 34));
                drawSoftFlow(canvas, w, h, deco, 0.36f, 0.74f, 0.18f, withAlpha(accent2, 40), withAlpha(Color.WHITE, 18));
            }
            case 17 -> {
                drawOrthoGrid(canvas, w, h, deco, withAlpha(Color.WHITE, 30));
                drawDotMatrix(canvas, w, h, deco + 71, withAlpha(accent, 40), false);
                drawSoftBeam(canvas, w, h, deco + 3, withAlpha(Color.WHITE, 30));
            }
            case 18 -> {
                drawSoftFlow(canvas, w, h, deco, 0.12f, 0.42f, 0.2f, withAlpha(Color.WHITE, 46), withAlpha(accent, 42));
                drawSoftFlow(canvas, w, h, deco + 41, 0.48f, 0.8f, 0.22f, withAlpha(accent2, 56), withAlpha(Color.WHITE, 22));
                drawSoftVeil(canvas, w, h, deco + 19, withAlpha(Color.WHITE, 22), withAlpha(accent2, 24));
            }
            case 19 -> {
                drawAuroraCurtain(canvas, w, h, deco, withAlpha(accent, 54), withAlpha(accent2, 38));
                drawMistStreaks(canvas, w, h, deco + 23, 5, withAlpha(Color.WHITE, 38));
                fillRadial(canvas, w * 0.5f, h * 0.5f, Math.max(w, h) * 0.36f, withAlpha(accent2, 38), Color.TRANSPARENT);
            }
            case 20 -> {
                drawSoftBeam(canvas, w, h, deco, withAlpha(accent, 78));
                drawSoftBeam(canvas, w, h, deco + 17, withAlpha(accent2, 52));
                drawDiagonalTexture(canvas, w, h, deco, withAlpha(Color.WHITE, 30));
            }
            case 21 -> {
                drawStarDust(canvas, w, h, deco, 96, withAlpha(Color.WHITE, 78), false);
                drawOrthoGrid(canvas, w, h, deco, withAlpha(Color.WHITE, 24));
                drawSoftCurrent(canvas, w, h, deco, randomRange(deco, 10, 0.62f, 0.82f), withAlpha(accent2, 34));
            }
            case 22 -> {
                drawSoftCurrent(canvas, w, h, deco, randomRange(deco, 10, 0.42f, 0.7f), withAlpha(accent, 72));
                drawSoftCurrent(canvas, w, h, deco + 31, randomRange(deco, 11, 0.58f, 0.84f), withAlpha(accent2, 52));
                drawDotMatrix(canvas, w, h, deco, withAlpha(Color.WHITE, 46), false);
            }
            default -> {
                fillRadial(canvas, w * x1, h * y1, Math.max(w, h) * 0.48f, withAlpha(Color.WHITE, 50), Color.TRANSPARENT);
                fillRadial(canvas, w * x2, h * y2, Math.max(w, h) * 0.44f, withAlpha(accent, 66), Color.TRANSPARENT);
                drawSoftCurrent(canvas, w, h, deco, randomRange(deco, 10, 0.4f, 0.68f), withAlpha(accent2, 42));
                drawContourLines(canvas, w, h, deco + 83, withAlpha(Color.WHITE, 34), withAlpha(accent, 28));
                drawStarDust(canvas, w, h, deco + 47, 46, withAlpha(Color.WHITE, 52), false);
            }
        }
    }

    private void fillLinear(Canvas canvas, int w, int h, int start, int center, int end, boolean reverse) {
        paint.setShader(new LinearGradient(reverse ? w : 0, 0, reverse ? 0 : w, h, new int[]{start, center, end}, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void fillRadial(Canvas canvas, float x, float y, float radius, int start, int end) {
        paint.setShader(new RadialGradient(x, y, radius, start, end, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    private void drawLightEffect(Canvas canvas, int w, int h) {
        if (!animated) return;
        long now = SystemClock.uptimeMillis();
        float phase = (float) ((Math.sin(now / 1450.0) + 1.0) * 0.5);
        float cycle = (now % 7800L) / 7800f;
        int seed = backgroundSeed == 0 ? artworkColor : backgroundSeed;
        int accent = style == PlayerSetting.AUDIO_BACKGROUND_ARTWORK ? vivid(artworkColor, 1.22f, 1.12f) : randomColor(seed, 12, 0.52f, 0.92f);
        int accent2 = rotate(accent, 58f + phase * 28f, 0.9f, 1f);
        int accent3 = rotate(accent, 142f + cycle * 36f, 0.82f, 1f);
        drawLightBloom(canvas, w, h, now, phase, accent, accent2);
        drawRecordHalo(canvas, w, h, now, phase, accent, accent3);
        drawAmbientSweep(canvas, w, h, now, phase, accent, accent2);
        int mode = Math.floorMod(mixSeed(seed ^ decorationSeed ^ style * 0x45D9F3B) + (int) (now / 7800L), 4);
        switch (mode) {
            case 0 -> drawLiquidSilk(canvas, w, h, now, phase, accent, accent2);
            case 1 -> drawParticleDrift(canvas, w, h, seed, now, accent, accent2, accent3);
            case 2 -> drawNebulaVeil(canvas, w, h, now, phase, accent, accent2, accent3);
            default -> drawCometTrails(canvas, w, h, seed, now, phase, accent, accent2);
        }
    }

    private void drawLightBloom(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float drift = (now % 11000L) / 11000f;
        float cx = w * (0.1f + 0.78f * drift);
        float cy = h * (0.22f + 0.1f * (float) Math.sin(now / 2100.0));
        float cx2 = w * (0.88f - 0.72f * ((now % 13700L) / 13700f));
        float cy2 = h * (0.68f + 0.14f * (float) Math.cos(now / 2600.0));
        fillRadial(canvas, cx, cy, Math.max(w, h) * 0.5f, withAlpha(Color.WHITE, 58 + (int) (phase * 36)), Color.TRANSPARENT);
        fillRadial(canvas, cx2, cy2, Math.max(w, h) * 0.6f, withAlpha(accent2, 82 + (int) (phase * 48)), Color.TRANSPARENT);
    }

    private void drawRecordHalo(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        boolean landscape = w > h * 1.18f;
        boolean anchored = !Float.isNaN(recordHaloCenterX) && !Float.isNaN(recordHaloCenterY) && !Float.isNaN(recordHaloRadius);
        float cx = anchored ? recordHaloCenterX : landscape ? w * 0.18f : w * 0.5f;
        float cy = anchored ? recordHaloCenterY : landscape ? h * 0.3f : h * 0.17f;
        float r = anchored ? recordHaloRadius * (1f + phase * 0.08f) : Math.min(w, h) * (0.17f + phase * 0.018f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            paint.setStrokeWidth(Math.max(1.6f, w * (0.0034f + i * 0.0019f)));
            paint.setColor(withAlpha(i == 0 ? Color.WHITE : i == 1 ? accent : accent2, 58 - i * 8));
            canvas.drawCircle(cx, cy, r + i * Math.min(w, h) * 0.026f, paint);
        }
        float angle = (now % 5200L) / 5200f * 360f;
        paint.setStrokeWidth(Math.max(3f, w * 0.009f));
        paint.setShader(new SweepGradient(cx, cy, new int[]{Color.TRANSPARENT, withAlpha(Color.WHITE, 150), withAlpha(accent2, 118), Color.TRANSPARENT}, new float[]{0f, 0.18f, 0.34f, 1f}));
        canvas.save();
        canvas.rotate(angle, cx, cy);
        canvas.drawCircle(cx, cy, r + Math.min(w, h) * 0.045f, paint);
        canvas.restore();
        paint.setShader(null);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawAmbientSweep(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float progress = (now % 9200L) / 9200f;
        float cx = w * (-0.28f + progress * 1.56f);
        float top = h * (0.08f + 0.12f * (float) Math.sin(now / 2600.0));
        float bottom = h * (0.86f + 0.08f * (float) Math.cos(now / 3100.0));
        float width = w * 0.34f;
        path.reset();
        path.moveTo(cx - width, top);
        path.cubicTo(cx - width * 0.2f, h * 0.24f, cx + width * 0.24f, h * 0.54f, cx - width * 0.08f, bottom);
        path.lineTo(cx + width * 0.9f, bottom);
        path.cubicTo(cx + width * 0.42f, h * 0.56f, cx + width * 0.68f, h * 0.24f, cx + width * 0.18f, top);
        path.close();
        paint.setShader(new LinearGradient(cx - width, top, cx + width, bottom, new int[]{Color.TRANSPARENT, withAlpha(accent2, 74 + (int) (phase * 38)), withAlpha(Color.WHITE, 86 + (int) (phase * 42)), withAlpha(accent, 74), Color.TRANSPARENT}, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawLiquidSilk(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float drift = (now % 8600L) / 8600f;
        drawSilkRibbon(canvas, w, h, drift, 0.28f, 0.09f, withAlpha(Color.WHITE, 108 + (int) (phase * 48)), withAlpha(accent, 128));
        drawSilkRibbon(canvas, w, h, (drift + 0.42f) % 1f, 0.56f, -0.08f, withAlpha(accent2, 126), withAlpha(Color.WHITE, 72));
    }

    private void drawSilkRibbon(Canvas canvas, int w, int h, float progress, float baseY, float lean, int coreColor, int edgeColor) {
        float x = w * (-0.38f + progress * 1.76f);
        float y = h * (baseY + 0.08f * (float) Math.sin(progress * Math.PI * 2.0));
        float thick = Math.max(h * 0.052f, w * 0.056f);
        path.reset();
        path.moveTo(x - w * 0.24f, y);
        path.cubicTo(x + w * 0.1f, y - h * (0.14f + lean), x + w * 0.38f, y + h * (0.18f - lean), x + w * 0.86f, y + h * lean);
        path.lineTo(x + w * 0.86f, y + h * lean + thick);
        path.cubicTo(x + w * 0.38f, y + h * (0.18f - lean) + thick, x + w * 0.1f, y - h * (0.14f + lean) + thick, x - w * 0.24f, y + thick);
        path.close();
        paint.setShader(new LinearGradient(x - w * 0.24f, y, x + w * 0.86f, y + thick, new int[]{Color.TRANSPARENT, edgeColor, coreColor, edgeColor, Color.TRANSPARENT}, new float[]{0f, 0.22f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawParticleDrift(Canvas canvas, int w, int h, int seed, long now, int accent, int accent2, int accent3) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float drift = (now % 9000L) / 9000f;
        for (int i = 0; i < 82; i++) {
            int mixed = mixSeed(seed + i * 131);
            float x = w * (randomRange(mixed, 1, -0.08f, 1.08f) + drift * randomRange(mixed, 3, -0.08f, 0.12f));
            float y = h * (randomRange(mixed, 2, 0.04f, 0.86f) + 0.028f * (float) Math.sin(now / 900.0 + i));
            if (x < -8f || x > w + 8f) x = Math.floorMod((int) x, Math.max(1, w));
            float twinkle = (float) ((Math.sin(now / randomRange(mixed, 4, 360.0f, 980.0f) + i * 0.7f) + 1.0) * 0.5);
            float size = Math.max(1.8f, Math.min(w, h) * randomRange(mixed, 5, 0.003f, 0.011f));
            int color = i % 4 == 0 ? Color.WHITE : i % 4 == 1 ? accent : i % 4 == 2 ? accent2 : accent3;
            paint.setStrokeWidth(Math.max(1f, size * 0.55f));
            paint.setColor(withAlpha(color, 48 + (int) (twinkle * 150)));
            if (i % 8 == 0) {
                canvas.drawLine(x - size, y, x + size, y, paint);
                canvas.drawLine(x, y - size, x, y + size, paint);
            } else {
                canvas.drawPoint(x, y, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNebulaVeil(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2, int accent3) {
        float drift = (now % 9800L) / 9800f;
        for (int i = 0; i < 4; i++) {
            float x = w * (-0.22f + ((drift + i * 0.27f) % 1.44f));
            float width = w * (0.22f + i * 0.045f);
            float lean = w * ((i % 2 == 0 ? 0.16f : -0.18f) + (phase - 0.5f) * 0.1f);
            path.reset();
            path.moveTo(x, -h * 0.08f);
            path.cubicTo(x + lean, h * 0.18f, x - lean * 0.35f, h * 0.5f, x + lean * 0.6f, h * 1.08f);
            path.lineTo(x + width + lean, h * 1.08f);
            path.cubicTo(x + width - lean * 0.25f, h * 0.58f, x + width + lean, h * 0.24f, x + width * 0.86f, -h * 0.08f);
            path.close();
            int color = i % 3 == 0 ? accent : i % 3 == 1 ? accent2 : accent3;
            paint.setShader(new LinearGradient(x, 0, x + lean, h, withAlpha(color, 88 + (int) (phase * 54)), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawCometTrails(Canvas canvas, int w, int h, int seed, long now, float phase, int accent, int accent2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float progress = (now % 6800L) / 6800f;
        for (int i = 0; i < 5; i++) {
            float offset = (progress + i * 0.19f) % 1f;
            float sx = w * (-0.18f + offset * 1.36f);
            float sy = h * randomRange(seed, 80 + i, 0.12f, 0.78f);
            float len = w * randomRange(seed, 90 + i, 0.16f, 0.34f);
            float lift = h * randomRange(seed, 100 + i, -0.06f, 0.08f);
            int color = i % 2 == 0 ? accent : accent2;
            paint.setStrokeWidth(Math.max(2.4f, w * randomRange(seed, 110 + i, 0.006f, 0.014f)));
            paint.setShader(new LinearGradient(sx - len, sy - lift, sx, sy, Color.TRANSPARENT, withAlpha(color, 132 + (int) (phase * 64)), Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(sx - len, sy - lift);
            path.cubicTo(sx - len * 0.62f, sy + lift, sx - len * 0.22f, sy - lift * 0.4f, sx, sy);
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLightFlow(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float sweep = (now % 5200L) / 5200f;
        drawFlowLight(canvas, w, h, sweep, withAlpha(Color.WHITE, 50 + (int) (phase * 30)), withAlpha(accent2, 36 + (int) (phase * 24)));
        drawFlowLight(canvas, w, h, (sweep + 0.48f) % 1f, withAlpha(accent, 34 + (int) (phase * 24)), withAlpha(Color.WHITE, 18));
    }

    private void drawLightAurora(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float drift = (now % 7200L) / 7200f;
        for (int i = 0; i < 3; i++) {
            float x = w * (-0.18f + ((drift + i * 0.31f) % 1.22f));
            float width = w * (0.18f + i * 0.035f);
            float lean = w * ((i % 2 == 0 ? 0.18f : -0.16f) + (phase - 0.5f) * 0.12f);
            path.reset();
            path.moveTo(x, -h * 0.08f);
            path.cubicTo(x + lean, h * 0.2f, x - lean * 0.45f, h * 0.48f, x + lean, h * 1.08f);
            path.lineTo(x + width + lean, h * 1.08f);
            path.cubicTo(x + width - lean * 0.35f, h * 0.58f, x + width + lean, h * 0.22f, x + width * 0.82f, -h * 0.08f);
            path.close();
            int color = i % 2 == 0 ? withAlpha(accent, 64 + (int) (phase * 34)) : withAlpha(accent2, 52 + (int) (phase * 30));
            paint.setShader(new LinearGradient(x, 0, x + lean, h, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawLightTwinkle(Canvas canvas, int w, int h, int seed, long now, float phase, int accent, int accent2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        int tick = (int) (now / 180L);
        for (int i = 0; i < 42; i++) {
            int mixed = mixSeed(seed + i * 97);
            float x = w * randomRange(mixed, 1, 0.04f, 0.96f);
            float y = h * randomRange(mixed, 2, 0.05f, 0.86f);
            float local = (float) ((Math.sin((tick + i * 11) * 0.32) + 1.0) * 0.5);
            float size = Math.max(1.8f, Math.min(w, h) * randomRange(mixed, 3, 0.0025f, 0.0085f));
            int color = i % 3 == 0 ? accent : i % 3 == 1 ? accent2 : Color.WHITE;
            paint.setStrokeWidth(Math.max(1f, size * 0.55f));
            paint.setColor(withAlpha(color, 30 + (int) (local * 118)));
            if (i % 7 == 0) {
                canvas.drawLine(x - size, y, x + size, y, paint);
                canvas.drawLine(x, y - size, x, y + size, paint);
            } else {
                canvas.drawPoint(x, y, paint);
            }
        }
        fillRadial(canvas, w * (0.18f + phase * 0.58f), h * 0.28f, Math.max(w, h) * 0.38f, withAlpha(accent2, 38), Color.TRANSPARENT);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLightPulse(Canvas canvas, int w, int h, int seed, long now, float phase, int accent, int accent2) {
        float cx = w * randomRange(seed, 31, 0.18f, 0.82f);
        float cy = h * randomRange(seed, 32, 0.18f, 0.66f);
        float base = Math.max(w, h) * (0.12f + (now % 2600L) / 2600f * 0.36f);
        fillRadial(canvas, cx, cy, base * 1.7f, withAlpha(accent, 44 + (int) (phase * 34)), Color.TRANSPARENT);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            float r = base + Math.max(w, h) * i * 0.13f;
            int alpha = Math.max(0, 92 - i * 18 - (int) (r / Math.max(w, h) * 54));
            paint.setStrokeWidth(Math.max(1.2f, w * (0.004f + i * 0.0016f)));
            paint.setColor(withAlpha(i % 2 == 0 ? accent : accent2, alpha));
            canvas.drawCircle(cx, cy, r, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLightPrism(Canvas canvas, int w, int h, int seed, long now, float phase, int accent, int accent2) {
        float drift = ((now % 6400L) / 6400f - 0.5f) * w * 0.28f;
        for (int i = 0; i < 4; i++) {
            float x = w * randomRange(seed, 51 + i, -0.1f, 0.82f) + drift * (i % 2 == 0 ? 1f : -0.8f);
            float y = h * randomRange(seed, 61 + i, 0.06f, 0.72f);
            float ww = w * randomRange(seed, 71 + i, 0.16f, 0.36f);
            float hh = h * randomRange(seed, 81 + i, 0.07f, 0.2f);
            path.reset();
            path.moveTo(x + ww * 0.18f, y);
            path.lineTo(x + ww, y + hh * (0.12f + phase * 0.18f));
            path.lineTo(x + ww * 0.78f, y + hh);
            path.lineTo(x, y + hh * 0.78f);
            path.close();
            paint.setShader(new LinearGradient(x, y, x + ww, y + hh, new int[]{withAlpha(Color.WHITE, 52), withAlpha(i % 2 == 0 ? accent : accent2, 108), withAlpha(Color.WHITE, 32)}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawLightWaves(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float shift = ((now % 5600L) / 5600f) * w;
        for (int i = 0; i < 3; i++) {
            float y = h * (0.34f + i * 0.17f + (phase - 0.5f) * 0.035f);
            float amp = h * (0.035f + i * 0.012f);
            path.reset();
            path.moveTo(-w * 0.18f, y);
            for (int p = 0; p <= 4; p++) {
                float x1 = -w * 0.18f + p * w * 0.34f - shift * 0.18f;
                path.cubicTo(x1 + w * 0.08f, y - amp, x1 + w * 0.18f, y + amp, x1 + w * 0.34f, y);
            }
            path.lineTo(w * 1.18f, y + amp * 2.2f);
            path.cubicTo(w * 0.68f, y + amp * 2.8f, w * 0.24f, y + amp * 1.1f, -w * 0.18f, y + amp * 2.1f);
            path.close();
            paint.setShader(new LinearGradient(0, y - amp, w, y + amp * 2.4f, i % 2 == 0 ? withAlpha(accent, 66) : withAlpha(accent2, 58), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawLightPresence(Canvas canvas, int w, int h, long now, float phase, int accent, int accent2) {
        float drift = (now % 7800L) / 7800f;
        float reverse = (now % 9400L) / 9400f;
        float cx = w * (0.12f + drift * 0.76f);
        float cy = h * (0.18f + (float) Math.sin(now / 1900.0) * 0.08f);
        float cx2 = w * (0.88f - reverse * 0.72f);
        float cy2 = h * (0.72f + (float) Math.cos(now / 2300.0) * 0.1f);
        fillRadial(canvas, cx, cy, Math.max(w, h) * 0.42f, withAlpha(Color.WHITE, 42 + (int) (phase * 28)), Color.TRANSPARENT);
        fillRadial(canvas, cx2, cy2, Math.max(w, h) * 0.5f, withAlpha(accent2, 66 + (int) (phase * 34)), Color.TRANSPARENT);
        drawCurvedLight(canvas, w, h, drift, withAlpha(Color.WHITE, 74), withAlpha(accent, 92));
    }

    private void drawCurvedLight(Canvas canvas, int w, int h, float progress, int coreColor, int edgeColor) {
        float startX = w * (-0.26f + progress * 1.42f);
        float y = h * (0.18f + 0.42f * (float) Math.sin(progress * Math.PI * 2.0));
        float thickness = Math.max(h * 0.045f, w * 0.055f);
        path.reset();
        path.moveTo(startX - w * 0.24f, y);
        path.cubicTo(startX + w * 0.04f, y - h * 0.18f, startX + w * 0.34f, y + h * 0.22f, startX + w * 0.74f, y + h * 0.02f);
        path.lineTo(startX + w * 0.74f, y + thickness);
        path.cubicTo(startX + w * 0.34f, y + h * 0.22f + thickness, startX + w * 0.04f, y - h * 0.18f + thickness, startX - w * 0.24f, y + thickness);
        path.close();
        paint.setShader(new LinearGradient(startX - w * 0.24f, y, startX + w * 0.74f, y + thickness, new int[]{Color.TRANSPARENT, edgeColor, coreColor, edgeColor, Color.TRANSPARENT}, new float[]{0f, 0.24f, 0.5f, 0.76f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawFlowLight(Canvas canvas, int w, int h, float progress, int color, int edgeColor) {
        float center = -w * 0.45f + progress * w * 1.9f;
        float width = Math.max(w * 0.075f, h * 0.045f);
        path.reset();
        path.moveTo(center - width, -h * 0.04f);
        path.lineTo(center + width * 0.18f, -h * 0.04f);
        path.lineTo(center + width * 1.05f, h * 1.04f);
        path.lineTo(center - width * 0.14f, h * 1.04f);
        path.close();
        paint.setShader(new LinearGradient(center - width, 0, center + width, h, new int[]{Color.TRANSPARENT, edgeColor, color, edgeColor, Color.TRANSPARENT}, new float[]{0f, 0.26f, 0.5f, 0.74f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawRibbon(Canvas canvas, int w, int h, float startY, float endY, int color, int edgeColor) {
        path.reset();
        path.moveTo(-w * 0.18f, h * startY);
        path.cubicTo(w * 0.18f, h * (startY - 0.1f), w * 0.42f, h * (endY + 0.08f), w * 1.18f, h * endY);
        path.lineTo(w * 1.18f, h * (endY + 0.12f));
        path.cubicTo(w * 0.48f, h * (endY + 0.02f), w * 0.18f, h * (startY + 0.18f), -w * 0.18f, h * (startY + 0.11f));
        path.close();
        paint.setShader(new LinearGradient(0, h * startY, w, h * endY, color, edgeColor, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawWave(Canvas canvas, int w, int h, float y, float amp, int color, int edgeColor) {
        path.reset();
        path.moveTo(0, y);
        path.cubicTo(w * 0.22f, y - amp, w * 0.36f, y + amp, w * 0.56f, y);
        path.cubicTo(w * 0.78f, y - amp, w * 0.9f, y + amp, w, y - amp * 0.2f);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        paint.setShader(new LinearGradient(0, y - amp, 0, h, color, edgeColor, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawGrid(Canvas canvas, int w, int h, int color, int step) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 420f));
        paint.setColor(color);
        for (int x = -w; x < w * 2; x += step) canvas.drawLine(x, 0, x + w / 2f, h, paint);
        for (int y = 0; y < h; y += step) canvas.drawLine(0, y, w, y + h * 0.08f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawVinyl(Canvas canvas, float cx, float cy, float r, int ringColor, int accentColor) {
        paint.setShader(new SweepGradient(cx, cy, new int[]{0x66000000, 0x22FFFFFF, 0x99000000, 0x22FFFFFF, 0x66000000}, null));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, r / 42f));
        paint.setColor(ringColor);
        for (int i = 1; i <= 5; i++) canvas.drawCircle(cx, cy, r * (0.28f + i * 0.11f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accentColor);
        canvas.drawCircle(cx, cy, r * 0.16f, paint);
    }

    private void drawHorizon(Canvas canvas, int w, int h, int color, float base, float variance) {
        path.reset();
        path.moveTo(0, h * base);
        path.lineTo(w * 0.22f, h * (base - variance));
        path.lineTo(w * 0.48f, h * (base + variance * 0.35f));
        path.lineTo(w * 0.72f, h * (base - variance * 0.6f));
        path.lineTo(w, h * (base + variance * 0.25f));
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawCloudBand(Canvas canvas, int w, int h, float y, int color) {
        paint.setColor(color);
        paint.setShader(null);
        canvas.drawOval(w * -0.08f, h * (y + 0.02f), w * 0.35f, h * (y + 0.18f), paint);
        canvas.drawOval(w * 0.18f, h * y, w * 0.72f, h * (y + 0.18f), paint);
        canvas.drawOval(w * 0.55f, h * (y + 0.04f), w * 1.1f, h * (y + 0.2f), paint);
    }

    private void drawFineLines(Canvas canvas, int w, int h, int color, boolean vertical) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 520f));
        paint.setColor(color);
        int step = Math.max(18, w / 16);
        for (int i = -step; i < (vertical ? w : h) + step; i += step) {
            if (vertical) canvas.drawLine(i, 0, i + w * 0.18f, h, paint);
            else canvas.drawLine(0, i, w, i + h * 0.16f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCandyStripes(Canvas canvas, int w, int h) {
        paint.setShader(null);
        int step = Math.max(48, w / 7);
        for (int i = -w; i < w * 2; i += step) {
            path.reset();
            path.moveTo(i, 0);
            path.lineTo(i + step * 0.38f, 0);
            path.lineTo(i + w * 0.7f + step * 0.38f, h);
            path.lineTo(i + w * 0.7f, h);
            path.close();
            paint.setColor(i % (step * 2) == 0 ? 0x38FFFFFF : 0x22000000);
            canvas.drawPath(path, paint);
        }
    }

    private void drawLeaf(Canvas canvas, int w, int h, float x, float y, int color) {
        path.reset();
        float cx = w * x;
        float cy = h * y;
        float rw = w * 0.18f;
        float rh = h * 0.12f;
        path.moveTo(cx, cy - rh);
        path.cubicTo(cx + rw, cy - rh * 0.6f, cx + rw, cy + rh * 0.7f, cx, cy + rh);
        path.cubicTo(cx - rw, cy + rh * 0.4f, cx - rw, cy - rh * 0.8f, cx, cy - rh);
        paint.setColor(color);
        paint.setShader(null);
        canvas.drawPath(path, paint);
    }

    private void drawCitrus(Canvas canvas, float cx, float cy, float r) {
        paint.setShader(new SweepGradient(cx, cy, new int[]{0xCCFFFFFF, 0x66FFF45A, 0xCCFFFFFF, 0x66FFF45A, 0xCCFFFFFF}, null));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xAAFFFFFF);
        paint.setStrokeWidth(Math.max(2f, r / 20f));
        canvas.drawCircle(cx, cy, r * 0.96f, paint);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            canvas.drawLine(cx, cy, cx + (float) Math.cos(a) * r * 0.9f, cy + (float) Math.sin(a) * r * 0.9f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMountain(Canvas canvas, int w, int h, float base, int color) {
        path.reset();
        path.moveTo(0, h * base);
        path.lineTo(w * 0.18f, h * (base - 0.12f));
        path.lineTo(w * 0.34f, h * (base - 0.04f));
        path.lineTo(w * 0.52f, h * (base - 0.18f));
        path.lineTo(w * 0.74f, h * (base - 0.03f));
        path.lineTo(w, h * (base - 0.1f));
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        paint.setColor(color);
        paint.setShader(null);
        canvas.drawPath(path, paint);
    }

    private void drawOrbitRings(Canvas canvas, float cx, float cy, float r, int seed, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 4; i++) {
            float scale = 0.72f + i * 0.28f + randomRange(seed, 20 + i, -0.05f, 0.06f);
            float stretch = randomRange(seed, 30 + i, 0.68f, 1.28f);
            paint.setStrokeWidth(Math.max(1.2f, r / (42f + i * 10f)));
            paint.setColor(withAlpha(color, Math.max(28, 120 - i * 22)));
            canvas.save();
            canvas.rotate(randomRange(seed, 40 + i, -22f, 22f), cx, cy);
            canvas.drawOval(cx - r * scale, cy - r * scale * stretch * 0.48f, cx + r * scale, cy + r * scale * stretch * 0.48f, paint);
            canvas.restore();
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSparkleField(Canvas canvas, int w, int h, int seed, int count, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float x = w * randomRange(seed, 80 + i * 2, -0.04f, 1.04f);
            float y = h * randomRange(seed, 81 + i * 2, 0.04f, 0.92f);
            float r = Math.max(1f, Math.min(w, h) * randomRange(seed, 120 + i, 0.002f, 0.008f));
            paint.setColor(withAlpha(color, 22 + Math.floorMod(mixSeed(seed + i * 17), 86)));
            canvas.drawCircle(x, y, r, paint);
        }
    }

    private void drawStaffLines(Canvas canvas, int w, int h, int seed, float centerY, int lineColor, int noteColor) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float spacing = h * randomRange(seed, 140, 0.022f, 0.036f);
        float y = h * centerY;
        paint.setStrokeWidth(Math.max(1f, w / 540f));
        paint.setColor(lineColor);
        for (int i = -2; i <= 2; i++) canvas.drawLine(w * 0.06f, y + i * spacing, w * 0.94f, y + i * spacing + h * randomRange(seed, 150 + i, -0.018f, 0.018f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(noteColor);
        int notes = 4 + Math.floorMod(mixSeed(seed + 160), 4);
        for (int i = 0; i < notes; i++) {
            float nx = w * randomRange(seed, 170 + i, 0.12f, 0.88f);
            float ny = y + spacing * randomRange(seed, 180 + i, -2.2f, 2.2f);
            float nr = Math.max(3f, w * randomRange(seed, 190 + i, 0.011f, 0.02f));
            canvas.drawOval(nx - nr * 1.35f, ny - nr, nx + nr * 1.35f, ny + nr, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawLightBeams(Canvas canvas, int w, int h, int seed, int color, int color2) {
        for (int i = 0; i < 3; i++) {
            float topX = w * randomRange(seed, 210 + i, 0.05f, 0.95f);
            float bottomX = w * randomRange(seed, 220 + i, -0.18f, 1.18f);
            float width = w * randomRange(seed, 230 + i, 0.18f, 0.34f);
            path.reset();
            path.moveTo(topX, h * randomRange(seed, 240 + i, -0.08f, 0.18f));
            path.lineTo(bottomX - width, h * 1.08f);
            path.lineTo(bottomX + width, h * 1.08f);
            path.close();
            paint.setShader(new LinearGradient(topX, 0, bottomX, h, i == 0 ? color : color2, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawConstellation(Canvas canvas, int w, int h, int seed, int pointColor, int lineColor) {
        int count = 7 + Math.floorMod(mixSeed(seed + 250), 5);
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = w * randomRange(seed, 260 + i, 0.08f, 0.92f);
            ys[i] = h * randomRange(seed, 280 + i, 0.1f, 0.72f);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 620f));
        paint.setColor(lineColor);
        for (int i = 1; i < count; i++) canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pointColor);
        for (int i = 0; i < count; i++) canvas.drawCircle(xs[i], ys[i], Math.max(2f, w * randomRange(seed, 300 + i, 0.004f, 0.01f)), paint);
    }

    private void drawGrain(Canvas canvas, int w, int h, int seed, int count, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            paint.setColor(withAlpha(color, 14 + Math.floorMod(mixSeed(seed + i * 19), 32)));
            canvas.drawCircle(w * randomRange(seed, 320 + i, 0f, 1f), h * randomRange(seed, 460 + i, 0f, 1f), Math.max(0.8f, w * randomRange(seed, 600 + i, 0.0012f, 0.003f)), paint);
        }
    }

    private void drawSoftFlow(Canvas canvas, int w, int h, int seed, float startY, float endY, float thickness, int color, int edgeColor) {
        float phase = randomRange(seed, 700, -0.12f, 0.12f);
        path.reset();
        path.moveTo(-w * 0.2f, h * (startY + phase));
        path.cubicTo(w * 0.18f, h * (startY - thickness * 0.55f), w * 0.52f, h * (endY - thickness * 0.25f), w * 1.2f, h * (endY + phase * 0.35f));
        path.lineTo(w * 1.2f, h * (endY + thickness));
        path.cubicTo(w * 0.62f, h * (endY + thickness * 0.45f), w * 0.28f, h * (startY + thickness * 0.85f), -w * 0.2f, h * (startY + thickness * 0.52f));
        path.close();
        paint.setShader(new LinearGradient(0, h * startY, w, h * endY, color, edgeColor, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawSoftCurrent(Canvas canvas, int w, int h, int seed, float y, int color) {
        path.reset();
        float amp = h * randomRange(seed, 720, 0.06f, 0.14f);
        float y0 = h * y;
        path.moveTo(-w * 0.12f, y0);
        path.cubicTo(w * 0.18f, y0 - amp, w * 0.42f, y0 + amp * 0.9f, w * 0.68f, y0 - amp * 0.22f);
        path.cubicTo(w * 0.88f, y0 - amp * 1.05f, w * 1.04f, y0 + amp * 0.28f, w * 1.12f, y0);
        path.lineTo(w * 1.12f, y0 + amp * 1.8f);
        path.cubicTo(w * 0.82f, y0 + amp * 2.2f, w * 0.48f, y0 + amp * 0.8f, -w * 0.12f, y0 + amp * 1.55f);
        path.close();
        paint.setShader(new LinearGradient(0, y0 - amp, w, y0 + amp * 2f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawSoftBeam(Canvas canvas, int w, int h, int seed, int color) {
        float topX = w * randomRange(seed, 740, 0.08f, 0.92f);
        float bottomX = w * randomRange(seed, 741, -0.1f, 1.1f);
        float width = w * randomRange(seed, 742, 0.28f, 0.54f);
        path.reset();
        path.moveTo(topX, h * randomRange(seed, 743, -0.12f, 0.18f));
        path.lineTo(bottomX - width, h * 1.08f);
        path.lineTo(bottomX + width, h * 1.08f);
        path.close();
        paint.setShader(new LinearGradient(topX, 0, bottomX, h, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawMemphisPop(Canvas canvas, int w, int h, int seed, int color, int color2, int color3) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        int count = 14;
        for (int i = 0; i < count; i++) {
            float x = w * randomRange(seed, 750 + i, 0.04f, 0.96f);
            float y = h * randomRange(seed, 780 + i, 0.08f, 0.86f);
            float s = Math.max(w, h) * randomRange(seed, 810 + i, 0.018f, 0.052f);
            int c = i % 3 == 0 ? color : i % 3 == 1 ? color2 : color3;
            paint.setColor(withAlpha(c, 38 + Math.floorMod(mixSeed(seed + i * 19), 58)));
            canvas.save();
            canvas.rotate(randomRange(seed, 840 + i, -28f, 28f), x, y);
            if (i % 4 == 0) {
                canvas.drawRect(x - s * 1.4f, y - s * 0.28f, x + s * 1.4f, y + s * 0.28f, paint);
            } else if (i % 4 == 1) {
                path.reset();
                path.moveTo(x, y - s);
                path.lineTo(x + s * 0.95f, y + s * 0.82f);
                path.lineTo(x - s * 0.95f, y + s * 0.82f);
                path.close();
                canvas.drawPath(path, paint);
            } else if (i % 4 == 2) {
                canvas.drawRect(x - s, y - s, x + s, y + s, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, w * 0.006f));
                canvas.drawLine(x - s, y - s, x + s, y + s, paint);
                canvas.drawLine(x + s, y - s, x - s, y + s, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            canvas.restore();
        }
    }

    private void drawCheckerPoster(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        float size = Math.max(26f, w * randomRange(seed, 860, 0.06f, 0.11f));
        float left = w * randomRange(seed, 861, -0.08f, 0.08f);
        float top = h * randomRange(seed, 862, 0.05f, 0.2f);
        int rows = 5 + Math.floorMod(mixSeed(seed + 863), 3);
        int cols = 4 + Math.floorMod(mixSeed(seed + 864), 3);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if ((x + y) % 2 != 0) continue;
                paint.setColor(withAlpha((x + y) % 4 == 0 ? color : color2, 46));
                canvas.drawRect(left + x * size, top + y * size, left + (x + 1) * size, top + (y + 1) * size, paint);
            }
        }
        drawDiagonalTexture(canvas, w, h, seed + 5, withAlpha(Color.WHITE, 22));
    }

    private void drawChromeShards(Canvas canvas, int w, int h, int seed, int color, int color2, int highlight) {
        for (int i = 0; i < 7; i++) {
            float x = w * randomRange(seed, 880 + i, -0.12f, 0.9f);
            float y = h * randomRange(seed, 900 + i, 0.06f, 0.78f);
            float ww = w * randomRange(seed, 920 + i, 0.18f, 0.42f);
            float hh = h * randomRange(seed, 940 + i, 0.08f, 0.22f);
            path.reset();
            path.moveTo(x + ww * 0.18f, y);
            path.lineTo(x + ww, y + hh * randomRange(seed, 960 + i, 0.06f, 0.34f));
            path.lineTo(x + ww * randomRange(seed, 980 + i, 0.58f, 0.92f), y + hh);
            path.lineTo(x, y + hh * randomRange(seed, 1000 + i, 0.42f, 0.9f));
            path.close();
            paint.setShader(new LinearGradient(x, y, x + ww, y + hh, new int[]{withAlpha(highlight, 68), withAlpha(i % 2 == 0 ? color : color2, 88), withAlpha(Color.WHITE, 32)}, new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.2f, w / 520f));
            paint.setColor(withAlpha(Color.WHITE, 54));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawNeonFrame(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        float margin = w * randomRange(seed, 1020, 0.05f, 0.12f);
        float top = h * randomRange(seed, 1021, 0.08f, 0.18f);
        float bottom = h * randomRange(seed, 1022, 0.72f, 0.9f);
        int[] colors = new int[]{withAlpha(color, 112), withAlpha(color2, 92), withAlpha(Color.WHITE, 42)};
        for (int i = 0; i < colors.length; i++) {
            paint.setStrokeWidth(Math.max(2f, w * (0.004f + i * 0.004f)));
            paint.setColor(colors[i]);
            float inset = i * w * 0.018f;
            canvas.drawLine(margin + inset, top + inset, w - margin - inset, top + inset, paint);
            canvas.drawLine(w - margin - inset, top + inset, w - margin - inset, bottom - inset, paint);
            canvas.drawLine(margin + inset, bottom - inset, w * randomRange(seed, 1030 + i, 0.46f, 0.82f), bottom - inset, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBauhausBlocks(Canvas canvas, int w, int h, int seed, int color, int color2, int color3) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            float x = w * randomRange(seed, 1040 + i, -0.08f, 0.9f);
            float y = h * randomRange(seed, 1060 + i, 0.06f, 0.82f);
            float ww = w * randomRange(seed, 1080 + i, 0.12f, 0.36f);
            float hh = h * randomRange(seed, 1100 + i, 0.05f, 0.16f);
            int c = i % 3 == 0 ? color : i % 3 == 1 ? color2 : color3;
            paint.setColor(withAlpha(c, 40 + Math.floorMod(mixSeed(seed + i * 29), 48)));
            canvas.save();
            canvas.rotate(randomRange(seed, 1120 + i, -12f, 12f), x + ww / 2f, y + hh / 2f);
            if (i % 3 == 2) {
                path.reset();
                path.moveTo(x, y + hh);
                path.lineTo(x + ww * 0.5f, y);
                path.lineTo(x + ww, y + hh);
                path.close();
                canvas.drawPath(path, paint);
            } else {
                canvas.drawRect(x, y, x + ww, y + hh, paint);
            }
            canvas.restore();
        }
    }

    private void drawPixelHalftone(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        float step = Math.max(16f, w * randomRange(seed, 1140, 0.038f, 0.066f));
        float originX = w * randomRange(seed, 1141, 0.48f, 0.88f);
        float originY = h * randomRange(seed, 1142, 0.12f, 0.42f);
        for (float y = -step; y < h + step; y += step) {
            for (float x = -step; x < w + step; x += step) {
                float d = (Math.abs(x - originX) + Math.abs(y - originY)) / (w + h);
                if (d > 0.48f || Math.floorMod(mixSeed(seed + (int) x * 7 + (int) y * 13), 4) == 0) continue;
                float s = step * (0.18f + (0.48f - d) * 0.9f);
                paint.setColor(withAlpha((x + y) % (step * 2) < step ? color : color2, (int) (18 + (0.48f - d) * 120)));
                canvas.drawRect(x - s, y - s, x + s, y + s, paint);
            }
        }
    }

    private void drawFashionStripes(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = -2; i < 9; i++) {
            float x = w * (-0.22f + i * 0.16f + randomRange(seed, 1160 + i, -0.035f, 0.035f));
            float top = h * randomRange(seed, 1180 + i, -0.08f, 0.16f);
            float width = w * randomRange(seed, 1200 + i, 0.035f, 0.075f);
            path.reset();
            path.moveTo(x, top);
            path.cubicTo(x + width * 1.7f, h * 0.24f, x - width * 1.2f, h * 0.54f, x + width * 1.6f, h * 1.08f);
            path.lineTo(x + width * 3.4f, h * 1.08f);
            path.cubicTo(x + width * 1.3f, h * 0.58f, x + width * 4.2f, h * 0.28f, x + width * 2.5f, top);
            path.close();
            paint.setColor(i % 2 == 0 ? color : color2);
            canvas.drawPath(path, paint);
        }
    }

    private void drawEditorialTape(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            float x = w * randomRange(seed, 1220 + i, -0.16f, 0.78f);
            float y = h * randomRange(seed, 1240 + i, 0.12f, 0.82f);
            float ww = w * randomRange(seed, 1260 + i, 0.24f, 0.48f);
            float hh = h * randomRange(seed, 1280 + i, 0.028f, 0.065f);
            canvas.save();
            canvas.rotate(randomRange(seed, 1300 + i, -18f, 18f), x + ww / 2f, y + hh / 2f);
            paint.setColor(withAlpha(i % 2 == 0 ? color : color2, 52 + Math.floorMod(mixSeed(seed + i * 23), 46)));
            path.reset();
            path.moveTo(x, y + hh * randomRange(seed, 1320 + i, 0f, 0.25f));
            path.lineTo(x + ww * 0.96f, y);
            path.lineTo(x + ww, y + hh);
            path.lineTo(x + ww * 0.06f, y + hh * randomRange(seed, 1340 + i, 0.76f, 1f));
            path.close();
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    private void drawPerspectivePosterGrid(Canvas canvas, int w, int h, int seed, int color, int color2) {
        drawGrid(canvas, w, h, withAlpha(Color.WHITE, 30), Math.max(28, w / 12));
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, w / 520f));
        paint.setColor(withAlpha(color, 62));
        float vx = w * randomRange(seed, 1360, 0.34f, 0.72f);
        float vy = h * randomRange(seed, 1361, -0.12f, 0.12f);
        for (int i = -4; i <= 8; i++) {
            float x = w * i / 6f;
            canvas.drawLine(vx, vy, x, h * 1.05f, paint);
        }
        paint.setColor(withAlpha(color2, 48));
        for (int i = 0; i < 8; i++) {
            float y = h * (0.18f + i * i * 0.015f);
            canvas.drawLine(0, y, w, y + h * 0.04f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTopographicLabels(Canvas canvas, int w, int h, int seed, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        for (int i = 0; i < 8; i++) {
            float x = w * randomRange(seed, 1380 + i, 0.06f, 0.92f);
            float y = h * randomRange(seed, 1400 + i, 0.1f, 0.84f);
            float len = w * randomRange(seed, 1420 + i, 0.025f, 0.07f);
            canvas.drawRect(x, y, x + len, y + Math.max(2f, h * 0.003f), paint);
        }
    }

    private void drawGlamStarburst(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        float cx = w * randomRange(seed, 1440, 0.18f, 0.82f);
        float cy = h * randomRange(seed, 1441, 0.16f, 0.52f);
        int count = 18;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + randomRange(seed, 1460 + i, -0.08f, 0.08f);
            float inner = Math.min(w, h) * randomRange(seed, 1480 + i, 0.035f, 0.08f);
            float outer = Math.max(w, h) * randomRange(seed, 1500 + i, 0.18f, 0.48f);
            paint.setStrokeWidth(Math.max(1.2f, w * randomRange(seed, 1520 + i, 0.002f, 0.008f)));
            paint.setColor(withAlpha(i % 2 == 0 ? color : color2, 42 + Math.floorMod(mixSeed(seed + i * 41), 58)));
            canvas.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner, cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHoloShards(Canvas canvas, int w, int h, int seed, int color, int color2, int color3) {
        drawChromeShards(canvas, w, h, seed, color, color2, Color.WHITE);
        drawBentoWire(canvas, w, h, seed + 53, withAlpha(Color.WHITE, 34), withAlpha(color3, 22));
        drawDiagonalTexture(canvas, w, h, seed + 71, withAlpha(Color.WHITE, 26));
    }

    private void drawOrthoGrid(Canvas canvas, int w, int h, int seed, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 720f));
        paint.setColor(color);
        int step = Math.max(34, (int) (w * randomRange(seed, 750, 0.08f, 0.14f)));
        int offsetX = Math.floorMod(mixSeed(seed + 751), step);
        int offsetY = Math.floorMod(mixSeed(seed + 752), step);
        for (int x = -offsetX; x < w + step; x += step) canvas.drawLine(x, 0, x, h, paint);
        for (int y = -offsetY; y < h + step; y += step) canvas.drawLine(0, y, w, y, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDotMatrix(Canvas canvas, int w, int h, int seed, int color, boolean cluster) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        float step = Math.max(14f, w * (cluster ? 0.045f : 0.07f));
        float size = Math.max(1f, w * (cluster ? 0.0024f : 0.0018f));
        int columns = (int) (w / step) + 3;
        int rows = (int) (h / step) + 3;
        float ox = w * randomRange(seed, 770, -0.08f, 0.12f);
        float oy = h * randomRange(seed, 771, -0.06f, 0.12f);
        paint.setStrokeWidth(size);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int mixed = mixSeed(seed + x * 37 + y * 101);
                if (Math.floorMod(mixed, cluster ? 5 : 3) == 0) continue;
                float px = ox + x * step;
                float py = oy + y * step;
                float fade = cluster ? 1f - clamp((Math.abs(px - w * 0.72f) + Math.abs(py - h * 0.28f)) / (w + h), 0f, 1f) : 0.55f;
                paint.setColor(withAlpha(color, (int) (12 + fade * (Math.floorMod(mixed, 38) + 10))));
                canvas.drawPoint(px, py, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDiagonalTexture(Canvas canvas, int w, int h, int seed, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStrokeWidth(Math.max(1f, w / 780f));
        int step = Math.max(28, (int) (w * randomRange(seed, 790, 0.07f, 0.13f)));
        int start = -w - Math.floorMod(mixSeed(seed + 791), step);
        for (int x = start; x < w * 2; x += step) {
            paint.setColor(withAlpha(color, 12 + Math.floorMod(mixSeed(seed + x), 28)));
            canvas.drawLine(x, h * 0.05f, x + w * 0.82f, h * 0.95f, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawContourLines(Canvas canvas, int w, int h, int seed, int color, int color2) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float baseY = h * randomRange(seed, 810, 0.16f, 0.72f);
        float spacing = h * randomRange(seed, 811, 0.045f, 0.082f);
        for (int i = -3; i <= 5; i++) {
            float y = baseY + i * spacing;
            float amp = h * randomRange(seed, 830 + i, 0.035f, 0.1f);
            path.reset();
            path.moveTo(-w * 0.12f, y);
            path.cubicTo(w * 0.16f, y - amp, w * 0.32f, y + amp * 0.7f, w * 0.52f, y - amp * 0.24f);
            path.cubicTo(w * 0.76f, y - amp * 1.1f, w * 0.86f, y + amp * 0.86f, w * 1.12f, y + amp * 0.12f);
            paint.setStrokeWidth(Math.max(1f, w * randomRange(seed, 850 + i, 0.002f, 0.006f)));
            paint.setColor(i % 2 == 0 ? color : color2);
            canvas.drawPath(path, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBentoWire(Canvas canvas, int w, int h, int seed, int lineColor, int fillColor) {
        paint.setShader(null);
        float left = w * randomRange(seed, 870, -0.06f, 0.14f);
        float top = h * randomRange(seed, 871, 0.12f, 0.3f);
        float cellW = w * randomRange(seed, 872, 0.18f, 0.28f);
        float cellH = h * randomRange(seed, 873, 0.09f, 0.16f);
        float gap = Math.max(8f, w * randomRange(seed, 874, 0.018f, 0.034f));
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                if (Math.floorMod(mixSeed(seed + row * 17 + col * 31), 5) == 0) continue;
                float x = left + col * (cellW + gap);
                float y = top + row * (cellH + gap);
                path.reset();
                path.moveTo(x, y);
                path.lineTo(x + cellW, y + h * randomRange(seed, 890 + col, -0.012f, 0.018f));
                path.lineTo(x + cellW + w * randomRange(seed, 900 + row, -0.018f, 0.018f), y + cellH);
                path.lineTo(x + w * randomRange(seed, 910 + col + row, -0.014f, 0.014f), y + cellH);
                path.close();
                paint.setColor(fillColor);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, w / 680f));
                paint.setColor(lineColor);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawAuroraCurtain(Canvas canvas, int w, int h, int seed, int color, int color2) {
        for (int i = 0; i < 4; i++) {
            float x = w * randomRange(seed, 760 + i, -0.16f, 0.92f);
            float width = w * randomRange(seed, 780 + i, 0.18f, 0.34f);
            float lean = w * randomRange(seed, 800 + i, -0.18f, 0.22f);
            path.reset();
            path.moveTo(x, -h * 0.08f);
            path.cubicTo(x + lean, h * 0.2f, x - lean * 0.5f, h * 0.48f, x + lean, h * 1.08f);
            path.lineTo(x + width + lean, h * 1.08f);
            path.cubicTo(x + width - lean * 0.3f, h * 0.56f, x + width + lean, h * 0.24f, x + width * 0.8f, -h * 0.08f);
            path.close();
            paint.setShader(new LinearGradient(x, 0, x + lean, h, i % 2 == 0 ? color : color2, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawSoftVeil(Canvas canvas, int w, int h, int seed, int color, int color2) {
        path.reset();
        path.moveTo(-w * 0.16f, h * randomRange(seed, 820, 0.22f, 0.52f));
        path.cubicTo(w * 0.12f, h * randomRange(seed, 821, 0.08f, 0.36f), w * 0.46f, h * randomRange(seed, 822, 0.32f, 0.72f), w * 1.16f, h * randomRange(seed, 823, 0.18f, 0.56f));
        path.lineTo(w * 1.16f, h * randomRange(seed, 824, 0.58f, 0.94f));
        path.cubicTo(w * 0.58f, h * randomRange(seed, 825, 0.84f, 1.06f), w * 0.22f, h * randomRange(seed, 826, 0.52f, 0.88f), -w * 0.16f, h * randomRange(seed, 827, 0.74f, 1.02f));
        path.close();
        paint.setShader(new LinearGradient(0, 0, w, h, color, color2, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawMistStreaks(Canvas canvas, int w, int h, int seed, int count, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < count; i++) {
            float y = h * randomRange(seed, 840 + i, 0.08f, 0.88f);
            float x = w * randomRange(seed, 860 + i, -0.14f, 0.74f);
            float len = w * randomRange(seed, 880 + i, 0.22f, 0.58f);
            float drift = h * randomRange(seed, 900 + i, -0.035f, 0.035f);
            paint.setStrokeWidth(Math.max(1.2f, w * randomRange(seed, 920 + i, 0.002f, 0.007f)));
            paint.setColor(withAlpha(color, 14 + Math.floorMod(mixSeed(seed + i * 31), 34)));
            canvas.drawLine(x, y, x + len, y + drift, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCrystalSlabs(Canvas canvas, int w, int h, int seed, int color, int color2) {
        for (int i = 0; i < 4; i++) {
            float x = w * randomRange(seed, 940 + i, -0.12f, 0.88f);
            float y = h * randomRange(seed, 960 + i, 0.08f, 0.74f);
            float ww = w * randomRange(seed, 980 + i, 0.18f, 0.36f);
            float hh = h * randomRange(seed, 1000 + i, 0.08f, 0.18f);
            float skew = w * randomRange(seed, 1020 + i, -0.1f, 0.12f);
            path.reset();
            path.moveTo(x + skew, y);
            path.lineTo(x + ww + skew * 0.35f, y + hh * 0.1f);
            path.lineTo(x + ww - skew, y + hh);
            path.lineTo(x - skew * 0.45f, y + hh * 0.86f);
            path.close();
            paint.setShader(new LinearGradient(x, y, x + ww, y + hh, i % 2 == 0 ? color : color2, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }
    }

    private void drawStarDust(Canvas canvas, int w, int h, int seed, int count, int color, boolean twinkle) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < count; i++) {
            float x = w * randomRange(seed, 1040 + i * 2, 0.02f, 0.98f);
            float y = h * randomRange(seed, 1041 + i * 2, 0.02f, 0.92f);
            float size = Math.max(1f, Math.min(w, h) * randomRange(seed, 1180 + i, 0.0012f, 0.0048f));
            int alpha = 16 + Math.floorMod(mixSeed(seed + i * 37), twinkle ? 92 : 48);
            paint.setStrokeWidth(Math.max(1f, size * 0.58f));
            paint.setColor(withAlpha(color, alpha));
            if (twinkle && i % 9 == 0) {
                canvas.drawLine(x - size, y, x + size, y, paint);
                canvas.drawLine(x, y - size, x, y + size, paint);
            } else {
                canvas.drawPoint(x, y, paint);
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSparseGrain(Canvas canvas, int w, int h, int seed, int count, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float radius = Math.max(0.8f, w * randomRange(seed, 760 + i, 0.0012f, 0.0042f));
            paint.setColor(withAlpha(color, 10 + Math.floorMod(mixSeed(seed + i * 23), 34)));
            canvas.drawCircle(w * randomRange(seed, 800 + i, 0f, 1f), h * randomRange(seed, 920 + i, 0f, 1f), radius, paint);
        }
    }

    private void drawReadability(Canvas canvas, int w, int h) {
        paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x5E000000, 0x1A000000, 0x8C000000}, new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private int vivid(int color, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] * satMul, 0.48f, 0.92f);
        hsv[2] = clamp(hsv[2] * valMul, 0.7f, 1f);
        return Color.HSVToColor(hsv);
    }

    private int rotate(int color, float hue, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hue) % 360f;
        hsv[1] = clamp(hsv[1] * satMul, 0.38f, 0.9f);
        hsv[2] = clamp(hsv[2] * valMul, 0.55f, 1f);
        return Color.HSVToColor(hsv);
    }

    private int randomColor(int seed, int slot, float satMin, float valMin) {
        int mixed = mixSeed(seed + slot * 0x9E3779B9);
        float hue = Math.floorMod(mixed, 360);
        float sat = satMin + (((mixed >>> 9) & 0xFF) / 255f) * (0.92f - satMin);
        float val = valMin + (((mixed >>> 17) & 0xFF) / 255f) * (1f - valMin);
        return Color.HSVToColor(new float[]{hue, clamp(sat, 0f, 1f), clamp(val, 0f, 1f)});
    }

    private int randomBackgroundColor(int seed, int slot) {
        int mode = Math.floorMod(mixSeed(seed + 0x51ED270B), 10);
        int mixed = mixSeed(seed + slot * 0x9E3779B9);
        float hue = Math.floorMod(mixed, 360);
        float satNoise = ((mixed >>> 9) & 0xFF) / 255f;
        float valNoise = ((mixed >>> 17) & 0xFF) / 255f;
        switch (mode) {
            case 0 -> {
                return Color.HSVToColor(new float[]{hue, 0.58f + satNoise * 0.34f, 0.9f + valNoise * 0.1f});
            }
            case 1 -> {
                float peachHue = (18f + slot * 22f + satNoise * 34f) % 360f;
                return Color.HSVToColor(new float[]{peachHue, 0.48f + satNoise * 0.34f, 0.86f + valNoise * 0.12f});
            }
            case 2 -> {
                float aquaHue = (168f + slot * 28f + satNoise * 46f) % 360f;
                return Color.HSVToColor(new float[]{aquaHue, 0.5f + satNoise * 0.36f, 0.8f + valNoise * 0.18f});
            }
            case 3 -> {
                float candyHue = (300f + slot * 26f + satNoise * 52f) % 360f;
                return Color.HSVToColor(new float[]{candyHue, 0.54f + satNoise * 0.36f, 0.84f + valNoise * 0.16f});
            }
            case 4 -> {
                float limeHue = (72f + slot * 24f + satNoise * 50f) % 360f;
                return Color.HSVToColor(new float[]{limeHue, 0.46f + satNoise * 0.36f, 0.82f + valNoise * 0.16f});
            }
            case 5 -> {
                float skyHue = (196f + slot * 20f + satNoise * 40f) % 360f;
                return Color.HSVToColor(new float[]{skyHue, 0.38f + satNoise * 0.36f, 0.82f + valNoise * 0.16f});
            }
            case 6 -> {
                float prismHue = (hue + slot * 42f) % 360f;
                return Color.HSVToColor(new float[]{prismHue, 0.42f + satNoise * 0.42f, 0.78f + valNoise * 0.2f});
            }
            case 7 -> {
                float violetHue = (252f + slot * 24f + satNoise * 46f) % 360f;
                return Color.HSVToColor(new float[]{violetHue, 0.58f + satNoise * 0.32f, slot == 1 ? 0.42f + valNoise * 0.24f : 0.24f + valNoise * 0.2f});
            }
            case 8 -> {
                float emeraldHue = (150f + slot * 24f + satNoise * 42f) % 360f;
                return Color.HSVToColor(new float[]{emeraldHue, 0.56f + satNoise * 0.32f, slot == 1 ? 0.44f + valNoise * 0.24f : 0.26f + valNoise * 0.18f});
            }
            default -> {
                float nightHue = (205f + slot * 28f + satNoise * 54f) % 360f;
                return Color.HSVToColor(new float[]{nightHue, 0.52f + satNoise * 0.34f, slot == 1 ? 0.38f + valNoise * 0.24f : 0.2f + valNoise * 0.2f});
            }
        }
    }

    private float randomRange(int seed, int slot, float min, float max) {
        return min + ((mixSeed(seed + slot * 0x85EBCA6B) & 0xFFFF) / 65535f) * (max - min);
    }

    private int mixSeed(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = 255;
        paint.setAlpha(255);
        invalidateSelf();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible && lightEffect && animated) invalidateSelf();
        else unscheduleSelf(frameCallback);
        return changed;
    }

    public void setAnimated(boolean animated) {
        if (this.animated == animated) return;
        this.animated = animated;
        unscheduleSelf(frameCallback);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
