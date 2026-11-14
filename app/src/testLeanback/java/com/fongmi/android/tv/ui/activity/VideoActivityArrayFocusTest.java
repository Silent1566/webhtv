package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.custom.ProgressLayout;
import com.fongmi.android.tv.utils.ResUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real production helpers on a recycled grid; synthetic 480-episode labels, not a tested media source. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = com.fongmi.android.tv.App.class,
        shadows = VideoActivityArrayFocusTest.WindowFocus.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class VideoActivityArrayFocusTest {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 180;
    private TestVideo host;
    private ActivityController<TestVideo> controller;
    private HorizontalGridView grid;
    private ArrayAdapter adapter;
    private ProgressLayout root;
    private Button outside;

    @Before
    public void setUp() throws Exception {
        controller = Robolectric.buildActivity(TestVideo.class);
        host = controller.get();
        host.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        root = new ProgressLayout(host, null);
        grid = new HorizontalGridView(host);
        grid.setId(R.id.array);
        grid.setHorizontalSpacing(ResUtil.dp2px(8));
        grid.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        grid.setItemAnimator(null);
        root.addView(grid, new RelativeLayout.LayoutParams(WIDTH, 100));
        outside = new Button(host);
        outside.setText("Outside row");
        outside.setFocusableInTouchMode(true);
        RelativeLayout.LayoutParams outsideParams = new RelativeLayout.LayoutParams(160, 60);
        outsideParams.topMargin = 110;
        root.addView(outside, outsideParams);

        // Avoid unrelated PlayerView PNG decoding: the generated constructor only assigns view fields.
        Constructor<?> constructor = ActivityVideoBinding.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] views = new Object[constructor.getParameterCount()];
        views[0] = root;
        views[3] = grid;
        host.binding = (ActivityVideoBinding) constructor.newInstance(views);
        ReflectionHelpers.setField(host, "mBinding", host.binding);

        adapter = new ArrayAdapter(host) {
            @NonNull @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ViewHolder holder = super.onCreateViewHolder(parent, viewType);
                // Fixed fixture width: Robolectric text measurement must not prevent recycling.
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(110, 64));
                return holder;
            }
        };
        ReflectionHelpers.setField(host, "mArrayAdapter", adapter);
        List<String> labels = new ArrayList<>();
        labels.add(host.getString(R.string.play_reverse));
        labels.add(host.getString(new History().getRevPlayText()));
        int size = call("getEpisodeSegmentSize", int.class, 480);
        assertEquals(40, size);
        for (int start = 1; start <= 480; start += size) labels.add(start + "-" + (start + size - 1));
        adapter.setSegmentSize(size);
        adapter.addAll(labels);
        grid.setAdapter(adapter);
        adapter.setOnKeyListener((view, key, event) -> call("onArrayKey", KeyEvent.class, event));
        grid.setOnKeyListener((view, key, event) -> call("onArrayKey", KeyEvent.class, event));
        controller.create().visible();
        settle();
    }

    @Test
    public void focusedPositionUsesAdapterPositionAfterRecycling() {
        seedFocus(9);
        assertRecycled();
        int attached = grid.indexOfChild(host.getCurrentFocus());
        assertNotEquals(9, attached);
        System.out.println("RANGE-FOCUS recycled first=" + firstAttached() + " focusedAdapter=9 attached=" + attached);
        assertEquals("Focused adapter 9 must not be reported as attached child " + attached,
                9, (int) call("focusedArrayPosition"));
    }

    @Test
    public void requestTargetsAdapterItemNotAnAttachedIndex() {
        seedFocus(5);
        assertRecycled();
        call("requestArrayFocus", int.class, 4);
        assertEquals("Immediate request must focus adapter 4, not getChildAt(4): " + state(), 4, focusedAdapter());
        settle();
        assertFocusAndSelection(4);
    }

    @Test
    public void rightAndLeftTraverse321To360AndHigherSegments() {
        seedFocus(9);
        assertRecycled();
        assertEquals("281-320", text(9));
        for (int target : new int[]{10, 11, 12, 11, 10, 9}) {
            int key = target > focusedAdapter() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
            assertTrue(call("onArrayKey", KeyEvent.class, new KeyEvent(KeyEvent.ACTION_DOWN, key)));
            settle();
            assertFocusAndSelection(target);
            assertRecycled();
        }
        assertEquals("281-320", text(9));
    }

    @Test
    public void leftFrom321To360ReturnsTo281To320() {
        seedFocus(10);
        assertRecycled();
        assertEquals("321-360", text(10));
        assertTrue(call("onArrayKey", KeyEvent.class, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        settle();
        assertFocusAndSelection(9);
    }

    @Test
    public void firstLastEmptyAndZeroDeltaAreSafe() {
        seedFocus(0);
        assertFalse(call("moveArraySelection", int.class, -1));
        assertFalse(call("moveArraySelection", int.class, 0));
        assertEquals(0, focusedAdapter());
        seedFocus(13);
        assertRecycled();
        assertFalse(call("moveArraySelection", int.class, 1));
        assertEquals(13, focusedAdapter());
        outside.requestFocus();
        adapter.clear();
        settle();
        assertFalse(call("moveArraySelection", int.class, 1));
        assertEquals(RecyclerView.NO_POSITION, (int) call("focusedArrayPosition"));
    }

    @Test
    public void noPositionIsNotAnAttachedIndex() {
        seedFocus(9);
        assertRecycled();
        adapter.notifyDataSetChanged();
        assertEquals(RecyclerView.NO_POSITION, grid.getChildAdapterPosition(host.getCurrentFocus()));
        assertEquals(RecyclerView.NO_POSITION, (int) call("focusedArrayPosition"));
    }

    @Test
    public void layoutAndPostedFallbackDoNotStealFocusAfterLeavingRow() {
        seedFocus(9);
        assertRecycled();
        call("requestArrayFocus", int.class, 10);
        assertTrue(outside.requestFocus());
        settle();
        assertTrue("Pending layout must not steal focus", outside.hasFocus());
        assertEquals(RecyclerView.NO_POSITION, (int) call("focusedArrayPosition"));
        assertNull(ReflectionHelpers.getField(host, "mArrayFocusListener"));

        seedFocus(9);
        View target = grid.findViewHolderForAdapterPosition(10).itemView;
        target.setFocusable(false);
        call("requestArrayFocus", int.class, 10);
        assertTrue(outside.requestFocus());
        target.setFocusable(true);
        settle();
        assertTrue("Posted fallback must not steal focus", outside.hasFocus());
    }

    @Test
    public void selectionAuthorityRejectsStaleSegmentCallbacksAfterRecycling() {
        seedFocus(9);
        assertRecycled();
        adapter.setSelectedPosition(9);
        call("selectEpisodeSegmentPosition", int.class, 4);
        settle();
        assertFocusAndSelection(9);
    }

    @Test
    public void displayedSegmentRemainsANoopAfterRecycling() {
        seedFocus(9);
        assertRecycled();
        Flag flag = new Flag();
        List<Episode> episodes = new ArrayList<>();
        for (int i = 1; i <= 480; i++) episodes.add(Episode.create("Synthetic " + i, "https://example.invalid/" + i));
        ReflectionHelpers.setField(flag, "episodes", episodes);
        ReflectionHelpers.setField(host, "mFastPlaybackFlag", flag);
        EpisodeAdapter shown = new EpisodeAdapter(host);
        shown.addAll(episodes.subList(280, 320));
        ReflectionHelpers.setField(host, "mEpisodeAdapter", shown);
        ChangeCount changes = new ChangeCount();
        shown.registerAdapterDataObserver(changes);
        adapter.registerAdapterDataObserver(changes);
        call("selectEpisodeSegment", int.class, 9, boolean.class, false);
        settle();
        assertEquals("Already displayed segment must not cause adapter refreshes", 0, changes.count);
        assertEquals(9, focusedAdapter());
    }

    private void seedFocus(int position) {
        grid.setSelectedPosition(position);
        grid.scrollToPosition(position);
        settle();
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        assertNotNull("Fixture must attach adapter " + position + ": " + state(), holder);
        assertTrue(holder.itemView.requestFocus());
        settle();
        assertEquals("Fixture must seed real focus: " + state(), position, focusedAdapter());
    }

    private void settle() {
        for (int frame = 0; frame < 4; frame++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, WIDTH, HEIGHT);
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(16));
            grid.getViewTreeObserver().dispatchOnGlobalLayout();
        }
    }

    private int focusedAdapter() {
        View focus = host.getCurrentFocus();
        return focus == null || focus.getParent() != grid ? RecyclerView.NO_POSITION : grid.getChildAdapterPosition(focus);
    }

    private int firstAttached() { return grid.getChildAdapterPosition(grid.getChildAt(0)); }

    private void assertRecycled() {
        assertTrue("Fixture must recycle: " + state(), grid.getChildCount() < adapter.getItemCount());
        assertTrue("First attached adapter must be >0: " + state(), firstAttached() > 0);
    }

    private void assertFocusAndSelection(int expected) {
        assertEquals("Focus must match requested adapter: " + state(), expected, focusedAdapter());
        assertEquals("Grid selection must match requested adapter", expected, grid.getSelectedPosition());
        assertEquals("Activated adapter selection must match", expected, (int) ReflectionHelpers.getField(adapter, "selectedPosition"));
    }

    private String text(int position) {
        return ((TextView) grid.findViewHolderForAdapterPosition(position).itemView).getText().toString();
    }

    private String state() {
        return "focused=" + focusedAdapter() + " selected=" + grid.getSelectedPosition()
                + " first=" + (grid.getChildCount() == 0 ? -1 : firstAttached()) + " attachedCount=" + grid.getChildCount();
    }

    private <T> T call(String method) { return ReflectionHelpers.callInstanceMethod(VideoActivity.class, host, method); }
    private <T> T call(String method, Class<?> type, Object value) {
        return ReflectionHelpers.callInstanceMethod(VideoActivity.class, host, method,
                ReflectionHelpers.ClassParameter.from((Class) type, value));
    }
    private <T> T call(String method, Class<?> t1, Object v1, Class<?> t2, Object v2) {
        return ReflectionHelpers.callInstanceMethod(VideoActivity.class, host, method,
                ReflectionHelpers.ClassParameter.from((Class) t1, v1), ReflectionHelpers.ClassParameter.from((Class) t2, v2));
    }

    public static class TestVideo extends VideoActivity {
        private ActivityVideoBinding binding;
        @Override protected ViewBinding getBinding() { return binding; }
        @Override protected void initView(Bundle state) { }
        @Override protected void initEvent() { }
        @Override protected boolean customWall() { return false; }
        // Isolate focus helpers from episode content loading; guards are exercised separately above.
        @Override public void onSegmentFocus(int position) { }
    }

    @Implements(Activity.class)
    public static class WindowFocus extends ShadowActivity {
        @Implementation @Override protected View getCurrentFocus() {
            Window window = getWindow();
            return window == null ? null : window.getDecorView().findFocus();
        }
    }

    private static class ChangeCount extends RecyclerView.AdapterDataObserver {
        int count;
        @Override public void onChanged() { count++; }
        @Override public void onItemRangeChanged(int start, int size, Object payload) { count++; }
    }
}
