package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码生成工具。
 *
 * 渲染逻辑对齐上游 FongMi/TV，修复「推送页二维码手机扫不出来」：
 * 1. 定位图案（Finder Pattern）必须是 7x7 方形、1:1:3:1:1 的黑白比例，手机相机/微信的
 *    检测器靠它定位与定方向；此前画成三层同心圆，标准检测器无法识别。
 * 2. 静区（Quiet Zone）强制至少 4 个模块；此前 margin=0/1，远低于 QR 规范要求。
 * 3. 按矩阵宽度整除计算模块像素并填充背景，保证黑白对比度与模块对齐；
 *    此前从像素矩阵反推模块大小，且背景透明，叠在半透明遮罩上对比度不足。
 */
public class QRCode {

    private static final int FINDER_MODULES = 7;
    private static final int MIN_MARGIN = 4;

    /**
     * 标准二维码：黑点 + 白底。
     * 上游 FongMi/TV 的 getWhiteBitmap 同样是黑点白底。此前推送页用白点黑底的反色二维码，
     * 叠加透明背景与半透明遮罩后，多数手机扫码器无法识别（真机报告扫不出来）。
     * 统一为标准 polarity 后全部扫码器可识别。
     */
    public static Bitmap getBitmap(String content, int size, int margin) {
        return render(content, size, margin, Color.BLACK, Color.WHITE);
    }

    /** 浅色弹窗用：黑点 + 白底（与 getBitmap 同为标准 polarity）。 */
    public static Bitmap getLightBitmap(String content, int size, int margin) {
        return render(content, size, margin, Color.BLACK, Color.WHITE);
    }

    private static Bitmap render(String content, int size, int margin, int foreground, int background) {
        try {
            margin = Math.max(MIN_MARGIN, margin);
            BitMatrix matrix = encode(content, margin);
            int width = Math.max(ResUtil.dp2px(size), matrix.getWidth());
            int module = width / matrix.getWidth();
            if (module < 1) module = 1;
            int offset = (width - matrix.getWidth() * module) / 2;
            int modules = matrix.getWidth() - 2 * margin;
            int start = offset + margin * module;
            int end = start + (modules - FINDER_MODULES) * module;
            Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(background);
            canvas.drawRect(0, 0, width, width, paint);
            paint.setColor(foreground);
            drawDataModules(canvas, paint, matrix, margin, modules, offset, module);
            drawFinder(canvas, paint, start, start, module, background);
            drawFinder(canvas, paint, end, start, module, background);
            drawFinder(canvas, paint, start, end, module, background);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private static BitMatrix encode(String content, int margin) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, margin);
        // 宽高传 0：让 ZXing 按内容生成最小矩阵（每模块 1px），再由 render 按目标尺寸放大。
        return new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
    }

    private static void drawDataModules(Canvas canvas, Paint paint, BitMatrix matrix, int margin, int modules, int offset, int module) {
        for (int y = margin; y < margin + modules; y++) {
            for (int x = margin; x < margin + modules; x++) {
                if (!matrix.get(x, y) || isFinderRegion(x - margin, y - margin, modules)) continue;
                canvas.drawCircle(offset + (x + 0.5f) * module, offset + (y + 0.5f) * module, 0.45f * module, paint);
            }
        }
    }

    private static boolean isFinderRegion(int x, int y, int modules) {
        return x < FINDER_MODULES && y < FINDER_MODULES
                || x >= modules - FINDER_MODULES && y < FINDER_MODULES
                || x < FINDER_MODULES && y >= modules - FINDER_MODULES;
    }

    /** 方形圆角定位图案：外框 7x7 前景、内框 5x5 背景、中心 3x3 前景，即标准 1:1:3:1:1。 */
    private static void drawFinder(Canvas canvas, Paint paint, int left, int top, int module, int background) {
        int right = left + FINDER_MODULES * module;
        int bottom = top + FINDER_MODULES * module;
        int foreground = paint.getColor();
        canvas.drawRoundRect(left, top, right, bottom, 2 * module, 2 * module, paint);
        paint.setColor(background);
        canvas.drawRoundRect(left + module, top + module, right - module, bottom - module, module, module, paint);
        paint.setColor(foreground);
        canvas.drawRoundRect(left + 2 * module, top + 2 * module, right - 2 * module, bottom - 2 * module, module / 2f, module / 2f, paint);
    }
}
