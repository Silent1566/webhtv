package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbSourceDialogLayoutTest {

    @Test
    public void tmdbSourceDialogMatchesAiConfigTvChromeAndFocus() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "TmdbSourceDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of("layout", "dialog_tmdb_source.xml")));

        assertTrue("TMDB source dialog should use the same light panel background as AI config",
                layout.contains("android:background=\"#F6F8FC\""));
        assertTrue("TMDB source dialog should group fields into light dialog cards",
                layout.contains("com.google.android.material.card.MaterialCardView")
                        && layout.contains("app:cardCornerRadius=\"18dp\"")
                        && layout.contains("app:strokeColor=\"#D8E0EA\""));
        assertTrue("TMDB source dialog controls should use AI config's stable TV heights",
                layout.contains("android:layout_height=\"44dp\"")
                        && layout.contains("android:layout_height=\"48dp\"")
                        && layout.contains("android:layout_height=\"52dp\""));

        assertTrue("TMDB source dialog should wire an explicit DPAD focus chain after AlertDialog buttons exist",
                source.contains("wireConfigDialogFocus(dialog, ruleInput, addBtn, disabledRuleInput, addDisabledBtn, manageBtn, resetBtn);"));
        assertTrue("TMDB source dialog should keep text editing usable by only leaving horizontal inputs at cursor edges",
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
