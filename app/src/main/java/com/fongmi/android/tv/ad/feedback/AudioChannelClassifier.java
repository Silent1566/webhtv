package com.fongmi.android.tv.ad.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 读取音频指纹与语音关键词通道在区间内的既有命中，作为归因证据。
 *
 * <p>这两个通道自己就是完整的检测链路（{@code ad.audio} 包），会实时命中并跳过广告。
 * 本类不做检测，只回答一个问题：**用户框选的这段，音频侧是否已经认出来了**。
 *
 * <p>命中意味着规则已经存在并生效，所以结论是诊断而非新规则 —— 用户看到的广告要么
 * 是跳过前的残留，要么来自别处。这与 {@link ExistingRuleClassifier} 报告
 * {@code ALREADY_HANDLED} 的理由相同。
 *
 * <p>未命中时也可能有话要说：功能开着、采集就绪、却没认出来，说明这段广告不在指纹库
 * 里，用户的反馈正好是「值得录一条指纹」的信号。当前只作为证据陈述，不自动产出
 * {@code AUDIO_FINGERPRINT_RULE} —— 录指纹要拿到原始 PCM，那是采集侧的能力，
 * 不是归因阶段能凭 manifest 完成的。
 */
public final class AudioChannelClassifier {

    public static final String CHANNEL_ID = "audio";

    /** 指纹命中的置信度：规则库认出了它，比任何结构推断都硬。 */
    private static final float CONFIDENCE_FINGERPRINT = 0.9f;
    /** 语音命中稍低：关键词可能出现在正片台词里。 */
    private static final float CONFIDENCE_SPEECH = 0.7f;
    /** 「该录指纹」只是建议，置信度压在仲裁阈值附近。 */
    private static final float CONFIDENCE_CAPTURE_HINT = 0.35f;

    private AudioChannelClassifier() {
    }

    /**
     * @return 音频侧的归因结论，无话可说时返回 null 表示弃权
     */
    public static AdAttribution classify(AdIntervalEvidence evidence) {
        if (evidence == null) return null;
        AudioIntervalFact audio = evidence.audio();
        SpeechIntervalFact speech = evidence.speech();

        if (audio.hasMatch()) return fingerprintMatched(evidence, audio, speech);
        if (speech.hasMatch()) return speechMatched(evidence, speech);
        return captureHint(evidence, audio);
    }

    /** 指纹命中：规则已存在且生效，属诊断。 */
    private static AdAttribution fingerprintMatched(AdIntervalEvidence evidence,
                                                    AudioIntervalFact audio,
                                                    SpeechIntervalFact speech) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.US, "音频指纹已命中该区间，规则：%s",
                String.join("、", audio.matchedRuleIds())));
        lines.add("这段广告已被指纹规则识别，用户看到的可能是跳过前的残留");
        if (speech.hasMatch()) {
            lines.add(String.format(Locale.US, "语音通道另有 %d 次命中", speech.hitCount()));
        }
        return new AdAttribution(CHANNEL_ID, AdCategory.AUDIO_FINGERPRINT,
                CONFIDENCE_FINGERPRINT, RiskLevel.LOW, lines, RemediationKind.NONE);
    }

    /** 语音命中：同样属诊断，但关键词可能误伤正片台词，风险标中。 */
    private static AdAttribution speechMatched(AdIntervalEvidence evidence,
                                               SpeechIntervalFact speech) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.US, "语音关键词在该区间命中 %d 次", speech.hitCount()));
        lines.add("关键词也可能出现在正片台词里，命中不等于该段一定是广告");
        return new AdAttribution(CHANNEL_ID, AdCategory.SPEECH_KEYWORD,
                CONFIDENCE_SPEECH, RiskLevel.MEDIUM, lines, RemediationKind.NONE);
    }

    /**
     * 无命中：只有在「功能开着且采集就绪」时才有信息量。
     *
     * <p>功能关着、或本次播放采集不了（非 Exo 内核、直播、时长未知），那么「没命中」
     * 不能说明任何事，弃权比报告一条无用证据好。
     */
    private static AdAttribution captureHint(AdIntervalEvidence evidence, AudioIntervalFact audio) {
        if (!audio.enabled() || !audio.captureReady()) return null;
        List<String> lines = new ArrayList<>();
        lines.add("音频指纹已开启且本次可采集，但该区间未命中任何指纹");
        lines.add("说明这段广告尚未入库，录一条指纹后同款广告可自动跳过");
        return new AdAttribution(CHANNEL_ID, AdCategory.AUDIO_FINGERPRINT,
                CONFIDENCE_CAPTURE_HINT, RiskLevel.LOW, lines, RemediationKind.NONE);
    }
}
