package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class KaraokeResultView extends LinearLayout {

    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_SECONDARY = 0xFFCBD5E1;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int PANEL_STROKE = 0x26FFFFFF;
    private static final int SURFACE = 0x18FFFFFF;
    private static final int SURFACE_STRONG = 0x24FFFFFF;
    private static final int SURFACE_DARK = 0x33111827;

    private MaterialTextView action;
    private boolean leanbackLandscapeExpanded;

    public KaraokeResultView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(isLandscapeLayout() ? 18 : 20), dp(isLandscapeLayout() ? 14 : 18), dp(isLandscapeLayout() ? 18 : 20), dp(isLandscapeLayout() ? 14 : 18));
        setMinimumWidth(dialogWidth());
        setBackground(panelBackground());
        setClipToOutline(false);
    }

    public KaraokeResultView setResult(KaraokeResult result) {
        removeAllViews();
        action = null;
        if (result == null) return this;
        if (isLandscapeLayout()) {
            addLandscapeResult(result);
            return this;
        }
        addHeader(result);
        addHero(result);
        addMetrics(result);
        addStats(result);
        addFootnote(result);
        addAction();
        return this;
    }

    public KaraokeResultView setLeanbackLandscapeExpanded(boolean expanded) {
        leanbackLandscapeExpanded = expanded;
        setMinimumWidth(dialogWidth());
        return this;
    }

    public KaraokeResultView setAction(Runnable runnable) {
        if (action != null) action.setOnClickListener(v -> {
            if (runnable != null) runnable.run();
        });
        return this;
    }

    public void requestActionFocus() {
        if (action != null) action.post(() -> action.requestFocus());
    }

    public int getPreferredDialogWidth() {
        return dialogWidth();
    }

    private void addLandscapeResult(KaraokeResult result) {
        setOrientation(VERTICAL);
        addHeader(result);

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams bodyParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, landscapeBodyHeight());
        bodyParams.topMargin = dp(10);
        addView(body, bodyParams);

        LinearLayout summary = new LinearLayout(getContext());
        summary.setOrientation(VERTICAL);
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        summary.setBackground(cardBackground(SURFACE, PANEL_STROKE));
        body.addView(summary, new LinearLayout.LayoutParams(landscapeSummaryWidth(), ViewGroup.LayoutParams.MATCH_PARENT));
        addLandscapeSummary(summary, result);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        scrollParams.leftMargin = dp(12);
        body.addView(scroll, scrollParams);

        LinearLayout detail = new LinearLayout(getContext());
        detail.setOrientation(VERTICAL);
        scroll.addView(detail, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addMetrics(detail, result, true);
        addStats(detail, result, true);
        addFootnote(detail, result, true);
    }

    private void addLandscapeSummary(LinearLayout parent, KaraokeResult result) {
        ScoreGaugeView gauge = new ScoreGaugeView(getContext());
        gauge.setResult(result.getScorePercent(), result.getGrade(), scoreColor(result.getScorePercent()));
        LinearLayout.LayoutParams gaugeParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        gaugeParams.gravity = Gravity.CENTER_HORIZONTAL;
        parent.addView(gauge, gaugeParams);

        MaterialTextView score = textView(getResources().getString(R.string.player_karaoke_result_score_value, result.getScorePercent(), result.getGrade()), 29, true, TEXT_PRIMARY, Gravity.CENTER);
        score.setIncludeFontPadding(false);
        score.setSingleLine(true);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.topMargin = dp(8);
        parent.addView(score, scoreParams);

        MaterialTextView headline = textView(landscapeResultHeadline(result), 13, false, TEXT_SECONDARY, Gravity.CENTER);
        headline.setSingleLine(true);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = dp(7);
        parent.addView(headline, headlineParams);

        if (result.getScoredLineCount() > 0) {
            MaterialTextView lines = textView(getResources().getString(R.string.player_karaoke_result_line_summary, result.getScoredLineCount(), result.getBestLineScorePercent()), 12, true, TEXT_PRIMARY, Gravity.CENTER);
            lines.setSingleLine(true);
            lines.setEllipsize(TextUtils.TruncateAt.END);
            lines.setPadding(dp(8), dp(5), dp(8), dp(5));
            lines.setBackground(chipBackground(SURFACE_STRONG, 0x30FFFFFF, dp(7)));
            LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linesParams.topMargin = dp(8);
            parent.addView(lines, linesParams);
        }

        View spacer = new View(getContext());
        parent.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addAction(parent);
    }

    private void addHeader(KaraokeResult result) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleGroup = new LinearLayout(getContext());
        titleGroup.setOrientation(VERTICAL);
        row.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialTextView title = textView(getResources().getString(R.string.player_karaoke_result_title), 20, true, TEXT_PRIMARY, Gravity.START);
        title.setIncludeFontPadding(false);
        titleGroup.addView(title, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialTextView mode = textView(resultMode(result), 12, true, modeTextColor(result), Gravity.CENTER);
        mode.setSingleLine(true);
        mode.setPadding(dp(10), dp(5), dp(10), dp(5));
        mode.setBackground(chipBackground(modeFillColor(result), modeStrokeColor(result), dp(8)));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeParams.leftMargin = dp(12);
        row.addView(mode, modeParams);
    }

    private void addHero(KaraokeResult result) {
        LinearLayout hero = new LinearLayout(getContext());
        hero.setOrientation(HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(14), dp(14), dp(14), dp(14));
        hero.setBackground(cardBackground(SURFACE, PANEL_STROKE));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(16);
        addView(hero, heroParams);

        ScoreGaugeView gauge = new ScoreGaugeView(getContext());
        gauge.setResult(result.getScorePercent(), result.getGrade(), scoreColor(result.getScorePercent()));
        hero.addView(gauge, new LinearLayout.LayoutParams(gaugeSize(), gaugeSize()));

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = dp(isWideLayout() ? 18 : 14);
        hero.addView(info, infoParams);

        MaterialTextView score = textView(getResources().getString(R.string.player_karaoke_result_score_value, result.getScorePercent(), result.getGrade()), scoreTextSize(), true, TEXT_PRIMARY, Gravity.START);
        score.setIncludeFontPadding(false);
        score.setSingleLine(true);
        info.addView(score, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialTextView headline = textView(resultHeadline(result), 13, false, TEXT_SECONDARY, Gravity.START);
        headline.setSingleLine(false);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = dp(8);
        info.addView(headline, headlineParams);

        if (result.getScoredLineCount() <= 0) return;
        MaterialTextView lines = textView(getResources().getString(R.string.player_karaoke_result_line_summary, result.getScoredLineCount(), result.getBestLineScorePercent()), 12, true, TEXT_PRIMARY, Gravity.START);
        lines.setSingleLine(true);
        lines.setPadding(dp(10), dp(6), dp(10), dp(6));
        lines.setBackground(chipBackground(SURFACE_STRONG, 0x30FFFFFF, dp(8)));
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linesParams.topMargin = dp(11);
        info.addView(lines, linesParams);
    }

    private void addMetrics(KaraokeResult result) {
        addMetrics(this, result, false);
    }

    private void addMetrics(LinearLayout parent, KaraokeResult result, boolean compact) {
        List<MetricSpec> metrics = metricSpecs(result);
        if (compact) {
            addMetricGrid(parent, metrics);
            return;
        }
        for (int i = 0; i < metrics.size(); i++) {
            MetricSpec metric = metrics.get(i);
            addMetric(parent, metric.label, metric.progress, metric.color, i == 0 ? 12 : 9, false);
        }
    }

    private List<MetricSpec> metricSpecs(KaraokeResult result) {
        List<MetricSpec> metrics = new ArrayList<>();
        metrics.add(new MetricSpec(metricLabel(result), result.getHitPercent(), scoreColor(result.getHitPercent())));
        metrics.add(new MetricSpec(getResources().getString(R.string.player_karaoke_result_voice_coverage), result.getVoicedPercent(), 0xFF38BDF8));
        if (result.isPitchScoring() && result.getBonusPercent() > 0) metrics.add(new MetricSpec(getResources().getString(R.string.player_karaoke_result_bonus), result.getBonusPercent(), 0xFFA78BFA));
        if (result.isPitchScoring()) metrics.add(new MetricSpec(getResources().getString(R.string.player_karaoke_result_perfect), result.getPerfectPercent(), 0xFFFBBF24));
        if (result.isPitchScoring() && result.getVibratoPercent() > 0) metrics.add(new MetricSpec(getResources().getString(R.string.player_karaoke_result_vibrato), result.getVibratoPercent(), 0xFF2DD4BF));
        if (result.getScoredLineCount() > 0) metrics.add(new MetricSpec(getResources().getString(R.string.player_karaoke_result_line_average), result.getAverageLineScorePercent(), scoreColor(result.getAverageLineScorePercent())));
        return metrics;
    }

    private void addMetricGrid(LinearLayout parent, List<MetricSpec> metrics) {
        for (int i = 0; i < metrics.size(); i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            rowParams.topMargin = dp(i == 0 ? 0 : 8);
            parent.addView(row, rowParams);
            addMetricCell(row, metrics.get(i), 0);
            if (i + 1 < metrics.size()) addMetricCell(row, metrics.get(i + 1), dp(8));
            else row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void addMetricCell(LinearLayout row, MetricSpec metric, int leftMargin) {
        MetricBarView bar = new MetricBarView(getContext());
        bar.setState(metric.label, metric.progress, metric.color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = leftMargin;
        row.addView(bar, params);
    }

    private void addMetric(LinearLayout parent, String label, int progress, int color, int topMargin, boolean compact) {
        MetricBarView bar = new MetricBarView(getContext());
        bar.setState(label, progress, color);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 46 : 52));
        params.topMargin = dp(topMargin);
        parent.addView(bar, params);
    }

    private static class MetricSpec {
        private final String label;
        private final int progress;
        private final int color;

        private MetricSpec(String label, int progress, int color) {
            this.label = label;
            this.progress = progress;
            this.color = color;
        }
    }

    private void addStats(KaraokeResult result) {
        addStats(this, result, false);
    }

    private void addStats(LinearLayout parent, KaraokeResult result, boolean compact) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(compact ? 8 : 12);
        parent.addView(row, rowParams);
        addStat(row, getResources().getString(R.string.player_karaoke_result_active_time, result.getTotalSeconds()), 0, compact);
        addStat(row, getResources().getString(R.string.player_karaoke_result_best_combo, result.getBestComboSeconds()), dp(compact ? 8 : 9), compact);
    }

    private void addStat(LinearLayout row, String text, int leftMargin, boolean compact) {
        MaterialTextView view = textView(text, compact ? 13 : 13, true, TEXT_SECONDARY, Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(10), dp(compact ? 7 : 9), dp(10), dp(compact ? 7 : 9));
        view.setBackground(cardBackground(SURFACE_DARK, 0x1EFFFFFF));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = leftMargin;
        row.addView(view, params);
    }

    private void addFootnote(KaraokeResult result) {
        addFootnote(this, result, false);
    }

    private void addFootnote(LinearLayout parent, KaraokeResult result, boolean compact) {
        boolean showTrack = !result.getTrackLabel().isEmpty();
        boolean showRhythm = result.isScoring() && !result.isPitchScoring();
        boolean showFree = !result.isScoring();
        boolean showFun = result.isPitchScoring();
        if (!showTrack && !showRhythm && !showFree && !showFun) return;

        LinearLayout note = new LinearLayout(getContext());
        note.setOrientation(VERTICAL);
        note.setPadding(dp(12), dp(compact ? 8 : 10), dp(12), dp(compact ? 8 : 10));
        note.setBackground(cardBackground(0x1014B8A6, 0x2138BDF8));
        LayoutParams noteParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(compact ? 8 : 13);
        parent.addView(note, noteParams);

        if (showTrack) addNoteLine(note, getResources().getString(R.string.player_karaoke_result_track, result.getTrackLabel()), TEXT_SECONDARY);
        if (showFun) addNoteLine(note, getResources().getString(R.string.player_karaoke_result_fun_note), TEXT_MUTED);
        if (showRhythm) addNoteLine(note, getResources().getString(R.string.player_karaoke_result_rhythm_note), TEXT_MUTED);
        else if (showFree) addNoteLine(note, getResources().getString(R.string.player_karaoke_result_no_track), TEXT_MUTED);
    }

    private void addNoteLine(LinearLayout note, String text, int color) {
        MaterialTextView view = textView(text, 12, false, color, Gravity.START);
        view.setSingleLine(false);
        note.addView(view, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addAction() {
        addAction(this);
    }

    private void addAction(LinearLayout parent) {
        action = textView(getResources().getString(R.string.dialog_positive), 15, true, 0xFF06151D, Gravity.CENTER);
        action.setSingleLine(true);
        action.setFocusable(true);
        action.setClickable(true);
        action.setPadding(dp(12), dp(11), dp(12), dp(11));
        updateActionBackground(false);
        action.setOnFocusChangeListener((v, hasFocus) -> updateActionBackground(hasFocus));
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(isLandscapeLayout() ? 10 : 16);
        parent.addView(action, params);
    }

    private void updateActionBackground(boolean focus) {
        if (action == null) return;
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF67E8F9, 0xFF34D399});
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(focus ? 2 : 1), focus ? 0xFFFFFFFF : 0x4434D399);
        action.setBackground(drawable);
        action.setAlpha(focus ? 1.0f : 0.96f);
    }

    private String metricLabel(KaraokeResult result) {
        if (result.isPitchScoring()) return getResources().getString(R.string.player_karaoke_result_metric_hit);
        if (result.isScoring()) return getResources().getString(R.string.player_karaoke_result_metric_rhythm);
        return getResources().getString(R.string.player_karaoke_result_metric_participation);
    }

    private String resultMode(KaraokeResult result) {
        if (result.isPitchScoring()) return getResources().getString(R.string.player_karaoke_result_scoring);
        if (result.isScoring()) return getResources().getString(R.string.player_karaoke_result_rhythm);
        return getResources().getString(R.string.player_karaoke_result_free);
    }

    private String resultHeadline(KaraokeResult result) {
        if (result.isPitchScoring()) {
            if (result.getScorePercent() >= 85) return getResources().getString(R.string.player_karaoke_result_pitch_headline_high);
            if (result.getScorePercent() >= 65) return getResources().getString(R.string.player_karaoke_result_pitch_headline_mid);
            return getResources().getString(R.string.player_karaoke_result_pitch_headline_low);
        }
        if (result.isScoring()) return getResources().getString(R.string.player_karaoke_result_rhythm_note);
        return getResources().getString(R.string.player_karaoke_result_no_track);
    }

    private String landscapeResultHeadline(KaraokeResult result) {
        if (result.isPitchScoring()) {
            if (result.getScorePercent() >= 85) return getResources().getString(R.string.player_karaoke_result_pitch_headline_high_short);
            if (result.getScorePercent() >= 65) return getResources().getString(R.string.player_karaoke_result_pitch_headline_mid_short);
            return getResources().getString(R.string.player_karaoke_result_pitch_headline_low_short);
        }
        if (result.isScoring()) return getResources().getString(R.string.player_karaoke_result_rhythm_note_short);
        return getResources().getString(R.string.player_karaoke_result_no_track_short);
    }

    private MaterialTextView textView(String text, int sizeSp, boolean bold, int color, int gravity) {
        MaterialTextView view = new MaterialTextView(getContext());
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF111827, 0xFF172033, 0xFF0B1120});
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), PANEL_STROKE);
        return drawable;
    }

    private GradientDrawable cardBackground(int color, int stroke) {
        return chipBackground(color, stroke, dp(8));
    }

    private GradientDrawable chipBackground(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int scoreColor(int score) {
        if (score >= 85) return 0xFF34D399;
        if (score >= 70) return 0xFF2DD4BF;
        if (score >= 50) return 0xFFFBBF24;
        return 0xFF38BDF8;
    }

    private int modeTextColor(KaraokeResult result) {
        if (result.isPitchScoring()) return 0xFFA7F3D0;
        if (result.isScoring()) return 0xFFBAE6FD;
        return 0xFFFDE68A;
    }

    private int modeFillColor(KaraokeResult result) {
        if (result.isPitchScoring()) return 0x2634D399;
        if (result.isScoring()) return 0x2638BDF8;
        return 0x26FBBF24;
    }

    private int modeStrokeColor(KaraokeResult result) {
        if (result.isPitchScoring()) return 0x5534D399;
        if (result.isScoring()) return 0x5538BDF8;
        return 0x55FBBF24;
    }

    private int dialogWidth() {
        int screenDp = getResources().getConfiguration().screenWidthDp;
        if (screenDp <= 0) return dp(320);
        if (isLandscapeLayout()) {
            int maxWidthDp = Math.max(360, screenDp - 48);
            int targetDp = leanbackLandscapeExpanded ? Math.min(860, Math.max(640, screenDp - 96)) : Math.min(720, Math.max(480, screenDp - 96));
            return dp(Math.min(maxWidthDp, targetDp));
        }
        int widthDp = Math.min(isWideLayout() ? 480 : 360, Math.max(306, screenDp - 48));
        return dp(widthDp);
    }

    private int landscapeBodyHeight() {
        int screenDp = getResources().getConfiguration().screenHeightDp;
        if (screenDp <= 0) return dp(252);
        if (leanbackLandscapeExpanded) return dp(Math.min(340, Math.max(276, screenDp - 96)));
        return dp(Math.min(276, Math.max(218, screenDp - 142)));
    }

    private int landscapeSummaryWidth() {
        return dp(getResources().getConfiguration().screenWidthDp >= 700 ? 236 : 206);
    }

    private int gaugeSize() {
        return dp(isWideLayout() ? 112 : 96);
    }

    private int scoreTextSize() {
        return isWideLayout() ? 34 : 30;
    }

    private boolean isWideLayout() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.screenWidthDp >= 540 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isLandscapeLayout() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class ScoreGaugeView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int score;
        private String grade = "";
        private int color = 0xFF38BDF8;

        private ScoreGaugeView(Context context) {
            super(context);
        }

        private void setResult(int score, String grade, int color) {
            this.score = Math.max(0, Math.min(100, score));
            this.grade = grade == null ? "" : grade;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = dp(9);
            float inset = stroke / 2f + dp(3);
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(stroke);
            paint.setColor(0x24FFFFFF);
            canvas.drawArc(rect, -218, 256, false, paint);
            paint.setColor(0x33FFFFFF);
            paint.setStrokeWidth(dp(1));
            canvas.drawArc(rect, -218, 256, false, paint);
            paint.setStrokeWidth(stroke);
            paint.setColor(color);
            canvas.drawArc(rect, -218, Math.max(4, score * 256f / 100f), false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(TEXT_PRIMARY);
            paint.setTextSize(dp(isWideLayout() ? 29 : 26));
            canvas.drawText(String.valueOf(score), getWidth() / 2f, getHeight() / 2f + dp(5), paint);

            paint.setTextSize(dp(12));
            paint.setColor(TEXT_MUTED);
            canvas.drawText(grade, getWidth() / 2f, getHeight() / 2f + dp(25), paint);
        }
    }

    private class MetricBarView extends View {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private String label = "";
        private int progress;
        private int color;

        private MetricBarView(Context context) {
            super(context);
        }

        private void setState(String label, int progress, int color) {
            this.label = label == null ? "" : label;
            this.progress = Math.max(0, Math.min(100, progress));
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = dp(8);
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SURFACE);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(PANEL_STROKE);
            canvas.drawRoundRect(rect, radius, radius, paint);

            float left = dp(12);
            float right = getWidth() - dp(12);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(dp(13));
            textPaint.setColor(TEXT_PRIMARY);
            textPaint.setTextAlign(Paint.Align.LEFT);
            String percent = progress + "%";
            float percentWidth = textPaint.measureText(percent);
            CharSequence display = TextUtils.ellipsize(label, textPaint, Math.max(0, right - left - percentWidth - dp(12)), TextUtils.TruncateAt.END);
            canvas.drawText(display.toString(), left, dp(18), textPaint);

            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setColor(TEXT_SECONDARY);
            canvas.drawText(percent, right, dp(18), textPaint);

            float top = dp(31);
            float height = dp(8);
            rect.set(left, top, right, top + height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x2AFFFFFF);
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
            if (progress <= 0) return;
            rect.set(left, top, left + (right - left) * progress / 100f, top + height);
            paint.setColor(color);
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
        }
    }
}
