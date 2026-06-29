package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchStatus;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.subtitle.model.SubtitleTrigger;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

public final class SubtitlePlaybackSession {

    interface Controller {

        interface Callback {
            void onSubtitleResult(SubtitleRequest request, SubtitleMatchResult result);
        }

        void bind(Callback callback);

        void onPlaybackStarted(SubtitleRequest request);

        void onEpisodeChanged(SubtitleRequest request);

        void onSourceChanged(SubtitleRequest request);

        void onStop(String playbackKey);

        void manualSearch(SubtitleRequest request, Callback callback);

        default void manualSearch(SubtitleRequest request, String keyword, Callback callback) {
            manualSearch(request, callback);
        }

        void resolve(SubtitleRequest request, SubtitleCandidate candidate, Callback callback);
    }

    interface ResultApplier {
        boolean apply(Host host, Sub sub);
    }

    interface ResultNotifier {
        void notify(Host host, SubtitleRequest request, SubtitleMatchResult result, boolean applied);
    }

    public interface ManualCallback {
        void onSubtitleResult(SubtitleRequest request, SubtitleMatchResult result, boolean applied);
    }

    public interface Host {
        String getSubtitlePlaybackKey();

        Site getSubtitleSite();

        Vod getSubtitleVod();

        Episode getSubtitleEpisode();

        TmdbItem getSubtitleTmdbItem();

        TmdbEpisode getSubtitleTmdbEpisode();

        PlayerManager getSubtitlePlayer();

        boolean isSubtitleHostActive();
    }

    private final Controller controller;
    private final SubtitleRequestFactory requestFactory;
    private final SubtitleInjector injector;
    private final ResultApplier resultApplier;
    private final ResultNotifier resultNotifier;
    private SubtitleRequest currentRequest;
    private String currentEpisodeMarker;
    private boolean active;

    public SubtitlePlaybackSession(Host host) {
        this(host, new DefaultController(), new SubtitleRequestFactory(), new SubtitleInjector(), DefaultResultApplier.INSTANCE, DefaultResultNotifier.INSTANCE);
    }

    SubtitlePlaybackSession(Host host, Controller controller, SubtitleRequestFactory requestFactory, SubtitleInjector injector, ResultApplier resultApplier) {
        this(host, controller, requestFactory, injector, resultApplier, DefaultResultNotifier.INSTANCE);
    }

    SubtitlePlaybackSession(Host host, Controller controller, SubtitleRequestFactory requestFactory, SubtitleInjector injector, ResultApplier resultApplier, ResultNotifier resultNotifier) {
        this.controller = controller == null ? new DefaultController() : controller;
        this.requestFactory = requestFactory == null ? new SubtitleRequestFactory() : requestFactory;
        this.injector = injector == null ? new SubtitleInjector() : injector;
        this.resultApplier = resultApplier == null ? DefaultResultApplier.INSTANCE : resultApplier;
        this.resultNotifier = resultNotifier == null ? DefaultResultNotifier.INSTANCE : resultNotifier;
        this.currentEpisodeMarker = "";
        this.controller.bind((request, result) -> onSubtitleResult(host, request, result));
    }

    public void onPlaybackStarted(Host host, Result result) {
        if (host == null) return;
        String playbackKey = host.getSubtitlePlaybackKey();
        if (SubtitleStrings.isEmpty(playbackKey)) return;
        if (hasProvidedSubtitle(result)) {
            stop(host);
            return;
        }

        SubtitleTrigger trigger = resolveTrigger(host);
        SubtitleRequest request = requestFactory.create(playbackKey, host.getSubtitleSite(), host.getSubtitleVod(), host.getSubtitleEpisode(), result, host.getSubtitleTmdbItem(), host.getSubtitleTmdbEpisode(), trigger);
        if (request == null || SubtitleStrings.isEmpty(request.getPlayUrl())) {
            stop(host);
            return;
        }

        currentRequest = request;
        currentEpisodeMarker = episodeMarker(host.getSubtitleEpisode());
        active = true;

        if (trigger == SubtitleTrigger.AUTO_PLAY) controller.onPlaybackStarted(request);
        else if (trigger == SubtitleTrigger.EPISODE_SWITCH) controller.onEpisodeChanged(request);
        else controller.onSourceChanged(request);
    }

