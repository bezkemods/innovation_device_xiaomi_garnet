package org.lineageos.settings.turbocharging;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class TurboChargingTile extends TileService {

    private static final String PREF_TURBO_ENABLED = "turbo_enable";

    @Override
    public void onClick() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean turboEnabled = prefs.getBoolean(PREF_TURBO_ENABLED, false);
        boolean newState = !turboEnabled;
        prefs.edit().putBoolean(PREF_TURBO_ENABLED, newState).apply();
        TurboChargingUtil.applyTurboAndSportsSettings(this);
        updateTileState();
        Toast.makeText(this,
                newState ? getString(R.string.toast_turbo_on) : getString(R.string.toast_turbo_off),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean turboEnabled = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(PREF_TURBO_ENABLED, false);
        tile.setState(turboEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
