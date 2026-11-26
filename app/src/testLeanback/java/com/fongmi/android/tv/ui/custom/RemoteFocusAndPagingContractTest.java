package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 首页环绕、详情页焦点顺序、分页与选集弹层返回的源码契约。 */
public class RemoteFocusAndPagingContractTest {

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void homeFunctionRowWrapsToOffscreenEndWithoutDroppingFocus() throws Exception {
        String row = read("app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomRowPresenter.java");
        assertTrue(row, row.contains("focusEdgeTarget(grid, target, 0)"));
        assertTrue(row, row.contains("grid.scrollToPosition(target)"));
        assertTrue(row, row.contains("int position = focusedPosition(grid);"));
        String item = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/FuncPresenter.java");
        assertTrue(item, item.contains("requestFocus(recyclerView, position, attempt + 1)"));
    }

    @Test
    public void detailPageFocusOrderStartsAtReverseChip() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        assertTrue(source, source.contains("private boolean onFlagKeyForDetail(KeyEvent event)"));
        assertTrue(source, source.contains("requestArrayFocus(0);"));
        assertTrue(source, source.contains("if (target >= 2 && getFlag() != null) showEpisodeSegment(target);"));
    }

    @Test
    public void episodeDialogBackReturnsToSegmentChipBeforeDismissing() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeListDialog.java");
        assertTrue(source, source.contains("dialog.setOnKeyListener"));
        assertTrue(source, source.contains("KeyEvent.KEYCODE_BACK"));
        assertTrue(source, source.contains("isFocusInside(binding.array)"));
        assertTrue(source, source.contains("focusArray();"));
    }

    @Test
    public void episodePagingReusesBindingsAndCachesTitleCompaction() throws Exception {
        String adapter = read("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java");
        assertTrue(adapter, adapter.contains("notifyItemRangeChanged(0, previous)"));
        String compact = read("app/src/main/java/com/fongmi/android/tv/utils/EpisodeTitleCompact.java");
        assertTrue(compact, compact.contains("COMPACT_CACHE"));
        assertTrue(compact, compact.contains("COMPACT_CACHE.get(key)"));
    }

    @Test
    public void homeFuncRowWrapIsHandledAtActivityDispatchLevel() throws Exception {
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        assertTrue(home, home.contains("wrapFuncRowEdge(KeyEvent event)"));
        assertTrue(home, home.contains("focus.getTag(R.id.tag_home_func_button)"));
        assertTrue(home, home.contains("focusFuncTarget(grid, target, 0)"));
        assertTrue(home, home.contains("grid.postDelayed(() -> focusFuncTarget(grid, target, attempt + 1), 33)"));
        String presenter = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/FuncPresenter.java");
        assertTrue(presenter, presenter.contains("setTag(R.id.tag_home_func_button"));
    }

    @Test
    public void qualityRowDownReachesReverseChipViaItemLevelListener() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        assertTrue(source, source.contains("mQualityAdapter.setOnKeyListener((view, keyCode, event) -> onQualityKey(event))"));
        assertTrue(source, source.contains("requestArrayFocus(0)"));
        String adapter = read("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/QualityAdapter.java");
        assertTrue(adapter, adapter.contains("holder.binding.text.setOnKeyListener(keyListener)"));
    }

    @Test
    public void detailBackChainFocusesFlagThenPlayerThenSingleBackExits() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        assertTrue(source, source.contains("handleDetailBackFocus()"));
        assertTrue(source, source.contains("applyFlagFocus(0)"));
        assertTrue(source, source.contains("focusDetailPlayer()"));
        assertTrue(source, source.contains("finishVideoPlayback();\n    }"));
        // 单次返回即退出：不允许再出现 2 秒双击退出计时
        assertTrue(source, !source.contains("mDetailBackAtVideo"));
    }

    @Test
    public void videoFocusRingUsesThemedDrawableNotApplicationContext() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        assertTrue(source, source.contains("mVideoFocusRingDrawable = mBinding.video.getForeground()"));
        assertTrue(source, source.contains("setForeground(mVideoFocusRingDrawable)"));
        // 焦点环不得用 application context 重新加载（?attr/tvPlayerRing 会解析成透明）
        assertTrue(source, !source.contains("setForeground(ResUtil.getDrawable(R.drawable.selector_video))"));
        assertTrue(source, source.contains("refreshDrawableState()"));
    }

    @Test
    public void seamlessRowDownTargetFollowsExpandState() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java");
        assertTrue(source, source.contains("expanded ? R.id.preloadSeamlessBar : View.NO_ID"));
        assertTrue(source, source.contains("mBinding.preloadSeamlessSwitch.setNextFocusDownId(R.id.preloadExpand)"));
    }

    @Test
    public void episodeDialogBackFallsBackToTopRowWhenSegmentHidden() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeListDialog.java");
        assertTrue(source, source.contains("focusSeason()"));
        assertTrue(source, source.contains("focusFlagRow()"));
        assertTrue(source, source.contains("grid.postDelayed"));
    }
}
