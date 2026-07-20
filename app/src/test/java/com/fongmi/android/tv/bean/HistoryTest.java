package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