    public void stop(Host host) {
        active = false;
        currentRequest = null;
        currentEpisodeMarker = "";
        if (host != null && !SubtitleStrings.isEmpty(host.getSubtitlePlaybackKey())) controller.onStop(host.getSubtitlePlaybackKey());
    }

    public void manualSearch(Host host, ManualCallback callback) {
        manualSearch(host, "", callback);
    }

    public void manualSearch(Host host, String keyword, ManualCallback callback) {
        if (callback == null) return;
        if (!canManualSearch(host)) {
            callback.onSubtitleResult(currentRequest, SubtitleMatchResult.error("inactive"), false);
            return;
        }
        SubtitleRequest request = currentRequest;
        controller.manualSearch(request, keyword, (current, result) -> {
            if (request != currentRequest || !isActive(host)) return;
            callback.onSubtitleResult(current, result, false);
        });
    }

    public String getManualSearchKeyword(Host host) {
        if (!canManualSearch(host)) return "";
        return manualKeyword(currentRequest);
    }

    public void resolveManual(Host host, SubtitleCandidate candidate, ManualCallback callback) {
        if (callback == null) return;
        if (!canManualSearch(host) || candidate == null) {
            callback.onSubtitleResult(currentRequest, SubtitleMatchResult.error("inactive"), false);
            return;
        }
        SubtitleRequest request = currentRequest;
        controller.resolve(request, candidate, (current, result) -> {
            if (request != currentRequest || !isActive(host)) return;
            boolean applied = applyMatchedSubtitle(host, result);
            callback.onSubtitleResult(current, result, applied);
        });
    }

    private void onSubtitleResult(Host host, SubtitleRequest request, SubtitleMatchResult result) {
        if (host == null || request == null || result == null) return;
        if (request != currentRequest) return;
        if (!host.isSubtitleHostActive()) return;
        if (result.getStatus() != SubtitleMatchStatus.MATCHED || result.getAsset() == null) {
            resultNotifier.notify(host, request, result, false);
            return;
        }

        boolean applied = applyMatchedSubtitle(host, result);
        resultNotifier.notify(host, request, result, applied);
    }

    private boolean applyMatchedSubtitle(Host host, SubtitleMatchResult result) {
        if (result == null || result.getStatus() != SubtitleMatchStatus.MATCHED || result.getAsset() == null) return false;
        Sub sub = injector.toSub(result.getAsset());
        if (sub == null) return false;
        return resultApplier.apply(host, sub);
    }

    private SubtitleTrigger resolveTrigger(Host host) {
        if (!active) return SubtitleTrigger.AUTO_PLAY;
        return SubtitleStrings.equals(currentEpisodeMarker, episodeMarker(host.getSubtitleEpisode())) ? SubtitleTrigger.SOURCE_SWITCH : SubtitleTrigger.EPISODE_SWITCH;
    }

    private boolean hasProvidedSubtitle(Result result) {
        if (result == null) return false;
        List<Sub> subs = result.getSubs();
        return subs != null && !subs.isEmpty();
    }

    private boolean canManualSearch(Host host) {
        return currentRequest != null && isActive(host);
    }

    private boolean isActive(Host host) {
        return host != null && active && host.isSubtitleHostActive();
    }

