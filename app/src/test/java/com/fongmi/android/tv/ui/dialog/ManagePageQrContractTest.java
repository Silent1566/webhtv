package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 管理页面弹窗二维码契约：双端共用、扫的是局域网地址、无局域网时隐藏、不抢 TV 焦点。 */
public class ManagePageQrContractTest {

    private static Path projectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("app/build.gradle"))) dir = dir.getParent();
        if (dir == null) throw new IllegalStateException("project root not found from " + System.getProperty("user.dir"));
        return dir;
    }

    private static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(projectRoot().resolve(rel)), "UTF-8");
    }

    @Test
    public void layoutHasQrAreaBetweenMessageAndButtons() throws Exception {
        String xml = read("app/src/main/res/layout/dialog_manage_page.xml");
        int message = xml.indexOf("@+id/contentScroll");
        int qr = xml.indexOf("@+id/qrArea");
        int image = xml.indexOf("@+id/qrImage");
        int footer = xml.indexOf("@+id/footer");
        assertTrue("二维码区必须在地址文字之后", message > 0 && qr > message);
        assertTrue("二维码图片必须在区域内", qr < image && image < footer);
        assertTrue("二维码区默认隐藏，待代码判定有效后显示",
                xml.substring(qr, footer).contains("android:visibility=\"gone\""));
    }

    @Test
    public void qrImageDoesNotStealRemoteFocus() throws Exception {
        String xml = read("app/src/main/res/layout/dialog_manage_page.xml");
        int image = xml.indexOf("@+id/qrImage");
        int end = xml.indexOf("/>", image);
        String block = xml.substring(image, end);
        assertTrue("TV 端二维码不可聚焦，避免遥控焦点落到图片", block.contains("android:focusable=\"false\""));
        assertTrue(block.contains("android:focusableInTouchMode=\"false\""));
    }

    @Test
    public void qrUsesLanUrlAndStandardRenderer() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java");
        assertTrue("二维码内容必须用局域网地址，不能用 127.0.0.1",
                source.contains("QRCode.getLightBitmap(lanUrl, 160, 0)"));
        assertFalse("不能把本机地址写进二维码", source.contains("QRCode.getLightBitmap(localUrl"));
        assertTrue("有效局域网地址时显示二维码区", source.contains("binding.qrArea.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void invalidLanUrlHidesQr() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java");
        assertTrue("必须过滤 127.0.0.1 / 0.0.0.0，避免生成扫不开的码",
                source.contains("!url.contains(\"127.0.0.1\") && !url.contains(\"0.0.0.0\")"));
        assertTrue("无有效局域网地址时二维码区保持隐藏", source.contains("android:visibility=\"gone\"")
                || read("app/src/main/res/layout/dialog_manage_page.xml").contains("android:visibility=\"gone\""));
    }

    @Test
    public void singleSharedDialogCoversBothFlavors() throws Exception {
        // 管理页对话框在 main sourceSet，leanback 与 mobile 两端都调用它，无需双份实现
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String setting = read("app/src/" + flavor + "/java/com/fongmi/android/tv/ui/"
                    + ("leanback".equals(flavor) ? "activity/SettingEnhanceActivity.java" : "fragment/SettingEnhanceFragment.java"));
            assertTrue(flavor + " 端应调用共享的 ManagePageDialog", setting.contains("ManagePageDialog.show("));
        }
        assertTrue("对话框实现必须在 main，双端共用",
                Files.exists(projectRoot().resolve("app/src/main/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java")));
        assertFalse("不应在 leanback 端复制一份对话框",
                Files.exists(projectRoot().resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java")));
        assertFalse("不应在 mobile 端复制一份对话框",
                Files.exists(projectRoot().resolve("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java")));
    }

    @Test
    public void hintStringsExistInAllLocales() throws Exception {
        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String s = read("app/src/main/res/" + locale + "/strings.xml");
            assertTrue(locale + " 缺少二维码提示文案", s.contains("name=\"manage_page_qr_hint\""));
        }
    }
}
