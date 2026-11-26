package com.fongmi.android.tv.following;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.FollowingCalendarActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 追剧日历页的设备契约：七列网格、周/月切换只重投影、选日只影响那一天的分段定位，
 * 以及集行把身份交回原追剧列表。
 *
 * <p>样本刻意不含 TMDB 身份（tmdbId=0），因此整页不会发起任何网络请求：
 * 断言的是“投影 + 分段 + 返回定位”，不是排期抓取。
 */
@RunWith(AndroidJUnit4.class)
public class FollowingCalendarActivityDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private List<String> identityKeys;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        FollowingSettings.setEnabled(true);
        identityKeys = List.of("local:tv:940001:s1", "local:tv:940002:s1");
        FollowingStore.database().getFollowingSourceDao().deleteAll();
        FollowingStore.database().getFollowingDao().deleteAll();
        for (String identityKey : identityKeys) {
            Following item = new Following();
            item.identityKey = identityKey;
            item.seriesKey = identityKey.substring(0, identityKey.lastIndexOf(':'));
            item.mediaType = "tv";
            item.tmdbId = 0;
            item.vodName = "日历样本 " + identityKey.substring(identityKey.length() - 4);
            item.trackedSeason = 1;
            item.watchedSeason = 1;
            item.watchedEpisode = 2;
            item.seasonReleasedEpisodes = 4;
            item.seasonTotalEpisodes = 8;
            item.enabled = true;
            item.nextCheckAt = Long.MAX_VALUE;
            item.createdAt = System.currentTimeMillis();
            item.updatedAt = item.createdAt;
            FollowingStore.saveNew(item, null);
        }
    }

    @After
    public void tearDown() {
        for (String identityKey : identityKeys) FollowingStore.delete(identityKey);
    }

    @Test
    public void rendersSevenColumnGridAndKeepsLocalRecordsSection() {
        try (ActivityScenario<FollowingCalendarActivity> scenario = ActivityScenario.launch(intent())) {
            assertTrue(await(scenario, activity -> {
                RecyclerView grid = activity.findViewById(R.id.grid);
                return grid != null && grid.getAdapter() != null && grid.getAdapter().getItemCount() > 0;
            }));
            assertTrue(await(scenario, activity -> activity.findViewById(R.id.dayNotice).getVisibility() == View.VISIBLE));
            scenario.onActivity(activity -> {
                RecyclerView grid = activity.findViewById(R.id.grid);
                assertEquals(7, ((GridLayoutManager) grid.getLayoutManager()).getSpanCount());
                assertEquals(0, grid.getAdapter().getItemCount() % 7);
                assertEquals(7, ((android.view.ViewGroup) activity.findViewById(R.id.weekdays)).getChildCount());
                RecyclerView entries = activity.findViewById(R.id.entries);
                assertTrue("本地记录区必须可见", entries.getAdapter().getItemCount() > 0);
                assertEquals(View.GONE, activity.findViewById(R.id.empty).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.dayNotice).getVisibility());
                assertEquals("Initial calendar keeps its controls at the top", 0, activity.findViewById(R.id.scroll).getScrollY());
            });
        }
    }

    @Test
    public void weekIsDefaultAndMonthModeRestoresWholeWeeks() {
        try (ActivityScenario<FollowingCalendarActivity> scenario = ActivityScenario.launch(intent())) {
            assertTrue("日历默认停在周视图", await(scenario, activity -> activity.findViewById(R.id.grid) != null
                    && ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter() != null
                    && ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter().getItemCount() == 7));
            scenario.onActivity(activity -> activity.findViewById(R.id.monthMode).performClick());
            assertTrue(await(scenario, activity -> {
                int count = ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter().getItemCount();
                return count >= 28 && count % 7 == 0;
            }));
            scenario.onActivity(activity -> activity.findViewById(R.id.weekMode).performClick());
            assertTrue(await(scenario, activity -> ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter().getItemCount() == 7));
        }
    }

    @Test
    public void selectingADayMovesSelectionWithoutHidingOtherSections() {
        try (ActivityScenario<FollowingCalendarActivity> scenario = ActivityScenario.launch(intent())) {
            assertTrue(await(scenario, activity -> ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter() != null));
            // 默认是周视图，逐日选格在月视图里验证（同屏 4–6 行、位置 9 必然存在）。
            scenario.onActivity(activity -> activity.findViewById(R.id.monthMode).performClick());
            assertTrue(await(scenario, activity -> ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter() != null
                    && ((RecyclerView) activity.findViewById(R.id.grid)).getAdapter().getItemCount() >= 28));
            int[] target = new int[1];
            scenario.onActivity(activity -> {
                RecyclerView grid = activity.findViewById(R.id.grid);
                target[0] = 9;
                RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target[0]);
                assertNotNull("目标日格必须已布局", holder);
                holder.itemView.performClick();
            });
            assertTrue(await(scenario, activity -> {
                RecyclerView grid = activity.findViewById(R.id.grid);
                RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target[0]);
                return holder != null && holder.itemView.isSelected();
            }));
            scenario.onActivity(activity -> {
                RecyclerView entries = activity.findViewById(R.id.entries);
                assertTrue("选日后本地记录区仍然可用", entries.getAdapter().getItemCount() > 0);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.dayNotice).getVisibility());
                assertEquals("Empty-day selection must not jump to local records", 0, activity.findViewById(R.id.scroll).getScrollY());
                TextView range = activity.findViewById(R.id.rangeLabel);
                assertFalse(range.getText().toString().isEmpty());
            });
        }
    }

    @Test
    public void recordRowReturnsIdentityAndClosesCalendar() {
        try (ActivityScenario<FollowingCalendarActivity> scenario = ActivityScenario.launch(intent())) {
            assertTrue(await(scenario, activity -> ((RecyclerView) activity.findViewById(R.id.entries)).getAdapter() != null
                    && ((RecyclerView) activity.findViewById(R.id.entries)).getAdapter().getItemCount() >= 2));
            scenario.onActivity(activity -> {
                RecyclerView entries = activity.findViewById(R.id.entries);
                RecyclerView.ViewHolder holder = entries.findViewHolderForAdapterPosition(1);
                assertNotNull("第一条本地记录必须已布局", holder);
                holder.itemView.findViewById(R.id.entryRow).performClick();
            });
            assertTrue(await(scenario, FollowingCalendarActivity::isFinishing));
        }
    }

    private Intent intent() {
        return new Intent(context, FollowingCalendarActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private boolean await(ActivityScenario<FollowingCalendarActivity> scenario, Check check) {
        boolean[] result = new boolean[1];
        for (int i = 0; i < 40; i++) {
            result[0] = false;
            scenario.onActivity(activity -> result[0] = check.value(activity));
            if (result[0]) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private interface Check {
        boolean value(FollowingCalendarActivity activity);
    }
}
