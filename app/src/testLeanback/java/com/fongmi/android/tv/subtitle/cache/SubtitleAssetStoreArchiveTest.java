package com.fongmi.android.tv.subtitle.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.fongmi.android.tv.utils.SafeZip;
import com.github.catvod.Init;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SubtitleAssetStoreArchiveTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();
    private SubtitleAssetStore store;

    @Before
    public void setUp() {
        Init.set(RuntimeEnvironment.getApplication());
        store = new SubtitleAssetStore();
    }

    @Test
    public void rejectsTraversalWithoutWritingOutsideItsWorkDirectory() throws Exception {
        File outside = new File(store.root(), "escaped.srt");
        assertFalse(outside.exists());
        File zip = archive("../escaped.srt", "should not escape");
        boolean rejected = false;
        try {
            store.unzipAndPick(zip, "assrt", "traversal");
        } catch (RuntimeException expected) {
            rejected = true;
        }
        assertFalse("untrusted ZIP entry escaped its work directory", outside.exists());
        assertTrue("invalid archive must fail, not appear empty or successful", rejected);
    }

    @Test
    public void corruptArchiveNeverReturnsOrChangesAnOlderSubtitle() throws Exception {
        File previous = store.unzipAndPick(archive("old.ass", "previous valid subtitle"), "assrt", "same-id");
        File broken = files.newFile("broken.zip");
        Files.write(broken.toPath(), "not a ZIP".getBytes(StandardCharsets.UTF_8));
        File[] before = store.root().listFiles();
        boolean rejected = false;
        try {
            store.unzipAndPick(broken, "assrt", "same-id");
        } catch (RuntimeException expected) {
            rejected = true;
        }
        assertTrue("corruption must not return a leftover subtitle", rejected);
        assertEquals("previous valid subtitle", Files.readString(previous.toPath()));
        assertEquals(before.length, store.root().listFiles().length);
    }

    @Test
    public void repeatedArchiveUsesFreshResultsInsteadOfPickingStaleAss() throws Exception {
        File previous = store.unzipAndPick(archive("old.ass", "previous"), "assrt", "repeat-id");
        File current = store.unzipAndPick(archive("新字幕/english.srt", "current"), "assrt", "repeat-id");

        assertEquals("english.srt", current.getName());
        assertEquals("current", Files.readString(current.toPath()));
        assertNotEquals(previous.getParentFile(), current.getParentFile());
        assertEquals("previous", Files.readString(previous.toPath()));
    }

    @Test
    public void normalArchiveRetainsChinesePathsAndAssPreference() throws Exception {
        File zip = files.newFile("normal.zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            entry(output, "中文目录/english.srt", "srt text");
            entry(output, "中文目录/简体.ass", "ass text");
        }
        File result = store.unzipAndPick(zip, "assrt", "normal");

        assertEquals("简体.ass", result.getName());
        assertEquals("ass text", Files.readString(result.toPath()));
        assertTrue(result.getCanonicalPath().startsWith(store.root().getCanonicalPath() + File.separator));
    }

    @Test
    public void unsupportedArchiveFailsAndRemovesOnlyItsOwnWorkDirectory() throws Exception {
        File sentinel = new File(store.root(), "preserved.txt");
        Files.write(sentinel.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        int before = store.root().listFiles().length;
        boolean rejected = false;
        try {
            store.unzipAndPick(archive("readme.txt", "no subtitles"), "assrt", "unsupported");
        } catch (RuntimeException expected) {
            rejected = true;
        }
        assertTrue("no supported subtitle must be a failure", rejected);
        assertEquals(before, store.root().listFiles().length);
        assertEquals("keep", Files.readString(sentinel.toPath()));
    }

    private File archive(String name, String value) throws Exception {
        File zip = files.newFile();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            entry(output, name, value);
        }
        return zip;
    }

    private static void entry(ZipOutputStream output, String name, String value) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
