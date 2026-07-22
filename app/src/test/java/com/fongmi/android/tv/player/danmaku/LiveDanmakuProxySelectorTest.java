package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LiveDanmakuProxySelectorTest {

    @Test
    public void bypassesDelegateForLoopbackAndLanTargets() {
        RecordingSelector delegate = new RecordingSelector();
        LiveDanmakuProxySelector selector = new LiveDanmakuProxySelector(delegate);

        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("ws://127.0.0.1:5266/live")));
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("ws://192.168.1.5:5266/live")));
        assertEquals(0, delegate.selectCount);
    }

    @Test
    public void delegatesPublicTargetsToAppProxySelector() {
        RecordingSelector delegate = new RecordingSelector();
        LiveDanmakuProxySelector selector = new LiveDanmakuProxySelector(delegate);

        assertEquals(delegate.proxies, selector.select(URI.create("wss://example.com/live")));
        assertEquals(1, delegate.selectCount);
    }

    private static final class RecordingSelector extends ProxySelector {

        private final List<Proxy> proxies = List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)));
        private int selectCount;

        @Override
        public List<Proxy> select(URI uri) {
            selectCount++;
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException error) {
        }
    }
}
