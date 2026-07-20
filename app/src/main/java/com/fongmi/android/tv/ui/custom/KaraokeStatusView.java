package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.karaoke.KaraokeNote;
import com.fongmi.android.tv.player.karaoke.KaraokePitch;
import com.fongmi.android.tv.player.karaoke.KaraokePitchSample;
import com.fongmi.android.tv.player.karaoke.KaraokeScoreSnapshot;
import com.fongmi.android.tv.player.karaoke.KaraokeStatus;
import com.fongmi.android.tv.player.karaoke.KaraokeTrack;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class KaraokeStatusView extends LinearLayout {

    private final MaterialTextView title;
    private final MaterialTextView detail;
    private final NoteTimelineView timeline;
    private final VolumeMeterView volume;
    private KaraokeStatus currentStatus = KaraokeStatus.INACTIVE;
    private KaraokeTrack currentTrack;
    private KaraokePitchSample currentSample;
    private KaraokeScoreSnapshot currentSnapshot;
    private boolean spectrumMode;
    private boolean playing;
    private boolean positionSyncActive;
    private boolean lastPositionPlaying;
    private long lastPositionMs = Long.MIN_VALUE;

    public KaraokeStatusView(Context context) {
        this(context, null);
    }

    public KaraokeStatusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(0, dp(2), 0, dp(2));
        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);

        title = textView(context, 13, true);
        detail = textView(context, 12, false);
        detail.setMinHeight(dp(14));
        timeline = new NoteTimelineView(context);
        volume = new VolumeMeterView(context);
        int meterWidth = meterWidth();
        addView(title, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(detail, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams timelineParams = new LayoutParams(meterWidth, timelineHeight());
        timelineParams.topMargin = dp(7);
        addView(timeline, timelineParams);
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        timeline.setPlaying(playing);
    }

    public void syncPosition(long positionMs, boolean playing) {
        if (getVisibility() != VISIBLE || !isShown()) {
            positionSyncActive = false;
            return;
        }
        long position = Math.max(0, positionMs);
        if (!positionSyncActive) {
            positionSyncActive = true;
            lastPositionMs = Long.MIN_VALUE;
        }
        if (lastPositionMs == position && lastPositionPlaying == playing) return;
        lastPositionMs = position;
        lastPositionPlaying = playing;
        setPlaying(playing);
        timeline.syncPosition(position);
    }

    public void setState(KaraokeStatus status, KaraokeTrack track, KaraokePitchSample sample, KaraokeScoreSnapshot snapshot) {
        if (currentTrack != track) lastPositionMs = Long.MIN_VALUE;
        currentStatus = status == null ? KaraokeStatus.INACTIVE : status;
        currentTrack = track;
        currentSample = sample;
        currentSnapshot = snapshot;
        renderState();
    }

    public void setSpectrumMode(boolean enabled) {
        spectrumMode = enabled;
        timeline.setSpectrumMode(enabled);
        renderState();
    }

    private void renderState() {
        if (spectrumMode) {
            title.setVisibility(GONE);
            detail.setVisibility(GONE);
            timeline.setVisibility(VISIBLE);
            setVisibility(VISIBLE);
            return;
        }
        KaraokeStatus status = currentStatus;
        KaraokeTrack track = currentTrack;
        KaraokePitchSample sample = currentSample;
        KaraokeScoreSnapshot snapshot = currentSnapshot;
        if (status == KaraokeStatus.INACTIVE) {
            timeline.setLevel(0);
            timeline.setState(null, null);
            setVisibility(PlayerSetting.isKaraokeMode() ? INVISIBLE : GONE);
            return;
        }
        setVisibility(VISIBLE);
        boolean attention = status == KaraokeStatus.NEED_PERMISSION || status == KaraokeStatus.MIC_UNAVAILABLE;
        title.setText(attention ? getDetail(status, sample, snapshot, track) : "");
        title.setVisibility(attention ? VISIBLE : GONE);
        detail.setVisibility(GONE);
        timeline.setLevel(getVolumeLevel(status, sample));
        timeline.setState(track, snapshot);
        timeline.setVisibility(showMeter(status, track, snapshot) ? VISIBLE : GONE);
    }

    private String getTitle(KaraokeStatus status, KaraokeTrack track, KaraokeScoreSnapshot snapshot) {
        if (status == KaraokeStatus.SCORING) {
            if (track != null && !track.hasPitchRequiredNotes()) {
                if (snapshot != null && snapshot.getTotalWeightMs() > 0) return getResources().getString(R.string.player_karaoke_status_rhythm_score_grade, snapshot.getScorePercent(), snapshot.getGrade());
                return getResources().getString(R.string.player_karaoke_status_rhythm_score, 0);
            }
            if (snapshot != null && snapshot.getTotalWeightMs() > 0) return getResources().getString(R.string.player_karaoke_status_score_grade, snapshot.getScorePercent(), snapshot.getGrade());
            return getResources().getString(R.string.player_karaoke_status_score, 0);
        }
        if (status == KaraokeStatus.FREE_SING && snapshot != null && snapshot.getTotalWeightMs() > 0) return getResources().getString(R.string.player_karaoke_status_free_score_grade, snapshot.getScorePercent(), snapshot.getGrade());
        return getResources().getString(R.string.player_karaoke_status_free);
    }

    private String getDetail(KaraokeStatus status, KaraokePitchSample sample, KaraokeScoreSnapshot snapshot, KaraokeTrack track) {
        if (status == KaraokeStatus.NEED_PERMISSION) return getResources().getString(R.string.player_karaoke_need_permission);
        if (status == KaraokeStatus.MIC_UNAVAILABLE) return getResources().getString(R.string.player_karaoke_mic_unavailable);
        if (sample == null || sample.getTimestampMs() <= 0) return getResources().getString(R.string.player_karaoke_no_voice);
        if (!sample.isVoiced()) return "";
        int midi = KaraokePitch.frequencyToNearestMidi(sample.getFrequencyHz());
        String pitch = KaraokePitch.midiToName(midi);
        if (status == KaraokeStatus.SCORING && track != null && snapshot != null && snapshot.getTargetNote() != null) {
            if (!track.hasPitchRequiredNotes()) return appendStats(getResources().getString(R.string.player_karaoke_rhythm_detail, snapshot.getScorePercent()), snapshot);
            String state = snapshot.isHit()
                    ? getResources().getString(R.string.player_karaoke_pitch_hit)
                    : getResources().getString(R.string.player_karaoke_pitch_miss, Math.abs(snapshot.getDistanceSemitones()));
            return appendStats(getResources().getString(R.string.player_karaoke_pitch_score, pitch, state), snapshot);
        }
        if (status == KaraokeStatus.FREE_SING && snapshot != null && snapshot.getTotalWeightMs() > 0) {
            return appendStats(getResources().getString(R.string.player_karaoke_free_detail, pitch, snapshot.getScorePercent()), snapshot);
        }
        return getResources().getString(R.string.player_karaoke_pitch, pitch);
    }

    private String appendStats(String text, KaraokeScoreSnapshot snapshot) {
        if (snapshot == null) return text;
        String value = text;
        if (snapshot.isPerfect()) value += " · " + getResources().getString(R.string.player_karaoke_pitch_perfect);
        if (snapshot.hasVibrato()) value += " · " + getResources().getString(R.string.player_karaoke_pitch_vibrato);
        if (snapshot.getCurrentLineScorePercent() > 0) value += " · " + getResources().getString(R.string.player_karaoke_line_score, snapshot.getCurrentLineScorePercent());
        if (snapshot.getCurrentComboSeconds() >= 2) value += " · " + getResources().getString(R.string.player_karaoke_combo, snapshot.getCurrentComboSeconds());
        return value;
    }

    private boolean showVolume(KaraokeStatus status) {
        return status == KaraokeStatus.FREE_SING || status == KaraokeStatus.SCORING;
    }

    private boolean showTimeline(KaraokeStatus status, KaraokeTrack track, KaraokeScoreSnapshot snapshot) {
        return status == KaraokeStatus.SCORING
                && track != null
                && track.hasScoredNotes()
                && snapshot != null;
    }

    private boolean showMeter(KaraokeStatus status, KaraokeTrack track, KaraokeScoreSnapshot snapshot) {
        return showVolume(status) || showTimeline(status, track, snapshot);
    }

    private float getVolumeLevel(KaraokeStatus status, KaraokePitchSample sample) {
        if (!showVolume(status) || sample == null || sample.getTimestampMs() <= 0) return 0;
        return (float) Math.max(0, Math.min(1, Math.sqrt(sample.getVolume()) * 2.35f));
    }

    private MaterialTextView textView(Context context, int sizeSp, boolean bold) {
        MaterialTextView view = new MaterialTextView(context);
        view.setSingleLine(true);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sizeSp);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int meterWidth() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 720) return dp(340);
        if (widthDp >= 540 || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) return dp(280);
        return dp(300);
    }

    private int timelineHeight() {
        return dp(isWideLayout() ? 86 : 74);
    }

    private boolean isWideLayout() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return widthDp >= 540 || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private class NoteTimelineView extends View {

        private static final long WINDOW_BEFORE_MS = 2200;
        private static final long WINDOW_AFTER_MS = 4200;
        private static final long POSITION_BACKWARD_SEEK_MS = 800;
        private static final long POSITION_FORWARD_SEEK_MS = 1800;
        private static final long HISTORY_RESET_MS = 2_000;
        private static final long HISTORY_MIN_INTERVAL_MS = 70;
        private static final int HISTORY_COUNT = 72;
        private static final int BAR_COUNT = 24;
        private static final float MIN_PITCH_SPAN = 14f;
        private static final float PITCH_PADDING = 2f;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final long[] historyTime = new long[HISTORY_COUNT];
        private final float[] historyPitch = new float[HISTORY_COUNT];
        private final float[] bars = new float[BAR_COUNT];
        private KaraokeTrack track;
        private KaraokeScoreSnapshot snapshot;
        private PitchScale pitchScale = defaultPitchScale();
        private boolean spectrumMode;
        private int historySize;
        private long lastHistoryPosition = -1;
        private float lastHistoryPitch = Float.NaN;
        private long smoothBasePosition = -1;
        private long smoothBaseRealtime;
        private long renderPosition = -1;
        private long lastSyncedPosition = -1;
        private float level;
        private boolean playing;

        private NoteTimelineView(Context context) {
            super(context);
        }

        private void setState(KaraokeTrack track, KaraokeScoreSnapshot snapshot) {
            if (this.track != track) {
                clearHistory();
                resetTimelineClock();
                pitchScale = pitchScaleFrom(track);
            }
            this.track = track;
            this.snapshot = snapshot;
            updateSmoothBase(snapshot);
            if (playing) appendHistory(snapshot);
            invalidate();
        }

        private void setSpectrumMode(boolean enabled) {
            if (spectrumMode == enabled) return;
            spectrumMode = enabled;
            if (enabled) clearHistory();
            invalidate();
        }

        private void setPlaying(boolean playing) {
            if (this.playing == playing) return;
            long now = SystemClock.elapsedRealtime();
            long position = drawPosition(now);
            this.playing = playing;
            smoothBasePosition = position;
            smoothBaseRealtime = now;
            renderPosition = position;
            invalidate();
        }

        private void setLevel(float level) {
            float next = Math.max(0, Math.min(1, level));
            this.level = this.level * 0.55f + next * 0.45f;
            System.arraycopy(bars, 1, bars, 0, bars.length - 1);
            bars[bars.length - 1] = this.level;
            invalidate();
        }

        private void syncPosition(long positionMs) {
            long syncedPosition = Math.max(0, positionMs);
            long position = syncedPosition;
            long now = SystemClock.elapsedRealtime();
            boolean resetHistory = false;
            long previousSynced = lastSyncedPosition;
            long previousSmooth = smoothBasePosition;
            long previousRender = renderPosition;
            if (playing && smoothBasePosition >= 0) {
                long current = drawPosition(now);
                long delta = syncedPosition - current;
                if (delta < -POSITION_BACKWARD_SEEK_MS && isBackwardSeek(syncedPosition)) {
                    renderPosition = position;
                    resetHistory = true;
                } else if (delta > POSITION_FORWARD_SEEK_MS && isForwardSeek(syncedPosition)) {
                    renderPosition = position;
                    resetHistory = true;
                } else if (delta < 0 || delta > POSITION_FORWARD_SEEK_MS) {
                    position = current;
                }
            } else {
                renderPosition = position;
            }
            lastSyncedPosition = syncedPosition;
            smoothBasePosition = position;
            smoothBaseRealtime = now;
            if (resetHistory || (lastHistoryPosition >= 0 && Math.abs(position - lastHistoryPosition) > HISTORY_RESET_MS)) clearHistory();
            debugSyncPosition(syncedPosition, position, previousSynced, previousSmooth, previousRender, resetHistory);
            invalidate();
        }

        private void debugSyncPosition(long syncedPosition, long position, long previousSynced, long previousSmooth, long previousRender, boolean resetHistory) {
            if (!SpiderDebug.isEnabled()) return;
            boolean nearStart = syncedPosition <= 5000;
            boolean backwardSynced = previousSynced >= 0 && syncedPosition + POSITION_BACKWARD_SEEK_MS < previousSynced;
            boolean backwardSmooth = previousSmooth >= 0 && syncedPosition + POSITION_BACKWARD_SEEK_MS < previousSmooth;
            boolean nearTail = snapshot != null && track != null && track.getDurationMs() > 0 && track.getDurationMs() - syncedPosition <= 5000;
            if (!nearStart && !backwardSynced && !backwardSmooth && !resetHistory && !nearTail) return;
            SpiderDebug.log("lyrics-loop", "karaoke.timeline sync input=%d applied=%d prevSynced=%d prevSmooth=%d prevRender=%d resetHistory=%s playing=%s trackDur=%d snapshotPos=%d",
                    syncedPosition, position, previousSynced, previousSmooth, previousRender, resetHistory, playing, track == null ? -1 : track.getDurationMs(), snapshot == null ? -1 : snapshot.getPositionMs());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(2);
            float right = getWidth() - dp(2);
            float top = dp(3);
            float bottom = getHeight() - dp(3);
            if (spectrumMode) {
                drawSpectrum(canvas, left, right, top, bottom);
                return;
            }
            drawBackground(canvas, left, right, top, bottom);
            drawVolumeBackground(canvas, left, right, top, bottom);
            if (track == null || snapshot == null || track.isEmpty()) {
                if (playing) postInvalidateOnAnimation();
                return;
            }
            long position = drawPosition();
            long start = Math.max(0, position - WINDOW_BEFORE_MS);
            long end = position + WINDOW_AFTER_MS;
            boolean pitchTrack = track.hasPitchRequiredNotes();
            drawNotes(canvas, left, right, top, bottom, start, end, position);
            if (pitchTrack) drawHistory(canvas, left, right, top, bottom, start, end);
            drawCursor(canvas, left, right, top, bottom);
            if (pitchTrack) drawSungMarker(canvas, left, right, top, bottom);
            if (pitchTrack) drawHitEffects(canvas, left, right, top, bottom);
            if (playing) postInvalidateOnAnimation();
        }

        private void drawSpectrum(Canvas canvas, float left, float right, float top, float bottom) {
            long now = SystemClock.elapsedRealtime();
            float width = right - left;
            float gap = Math.max(dp(2), width / 118f);
            float barWidth = Math.max(dp(3), (width - gap * (BAR_COUNT - 1)) / BAR_COUNT);
            float base = bottom - dp(4);
            float maxHeight = Math.max(dp(24), bottom - top - dp(8));
            boolean settling = false;
            for (int i = 0; i < BAR_COUNT; i++) {
                float unit = BAR_COUNT <= 1 ? 0f : i / (float) (BAR_COUNT - 1);
                float center = 1f - Math.abs(unit * 2f - 1f);
                float wave = 0.48f
                        + 0.22f * (float) Math.sin(now / 210f + i * 0.73f)
                        + 0.16f * (float) Math.sin(now / 370f - i * 0.41f)
                        + 0.10f * (float) Math.cos(now / 125f + i * 1.31f);
                float target = playing ? clamp((0.18f + wave * 0.58f) * (0.62f + center * 0.38f)) : 0.08f;
                bars[i] = bars[i] * 0.76f + target * 0.24f;
                if (!playing && bars[i] > 0.1f) settling = true;
                float height = Math.max(dp(5), maxHeight * bars[i]);
                float x = left + i * (barWidth + gap);
                rect.set(x, base - height, x + barWidth, base);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(spectrumColor(unit));
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint);
                if (playing && bars[i] > 0.72f && (i + now / 260) % 5 == 0) {
                    paint.setColor(0xCCFFD166);
                    canvas.drawCircle(x + barWidth / 2f, base - height - dp(5), dp(1.5f), paint);
                }
            }
            if (playing) postInvalidateDelayed(42);
            else if (settling) postInvalidateDelayed(48);
        }

        private float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }

        private int spectrumColor(float unit) {
            if (unit < 0.5f) return blendColor(0x52FFFFFF, 0xD8CFEFFF, unit * 2f);
            return blendColor(0xD8CFEFFF, 0x72FFFFFF, (unit - 0.5f) * 2f);
        }

        private int blendColor(int start, int end, float ratio) {
            float value = clamp(ratio);
            int a = (int) (Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * value);
            int r = (int) (Color.red(start) + (Color.red(end) - Color.red(start)) * value);
            int g = (int) (Color.green(start) + (Color.green(end) - Color.green(start)) * value);
            int b = (int) (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * value);
            return Color.argb(a, r, g, b);
        }

        private void drawBackground(Canvas canvas, float left, float right, float top, float bottom) {
            float y = bottom - dp(7);
            rect.set(left, y - dp(1), right, y + dp(1));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x12FFFFFF);
            canvas.drawRoundRect(rect, dp(1), dp(1), paint);
        }

        private void drawNotes(Canvas canvas, float left, float right, float top, float bottom, long start, long end, long position) {
            List<KaraokeNote> notes = track.getNotes();
            float cursorX = cursorX(left, right);
            drawTargetGuide(canvas, notes, left, right, top, bottom, start, end);
            NoteSegment segment = null;
            for (KaraokeNote note : notes) {
                if (!note.isScored() || note.getEndMs() < start || note.getStartMs() > end) continue;
                float x1 = xOf(note.getStartMs(), start, end, left, right);
                float x2 = xOf(note.getEndMs(), start, end, left, right);
                float y = yOf(note.isPitchRequired() ? note.getPitch() : pitchScale.center(), top, bottom);
                NoteSegment next = new NoteSegment(note, Math.max(left, x1), Math.min(right, Math.max(x2, x1 + dp(2))), y);
                if (segment != null && segment.canMerge(next)) {
                    segment.merge(next);
                } else {
                    if (segment != null) drawSegment(canvas, segment, position, cursorX);
                    segment = next;
                }
            }
            if (segment != null) drawSegment(canvas, segment, position, cursorX);
        }

        private void drawTargetGuide(Canvas canvas, List<KaraokeNote> notes, float left, float right, float top, float bottom, long start, long end) {
            if (!track.hasPitchRequiredNotes()) return;
            path.reset();
            boolean started = false;
            KaraokeNote last = null;
            for (KaraokeNote note : notes) {
                if (!note.isScored() || !note.isPitchRequired() || note.getEndMs() < start || note.getStartMs() > end) continue;
                float x1 = xOf(note.getStartMs(), start, end, left, right);
                float x2 = xOf(note.getEndMs(), start, end, left, right);
                float y = yOf(note.getPitch(), top, bottom);
                boolean join = last != null && Math.abs(last.getPitch() - note.getPitch()) <= 1 && note.getStartMs() - last.getEndMs() <= 220;
                if (!started || !join) {
                    path.moveTo(Math.max(left, x1), y);
                    started = true;
                } else {
                    path.lineTo(Math.max(left, x1), y);
                }
                path.lineTo(Math.min(right, Math.max(x2, x1 + dp(2))), y);
                last = note;
            }
            if (path.isEmpty()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2.4f));
            paint.setColor(0x42FFFFFF);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawVolumeBackground(Canvas canvas, float left, float right, float top, float bottom) {
            float width = right - left;
            float gap = Math.max(1f, width / 150f);
            float barWidth = (width - gap * (BAR_COUNT - 1)) / BAR_COUNT;
            float base = bottom - dp(7);
            float maxHeight = Math.max(dp(18), (bottom - top) * 0.48f);
            for (int i = 0; i < BAR_COUNT; i++) {
                float value = Math.max(0.06f + (i % 5) * 0.01f, bars[i]);
                float height = maxHeight * Math.min(1f, 0.18f + value * 0.82f);
                float x = left + i * (barWidth + gap);
                rect.set(x, base - height, x + barWidth, base);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(volumeColor(value, i));
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint);
            }
        }

        private void drawSegment(Canvas canvas, NoteSegment segment, long position, float cursorX) {
            boolean current = segment.contains(position);
            boolean scoredCurrent = segment.contains(snapshot.getTargetNote());
            float h = segment.pitchRequired ? dp(isWideLayout() ? 5 : 4) : dp(isWideLayout() ? 7 : 6);
            float radius = h / 2f;
            if (segment.golden && current) drawGoldenGlow(canvas, segment.x1, segment.x2, segment.y, h);
            if (current) drawCurrentGlow(canvas, segment.x1, segment.x2, segment.y, h, scoredCurrent);
            rect.set(segment.x1, segment.y - h / 2f, segment.x2, segment.y + h / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(segmentColor(segment, position));
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (current) drawProgressFill(canvas, segment, position, h, scoredCurrent, cursorX);
            if (current) drawCurrentStroke(canvas, segment.x1, segment.x2, segment.y, h, scoredCurrent);
        }

        private void drawGoldenGlow(Canvas canvas, float x1, float x2, float y, float h) {
            float inset = dp(2);
            rect.set(x1 - inset, y - h / 2f - inset, x2 + inset, y + h / 2f + inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33FBBF24);
            canvas.drawRoundRect(rect, h, h, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xAAFBBF24);
            canvas.drawRoundRect(rect, h, h, paint);
        }

        private void drawCurrentGlow(Canvas canvas, float x1, float x2, float y, float h, boolean scoredCurrent) {
            float inset = dp(3);
            rect.set(x1 - inset, y - h / 2f - inset, x2 + inset, y + h / 2f + inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(activeColor(scoredCurrent, 0x24FFFFFF, 0x26F97364, 0x20FFFFFF));
            canvas.drawRoundRect(rect, h, h, paint);
        }

        private void drawCurrentStroke(Canvas canvas, float x1, float x2, float y, float h, boolean scoredCurrent) {
            float inset = dp(1);
            rect.set(x1 - inset, y - h / 2f - inset, x2 + inset, y + h / 2f + inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(0xCCFFFFFF);
            canvas.drawRoundRect(rect, h, h, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawProgressFill(Canvas canvas, NoteSegment segment, long position, float h, boolean scoredCurrent, float cursorX) {
            if (segment.endMs <= segment.startMs) return;
            float progress = (position - segment.startMs) / (float) (segment.endMs - segment.startMs);
            progress = Math.max(0f, Math.min(1f, progress));
            float progressX = segment.x1 + (segment.x2 - segment.x1) * progress;
            progressX = Math.min(segment.x2, Math.max(segment.x1 + dp(1.8f), progressX));
            progressX = Math.min(progressX, cursorX);
            rect.set(segment.x1, segment.y - h / 2f, progressX, segment.y + h / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(activeColor(scoredCurrent, 0xFFFFC766, 0xFFF97364, 0xFFFFC766));
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
        }

        private void drawHistory(Canvas canvas, float left, float right, float top, float bottom, long start, long end) {
            path.reset();
            boolean started = false;
            for (int i = 0; i < historySize; i++) {
                long time = historyTime[i];
                if (time < start || time > end) {
                    started = false;
                    continue;
                }
                float x = xOf(time, start, end, left, right);
                float y = yOf(historyPitch[i], top, bottom);
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            if (path.isEmpty()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.2f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xCCFFC766);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCursor(Canvas canvas, float left, float right, float top, float bottom) {
            float x = cursorX(left, right);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99FFFFFF);
            canvas.drawRect(x - dp(0.7f), top, x + dp(0.7f), bottom, paint);
        }

        private float cursorX(float left, float right) {
            return left + (right - left) * WINDOW_BEFORE_MS / (float) (WINDOW_BEFORE_MS + WINDOW_AFTER_MS);
        }

        private void drawSungMarker(Canvas canvas, float left, float right, float top, float bottom) {
            if (!snapshot.isVoiced() || Double.isNaN(snapshot.getSungMidi())) return;
            double midi = snapshot.getSungMidi();
            if (snapshot.getTargetNote() != null && !Double.isNaN(snapshot.getDistanceSemitones())) midi = snapshot.getTargetNote().getPitch() + snapshot.getDistanceSemitones();
            float x = left + (right - left) * WINDOW_BEFORE_MS / (float) (WINDOW_BEFORE_MS + WINDOW_AFTER_MS);
            float y = yOf((float) midi, top, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(snapshot.isHit() ? 0xFFFFC766 : 0xFFF97364);
            canvas.drawCircle(x, y, dp(3.6f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xD9000000);
            canvas.drawCircle(x, y, dp(3.6f), paint);
        }

        private void drawHitEffects(Canvas canvas, float left, float right, float top, float bottom) {
            KaraokeNote note = snapshot.getTargetNote();
            if (note == null || (!snapshot.isHit() && !snapshot.isPerfect() && !snapshot.hasVibrato())) return;
            float x = left + (right - left) * WINDOW_BEFORE_MS / (float) (WINDOW_BEFORE_MS + WINDOW_AFTER_MS);
            float y = yOf(note.isPitchRequired() ? note.getPitch() : pitchScale.center(), top, bottom);
            long now = SystemClock.elapsedRealtime();
            float phase = (now % 700) / 700f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.1f));
            paint.setColor(snapshot.isPerfect() ? 0xBFFBBF24 : 0x80FFFFFF);
            canvas.drawCircle(x, y, dp(5.5f + phase * 5.5f), paint);
            if (snapshot.isPerfect() || snapshot.hasVibrato()) drawSparkles(canvas, x, y, now, snapshot.hasVibrato());
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawSparkles(Canvas canvas, float x, float y, long now, boolean vibrato) {
            int count = vibrato ? 7 : 5;
            float phase = (now % 900) / 900f;
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * (i / (double) count + phase * (vibrato ? 0.7 : 0.35));
                float radius = dp(vibrato ? 14 : 12) + dp(3) * (float) Math.sin(phase * Math.PI * 2 + i);
                float sx = x + (float) Math.cos(angle) * radius;
                float sy = y + (float) Math.sin(angle) * radius * 0.56f;
                paint.setColor(vibrato && i % 2 == 0 ? 0xCCFFFFFF : 0xDDFBBF24);
                canvas.drawCircle(sx, sy, dp(i % 3 == 0 ? 1.8f : 1.25f), paint);
            }
        }

        private void appendHistory(KaraokeScoreSnapshot snapshot) {
            if (snapshot == null) {
                clearHistory();
                return;
            }
            long position = snapshot.getPositionMs();
            if (lastHistoryPosition >= 0) {
                long delta = position - lastHistoryPosition;
                if (delta < -POSITION_BACKWARD_SEEK_MS || delta > HISTORY_RESET_MS) {
                    clearHistory();
                } else if (delta < 0) {
                    return;
                }
            }
            if (!snapshot.isVoiced() || Double.isNaN(snapshot.getSungMidi())) {
                lastHistoryPosition = position;
                return;
            }
            if (lastHistoryPosition >= 0 && position - lastHistoryPosition < HISTORY_MIN_INTERVAL_MS) return;
            float pitch = (float) snapshot.getSungMidi();
            if (snapshot.getTargetNote() != null && !Double.isNaN(snapshot.getDistanceSemitones())) pitch = (float) (snapshot.getTargetNote().getPitch() + snapshot.getDistanceSemitones());
            if (historySize == HISTORY_COUNT) {
                System.arraycopy(historyTime, 1, historyTime, 0, HISTORY_COUNT - 1);
                System.arraycopy(historyPitch, 1, historyPitch, 0, HISTORY_COUNT - 1);
                historySize--;
            }
            historyTime[historySize] = position;
            historyPitch[historySize] = smoothHistoryPitch(pitch);
            historySize++;
            lastHistoryPosition = position;
        }

        private float smoothHistoryPitch(float pitch) {
            if (Float.isNaN(lastHistoryPitch) || Math.abs(pitch - lastHistoryPitch) > 5f) {
                lastHistoryPitch = pitch;
            } else {
                lastHistoryPitch = lastHistoryPitch * 0.72f + pitch * 0.28f;
            }
            return lastHistoryPitch;
        }

        private void clearHistory() {
            historySize = 0;
            lastHistoryPosition = -1;
            lastHistoryPitch = Float.NaN;
        }

        private void resetTimelineClock() {
            smoothBasePosition = -1;
            smoothBaseRealtime = 0;
            renderPosition = -1;
            lastSyncedPosition = -1;
        }

        private void updateSmoothBase(KaraokeScoreSnapshot snapshot) {
            if (snapshot == null || smoothBasePosition >= 0) return;
            long now = SystemClock.elapsedRealtime();
            long position = snapshot.getPositionMs();
            smoothBasePosition = position;
            smoothBaseRealtime = now;
            renderPosition = position;
        }

        private long drawPosition() {
            return drawPosition(SystemClock.elapsedRealtime());
        }

        private long drawPosition(long now) {
            long candidate;
            if (snapshot == null) {
                candidate = 0;
            } else if (!playing) {
                candidate = smoothBasePosition >= 0 ? smoothBasePosition : snapshot.getPositionMs();
            } else if (smoothBasePosition < 0) {
                candidate = snapshot.getPositionMs();
            } else {
                long elapsed = Math.max(0, now - smoothBaseRealtime);
                candidate = smoothBasePosition + elapsed;
            }
            if (!playing) {
                renderPosition = candidate;
                return candidate;
            }
            if (renderPosition < 0 || candidate >= renderPosition) {
                renderPosition = candidate;
            }
            return renderPosition;
        }

        private boolean isBackwardSeek(long position) {
            return lastSyncedPosition < 0 || position < lastSyncedPosition - POSITION_BACKWARD_SEEK_MS;
        }

        private boolean isForwardSeek(long position) {
            return lastSyncedPosition < 0 || position > lastSyncedPosition + POSITION_FORWARD_SEEK_MS;
        }

        private float xOf(long timeMs, long start, long end, float left, float right) {
            float value = (timeMs - start) / (float) Math.max(1, end - start);
            return left + (right - left) * Math.max(0, Math.min(1, value));
        }

        private float yOf(float pitch, float top, float bottom) {
            float innerTop = top + dp(5);
            float innerBottom = bottom - dp(5);
            float value = (pitch - pitchScale.min) / Math.max(1f, pitchScale.max - pitchScale.min);
            value = Math.max(0f, Math.min(1f, value));
            return innerBottom - value * Math.max(1f, innerBottom - innerTop);
        }

        private int segmentColor(NoteSegment segment, long position) {
            if (segment.endMs < position) return 0x33FFFFFF;
            if (segment.contains(position)) return 0xB3FFFFFF;
            return 0x72FFFFFF;
        }

        private int volumeColor(float value, int index) {
            float position = BAR_COUNT <= 1 ? 0f : index / (float) (BAR_COUNT - 1);
            int color = blend(0xFFEAFBFF, 0xFFC7F9E9, position * 0.58f);
            if (value > 0.82f) color = blend(color, 0xFFFFFFFF, 0.18f);
            int alpha = value > 0.82f ? 0x7A : value > 0.45f ? 0x58 : 0x36;
            return withAlpha(color, alpha);
        }

        private int blend(int from, int to, float amount) {
            float ratio = Math.max(0f, Math.min(1f, amount));
            int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * ratio);
            int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * ratio);
            int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio);
            return Color.rgb(r, g, b);
        }

        private int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
        }

        private int activeColor(boolean scoredCurrent, int hit, int miss, int pending) {
            if (!scoredCurrent || !snapshot.isVoiced()) return pending;
            return snapshot.isHit() ? hit : miss;
        }

        private boolean isGolden(KaraokeNote note) {
            return note.getType().getScoreWeight() > 1.0;
        }

        private class NoteSegment {

            private static final long MAX_JOIN_GAP_MS = 240;

            private long startMs;
            private long endMs;
            private final int pitch;
            private final boolean pitchRequired;
            private boolean golden;
            private float x1;
            private float x2;
            private float y;

            private NoteSegment(KaraokeNote note, float x1, float x2, float y) {
                this.startMs = note.getStartMs();
                this.endMs = note.getEndMs();
                this.pitch = note.getPitch();
                this.pitchRequired = note.isPitchRequired();
                this.golden = isGolden(note);
                this.x1 = x1;
                this.x2 = x2;
                this.y = y;
            }

            private boolean canMerge(NoteSegment next) {
                if (next == null || pitchRequired != next.pitchRequired) return false;
                if (pitchRequired && Math.abs(pitch - next.pitch) > 1) return false;
                return next.startMs - endMs <= MAX_JOIN_GAP_MS;
            }

            private void merge(NoteSegment next) {
                endMs = Math.max(endMs, next.endMs);
                x2 = Math.max(x2, next.x2);
                y = (y + next.y) / 2f;
                golden |= next.golden;
            }

            private boolean contains(long position) {
                return position >= startMs && position < endMs;
            }

            private boolean contains(KaraokeNote note) {
                return note != null && note.getStartMs() >= startMs && note.getEndMs() <= endMs;
            }
        }

        private PitchScale defaultPitchScale() {
            return new PitchScale(60f - MIN_PITCH_SPAN / 2f, 60f + MIN_PITCH_SPAN / 2f);
        }

        private PitchScale pitchScaleFrom(KaraokeTrack track) {
            if (track == null || !track.hasPitchRequiredNotes()) return defaultPitchScale();
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (KaraokeNote note : track.getNotes()) {
                if (!note.isScored() || !note.isPitchRequired()) continue;
                min = Math.min(min, note.getPitch());
                max = Math.max(max, note.getPitch());
            }
            if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) return defaultPitchScale();
            float low = min - PITCH_PADDING;
            float high = max + PITCH_PADDING;
            float span = high - low;
            if (span < MIN_PITCH_SPAN) {
                float center = (low + high) / 2f;
                low = center - MIN_PITCH_SPAN / 2f;
                high = center + MIN_PITCH_SPAN / 2f;
            }
            return new PitchScale(low, high);
        }

        private class PitchScale {

            private final float min;
            private final float max;

            private PitchScale(float min, float max) {
                this.min = min;
                this.max = Math.max(min + 1f, max);
            }

            private float center() {
                return (min + max) / 2f;
            }
        }
    }

    private static class VolumeMeterView extends View {

        private static final int BAR_COUNT = 18;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] bars = new float[BAR_COUNT];
        private float level;

        private VolumeMeterView(Context context) {
            super(context);
        }

        private void setLevel(float level) {
            float next = Math.max(0, Math.min(1, level));
            this.level = this.level * 0.55f + next * 0.45f;
            System.arraycopy(bars, 1, bars, 0, bars.length - 1);
            bars[bars.length - 1] = this.level;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float gap = Math.max(1f, getWidth() / 90f);
            float barWidth = (getWidth() - gap * (BAR_COUNT - 1)) / BAR_COUNT;
            float radius = Math.max(1f, barWidth / 2f);
            for (int i = 0; i < BAR_COUNT; i++) drawBar(canvas, i, barWidth, gap, radius);
        }

        private void drawBar(Canvas canvas, int index, float barWidth, float gap, float radius) {
            float value = bars[index];
            float idle = 0.08f + (index % 4) * 0.015f;
            float left = index * (barWidth + gap);
            float idleHeight = getHeight() * idle;
            rect.set(left, getHeight() - idleHeight, left + barWidth, getHeight());
            paint.setColor(0x24FFFFFF);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (value <= 0.02f) return;
            float height = getHeight() * Math.max(idle, value);
            float top = getHeight() - height;
            rect.set(left, top, left + barWidth, getHeight());
            paint.setColor(getActiveColor(value));
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (value <= 0.72f) return;
            float capHeight = Math.min(height * 0.28f, getHeight() * 0.22f);
            rect.set(left, top, left + barWidth, top + capHeight);
            paint.setColor(value > 0.9f ? 0xFFEAFBFF : 0xCCEAFBFF);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        private int getActiveColor(float value) {
            if (value > 0.82f) return 0xDDEAFBFF;
            if (value > 0.45f) return 0xBFD8FFF4;
            return 0x82EAFBFF;
        }
    }
}
