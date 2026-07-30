package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;

import org.junit.Test;

public class WebHomeThemeBridgeTest {

    @Test
    public void remotePageMustStayInsideTheSupportedRange() {
        JsonObject payload = new JsonObject();
        assertEquals(1, WebHomeThemeBridge.positiveInt(payload, "page", 1));

        payload.addProperty("page", WebHomeThemeBridge.MAX_PAGE);
        assertEquals(WebHomeThemeBridge.MAX_PAGE, WebHomeThemeBridge.positiveInt(payload, "page", 1));

        assertInvalidPage(0);
        assertInvalidPage(WebHomeThemeBridge.MAX_PAGE + 1);
    }

    private static void assertInvalidPage(int page) {
        JsonObject payload = new JsonObject();
        payload.addProperty("page", page);
        try {
            WebHomeThemeBridge.positiveInt(payload, "page", 1);
            fail("Expected an invalid page error for " + page);
        } catch (IllegalArgumentException expected) {
        }
    }
}
