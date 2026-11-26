package com.fongmi.android.tv.remote;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemotePlayerCommandTest {

    private static JsonObject payload(String action, Long value) {
        JsonObject object = new JsonObject();
        object.addProperty("action", action);
        if (value != null) object.addProperty("value", value);
        return object;
    }

    @Test
    public void parsesEverySupportedAction() {
        for (RemotePlayerCommand.Action action : RemotePlayerCommand.Action.values()) {
            RemotePlayerCommand.Parsed parsed = RemotePlayerCommand.parse(payload(action.wire, null));
            assertEquals(action, parsed.action);
        }
    }

    @Test
    public void rejectsUnknownOrMissingActions() {
        assertNull(RemotePlayerCommand.parse(null));
        assertNull(RemotePlayerCommand.parse(new JsonObject()));
        assertNull(RemotePlayerCommand.parse(payload("fly_to_mars", null)));
        assertNull("an empty action must not fall through to a default", RemotePlayerCommand.parse(payload("", null)));
    }

    @Test
    public void seekValueIsOptionalAndClamped() {
        assertEquals(10000L, RemotePlayerCommand.parse(payload("seek_forward", null)).valueMs);
        assertEquals(30000L, RemotePlayerCommand.parse(payload("seek_forward", 30000L)).valueMs);
        assertEquals("too small a step is pointless", 1000L, RemotePlayerCommand.parse(payload("seek_back", 5L)).valueMs);
        assertEquals("do not let a payload jump to the end of a file", 600000L,
                RemotePlayerCommand.parse(payload("seek_back", 9999999L)).valueMs);
    }

    @Test
    public void navigationActionsCarryNoSeekValue() {
        RemotePlayerCommand.Parsed parsed = RemotePlayerCommand.parse(payload("left", 5000L));
        assertEquals(RemotePlayerCommand.Action.LEFT, parsed.action);
        assertEquals(0L, parsed.valueMs);
    }
}
