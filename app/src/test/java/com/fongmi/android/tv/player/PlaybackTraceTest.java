package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackTraceTest {

    @Test
    public void ensureKeepsOneTraceUntilExplicitBegin() {
        PlaybackTrace trace = new PlaybackTrace();

        String first = trace.ensure(1_000);
        assertEquals(first, trace.ensure(2_000));

        String second = trace.begin(2_000);
        assertFalse(first.equals(second));
        assertEquals(second, trace.current());
    }

    @Test
    public void generatedIdContainsOnlyTimeAndSequenceShape() {
        String traceId = PlaybackTrace.createId(123_456, 42);

        assertTrue(traceId.matches("p-[0-9a-z]+-[0-9a-z]+"));
        assertFalse(traceId.contains("http"));
        assertFalse(traceId.contains("token"));
        assertFalse(traceId.contains("movie"));
    }

    @Test
    public void invalidExternalIdsAreNotAccepted() {
        assertEquals(PlaybackTrace.NONE, PlaybackTrace.normalize(null));
        assertEquals(PlaybackTrace.NONE, PlaybackTrace.normalize("movie-https-token"));
        assertEquals("p-abc-1", PlaybackTrace.normalize("p-abc-1"));
    }

    @Test
    public void clearRemovesCurrentTrace() {
        PlaybackTrace trace = new PlaybackTrace();
        trace.begin(100);

        trace.clear();

        assertEquals(PlaybackTrace.NONE, trace.current());
    }

    @Test
    public void startupStagesUseMonotonicElapsedTimeAndIgnoreDuplicates() {
        PlaybackTrace trace = new PlaybackTrace();
        trace.begin(1_000);

        assertTrue(trace.mark(PlaybackTrace.Stage.REQUEST, 1_000, "reason=start"));
        assertTrue(trace.mark(PlaybackTrace.Stage.PREPARE, 1_125, "player=0"));
        assertFalse(trace.mark(PlaybackTrace.Stage.PREPARE, 1_500, "retry"));

        assertEquals(0, trace.stageElapsedMs(PlaybackTrace.Stage.REQUEST));
        assertEquals(125, trace.stageElapsedMs(PlaybackTrace.Stage.PREPARE));
        assertTrue(trace.hasStage(PlaybackTrace.Stage.PREPARE));
    }

    @Test
    public void newTraceClearsPreviousStartupStages() {
        PlaybackTrace trace = new PlaybackTrace();
        trace.begin(100);
        trace.mark(PlaybackTrace.Stage.READY, 200, "player=0");

        trace.begin(300);

        assertFalse(trace.hasStage(PlaybackTrace.Stage.READY));
        assertEquals(-1, trace.stageElapsedMs(PlaybackTrace.Stage.READY));
    }

    @Test
    public void stageCannotBeRecordedWithoutActiveTrace() {
        PlaybackTrace trace = new PlaybackTrace();

        assertFalse(trace.mark(PlaybackTrace.Stage.REQUEST, 100, "reason=start"));
    }
}
