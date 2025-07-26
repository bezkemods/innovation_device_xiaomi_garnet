package org.lineageos.settings.turbocharging;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class TurboChargingTile extends TileService {

    @Override
    public void onClick() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs == null) {
                Log.e(TurboChargingConstants.TAG, "SharedPreferences is null");
                return;
            }
            
            boolean turboEnabled = prefs.getBoolean(TurboChargingConstants.PREF_TURBO_ENABLED, false);
            boolean newState = !turboEnabled;
            
            // Save new state
            SharedPreferences.Editor editor = prefs.edit();
            if (editor != null) {
                editor.putBoolean(TurboChargingConstants.PREF_TURBO_ENABLED, newState);
                editor.apply();
                
                // Apply settings
                TurboChargingUtil.applyTurboAndSportsSettings(this);
                
                // Update tile
                updateTileState();
                
                // Show toast
                showToast(newState);
                
                Log.d(TurboChargingConstants.TAG, "Turbo charging toggled to: " + newState);
            }
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error in TurboChargingTile onClick", e);
            showErrorToast();
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
        Log.d(TurboChargingConstants.TAG, "TurboChargingTile started listening");
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TurboChargingConstants.TAG, "TurboChargingTile stopped listening");
    }

    private void updateTileState() {
        try {
            Tile tile = getQsTile();
            if (tile == null) {
                Log.w(TurboChargingConstants.TAG, "QS Tile is null");
                return;
            }
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs == null) {
                Log.e(TurboChargingConstants.TAG, "SharedPreferences is null in updateTileState");
                return;
            }
            
            boolean turboEnabled = prefs.getBoolean(TurboChargingConstants.PREF_TURBO_ENABLED, false);
            
            tile.setState(turboEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.turbo_charge_title));
            tile.setSubtitle(turboEnabled ? "ON" : "OFF");
            tile.updateTile();
            
            Log.d(TurboChargingConstants.TAG, "Tile state updated: " + (turboEnabled ? "ACTIVE" : "INACTIVE"));
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error updating tile state", e);
        }
    }

    private void showToast(boolean enabled) {
        try {
            String message = enabled ? getString(R.string.toast_turbo_on) : getString(R.string.toast_turbo_off);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error showing toast", e);
        }
    }

    private void showErrorToast() {
        try {
            Toast.makeText(this, "Error toggling turbo charging", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error showing error toast", e);
        }
    }
}
