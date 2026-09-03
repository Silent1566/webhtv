package com.fongmi.android.tv.player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.IntroSkipService.IntroSkipPlan;
import com.fongmi.android.tv.service.IntroSkipService.Segment;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.util.HashSet;
import java.util.Set;

public class IntroSkipPlayback {

    public interface SkipConfirmListener {
        /**
         * @return 是否已经把选择权交给用户（弹出了确认框）。返回 false 表示这次没能询问，
         *         调用方不得把该段标记为已处理，否则这一段将永久不再触发。
         */
        boolean onSkipConfirm(Segment segment, Runnable action);
    }

    /**
     * 跳过已执行的回调，用于给用户一个「刚才发生了什么」的提示。
     *
     * @param seeked true 表示跳到了段末，false 表示按「本集看完」切到了下一集
     */
    public interface SkipNoticeListener {
        void onSkipped(Segment segment, boolean seeked);
    }

    /**
     * 「本集看完」的处理动作。
     *
     * @return 是否真的切走了。末集、电影、倒序首集切不动，此时不能提示「进入下一集」。
     */
    public interface EndingAction {
        boolean run();
    }

    private static final long TOLERANCE_MS = 1500;
    private static final long MIN_SKIP_DELTA_MS = 1500;
    /** 时长归一粒度：HLS 的时长会随 manifest 精化抖动，别为几百毫秒反复重解析。 */
    private static final long DURATION_BUCKET_MS = 2000;

    private final IntroSkipService service = new IntroSkipService();
    private final Set<String> skipped = new HashSet<>();
    private IntroSkipPlan plan = IntroSkipPlan.empty();
    private String loadedKey = "";
    private String loadingKey = "";
    private int generation;
    private boolean loading;
    private long resumeMs;
    private boolean suppressOpening;
    private boolean suppressEnding;
    private SkipConfirmListener skipConfirmListener;
    private Runnable skipConfirmDismisser;
    private SkipNoticeListener skipNoticeListener;

    public void reset() {
        generation++;
        loading = false;
        loadedKey = "";
        loadingKey = "";
        plan = IntroSkipPlan.empty();
        skipped.clear();
        resumeMs = 0;
        suppressOpening = false;
        suppressEnding = false;
        if (skipConfirmDismisser != null) skipConfirmDismisser.run();
    }

    /**
     * 用户手动清空了片头/片尾，控制栏那一格暂不再显示探测值。
     *
     * <p>长按清空是明确的「这里不要有值」，紧接着又渲染出探测时间会让人以为清空失败。
     * 抑制只持续到本集结束（{@link #reset()} 解除）。
     */
    public void suppressDetected(boolean opening) {
        if (opening) suppressOpening = true;
        else suppressEnding = true;
    }

    /**
     * 记录本次续播基准位置。起点在该位置及之前的片头段视为"用户已越过或正处于"，
     * 不再触发自动跳过/确认提示，避免 seekTo 尚未落地时误弹确认框。
     */
    public void setResumePosition(long ms) {
        resumeMs = Math.max(0, ms);
    }

    public void setSkipConfirmListener(SkipConfirmListener listener) {
        this.skipConfirmListener = listener;
    }

    /**
     * 换集/换源时收掉还挂着的确认框。
     *
     * <p>不收的话它会一直停在屏幕上：新一集因为「已有框在显示」而永远弹不出提示，
     * 而用户此时点确定，动作是按上一集的段落算的。
     */
    public void setSkipConfirmDismisser(Runnable dismisser) {
        this.skipConfirmDismisser = dismisser;
    }

    public void setSkipNoticeListener(SkipNoticeListener listener) {
        this.skipNoticeListener = listener;
    }

    /**
     * 探测到的片头落点（正片从这里继续），无数据返回 -1。
     *
     * <p>仅用于控制栏展示。一集可能有多段片头（回顾 + OP，中间夹正片），这里给最先触发的那
     * 一段，与用户接下来真正会看到的跳过动作一致；不把多段合成一个数，那会谎报中间的正片。
     */
    public long getDetectedOpeningMs() {
        if (suppressOpening) return -1;
        for (Segment segment : plan.getOpenings()) {
            // 续播已越过的段不会再触发，显示它等于报一个不会发生的时间
            if (segment.getEndMs() > 0 && isKindEnabled(segment) && !passedOnResume(segment, 0)) return segment.getEndMs();
        }
        return -1;
    }

    /**
     * 探测到的片尾时长（距本集结尾多久开始），无数据返回 -1。
     *
     * <p>换算成「距结尾的剩余时长」，与手动片尾按钮同一语义，两者显示出来才可比。
     */
    public long getDetectedEndingMs(long durationMs) {
        if (suppressEnding || durationMs <= 0) return -1;
        for (Segment segment : plan.getEndings()) {
            long start = segment.getStartMs();
            if (start > 0 && start < durationMs && isKindEnabled(segment) && !passedOnResume(segment, durationMs)) return durationMs - start;
        }
        return -1;
    }

