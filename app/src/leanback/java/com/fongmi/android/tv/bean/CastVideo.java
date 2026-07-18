package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.util.Map;

public record CastVideo(String name, String url, long position, Map<String, String> headers) {

    public CastVideo {
        if (url.startsWith("file")) url = Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", "");
        if (url.contains("127.0.0.1")) url = url.replace("127.0.0.1", Util.getIp());
    }
}