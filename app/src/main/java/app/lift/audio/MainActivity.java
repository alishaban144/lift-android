package app.lift.audio;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 21, 17);
    private static final int SURFACE = Color.rgb(26, 34, 27);
    private static final int LINE = Color.rgb(58, 75, 60);
    private static final int PRIMARY = Color.rgb(202, 253, 136);
    private static final int INK = Color.rgb(238, 247, 233);
    private static final int MUTED = Color.rgb(174, 194, 170);
    private static final int WARM = Color.rgb(255, 204, 154);
    private static final int INSTALL_ACCESS = 2;

    private TextView status;
    private TextView boost;
    private static final int[] LEVELS = {125, 150, 200, 300, 400};
    private final TextView[] presets = new TextView[LEVELS.length];
    private TextView tileHint;
    private GainBezel meter;
    private LinearLayout updateBanner;
    private TextView updateLabel;
    private TextView updateAction;
    private TextView checkUpdates;
    private LiftUpdate.Release availableUpdate;
    private boolean checkingUpdate;
    private boolean downloading;
    private boolean readyToInstall;
    private String downloadedVersion;
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
        addUpdateBanner(column);

        LinearLayout panel = vertical();
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(shape(SURFACE, LINE, 28));
        add(column, panel, 12);
        meter = new GainBezel(this, LINE, PRIMARY, INK, MUTED, this::setGain);
        panel.addView(meter, new LinearLayout.LayoutParams(-1, dp(204)));

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
        add(column, tileHint, 9);
        checkUpdates = action("Check for updates", BACKGROUND, MUTED);
        checkUpdates.setMinHeight(dp(48));
        checkUpdates.setOnClickListener(view -> checkForUpdate(true));
        add(column, checkUpdates, 0);

        setContentView(scroll);
        render(BoostService.gain(this));
        readyToInstall = state != null && state.getBoolean("readyToInstall") && LiftUpdate.apk(this).isFile();
        downloadedVersion = state == null ? null : state.getString("downloadedVersion");
        checkForUpdate(false);
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

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("readyToInstall", readyToInstall);
        state.putString("downloadedVersion", downloadedVersion);
        super.onSaveInstanceState(state);
    }

    private void addUpdateBanner(LinearLayout column) {
        updateBanner = new LinearLayout(this);
        updateBanner.setGravity(Gravity.CENTER_VERTICAL);
        updateBanner.setPadding(dp(12), dp(7), dp(7), dp(7));
        updateBanner.setBackground(shape(Color.rgb(36, 55, 32), Color.rgb(74, 110, 60), 18));
        updateBanner.setVisibility(View.GONE);
        updateLabel = label("", 14, INK, "sans-serif-medium");
        updateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.setMarginEnd(dp(8));
        updateBanner.addView(updateLabel, labelParams);
        updateAction = action("Update", PRIMARY, BACKGROUND);
        updateAction.setMinHeight(dp(48));
        updateAction.setOnClickListener(view -> installOrDownload());
        updateBanner.addView(updateAction, new LinearLayout.LayoutParams(-2, -2));
        add(column, updateBanner, 12);
    }

    private void checkForUpdate(boolean userInitiated) {
        if (checkingUpdate || downloading) return;
        checkingUpdate = true;
        checkUpdates.setEnabled(false);
        var context = getApplicationContext();
        LiftUpdate.IO.execute(() -> {
            try {
                LiftUpdate.Release result = LiftUpdate.check(context, userInitiated);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    checkingUpdate = false;
                    checkUpdates.setEnabled(true);
                    if (result != null && !result.version.equals(downloadedVersion)) {
                        readyToInstall = false;
                    }
                    availableUpdate = result;
                    showUpdateBanner();
                    if (userInitiated && result == null) Toast.makeText(this, "Lift is up to date.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    checkingUpdate = false;
                    checkUpdates.setEnabled(true);
                    if (userInitiated) Toast.makeText(this, "Couldn’t check for updates. Try again.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showUpdateBanner() {
        updateBanner.setVisibility(availableUpdate == null ? View.GONE : View.VISIBLE);
        if (availableUpdate == null) return;
        updateLabel.setText("Lift " + availableUpdate.version + " available");
        updateAction.setText(readyToInstall ? "Install" : "Update");
        updateAction.setEnabled(true);
    }

    private void installOrDownload() {
        if (availableUpdate == null || downloading) return;
        if (readyToInstall && LiftUpdate.apk(this).isFile()) {
            openInstaller();
            return;
        }
        downloading = true;
        updateAction.setEnabled(false);
        checkUpdates.setEnabled(false);
        updateLabel.setText("Downloading update…");
        LiftUpdate.Release release = availableUpdate;
        var context = getApplicationContext();
        LiftUpdate.IO.execute(() -> {
            try {
                LiftUpdate.download(context, release);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    downloading = false;
                    readyToInstall = true;
                    downloadedVersion = release.version;
                    checkUpdates.setEnabled(true);
                    showUpdateBanner();
                    if (hasWindowFocus()) openInstaller();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    downloading = false;
                    checkUpdates.setEnabled(true);
                    showUpdateBanner();
                    updateLabel.setText("Update couldn’t be verified or downloaded.");
                    updateAction.setText("Retry");
                });
            }
        });
    }

    private void openInstaller() {
        if (!LiftUpdate.apk(this).isFile()) return;
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent access = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(access, INSTALL_ACCESS);
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(LiftUpdate.apkUri(this), UpdateFileProvider.APK_MIME);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (ActivityNotFoundException | SecurityException error) {
            updateLabel.setText("Android couldn’t open the installer.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == INSTALL_ACCESS && getPackageManager().canRequestPackageInstalls()) openInstaller();
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
            preset.setOnClickListener(view -> setGain(level));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (index + 1 < end) params.setMarginEnd(dp(8));
            row.addView(preset, params);
            presets[index] = preset;
        }
        add(levels, row, 8);
    }

    private void setGain(int percent) {
        BoostService.setGain(this, percent);
        render(percent);
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
            if (preset.isSelected() == selected) continue;
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

}
