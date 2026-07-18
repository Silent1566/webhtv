package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsParser;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.fongmi.android.tv.player.lyrics.LyricsWord;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

public class LyricsOverlayView extends FrameLayout {

    private static final int ROWS = 5;
    private static final long WORD_REFRESH_MS = 50;
    private static final long AUDIO_STAGE_WORD_REFRESH_MS = 16;
    private static final long ROW_FADE_DURATION_MS = 160;
    private static final long POSITION_RESET_THRESHOLD_MS = 1200;
    private static final int PRIMARY_COLOR = 0xFFFFC766;

    private final LinearLayout box;
    private final MaterialTextView[] rows = new MaterialTextView[ROWS];
    private final Runnable wordRefresh = this::refreshWordProgress;
    private List<LyricsLine> lines = Collections.emptyList();
    private boolean compact;
    private boolean desktopMode;
    private boolean audioStageMode;
    private boolean suppressed;
    private boolean playing;
    private boolean dragging;
    private int index = -1;
    private int dragBaseIndex = -1;
    private int dragPreviewIndex = -1;
    private float dragStartY;
    private long basePositionMs;
    private long baseRealtimeMs;
    private SeekListener seekListener;

    public interface SeekListener {
        void onLyricsSeek(long positionMs);
    }

    public LyricsOverlayView(Context context) {
        this(context, null);
    }

