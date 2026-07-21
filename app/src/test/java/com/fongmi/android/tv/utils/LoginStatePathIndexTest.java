package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class LoginStatePathIndexTest {

    @Test
    public void keepsParentAndRemovesDescendant() {
        LoginStatePathIndex index = new LoginStatePathIndex();
        index.add("app/shared_prefs/a.xml");
        index.add("app/shared_prefs/b.xml");
        index.add("app/shared_prefs");

        assertEquals(Arrays.asList("app/shared_prefs"), index.asList());
        assertTrue(index.hasAncestor("app/shared_prefs/a.xml"));
        assertTrue(index.hasDescendant("app"));
    }

    @Test
    public void doesNotReplaceExistingParentWithChild() {
        LoginStatePathIndex index = new LoginStatePathIndex(Arrays.asList("sdcard/TVBox"));
        index.add("sdcard/TVBox/config.json");

        assertEquals(Arrays.asList("sdcard/TVBox"), index.asList());
    }

    @Test
    public void detectsPartialDirectorySelection() {
        LoginStatePathIndex index = new LoginStatePathIndex(Arrays.asList("app/shared_prefs/token.xml"));

        assertFalse(index.hasAncestor("app/shared_prefs"));
        assertTrue(index.hasDescendant("app/shared_prefs"));
        assertFalse(index.hasDescendant("app/cache"));
    }

    @Test
    public void ignoresBlankAndNormalizesSeparators() {
        LoginStatePathIndex index = new LoginStatePathIndex(Arrays.asList("", " app\\cookies ", "app/cookies/"));

        assertEquals(Arrays.asList("app/cookies"), index.asList());
    }

    @Test(timeout = 5000)
    public void handlesFiveThousandLearnedFilesWithoutQuadraticQueries() {
        LoginStatePathIndex index = new LoginStatePathIndex();
        for (int i = 0; i < 5000; i++) index.add("app/accounts/user_" + i + "/token.json");

        assertEquals(5000, index.size());
        for (int i = 0; i < 5000; i++) assertTrue(index.hasAncestor("app/accounts/user_" + i + "/token.json"));
        assertTrue(index.hasDescendant("app/accounts"));

        index.add("app/accounts");
        assertEquals(Arrays.asList("app/accounts"), index.asList());
    }
}
