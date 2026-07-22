package com.fongmi.android.tv.player.danmaku;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

final class LiveDanmakuProxySelector extends ProxySelector {

    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);

    private final ProxySelector delegate;

    LiveDanmakuProxySelector(ProxySelector delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null || DanmakuUrlPolicy.isDirectHost(uri.getHost())) return DIRECT;
        if (delegate == null) return DIRECT;
        List<Proxy> selected = delegate.select(uri);
        return selected == null || selected.isEmpty() ? DIRECT : selected;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException error) {
        if (uri == null || DanmakuUrlPolicy.isDirectHost(uri.getHost()) || delegate == null) return;
        delegate.connectFailed(uri, socketAddress, error);
    }
}
