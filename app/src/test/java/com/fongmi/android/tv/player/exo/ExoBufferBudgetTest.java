package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExoBufferBudgetTest {

    @Test
    public void lowRamDeviceUsesTwentyPercentHeapBudget() {
        assertEquals(percentOfMib(128, 20), effective(256, 128, true));
        assertEquals(percentOfMib(256, 20), effective(256, 256, true));
    }

    @Test
    public void normalDeviceUsesThirtyPercentHeapBudget() {
        assertEquals(percentOfMib(256, 30), effective(256, 256, false));
        assertEquals(percentOfMib(512, 30), effective(256, 512, false));
        assertEquals(percentOfMib(768, 30), effective(256, 768, false));
    }

    @Test
    public void heapBudgetNeverExceedsMaximum() {
        assertEquals(mib(256), effective(256, 1024, false));
        assertEquals(mib(256), effective(256, 2048, false));
    }

    @Test
    public void userTargetRemainsUpperBound() {
        assertEquals(mib(64), effective(64, 512, false));
        assertEquals(mib(128), effective(128, 1024, false));
    }

    @Test
    public void requestedCapacityIsPreservedWhenEffectiveCapacityIsReduced() {
        ExoBufferBudget.Budget normal = budget(256, 256, false);
        ExoBufferBudget.Budget lowRam = budget(256, 256, true);

        assertEquals(mib(256), normal.requestedTargetBytes());
        assertEquals(mib(256), lowRam.requestedTargetBytes());
        assertTrue(normal.effectiveTargetBytes() < normal.requestedTargetBytes());
        assertTrue(lowRam.effectiveTargetBytes() < lowRam.requestedTargetBytes());
    }

    @Test
    public void requestedCapacityRemainsIndependentFromHeapBudget() {
        ExoBufferBudget.Budget sixtyFour = budget(64, 128, true);
        ExoBufferBudget.Budget oneTwentyEight = budget(128, 256, false);

        assertEquals(mib(64), sixtyFour.requestedTargetBytes());
        assertEquals(mib(128), oneTwentyEight.requestedTargetBytes());
        assertTrue(sixtyFour.effectiveTargetBytes() <= sixtyFour.requestedTargetBytes());
        assertTrue(oneTwentyEight.effectiveTargetBytes() <= oneTwentyEight.requestedTargetBytes());
    }

    @Test
    public void budgetRetainsInputsForDiagnostics() {
        ExoBufferBudget.Budget budget = budget(256, 256, true);

        assertEquals(percentOfMib(256, 20), budget.heapBudgetBytes());
        assertEquals(mibLong(256), budget.heapLimitBytes());
        assertEquals(mib(48), budget.reservedHeadroomBytes());
        assertEquals(mib(208), budget.availableAfterReserveBytes());
        assertTrue(budget.lowRamDevice());
    }

    @Test
    public void deviceMemoryMetadataDoesNotChangeEffectiveBudget() {
        ExoBufferBudget.Budget base = budget(128, 512, false);
        ExoBufferBudget.Budget diagnostics = base.withDeviceMemory(256, 512, true);

        assertEquals(base.effectiveTargetBytes(), diagnostics.effectiveTargetBytes());
        assertEquals(256, diagnostics.memoryClassMb());
        assertEquals(512, diagnostics.largeMemoryClassMb());
        assertTrue(diagnostics.largeHeap());
    }

    @Test
    public void smallHeapDoesNotAllocateBeyondHeapLimit() {
        assertEquals(mib(16), effective(256, 16, true));
    }

    @Test
    public void reservedHeadroomCapsConstrainedHeap() {
        assertEquals(mib(16), effective(256, 80, false));
        assertEquals(mib(16), effective(256, 64, true));
    }

    private static int effective(int requestedMib, int heapMib, boolean lowRamDevice) {
        return ExoBufferBudget.calculateEffectiveTargetBytes(mib(requestedMib), mibLong(heapMib), lowRamDevice);
    }

    private static ExoBufferBudget.Budget budget(int requestedMib, int heapMib, boolean lowRamDevice) {
        return ExoBufferBudget.calculate(mib(requestedMib), mibLong(heapMib), lowRamDevice);
    }

    private static int percentOfMib(int heapMib, int percent) {
        return (int) (mibLong(heapMib) * percent / 100);
    }

    private static int mib(int value) {
        return value * 1024 * 1024;
    }

    private static long mibLong(int value) {
        return value * 1024L * 1024L;
    }
}
