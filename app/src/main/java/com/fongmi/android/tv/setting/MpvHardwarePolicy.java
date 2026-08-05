package com.fongmi.android.tv.setting;

import android.os.Build;

import java.util.Locale;
import java.util.regex.Pattern;

final class MpvHardwarePolicy {

    private static final Pattern LEGACY_HISILICON_HARDWARE = Pattern.compile("(^|[^a-z0-9])hi\\d{4}[a-z0-9]*($|[^a-z0-9])");

    private MpvHardwarePolicy() {
    }

    static boolean blocksZeroCopy() {
        String socManufacturer = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                socManufacturer = Build.SOC_MANUFACTURER;
            } catch (Throwable ignored) {
            }
        }
        return blocksZeroCopy(Build.MANUFACTURER, Build.HARDWARE, socManufacturer);
    }

    static boolean blocksZeroCopy(String manufacturer, String hardware, String socManufacturer) {
        String normalizedManufacturer = normalize(manufacturer);
        String normalizedHardware = normalize(hardware);
        String normalizedSoc = normalize(socManufacturer);
        boolean hiSiliconChip = normalizedHardware.contains("kirin")
                || normalizedHardware.contains("hisilicon")
                || LEGACY_HISILICON_HARDWARE.matcher(normalizedHardware).find()
                || normalizedSoc.contains("hisilicon");
        boolean chipUnknown = normalizedHardware.isEmpty() || normalizedHardware.equals("unknown");
        boolean huaweiFamily = normalizedManufacturer.contains("huawei") || normalizedManufacturer.contains("honor");
        // Older Android versions do not expose SOC_MANUFACTURER, and some Huawei builds
        // report only "unknown". Prefer the compatible copy path in that ambiguous case.
        return hiSiliconChip || (chipUnknown && huaweiFamily);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
