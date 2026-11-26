package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VerticalPageSwitchGesturesTest {

    @Test
    public void armsOnlyInTheMiddleHalfOfThePage() {
        assertTrue(VerticalPageSwitchGestures.arms(500f, 1000));
        assertFalse("left brightness strip must keep its gesture", VerticalPageSwitchGestures.arms(200f, 1000));
        assertFalse("right volume strip must keep its gesture", VerticalPageSwitchGestures.arms(800f, 1000));
        assertFalse("an unmeasured page cannot arm the gesture", VerticalPageSwitchGestures.arms(500f, 0));
    }

    @Test
    public void startsDragOnlyForDominantVerticalMovement() {
        assertTrue(VerticalPageSwitchGestures.startsDrag(2f, 20f, 18f));
        assertFalse("below the slop the tap still belongs to the player", VerticalPageSwitchGestures.startsDrag(2f, 10f, 18f));
        assertFalse("horizontal movement belongs to seek/other gestures", VerticalPageSwitchGestures.startsDrag(40f, 20f, 18f));
    }

    @Test
    public void commitsOnDistanceOrFlickVelocity() {
        // 阈值 = min(页面高 18%, dp 上限)。2000 高页面配 240px 上限时门槛是 240px。
        assertTrue("long drag commits", VerticalPageSwitchGestures.commits(-260f, 0f, 2000, 240f));
        assertFalse("exactly at the threshold keeps the current episode",
                VerticalPageSwitchGestures.commits(-240f, 0f, 2000, 240f));
        assertTrue("quick flick commits even with little travel",
                VerticalPageSwitchGestures.commits(-40f, -600f, 2000, 240f));
        assertFalse("short slow drag returns to the current episode",
                VerticalPageSwitchGestures.commits(-60f, 120f, 2000, 240f));
        assertTrue("a short page makes the ratio, not the dp cap, the binding threshold",
                VerticalPageSwitchGestures.commits(-100f, 0f, 400, 240f));
    }
}
