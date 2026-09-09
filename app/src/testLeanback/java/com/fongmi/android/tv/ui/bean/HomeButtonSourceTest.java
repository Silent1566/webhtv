package com.fongmi.android.tv.ui.bean;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HomeButtonSourceTest {

    @Test
    public void homeButtonIncludesAdblockInDefaultAndSortableCatalog() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java");
        assertTrue(source.contains("ALL = \"0,1,2,3,4,5,6,7,8\""));
        assertTrue(source.contains("new HomeButton(8, R.string.home_adblock)"));
        assertTrue(source.contains("ids.add(\"8\")"));
    }

    @Test
    public void homeButtonRendersStateAndTogglesTheSharedPreference() throws Exception {
        String func = read("app/src/leanback/java/com/fongmi/android/tv/bean/Func.java");
        assertTrue(func.contains("R.string.home_adblock_on"));
        assertTrue(func.contains("R.string.home_adblock_off"));
        assertTrue(func.contains("R.drawable.ic_live_block"));
        assertTrue(func.contains("private final String text"));
        assertTrue(func.contains("getText().equals(other.getText())"));

        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        assertTrue(home.contains("else if (item.getResId() == R.string.home_adblock)"));
        assertTrue(home.contains("Setting.putAdblock(!Setting.isAdblock())"));
        assertTrue(home.contains("setFunc();"));
    }

    @Test
    public void allLeanbackLocalesExposeAdblockLabels() throws Exception {
        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = read("app/src/leanback/res/" + locale + "/strings.xml");
            assertTrue(locale, strings.contains("<string name=\"home_adblock\">")
                    && strings.contains("<string name=\"home_adblock_on\">")
                    && strings.contains("<string name=\"home_adblock_off\">") );
            if (!locale.equals("values")) assertTrue(locale, strings.contains("去"));
        }
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
