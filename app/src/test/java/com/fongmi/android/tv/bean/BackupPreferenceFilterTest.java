package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupPreferenceFilterTest {

    @Test
    public void webHomeExtensionPreferencesFollowWebHomeOption() {
        SyncOptions webHomeOnly = new SyncOptions().config(false).spider(false).webHome(true).settings(false);
        SyncOptions spiderOnly = new SyncOptions().config(false).spider(true).webHome(false).settings(false);

        assertTrue(Backup.include("web_home_extension", webHomeOnly));
        assertTrue(Backup.include("web_home_extension_user_sources", webHomeOnly));
        assertTrue(Backup.include("web_home_ext_enabled_123", webHomeOnly));

        assertFalse(Backup.include("web_home_extension", spiderOnly));
        assertFalse(Backup.include("web_home_extension_user_sources", spiderOnly));
        assertFalse(Backup.include("web_home_ext_enabled_123", spiderOnly));
    }

    @Test
    public void unrelatedWebHomeDisplaySettingRemainsAnAppSetting() {
        SyncOptions webHomeOnly = new SyncOptions().config(false).spider(false).webHome(true).settings(false);
        SyncOptions settingsOnly = new SyncOptions().config(false).spider(false).webHome(false).settings(true);

        assertFalse(Backup.include("web_home_fullscreen", webHomeOnly));
        assertTrue(Backup.include("web_home_fullscreen", settingsOnly));
    }

    @Test
    public void episodeHistoryFollowsSettingsOption() {
        SyncOptions settingsOnly = new SyncOptions().config(false).spider(false).webHome(false).settings(true);
        SyncOptions spiderOnly = new SyncOptions().config(false).spider(true).webHome(false).settings(false);

        assertTrue(Backup.include("episode_history", settingsOnly));
        assertFalse(Backup.include("episode_history", spiderOnly));
    }

    @Test
    public void manifestCountCoversSourcesSwitchAndPerExtensionState() {
        Backup backup = new Backup();
        backup.setPrefers(Map.of(
                "web_home_extension", true,
                "web_home_extension_user_sources", "[{\"id\":\"one\"},{\"id\":\"two\"}]",
                "web_home_ext_enabled_123", false,
                "unrelated", "value"
        ));

        assertEquals(3, backup.getWebHomeExtensionPreferenceCount());
        assertEquals(2, backup.getWebHomeExtensionSourceCount());
    }
}
