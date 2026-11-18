package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSelectionLocatorSourceTest {

    @Test
    public void bothPlayersLocateSeamlessEpisodeByIdentityFirst() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String body = methodBody(readVideo(mode), "private boolean seamless(Flag flag)");
            assertTrue(mode + " must resolve the history episode identity first", body.contains("Episode episode = mHistory == null ? null : flag.find(mHistory.getEpisode(), true);"));
            assertTrue(mode + " must keep the scraped mark only as a fallback", body.contains("if (episode == null && !getMark().isEmpty()) episode = flag.find(getMark(), false);"));
            assertFalse(mode + " must not branch on an empty mark before the identity lookup", body.contains("getMark().isEmpty() ?"));
            assertFalse(mode + " must not resolve by remarks text without the episode url", body.contains("flag.find(mHistory.getVodRemarks(), false)"));
            assertTrue(mode + " must keep the existing playback guard", body.contains("(isPlaybackFlag(flag) && episode.isSelected())"));
        }
    }

    @Test
    public void mobileSeamlessOnlyRewritesHistoryUrlForTheSameIdentity() throws Exception {
        String body = methodBody(readVideo("mobile"), "private boolean seamless(Flag flag)");
        assertTrue("mobile must gate the url write on the history identity", body.contains("boolean sameHistoryIdentity = TextUtils.isEmpty(mHistory.getEpisodeUrl()) || episode.matchesPlayback(mHistory.getEpisode(), flag.containsEpisodeUrl(mHistory.getEpisode()));"));
        assertTrue("mobile must write the url only through that gate", body.contains("if (sameHistoryIdentity) mHistory.setEpisodeUrl(episode.getUrl());"));
        assertFalse("mobile must not write the resolved url unconditionally", body.contains("\n        mHistory.setEpisodeUrl"));
        assertTrue("the identity gate must be evaluated before any history field is written", body.indexOf("boolean sameHistoryIdentity") < body.indexOf("mHistory.setVodRemarks("));
        assertTrue(body.contains("onItemClick(episode);"));
    }

    @Test
    public void mobileSelectedPositionUsesLeanbackThreeLevelFallback() throws Exception {
        String mobile = methodBody(readVideo("mobile"), "private int getSelectedEpisodePosition(List<Episode> items)");
        String leanback = methodBody(readVideo("leanback"), "private int getSelectedEpisodePosition(List<Episode> episodes)");
        assertTrue("mobile must keep the url exact match channel", mobile.contains("if (!TextUtils.equals(historyEpisode.getUrl(), items.get(i).getUrl())) continue;"));
        assertTrue("mobile must keep the tmdb number cross check", mobile.contains("!items.get(i).matchesNumber(historyEpisode)) continue;"));
        assertTrue("mobile must keep the playback identity fallback", mobile.contains("if (items.get(i).matchesPlayback(historyEpisode)) return i;"));
        assertTrue("mobile must keep the history short circuit", mobile.contains("if (mHistory == null) return 0;"));
        assertTrue("mobile must keep the empty url break", mobile.contains("if (TextUtils.isEmpty(historyEpisode.getUrl())) break;"));
        assertTrue("url exact match must precede the identity fallback", mobile.indexOf("matchesNumber(historyEpisode)") < mobile.indexOf("matchesPlayback(historyEpisode)"));
        assertTrue("the identity fallback must precede the default position", mobile.indexOf("matchesPlayback(historyEpisode)") < mobile.lastIndexOf("return 0;"));
        assertTrue("leanback must keep the tmdb number cross check", leanback.contains("!episodes.get(i).matchesNumber(historyEpisode)) continue;"));
        assertTrue("leanback must keep the playback identity fallback", leanback.contains("if (episodes.get(i).matchesPlayback(historyEpisode)) return i;"));
    }

    @Test
    public void fastTmdbPlaybackNoLongerFallsBackToTheFirstItem() throws Exception {
        String source = readVideo("leanback");
        assertTrue(methodBody(source, "private Flag findFastTmdbPlaybackFlag(Vod item)").contains("return findIntentPlaybackFlag("));
        assertTrue(methodBody(source, "private Episode findFastTmdbPlaybackEpisode(Flag flag)").contains("return findIntentPlaybackEpisode("));
        String pending = methodBody(source, "private void startPendingFastTmdbPlayback()");
        assertTrue("the unresolved fast playback must keep the normal chain fallback", pending.contains("if (flag == null || episode == null || TextUtils.isEmpty(episode.getUrl())) {") && pending.contains("applyCachedTmdbDetailNextFrame(item);"));
    }

    @Test
    public void intentPlaybackEpisodeFallsBackToTheHistoryIdentity() throws Exception {
        String body = methodBody(readVideo("leanback"), "private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl)");
        int url = body.indexOf("if (!TextUtils.isEmpty(playUrl)) {");
        int name = body.indexOf("Episode episode = flag.find(playName, true);");
        int history = body.indexOf("return mHistory == null ? null : flag.find(mHistory.getEpisode(), true);");
        assertTrue("url exact match must come first", url >= 0 && url < name);
        assertTrue("the play name must be tried before the history rescue", name > 0 && name < history);
        assertTrue("the history episode identity must be the last channel", history > 0);
    }

    @Test
    public void folderClickReplacesAnyDifferentHistoryEpisodeAndClearsItsProgress() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            for (int saved : new int[]{1, 3}) {
                History history = folderHistory(saved);
                applySelection(mode, history, "04 4K.mp4", false, "", "");
                assertEquals(mode, "url-4", history.getEpisodeUrl());
                assertEquals(mode, "04 4K.mp4", history.getVodRemarks());
                assertEquals(mode, -9223372036854775807L, history.getPosition());
                assertEquals(mode, -9223372036854775807L, history.getDuration());
            }
        }
    }

    @Test
    public void folderClickOnTheSavedEpisodeKeepsItsProgress() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            History history = folderHistory(4);
            applySelection(mode, history, "04 4K.mp4", false, "", "");
            assertEquals("url-4", history.getEpisodeUrl());
            assertEquals(90000, history.getPosition());
            assertEquals(300000, history.getDuration());
        }
    }

    @Test
    public void generalRemarksAndHistoryResumeDoNotBecomeFolderSelections() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            for (String mark : new String[]{"", "全24集", "国语", "not-in-the-folder.mp4"}) {
                History history = folderHistory(3);
                applySelection(mode, history, mark, false, "", "");
                assertEquals(mode + " " + mark, "url-3", history.getEpisodeUrl());
                assertEquals(90000, history.getPosition());
            }
            History history = folderHistory(3);
            applySelection(mode, history, "04 4K.mp4", true, "", "");
            assertEquals("url-3", history.getEpisodeUrl());
            assertEquals(90000, history.getPosition());
        }
    }

    @Test
    public void explicitEpisodeIdentityStillWinsOverAFolderMark() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            History history = folderHistory(3);
            applySelection(mode, history, "04 4K.mp4", false, "02 4K.mp4", "url-2");
            assertEquals(mode, "url-2", history.getEpisodeUrl());
        }
    }

    @Test
    public void ambiguousFolderNamesDoNotOverwriteAnExistingVersionUrl() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            History history = folderHistory(4);
            history.setEpisodeUrl("url-4-variant");
            Flag flag = new Flag("Alist");
            flag.getEpisodes().add(Episode.create("04 4K.mp4", "url-4"));
            flag.getEpisodes().add(Episode.create("04 4K.mp4", "url-4-variant"));
            Vod vod = new Vod();
            vod.setFlags(java.util.List.of(flag));
            applySelection(mode, history, vod, "04 4K.mp4", false, "", "");
            assertEquals(mode, "url-4-variant", history.getEpisodeUrl());
            assertEquals(90000, history.getPosition());
            applySelection(mode, history, vod, "04 4K.mp4", false, "04 4K.mp4", "url-4-variant");
            assertEquals(mode, "url-4-variant", history.getEpisodeUrl());
        }
    }

    @Test
    public void folderClickClearsLegacyProgressWithoutASavedUrl() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            History history = folderHistory(1);
            history.setEpisodeUrl("");
            applySelection(mode, history, "04 4K.mp4", false, "", "");
            assertEquals(mode, "url-4", history.getEpisodeUrl());
            assertEquals(mode, -9223372036854775807L, history.getPosition());
        }
    }

    private static History folderHistory(int episode) {
        History history = new History();
        history.setKey("alist@@@folder");
        history.setVodFlag("Alist");
        history.setVodRemarks(String.format(java.util.Locale.ROOT, "%02d 4K.mp4", episode));
        history.setEpisodeUrl("url-" + episode);
        history.setPosition(90000);
        history.setDuration(300000);
        return history;
    }

    private static final java.util.Map<String, java.lang.reflect.Method> selectionFixtures = new java.util.HashMap<>();

    private static void applySelection(String mode, History history, String mark, boolean resume, String name, String url) throws Exception {
        Vod vod = new Vod();
        Flag flag = new Flag("Alist");
        for (int i = 1; i <= 4; i++) flag.getEpisodes().add(Episode.create(String.format(java.util.Locale.ROOT, "%02d 4K.mp4", i), "url-" + i));
        vod.setFlags(java.util.List.of(flag));
        applySelection(mode, history, vod, mark, resume, name, url);
    }

    private static void applySelection(String mode, History history, Vod vod, String mark, boolean resume, String name, String url) throws Exception {
        java.lang.reflect.Method fixture = selectionFixtures.get(mode);
        if (fixture == null) {
            String source = readVideo(mode);
            String apply = methodBody(source, "private void applyIntentPlaybackSelection(Vod item)");
            String check = methodBody(source, "private boolean checkHistory(Vod item)");
            int markStart = check.indexOf("if (!TextUtils.isEmpty(getMark())");
            int selectionEnd = check.indexOf("applyIntentPlaybackSelection(item);", markStart);
            assertTrue("missing initialization handoff", markStart >= 0 && selectionEnd > markStart);
            String initialize = check.substring(markStart, selectionEnd + "applyIntentPlaybackSelection(item);".length());
            String findFlag = methodBody(source, "private Flag findIntentPlaybackFlag(List<Flag> flags, String playFlagKey, String playFlag, String playUrl)");
            String findEpisode = methodBody(source, "private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl)");
            String folder = source.contains("private Episode findFolderPlaybackEpisode(Vod item)")
                    ? "private Episode findFolderPlaybackEpisode(Vod item) " + methodBody(source, "private Episode findFolderPlaybackEpisode(Vod item)") : "";
            String code = "package com.fongmi.android.tv.bean;\n"
                    + "import java.util.List; import android.text.TextUtils; import androidx.media3.common.C; import com.fongmi.android.tv.utils.Util;\n"
                    + "public class FolderSelectionFixture { History mHistory; String mark, name, url; boolean resume;\n"
                    + "static class Setting { static boolean isHistoryAggregationEffective() { return false; } }\n"
                    + "String getMark(){return mark;} String getIntentPlaybackFlag(){return \"\";} String getIntentPlaybackFlagKey(){return \"\";}\n"
                    + "String getIntentPlaybackEpisodeName(){return name;} String getIntentPlaybackEpisodeUrl(){return url;} boolean isResumeFromHistory(){return resume;}\n"
                    + "Episode withSourceSeasonEpisodeIdentity(Episode e){return e;} Episode withIntentTmdbEpisodeIdentity(Episode e){return e;}\n"
                    + "String getHistoryEpisodeName(Episode e){return e.getName();} void setHistoryFlag(Flag f){mHistory.setVodFlag(f.getFlag());}\n"
                    + "private void initializeSelection(Vod item){" + initialize + "}"
                    + "private void applyIntentPlaybackSelection(Vod item) " + apply
                    + "private Flag findIntentPlaybackFlag(List<Flag> flags, String playFlagKey, String playFlag, String playUrl) " + findFlag
                    + "private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl) " + findEpisode + folder
                    + "public static void apply(History h,Vod v,String m,boolean r,String n,String u){FolderSelectionFixture f=new FolderSelectionFixture();f.mHistory=h;f.mark=m;f.resume=r;f.name=n;f.url=u;f.initializeSelection(v);}}";
            Path output = Files.createTempDirectory("folder-selection-" + mode);
            Path sourceFile = output.resolve("FolderSelectionFixture.java");
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8);
            java.util.Set<String> paths = new java.util.LinkedHashSet<>(java.util.Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
            for (ClassLoader loader = PlaybackSelectionLocatorSourceTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
                if (loader instanceof java.net.URLClassLoader urls) for (java.net.URL entry : urls.getURLs()) if (entry.getProtocol().equals("file")) paths.add(Path.of(entry.toURI()).toString());
            }
            // javax.tools 不在 Android 单测编译期类路径上，按仓库既有写法用反射取 JDK 编译器。
            java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
            Object compiler = java.lang.Class.forName("javax.tools.ToolProvider").getMethod("getSystemJavaCompiler").invoke(null);
            assertTrue("existing JDK compiler", compiler != null);
            String[] compilerArgs = {"-proc:none", "-classpath", String.join(java.io.File.pathSeparator, paths), "-d", output.toString(), sourceFile.toString()};
            int result = (Integer) java.lang.Class.forName("javax.tools.Tool").getMethod("run",
                    java.io.InputStream.class, java.io.OutputStream.class, java.io.OutputStream.class, String[].class)
                    .invoke(compiler, null, errors, errors, compilerArgs);
            assertEquals(errors.toString(StandardCharsets.UTF_8), 0, result);
            try (java.util.stream.Stream<Path> artifacts = Files.walk(output)) {
                artifacts.forEach(path -> path.toFile().deleteOnExit());
            }
            ClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, PlaybackSelectionLocatorSourceTest.class.getClassLoader());
            fixture = loader.loadClass("com.fongmi.android.tv.bean.FolderSelectionFixture").getMethod("apply", History.class, Vod.class, String.class, boolean.class, String.class, String.class);
            selectionFixtures.put(mode, fixture);
        }
        fixture.invoke(null, history, vod, mark, resume, name, url);
    }

    private static String readVideo(String mode) throws Exception {
        return read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start + signature.length());
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced method body: " + signature);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
