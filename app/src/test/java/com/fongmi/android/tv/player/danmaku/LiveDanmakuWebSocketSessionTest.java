package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuWebSocketSessionTest {

    @Test
    public void invalidConnectionMovesThroughConnectingToStoppedAndCanRelease() {
        List<LiveDanmakuWebSocketSession.State> states = new ArrayList<>();
        LiveDanmakuWebSocketSession session = new LiveDanmakuWebSocketSession(new LiveDanmakuWebSocketSession.Listener() {
            @Override
            public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String url, int code, String detail) {
                states.add(state);
            }

            @Override
            public void onMessage(long generation, String text) {
            }
        });

        long generation = session.connect("ws://");

        assertEquals(1L, generation);
        assertEquals(LiveDanmakuWebSocketSession.State.STOPPED, session.state());
        assertEquals(List.of(LiveDanmakuWebSocketSession.State.CONNECTING, LiveDanmakuWebSocketSession.State.STOPPED), states);
        session.release();
        assertEquals(LiveDanmakuWebSocketSession.State.RELEASED, session.state());
        assertTrue(session.generation() > generation);
    }
}
