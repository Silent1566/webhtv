package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvHardwarePolicyTest {

    @Test
    public void blocksKnownHiSiliconAndKirinDevices() {
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "kirin9000", "hisilicon"));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HONOR", "kirin980", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "hi3660", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "unknown", ""));
        assertTrue(MpvHardwarePolicy.blocksZeroCopy("", "unknown", "HiSilicon Technologies"));
    }

    @Test
    public void preservesZeroCopyOnUnrelatedHardware() {
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("Google", "tensor", "Google"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("Samsung", "exynos", "Samsung"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("HUAWEI", "qcom", "Qualcomm"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy("HONOR", "mt6877", "MediaTek"));
        assertFalse(MpvHardwarePolicy.blocksZeroCopy(null, null, null));
    }
}