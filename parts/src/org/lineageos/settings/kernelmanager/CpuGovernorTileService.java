/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.kernelmanager;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.preference.PreferenceManager;
import org.lineageos.settings.R;

public class CpuGovernorTileService extends TileService {

    private KernelManagerUtils mUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        mUtils = new KernelManagerUtils();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        if (!mUtils.isKernelManagerSupported()) return;

        String currentGov = mUtils.getCurrentGovernor(KernelManagerUtils.CLUSTER_LITTLE);
        String[] available = mUtils.getAvailableGovernors();
        
        // Ciklikus váltás
        String nextGov = available[0];
        for (int i = 0; i < available.length; i++) {
            if (available[i].equals(currentGov)) {
                nextGov = available[(i + 1) % available.length];
                break;
            }
        }
        
        if (mUtils.setGovernor(nextGov)) {
            // Mentés prefs-be
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putString("cpu_governor", nextGov).apply();
            updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (!mUtils.isKernelManagerSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
            return;
        }

        String gov = mUtils.getCurrentGovernor(KernelManagerUtils.CLUSTER_LITTLE);
        tile.setState(Tile.STATE_ACTIVE);
        tile.setLabel("CPU Gov");
        tile.setSubtitle(gov);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_cpu_governor_active));
        tile.updateTile();
    }
}
