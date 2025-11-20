package org.lineageos.settings.thermal;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.graphics.drawable.Icon;
import android.util.Log;
import org.lineageos.settings.R;

public class ThermalTileService extends TileService {
    
    private static final String TAG = "ThermalTileService";
    private static final String THERMAL_ENABLED_KEY = "thermal_enabled";
    private SharedPreferences mSharedPrefs;
    private ThermalUtils mThermalUtils;
    
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            mThermalUtils = ThermalUtils.getInstance(this);
            Log.d(TAG, "ThermalTileService created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        try {
            updateTile();
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartListening", e);
        }
    }

    @Override
    public void onClick() {
        try {
            if (mThermalUtils == null) {
                mThermalUtils = ThermalUtils.getInstance(this);
            }
            
            boolean enabled = mSharedPrefs.getBoolean(THERMAL_ENABLED_KEY, false);
            mThermalUtils.setEnabled(!enabled);
            updateTile();
            Log.d(TAG, "Thermal profiles toggled: " + !enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error in onClick", e);
        }
    }

    private void updateTile() {
        try {
            Tile tile = getQsTile();
            if (tile == null) {
                Log.w(TAG, "QS Tile is null");
                return;
            }
            
            boolean enabled = mSharedPrefs.getBoolean(THERMAL_ENABLED_KEY, false);
            
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            
            // Set icon based on state
            int iconRes = enabled ? R.drawable.ic_thermal_enabled : 
                    R.drawable.ic_thermal_disabled;
            tile.setIcon(Icon.createWithResource(this, iconRes));
            
            // Set label and subtitle
            tile.setLabel(getString(R.string.thermal_tile_label));
            tile.setSubtitle(getString(enabled ? 
                    R.string.thermal_tile_enabled_subtitle : 
                    R.string.thermal_tile_disabled_subtitle));
            
            tile.updateTile();
            Log.d(TAG, "Tile updated - enabled: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error updating tile", e);
        }
    }
}
