package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LocalProxyRangeDataSourceTest {

    @Test
    public void getResourceLength_parsesRealProxyContentRange() {
        Map<String, List<String>> headers = Collections.singletonMap("content-range", Collections.singletonList("bytes 0-1023/22493671210"));

        assertEquals(22493671210L, LocalProxyRangeDataSource.getResourceLength(headers));
    }

    @Test
    public void getResourceLength_keepsUnknownLengthUnresolved() {
        Map<String, List<String>> headers = Collections.singletonMap("Content-Range", Collections.singletonList("bytes 0-1023/*"));

        assertEquals(C.LENGTH_UNSET, LocalProxyRangeDataSource.getResourceLength(headers));
    }

    @Test
    public void getChunkLength_boundsOpenAndFinalRanges() {
        assertEquals(8, LocalProxyRangeDataSource.getChunkLength(C.LENGTH_UNSET, 8));
        assertEquals(8, LocalProxyRangeDataSource.getChunkLength(20, 8));
        assertEquals(4, LocalProxyRangeDataSource.getChunkLength(4, 8));
    }

}
