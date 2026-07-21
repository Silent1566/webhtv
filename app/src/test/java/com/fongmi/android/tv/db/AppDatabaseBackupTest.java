package com.fongmi.android.tv.db;

import com.fongmi.android.tv.utils.AppBackup;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AppDatabaseBackupTest {

    @Test
    public void publishesCompletedBackupByReplacingTheTarget() throws Exception {
        Path directory = Files.createTempDirectory("webhtv-backup-");
        File temporary = directory.resolve(".backup.tmp").toFile();
        File target = directory.resolve("bak-20260720-1200.zip").toFile();
        try {
            Files.writeString(temporary.toPath(), "new", StandardCharsets.UTF_8);
            Files.writeString(target.toPath(), "old", StandardCharsets.UTF_8);

            AppDatabase.publishBackup(temporary, target);

            assertEquals("new", Files.readString(target.toPath(), StandardCharsets.UTF_8));
            assertFalse(temporary.exists());
            assertFalse(AppBackup.isBackup(directory.resolve(".backup.tmp").toFile()));
        } finally {
            Files.deleteIfExists(temporary.toPath());
            Files.deleteIfExists(target.toPath());
            Files.deleteIfExists(directory);
        }
    }
}
