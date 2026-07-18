package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;

import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CustomSeekView extends FrameLayout implements Player.Listener, TimeBar.OnScrubListener {

    public interface SeekListener {
        void onSeekStarted();
    }

    private static final String TAG = "CustomSeekView";
    private static final int MAX_UPDATE_INTERVAL_MS = 1000;
    private static final int MIN_UPDATE_INTERVAL_MS = 200;

    private final StringBuilder timeBuilder = new StringBuilder();
    private final Formatter timeFormatter = new Formatter(timeBuilder, Locale.getDefault());
    private final TextView positionView;
    private final TextView durationView;
    private final DefaultTimeBar timeBar;
    private final Runnable runnable;
    private long currentDuration;
    private long currentPosition;
    private long currentBuffered;
    private boolean scrubbing;
    private boolean attached;
    private Player player;
    private SeekListener seekListener;

    public CustomSeekView(Context context) {
        this(context, null);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_control_seek, this);
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        timeBar = findViewById(R.id.timeBar);
        runnable = this::updateProgress;
        timeBar.addListener(this);
        // Make this view focusable and delegate to timeBar
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        resetView();
    }

    public void setPlayer(Player player) {
        this.player = player;
        player.addListener(this);
        if (attached) updateTimeline();
    }

    public void setSeekListener(SeekListener seekListener) {
        this.seekListener = seekListener;
    }

    private String stringToTime(long time) {
        return Util.getStringForTime(timeBuilder, timeFormatter, time);
    }

    private void updateTimeline() {
        if (!attached || player == null) return;
        long duration = player.getDuration();
        if (duration < 0) duration = 0;
        currentDuration = duration;
        setKeyTimeIncrement(duration);
        timeBar.setDuration(duration);
        durationView.setText(stringToTime(duration));
        updateProgress();
    }

    private void updateProgress() {
        removeCallbacks(runnable);
        if (!attached || player == null) return;
        long position = player.getCurrentPosition();
        long buffered = player.getBufferedPosition();
        long duration = player.getDuration();
        if (duration < 0) duration = 0;
        if (duration != currentDuration) {
            currentDuration = duration;
            setKeyTimeIncrement(duration);
            timeBar.setDuration(duration);
            durationView.setText(stringToTime(duration));
        }
        if (position != currentPosition) {
            currentPosition = position;
            if (!scrubbing) {
                timeBar.setPosition(position);
                positionView.setText(stringToTime(position));
            }
        }
        if (buffered != currentBuffered) {
            currentBuffered = buffered;
            timeBar.setBufferedPosition(buffered);
        }
        if (player.isPlaying()) {
            postDelayed(runnable, delayMs(position));
        } else {
            postDelayed(runnable, MAX_UPDATE_INTERVAL_MS);
        }
    }

    private void resetView() {
        positionView.setText("00:00");
        durationView.setText("00:00");
        timeBar.setPosition(currentPosition = 0);
        timeBar.setDuration(currentDuration = 0);
        timeBar.setBufferedPosition(currentBuffered = 0);
    }

    private void setKeyTimeIncrement(long duration) {
        if (duration > TimeUnit.HOURS.toMillis(3)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(5));
        } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
        } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
        } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
        } else if (duration > 0) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private long delayMs(long position) {
        float speed = player.getPlaybackParameters().speed;
        long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
        long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), mediaTimeUntilNextFullSecondMs);
        long delayMs = (long) (mediaTimeDelayMs / Math.max(speed, 0.1f));
        return Util.constrainValue(delayMs, MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS);
    }

    private void seekToTimeBarPosition(long positionMs) {
        if (seekListener != null) seekListener.onSeekStarted();
        player.seekTo(positionMs);
        updateProgress();
        player.play();
    }

    public void previewSeekPosition(long positionMs) {
        long duration = currentDuration > 0 ? currentDuration : player == null ? 0 : Math.max(0, player.getDuration());
        long position = duration > 0 ? Util.constrainValue(positionMs, 0, duration) : Math.max(0, positionMs);
        removeCallbacks(runnable);
        scrubbing = true;
        timeBar.setPosition(position);
        positionView.setText(stringToTime(position));
    }

    public void commitSeekPreview(long positionMs) {
        long duration = currentDuration > 0 ? currentDuration : player == null ? 0 : Math.max(0, player.getDuration());
        long position = duration > 0 ? Util.constrainValue(positionMs, 0, duration) : Math.max(0, positionMs);
        scrubbing = false;
        timeBar.setPosition(position);
        positionView.setText(stringToTime(position));
        removeCallbacks(runnable);
        postDelayed(runnable, 350);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        // Sync focus navigation from parent to timeBar
        int nextFocusUpId = getNextFocusUpId();
        int nextFocusDownId = getNextFocusDownId();
        Log.d(TAG, "onAttachedToWindow - nextFocusUpId: " + nextFocusUpId + ", nextFocusDownId: " + nextFocusDownId);
        if (nextFocusUpId != NO_ID) {
            timeBar.setNextFocusUpId(nextFocusUpId);
            Log.d(TAG, "Set timeBar nextFocusUpId to: " + nextFocusUpId);
        }
        if (nextFocusDownId != NO_ID) {
            timeBar.setNextFocusDownId(nextFocusDownId);
            Log.d(TAG, "Set timeBar nextFocusDownId to: " + nextFocusDownId);
        }
        updateTimeline();
    }

    @Override
    public boolean requestFocus(int direction, android.graphics.Rect previouslyFocusedRect) {
        Log.d(TAG, "requestFocus called with direction: " + direction);
        // Delegate focus to timeBar
        if (timeBar != null && timeBar.getVisibility() == VISIBLE) {
            return timeBar.requestFocus(direction, previouslyFocusedRect);
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(runnable);
        super.onDetachedFromWindow();
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        resetView();
    }

    @Override
    public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        scrubbing = true;
        positionView.setText(stringToTime(position));
    }

    @Override
    public void onScrubMove(@NonNull TimeBar timeBar, long position) {
        positionView.setText(stringToTime(position));
    }

    @Override
    public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        if (!canceled) seekToTimeBarPosition(position);
    }
}