    public LyricsOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);

        box = new LinearLayout(context);
        box.setGravity(Gravity.CENTER);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));

        for (int i = 0; i < ROWS; i++) {
            rows[i] = textView(context);
            box.addView(rows[i], new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(box, params);
        applyStyle();
    }

    public void setDesktopMode(boolean desktopMode) {
        this.desktopMode = desktopMode;
        setClickable(desktopMode);
        box.setPadding(dp(desktopMode ? 16 : 18), dp(desktopMode ? 8 : 10), dp(desktopMode ? 16 : 18), dp(desktopMode ? 8 : 10));
        box.setBackground(desktopMode ? desktopBackground() : null);
        applyStyle();
    }

    public void setAudioStageMode(boolean audioStageMode) {
        if (this.audioStageMode == audioStageMode) return;
        this.audioStageMode = audioStageMode;
        setClickable(audioStageMode);
        LayoutParams params = (LayoutParams) box.getLayoutParams();
        params.gravity = Gravity.CENTER;
        box.setLayoutParams(params);
        box.setPadding(dp(18), dp(10), dp(18), dp(10));
        applyStyle();
    }

    public void setSeekListener(SeekListener seekListener) {
        this.seekListener = seekListener;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
        if (suppressed) stopWordRefresh();
        if (suppressed) setVisibility(hiddenVisibility());
        else if (!lines.isEmpty()) setVisibility(VISIBLE);
        else setVisibility(hiddenVisibility());
    }

    public void setLyrics(LyricsResult result, List<LyricsLine> lines) {
        stopWordRefresh();
        this.lines = lines == null ? Collections.emptyList() : lines;
        this.index = -1;
        setVisibility(this.lines.isEmpty() || suppressed ? hiddenVisibility() : VISIBLE);
    }

    public void refreshStyle() {
        compact = isCompactHeight(getHeight());
        applyStyle();
        index = -1;
        requestLayout();
        invalidate();
    }

    public void update(long positionMs) {
        update(positionMs, false);
    }

    public void update(long positionMs, boolean playing) {
        if (lines.isEmpty()) {
            clear();
            return;
        }
        if (suppressed) {
            stopWordRefresh();
            setVisibility(hiddenVisibility());
            return;
        }
        long position = Math.max(0, positionMs);
        int debugPreviousIndex = index;
        long previousBase = basePositionMs;
        long previousDisplay = displayPositionMs();
        boolean previousPlaying = this.playing;
        boolean positionReset = isPositionReset(position, playing);
        if (positionReset) {
            stopWordRefresh();
            index = -1;
        }
        this.playing = playing;
        this.basePositionMs = position;
        this.baseRealtimeMs = SystemClock.elapsedRealtime();
        if (dragging) return;
        int nextIndex = LyricsParser.findLine(lines, position);
        if (nextIndex == index) {
            debugUpdate("same", position, previousBase, previousDisplay, previousPlaying, playing, debugPreviousIndex, nextIndex, positionReset);
            renderPrimaryLine(position);
            scheduleWordRefresh(position);
            return;
        }
        int previousIndex = index;
        index = nextIndex;
        debugUpdate("line", position, previousBase, previousDisplay, previousPlaying, playing, debugPreviousIndex, nextIndex, positionReset);
        render(position, shouldAnimateLineChange(previousIndex, nextIndex), Integer.compare(nextIndex, previousIndex));
        scheduleWordRefresh(position);
        setVisibility(VISIBLE);
    }

    private boolean isPositionReset(long positionMs, boolean nextPlaying) {
        if (index < 0 || basePositionMs <= 0) return false;
        long current = playing ? displayPositionMs() : basePositionMs;
        return positionMs + POSITION_RESET_THRESHOLD_MS < current;
    }

    public String debugState() {
        long display = lines.isEmpty() ? basePositionMs : displayPositionMs();
        return "lines=" + lines.size()
                + ",index=" + index
                + ",base=" + basePositionMs
                + ",display=" + display
                + ",playing=" + playing
                + ",dragging=" + dragging
                + ",suppressed=" + suppressed
                + ",visible=" + getVisibility();
    }

    private void debugUpdate(String event, long position, long previousBase, long previousDisplay, boolean previousPlaying, boolean nextPlaying, int previousIndex, int nextIndex, boolean positionReset) {
        if (!SpiderDebug.isEnabled()) return;
        boolean nearStart = position <= 5000;
        boolean backwardPosition = previousBase > 0 && position + POSITION_RESET_THRESHOLD_MS < previousBase;
        boolean backwardDisplay = previousDisplay > 0 && position + POSITION_RESET_THRESHOLD_MS < previousDisplay;
        boolean backwardLine = previousIndex >= 0 && nextIndex >= 0 && nextIndex < previousIndex;
        boolean playingChanged = previousPlaying != nextPlaying;
        boolean nearTail = nextIndex >= 0 && lines.size() > 0 && nextIndex >= lines.size() - 2;
        if (!positionReset && !nearStart && !backwardPosition && !backwardDisplay && !backwardLine && !playingChanged && !nearTail) return;
        SpiderDebug.log("lyrics-loop", "overlay update event=%s pos=%d prevBase=%d prevDisplay=%d prevPlaying=%s nextPlaying=%s prevIndex=%d nextIndex=%d reset=%s nearStart=%s backwardBase=%s backwardDisplay=%s lines=%d",
                event, position, previousBase, previousDisplay, previousPlaying, nextPlaying, previousIndex, nextIndex, positionReset, nearStart, backwardPosition, backwardDisplay, lines.size());
    }

    private void render(long positionMs) {
        render(positionMs, false, 0);
    }

    private void render(long positionMs, boolean animate, int direction) {
        if (desktopMode) {
            renderDesktop(positionMs, animate, direction);
            return;
        }
        int count = visibleRows();
        int center = count / 2;
        int offset = (ROWS - count) / 2;
        for (int i = 0; i < ROWS; i++) {
            MaterialTextView row = rows[i];
            if (i < offset || i >= offset + count) {
                hideRow(row, GONE);
                continue;
            }
            int lineIndex = index + i - offset - center;
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                row.setText("");
                hideRow(row, INVISIBLE);
                continue;
            }
            row.setVisibility(VISIBLE);
            int distance = Math.abs(i - offset - center);
            style(row, distance);
            setRowText(row, lines.get(lineIndex), lineIndex == index, positionMs);
            if (animate) animateRow(row, distance, direction);
        }
    }

    private void renderDesktop(long positionMs, boolean animate, int direction) {
        int count = visibleRows();
        for (int i = 0; i < ROWS; i++) {
            MaterialTextView row = rows[i];
            if (i >= count) {
                hideRow(row, GONE);
                continue;
            }
            int lineIndex = index + i;
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                row.setText("");
                hideRow(row, i == 0 ? INVISIBLE : GONE);
                continue;
            }
            row.setVisibility(VISIBLE);
            style(row, i);
            setRowText(row, lines.get(lineIndex), i == 0, positionMs);
            if (animate) animateRow(row, i, direction);
        }
    }

    public void clear() {
        stopWordRefresh();
        lines = Collections.emptyList();
        index = -1;
        dragging = false;
        dragBaseIndex = -1;
        dragPreviewIndex = -1;
        for (MaterialTextView row : rows) row.setText("");
        setVisibility(hiddenVisibility());
    }

    private int hiddenVisibility() {
        return audioStageMode ? INVISIBLE : GONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!audioStageMode || suppressed || lines.isEmpty() || seekListener == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isAudioStageTouchArea(event.getX(), event.getY())) return false;
                beginLyricsDrag(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateLyricsDrag(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                finishLyricsDrag(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishLyricsDrag(false);
                return true;
            default:
                return true;
        }
    }

    public boolean isAudioStageTouchPoint(float rawX, float rawY) {
        if (!audioStageMode || getVisibility() != VISIBLE) return false;
        Rect rect = new Rect();
        if (!getGlobalVisibleRect(rect) || !rect.contains((int) rawX, (int) rawY)) return false;
        return isAudioStageTouchArea(rawX - rect.left, rawY - rect.top);
    }

    private boolean isAudioStageTouchArea(float x, float y) {
        if (!audioStageMode) return true;
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        int width = Math.min(getWidth(), audioStageTouchWidth());
        int left = (getWidth() - width) / 2;
        return x >= left && x <= left + width && y >= 0 && y <= getHeight();
    }

    private int audioStageTouchWidth() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 720) return dp(340);
        if (widthDp >= 540 || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) return dp(280);
        return dp(300);
    }

    private void beginLyricsDrag(float y) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        stopWordRefresh();
        dragging = true;
        dragStartY = y;
        dragBaseIndex = index >= 0 ? index : LyricsParser.findLine(lines, basePositionMs);
        dragPreviewIndex = clampLineIndex(dragBaseIndex);
    }

    private void updateLyricsDrag(float y) {
        if (!dragging) return;
        int offset = Math.round((dragStartY - y) / Math.max(dp(34), getHeight() / (float) Math.max(3, visibleRows())));
        int next = clampLineIndex(dragBaseIndex + offset);
        if (next == dragPreviewIndex) return;
        dragPreviewIndex = next;
        index = next;
        render(lines.get(next).getTimeMs());
    }

    private void finishLyricsDrag(boolean seek) {
        if (!dragging) return;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        int target = clampLineIndex(dragPreviewIndex);
        dragging = false;
        dragBaseIndex = -1;
        dragPreviewIndex = -1;
        if (seek && target >= 0 && target < lines.size()) {
            long position = lines.get(target).getTimeMs();
            index = target;
            render(position);
            seekListener.onLyricsSeek(position);
        } else if (!lines.isEmpty()) {
            index = -1;
            update(basePositionMs, playing);
        }
    }

    private int clampLineIndex(int value) {
        if (lines.isEmpty()) return -1;
        return Math.max(0, Math.min(lines.size() - 1, value));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        boolean nextCompact = isCompactHeight(h);
        if (compact == nextCompact && w == oldw && h == oldh) return;
        compact = nextCompact;
        applyStyle();
        if (!lines.isEmpty() && index >= 0) {
            long positionMs = displayPositionMs();
            index = -1;
            update(positionMs, playing);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopWordRefresh();
        super.onDetachedFromWindow();
    }

    private MaterialTextView textView(Context context) {
        MaterialTextView view = new MaterialTextView(context);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(2);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(4f, 0, 2f, 0xCC000000);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private void applyStyle() {
        applyBoxPadding();
        if (desktopMode) {
            for (int i = 0; i < ROWS; i++) {
                MaterialTextView row = rows[i];
                row.setVisibility(i < visibleRows() ? row.getVisibility() : GONE);
                style(row, i);
            }
            return;
        }
        int count = visibleRows();
        int offset = (ROWS - count) / 2;
        int center = count / 2;
        for (int i = 0; i < ROWS; i++) {
            MaterialTextView row = rows[i];
            row.setVisibility(i < offset || i >= offset + count ? GONE : row.getVisibility());
            style(row, Math.abs(i - offset - center));
        }
    }

    private void style(MaterialTextView view, int distance) {
        boolean primary = distance == 0;
        int size = lyricTextSize(primary, distance);
        float scale = lyricTextScale();
        int color = primary ? PRIMARY_COLOR : distance == 1 ? Color.WHITE : 0xB8FFFFFF;
        view.setPadding(0, rowVerticalPadding(), 0, rowVerticalPadding());
        view.setTextSize(size * scale);
        view.setTextColor(color);
        view.setAlpha(alphaForDistance(distance));
        view.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        view.setMaxLines(desktopMode || tightAudioRows() || denseLandscapeRows() ? 1 : primary ? 2 : 1);
        view.setMinHeight(rowMinHeight(distance, scale));
    }

    private void setRowText(MaterialTextView row, LyricsLine line, boolean primary, long positionMs) {
        if (!primary || !line.hasWords()) {
            row.setText(line.getText());
            return;
        }
        row.setTextColor(Color.WHITE);
        row.setText(buildKaraokeText(line, positionMs));
    }

    private SpannableString buildKaraokeText(LyricsLine line, long positionMs) {
        String text = line.getText();
        SpannableString span = new SpannableString(text);
        if (!text.isEmpty()) span.setSpan(new ForegroundColorSpan(Color.WHITE), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyKaraokeSpans(span, line, Math.max(0, positionMs - line.getTimeMs()));
        return span;
    }

    private void applyKaraokeSpans(SpannableString span, LyricsLine line, long relativeMs) {
        int cursor = 0;
        String text = line.getText();
        for (LyricsWord word : line.getWords()) {
            String value = word.getText();
            if (TextUtils.isEmpty(value)) continue;
            int start = text.indexOf(value, cursor);
            if (start < 0) start = cursor;
            int stop = Math.min(text.length(), start + value.length());
            if (stop <= start) continue;
            if (relativeMs >= word.getEndOffsetMs()) {
                span.setSpan(new ForegroundColorSpan(PRIMARY_COLOR), start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (relativeMs >= word.getStartOffsetMs()) {
                float progress = word.getDurationMs() <= 0 ? 1f : Math.min(1f, Math.max(0f, (relativeMs - word.getStartOffsetMs()) / (float) word.getDurationMs()));
                span.setSpan(new KaraokeSpan(PRIMARY_COLOR, progress), start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return;
            } else {
                return;
            }
            cursor = stop;
        }
    }

    private void renderPrimaryLine(long positionMs) {
        if (index < 0 || index >= lines.size()) return;
        int primary = primaryRow();
        if (primary < 0 || primary >= ROWS || rows[primary].getVisibility() != VISIBLE) return;
        LyricsLine line = lines.get(index);
        if (!line.hasWords()) return;
        style(rows[primary], 0);
        setRowText(rows[primary], line, true, positionMs);
    }

    private void refreshWordProgress() {
        if (!playing || lines.isEmpty() || index < 0 || index >= lines.size()) return;
        long position = displayPositionMs();
        int nextIndex = LyricsParser.findLine(lines, position);
        if (nextIndex != index) {
            int previousIndex = index;
            index = nextIndex;
            debugRefresh("line", position, previousIndex, nextIndex);
            render(position, shouldAnimateLineChange(previousIndex, nextIndex), Integer.compare(nextIndex, previousIndex));
            scheduleWordRefresh(position);
            return;
        }
        if (lines.get(index).hasWords()) renderPrimaryLine(position);
        scheduleWordRefresh(position);
    }

    private long displayPositionMs() {
        if (!playing) return basePositionMs;
        long elapsed = Math.max(0, SystemClock.elapsedRealtime() - baseRealtimeMs);
        return basePositionMs + elapsed;
    }

    private void scheduleWordRefresh() {
        scheduleWordRefresh(basePositionMs);
    }

    private void scheduleWordRefresh(long positionMs) {
        stopWordRefresh();
        if (!playing || dragging || index < 0 || index >= lines.size()) return;
        long delay = nextRefreshDelay(positionMs);
        if (delay < 0) {
            debugRefresh("stop", positionMs, index, index);
            return;
        }
        App.post(wordRefresh, delay);
    }

    private void debugRefresh(String event, long position, int previousIndex, int nextIndex) {
        if (!SpiderDebug.isEnabled()) return;
        boolean nearStart = position <= 5000;
        boolean backwardLine = previousIndex >= 0 && nextIndex >= 0 && nextIndex < previousIndex;
        boolean nearTail = nextIndex >= 0 && lines.size() > 0 && nextIndex >= lines.size() - 2;
        if (!nearStart && !backwardLine && !nearTail && !"stop".equals(event)) return;
        SpiderDebug.log("lyrics-loop", "overlay refresh event=%s pos=%d prevIndex=%d nextIndex=%d base=%d display=%d playing=%s dragging=%s lines=%d",
                event, position, previousIndex, nextIndex, basePositionMs, displayPositionMs(), playing, dragging, lines.size());
    }

    private long nextRefreshDelay(long positionMs) {
        LyricsLine line = lines.get(index);
        long nextLineDelay = Long.MAX_VALUE;
        if (index + 1 < lines.size()) nextLineDelay = Math.max(1, lines.get(index + 1).getTimeMs() - positionMs);
        if (!line.hasWords()) return nextLineDelay == Long.MAX_VALUE ? -1 : nextLineDelay;
        long interval = audioStageMode ? AUDIO_STAGE_WORD_REFRESH_MS : WORD_REFRESH_MS;
        return nextLineDelay == Long.MAX_VALUE ? interval : Math.min(interval, nextLineDelay);
    }

    private void stopWordRefresh() {
        App.removeCallbacks(wordRefresh);
    }

    private int primaryRow() {
        if (desktopMode) return 0;
        int count = visibleRows();
        return (ROWS - count) / 2 + count / 2;
    }

    private int visibleRows() {
        if (desktopMode) return 2;
        int rows = Math.min(PlayerSetting.getLyricsRows(), ROWS);
        return rows;
    }

    private boolean isCompactHeight(int height) {
        return height > 0 && height < dp(300);
    }

    private boolean tightAudioRows() {
        return audioStageMode && !desktopMode && compact && getHeight() > 0 && getHeight() < dp(130) && visibleRows() >= 4;
    }

    private int lyricTextSize(boolean primary, int distance) {
        if (desktopMode) return primary ? 20 : 14;
        if (landscapeAudioStage()) {
            if (denseLandscapeRows()) return primary ? 24 : distance == 1 ? 16 : 14;
            if (tightAudioRows()) return primary ? 22 : 15;
            if (compact) return primary ? 28 : distance == 1 ? 20 : 18;
            return primary ? 34 : distance == 1 ? 24 : 21;
        }
        if (tightAudioRows()) return primary ? 15 : 10;
        if (compact) return primary ? 18 : 13;
        return primary ? 28 : distance == 1 ? 19 : 16;
    }

    private float lyricTextScale() {
        if (!landscapeAudioStage()) return PlayerSetting.getLyricsTextSizeScale();
        return switch (PlayerSetting.getLyricsTextSizeOption()) {
            case 0 -> 0.78f;
            case 1 -> 0.85f;
            case 2 -> 1.0f;
            case 3 -> 1.15f;
            default -> 0.85f;
        } * (denseLandscapeRows() ? 0.94f : 1f);
    }

    private int rowVerticalPadding() {
        if (tightAudioRows()) return 0;
        return dp(compact && visibleRows() >= 4 ? 1 : 3);
    }

    private int rowMinHeight(int distance, float scale) {
        boolean primary = distance == 0;
        int baseDp = desktopMode ? primary ? 32 : 24 : landscapeAudioStage() ? landscapeRowMinHeight(primary) : tightAudioRows() ? primary ? 24 : 14 : compact ? primary ? 44 : 24 : primary ? 62 : 34;
        int base = dp(baseDp * scale);
        if (!audioStageMode || getHeight() <= 0) return base;
        int count = visibleRows();
        int available = getHeight() - box.getPaddingTop() - box.getPaddingBottom();
        if (available <= 0) return base;
        float primaryWeight = count == 1 ? 1f : tightAudioRows() || denseLandscapeRows() ? 1.25f : compact ? 1.45f : 1.6f;
        float totalWeight = primaryWeight + Math.max(0, count - 1);
        float weight = primary ? primaryWeight : 1f;
        int min = dp(tightAudioRows() ? primary ? 22 : 12 : denseLandscapeRows() ? primary ? 24 : 14 : compact ? primary ? 30 : 18 : primary ? 42 : 24);
        int budget = Math.max(min, Math.round(available * weight / totalWeight));
        return Math.min(base, budget);
    }

    private int landscapeRowMinHeight(boolean primary) {
        if (tightAudioRows()) return primary ? 32 : 20;
        if (compact) return primary ? 50 : 30;
        return primary ? 68 : 40;
    }

    private boolean landscapeAudioStage() {
        return audioStageMode && !desktopMode && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean denseLandscapeRows() {
        return landscapeAudioStage() && compact && visibleRows() >= 4;
    }

    private float alphaForDistance(int distance) {
        return distance == 0 ? 1f : desktopMode ? 0.72f : distance == 1 ? 0.82f : 0.58f;
    }

    private void applyBoxPadding() {
        if (desktopMode) {
            box.setPadding(dp(16), dp(8), dp(16), dp(8));
        } else if (audioStageMode && tightAudioRows()) {
            box.setPadding(dp(18), dp(2), dp(18), dp(2));
        } else {
            box.setPadding(dp(18), dp(audioStageMode && compact && visibleRows() >= 4 ? 6 : 10), dp(18), dp(audioStageMode && compact && visibleRows() >= 4 ? 6 : 10));
        }
    }

    private boolean shouldAnimateLineChange(int previousIndex, int nextIndex) {
        return !dragging && previousIndex >= 0 && nextIndex >= 0 && previousIndex != nextIndex;
    }

    private void animateRow(MaterialTextView row, int distance, int direction) {
        row.animate().cancel();
        row.setAlpha(0f);
        row.setTranslationY(dp(direction >= 0 ? 6 : -6));
        row.animate()
                .alpha(alphaForDistance(distance))
                .translationY(0f)
                .setDuration(ROW_FADE_DURATION_MS)
                .start();
    }

    private void hideRow(MaterialTextView row, int visibility) {
        row.animate().cancel();
        row.setTranslationY(0f);
        row.setText("");
        row.setVisibility(visibility);
    }

    private GradientDrawable desktopBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xB320232A);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), 0x26FFFFFF);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class KaraokeSpan extends ReplacementSpan {

        private final int activeColor;
        private final float progress;

        private KaraokeSpan(int activeColor, float progress) {
            this.activeColor = activeColor;
            this.progress = Math.min(1f, Math.max(0f, progress));
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            int color = paint.getColor();
            float width = paint.measureText(text, start, end);
            canvas.drawText(text, start, end, x, y, paint);
            if (progress > 0f && width > 0f) {
                int save = canvas.save();
                canvas.clipRect(x, top, x + width * progress, bottom);
                paint.setColor(activeColor);
                canvas.drawText(text, start, end, x, y, paint);
                canvas.restoreToCount(save);
            }
            paint.setColor(color);
        }
    }
}
