package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 音频/语音通道的归因。此前 {@code AudioIntervalFact} 与 {@code SpeechIntervalFact}
 * 在生产侧永远是 {@code unavailable()} —— 没有任何构造点填真值，也没有任何分类器读它们，
 * 音频通道从未参与过一次归因。
 */
public class AudioChannelClassifierTest {

    private static AdIntervalEvidence evidence(AudioIntervalFact audio, SpeechIntervalFact speech) {
        return new AdIntervalEvidence(
                0, 25_100, StartOrigin.FALLBACK_WINDOW,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                List.of(new SegmentFact(0, "v.example.com", "/seg/0.ts", 8.0, false)),
                List.of(new SegmentFact(1, "v.example.com", "/seg/1.ts", 8.0, false)),
                false, false, List.of(), false, List.of(), audio, speech);
    }

    /** 指纹命中：给出诊断，说明规则已存在。 */
    @Test
    public void reportsFingerprintMatchAsDiagnosis() {
        AdAttribution result = AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(true, List.of("probe-42"), true),
                SpeechIntervalFact.unavailable()));

        assertNotNull(result);
        assertEquals(AdCategory.AUDIO_FINGERPRINT, result.category());
        assertEquals(RemediationKind.NONE, result.remediation());
        assertFalse("诊断不产出规则", result.actionable());
        assertTrue("证据要点出命中的规则 id",
                String.join("\n", result.evidence()).contains("probe-42"));
    }

    /** 语音命中：同为诊断，但风险标中 —— 关键词可能出现在正片台词里。 */
    @Test
    public void reportsSpeechMatchWithMediumRisk() {
        AdAttribution result = AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(true, List.of(), true),
                new SpeechIntervalFact(true, 3)));

        assertNotNull(result);
        assertEquals(AdCategory.SPEECH_KEYWORD, result.category());
        assertEquals(RiskLevel.MEDIUM, result.risk());
        assertTrue(String.join("\n", result.evidence()).contains("3 次"));
    }

    /** 指纹命中优先于语音，且要把语音命中一并写进证据。 */
    @Test
    public void fingerprintTakesPrecedenceButMentionsSpeech() {
        AdAttribution result = AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(true, List.of("probe-7"), true),
                new SpeechIntervalFact(true, 2)));

        assertEquals(AdCategory.AUDIO_FINGERPRINT, result.category());
        assertTrue("语音命中也要提及",
                String.join("\n", result.evidence()).contains("语音通道另有 2 次命中"));
    }

    /** 功能开着、可采集、却没命中 —— 这是「值得录指纹」的信号，要说出来。 */
    @Test
    public void suggestsCaptureWhenEnabledButNoMatch() {
        AdAttribution result = AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(true, List.of(), true),
                SpeechIntervalFact.unavailable()));

        assertNotNull(result);
        assertEquals(AdCategory.AUDIO_FINGERPRINT, result.category());
        assertTrue("应提示这段尚未入库",
                String.join("\n", result.evidence()).contains("尚未入库"));
        assertFalse("仍然只是建议，不产出规则", result.actionable());
    }

    /**
     * 功能关着时「没命中」不含信息量，必须弃权。
     *
     * <p>报告一条「音频没认出来」在功能关闭时是误导 —— 它根本没在听。
     */
    @Test
    public void abstainsWhenAudioDisabled() {
        assertNull(AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(false, List.of(), true),
                SpeechIntervalFact.unavailable())));
    }

    /** 本次播放采集不了（非 Exo / 直播 / 时长未知）时同样弃权。 */
    @Test
    public void abstainsWhenCaptureNotReady() {
        assertNull(AudioChannelClassifier.classify(evidence(
                new AudioIntervalFact(true, List.of(), false),
                SpeechIntervalFact.unavailable())));
    }

    /** 两个通道都不可用（生产侧改动前的状态）必须弃权，不能凭空产出结论。 */
    @Test
    public void abstainsWhenBothChannelsUnavailable() {
        assertNull(AudioChannelClassifier.classify(evidence(
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable())));
    }

    @Test
    public void abstainsOnNullEvidence() {
        assertNull(AudioChannelClassifier.classify(null));
    }
}