    private String manualKeyword(SubtitleRequest request) {
        if (request == null) return "";
        String title = firstNonEmpty(request.getTmdbItem() == null ? "" : request.getTmdbItem().getTitle(), request.getVodName());
        if (SubtitleStrings.isEmpty(title)) return "";
        int season = request.getSeasonNumber();
        int episode = request.getEpisodeNumber();
        if (season > 0 && episode > 0) return String.format(java.util.Locale.US, "%s S%02dE%02d", title, season, episode);
        if (episode > 0) return title + " 第" + episode + "集";
        String year = request.getVodYear();
        return !SubtitleStrings.isEmpty(year) && !title.contains(year) ? title + " " + year : title;
    }

    private String firstNonEmpty(String first, String second) {
        return !SubtitleStrings.isEmpty(first) ? first : SubtitleStrings.isEmpty(second) ? "" : second;
    }

    private String episodeMarker(Episode episode) {
        if (episode == null) return "";
        if (!SubtitleStrings.isEmpty(episode.getUrl())) return episode.getUrl();
        if (episode.getNumber() > 0) return String.valueOf(episode.getNumber());
        return episode.getName();
    }

    private static final class DefaultController implements Controller {

        private Callback callback;
        private final SubtitleAutoController delegate = new SubtitleAutoController((request, result) -> {
            if (callback != null) callback.onSubtitleResult(request, result);
        });
        private final SubtitleMatchService manualService = new SubtitleMatchService();

        @Override
        public void bind(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onPlaybackStarted(SubtitleRequest request) {
            delegate.onPlaybackStarted(request);
        }

        @Override
        public void onEpisodeChanged(SubtitleRequest request) {
            delegate.onEpisodeChanged(request);
        }

        @Override
        public void onSourceChanged(SubtitleRequest request) {
            delegate.onSourceChanged(request);
        }

        @Override
        public void onStop(String playbackKey) {
            delegate.onStop(playbackKey);
        }

        @Override
        public void manualSearch(SubtitleRequest request, Callback callback) {
            manualSearch(request, "", callback);
        }

        @Override
        public void manualSearch(SubtitleRequest request, String keyword, Callback callback) {
            manualService.manualSearch(request, keyword, (current, result) -> {
                if (callback != null) callback.onSubtitleResult(current, result);
            });
        }

        @Override
        public void resolve(SubtitleRequest request, SubtitleCandidate candidate, Callback callback) {
            manualService.resolve(request, candidate, (current, result) -> {
                if (callback != null) callback.onSubtitleResult(current, result);
            });
        }
    }

    private enum DefaultResultApplier implements ResultApplier {
        INSTANCE;

        @Override
        public boolean apply(Host host, Sub sub) {
            if (host == null || sub == null) return false;
            PlayerManager player = host.getSubtitlePlayer();
            if (player == null || player.isReleased()) return false;
            player.setSub(sub);
            return true;
        }
    }

    private enum DefaultResultNotifier implements ResultNotifier {
        INSTANCE;

        @Override
        public void notify(Host host, SubtitleRequest request, SubtitleMatchResult result, boolean applied) {
            if (result == null || result.getStatus() == SubtitleMatchStatus.CANCELED) return;
            switch (result.getStatus()) {
                case MATCHED -> {
                    String name = result.getAsset() == null ? "" : result.getAsset().getDisplayName();
                    Notify.show(ResUtil.getString(applied ? R.string.subtitle_auto_match_hit : R.string.subtitle_auto_match_hit_not_applied, name));
                }
                case NO_MATCH -> Notify.show(R.string.subtitle_auto_match_empty);
                case SKIPPED -> {
                    if ("provider_unavailable".equals(result.getReason())) Notify.show(R.string.subtitle_auto_match_provider_unavailable);
                }
                case ERROR -> Notify.show(ResUtil.getString(R.string.subtitle_auto_match_failed, readableReason(result.getReason())));
                default -> {
                }
            }
        }

        private String readableReason(String reason) {
            return SubtitleStrings.isEmpty(reason) ? ResUtil.getString(R.string.subtitle_auto_match_unknown_reason) : reason;
        }
    }
}
