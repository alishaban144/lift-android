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
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
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
    private LoudnessEnhancer effect;

    static boolean isActive() { return active; }
    static String problem() { return problem; }
    static void addStateListener(Runnable listener) { stateListeners.add(listener); }
    static void removeStateListener(Runnable listener) { stateListeners.remove(listener); }

    static int gain(Context context) {
        return Math.max(100, Math.min(400, context.getSharedPreferences("lift", MODE_PRIVATE).getInt("gainPercent", 125)));
    }

    static void setGain(Context context, int gain) {
        context.getSharedPreferences("lift", MODE_PRIVATE).edit().putInt("gainPercent", gain).apply();
        if (active) setEnabled(context, true);
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
                // Some Android builds expose the output mix to legacy audio effects.
                // Android reserves the right to restrict this session.
                effect = new LoudnessEnhancer(0);
                effect.setControlStatusListener((changed, controlled) -> {
                    if (!controlled && effect == changed) {
                        problem = "Another audio effect took control of the output.";
                        stopSelf();
                    }
                });
            }
            if (!effect.hasControl()) throw new IllegalStateException("Audio effect has no control");
            effect.setTargetGain((int) Math.round(2_000 * Math.log10(gain(this) / 100.0)));
            int result = effect.setEnabled(true);
            if (result != AudioEffect.SUCCESS || !effect.getEnabled()) {
                throw new IllegalStateException("Audio effect did not enable");
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
        releaseEffect();
        active = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        publishState();
        super.onDestroy();
    }

    private void releaseEffect() {
        if (effect == null) return;
        LoudnessEnhancer previous = effect;
        effect = null;
        previous.release();
    }

    private static void publishState() {
        for (Runnable listener : new ArrayList<>(stateListeners)) listener.run();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
