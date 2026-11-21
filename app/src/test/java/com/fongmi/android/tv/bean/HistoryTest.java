package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryTest {

    @Test
    public void getVodId_toleratesIncompleteKey() {
        History history = new History();
        history.setKey("incomplete");

        assertEquals("", history.getVodId());
    }

    @Test
    public void getVodId_excludesConfigIdFromPushHistoryKey() {
        History history = new History();
        history.setKey("push_agent@@@https://pan.quark.cn/s/eab19bde98be@@@48");

        assertEquals("https://pan.quark.cn/s/eab19bde98be", history.getVodId());
    }

    @Test
    public void getVodId_toleratesPreviouslyDuplicatedConfigSuffix() {
        History history = new History();
        history.setKey("push_agent@@@https://pan.quark.cn/s/eab19bde98be@@@48@@@48");

        assertEquals("https://pan.quark.cn/s/eab19bde98be", history.getVodId());
    }

    @Test
    public void isSameContent_keepsUnchangedCopy() {
        History history = coverHistory();

        assertTrue(history.isSameContent(history.copy()));
    }

    @Test
    public void isSameContent_refreshesPositionWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setPosition(90000);

        assertFalse(history.isSameContent(updated));
    }

    @Test
    public void isSameContent_refreshesDurationWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setDuration(60000);

        assertFalse(history.isSameContent(updated));
    }

    @Test
    public void isSameContent_refreshesEpisodeWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setVodRemarks("Episode 2");

        assertFalse(history.isSameContent(updated));
    }

    @Test
    public void kernelForFlag_keepsEachLineSeparate() {
        History history = new History();
        history.setVodFlag("高清线路2");

        history.setKernelForFlag("高清线路1", PlayerSetting.EXO);
        history.setKernelForFlag("高清线路2", PlayerSetting.MPV);

        assertEquals(PlayerSetting.EXO, history.getKernelForFlag("高清线路1"));
        assertEquals(PlayerSetting.MPV, history.getKernelForFlag("高清线路2"));
        assertEquals("a line nobody picked must not inherit another line's core",
                PlayerSetting.NONE, history.getKernelForFlag("高清线路3"));
    }

    @Test
    public void kernelForFlag_ignoresTheLegacyWholeShowValue() {
        // 旧版把用户的选择写在整剧 player 上，而 vodFlag 会被之后的换线路改写，
        // 那份值无法可靠归到某条线路上，迁移后一律不再参与判定。
        History legacy = new Gson().fromJson("{\"player\":0,\"vodFlag\":\"高清线路2\"}", History.class);

        assertEquals(PlayerSetting.NONE, legacy.getKernelForFlag("高清线路2"));
        assertEquals(PlayerSetting.NONE, legacy.getKernelForFlag("高清线路1"));

        legacy.setKernelForFlag("高清线路1", PlayerSetting.MPV);

        assertEquals(PlayerSetting.MPV, legacy.getKernelForFlag("高清线路1"));
        assertEquals("a per-line pick must not resurrect the legacy whole-show value",
                PlayerSetting.NONE, legacy.getKernelForFlag("高清线路2"));
    }

    @Test
    public void kernelForFlag_survivesCopyAndJsonRoundTrip() {
        History history = new History();
        history.setKernelForFlag("高清线路1", PlayerSetting.MPV);

        assertEquals(PlayerSetting.MPV, history.copy().getKernelForFlag("高清线路1"));

        History restored = new Gson().fromJson(new Gson().toJson(history), History.class);
        assertEquals(PlayerSetting.MPV, restored.getKernelForFlag("高清线路1"));
    }

    @Test
    public void kernelForFlag_toleratesCorruptStorage() {
        History history = new Gson().fromJson("{\"kernelByFlag\":\"not json\"}", History.class);

        assertEquals("a corrupt line table must behave like no memory instead of throwing",
                PlayerSetting.NONE, history.getKernelForFlag("高清线路1"));
    }

    private static History coverHistory() {
        History history = new History();
        history.setKey("test@@@cover");
        history.setVodName("Title");
        history.setVodPic("cover.jpg");
        history.setVodRemarks("Episode 1");
        history.setPosition(30000);
        history.setDuration(120000);
        history.setCreateTime(1000);
        return history;
    }
}
