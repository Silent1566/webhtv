package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;

public class MpvConfigEditText extends SafeScrollEditText {

    private final Paint gutterPaint = new Paint();
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int gutterWidth;
    private final int contentInset;

    public MpvConfigEditText(Context context) {
        this(context, null);
    }

    public MpvConfigEditText(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public MpvConfigEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gutterWidth = dp(46);
        contentInset = dp(12);
        initEditor();
    }

    private void initEditor() {
        gutterPaint.setColor(Color.parseColor("#F8F9FA"));
        dividerPaint.setColor(Color.parseColor("#E1E5EA"));
        dividerPaint.setStrokeWidth(dp(1));
        numberPaint.setColor(Color.parseColor("#9AA0A6"));
        numberPaint.setTextAlign(Paint.Align.RIGHT);
        numberPaint.setTextSize(sp(11));
        numberPaint.setTypeface(Typeface.MONOSPACE);
        setTypeface(Typeface.MONOSPACE);
        setGravity(Gravity.TOP | Gravity.START);
        setHorizontallyScrolling(true);
        setPadding(gutterWidth + contentInset, dp(12), dp(18), dp(18));
        setLineSpacing(dp(2), 1f);
        setSelectAllOnFocus(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawGutter(canvas);
        super.onDraw(canvas);
    }

    private void drawGutter(Canvas canvas) {
        int left = getScrollX();
        int top = getScrollY();
        canvas.drawRect(left, top, left + gutterWidth, top + getHeight(), gutterPaint);
        canvas.drawLine(left + gutterWidth, top, left + gutterWidth, top + getHeight(), dividerPaint);
        Layout layout = getLayout();
        if (layout == null) return;
        int first = Math.max(0, layout.getLineForVertical(Math.max(0, getScrollY() - getTotalPaddingTop())));
        int last = Math.min(layout.getLineCount() - 1, layout.getLineForVertical(getScrollY() + getHeight()));
        for (int line = first; line <= last; line++) {
            float baseline = getTotalPaddingTop() + layout.getLineBaseline(line);
            canvas.drawText(String.valueOf(line + 1), left + gutterWidth - dp(9), baseline, numberPaint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
