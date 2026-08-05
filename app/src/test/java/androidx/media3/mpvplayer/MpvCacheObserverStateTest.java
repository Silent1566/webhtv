package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvCacheObserverStateTest {

    @Test
    public void recordsFirstValueForEachLogicalMetric() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertFalse(state.hasObservedValues());
        assertTrue(state.record("demuxer-cache-state/cache-duration", 4.5));
        assertTrue(state.hasObservedValues());
        assertEquals(1, state.observedCount());
    }

    @Test
    public void aliasesShareOneLogicalMetric() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertTrue(state.record("cache-speed", 1024L));
        assertFalse(state.record("demuxer-cache-state/raw-input-rate", 2048L));
        assertEquals(1, state.observedCount());
    }

    @Test
    public void unavailableAndUnrelatedPropertiesAreIgnored() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertFalse(state.record("demuxer-cache-state/fw-bytes", null));
        assertFalse(state.record("time-pos", 1.0));
        assertFalse(state.hasObservedValues());
        assertEquals(0, state.observedCount());
    }

    @Test
    public void resetRequiresFreshObserverValuesForNewMedia() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        state.record("demuxer-cache-state/idle", false);
        state.record("demuxer-cache-state/eof-cached", true);

        assertEquals(2, state.observedCount());
        state.reset();

        assertEquals(0, state.observedCount());
        assertFalse(state.hasObservedValues());
    }
}