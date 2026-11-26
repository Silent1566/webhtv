package com.fongmi.android.tv.smb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SmbPathTest {

    @Test
    public void normalizeAcceptsWindowsAndUnixSeparators() {
        assertEquals("tv/S01E01.mkv", SmbPath.normalize("tv\\S01E01.mkv"));
        assertEquals("tv/S01E01.mkv", SmbPath.normalize("/tv//S01E01.mkv"));
        assertEquals("", SmbPath.normalize("/"));
        assertEquals("", SmbPath.normalize(null));
        assertEquals("", SmbPath.normalize("   "));
    }

    @Test
    public void normalizeRejectsTraversal() {
        assertNull(SmbPath.normalize("../etc/passwd"));
        assertNull(SmbPath.normalize("tv/../../secret"));
        assertEquals("a/..b/c", SmbPath.normalize("a/..b/c"));
    }

    @Test
    public void resolveJoinsWithoutDoublingSlashes() {
        assertEquals("tv/S01E01.mkv", SmbPath.resolve("tv", "S01E01.mkv"));
        assertEquals("tv/S01E01.mkv", SmbPath.resolve("/tv/", "/S01E01.mkv"));
        assertEquals("S01E01.mkv", SmbPath.resolve("", "S01E01.mkv"));
        assertNull("a child name that escapes the share must be refused", SmbPath.resolve("tv", "../x.mkv"));
    }

    @Test
    public void parentWalksUpToShareRoot() {
        assertEquals("tv", SmbPath.parent("tv/S01E01.mkv"));
        assertEquals("", SmbPath.parent("tv"));
        assertEquals("", SmbPath.parent(""));
        assertEquals("a/b", SmbPath.parent("a/b/c"));
    }

    @Test
    public void displayNameIsTheLastSegment() {
        assertEquals("S01E01.mkv", SmbPath.displayName("tv/S01E01.mkv"));
        assertEquals("tv", SmbPath.displayName("tv"));
        assertEquals("", SmbPath.displayName(""));
    }

    @Test
    public void shareIsTheFirstSegment() {
        assertEquals("media", SmbPath.share("media/tv/S01E01.mkv"));
        assertEquals("media", SmbPath.share("media"));
        assertEquals("", SmbPath.share(""));
    }
}
