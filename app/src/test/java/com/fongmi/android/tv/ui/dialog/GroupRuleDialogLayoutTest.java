package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class GroupRuleDialogLayoutTest {

    @Test
    public void groupRuleDialogMatchesAiConfigTvChromeAndFocus() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "GroupRuleDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_group_rule_edit.xml")));

        assertTrue("Group rule editor should use the same light panel background as AI config",
                layout.contains("android:background=\"#F6F8FC\""));
        assertTrue("Group rule editor should group fields into light dialog cards",
                layout.contains("com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"18dp\"")
                        && layout.contains("app:strokeColor=\"#D8E0EA\""));
        assertTrue("Group rule editor inputs should use AI config's stable TV heights",
                layout.contains("android:layout_height=\"44dp\""));

        assertTrue("Group rule dialog should use LightDialog theme",
                source.contains("Theme_WebHTV_LightDialog") && source.contains("LightDialog.apply"));
        assertTrue("Group rule editor should wire an explicit DPAD focus chain after AlertDialog buttons exist",
                source.contains("wireTextDpadFocus(nameInput, null, regexInput, null, null)")
                        && source.contains("wireTextDpadFocus(regexInput, nameInput, positive, null, null)"));
        assertTrue("Group rule editor should keep text editing usable by only leaving horizontal inputs at cursor edges",
                source.contains("private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right)")
                        && source.contains("isCursorAtStart(view)")
                        && source.contains("isCursorAtEnd(view)"));
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
