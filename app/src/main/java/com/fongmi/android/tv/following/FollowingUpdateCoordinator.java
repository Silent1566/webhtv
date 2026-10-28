package com.fongmi.android.tv.following;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.following.FollowingSourceProbe.FollowingSourceSnapshot;

import java.io.IOException;
import java.util.List;

public class FollowingUpdateCoordinator {

    private final FollowingMetadataClient metadataClient;
    private final FollowingSourceProbe sourceProbe;

    public FollowingUpdateCoordinator() {
        this(new FollowingMetadataClient(), new FollowingSourceProbe());
    }

    FollowingUpdateCoordinator(FollowingMetadataClient metadataClient, FollowingSourceProbe sourceProbe) {
        this.metadataClient = metadataClient;
        this.sourceProbe = sourceProbe;
    }

    public int checkDue(long now) throws IOException {
        if (!FollowingSettings.isEnabled()) return 0;
        boolean foreground = App.isForeground();
        List<Following> due = FollowingStore.database().getFollowingDao().findDue(now);
        int checked = 0;
        for (Following item : due) {
            if (check(item, false, foreground)) checked++;
        }
        return checked;
    }

    public boolean checkNow(String identityKey, boolean manual) {
        Following item = FollowingStore.find(identityKey);
        return item != null && check(item, manual);
    }

    boolean check(Following item, boolean manual) {
        return check(item, manual, App.isForeground());
    }

    boolean check(Following item, boolean manual, boolean foreground) {
        long now = System.currentTimeMillis();
        try {
            FollowingSource source = FollowingStore.preferredSource(item.identityKey);
            FollowingMetadataSnapshot metadata = null;
            boolean metadataChanged = false;
            boolean sourceAlreadyProbed = false;
            boolean probeFailed = false;
            Throwable metadataError = null;
            if (item.tmdbId > 0) {
                try {
                    metadata = metadataClient.fetch(item, manual);
                } catch (Throwable error) {
                    // TMDB is the preferred official provider, but an unavailable/rate-limited
                    // provider must not make the whole record fail when the bound source is
                    // still usable. Preserve the last official snapshot and probe the source.
                    metadataError = error;
                }
            }
            if (metadata != null) {
                metadataChanged = FollowingUpdatePolicy.applyMetadata(item, metadata, now);
            } else {
                ProbeOutcome initial = probeBestEffort(item, source, manual, false);
                FollowingSourceSnapshot sourceSnapshot = initial.snapshot;
                sourceAlreadyProbed = sourceSnapshot != null;
                if (sourceSnapshot == null || sourceSnapshot.source == null) {
                    Throwable failure = metadataError != null
                            ? metadataError : new IllegalStateException("来源检查失败");
                    FollowingUpdatePolicy.markFailure(item, failure, now);
                    FollowingStore.update(item);
                    FollowingScheduler.scheduleNext(App.get(), item);
                    return false;
                }
                source = sourceSnapshot.source;
            }
            FollowingStore.reconcile(item);
            FollowingUpdatePolicy.refreshDerived(item, now);
            if (shouldProbeAgain(sourceAlreadyProbed, FollowingSettings.shouldProbe(manual, metadataChanged, source))) {
                ProbeOutcome probed = probeBestEffort(item, source, manual, false);
                if (probed.snapshot != null) source = probed.snapshot.source;
                // 探针确实跑过却没拿到快照才算失败：仅凭 snapshot==null 会把「本次本不该探」误判成失败。
                probeFailed = probed.attempted && probed.snapshot == null;
            }
            item.lastCheckedAt = now;
            item.failureCount = 0;
            item.lastError = composeLastError(metadataError, probeFailed, source);
            item.nextCheckAt = FollowingSchedulePolicy.nextCheckAt(now, item.officialStatus, item.nextAirAt, foreground);
            item.updatedAt = now;
            FollowingStore.update(item);
            if (source != null) FollowingStore.updateSource(source);
            if (FollowingNotifier.notifyUpdate(item, source)) FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return true;
        } catch (Throwable error) {
            FollowingUpdatePolicy.markFailure(item, error, now);
            FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return false;
        }
    }

    /** 探针结果 + 是否真的调用过探针：只有 attempted && snapshot==null 才是「检查过但失败」。 */
    private static final class ProbeOutcome {
        static final ProbeOutcome NOT_RUN = new ProbeOutcome(false, null);

        final boolean attempted;
        final FollowingSourceSnapshot snapshot;

        ProbeOutcome(boolean attempted, FollowingSourceSnapshot snapshot) {
            this.attempted = attempted;
            this.snapshot = snapshot;
        }
    }

    /**
     * item.lastError 合成优先级：TMDB 降级文案（既有断言逐字锁定）> 源侧检查失败原因 > 空。
     * 源侧失败必须落库原因，否则 UI 只能显示「尚未检查」，用户看不到失败。
     */
    static String composeLastError(Throwable metadataError, boolean probeFailed, FollowingSource source) {
        if (metadataError != null) return "TMDB不可用，已回退原站";
        return probeFailed ? probeFailureReason(source) : "";
    }

    /** 本次 check 内探针至多跑一次：第一次已经跑出快照时不再重复探测同一来源。 */
    static boolean shouldProbeAgain(boolean sourceAlreadyProbed, boolean due) {
        return !sourceAlreadyProbed && due;
    }

    static String probeFailureReason(FollowingSource source) {
        if (source == null || source.lastError == null || source.lastError.isBlank()) return "来源检查失败";
        return source.lastError;
    }

    private ProbeOutcome probeBestEffort(Following item, FollowingSource source, boolean manual, boolean required) {
        if (source == null) source = fallbackSource(item);
        if (source == null || !FollowingSettings.shouldProbe(manual, false, source)) return ProbeOutcome.NOT_RUN;
        try {
            FollowingSourceSnapshot result = sourceProbe.probe(item, source);
            FollowingStore.updateSource(source);
            return new ProbeOutcome(true, result);
        } catch (Throwable error) {
            source.lastError = error.getMessage() == null ? "来源检查失败" : error.getMessage();
            source.lastProbeAt = System.currentTimeMillis();
            FollowingStore.updateSource(source);
            if (required) throw error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error);
            return new ProbeOutcome(true, null);
        }
    }

    private static FollowingSource fallbackSource(Following item) {
        if (item == null || item.siteKey == null || item.siteKey.isBlank() || item.vodId == null || item.vodId.isBlank()) return null;
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.cid = item.cid;
        source.siteKey = item.siteKey;
        source.vodId = item.vodId;
        source.vodName = item.vodName;
        source.vodPic = item.vodPic;
        source.preferred = true;
        return source;
    }
}
