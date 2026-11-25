package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.utils.KeyUtil;

public class CustomKeyDownVod extends GestureDetector.SimpleOnGestureListener {

    private final GestureDetector detector;
    private final Listener listener;
    private final SeekSession seek;
    private final HeldKey speedKey = new HeldKey();
    private boolean changeSpeed;
    private boolean full;

    public static CustomKeyDownVod create(Activity activity) {
        return new CustomKeyDownVod(activity);
    }

    private CustomKeyDownVod(Activity activity) {
        this.detector = new GestureDetector(activity, this);
        this.listener = (Listener) activity;
        this.seek = new SeekSession(listener);
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (!full) return false;
        return detector.onTouchEvent(e);
    }

    public boolean isChangingSpeed() {
        return changeSpeed;
    }

    public void releaseSpeed() {
        speedKey.clear();
        if (!changeSpeed) return;
        changeSpeed = false;
        listener.onSpeedEnd();
    }

    public void setFull(boolean full) {
        if (!full) {
            cancelSeek();
            releaseSpeed();
        }
        this.full = full;
    }

    public boolean hasEvent(KeyEvent event) {
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    public boolean onKeyDown(KeyEvent event) {
        check(event);
        return true;
    }

    private void check(KeyEvent event) {
        if (KeyUtil.isActionDown(event) && !KeyUtil.isUpKey(event) && speedKey.isHeld()) releaseSpeed();
        if (seek.onKey(event)) return;
        if (KeyUtil.isUpKey(event) && (isHdmi(event) || speedKey.isHeld())) {
            checkHdmiSpeed(event);
            return;
        }
        if (KeyUtil.isActionUp(event) && KeyUtil.isUpKey(event)) {
            if (changeSpeed) listener.onSpeedEnd();
            else listener.onKeyUp();
            changeSpeed = false;
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isDownKey(event)) {
            listener.onKeyDown();
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isEnterKey(event)) {
            listener.onKeyCenter();
        } else if (!changeSpeed && event.isLongPress() && KeyUtil.isUpKey(event)) {
            listener.onSpeedUp();
            changeSpeed = true;
        }
    }

    private void checkHdmiSpeed(KeyEvent event) {
        if (!isHdmi(event)) return;
        if (KeyUtil.isActionDown(event) && event.getRepeatCount() == 0) {
            if (!speedKey.start(event)) return;
            if (changeSpeed) {
                changeSpeed = false;
                listener.onSpeedEnd();
            }
        }
        if (!speedKey.accept(event)) return;
        if (event.isCanceled()) {
            releaseSpeed();
        } else if (KeyUtil.isActionUp(event)) {
            boolean wasChangingSpeed = changeSpeed;
            releaseSpeed();
            if (!wasChangingSpeed) listener.onKeyUp();
        } else if (KeyUtil.isActionDown(event) && !changeSpeed && (event.isLongPress()
                || event.getRepeatCount() > 0 && event.getEventTime() - speedKey.eventTime >= ViewConfiguration.getLongPressTimeout())) {
            changeSpeed = true;
            listener.onSpeedUp();
        }
    }

    private static boolean isHdmi(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_HDMI);
    }

    private static final class HeldKey {
        private int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        private int deviceId;
        private int source;
        private long downTime;
        private long eventTime;
        private long lastEventTime;
        private long lastHdmiTime = Long.MIN_VALUE;

        boolean isHeld() {
            return keyCode != KeyEvent.KEYCODE_UNKNOWN;
        }

        void clear() {
            keyCode = KeyEvent.KEYCODE_UNKNOWN;
        }

        boolean start(KeyEvent event) {
            if (isHdmi(event) && event.getEventTime() < lastHdmiTime) return false;
            keyCode = event.getKeyCode();
            deviceId = event.getDeviceId();
            source = event.getSource();
            downTime = event.getDownTime();
            eventTime = lastEventTime = event.getEventTime();
            return true;
        }

        boolean accept(KeyEvent event) {
            if (keyCode != event.getKeyCode() || deviceId != event.getDeviceId() || source != event.getSource()) return false;
            // HdmiCecLocalDevice injects a fresh downTime for every event.
            if (isHdmi(event)) {
                if (event.getDownTime() < downTime || event.getEventTime() < lastEventTime) return false;
                lastHdmiTime = lastEventTime = event.getEventTime();
            } else if (downTime != event.getDownTime()) {
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        listener.onSingleTap();
        return true;
    }

    public void reset() {
        cancelSeek();
    }

    public void cancelSeek() {
        seek.cancelSeek();
    }

    static final class SeekSession {
        private final SeekListener listener;
        private final Runnable commit = this::commitSeek;
        private final HeldKey heldKey = new HeldKey();
        private long offset;
        private String session;
        private boolean pending;

        SeekSession(SeekListener listener) {
            this.listener = listener;
        }

        void cancelSeek() {
            heldKey.clear();
            cancelPending();
        }

        private void cancelPending() {
            boolean wasPending = pending;
            App.removeCallbacks(commit);
            offset = 0;
            session = null;
            pending = false;
            if (wasPending) listener.onSeekCancel();
        }

        boolean onKey(KeyEvent event) {
            if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) {
                if (KeyUtil.isActionDown(event)) cancelSeek();
                return false;
            }
            if (KeyUtil.isActionDown(event)) onDown(event);
            else if (KeyUtil.isActionUp(event)) onUp(event);
            return true;
        }

        private void onDown(KeyEvent event) {
            String current = listener.getSeekSession();
            if (event.getRepeatCount() == 0) {
                if (!heldKey.start(event)) return;
                if (pending && !session.equals(current)) cancelPending();
            }
            if (!heldKey.accept(event)) return;
            if (event.isCanceled()) {
                cancelSeek();
            } else if (!listener.canSeek() || current == null || PlaybackTrace.NONE.equals(current)) {
                cancelPending();
            } else if (pending && !session.equals(current)) {
                cancelSeek();
            } else {
                App.removeCallbacks(commit);
                session = current;
                pending = true;
                offset += KeyUtil.isLeftKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
                listener.onSeeking(offset);
            }
        }

        private void onUp(KeyEvent event) {
            if (!heldKey.accept(event)) {
                // Preserve ordinary Live's unpaired release, not stale HDMI input.
                if (!pending && !isHdmi(event)) listener.onSeekUnavailable(event);
                return;
            }
            heldKey.clear();
            if (event.isCanceled()) {
                cancelSeek();
            } else if (!listener.canSeek()) {
                cancelPending();
                listener.onSeekUnavailable(event);
            } else if (pending && session.equals(listener.getSeekSession())) {
                App.post(commit, 250);
            } else {
                cancelSeek();
            }
        }

        private void commitSeek() {
            if (!pending || !listener.canSeek() || !session.equals(listener.getSeekSession()) || offset == 0) {
                cancelSeek();
                return;
            }
            long time = offset;
            offset = 0;
            pending = false;
            session = null;
            listener.onSeekEnd(time);
        }
    }

    public interface SeekListener {
        boolean canSeek();
        String getSeekSession();
        void onSeeking(long time);
        void onSeekEnd(long time);
        void onSeekCancel();
        default void onSeekUnavailable(KeyEvent event) { }
    }

    public interface Listener extends SeekListener {
        void onSpeedUp();
        void onSpeedEnd();
        void onKeyUp();
        void onKeyDown();
        void onKeyCenter();
        void onSingleTap();
        void onDoubleTap();
    }
}
