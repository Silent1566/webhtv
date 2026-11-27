package com.fongmi.android.tv.utils;

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

/**
 * ZIP 解包统一入口：条目路径必须落在目标目录内，条目数、单文件与累计解压量都有上限，
 * 并按实际读到的字节、CRC 与声明长度复核。任何异常都整体失败，不保留半成品。
 */
public final class SafeZip {

    private static final int MAX_ENTRIES = 256;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    private SafeZip() {
    }

    /** 解压到 cache 下新建的私有目录，返回该目录；失败时目录已被清理。 */
    public static File extract(File archive, File cache) throws IOException {
        return extract(archive, cache, MAX_ENTRIES, MAX_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    public static File extract(File archive, File cache, int maxEntries, long maxFileBytes, long maxTotalBytes) throws IOException {
        if (maxEntries < 1 || maxFileBytes < 1 || maxTotalBytes < 1) throw new IllegalArgumentException("Invalid archive limits");
        checkInterrupted();
        File root = cache.getCanonicalFile();
        if (!root.isDirectory()) throw new IOException("Subtitle cache is not a directory");
        File directory = new File(root, "zip-" + UUID.randomUUID());
        if (!directory.mkdir()) throw new IOException("Cannot create subtitle archive directory");
        try (ZipFile zip = new ZipFile(archive)) {
            unzip(zip, directory, maxEntries, maxFileBytes, maxTotalBytes, new ArrayList<>(), new ArrayList<>());
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

    /** 解压进已存在的目标目录，保留目录内其它内容；失败时只删除本次新建的条目。 */
    public static void extractInto(File archive, File destination) throws IOException {
        extractInto(archive, destination, MAX_ENTRIES, MAX_FILE_BYTES, MAX_TOTAL_BYTES);
    }

    public static void extractInto(File archive, File destination, int maxEntries, long maxFileBytes, long maxTotalBytes) throws IOException {
        if (maxEntries < 1 || maxFileBytes < 1 || maxTotalBytes < 1) throw new IllegalArgumentException("Invalid archive limits");
        checkInterrupted();
        File directory = destination.getCanonicalFile();
        if (!directory.isDirectory()) throw new IOException("Archive destination is not a directory");
        List<File> createdFiles = new ArrayList<>();
        List<File> createdDirs = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            unzip(zip, directory, maxEntries, maxFileBytes, maxTotalBytes, createdFiles, createdDirs);
        } catch (IOException | RuntimeException failure) {
            for (int i = createdFiles.size() - 1; i >= 0; i--) {
                if (!createdFiles.get(i).delete()) failure.addSuppressed(new IOException("Cannot remove partial archive output"));
            }
            for (int i = createdDirs.size() - 1; i >= 0; i--) {
                try {
                    discard(createdDirs.get(i));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    public static void discard(File file) throws IOException {
        if (file.getCanonicalFile().equals(file.getAbsoluteFile()) && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list archive directory");
            for (File child : children) discard(child);
        }
        if (!file.delete()) throw new IOException("Cannot remove archive output");
    }

    private static void unzip(ZipFile zip, File directory, int maxEntries, long maxFileBytes, long maxTotalBytes,
                              List<File> createdFiles, List<File> createdDirs) throws IOException {
        List<ZipEntry> entries = new ArrayList<>();
        Set<File> targets = new HashSet<>();
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        long declaredTotal = 0;
        while (enumeration.hasMoreElements()) {
            checkInterrupted();
            ZipEntry entry = enumeration.nextElement();
            if (entries.size() >= maxEntries) throw new IOException("Archive entry limit exceeded");
            File target = target(directory, entry.getName());
            if (!targets.add(target)) throw new IOException("Duplicate archive entry");
            long size = entry.getSize();
            if (size < 0 || entry.getCrc() < 0 || (entry.isDirectory() && size != 0)) throw new IOException("Invalid archive entry");
            if (size > maxFileBytes || size > maxTotalBytes - declaredTotal) throw new IOException("Archive size limit exceeded");
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
                mkdir(output.getParentFile(), createdDirs);
                if (!output.createNewFile()) throw new IOException("Archive output already exists");
                createdFiles.add(output);
            }
            CRC32 crc = new CRC32();
            long bytes = 0;
            try (InputStream input = zip.getInputStream(entry)) {
                FileOutputStream stream = isDirectory ? null : new FileOutputStream(output);
                try {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkInterrupted();
                        if (count > maxFileBytes - bytes || count > maxTotalBytes - total) throw new IOException("Archive size limit exceeded");
                        if (isDirectory) throw new IOException("Archive directory entry has content");
                        bytes += count;
                        total += count;
                        crc.update(buffer, 0, count);
                        stream.write(buffer, 0, count);
                    }
                } finally {
                    if (stream != null) stream.close();
                }
            }
            if (bytes != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Archive integrity check failed");
            if (isDirectory) mkdir(output, createdDirs);
        }
    }

    private static File target(File directory, String name) throws IOException {
        if (name.isEmpty() || name.length() > 1024 || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0 || name.indexOf(0) >= 0) throw new IOException("Invalid archive path");
        String[] parts = name.split("/", -1);
        if (parts.length > 33) throw new IOException("Archive path too deep");
        int depth = 0;
        for (String part : parts) {
            if (part.equals("..")) throw new IOException("Invalid archive path");
            if (!part.isEmpty() && !part.equals(".")) depth++;
        }
        if (depth > 32) throw new IOException("Archive path too deep");
        File target = new File(directory, name).getCanonicalFile();
        if (!target.getPath().startsWith(directory.getPath() + File.separator)) throw new IOException("Archive path escapes directory");
        return target;
    }

    private static void mkdir(File directory, List<File> createdDirs) throws IOException {
        if (directory.isDirectory()) return;
        File top = directory;
        while (top.getParentFile() != null && !top.getParentFile().exists()) top = top.getParentFile();
        if (!directory.mkdirs()) throw new IOException("Cannot create archive directory");
        if (!createdDirs.contains(top)) createdDirs.add(top);
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Archive extraction cancelled");
    }
}
