package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdAudioRuntimeController implements AutoCloseable {

    public interface PlaybackPort extends AdSkipCoordinator.PlaybackPort {
        boolean isEligible(long sessionId, long generation);

        default AdAudioSignalProvider.SessionContext sessionContext(
                long sessionId, long generation) {
            return new AdAudioSignalProvider.SessionContext(
                    sessionId, generation, "session-" + sessionId, "", Map.of());
        }
    }

    @FunctionalInterface
    public interface ProbeProviderFactory {
        AdAudioSignalProvider create(ProbeRuleSidecar sidecar);
    }

    @FunctionalInterface
    public interface SpeechProviderFactory {
        AdAudioSignalProvider create();
    }

    private static final int RUNTIME_CANDIDATE_CAPACITY = 1_024;

    private final PlaybackMediaSignalHub hub;
    private final PlaybackMediaClock clock;
    private final AdAudioRuleSource ruleSource;
    private final PlaybackPort playback;
    private final Executor worker;
    private final Runnable workerShutdown;
    private final Executor speechWorker;
    private final Runnable speechWorkerShutdown;
    private final ProbeProviderFactory probeProviderFactory;
    private final SpeechProviderFactory speechProviderFactory;
    private final SpeechRecognitionFactory recognitionFactory;
    private final AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();

    private AdAudioRuleSnapshot snapshot = new AdAudioRuleSnapshot(
            "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "");
    private AdSkipCoordinator.UiPort ui;
    private AdSkipCoordinator coordinator;
    private PcmAdAudioSignalProvider pcmProvider;
    private AdAudioSignalProvider probeProvider;
    private AdAudioSignalProvider speechProvider;
    private AdAudioDetectionMultiplexer multiplexer;
    private AdSkipPolicyController policy;
    private AdSkipPolicyController.Mode skipMode = AdSkipPolicyController.Mode.PROMPT;
    private SpeechAdConfig speechConfig = SpeechAdConfig.defaults();
    private boolean enabled;
    private String lastRefreshLog = "";
    private long activeSessionId = Long.MIN_VALUE;
    private boolean closed;

    public AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                    AdAudioRuleSource ruleSource, PlaybackPort playback) {
        this(hub, clock, ruleSource, playback, createWorkers(), false);
    }

    public AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                    AdAudioRuleSource ruleSource, PlaybackPort playback,
                                    SpeechRecognitionFactory recognitionFactory) {
        this(hub, clock, ruleSource, playback, createWorkers(), recognitionFactory);
    }

    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Workers workers,
                                     SpeechRecognitionFactory recognitionFactory) {
        this(hub, clock, ruleSource, playback, workers.analysis,
                workers.analysis::shutdownNow, workers.speech,
                workers.speech::shutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"),
                null, Objects.requireNonNull(recognitionFactory, "recognitionFactory"));
    }
    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Workers workers, boolean useNoopSpeechProvider) {
        this(hub, clock, ruleSource, playback, workers.analysis,
                workers.analysis::shutdownNow, workers.speech,
                workers.speech::shutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"),
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                ignored -> new NoopAdAudioSignalProvider("probe"),
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             Executor speechWorker, Runnable speechWorkerShutdown,
                             ProbeProviderFactory probeProviderFactory,
                             SpeechProviderFactory speechProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                speechWorker, speechWorkerShutdown,
                probeProviderFactory, speechProviderFactory, null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             SpeechRecognitionFactory recognitionFactory,
                             Executor speechWorker, Runnable speechWorkerShutdown) {
        this(hub, clock, ruleSource, playback, Runnable::run, () -> { },
                speechWorker, speechWorkerShutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"), null,
                Objects.requireNonNull(recognitionFactory, "recognitionFactory"));
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             ProbeProviderFactory probeProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                probeProviderFactory,
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             ProbeProviderFactory probeProviderFactory,
                             SpeechProviderFactory speechProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                probeProviderFactory, speechProviderFactory, null);
    }

    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Executor worker, Runnable workerShutdown,
                                     Executor speechWorker, Runnable speechWorkerShutdown,
                                     ProbeProviderFactory probeProviderFactory,
                                     SpeechProviderFactory speechProviderFactory,
                                     SpeechRecognitionFactory recognitionFactory) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ruleSource = Objects.requireNonNull(ruleSource, "ruleSource");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.workerShutdown = workerShutdown;
        this.speechWorker = Objects.requireNonNull(speechWorker, "speechWorker");
        this.speechWorkerShutdown = speechWorkerShutdown;
        this.probeProviderFactory = Objects.requireNonNull(
                probeProviderFactory, "probeProviderFactory");
        if (speechProviderFactory == null && recognitionFactory == null) {
            throw new NullPointerException("speech provider source");
        }
        this.speechProviderFactory = speechProviderFactory;
        this.recognitionFactory = recognitionFactory;
    }

    public synchronized void start(boolean enabled) {
        if (closed) return;
        this.enabled = enabled;
        reconfigureLocked();
    }

    public synchronized void reloadRules() {
        if (closed) return;
        reconfigureLocked();
    }

    private void reconfigureLocked() {
        loadRulesLocked();
        deactivateLocked();
        PlaybackMediaSignalHub.Session session = hub.session();
        if (coordinator != null) coordinator.reset(session.id());
        refreshLocked();
    }

    public synchronized void bindUi(AdSkipCoordinator.UiPort ui) {
        if (closed) return;
        Objects.requireNonNull(ui, "ui");
        // PlaybackActivity rebinds whenever it regains ownership, which can happen on every
        // playback state change. Re-creating the coordinator would orphan the already
        // running providers: they keep a reference to the old one and the output listener
        // then drops their candidates, so the prompt silently disappears.
        if (this.ui == ui && coordinator != null) {
            refreshLocked();
            return;
        }
        if (coordinator != null) coordinator.close();
        this.ui = ui;
        this.coordinator = new AdSkipCoordinator(playback, ui, 5_000L, diagnostics);
        refreshLocked();
    }

    public synchronized void unbindUi() {
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
        deactivateLocked();
    }

    public synchronized void refresh() {
        if (closed) return;
        refreshLocked();
    }

    public synchronized void suspend() {
        if (closed) return;
        deactivateLocked();
        PlaybackMediaSignalHub.Session session = hub.session();
        if (coordinator != null) coordinator.reset(session.id());
    }

    public synchronized boolean needsPipelineRebuild() {
        return hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO)
                && !hub.isPipelineAttached();
    }

    public synchronized boolean isActive() {
        return isActiveLocked();
    }

    public synchronized AdAudioRuleSnapshot snapshot() {
        return snapshot;
    }

    public synchronized AdSkipPolicyController.Mode skipMode() {
        return skipMode;
    }

    public synchronized void setSkipMode(AdSkipPolicyController.Mode mode) {
        if (closed) return;
        skipMode = Objects.requireNonNull(mode, "mode");
        if (policy != null) installModeResolver(policy);
    }

    public synchronized void setSpeechConfig(SpeechAdConfig config) {
        if (closed) return;
        SpeechAdConfig next = Objects.requireNonNull(config, "config");
        boolean rebuild = !next.equals(speechConfig);
        speechConfig = next;
        if (policy != null) installModeResolver(policy);
        if (rebuild) reconfigureLocked();
    }

    public AdAudioDiagnostics.Snapshot diagnostics() {
        return diagnostics.snapshot();
    }

    public synchronized void stop() {
        if (closed) return;
        enabled = false;
        deactivateLocked();
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        deactivateLocked();
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
        if (workerShutdown != null) workerShutdown.run();
        if (speechWorkerShutdown != null) speechWorkerShutdown.run();
    }

    private void loadRulesLocked() {
        try {
            AdAudioRuleSnapshot loaded = ruleSource.load();
            if (loaded == null) {
                diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
                snapshot = new AdAudioRuleSnapshot(
                        "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "RULE_LOAD_FAILED");
            } else {
                snapshot = loaded;
                if (loaded.hasError()) diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
            }
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
            snapshot = new AdAudioRuleSnapshot(
                    "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "RULE_LOAD_FAILED");
        }
    }

    private void refreshLocked() {
        boolean fingerprintReady = enabled && !snapshot.hasError() && snapshot.hasRules();
        boolean speechReady = speechConfig.enabled() && !speechConfig.keywords().isEmpty();
        if (ui == null || (!fingerprintReady && !speechReady)) {
            // Transition-only: refreshLocked runs every 5s from the host position pump, and
            // an unsampled line here would churn the bounded debug-log ring.
            logTransition("refresh skipped ui=" + (ui != null)
                    + " fingerprint=" + fingerprintReady + " speech=" + speechReady);
            deactivateLocked();
            return;
        }
        PlaybackMediaSignalHub.Session session = hub.session();
        if (!playback.isEligible(session.id(), session.generation())) {
            logTransition("refresh ineligible session=" + session.id()
                    + " gen=" + session.generation());
            deactivateLocked();
            return;
        }
        if (multiplexer != null) {
            // Re-validate the hub SESSION: an active-but-parked provider still holds a
            // capture lease bound to the session it was created for. Generation is
            // deliberately not checked -- it bumps on every seek/flush and the providers
            // already self-heal through onTimelineReset, so comparing it here would rebuild
            // the recognizer on each seek.
            if (isActiveLocked() && activeSessionId == session.id()) {
                publishHostPositionLocked(session);
                return;
            }
            deactivateLocked();
        }
        activateLocked(session, fingerprintReady, speechReady);
        publishHostPositionLocked(session);
        // Logged after the host position is published: the speech provider only leaves IDLE
        // once it has an eligible position, so reading state before this is misleading.
        logTransition("activated"
                + " speech=" + (speechProvider == null ? "none" : speechProvider.state())
                + " pcm=" + (pcmProvider == null ? "none" : pcmProvider.state())
                + " capture=" + hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO)
                + " pipeline=" + hub.isPipelineAttached());
    }

    /** Emits {@code message} only when it differs from the previous refresh outcome. */
    private void logTransition(String message) {
        if (message.equals(lastRefreshLog)) return;
        lastRefreshLog = message;
        AdAudioDiagnostics.log("%s", message);
    }

    private void activateLocked(PlaybackMediaSignalHub.Session session,
                                boolean fingerprintReady, boolean speechReady) {
        AdSkipCoordinator currentCoordinator = coordinator;
        if (currentCoordinator == null) return;
        AdAudioSignalProvider.SessionContext context;
        try {
            context = playback.sessionContext(session.id(), session.generation());
        } catch (RuntimeException e) {
            context = null;
        }
        if (context == null || context.sessionId() != session.id()
                || context.generation() != session.generation()) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return;
        }

        AdAudioRuleSnapshot routingSnapshot = routingSnapshotLocked();
        AdSkipPolicyController nextPolicy = new AdSkipPolicyController(
                context, routingSnapshot.version(), RUNTIME_CANDIDATE_CAPACITY,
                currentCoordinator::onCandidate,
                currentCoordinator::onAutoCandidate);
        installModeResolver(nextPolicy);
        AdAudioDetectionMultiplexer[] muxHolder = new AdAudioDetectionMultiplexer[1];
        AdAudioSignalProvider.Listener output = new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                synchronized (AdAudioRuntimeController.this) {
                    if (multiplexer != muxHolder[0] || policy != nextPolicy
                            || coordinator != currentCoordinator) return;
                }
                nextPolicy.onCandidate(candidate);
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                if (error != null && !PcmAdAudioSignalProvider.ID.equals(error.providerId())) {
                    diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }

            @Override
            public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
                PcmAdAudioSignalProvider currentPcm;
                AdAudioSignalProvider currentProbe;
                AdAudioSignalProvider currentSpeech;
                synchronized (AdAudioRuntimeController.this) {
                    if (multiplexer != muxHolder[0] || policy != nextPolicy
                            || coordinator != currentCoordinator) return;
                    currentPcm = pcmProvider;
                    currentProbe = probeProvider;
                    currentSpeech = speechProvider;
                }
                nextPolicy.onTimelineReset(reset);
                currentCoordinator.onTimelineReset(new PlaybackMediaSignalHub.Lifecycle(
                        reset.sessionId(), reset.generation(),
                        PlaybackMediaSignalHub.ResetReason.valueOf(reset.reason().name()),
                        reset.mediaAnchorMs()));
                notifyTimelineReset(currentPcm, reset);
                notifyTimelineReset(currentProbe, reset);
                notifyTimelineReset(currentSpeech, reset);
            }
        };
        Set<String> allowedRuleIds = new HashSet<>();
        if (fingerprintReady) {
            snapshot.ruleSet().rules().stream()
                    .map(AudioFingerprintRule::id)
                    .forEach(allowedRuleIds::add);
        }
        if (speechReady) allowedRuleIds.add(SpeechAdSignalProvider.RULE_ID);
        AdAudioDetectionMultiplexer nextMux = new AdAudioDetectionMultiplexer(
                context, routingSnapshot.version(), Set.copyOf(allowedRuleIds),
                RUNTIME_CANDIDATE_CAPACITY, output);
        muxHolder[0] = nextMux;
        PcmAdAudioSignalProvider nextPcm = fingerprintReady
                ? new PcmAdAudioSignalProvider(hub, worker, diagnostics) : null;
        AdAudioSignalProvider nextProbe = fingerprintReady
                ? createProbeProviderLocked() : null;
        AdAudioSignalProvider nextSpeech = speechReady
                ? createSpeechProviderLocked() : null;

        policy = nextPolicy;
        multiplexer = nextMux;
        pcmProvider = nextPcm;
        probeProvider = nextProbe;
        speechProvider = nextSpeech;
        activeSessionId = session.id();

        startProvider(nextPcm, true, context, routingSnapshot, nextMux);
        startProvider(nextProbe, snapshot.probeAvailable(), context, routingSnapshot, nextMux);
        startProvider(nextSpeech, true, context, routingSnapshot, nextMux);
        if (nextPcm != null && !isRunning(nextPcm)) {
            closeProvider(nextPcm);
            if (pcmProvider == nextPcm) pcmProvider = null;
        }
        if (nextProbe != null && !isRunning(nextProbe)) {
            closeProvider(nextProbe);
            if (probeProvider == nextProbe) probeProvider = null;
        }
        if (nextSpeech != null && !isReadyForHostPosition(nextSpeech)) {
            closeProvider(nextSpeech);
            if (speechProvider == nextSpeech) speechProvider = null;
        }
    }
    private void deactivateLocked() {
        PcmAdAudioSignalProvider oldPcm = pcmProvider;
        AdAudioSignalProvider oldProbe = probeProvider;
        AdAudioSignalProvider oldSpeech = speechProvider;
        AdAudioDetectionMultiplexer oldMux = multiplexer;
        AdSkipPolicyController oldPolicy = policy;
        pcmProvider = null;
        probeProvider = null;
        speechProvider = null;
        multiplexer = null;
        policy = null;
        activeSessionId = Long.MIN_VALUE;
        closeProvider(oldSpeech);
        closeProvider(oldProbe);
        closeProvider(oldPcm);
        if (oldMux != null) oldMux.close();
        if (oldPolicy != null) oldPolicy.close();
    }

    private boolean isActiveLocked() {
        // IDLE counts as active for the speech provider: it parks there while the position
        // is not yet eligible (buffering, duration unknown) but keeps its recognizer and
        // capture lease. Treating it as inactive would make every refresh tear it down.
        return isRunning(pcmProvider) || isRunning(probeProvider)
                || isReadyForHostPosition(speechProvider);
    }

    private void publishHostPositionLocked(PlaybackMediaSignalHub.Session session) {
        if (multiplexer == null) return;
        AdSkipCoordinator.PlaybackSnapshot playbackSnapshot;
        try {
            playbackSnapshot = playback.snapshot(session.id(), session.generation());
        } catch (RuntimeException e) {
            return;
        }
        if (playbackSnapshot == null
                || playbackSnapshot.sessionId() != session.id()
                || playbackSnapshot.generation() != session.generation()) return;
        AdAudioSignalProvider.HostPosition position =
                new AdAudioSignalProvider.HostPosition(
                        session.id(), session.generation(),
                        Math.max(0L, playbackSnapshot.positionMs()),
                        Math.max(-1L, playbackSnapshot.durationMs()),
                        playbackSnapshot.seekable(), playbackSnapshot.live());
        multiplexer.onHostPosition(position);
        notifyHostPosition(pcmProvider, position);
        notifyHostPosition(probeProvider, position);
        notifyHostPosition(speechProvider, position);
    }

    private AdAudioSignalProvider createProbeProviderLocked() {
        if (!snapshot.probeAvailable()) return new NoopAdAudioSignalProvider("probe");
        try {
            AdAudioSignalProvider provider =
                    probeProviderFactory.create(snapshot.probeSidecar());
            return provider == null ? new NoopAdAudioSignalProvider("probe") : provider;
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return new NoopAdAudioSignalProvider("probe");
        }
    }

    private void startProvider(AdAudioSignalProvider provider, boolean providerEnabled,
                               AdAudioSignalProvider.SessionContext context,
                               AdAudioRuleSnapshot routingSnapshot,
                               AdAudioSignalProvider.Listener listener) {
        if (provider == null) return;
        try {
            provider.setEnabled(providerEnabled);
            provider.start(context, routingSnapshot, listener);
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            closeProvider(provider);
        }
    }

    private AdAudioSignalProvider createSpeechProviderLocked() {
        try {
            if (recognitionFactory != null) {
                return new SpeechAdSignalProvider(
                        hub, recognitionFactory, () -> speechConfig,
                        speechWorker, diagnostics);
            }
            AdAudioSignalProvider provider = speechProviderFactory.create();
            return provider == null
                    ? new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID)
                    : provider;
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID);
        }
    }

    private AdAudioRuleSnapshot routingSnapshotLocked() {
        if (!snapshot.version().isEmpty()) return snapshot;
        return new AdAudioRuleSnapshot(
                snapshot.sourceId(), "speech-runtime-v1", snapshot.ruleSet(),
                snapshot.warnings(), snapshot.lastError(), snapshot.probeSidecar());
    }

    private void installModeResolver(AdSkipPolicyController target) {
        target.setMode(skipMode);
        target.setModeResolver(providerId -> SpeechAdSignalProvider.ID.equals(providerId)
                ? speechConfig.mode() : skipMode);
    }
    private static boolean isReadyForHostPosition(AdAudioSignalProvider provider) {
        if (provider == null) return false;
        AdAudioSignalProvider.ProviderState state = provider.state();
        return state == AdAudioSignalProvider.ProviderState.IDLE
                || state == AdAudioSignalProvider.ProviderState.RUNNING;
    }
    private static boolean isRunning(AdAudioSignalProvider provider) {
        return provider != null
                && provider.state() == AdAudioSignalProvider.ProviderState.RUNNING;
    }

    private static void notifyTimelineReset(
            AdAudioSignalProvider provider,
            AdAudioSignalProvider.TimelineReset reset) {
        if (provider == null) return;
        try {
            provider.onTimelineReset(reset);
        } catch (RuntimeException ignored) {
        }
    }

    private static void notifyHostPosition(
            AdAudioSignalProvider provider,
            AdAudioSignalProvider.HostPosition position) {
        if (provider == null) return;
        try {
            provider.onHostPosition(position);
        } catch (RuntimeException ignored) {
        }
    }

    private static void closeProvider(AdAudioSignalProvider provider) {
        if (provider == null) return;
        try {
            provider.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static Workers createWorkers() {
        ExecutorService analysis = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ad-audio-matcher");
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService speech = Executors.newSingleThreadExecutor(r -> {
            Runnable speechTask = () -> {
                try {
                    android.os.Process.setThreadPriority(
                            android.os.Process.THREAD_PRIORITY_BACKGROUND);
                } catch (RuntimeException ignored) {
                }
                r.run();
            };
            Thread thread = new Thread(speechTask, "ad-audio-speech-owner");
            thread.setDaemon(true);
            try {
                thread.setPriority(Thread.NORM_PRIORITY - 1);
            } catch (RuntimeException ignored) {
            }
            return thread;
        });
        return new Workers(analysis, speech);
    }

    private record Workers(ExecutorService analysis, ExecutorService speech) {
    }
}
