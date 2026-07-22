package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackRouteCapabilities;

public final class MpvNetworkRecoveryPolicy {

    private MpvNetworkRecoveryPolicy() {
    }

    public static Decision resolve(String playableUri) {
        PlaybackRoute.Resolution resolution = PlaybackRoute.resolve(playableUri);
        PlaybackRoute route = resolution.route();
        return switch (route) {
            case DIRECT_REMOTE_HTTP -> decision(resolution, "mpv-native-curl", true, true, false);
            case APP_LOCAL_SERVICE -> decision(resolution, "app-owned-local-service", true, false, false);
            case EXTERNAL_LOOPBACK_PROXY -> decision(resolution, "external-policy-unknown", false, false, false);
            default -> decision(resolution, "not-applicable", false, false, false);
        };
    }

    private static Decision decision(PlaybackRoute.Resolution resolution, String recoveryBoundary, boolean upstreamRecoveryPolicyKnown, boolean nativeRemoteRecovery, boolean appReconnectOverlay) {
        PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(resolution);
        return new Decision(resolution.route(), resolution.owner().label(), resolution.evidence().label(), resolution.confidence().label(), capabilities.observedLeg().label(), capabilities.upstreamVisibility().label(), capabilities.controlScope().label(), recoveryBoundary, upstreamRecoveryPolicyKnown, nativeRemoteRecovery, appReconnectOverlay);
    }

    public record Decision(PlaybackRoute route, String routeOwner, String routeEvidence, String routeConfidence, String observedLeg, String upstreamVisibility, String controlScope, String recoveryBoundary, boolean upstreamRecoveryPolicyKnown, boolean nativeRemoteRecovery, boolean appReconnectOverlay) {
    }
}
