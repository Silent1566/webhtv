package com.fongmi.android.tv.subtitle.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class SubtitleArchive {

    private static final int MAX_ENTRIES = 256;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    static File extract(File archive, File cache) throws IOException {
        return extract(archive, cache, MAX_ENTRIES, MAX_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    static File extract(File archive, File cache, int maxEntries, long maxFileBytes, long maxTotalBytes) throws IOException {
        if (maxEntries < 1 || maxFileBytes < 1 || maxTotalBytes < 1) throw new IllegalArgumentException("Invalid archive limits");
        checkInterrupted();
        File root = cache.getCanonicalFile();
        if (!root.isDirectory()) throw new IOException("Subtitle cache is not a directory");
        File directory = new File(root, "zip-" + UUID.randomUUID());
        if (!directory.mkdir()) throw new IOException("Cannot create subtitle archive directory");
        try (ZipFile zip = new ZipFile(archive)) {
            List<ZipEntry> entries = new ArrayList<>();
            Set<File> targets = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            long declaredTotal = 0;
            while (enumeration.hasMoreElements()) {
                checkInterrupted();
                ZipEntry entry = enumeration.nextElement();
                if (entries.size() >= maxEntries) throw new IOException("Subtitle archive entry limit exceeded");
                File target = target(directory, entry.getName());
                if (!targets.add(target)) throw new IOException("Duplicate subtitle archive entry");
                long size = entry.getSize();
                if (size < 0 || entry.getCrc() < 0 || (entry.isDirectory() && size != 0)) throw new IOException("Invalid subtitle archive entry");
                if (size > maxFileBytes || size > maxTotalBytes - declaredTotal) throw new IOException("Subtitle archive size limit exceeded");
                declaredTotal += size;
                entries.add(entry);
            }
            byte[] buffer = new byte[16384];
            long total = 0;
            for (ZipEntry entry : entries) {
                checkInterrupted();
                File output = target(directory, entry.getName());
                boolean isDirectory = entry.isDirectory();
                if (!isDirectory) {
                    mkdir(output.getParentFile());
                    if (!output.createNewFile()) throw new IOException("Subtitle archive output already exists");
                }
                CRC32 crc = new CRC32();
                long bytes = 0;
                try (InputStream input = zip.getInputStream(entry)) {
                    FileOutputStream stream = isDirectory ? null : new FileOutputStream(output);
                    try {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            checkInterrupted();
                            if (count > maxFileBytes - bytes || count > maxTotalBytes - total) throw new IOException("Subtitle archive size limit exceeded");
                            if (isDirectory) throw new IOException("Subtitle archive directory entry has content");
                            bytes += count;
                            total += count;
                            crc.update(buffer, 0, count);
                            stream.write(buffer, 0, count);
                        }
                    } finally {
                        if (stream != null) stream.close();
                    }
                }
                if (bytes != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Subtitle archive integrity check failed");
                if (isDirectory) mkdir(output);
            }
            return directory;
        } catch (IOException | RuntimeException failure) {
            try {
                discard(directory);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static void discard(File file) throws IOException {
        if (file.getCanonicalFile().equals(file.getAbsoluteFile()) && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list subtitle archive directory");
            for (File child : children) discard(child);
        }
        if (!file.delete()) throw new IOException("Cannot remove subtitle archive output");
    }

    private static File target(File directory, String name) throws IOException {
        if (name.isEmpty() || name.length() > 1024 || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.indexOf(0) >= 0) throw new IOException("Invalid subtitle archive path");
        String[] parts = name.split("/", -1);
        if (parts.length > 33) throw new IOException("Subtitle archive path too deep");
        int depth = 0;
        for (String part : parts) {
            if (part.equals("..")) throw new IOException("Invalid subtitle archive path");
            if (!part.isEmpty() && !part.equals(".")) depth++;
        }
        if (depth > 32) throw new IOException("Subtitle archive path too deep");
        File target = new File(directory, name).getCanonicalFile();
        if (!target.getPath().startsWith(directory.getPath() + File.separator)) throw new IOException("Subtitle archive path escapes directory");
        return target;
    }

    private static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create subtitle archive directory");
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Subtitle extraction cancelled");
    }
}
