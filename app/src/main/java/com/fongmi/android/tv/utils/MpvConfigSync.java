package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Archives only the files owned by MPV configuration management. */
public final class MpvConfigSync {

    public static final String PART_NAME = "mpvConfigFiles";

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    private static final String MPV_CONF = "mpv.conf";
    private static final String INPUT_CONF = "input.conf";
    private static final String SCRIPTS = "scripts/";
    private static final String PROFILES = "profiles/";

    private MpvConfigSync() {
    }

    public static Archive createArchive() throws IOException {
        File root = MpvConfigStore.configDir().getCanonicalFile();
        File archive = File.createTempFile("webhtv-mpv-sync-", ".zip", Path.cache());
        int count = 0;
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive), BUFFER_SIZE))) {
            for (String name : new String[]{MPV_CONF, INPUT_CONF}) {
                File file = new File(root, name);
                if (file.isFile()) {
                    Stats stats = addFile(root, file, name, zos, buffer);
                    count += stats.count;
                    size += stats.size;
                }
            }
            File scripts = new File(root, "scripts");
            addDirectoryEntry(scripts, SCRIPTS, zos);
            Stats scriptsStats = addTree(root, scripts, SCRIPTS, zos, buffer);
            count += scriptsStats.count;
            size += scriptsStats.size;
            checkLimits(count, size);
            for (String target : new String[]{"mpv_conf", "input_conf"}) {
                File profileDir = new File(root, "profiles" + File.separator + target);
                String prefix = PROFILES + target + "/";
                addDirectoryEntry(profileDir, prefix, zos);
                Stats profileStats = addTree(root, profileDir, prefix, zos, buffer);
                count += profileStats.count;
                size += profileStats.size;
                checkLimits(count, size);
            }
        } catch (Throwable e) {
            deleteRecursively(archive);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
        if (archive.length() > MAX_ARCHIVE_BYTES) {
            deleteRecursively(archive);
            throw new IOException("MPV config archive too large");
        }
        SpiderDebug.log("sync", "archive mpv config count=%d size=%d zip=%d file=%s", count, size, archive.length(), archive.getAbsolutePath());
        return new Archive(archive, count, size, archive.length());
    }

    public static int restoreArchive(File archive) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() <= 0) return 0;
        if (archive.length() > MAX_ARCHIVE_BYTES) throw new IOException("MPV config archive too large");
        File staging = new File(Path.cache(), "webhtv-mpv-restore-" + UUID.randomUUID());
        if (!staging.mkdirs()) throw new IOException("Cannot create MPV config staging directory");
        int count = 0;
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("Too many MPV config files");
                    String path = normalize(entry.getName());
                    if (!isAllowed(path)) throw new IOException("Invalid MPV config path");
                    File out = new File(staging, path);
                    if (!inside(staging, out)) throw new IOException("Invalid MPV config path");
                    if (entry.isDirectory()) {
                        if (!out.mkdirs() && !out.isDirectory()) throw new IOException("Cannot create MPV config directory");
                        zis.closeEntry();
                        continue;
                    }
                    count++;
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Cannot create MPV config directory");
                    long written = 0;
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out), BUFFER_SIZE)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            written += read;
                            total += read;
                            if (written > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES) throw new IOException("MPV config archive too large");
                            output.write(buffer, 0, read);
                        }
                    }
                    if (entry.getTime() > 0) out.setLastModified(entry.getTime());
                    zis.closeEntry();
                }
            }
            apply(staging);
            SpiderDebug.log("sync", "restore mpv config count=%d file=%s", count, archive.getAbsolutePath());
            return count;
        } finally {
            deleteRecursively(staging);
        }
    }

    static boolean isAllowed(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")) return false;
        if (MPV_CONF.equals(path) || INPUT_CONF.equals(path)) return true;
        return path.startsWith(SCRIPTS) || path.startsWith(PROFILES + "mpv_conf/") || path.startsWith(PROFILES + "input_conf/")
                || "scripts".equals(path) || "profiles".equals(path)
                || "profiles/mpv_conf".equals(path) || "profiles/input_conf".equals(path);
    }

    private static void apply(File staging) throws IOException {
        File root = MpvConfigStore.configDir();
        replaceFile(new File(staging, MPV_CONF), new File(root, MPV_CONF));
        replaceFile(new File(staging, INPUT_CONF), new File(root, INPUT_CONF));
        replaceDirectory(new File(staging, "scripts"), new File(root, "scripts"));
        replaceDirectory(new File(staging, PROFILES + "mpv_conf"), new File(root, "profiles/mpv_conf"));
        replaceDirectory(new File(staging, PROFILES + "input_conf"), new File(root, "profiles/input_conf"));
        MpvConfigStore.ensureCustomButtonScript();
    }

    private static void replaceFile(File source, File target) throws IOException {
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace MPV config file");
        if (!source.isFile()) return;
        copy(source, target);
    }

    private static void replaceDirectory(File source, File target) throws IOException {
        deleteRecursively(target);
        if (!source.exists()) {
            if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Cannot create MPV config directory");
            return;
        }
        if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Cannot create MPV config directory");
        File[] files = source.listFiles();
        if (files == null) return;
        for (File file : files) {
            File output = new File(target, file.getName());
            if (file.isDirectory()) copyDirectory(file, output);
            else copy(file, output);
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Cannot create MPV config directory");
        File[] files = source.listFiles();
        if (files == null) return;
        for (File file : files) {
            File output = new File(target, file.getName());
            if (file.isDirectory()) copyDirectory(file, output);
            else copy(file, output);
        }
    }

    private static void copy(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Cannot create MPV config directory");
        byte[] buffer = new byte[BUFFER_SIZE];
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source), BUFFER_SIZE);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target), BUFFER_SIZE)) {
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        target.setLastModified(source.lastModified());
    }

    private static Stats addTree(File root, File directory, String prefix, ZipOutputStream zos, byte[] buffer) throws IOException {
        if (!directory.isDirectory() || !isRegularPath(directory) || !inside(root, directory)) return new Stats();
        Stats stats = new Stats();
        File[] files = directory.listFiles();
        if (files == null) return stats;
        for (File file : files) {
            if (!isRegularPath(file) || !inside(root, file)) continue;
            String name = prefix + file.getName();
            if (file.isDirectory()) {
                addDirectoryEntry(file, name + "/", zos);
                stats.add(addTree(root, file, name + "/", zos, buffer));
            } else if (file.isFile()) {
                stats.add(addFile(root, file, name, zos, buffer));
            }
        }
        return stats;
    }

    private static Stats addFile(File root, File file, String name, ZipOutputStream zos, byte[] buffer) throws IOException {
        if (!isRegularPath(file) || !inside(root, file) || file.length() > MAX_ENTRY_BYTES) throw new IOException("MPV config file too large");
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        long size = 0;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                zos.write(buffer, 0, read);
            }
        }
        zos.closeEntry();
        return new Stats(1, size);
    }

    private static void checkLimits(int count, long size) throws IOException {
        if (count > MAX_ENTRIES || size > MAX_ARCHIVE_BYTES) throw new IOException("MPV config archive too large");
    }

    private static void addDirectoryEntry(File directory, String name, ZipOutputStream zos) throws IOException {
        if (!directory.isDirectory() || !isRegularPath(directory) || !inside(MpvConfigStore.configDir(), directory)) return;
        ZipEntry entry = new ZipEntry(name.endsWith("/") ? name : name + "/");
        entry.setTime(directory.lastModified());
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String path = value.replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        while (path.endsWith("/") && path.length() > 0) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static boolean inside(File root, File file) {
        try {
            String base = root.getCanonicalPath();
            String path = file.getCanonicalPath();
            return path.equals(base) || path.startsWith(base + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isRegularPath(File file) {
        try {
            return file.getAbsoluteFile().equals(file.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        try {
            if (!file.getAbsoluteFile().equals(file.getCanonicalFile())) {
                file.delete();
                return;
            }
        } catch (IOException e) {
            file.delete();
            return;
        }
        File[] files = file.isDirectory() ? file.listFiles() : null;
        if (files != null) for (File child : files) deleteRecursively(child);
        file.delete();
    }

    public static final class Archive {
        private final File file;
        private final int count;
        private final long rawSize;
        private final long zipSize;

        private Archive(File file, int count, long rawSize, long zipSize) {
            this.file = file;
            this.count = count;
            this.rawSize = rawSize;
            this.zipSize = zipSize;
        }

        public File getFile() {
            return file;
        }

        public int getCount() {
            return count;
        }

        public long getRawSize() {
            return rawSize;
        }

        public long getZipSize() {
            return zipSize;
        }

        public void delete() {
            deleteRecursively(file);
        }
    }

    private static final class Stats {
        private int count;
        private long size;

        private Stats() {
        }

        private Stats(int count, long size) {
            this.count = count;
            this.size = size;
        }

        private void add(Stats other) {
            count += other.count;
            size += other.size;
        }
    }
}
