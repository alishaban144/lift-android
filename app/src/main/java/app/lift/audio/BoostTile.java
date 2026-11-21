package app.lift.audio;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class BoostTile extends TileService {
    private final Runnable stateListener = this::updateState;

    @Override public void onStartListening() {
        super.onStartListening();
        BoostService.addStateListener(stateListener);
        updateState();
    }

    @Override public void onStopListening() {
        BoostService.removeStateListener(stateListener);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        BoostService.removeStateListener(stateListener);
        super.onDestroy();
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
        tile.setSubtitle(active ? BoostService.gain(this) + "%" : "Off");
        tile.updateTile();
    }
}
