package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 契约测试：二维码渲染必须对齐上游 FongMi/TV，保证手机标准扫码器可识别。
 * 修复的失败根因：同心圆定位图案、静区不足、透明背景导致对比度不足。
 */
public class QRCodeRenderContractTest {

    private static final String SOURCE = "src/main/java/com/fongmi/android/tv/utils/QRCode.java";

    @Test
    public void finderPatternIsStandardSquareNotConcentricCircles() throws Exception {
        String source = read();
        // 定位图案必须是方形圆角三层（7x7 / 5x5 / 3x3），标准 1:1:3:1:1 比例。
        assertTrue("定位图案必须用 drawRoundRect 方形", source.contains("canvas.drawRoundRect(left, top, right, bottom, 2 * module"));
        assertTrue("内圈必须是 5x5", source.contains("canvas.drawRoundRect(left + module, top + module, right - module, bottom - module, module, module"));
        assertTrue("中心必须是 3x3", source.contains("canvas.drawRoundRect(left + 2 * module, top + 2 * module, right - 2 * module, bottom - 2 * module"));
        assertTrue("必须有 7x7 定位模块常量", source.contains("FINDER_MODULES = 7"));
        assertTrue("必须绘制三个定位角", count(source, "drawFinder(canvas, paint,") == 3);
        assertFalse("同心圆定位图案无法被标准扫码器识别，必须移除", source.contains("drawFinderPattern"));
    }

    @Test
    public void quietZoneIsAtLeastFourModules() throws Exception {
        String source = read();
        assertTrue("静区下限必须为 4 模块", source.contains("MIN_MARGIN = 4"));
        assertTrue("渲染前必须把 margin 提升到下限", source.contains("margin = Math.max(MIN_MARGIN, margin)"));
        assertTrue("静区必须计入矩阵编码", source.contains("hints.put(EncodeHintType.MARGIN, margin)"));
    }

    @Test
    public void matrixIsEncodedAtModuleResolutionAndScaled() throws Exception {
        String source = read();
        assertTrue("按内容生成最小矩阵再缩放", source.contains("BarcodeFormat.QR_CODE, 0, 0, hints"));
        assertTrue("模块像素按矩阵整除计算", source.contains("int module = width / matrix.getWidth()"));
        assertTrue("模块像素不得小于 1", source.contains("if (module < 1) module = 1"));
        assertTrue("数据模块从矩阵读取", source.contains("matrix.get(x, y)"));
    }

    @Test
    public void backgroundIsFilledForContrast() throws Exception {
        String source = read();
        assertTrue("必须填充背景保证黑白对比度", source.contains("canvas.drawRect(0, 0, width, width, paint)"));
        assertTrue("getBitmap 必须是标准 polarity 黑点白底（反色二维码多数手机扫不出）",
                count(source, "return render(content, size, margin, Color.BLACK, Color.WHITE)") == 2);
        assertTrue("位图必须为 ARGB_8888", source.contains("Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)"));
    }

    @Test
    public void publicApiSignaturesUnchangedForCallers() throws Exception {
        String source = read();
        assertTrue("必须保留 getBitmap(content, size, margin)", source.contains("public static Bitmap getBitmap(String content, int size, int margin)"));
        assertTrue("必须保留 getLightBitmap(content, size, margin)", source.contains("public static Bitmap getLightBitmap(String content, int size, int margin)"));
    }

    private static String read() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(SOURCE), StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        int total = 0, at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            total++;
            at += needle.length();
        }
        return total;
    }
}
