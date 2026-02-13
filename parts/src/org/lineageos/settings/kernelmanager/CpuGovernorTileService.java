/*
 * Copyright (C) 2025 bezke
 * Optimized for Garnet (Snapdragon 7s Gen 2)
 * Battery-optimized version
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.kernelmanager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.lineageos.settings.R;

public class CpuGovernorTileService extends TileService {
    private static final String TAG = "CpuGovernorTileService";
    
    // Battery-optimized update interval (reduced frequency for battery saving)
    private static final int UPDATE_INTERVAL = 3000; // Increased from 2000 to 3000 ms
    
    private Handler mUpdateHandler;
    private Handler mToastHandler;
    private Runnable mUpdateRunnable;
    private KernelManagerUtils mKernelUtils;
    private SharedPreferences mSharedPrefs;
    
    private boolean mIsListening = false;
    private String mLastKnownGovernor = null; // Cache to reduce unnecessary updates

    @Override
    public void onCreate() {
        super.onCreate();
        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUpdateHandler = new Handler(Looper.getMainLooper());
        mToastHandler = new Handler(Looper.getMainLooper());
        
        Log.d(TAG, "CPU Governor Tile Service created (battery-optimized)");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mIsListening = true;
        
        if (!mKernelUtils.isKernelManagerSupported()) {
            updateTileState(Tile.STATE_UNAVAILABLE, "CPU Unavailable", null, null);
            return;
        }
        
        // Initial update
        updateTile();
        // Start periodic updates only when tile is visible
        startPeriodicUpdates();
        Log.d(TAG, "Started listening for CPU tile updates");
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mIsListening = false;
        stopPeriodicUpdates();
        Log.d(TAG, "Stopped listening for CPU tile updates");
    }

    @Override
    public void onClick() {
        super.onClick();
        
        if (!mKernelUtils.isKernelManagerSupported()) {
            openKernelManagerSettings();
            showToast("Kernel Manager not supported");
            return;
        }
        
        cycleGovernor();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "CPU Governor tile added");
        updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.d(TAG, "CPU Governor tile removed");
        stopPeriodicUpdates();
    }

    private void startPeriodicUpdates() {
        if (mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
        
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsListening) {
                    updateTile();
                    mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
                }
            }
        };
        
        // First update immediately, then periodic
        mUpdateHandler.post(mUpdateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
    }

    private void updateTile() {
        try {
            if (!mKernelUtils.isKernelManagerSupported()) {
                updateTileState(Tile.STATE_UNAVAILABLE, "CPU Unavailable", null, null);
                return;
            }

            String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            
            if (TextUtils.isEmpty(currentGovernor)) {
                updateTileState(Tile.STATE_UNAVAILABLE, "Unknown", null, null);
                return;
            }
            
            // Battery optimization: Only update if governor changed
            if (currentGovernor.equals(mLastKnownGovernor)) {
                return; // Skip update if no change
            }
            
            mLastKnownGovernor = currentGovernor;
            
            String label = formatGovernorName(currentGovernor);
            Icon icon = createGovernorIcon(currentGovernor);
            
            int state = getGovernorState(currentGovernor);
            
            updateTileState(state, label, null, icon);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating tile", e);
            updateTileState(Tile.STATE_UNAVAILABLE, "CPU Error", null, null);
        }
    }

    private int getGovernorState(String governor) {
        // Battery-conscious governors show as inactive
        if ("powersave".equals(governor)) {
            return Tile.STATE_INACTIVE;
        }
        // Performance governor shows as active
        if ("performance".equals(governor)) {
            return Tile.STATE_ACTIVE;
        }
        // Default governors (walt, schedutil) show as inactive (balanced)
        return Tile.STATE_INACTIVE;
    }

    private void updateTileState(int state, String label, String subtitle, Icon icon) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(state);
            tile.setLabel(label);
            
            if (!TextUtils.isEmpty(subtitle)) {
                tile.setSubtitle(subtitle);
            }
            
            if (icon != null) {
                tile.setIcon(icon);
            } else {
                try {
                    int iconRes = state == Tile.STATE_ACTIVE ? 
                        R.drawable.ic_cpu_governor_active : 
                        R.drawable.ic_cpu_governor_inactive;
                    tile.setIcon(Icon.createWithResource(this, iconRes));
                } catch (Exception e) {
                    tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_manage));
                }
            }
            
            tile.updateTile();
        }
    }

    private String formatGovernorName(String governor) {
        if (TextUtils.isEmpty(governor)) {
            return "Unknown";
        }
        
        switch (governor) {
            case "walt":
                return "Walt";
            case "schedhorizon":
                return "SchedHorizon";
            case "schedutil":
                return "Schedutil";
            case "performance":
                return "Performance";
            case "powersave":
                return "Powersave";
            case "ondemand":
                return "OnDemand";
            case "conservative":
                return "Conservative";
            default:
                return governor.substring(0, 1).toUpperCase() + 
                       (governor.length() > 1 ? governor.substring(1) : "");
        }
    }

    private Icon createGovernorIcon(String governor) {
        try {
            int size = 128;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            
            String shortName = getShortGovernorName(governor);
            
            paint.setTextSize(size * 0.25f);
            Rect textBounds = new Rect();
            paint.getTextBounds(shortName, 0, shortName.length(), textBounds);
            
            if (textBounds.width() > size * 0.8f) {
                paint.setTextSize(size * 0.2f);
                paint.getTextBounds(shortName, 0, shortName.length(), textBounds);
            }
            
            float textY = size / 2f + textBounds.height() / 2f;
            canvas.drawText(shortName, size / 2f, textY, paint);
            
            return Icon.createWithBitmap(bitmap);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating governor icon", e);
            return null;
        }
    }

    private String getShortGovernorName(String governor) {
        switch (governor) {
            case "walt":
                return "WALT";
            case "schedhorizon":
                return "SCHD";
            case "schedutil":
                return "UTIL";
            case "performance":
                return "PERF";
            case "powersave":
                return "SAVE";
            case "ondemand":
                return "ONDM";
            case "conservative":
                return "CONS";
            default:
                return governor.length() >= 4 ? 
                    governor.substring(0, 4).toUpperCase() : 
                    governor.toUpperCase();
        }
    }

    private void cycleGovernor() {
        try {
            String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            String[] availableGovernors = mKernelUtils.getAvailableGovernors();
            
            if (availableGovernors == null || availableGovernors.length == 0) {
                showToast("No governors available");
                return;
            }
            
            int currentIndex = -1;
            for (int i = 0; i < availableGovernors.length; i++) {
                if (currentGovernor.equals(availableGovernors[i])) {
                    currentIndex = i;
                    break;
                }
            }
            
            String nextGovernor;
            if (currentIndex >= 0 && currentIndex < availableGovernors.length - 1) {
                nextGovernor = availableGovernors[currentIndex + 1];
            } else {
                nextGovernor = availableGovernors[0];
            }
            
            boolean success = mKernelUtils.setGovernor(nextGovernor);
            if (success) {
                SharedPreferences.Editor editor = mSharedPrefs.edit();
                editor.putString("cpu_governor", nextGovernor);
                editor.apply();
                
                // Update cached value immediately
                mLastKnownGovernor = nextGovernor;
                
                String message = "Governor: " + formatGovernorName(nextGovernor);
                showToast(message);
                
                // Force immediate tile update
                mUpdateHandler.postDelayed(this::updateTile, 100);
                
                Log.d(TAG, "Governor changed from " + currentGovernor + " to " + nextGovernor);
            } else {
                Log.e(TAG, "Failed to change governor to " + nextGovernor);
                showToast("Failed to change governor");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cycling governor", e);
            showToast("Error changing governor");
        }
    }

    private void showToast(String message) {
        mToastHandler.post(() -> {
            try {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Toast shown: " + message);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show toast: " + message, e);
            }
        });
    }

    private void openKernelManagerSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(getPackageName(), 
                "org.lineageos.settings.kernelmanager.KernelManagerActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Kernel Manager settings", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPeriodicUpdates();
        mKernelUtils = null;
        Log.d(TAG, "CPU Governor Tile Service destroyed");
    }
}
