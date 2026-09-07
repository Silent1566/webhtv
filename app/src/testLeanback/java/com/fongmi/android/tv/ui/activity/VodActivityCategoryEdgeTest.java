package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class VodActivityCategoryEdgeTest {

    @Test
    public void standaloneVodEdgesSwitchPagerCategoriesAndPreserveRowFocus() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java");

        assertTrue("standalone VOD must receive category edge events", source.contains("FolderFragment.CategoryEdgeHost"));
        assertTrue("edge navigation must resolve the adjacent pager position", source.contains("int target = position + (towardEnd ? 1 : -1);"));
        assertTrue("edge navigation must switch the standalone VOD pager", source.contains("mBinding.pager.setCurrentItem(target);"));
        assertTrue("the first content row must restore content focus after the page switch", source.contains("mPendingContentRow = contentRow == 0 ? 0 : NO_PENDING_CONTENT_ROW;"));
        assertTrue("category-row edges must retain focus on the category strip", source.contains("if (mPendingContentRow != NO_PENDING_CONTENT_ROW)"));
        assertTrue("edge navigation must cancel a queued category pager update", source.contains("App.removeCallbacks(mRunnable);"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
