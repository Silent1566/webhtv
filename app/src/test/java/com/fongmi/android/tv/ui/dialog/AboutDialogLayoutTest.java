package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void githubProxyRemoveActionUsesIconButtonOnNarrowScreens() throws Exception {
        String layout = read(findMainResPath().resolve(Path.of("layout", "adapter_github_proxy.xml")));
        String remove = layout.substring(layout.indexOf("android:id=\"@+id/remove\""));
        remove = remove.substring(0, remove.indexOf("/>"));

        assertTrue("GitHub proxy remove action should be an icon button to avoid wrapped text on phones",
                layout.contains("<androidx.appcompat.widget.AppCompatImageButton")
                        && remove.contains("android:layout_width=\"44dp\"")
                        && remove.contains("android:layout_height=\"44dp\""));
        assertTrue("GitHub proxy remove action should keep an accessible label",
                remove.contains("android:contentDescription=\"@string/setting_github_proxy_remove\""));
        assertTrue("GitHub proxy remove action should use the existing delete icon",
                remove.contains("android:src=\"@drawable/ic_action_delete\""));
        assertFalse("GitHub proxy remove action should not render text that can wrap on narrow screens",
                remove.contains("android:text="));
        assertFalse("GitHub proxy remove action should not use weighted narrow columns",
                remove.contains("android:layout_weight="));
    }

    @Test
    public void mobileGithubProxyActionsDoNotRequireFocusBeforeClick() throws Exception {
        String layout = read(findMobileResPath().resolve(Path.of("layout", "adapter_github_proxy.xml")));
        String text = layout.substring(layout.indexOf("android:id=\"@+id/text\""));
        text = text.substring(0, text.indexOf("/>"));
        String remove = layout.substring(layout.indexOf("android:id=\"@+id/remove\""));
        remove = remove.substring(0, remove.indexOf("/>"));

        assertFalse("Mobile GitHub proxy source should activate on the first tap, not first acquire focus",
                text.contains("android:focusable=\"true\"")
                        || text.contains("android:focusableInTouchMode=\"true\""));
        assertFalse("Mobile GitHub proxy remove should run on the first tap, not first acquire focus",
                remove.contains("android:focusable=\"true\"")
                        || remove.contains("android:focusableInTouchMode=\"true\""));
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

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }
}
