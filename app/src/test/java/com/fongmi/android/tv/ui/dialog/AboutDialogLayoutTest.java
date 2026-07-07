package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class AboutDialogLayoutTest {

    @Test
    public void aboutDialogKeepsUpdateActionsInsideTvSafeArea() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AboutDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_about.xml")));

        assertTrue("About dialog should reserve vertical safe space for TV overscan",
                source.contains("DIALOG_VERTICAL_SAFE_SPACE_DP"));
        assertTrue("About dialog window should use a bounded height instead of wrap content",
                source.contains("params.height = getDialogHeight(activity);"));
        assertTrue("About dialog bounded height must be applied to the actual window",
                source.contains("window.setLayout(params.width, params.height);"));
        assertTrue("About dialog root must fill the bounded window height",
                layout.contains("android:layout_height=\"match_parent\""));
        String contentScroll = layout.substring(layout.indexOf("android:id=\"@+id/contentScroll\""));
        assertTrue("About dialog disclaimer should shrink before the update buttons are clipped",
                contentScroll.contains("android:layout_height=\"0dp\"")
                        && contentScroll.contains("android:layout_weight=\"1\""));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
