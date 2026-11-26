package com.fongmi.android.tv.smb;

import org.junit.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SmbTimeoutTest {

    @Test
    public void returnsTheTaskValueWithinBudget() throws Exception {
        assertEquals("ok", SmbTimeout.call(() -> "ok", 1000L, () -> fail("must not time out")));
    }

    @Test
    public void propagatesTheTaskExceptionInsteadOfSwallowingIt() {
        try {
            SmbTimeout.call(() -> {
                throw new IllegalStateException("boom");
            }, 1000L, () -> fail("must not time out"));
            fail("expected the task exception to surface");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException || "boom".equals(e.getMessage()));
        }
    }

    @Test
    public void timesOutRunsTheCleanupAndInterruptsTheWorker() {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        long startedAt = System.currentTimeMillis();
        try {
            SmbTimeout.call(() -> {
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
                return "late";
            }, 120L, () -> cleaned.set(true));
            fail("expected a timeout");
        } catch (Exception e) {
            assertTrue("a timeout must be reported as TimeoutException, got " + e, e instanceof TimeoutException);
        }
        assertTrue("cleanup must run so the blocking SMB call can unblock", cleaned.get());
        assertTrue("the worker must be interrupted rather than left running", awaitFlag(interrupted));
        assertTrue("must not wait for the whole sleep", System.currentTimeMillis() - startedAt < 2000L);
    }

    /** 中断是异步投递的：给工作线程一个很短的上限，而不是在同一刻断言。 */
    private static boolean awaitFlag(AtomicBoolean flag) {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (flag.get()) return true;
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                return flag.get();
            }
        }
        return flag.get();
    }

    @Test
    public void rejectsNonPositiveBudgets() {
        try {
            SmbTimeout.call(() -> "never", 0L, () -> fail("no cleanup expected"));
            fail("expected an argument failure");
        } catch (Exception e) {
            assertFalse(e instanceof TimeoutException);
        }
    }
}
