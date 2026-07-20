package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppBackupTest {

    @Test
    public void newBackupsUseShortPrefix() {
        String name = AppBackup.fileName();
        assertTrue(name.matches("bak-\\d{8}-\\d{4}\\.zip"));
    }

    @Test
    public void recognizesCurrentAndLegacyBackupNames() {
        assertTrue(AppBackup.isBackupZipName("bak-20260718-2226.zip"));
        assertTrue(AppBackup.isBackupZipName("webhtv-backup-20260718-2226.zip"));
        assertTrue(AppBackup.isBackupManifestName("bak-20260718-2226.json"));
        assertTrue(AppBackup.isBackupManifestName("webhtv-backup-20260718-2226.json"));
        assertFalse(AppBackup.isBackupZipName("backup-20260718-2226.zip"));
    }

    @Test
    public void sortsCurrentAndLegacyNamesByTimestamp() {
        assertEquals("20260718-2226", AppBackup.backupSortKey("bak-20260718-2226.zip"));
        assertEquals("20260718-2225", AppBackup.backupSortKey("webhtv-backup-20260718-2225.zip"));
    }
}
