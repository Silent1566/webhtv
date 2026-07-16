package com.fongmi.android.tv.player.iso;

import java.io.Closeable;
import java.io.IOException;

public interface RemoteIsoSource extends Closeable {

    long length() throws IOException;

    int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException;

    String validator();

    @Override
    void close();
}