    /**
     * 去重键带上归一后的时长：折算依赖时长，而时长在 onPrepare 时还是 0、到 STATE_READY 才有值。
     * 只按身份去重会让第二次请求被跳过，计划永远停在「按时长 0 折算」的状态。重解析走的是
     * 服务层的原始段缓存，不会再发网络请求。
     */
    private String signature(IntroSkipService.Query query) {
        long duration = query.getDurationMs();
        return query.cacheKey() + "@" + (duration <= 0 ? 0 : duration / DURATION_BUCKET_MS);
    }

    /**
     * 后台预热某一集的数据，只灌缓存，不碰当前计划、不回调、不参与去重状态。
     * 重复调用是安全的：命中缓存就直接返回。
     */
    public void preload(IntroSkipService.Query query) {
        if (query == null || !query.hasLookupKey()) return;
        Task.execute(() -> service.preload(query));
    }

    public void request(IntroSkipService.Query query, Runnable onLoaded) {
        if (query == null || !query.hasLookupKey()) return;
        String key = signature(query);
        if (key.equals(loadedKey) || (loading && key.equals(loadingKey))) return;
        int current = ++generation;
        loading = true;
        loadingKey = key;
        Task.execute(() -> {
            IntroSkipPlan loaded = service.load(query);
            App.post(() -> {
                if (current != generation || !key.equals(loadingKey)) return;
                loading = false;
                loadedKey = key;
                // 无条件采纳，空计划也要。解析层给出空，是「这些段对不上本集时间轴」的明确判定；
                // 留着上一次按 duration=0 算出的旧计划，会拿错误的落点去跳（把正片中段当片尾）。
                // skipped 不清：id 不含时间边界，重解析后仍能命中，已跳过的段不会重来。
                plan = loaded;
                SpiderDebug.log("intro-skip", "plan ready key=%s resumeMs=%d segments=%s", key, resumeMs, describe(plan));
                if (onLoaded != null) onLoaded.run();
            });
        });
    }

