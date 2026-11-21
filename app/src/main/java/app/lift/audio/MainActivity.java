package app.lift.audio;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 21, 17);
    private static final int SURFACE = Color.rgb(26, 34, 27);
    private static final int LINE = Color.rgb(58, 75, 60);
    private static final int PRIMARY = Color.rgb(202, 253, 136);
    private static final int INK = Color.rgb(238, 247, 233);
    private static final int MUTED = Color.rgb(174, 194, 170);
    private static final int WARM = Color.rgb(255, 204, 154);

    private TextView status;
    private TextView boost;
    private static final int[] LEVELS = {125, 150, 200, 300, 400};
    private final TextView[] presets = new TextView[LEVELS.length];
    private TextView tileHint;
    private Meter meter;
    private final Runnable stateListener = () -> render(BoostService.gain(this));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout frame = new LinearLayout(this);
        frame.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(frame);
        LinearLayout column = vertical();
        column.setPadding(dp(24), dp(22), dp(24), dp(28));
        frame.addView(column, new LinearLayout.LayoutParams(-1, -2));
        frame.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int width = Math.min(r - l, dp(520));
            if (width > 0 && column.getLayoutParams().width != width) {
                column.getLayoutParams().width = width;
                column.requestLayout();
            }
        });

        TextView logo = label("Lift", 38, INK, "sans-serif-medium");
        logo.setAccessibilityHeading(true);
        add(column, logo, 1);
        add(column, label("Media boost", 17, MUTED, "sans-serif"), 21);

        LinearLayout panel = vertical();
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(shape(SURFACE, LINE, 28));
        add(column, panel, 12);
        meter = new Meter();
        meter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(meter, new LinearLayout.LayoutParams(-1, dp(177)));

        status = label("", 14, MUTED, "sans-serif");
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(panel, status, 14);
        boost = action("Turn on boost", PRIMARY, BACKGROUND);
        boost.setOnClickListener(view -> toggleBoost());
        add(panel, boost, 0);

        LinearLayout levels = vertical();
        levels.setPadding(dp(17), dp(14), dp(17), dp(14));
        levels.setBackground(shape(SURFACE, LINE, 22));
        add(column, levels, 12);
        TextView gainLabel = label("Target level", 16, INK, "sans-serif-medium");
        gainLabel.setAccessibilityHeading(true);
        add(levels, gainLabel, 9);
        addPresetRow(levels, 0, 3);
        addPresetRow(levels, 3, LEVELS.length);
        add(levels, label("Start low. High volume can damage hearing.", 13, MUTED, "sans-serif"), 0);

        TextView addTile = action(Build.VERSION.SDK_INT >= 33 ? "Add Quick Settings tile" : "How to add the tile",
            SURFACE, INK);
        addTile.setOnClickListener(view -> addTile());
        add(column, addTile, 4);
        tileHint = label("Tap the tile to toggle. Touch and hold to open Lift.", 13, MUTED, "sans-serif");
        tileHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(column, tileHint, 0);

        setContentView(scroll);
        render(BoostService.gain(this));
    }

    @Override protected void onStart() {
        super.onStart();
        BoostService.addStateListener(stateListener);
        render(BoostService.gain(this));
    }

    @Override protected void onStop() {
        BoostService.removeStateListener(stateListener);
        super.onStop();
    }

    private void toggleBoost() {
        boolean enable = !BoostService.isActive();
        if (enable && Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply();
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
            return;
        }
        BoostService.setEnabled(this, enable);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && results.length > 0) BoostService.setEnabled(this, true);
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) {
            tileHint.setText("Open Quick Settings, tap Edit, then drag Media boost into your active tiles.");
            return;
        }
        try {
            StatusBarManager manager = getSystemService(StatusBarManager.class);
            manager.requestAddTileService(new ComponentName(this, BoostTile.class), getString(R.string.tile_label),
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_boost),
                getMainExecutor(), result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                        || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        tileHint.setText("Media boost is in Quick Settings. Touch and hold it to open Lift.");
                    } else {
                        tileHint.setText("You can also add Media boost using Edit in Quick Settings.");
                    }
                });
        } catch (RuntimeException error) {
            tileHint.setText("Open Quick Settings, tap Edit, then add Media boost.");
        }
    }

    private void addPresetRow(LinearLayout levels, int first, int end) {
        LinearLayout row = new LinearLayout(this);
        for (int index = first; index < end; index++) {
            int level = LEVELS[index];
            TextView preset = action(level + "%", SURFACE, INK);
            preset.setMinHeight(dp(48));
            preset.setPadding(dp(4), dp(6), dp(4), dp(6));
            preset.setOnClickListener(view -> {
                BoostService.setGain(this, level);
                render(level);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (index + 1 < end) params.setMarginEnd(dp(8));
            row.addView(preset, params);
            presets[index] = preset;
        }
        add(levels, row, 8);
    }

    private void render(int percent) {
        boolean active = BoostService.isActive();
        String issue = BoostService.problem();
        status.setText(issue != null ? issue : active ? "Boost on" : "Boost off");
        status.setTextColor(issue != null ? WARM : active ? PRIMARY : MUTED);
        boost.setText(active ? "Turn off boost" : "Turn on boost");
        for (int index = 0; index < presets.length; index++) {
            TextView preset = presets[index];
            boolean selected = LEVELS[index] == percent;
            preset.setSelected(selected);
            preset.setTextColor(selected ? BACKGROUND : INK);
            preset.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20789869),
                shape(selected ? PRIMARY : SURFACE, selected ? PRIMARY : LINE, 14), null));
        }
        meter.update(percent, active);
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView label(String text, int size, int color, String font) {
        TextView result = new TextView(this);
        result.setText(text);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setTypeface(Typeface.create(font, Typeface.NORMAL));
        return result;
    }

    private TextView action(String text, int fill, int textColor) {
        TextView button = new android.widget.Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(54));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20789869),
            shape(fill, fill == SURFACE ? LINE : fill, 17), null));
        return button;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void add(LinearLayout parent, View child, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(gap);
        parent.addView(child, params);
    }

    private final class Meter extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private int gain;
        private boolean active;

        Meter() {
            super(MainActivity.this);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void update(int percent, boolean enabled) {
            gain = percent;
            active = enabled;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .5f;
            float cy = getHeight() * .52f;
            float r = Math.min(getWidth() * .40f, getHeight() * .45f);
            track.set(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(6));
            paint.setColor(LINE);
            canvas.drawArc(track, 140, 260, false, paint);
            paint.setColor(active ? PRIMARY : MUTED);
            canvas.drawArc(track, 140, ((gain - 100) / 300f) * 260, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            paint.setTextSize(r * .58f);
            paint.setColor(INK);
            canvas.drawText(gain + "%", cx, cy + r * .13f, paint);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(r * .24f);
            paint.setColor(MUTED);
            canvas.drawText("TARGET", cx, cy + r * .47f, paint);
        }
    }
}
