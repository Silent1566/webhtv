package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.fongmi.android.tv.utils.ResUtil;

/**
 * 竖屏短剧的整页上下滑切集：当前页跟手移动，对端页从反向边缘滑入，
 * 因此滑动过程中不会漏出应用背景。起始于左右两侧四分之一的手势原地放弃，
 * 交给亮度/音量等既有手势。
 *
 * <p>移植自 wyicibo1-netizen/webhtv 的 dc580f7b1c33 版本，
 * 判定逻辑抽到 {@link VerticalPageSwitchGestures} 以便单元测试。
 */
public class VerticalPageSwitcher {

    public interface Listener {

        boolean isEnabled();

        boolean canSwitch(int offset);

        void onSwitch(int offset);

        void onPreparePeer(int offset);

        void onCancelBaseGesture();
    }

    private static final String TAG = "TV-vpage";
    private static final long OUT_DURATION = 190;
    private static final long IN_DURATION = 240;
    private static final int SLOP_DP = 6;
    private static final int THRESHOLD_DP = 80;
    /** 没有相邻集时的拖动阻尼，让页面仍能跟手但明显吃力。 */
    private static final float BLOCKED_DAMPING = 0.35f;

    private final View page;
    private final View peer;
    private final Listener listener;
    private VelocityTracker tracker;
    private float downX;
    private float downY;
    private float dragY;
    private int offset;
    private int height;
    private boolean armed;
    private boolean dragging;

    public VerticalPageSwitcher(View page, View peer, Listener listener) {
        this.page = page;
        this.peer = peer;
        this.listener = listener;
    }

    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (peer == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                page.animate().cancel();
                page.setTranslationY(0f);
                page.setAlpha(1f);
                peer.animate().cancel();
                peer.setVisibility(View.GONE);
                if (!listener.isEnabled()) return false;
                height = page.getHeight();
                syncPeerGeometry();
                downX = event.getRawX();
                downY = event.getRawY();
                armed = height > 0 && VerticalPageSwitchGestures.arms(downX, page.getWidth());
                Log.d(TAG, "down rawX=" + downX + " rawY=" + downY + " width=" + page.getWidth()
                        + " height=" + height + " armed=" + armed + " enabled=" + listener.isEnabled());
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!armed) return false;
                if (!dragging) {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!VerticalPageSwitchGestures.startsDrag(dx, dy, ResUtil.dp2px(SLOP_DP))) return false;
                    startDrag(dy);
                }
                tracker.addMovement(event);
                dragY = event.getRawY() - downY;
                if (!listener.canSwitch(offset)) dragY *= BLOCKED_DAMPING;
                page.setTranslationY(dragY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    reset();
                    return false;
                }
                tracker.addMovement(event);
                tracker.computeCurrentVelocity(1000);
                settle(event.getActionMasked() == MotionEvent.ACTION_CANCEL, tracker.getYVelocity());
                reset();
                return true;
            default:
                return false;
        }
    }

    /** 对端页与当前页在容器里的位置/尺寸可能不同（内嵌与全屏切换），手势开始时对齐一次。 */
    private void syncPeerGeometry() {
        if (page.getWidth() <= 0 || page.getHeight() <= 0) return;
        peer.setX(page.getX());
        peer.setY(page.getY());
        ViewGroup.LayoutParams params = peer.getLayoutParams();
        if (params == null || (params.width == page.getWidth() && params.height == page.getHeight())) return;
        params.width = page.getWidth();
        params.height = page.getHeight();
        peer.setLayoutParams(params);
    }

    private void startDrag(float dy) {
        dragging = true;
        offset = dy < 0 ? 1 : -1;
        page.animate().cancel();
        peer.animate().cancel();
        listener.onCancelBaseGesture();
        listener.onPreparePeer(offset);
        peer.setTranslationY(0f);
        peer.setVisibility(View.VISIBLE);
        tracker = VelocityTracker.obtain();
    }

    private void settle(boolean canceled, float velocity) {
        int direction = dragY < 0 ? 1 : -1;
        float threshold = ResUtil.dp2px(THRESHOLD_DP);
        boolean commit = !canceled
                && listener.canSwitch(direction)
                && VerticalPageSwitchGestures.commits(dragY, velocity, height, threshold);
        Log.d(TAG, "settle dragY=" + dragY + " velocity=" + velocity + " height=" + height
                + " threshold=" + threshold + " ratio=" + (height * 0.18f)
                + " direction=" + direction + " canSwitch=" + listener.canSwitch(direction) + " commit=" + commit);
        if (!commit) {
            page.animate().translationY(0f).setDuration(OUT_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> peer.setVisibility(View.GONE)).start();
            return;
        }
        float out = direction > 0 ? -height : height;
        page.animate().translationY(out).setDuration(OUT_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    listener.onSwitch(direction);
                    page.setTranslationY(0f);
                    page.setAlpha(0f);
                    page.animate().alpha(1f).setDuration(IN_DURATION)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> {
                                peer.setVisibility(View.GONE);
                                page.setAlpha(1f);
                            }).start();
                }).start();
    }

    private void reset() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
        armed = false;
        dragging = false;
        dragY = 0f;
        offset = 0;
    }
}
