package com.fongmi.android.tv.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 真实往返测试：按 QRCode 的渲染规则（方形定位图案 + 静区 + 填充背景）重建像素网格，
 * 用 ZXing 解码器验证可被标准扫码器识别。
 */
public class QRCodeDecodeRoundTripTest {

    private static final int FINDER_MODULES = 7;
    private static final int MIN_MARGIN = 4;
    private static final int MODULE_PX = 6;
    private static final String CONTENT = "http://192.168.0.229:9978";

    @Test
    public void lightThemeBlackOnWhiteDecodes() throws Exception {
        assertEquals(true, tryDecode(CONTENT, true, MIN_MARGIN, false));
    }

    @Test
    public void invertedPolarityFailsToDecode() throws Exception {
        // 反证：旧推送页用的白点黑底（反色）在此解码器下失败，是「手机扫不出来」的直接原因。
        assertEquals(false, tryDecode(CONTENT, true, MIN_MARGIN, true));
    }

    /** 按 QRCode.java 的渲染规则生成像素并尝试解码。 */
    private boolean tryDecode(String content, boolean squareFinder, int margin, boolean invert) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, margin);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
        int modules = matrix.getWidth() - 2 * margin;
        int pixels = matrix.getWidth() * MODULE_PX;
        int[] buffer = new int[pixels * pixels];
        int bg = invert ? 0xFF000000 : 0xFFFFFFFF;
        int fg = invert ? 0xFFFFFFFF : 0xFF000000;
        Arrays.fill(buffer, bg);

        // 数据模块：圆点（半径 0.45 * module），与 QRCode.drawDataModules 一致
        int radius = (int) (0.45f * MODULE_PX);
        for (int y = margin; y < margin + modules; y++) {
            for (int x = margin; x < margin + modules; x++) {
                if (!matrix.get(x, y)) continue;
                if (isFinderRegion(x - margin, y - margin, modules)) continue;
                drawDisc(buffer, pixels, x * MODULE_PX + MODULE_PX / 2, y * MODULE_PX + MODULE_PX / 2, radius, fg);
            }
        }
        // 定位图案
        int end = modules - FINDER_MODULES;
        if (squareFinder) {
            drawSquareFinder(buffer, pixels, margin * MODULE_PX, margin * MODULE_PX, fg, bg);
            drawSquareFinder(buffer, pixels, (margin + end) * MODULE_PX, margin * MODULE_PX, fg, bg);
            drawSquareFinder(buffer, pixels, margin * MODULE_PX, (margin + end) * MODULE_PX, fg, bg);
        } else {
            drawCircleFinder(buffer, pixels, margin * MODULE_PX, margin * MODULE_PX, fg, bg);
            drawCircleFinder(buffer, pixels, (margin + end) * MODULE_PX, margin * MODULE_PX, fg, bg);
            drawCircleFinder(buffer, pixels, margin * MODULE_PX, (margin + end) * MODULE_PX, fg, bg);
        }
        try {
            RGBLuminanceSource source = new RGBLuminanceSource(pixels, pixels, buffer);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> decodeHints = new EnumMap<>(DecodeHintType.class);
            decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            decodeHints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            Result result = new MultiFormatReader().decode(bitmap, decodeHints);
            return content.equals(result.getText());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFinderRegion(int x, int y, int modules) {
        return x < FINDER_MODULES && y < FINDER_MODULES
                || x >= modules - FINDER_MODULES && y < FINDER_MODULES
                || x < FINDER_MODULES && y >= modules - FINDER_MODULES;
    }

    /** 标准 7x7 方形定位图案：外框 7x7 前景、内框 5x5 背景、中心 3x3 前景。 */
    private static void drawSquareFinder(int[] buffer, int w, int left, int top, int fg, int bg) {
        int size = FINDER_MODULES * MODULE_PX;
        fillRect(buffer, w, left, top, size, size, fg);
        fillRect(buffer, w, left + MODULE_PX, top + MODULE_PX, size - 2 * MODULE_PX, size - 2 * MODULE_PX, bg);
        fillRect(buffer, w, left + 2 * MODULE_PX, top + 2 * MODULE_PX, size - 4 * MODULE_PX, size - 4 * MODULE_PX, fg);
    }

    /** 旧实现的同心圆定位图案。 */
    private static void drawCircleFinder(int[] buffer, int w, int left, int top, int fg, int bg) {
        int size = FINDER_MODULES * MODULE_PX;
        int cx = left + size / 2, cy = top + size / 2;
        drawDisc(buffer, w, cx, cy, size / 2, fg);
        drawDisc(buffer, w, cx, cy, size / 2 - MODULE_PX, bg);
        drawDisc(buffer, w, cx, cy, size / 2 - 2 * MODULE_PX, fg);
    }

    private static void fillRect(int[] buffer, int w, int left, int top, int bw, int bh, int color) {
        for (int y = top; y < top + bh; y++)
            for (int x = left; x < left + bw; x++)
                if (x >= 0 && y >= 0 && x < w && y < w) buffer[y * w + x] = color;
    }

    private static void drawDisc(int[] buffer, int w, int cx, int cy, int r, int color) {
        int r2 = r * r;
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r2 && x >= 0 && y >= 0 && x < w && y < w) buffer[y * w + x] = color;
            }
        }
    }
}