    /**
     * 按时间顺序逐段判定。四类片段（回顾、片头、片尾、预告）走同一条通路，差别只在
     * 「用户是否开了这一类」和「有没有可 seek 的落点」，不再按片头/片尾分两套逻辑。
     */
    public boolean apply(PlayerManager player, EndingAction onEnding) {
        if (player == null || plan == null || plan.isEmpty()) return false;
        int mode = Setting.getIntroSkipMode();
        if (mode == Setting.INTRO_SKIP_OFF) return false;
        long position = player.getPosition();
        long duration = player.getDuration();
        if (position < 0) return false;
        long resume = resumeMs;
        if (resume > 0) {
            // 续播 seek 还没落地，当前位置是上一段的残留值，这一轮什么都别判
            if (position + TOLERANCE_MS < resume) return false;
            // 落点已到：之后进度由用户自己掌握，再按续播位置抑制就会锁死整场
            resumeMs = 0;
        }

        for (Segment segment : plan.getAll()) {
            String id = id(segment);
            if (skipped.contains(id)) continue;
            // 以下都是「此刻不适用」，只跳过本轮、不写 skipped。时长会从 0 变成真值、
            // 用户会中途改设置、会往回拖进度，任何一条提前写死都会让这一段整集失效。
            if (!isKindEnabled(segment)) continue;
            long start = segment.getStartMs();
            if (duration > 0 && start >= duration) continue;
            if (position + TOLERANCE_MS < start) continue;
            // 续播落点就在这个尾部段里：用户自己挑的位置，不能立刻判「本集看完」跳走。
            // 这是一次明确决定（本集不再自动处理该段），所以写 skipped。
            if (resume > 0 && segment.isEnding() && start <= resume) {
                skipped.add(id);
                continue;
            }

            long target = seekTarget(segment, position, duration);
            boolean canEnd = target <= 0 && endsWithFile(segment, duration);
            if (target <= 0 && !canEnd) continue;

            SpiderDebug.log("intro-skip", "hit kind=%s provider=%s from=%d start=%d end=%d openEnded=%s target=%d canEnd=%s duration=%d mode=%d", segment.getKind(), segment.getProvider(), position, start, segment.getEndMs(), segment.isOpenEnded(), target, canEnd, duration, mode);
            if (mode == Setting.INTRO_SKIP_AUTO) {
                skipped.add(id);
                runSkip(player, segment, target, canEnd, onEnding);
                return true;
            }
            if (mode == Setting.INTRO_SKIP_CONFIRM && skipConfirmListener != null) {
                int current = generation;
                // 只有确认框真的弹出来了才算已处理；被别的框挡住时留着下个 tick 再问
                if (!skipConfirmListener.onSkipConfirm(segment, () -> confirmSkip(current, player, segment, onEnding))) continue;
                skipped.add(id);
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 用户点确认时才决定怎么做。确认框可能挂很久：期间位置已推进，沿用检测时算好的落点会
     * 往回 seek；更要紧的是可能已经切集，此时按上一集的段落动作会把用户带到别处。
     */
    private void confirmSkip(int expectedGeneration, PlayerManager player, Segment segment, EndingAction onEnding) {
        if (expectedGeneration != generation) return; // 已切集/换源，这个确认过期了
        if (player.isReleased()) return;
        long position = player.getPosition();
        long duration = player.getDuration();
        long target = seekTarget(segment, position, duration);
        boolean canEnd = target <= 0 && endsWithFile(segment, duration);
        if (target <= 0 && !canEnd) return; // 已经放过去了，无事可做
        runSkip(player, segment, target, canEnd, onEnding);
    }

    private void runSkip(PlayerManager player, Segment segment, long target, boolean canEnd, EndingAction onEnding) {
        boolean seeked = target > 0;
        if (seeked) player.seekTo(target);
        boolean advanced = !seeked && canEnd && onEnding != null && onEnding.run();
        // 切不动（末集/电影）时既没跳也没换集，此时提示「已跳过」是假话
        if (skipNoticeListener != null && (seeked || advanced)) skipNoticeListener.onSkipped(segment, seeked);
    }

    /**
     * 本段是否一路延伸到文件结束——只有这种情况「跳过」才等于「本集看完」。
     *
     * <p>片尾之后还剩内容（彩蛋、预告）时不能按看完处理，那会把剩下的正片一起扔掉。
     */
    private boolean endsWithFile(Segment segment, long duration) {
        if (!segment.isEnding()) return false;
        if (segment.isOpenEnded()) return true;
        long end = segment.getEndMs();
        if (end <= 0) return true;
        return duration > 0 && end >= duration - TOLERANCE_MS;
    }

    /**
     * 续播落点是否已经越过本段——仅用于控制栏显示，判定不再走它。
     *
     * <p>片头看结束点、片尾看起点：接口对没给 start_ms 的片头会填 0，按起点判断会让
     * 「起点 0 ≤ 任何续播位置」恒成立，只要有观看历史开头那段就再也不显示。
     */
    private boolean passedOnResume(Segment segment, long duration) {
        if (resumeMs <= 0) return false;
        if (segment.isEnding()) return segment.getStartMs() <= resumeMs;
        long end = segment.getEndMs();
        if (end <= 0 && duration > 0) end = duration;
        return end > 0 && resumeMs >= end - MIN_SKIP_DELTA_MS;
    }

    private boolean isKindEnabled(Segment segment) {
        switch (segment.getKind()) {
            case RECAP: return Setting.isIntroSkipKindEnabled(Setting.INTRO_SKIP_KIND_RECAP);
            case INTRO: return Setting.isIntroSkipKindEnabled(Setting.INTRO_SKIP_KIND_INTRO);
            case OUTRO: return Setting.isIntroSkipKindEnabled(Setting.INTRO_SKIP_KIND_OUTRO);
            case PREVIEW: return Setting.isIntroSkipKindEnabled(Setting.INTRO_SKIP_KIND_PREVIEW);
            default: return false;
        }
    }

    /**
     * 本段的 seek 落点，没有可跳之处返回 -1。
     *
     * <p>只回答「往前跳到哪」。是否该按「本集看完」处理由 {@link #endsWithFile} 单独判断——
     * 早先两件事挤在一个返回值里，于是「片头已经放过去了」和「片尾一直到文件结束」都得到 -1，
     * 确认片头时被当成看完直接切到了下一集。
     */
    private long seekTarget(Segment segment, long position, long duration) {
        if (segment.isOpenEnded()) return -1;
        long end = segment.getEndMs();
        if (end <= 0) return -1;
        if (duration > 0 && end > duration) end = duration;
        if (duration > 0 && end >= duration - TOLERANCE_MS) return -1;
        return end - position > MIN_SKIP_DELTA_MS ? end : -1;
    }

    /** 段落边界摘要。只打数量不打边界，出问题时无法分辨「没数据」和「有数据但被护栏拦了」。 */
    private String describe(IntroSkipPlan value) {
        StringBuilder text = new StringBuilder();
        for (Segment segment : value.getAll()) {
            if (text.length() > 0) text.append(',');
            text.append(segment.getKind()).append('[').append(segment.getStartMs()).append('-').append(segment.getEndMs());
            if (segment.isOpenEnded()) text.append("|open");
            text.append(']');
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    /**
     * 段落身份。刻意不含时间边界：尾部段会随本集时长折算而平移，把边界写进 id 会让同一段
     * 在时长抖动后换出新 id，逃出 skipped 再触发一次，表现为重复跳过 / 重复换集。
     * 同类同源在一集里只会有一段（去重已保证），所以类型 + 数据源足够唯一。
     */
    private String id(Segment segment) {
        return segment.getKind() + "|" + segment.getProvider();
    }
}
