package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigUseLiveSyncAcceptanceTest {

    @Test
    public void switchingVodAlsoSwitchsItsMatchingLiveConfig() throws Exception {
        String manage = read("app/src/main/java/com/fongmi/android/tv/server/process/Manage.java");
        String remote = read("app/src/main/java/com/fongmi/android/tv/remote/RemoteConfigOps.java");
        String setting = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java");
        String settingFragment = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java");

        assertTrue(manage.contains("default -> {\n                VodConfig.load(config, new Callback());\n                loadMatchingLiveConfig(config);"));
        assertTrue(manage.contains("Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);"));
        assertTrue(manage.contains("if (liveConfig == null) return;"));
        assertTrue(remote.contains("Config liveConfig = type == 0 ? matchingLiveConfig(config) : null;"));
        assertTrue(remote.contains("if (liveConfig != null) LiveConfig.load(liveConfig, new Callback());"));
        assertTrue(remote.contains("Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);"));
        assertTrue(setting.contains("loadMatchingLiveConfig(config);"));
        assertTrue(setting.contains("Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);"));
        assertTrue(settingFragment.contains("loadMatchingLiveConfig(config);"));
        assertTrue(settingFragment.contains("Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);"));
        assertFalse(manage.contains("default -> VodConfig.load(config, new Callback());"));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
