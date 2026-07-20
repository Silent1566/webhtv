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
}
