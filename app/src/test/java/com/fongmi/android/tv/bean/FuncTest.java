package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FuncTest {

    @Test
    public void historyButtonUsesHistoryIcon() {
        assertEquals(R.drawable.ic_setting_history, Func.create(R.string.home_history_button).getDrawable());
    }
}
