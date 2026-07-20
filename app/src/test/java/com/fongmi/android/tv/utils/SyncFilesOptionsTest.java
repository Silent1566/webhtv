package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.SyncOptions;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyncFilesOptionsTest {

    @Test
    public void webHomeOnlyIncludesCustomCspFiles() {
        SyncOptions options = new SyncOptions().config(false).spider(false).webHome(true);

        assertEquals(List.of(SyncFiles.CUSTOM_CSP_PATH), SyncFiles.getPaths(options));
    }

    @Test
    public void configOnlyIncludesCustomCspFiles() {
        SyncOptions options = new SyncOptions().config(true).spider(false).webHome(false);

        assertEquals(List.of(SyncFiles.CUSTOM_CSP_PATH), SyncFiles.getPaths(options));
    }

    @Test
    public void noFileBackedFeatureProducesNoArchivePaths() {
        SyncOptions options = new SyncOptions().config(false).spider(false).webHome(false);

        assertTrue(SyncFiles.getPaths(options).isEmpty());
    }
}
