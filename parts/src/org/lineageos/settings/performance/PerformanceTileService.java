/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.performance;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import org.lineageos.settings.R;

public class PerformanceTileService extends TileService {
    private static final String TAG = "PerformanceTileService";
    private PerformanceUtils mPerformanceUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        mPerformanceUtils = new PerformanceUtils(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        int currentMode = mPerformanceUtils.getCurrentMode();
        int newMode;
        
        // Cycle through modes: Battery Saver -> Balanced -> Performance -> Battery Saver
        switch (currentMode) {
            case PerformanceUtils.MODE_BATTERY_SAVER:
                newMode = PerformanceUtils.MODE_BALANCED;
                break;
            case PerformanceUtils.MODE_BALANCED:
                newMode = PerformanceUtils.MODE_PERFORMANCE;
                break;
            case PerformanceUtils.MODE_PERFORMANCE:
            default:
                newMode = PerformanceUtils.MODE_BATTERY_SAVER;
                break;
        }
        
        mPerformanceUtils.setPerformanceMode(newMode);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null && mPerformanceUtils != null) {
            int currentMode = mPerformanceUtils.getCurrentMode();
            
            switch (currentMode) {
                case PerformanceUtils.MODE_BATTERY_SAVER:
                    tile.setState(Tile.STATE_INACTIVE);
                    tile.setIcon(Icon.createWithResource(this, R.drawable.ic_performance_battery_saver));
                    tile.setSubtitle(getString(R.string.performance_mode_battery_saver));
                    break;
                case PerformanceUtils.MODE_BALANCED:
                    tile.setState(Tile.STATE_INACTIVE);
                    tile.setIcon(Icon.createWithResource(this, R.drawable.ic_performance_balanced));
                    tile.setSubtitle(getString(R.string.performance_mode_balanced));
                    break;
                case PerformanceUtils.MODE_PERFORMANCE:
                    tile.setState(Tile.STATE_ACTIVE);
                    tile.setIcon(Icon.createWithResource(this, R.drawable.ic_performance_performance));
                    tile.setSubtitle(getString(R.string.performance_mode_performance));
                    break;
                default:
                    tile.setState(Tile.STATE_INACTIVE);
                    tile.setIcon(Icon.createWithResource(this, R.drawable.ic_performance_balanced));
                    tile.setSubtitle(getString(R.string.performance_mode_balanced));
                    break;
            }
            
            tile.setLabel(getString(R.string.performance_tile_label));
            tile.updateTile();
        }
    }
}
