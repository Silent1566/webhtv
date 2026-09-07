package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvAudioDecoderPolicyTest {

    @Test
    public void prioritizesOnlyBuiltAudioMediaCodecDecoders() {
        String decoderList = MpvAudioDecoderPolicy.hardwareFirstDecoderList();

        assertEquals(
                "aac_mediacodec,mp3_mediacodec,amrnb_mediacodec,amrwb_mediacodec",
                decoderList);
        assertFalse(decoderList.endsWith("-"));
    }

    @Test
    public void performancePriorityManagesAudioDecoderOrder() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("ad", MpvAudioDecoderPolicy.hardwareFirstDecoderList());

        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("ad"));
        assertEquals(candidates,
                MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates));
        assertTrue(MpvOptionPriorityPolicy.selectPerformanceOverlay(
                false, candidates).isEmpty());
    }
}
