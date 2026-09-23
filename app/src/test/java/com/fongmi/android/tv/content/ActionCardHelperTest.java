package com.fongmi.android.tv.content;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActionCardHelperTest {

    @Test
    public void emptyResponseIsSuccessfulAndSilent() {
        assertEquals("", ActionCardHelper.nonJsonResponse(null));
        assertEquals("", ActionCardHelper.nonJsonResponse(""));
        assertEquals("", ActionCardHelper.nonJsonResponse("  \n\t"));
        assertEquals("", ActionCardHelper.nonJsonResponse("null"));
        assertEquals("", ActionCardHelper.nonJsonResponse("  null  "));
    }

    @Test
    public void nonJsonTextIsPreservedForTheUser() {
        assertEquals("动作执行失败", ActionCardHelper.nonJsonResponse("  动作执行失败  "));
    }
}
