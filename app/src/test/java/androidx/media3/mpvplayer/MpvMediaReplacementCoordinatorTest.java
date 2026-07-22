package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvMediaReplacementCoordinatorTest {

    @Test
    public void reusingActiveContextIgnoresOldEndBeforeNewStart() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        coordinator.begin(true, true, false);

        assertTrue(coordinator.shouldIgnoreEndFile(false, false));
        assertFalse(coordinator.shouldIgnoreEndFile(false, false));
    }

    @Test
    public void newStartStopsIgnoringEndFile() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        coordinator.begin(true, true, false);
        coordinator.onStartFile();

        assertFalse(coordinator.shouldIgnoreEndFile(false, true));
    }

    @Test
    public void pendingStopDefersPrepareUntilAcknowledged() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        coordinator.begin(true, false, true);

        assertTrue(coordinator.deferPrepare(true, true));
        assertTrue(coordinator.resumeAfterStopAcknowledged());
        assertFalse(coordinator.resumeAfterStopAcknowledged());
    }

    @Test
    public void freshContextNeverDefersPrepare() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        coordinator.begin(false, false, true);

        assertFalse(coordinator.deferPrepare(false, true));
    }

    @Test
    public void stopTimeoutIgnoresLateOldEndBeforeNewStart() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        coordinator.begin(true, false, true);
        coordinator.deferPrepare(true, true);

        assertTrue(coordinator.resumeAfterTimeout());
        assertTrue(coordinator.shouldIgnoreEndFile(false, false));
    }

    @Test
    public void newerMediaInvalidatesOlderGeneration() {
        MpvMediaReplacementCoordinator coordinator = new MpvMediaReplacementCoordinator();
        long first = coordinator.begin(true, true, false);
        long second = coordinator.begin(true, true, false);

        assertFalse(coordinator.isCurrent(first));
        assertTrue(coordinator.isCurrent(second));
    }
}
