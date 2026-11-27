package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SafeZipTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test
    public void extractsNormalFilesAndCountsTheExactLimit() throws Exception {
        File cache = files.newFolder();
        File zip = zip(new String[]{"中文/", "中文/a.ass", "b.srt"}, new byte[][]{new byte[0], bytes("abc"), bytes("de")});

        File directory = SafeZip.extract(zip, cache, 3, 3, 5);

        assertEquals("abc", Files.readString(new File(directory, "中文/a.ass").toPath()));
        assertEquals("de", Files.readString(new File(directory, "b.srt").toPath()));
    }

    @Test
    public void rejectsParentAbsoluteWindowsAndSiblingPathsWithoutTouchingOutsideFiles() throws Exception {
        File cache = files.newFolder();
        File sentinel = new File(cache, "sentinel.srt");
        Files.write(sentinel.toPath(), bytes("keep"));
        for (String name : new String[]{"../sentinel.srt", "a/../../sentinel.srt", "/outside.srt", "C:/outside.srt", "..\\outside.srt", "\\server\\share\\outside.srt", "../sibling/evil.srt"}) {
            assertThrows(name, IOException.class, () -> SafeZip.extract(zip(name, "payload"), cache));
            assertEquals("keep", Files.readString(sentinel.toPath()));
            assertEquals(1, cache.listFiles().length);
        }
    }

    @Test
    public void rejectsDuplicateCanonicalTargetBeforeAnyDataCanBeSelected() throws Exception {
        File cache = files.newFolder();
        File zip = zip(new String[]{"first.ass", "./first.ass"}, new byte[][]{bytes("one"), bytes("two")});

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void rejectsTooManyEntriesIncludingDirectories() throws Exception {
        File cache = files.newFolder();
        File zip = zip(new String[]{"a/", "b/", "c.srt"}, new byte[][]{new byte[0], new byte[0], bytes("x")});

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache, 2, 8, 16));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void rejectsSingleFileAndAggregateByteOverflow() throws Exception {
        File cache = files.newFolder();
        assertThrows(IOException.class, () -> SafeZip.extract(zip("large.ass", "12345"), cache, 4, 4, 16));
        File zip = zip(new String[]{"a.srt", "b.srt"}, new byte[][]{bytes("123"), bytes("456")});
        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache, 4, 4, 5));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void defaultPolicyRejectsEntry257() throws Exception {
        File cache = files.newFolder();
        String[] names = new String[257];
        byte[][] values = new byte[names.length][];
        for (int i = 0; i < names.length; i++) {
            names[i] = "sub-" + i + ".srt";
            values[i] = bytes("x");
        }
        File zip = zip(names, values);

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void actualStreamIsLimitedEvenIfMetadataClaimsLessData() throws Exception {
        File cache = files.newFolder();
        File zip = storedZip("sub.srt", bytes("123456"));
        patchCentralValue(zip, 24, 1);

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache, 4, 4, 8));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void rejectsDirectoryEntryWithForgedCrcInsteadOfTrustingItsName() throws Exception {
        File cache = files.newFolder();
        File zip = zip(new String[]{"dir/", "dir/sub.srt"}, new byte[][]{new byte[0], bytes("subtitle")});
        patchCentralValue(zip, 16, 1);

        IOException failure = assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertTrue(failure.getMessage().contains("integrity"));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void rejectsDirectoryEntryCarryingContentEvenWhenItsLengthClaimsZero() throws Exception {
        File cache = files.newFolder();
        File zip = zip(new String[]{"dir/", "dir/sub.srt"}, new byte[][]{bytes("hidden"), bytes("subtitle")});
        patchCentralValue(zip, 24, 0);

        IOException failure = assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertTrue(failure.getMessage().contains("directory entry has content"));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void detectsCrcMismatchAndRemovesPartialOutput() throws Exception {
        File cache = files.newFolder();
        File zip = storedZip("sub.srt", bytes("subtitle"));
        patchCentralValue(zip, 16, 0);

        IOException failure = assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertTrue(failure.getMessage().contains("integrity"));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void truncatedArchiveDoesNotLeaveAWorkDirectory() throws Exception {
        File cache = files.newFolder();
        File zip = zip("sub.srt", "subtitle");
        try (RandomAccessFile output = new RandomAccessFile(zip, "rw")) {
            output.setLength(output.length() / 2);
        }

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void preCancelledExtractionFailsWithoutCreatingAWorkDirectoryAndKeepsTheFlag() throws Exception {
        File cache = files.newFolder();
        File zip = zip("sub.srt", "subtitle");
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void concurrentRequestsUseDifferentDirectories() throws Exception {
        File cache = files.newFolder();
        File zip = zip("sub.ass", "same candidate");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<File>> results = executor.invokeAll(Arrays.asList(
                    () -> SafeZip.extract(zip, cache), () -> SafeZip.extract(zip, cache)));
            File first = results.get(0).get(5, TimeUnit.SECONDS);
            File second = results.get(1).get(5, TimeUnit.SECONDS);
            assertNotEquals(first, second);
            assertEquals("same candidate", Files.readString(new File(first, "sub.ass").toPath()));
            assertEquals("same candidate", Files.readString(new File(second, "sub.ass").toPath()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectsDeepAndRootOnlyPaths() throws Exception {
        File cache = files.newFolder();
        String deep = String.join("/", java.util.Collections.nCopies(33, "a")) + ".srt";
        for (String path : new String[]{".", "./", deep}) {
            assertThrows(IOException.class, () -> SafeZip.extract(zip(path, "x"), cache));
        }
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void failedFileDirectoryConflictCleansOnlyTheCurrentExtraction() throws Exception {
        File cache = files.newFolder();
        File original = SafeZip.extract(zip("previous.ass", "keep"), cache);
        File zip = zip(new String[]{"conflict", "conflict/new.ass"}, new byte[][]{bytes("file"), bytes("subtitle")});

        assertThrows(IOException.class, () -> SafeZip.extract(zip, cache));
        assertEquals("keep", Files.readString(new File(original, "previous.ass").toPath()));
        assertEquals(1, cache.listFiles().length);
    }

    @Test
    public void extractIntoKeepsExistingFilesAndAddsArchiveEntries() throws Exception {
        File destination = files.newFolder("destination");
        File existing = new File(destination, "keep.srt");
        Files.write(existing.toPath(), bytes("keep"));

        SafeZip.extractInto(zip("中文/new.ass", "added"), destination);

        assertEquals("keep", Files.readString(existing.toPath()));
        assertEquals("added", Files.readString(new File(destination, "中文/new.ass").toPath()));
    }

    @Test
    public void extractIntoRejectsEscapingEntryAndRemovesOnlyItsOwnFiles() throws Exception {
        File destination = files.newFolder("destination");
        File existing = new File(destination, "keep.srt");
        Files.write(existing.toPath(), bytes("keep"));
        File zip = zip(new String[]{"first.srt", "../escape.srt"}, new byte[][]{bytes("one"), bytes("bad")});

        assertThrows(IOException.class, () -> SafeZip.extractInto(zip, destination));

        assertFalse("escaping entry must not be written", new File(files.getRoot(), "escape.srt").exists());
        assertFalse("partial output must be removed", new File(destination, "first.srt").exists());
        assertEquals("existing file must survive", "keep", Files.readString(existing.toPath()));
    }

    @Test
    public void extractIntoRequiresAnExistingDirectory() throws Exception {
        File missing = new File(files.getRoot(), "absent");

        assertThrows(IOException.class, () -> SafeZip.extractInto(zip("a.srt", "x"), missing));
        assertFalse(missing.exists());
    }
    private File zip(String name, String value) throws Exception {
        return zip(new String[]{name}, new byte[][]{bytes(value)});
    }

    private File zip(String[] names, byte[][] values) throws Exception {
        File zip = files.newFile();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            for (int i = 0; i < names.length; i++) {
                output.putNextEntry(new ZipEntry(names[i]));
                output.write(values[i]);
                output.closeEntry();
            }
        }
        return zip;
    }

    private File storedZip(String name, byte[] data) throws Exception {
        File zip = files.newFile();
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(entry);
            output.write(data);
            output.closeEntry();
        }
        return zip;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void patchCentralValue(File zip, int fieldOffset, int value) throws Exception {
        byte[] data = Files.readAllBytes(zip.toPath());
        for (int i = 0; i < data.length - 46; i++) {
            if (data[i] == 0x50 && data[i + 1] == 0x4b && data[i + 2] == 0x01 && data[i + 3] == 0x02) {
                Arrays.fill(data, i + fieldOffset, i + fieldOffset + 4, (byte) 0);
                data[i + fieldOffset] = (byte) value;
                Files.write(zip.toPath(), data);
                return;
            }
        }
        throw new AssertionError("No central ZIP directory found");
    }
}
