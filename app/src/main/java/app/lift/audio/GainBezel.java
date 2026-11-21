package app.lift.audio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

final class GainBezel extends View {
    interface OnGainChanged { void select(int percent); }

    private static final int MIN = 100;
    private static final int MAX = 400;
    private static final int STEP = 5;
    private static final float START_ANGLE = 140f;
    private static final float SWEEP = 260f;
    private final RectF track = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnGainChanged onGainChanged;
    private final int outline;
    private final int accent;
    private final int label;
    private final int muted;
    private final Vibrator vibrator;
    private int gain = MIN;
    private boolean active;
    private boolean dragging;
    private float dragSweep;
    private float lastAngle;
    private long lastTick;

    GainBezel(Context context, int outline, int accent, int label, int muted, OnGainChanged onGainChanged) {
        super(context);
        this.outline = outline;
        this.accent = accent;
        this.label = label;
        this.muted = muted;
        this.onGainChanged = onGainChanged;
        setContentDescription("Target level");
        setFocusable(true);
        setClickable(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = context.getSystemService(Vibrator.class);
        }
    }

    void update(int percent, boolean enabled) {
        gain = percent;
        active = enabled;
        if (Build.VERSION.SDK_INT >= 30) setStateDescription(gain + " percent");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * .5f;
        float cy = getHeight() * .51f;
        float radius = radius();
        float progress = (gain - MIN) / (float) (MAX - MIN);
        track.set(cx - radius, cy - radius, cx + radius, cy + radius);

        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(accent);
            canvas.drawCircle(cx, cy, radius + dp(14), paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        for (int index = 0; index <= 20; index++) {
            double angle = Math.toRadians(START_ANGLE + SWEEP * index / 20f);
            float x = (float) Math.cos(angle);
            float y = (float) Math.sin(angle);
            float from = radius + dp(index % 5 == 0 ? 8 : 10);
            float to = radius + dp(13);
            paint.setColor(index / 20f <= progress ? muted : outline);
            canvas.drawLine(cx + x * from, cy + y * from, cx + x * to, cy + y * to, paint);
        }
        paint.setStrokeWidth(dp(5));
        paint.setColor(outline);
        canvas.drawArc(track, START_ANGLE, SWEEP, false, paint);
        paint.setColor(active || dragging ? accent : muted);
        canvas.drawArc(track, START_ANGLE, SWEEP * progress, false, paint);

        double mark = Math.toRadians(START_ANGLE + SWEEP * progress);
        float dotX = cx + (float) Math.cos(mark) * radius;
        float dotY = cy + (float) Math.sin(mark) * radius;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        canvas.drawCircle(dotX, dotY, dp(dragging ? 9 : 7), paint);
        paint.setColor(outline);
        canvas.drawCircle(dotX, dotY, dp(2.4f), paint);

        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        paint.setTextSize(radius * .55f);
        paint.setColor(label);
        canvas.drawText(gain + "%", cx, cy + radius * .09f, paint);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(radius * .18f);
        paint.setColor(muted);
        canvas.drawText("TURN TO ADJUST", cx, cy + radius * .48f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - getWidth() * .5f;
        float y = event.getY() - getHeight() * .51f;
        float angle = normalize((float) Math.toDegrees(Math.atan2(y, x)));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(Math.hypot(x, y) - radius()) > dp(25)) return false;
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                float dialAngle = normalize(angle - START_ANGLE);
                dragSweep = dialAngle <= SWEEP ? dialAngle : dialAngle < (SWEEP + 360) / 2 ? SWEEP : 0;
                lastAngle = angle;
                select(gainForSweep(dragSweep), true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                dragSweep = Math.min(SWEEP, Math.max(0, dragSweep + normalize(angle - lastAngle + 180) - 180));
                lastAngle = angle;
                select(gainForSweep(dragSweep), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                invalidate();
                return true;
            default:
                return dragging;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP) {
            select(gain + STEP, true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            select(gain - STEP, true);
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, MIN, MAX, gain));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        if (gain < MAX) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if (gain > MIN) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
            select(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) / STEP) * STEP, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            select(gain + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? STEP : -STEP), true);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private void select(int percent, boolean haptic) {
        int next = Math.min(MAX, Math.max(MIN, percent));
        if (next == gain) return;
        if (haptic) tick(next, next / 50 != gain / 50);
        onGainChanged.select(next);
    }

    private void tick(int percent, boolean accentTick) {
        long now = SystemClock.uptimeMillis();
        if (now - lastTick < 35 || Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return;
        lastTick = now;
        if (vibrator != null && vibrator.hasAmplitudeControl()) {
            int amplitude = 38 + (percent - MIN) * 115 / (MAX - MIN) + (accentTick ? 36 : 0);
            vibrator.vibrate(VibrationEffect.createOneShot(accentTick ? 15 : 8, amplitude));
        } else {
            performHapticFeedback(accentTick ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private float radius() { return Math.min(getWidth() * .34f, getHeight() * .41f); }
    private float dp(float dp) { return dp * getResources().getDisplayMetrics().density; }
    private static float normalize(float angle) { return (angle % 360f + 360f) % 360f; }
    private static int gainForSweep(float angle) { return MIN + Math.round(angle / SWEEP * (MAX - MIN) / STEP) * STEP; }
}
