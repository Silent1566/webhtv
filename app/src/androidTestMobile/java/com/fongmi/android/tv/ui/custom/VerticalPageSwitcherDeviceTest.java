package com.fongmi.android.tv.ui.custom;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class VerticalPageSwitcherDeviceTest {

    @Test
    @UiThreadTest
    public void tapWithoutEpisodePreviewFallsThroughToPlayer() {
        View page = new View(InstrumentationRegistry.getInstrumentation().getTargetContext());
        VerticalPageSwitcher switcher = new VerticalPageSwitcher(page, null, new VerticalPageSwitcher.Listener() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public boolean canSwitch(int offset) {
                throw new AssertionError("No episode should be queried without a preview");
            }

            @Override
            public void onSwitch(int offset) {
                throw new AssertionError("No episode should be switched without a preview");
            }

            @Override
            public void onPreparePeer(int offset) {
                throw new AssertionError("No preview should be prepared when absent");
            }

            @Override
            public void onCancelBaseGesture() {
                throw new AssertionError("The player should keep the gesture");
            }
        });
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now, action, 50f, 50f, 0);
            try {
                assertFalse(switcher.onTouchEvent(event));
            } finally {
                event.recycle();
            }
        }
    }
}
