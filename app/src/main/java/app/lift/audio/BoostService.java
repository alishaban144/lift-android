package app.lift.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BoostService extends Service {
    private static final String STOP = "app.lift.audio.STOP";
    private static final String CHANNEL = "active_boost";
    private static final int NOTIFICATION_ID = 1;
    private static final Set<Runnable> stateListeners = new LinkedHashSet<>();
    private static boolean active;
    private static String problem;
    private static BoostService running;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateNotification = () ->
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    private AudioEffect effect;

    static boolean isActive() { return active; }
    static String problem() { return problem; }
    static void addStateListener(Runnable listener) { stateListeners.add(listener); }
    static void removeStateListener(Runnable listener) { stateListeners.remove(listener); }

    static int gain(Context context) {
        return Math.max(100, Math.min(400, context.getSharedPreferences("lift", MODE_PRIVATE).getInt("gainPercent", 125)));
    }

    static void setGain(Context context, int gain) {
        context.getSharedPreferences("lift", MODE_PRIVATE).edit().putInt("gainPercent", gain).apply();
        if (active && running != null) running.applyLiveGain();
    }

    @Override public void onCreate() {
        super.onCreate();
        running = this;
    }

    static void setEnabled(Context context, boolean enable) {
        Intent intent = new Intent(context, BoostService.class);
        problem = null;
        try {
            if (enable) context.startForegroundService(intent);
            else context.stopService(intent);
        } catch (RuntimeException error) {
            problem = "Android could not start the boost. Try again from Lift.";
            publishState();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            if (effect == null) {
                effect = createEffect(gain(this));
                effect.setControlStatusListener((changed, controlled) -> {
                    if (!controlled && effect == changed) {
                        problem = "Another audio effect took control of the output.";
                        stopSelf();
                    }
                });
            } else {
                enableEffect(effect, gain(this));
            }
            problem = null;
            active = true;
            publishState();
        } catch (RuntimeException error) {
            problem = "This device or output does not allow media boost.";
            active = false;
            releaseEffect();
            publishState();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void applyLiveGain() {
        try {
            setEffectGain();
            mainHandler.removeCallbacks(updateNotification);
            mainHandler.postDelayed(updateNotification, 120);
            publishState();
        } catch (RuntimeException error) {
            problem = "Android couldn’t change the boost level.";
            stopSelf();
        }
    }

    private void setEffectGain() {
        applyGain(effect, gain(this));
    }

    private static AudioEffect createEffect(int percent) {
        AudioEffect candidate = null;
        try {
            // Separate broadband gain from peak control. Quiet and mid-level
            // material get the selected gain until the peak limiter engages.
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, false, 0, false, 0, true).setPreferredFrameDuration(10f).build();
            candidate = new DynamicsProcessing(0, 0, config);
            enableEffect(candidate, percent);
            Log.i("LiftBoost", "Using DynamicsProcessing on the media mix");
            return candidate;
        } catch (RuntimeException unsupported) {
            if (candidate != null) candidate.release();
            Log.i("LiftBoost", "DynamicsProcessing unavailable on the media mix", unsupported);
        }

        candidate = new LoudnessEnhancer(0);
        try {
            enableEffect(candidate, percent);
            Log.i("LiftBoost", "Using LoudnessEnhancer on the media mix");
            return candidate;
        } catch (RuntimeException unsupported) {
            candidate.release();
            throw unsupported;
        }
    }

    private static void enableEffect(AudioEffect candidate, int percent) {
        if (!candidate.hasControl()) throw new IllegalStateException("Audio effect has no control");
        applyGain(candidate, percent);
        if (candidate.setEnabled(true) != AudioEffect.SUCCESS || !candidate.getEnabled()) {
            throw new IllegalStateException("Audio effect did not enable");
        }
    }

    private static void applyGain(AudioEffect effect, int percent) {
        float gainDb = (float) (20 * Math.log10(percent / 100.0));
        if (effect instanceof DynamicsProcessing dynamics) {
            // Use one linked limiter for all output channels to preserve stereo balance.
            dynamics.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                true, percent > 100, 0, 1.0f, 120.0f, 20.0f, -3.0f, 0.0f));
            dynamics.setInputGainAllChannelsTo(gainDb);
        } else {
            ((LoudnessEnhancer) effect).setTargetGain(Math.round(100 * gainDb));
        }
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Active media boost", NotificationManager.IMPORTANCE_LOW));
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent settings = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BoostService.class).setAction(STOP), flags);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_boost)
            .setContentTitle("Media boost")
            .setContentText("Target level " + gain(this) + "%")
            .setContentIntent(settings)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "Turn off", stop).build())
            .build();
    }

    @Override public void onDestroy() {
        running = null;
        mainHandler.removeCallbacks(updateNotification);
        releaseEffect();
        active = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        publishState();
        super.onDestroy();
    }

    private void releaseEffect() {
        if (effect == null) return;
        AudioEffect previous = effect;
        effect = null;
        previous.release();
    }

    private static void publishState() {
        for (Runnable listener : new ArrayList<>(stateListeners)) listener.run();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
