package com.fongmi.android.tv.smb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SmbUriResolverTest {

    @Test
    public void storageUriNeverCarriesCredentials() {
        assertEquals("smb://nas.local/media/movie.mkv",
                SmbUriResolver.storageUri("nas.local", 445, "media", "/movie.mkv"));
        assertEquals("smb://nas.local/media/tv/S01E01.mkv",
                SmbUriResolver.storageUri("nas.local", -1, "media", "tv/S01E01.mkv"));
        assertNull("a share without a file path cannot be played", SmbUriResolver.storageUri("nas.local", 445, "", "/x.mkv"));
        assertNull("a missing host cannot be played", SmbUriResolver.storageUri("", 445, "media", "/x.mkv"));
    }

    @Test
    public void customPortSurvivesAndDefaultPortIsDropped() {
        assertEquals("smb://nas.local:1445/media/x.mkv",
                SmbUriResolver.storageUri("nas.local", 1445, "media", "/x.mkv"));
        assertEquals("smb://nas.local/media/x.mkv",
                SmbUriResolver.storageUri("nas.local", 445, "media", "/x.mkv"));
    }

    @Test
    public void playbackUriAddsPercentEncodedUserInfo() {
        String storage = "smb://nas.local/media/x.mkv";
        assertEquals("smb://user:p%40ss%3Aw%2Frd@nas.local/media/x.mkv",
                SmbUriResolver.playbackUri(storage, "user", "p@ss:w/rd", ""));
        assertEquals("guest stays untouched", "smb://nas.local/media/x.mkv",
                SmbUriResolver.playbackUri(storage, "", "", ""));
        assertEquals("empty password still authenticates the user",
                "smb://user@nas.local/media/x.mkv",
                SmbUriResolver.playbackUri(storage, "user", "", ""));
    }

    @Test
    public void stripCredentialsIsIdempotentAndKeepsPort() {
        assertEquals("smb://nas.local:1445/media/x.mkv",
                SmbUriResolver.stripCredentials("smb://us%40er:pw@nas.local:1445/media/x.mkv"));
        assertEquals("smb://nas.local/media/x.mkv",
                SmbUriResolver.stripCredentials("smb://nas.local/media/x.mkv"));
        assertEquals("smb://nas.local/media/a@b.mkv",
                SmbUriResolver.stripCredentials("smb://nas.local/media/a@b.mkv"));
    }

    @Test
    public void hostKeyIsCaseInsensitiveAndIncludesShare() {
        assertEquals("nas.local/media", SmbUriResolver.hostKey("smb://NAS.Local/media/x.mkv"));
        assertEquals("nas.local/media", SmbUriResolver.hostKey("smb://user:pw@nas.local:445/media/y.mkv"));
        assertEquals("a share-only URI may still pre-seed credentials", "nas.local/x.mkv",
                SmbUriResolver.hostKey("smb://nas.local/x.mkv"));
        assertNull("a bare host has no share to key on", SmbUriResolver.hostKey("smb://nas.local"));
        assertNull(SmbUriResolver.hostKey(""));
    }

    @Test
    public void hostKeyOverloadMatchesTheUriForm() {
        assertEquals(SmbUriResolver.hostKey("smb://nas.local/media/x.mkv"),
                SmbUriResolver.hostKey("NAS.Local", "media"));
        assertNull(SmbUriResolver.hostKey("nas.local", ""));
        assertNull(SmbUriResolver.hostKey("", "media"));
    }

    @Test
    public void authPartsRoundTripThroughPlaybackUri() {
        String playback = SmbUriResolver.playbackUri("smb://nas.local/media/x.mkv", "us er", "p w", "WORKGROUP");
        assertEquals("us er", SmbUriResolver.username(playback));
        assertEquals("p w", SmbUriResolver.password(playback));
        assertEquals("smb://nas.local/media/x.mkv", SmbUriResolver.stripCredentials(playback));
    }
}
