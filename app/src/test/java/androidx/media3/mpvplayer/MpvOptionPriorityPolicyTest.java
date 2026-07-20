package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvOptionPriorityPolicyTest {

    @Test
    public void performancePriorityOverlaysOnlyExplicitlyManagedOptions() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("cache-pause-wait", "2.000");
        candidates.put("video-sync", "audio");
        candidates.put("sub-font", "User Font");
        candidates.put("glsl-shaders", "/storage/user-shader.glsl");

        Map<String, String> overlay = MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates);

        assertEquals(2, overlay.size());
        assertEquals("2.000", overlay.get("cache-pause-wait"));
        assertEquals("audio", overlay.get("video-sync"));
        assertFalse(overlay.containsKey("sub-font"));
        assertFalse(overlay.containsKey("glsl-shaders"));
    }

    @Test
    public void configPriorityLeavesAllSameNameOptionsToMpvConf() {
        Map<String, String> candidates = Map.of(
                "hwdec", "mediacodec-copy",
                "demuxer-max-bytes", "67108864",
                "framedrop", "vo");

        assertTrue(MpvOptionPriorityPolicy.selectPerformanceOverlay(false, candidates).isEmpty());
        assertEquals("mpv.conf", MpvOptionPriorityPolicy.priorityName(false));
    }

    @Test
    public void currentPerformanceCatalogIsExplicitlyManaged() {
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vo"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("hwdec"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("cache-pause-initial"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("cache-pause-wait"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("demuxer-max-bytes"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("framedrop"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("video-sync"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("interpolation"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("hls-bitrate"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-fast"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-threads"));
        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("vd-lavc-skiploopfilter"));
        assertFalse(MpvOptionPriorityPolicy.isPerformanceManaged("sub-font"));
        assertFalse(MpvOptionPriorityPolicy.isPerformanceManaged("glsl-shaders"));
        assertEquals("performance", MpvOptionPriorityPolicy.priorityName(true));
    }

    @Test
    public void nullCandidateValuesAreNeverApplied() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("hwdec", null);
        candidates.put("cache", "yes");

        Map<String, String> overlay = MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates);

        assertEquals(Map.of("cache", "yes"), overlay);
    }
}
