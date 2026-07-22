package com.fongmi.android.tv.player.diagnostic;

public final class PanDiagnosticVerdict {

    private static final double SUFFICIENT_RATIO = 1.25;
    private static final double MATERIAL_LOSS_RATIO = 0.75;

    private PanDiagnosticVerdict() {
    }

    public static Result resolve(Input input) {
        if (input == null || input.requiredBitsPerSecond <= 0) return result(Cause.INCONCLUSIVE, Confidence.LOW, "缺少资源持续码率");
        long required = input.requiredBitsPerSecond;
        long safe = (long) (required * SUFFICIENT_RATIO);
        if (known(input.baselineBitsPerSecond) && input.baselineBitsPerSecond < required) {
            return result(Cause.DEVICE_NETWORK, cap(Confidence.HIGH, input.evidenceConfidence), "公共网络基准低于资源最低需求");
        }
        boolean appComparable = !known(input.proxyBitsPerSecond) || !known(input.dataSourceBitsPerSecond)
                || input.dataSourceBitsPerSecond <= input.proxyBitsPerSecond * 1.35d;
        if (appComparable && input.rebufferCount > 0 && input.dataSourceBitsPerSecond >= safe) {
            return result(Cause.PLAYER_BUFFERING, cap(Confidence.MEDIUM, input.evidenceConfidence), "供数充足但播放器仍发生重缓冲");
        }
        if (appComparable && input.droppedFrames >= 60 && input.dataSourceBitsPerSecond >= safe) {
            return result(Cause.DECODE_RENDER, cap(Confidence.MEDIUM, input.evidenceConfidence), "供数充足但解码或渲染掉帧较多");
        }
        if (appComparable && input.dataSourceBitsPerSecond >= safe && input.rebufferCount == 0 && input.droppedFrames < 60) {
            return result(Cause.SUFFICIENT, cap(Confidence.HIGH, input.evidenceConfidence), "完整链路满足资源安全吞吐");
        }
        if (known(input.directConcurrentBitsPerSecond) && known(input.proxyBitsPerSecond)
                && input.proxyBitsPerSecond < input.directConcurrentBitsPerSecond * MATERIAL_LOSS_RATIO) {
            if (input.directConcurrentBitsPerSecond >= safe) {
                return result(Cause.EXTERNAL_PROXY, cap(Confidence.HIGH, input.evidenceConfidence), "同并发直链充足，但Go代理聚合存在显著损耗");
            }
            return result(Cause.MULTIPLE_BOTTLENECKS, cap(Confidence.MEDIUM, input.evidenceConfidence), "同源直链并发本身不足，Go相对直链又产生显著额外损耗");
        }
        if (known(input.proxyBitsPerSecond) && known(input.dataSourceBitsPerSecond)
                && input.dataSourceBitsPerSecond < input.proxyBitsPerSecond * MATERIAL_LOSS_RATIO) {
            if (input.proxyBitsPerSecond >= safe) {
                return result(Cause.APP_DATA_SOURCE, cap(Confidence.HIGH, input.evidenceConfidence), "Go代理供数充足，但App DataSource存在显著损耗");
            }
            return result(Cause.MULTIPLE_BOTTLENECKS, cap(Confidence.MEDIUM, input.evidenceConfidence), "Go代理供数本身不足，App DataSource又产生显著额外损耗");
        }
        if (!appComparable) {
            return result(Cause.INCONCLUSIVE, Confidence.LOW, "App样本显著高于同线程Go样本，存在时间波动或突发缓冲；App层本轮不定责");
        }
        if (known(input.baselineBitsPerSecond) && input.baselineBitsPerSecond >= safe
                && known(input.directConcurrentBitsPerSecond) && input.directConcurrentBitsPerSecond < required
                && (!known(input.proxyBitsPerSecond) || input.proxyBitsPerSecond < required)) {
            return result(Cause.UPSTREAM_PROVIDER, cap(Confidence.HIGH, input.evidenceConfidence), "公共网络充足，但同源直链并发及代理聚合仍低于资源需求");
        }
        if (known(input.directConcurrentBitsPerSecond) && input.directConcurrentBitsPerSecond < required
                && known(input.proxyBitsPerSecond) && input.proxyBitsPerSecond < required
                && known(input.dataSourceBitsPerSecond) && input.dataSourceBitsPerSecond < required) {
            return result(Cause.UPSTREAM_CAPACITY, cap(Confidence.MEDIUM, input.evidenceConfidence), "瓶颈位于Go之前；无公共网络基准时不能继续区分本机网络与上游源站/节点");
        }
        if (known(input.upstreamBitsPerSecond) && known(input.directConcurrentBitsPerSecond)
                && input.upstreamBitsPerSecond < required && input.directConcurrentBitsPerSecond >= required) {
            return result(Cause.SINGLE_CONNECTION_LIMIT, cap(Confidence.MEDIUM, input.evidenceConfidence), "上游单连接受限，但直链并发可以明显改善吞吐");
        }
        return result(Cause.INCONCLUSIVE, Confidence.LOW, "证据层级不完整，不能可靠归因");
    }

    private static boolean known(long value) {
        return value > 0;
    }

    private static Result result(Cause cause, Confidence confidence, String reason) {
        return new Result(cause, confidence, reason);
    }

    private static Confidence cap(Confidence desired, Confidence evidence) {
        Confidence actual = evidence == null ? Confidence.LOW : evidence;
        return desired.ordinal() >= actual.ordinal() ? desired : actual;
    }

    public record Input(long requiredBitsPerSecond, long baselineBitsPerSecond, long upstreamBitsPerSecond,
                        long directConcurrentBitsPerSecond, long proxyBitsPerSecond, long dataSourceBitsPerSecond,
                        int rebufferCount, int droppedFrames, Confidence evidenceConfidence) {
    }

    public record Result(Cause cause, Confidence confidence, String reason) {
    }

    public enum Cause {
        DEVICE_NETWORK,
        UPSTREAM_PROVIDER,
        EXTERNAL_PROXY,
        APP_DATA_SOURCE,
        UPSTREAM_CAPACITY,
        MULTIPLE_BOTTLENECKS,
        SINGLE_CONNECTION_LIMIT,
        PLAYER_BUFFERING,
        DECODE_RENDER,
        SUFFICIENT,
        INCONCLUSIVE
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
