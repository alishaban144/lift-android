package app.lift.audio;

import android.os.Build;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class BoostTile extends TileService {
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateState(); }
    };
    private boolean listening;

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            IntentFilter filter = new IntentFilter(BoostService.STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            listening = true;
        }
        updateState();
    }

    @Override public void onStopListening() {
        stopListening();
        super.onStopListening();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void stopListening() {
        if (!listening) return;
        unregisterReceiver(receiver);
        listening = false;
    }

    @Override public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::toggle);
        } else {
            toggle();
        }
    }

    private void toggle() {
        BoostService.setEnabled(this, !BoostService.isActive());
        updateState();
    }

    void updateState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = BoostService.isActive();
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(active ? BoostService.gain(this) + "%" : "Off");
        }
        tile.updateTile();
    }
}
