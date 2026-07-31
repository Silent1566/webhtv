package com.fongmi.android.tv.history;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlobalHistoryResumeSourceTest {

    @Test
    public void allNativeHistoryEntriesUseTheSharedCoordinator() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java", "HistoryResumeCoordinator.open(this, item)");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java", "HistoryResumeCoordinator.open(this, item)");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java", "HistoryResumeCoordinator.open(this, item)");
    }

    @Test
    public void bothPlayersAcceptExplicitCrossConfigResumeHistory() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("EXTRA_RESUME_HISTORY_CID"));
            assertTrue(source.contains("EXTRA_RESUME_HISTORY_KEY"));
            assertTrue(source.contains("startFromResolvedHistory"));
            assertTrue(source.contains("resumeHistory.forPlaybackKey(getHistoryKey(), VodConfig.getCid())"));
        }
    }

    @Test
    public void resolvedPlaybackPreservesHistoryBackdrop() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("intent.putExtra(\"wallPic\", source.getWallPic())"));
        }
    }

    @Test
    public void manualSearchDoesNotDropResumeContextOnFolderResults() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");

        assertTrue(mobile.contains("FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item), getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid())"));
        assertTrue(leanback.contains("VodActivity.start(this, item.getSiteKey(), Result.folder(item), 0, getHistoryResumeCid(), getHistoryResumeKey(), getHistoryResumeTargetCid())"));
        assertFalse(mobile.contains("Notify.show(R.string.history_source_episode_missing)"));
    }

    @Test
    public void manualSearchCarriesHistoryContextUntilVodSelection() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/SearchActivity.java", "directFromHistory");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java", "HistoryResumeCoordinator.openSelected");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SearchActivity.java", "directFromHistory");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java", "HistoryResumeCoordinator.openSelected");
    }

    @Test
    public void manualSearchRejectsResultsAfterTheVodConfigChanges() throws Exception {
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");
        assertTrue(coordinator.contains("int targetCid, Vod selected"));
        assertTrue(coordinator.contains("if (VodConfig.getCid() != targetCid)"));
        assertTrue(coordinator.contains("showSearchFallback(activity, current, targetCid)"));
        assertTrue(coordinator.contains("openSearch(activity, history.getCid(), history.getKey(), targetCid, history.getVodName())"));

        for (String path : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java",
                "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java"}) {
            String source = read(path);
            assertTrue(path + " must carry the target cid", source.contains("getHistoryResumeTargetCid()"));
            assertTrue(path + " must pass the target cid to the coordinator", source.contains("getHistoryResumeTargetCid(), item"));
        }
    }

    @Test
    public void automaticResolverSkipsNonPlayableSearchEntries() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("vod == null || vod.isFolder() || vod.isAction()"));
        assertTrue(source.contains("selected.isFolder() || selected.isAction()"));
    }

    @Test
    public void resolvedDetailFallsBackToTheValidatedCandidateVodId() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("if (detail.getId().isEmpty()) detail.setId(candidate.getId())"));
    }

    @Test
    public void manualSearchPreservesHistoryContextThroughFolders() throws Exception {
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/activity/FolderActivity.java", "historyResumeCid");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java", "historyResumeKey");
        assertContains("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java", "HistoryResumeCoordinator.openSelected");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java", "historyResumeCid");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java", "historyResumeKey");
        assertContains("app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java", "HistoryResumeCoordinator.openSelected");
    }

    @Test
    public void indexFolderItemsContinueHistoryAwareSearchInsteadOfResolvingAsVod() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java");
            int method = source.indexOf("public void onItemClick(Vod item)");
            int indexBranch = source.indexOf("getSite().isIndex()", method);
            int selectedBranch = source.indexOf("HistoryResumeCoordinator.openSelected", method);

            assertTrue(indexBranch > method && indexBranch < selectedBranch);
            assertTrue(source.indexOf("HistoryResumeCoordinator.openSearch", indexBranch) > indexBranch);
        }
    }

    @Test
    public void automaticResolverUsesTheConfiguredSearchPool() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("new ExecutorCompletionService<>(Task.searchPoolExecutor())"));
    }

    @Test
    public void automaticResumeUsesFullSearchAndPersistentLightProgress() throws Exception {
        String resolver = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        assertTrue(resolver.contains("SiteApi.searchContent(site, history.getVodName(), false, \"1\")"));
        assertTrue(coordinator.contains("ProgressBar"));
        assertTrue(coordinator.contains("R.style.Theme_WebHTV_LightDialog"));
        assertTrue(coordinator.contains("setCancelable(false)"));
        assertTrue(coordinator.contains("dismiss(loading)"));
        assertFalse(coordinator.contains("Notify.show(R.string.history_source_searching)"));
    }

    @Test
    public void resolvedPlaybackReloadsHistoryBeforeLaunching() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java");

        assertTrue(source.contains("History current = History.find(history.getCid(), history.getKey())"));
        assertTrue(source.contains("VideoActivity.startFromResolvedHistory(activity, current"));
        assertTrue(source.contains("Notify.show(R.string.history_record_missing)"));
    }

    @Test
    public void bothPlayersAbortWhenExplicitResumeHistoryDisappears() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

            assertTrue(source.contains("hasIntentResumeHistory() && resumeHistory == null"));
            assertTrue(source.contains("Notify.show(R.string.history_record_missing)"));
            assertTrue(source.contains("if (!checkHistory(item)) return;"));
        }
    }

    @Test
    public void automaticDetailValidationHonorsTheOverallDeadline() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");

        assertTrue(source.contains("future.get(remaining, TimeUnit.NANOSECONDS)"));
        assertTrue(source.contains("future.cancel(true)"));
    }

    @Test
    public void automaticSearchPreservesCancellationInterrupts() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java");
        int start = source.indexOf("private static List<Candidate> searchCandidates");
        int end = source.indexOf("private static Resolved resolveCandidateBefore", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));
        assertFalse(source.contains("catch (Throwable"));

        start = source.indexOf("private static List<Candidate> searchSite");
        end = source.indexOf("private static Resolved resolveCandidate", start);
        method = source.substring(start, end);
        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));

        start = source.indexOf("private static Resolved resolveCandidate(");
        end = source.indexOf("private static TmdbItem cachedTmdb", start);
        method = source.substring(start, end);
        assertTrue(method.contains("catch (InterruptedException e)"));
        assertTrue(method.contains("Thread.currentThread().interrupt()"));
    }

    @Test
    public void unstableTmdbMetadataDoesNotFanOutDeletion() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private History deleteRelated(boolean global)");
        int end = source.indexOf("private static void notifyChanged()", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("if (!identity.isEmpty() && Setting.isHistoryAggregationEffective())"));
        assertFalse(method.contains("findByTmdbId(getCid(), getTmdbId())"));
    }

    @Test
    public void playbackAggregationUsesMediaTypeQualifiedTmdbIdentity() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("private static History findPlaybackByTmdb(");
        int end = source.indexOf("private static", start + 1);
        String method = source.substring(start, end);

        assertTrue(method.contains("findByTmdbIdentity"));
        assertFalse(method.contains("findByTmdbId("));
    }

    @Test
    public void globalClearOnlyDeletesTracksOwnedByHistoryRecords() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        int start = source.indexOf("public static void deleteForDisplay()");
        int end = source.indexOf("public static void sync(", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("getHistoryDao().findAll()"));
        assertTrue(method.contains("getTrackDao().delete(item.getKey())"));
        assertFalse(method.contains("getTrackDao().deleteAll()"));
    }

    @Test
    public void globalDisplayQueryIsIsolatedFromGenericHistoryGet() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        assertTrue(source.contains("public static List<History> getForDisplay()"));
        assertTrue(source.contains("HistoryDao().findAll()") || source.contains("getHistoryDao().findAll()"));
        assertTrue(source.contains("if (!Setting.isGlobalHistoryEnabled()) return get();"));
    }

    @Test
    public void resolvedPlaybackRebindsHistoryToTargetMetadata() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            int start = source.indexOf("private boolean checkHistory(Vod item)");
            int end = source.indexOf("private void enrichHistoryMeta", start);
            String method = source.substring(start, end);

            assertTrue(method.contains("mHistory.setVodName(item.getName())"));
            assertTrue(method.contains("mHistory.setVodPic(getInitialArtwork(item))"));
        }
    }

    @Test
    public void explicitResumeDoesNotDeleteSourceHistoryInIncognitoMode() throws Exception {
        for (String mode : new String[]{"mobile", "leanback"}) {
            String source = read("app/src/" + mode + "/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
            assertTrue(source.contains("resumeHistory == null && Setting.isIncognito()"));
        }
    }

    @Test
    public void leanbackSearchStaysUsableWhenSelectedSourceCannotResume() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java");
        int method = source.indexOf("public void onItemClick(Vod item)");
        int resume = source.indexOf("if (!item.isFolder() && isHistoryResume())", method);
        int leaving = source.indexOf("mLeavingForPlayback = true", method);
        assertTrue("history selection must resolve before the search page enters leaving state", resume > method && resume < leaving);
    }

    private static void assertContains(String path, String token) throws Exception {
        assertTrue(path + " must contain " + token, read(path).contains(token));
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
