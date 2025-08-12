/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.util.Log;

import org.lineageos.settings.R;

import java.util.Arrays;
import java.util.List;

public class CpuGovernorTileService extends TileService {
    private static final String TAG = "CpuGovernorTileService";
    
    // Tile states
    private static final int STATE_INACTIVE = Tile.STATE_INACTIVE;
    private static final int STATE_ACTIVE = Tile.STATE_ACTIVE;
    private static final int STATE_UNAVAILABLE = Tile.STATE_UNAVAILABLE;
    
    // Update intervals
    private static final int UPDATE_INTERVAL_FAST = 1000; // 1 second
    private static final int UPDATE_INTERVAL_SLOW = 5000; // 5 seconds
    
    // CPU frequency monitoring
    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;
    private KernelManagerUtils mKernelUtils;
    private SharedPreferences mSharedPrefs;
    
    // Tile customization preferences
    private static final String KEY_TILE_SIZE = "cpu_tile_size";
    private static final String KEY_TILE_STYLE = "cpu_tile_style";
    private static final String KEY_UPDATE_SPEED = "cpu_tile_update_speed";
    private static final String KEY_SHOW_PERCENTAGE = "cpu_tile_show_percentage";
    
    // Tile sizes
    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;
    
    // Tile styles
    public static final int STYLE_SPEEDOMETER = 0;
    public static final int STYLE_BAR_CHART = 1;
    public static final int STYLE_SIMPLE = 2;
    
    // Governor cycling order
    private static final List<String> GOVERNOR_CYCLE = Arrays.asList(
        "schedhorizon", "schedutil", "performance", "powersave", "ondemand", "conservative"
    );
    
    private boolean mIsListening = false;
    private int mCurrentSize = SIZE_MEDIUM;
    private int mCurrentStyle = STYLE_SPEEDOMETER;
    private boolean mShowPercentage = true;
    private int mUpdateSpeed = UPDATE_INTERVAL_FAST;

    @Override
    public void onCreate() {
        super.onCreate();
        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUpdateHandler = new Handler(Looper.getMainLooper());
        
        loadPreferences();
        Log.d(TAG, "CPU Governor Tile Service created");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mIsListening = true;
        
        if (!mKernelUtils.isKernelManagerSupported()) {
            updateTileState(STATE_UNAVAILABLE, getString(R.string.cpu_tile_unavailable), null);
            return;
        }
        
        startPeriodicUpdates();
        updateTile();
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
            // Open kernel manager settings if not supported
            openKernelManagerSettings();
            return;
        }
        
        // Cycle to next governor
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

    private void loadPreferences() {
        mCurrentSize = mSharedPrefs.getInt(KEY_TILE_SIZE, SIZE_MEDIUM);
        mCurrentStyle = mSharedPrefs.getInt(KEY_TILE_STYLE, STYLE_SPEEDOMETER);
        mShowPercentage = mSharedPrefs.getBoolean(KEY_SHOW_PERCENTAGE, true);
        mUpdateSpeed = mSharedPrefs.getInt(KEY_UPDATE_SPEED, UPDATE_INTERVAL_FAST);
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
                    mUpdateHandler.postDelayed(this, mUpdateSpeed);
                }
            }
        };
        
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
                updateTileState(STATE_UNAVAILABLE, getString(R.string.cpu_tile_unavailable), null);
                return;
            }

            String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            CpuStats stats = getCpuStats();
            
            String label = formatGovernorName(currentGovernor);
            String subtitle = formatCpuInfo(stats);
            
            Icon icon = createDynamicIcon(stats, currentGovernor);
            
            int state = STATE_ACTIVE;
            if ("powersave".equals(currentGovernor)) {
                state = STATE_INACTIVE;
            }
            
            updateTileState(state, label, subtitle, icon);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating tile", e);
            updateTileState(STATE_UNAVAILABLE, getString(R.string.cpu_tile_error), null);
        }
    }

    private void updateTileState(int state, String label, String subtitle) {
        updateTileState(state, label, subtitle, null);
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
                // Use default icon based on state
                int iconRes = state == STATE_ACTIVE ? 
                    R.drawable.ic_cpu_governor_active : 
                    R.drawable.ic_cpu_governor_inactive;
                tile.setIcon(Icon.createWithResource(this, iconRes));
            }
            
            tile.updateTile();
        }
    }

    private CpuStats getCpuStats() {
        CpuStats stats = new CpuStats();
        
        // Get frequencies for all cores
        for (int i = 0; i < 8; i++) {
            String freqStr = mKernelUtils.getCurrentCoreFrequency(i);
            boolean isOnline = mKernelUtils.isCoreOnline(i);
            
            long freq = 0;
            try {
                freq = Long.parseLong(freqStr);
            } catch (NumberFormatException e) {
                freq = 0;
            }
            
            stats.coreFrequencies[i] = freq;
            stats.coreOnline[i] = isOnline;
        }
        
        // Calculate cluster stats
        calculateClusterStats(stats, KernelManagerUtils.EFFICIENCY_CLUSTER, new int[]{0, 1, 2, 3});
        calculateClusterStats(stats, KernelManagerUtils.PERFORMANCE_CLUSTER, new int[]{4, 5, 6, 7});
        
        // Calculate overall average
        stats.calculateOverallAverage();
        
        return stats;
    }

    private void calculateClusterStats(CpuStats stats, int cluster, int[] coreIds) {
        long totalFreq = 0;
        int onlineCores = 0;
        long maxFreq = 0;
        
        try {
            String maxFreqStr = mKernelUtils.getCurrentMaxFrequency(cluster);
            maxFreq = Long.parseLong(maxFreqStr);
        } catch (NumberFormatException e) {
            maxFreq = cluster == KernelManagerUtils.EFFICIENCY_CLUSTER ? 1958400 : 2400000;
        }
        
        for (int coreId : coreIds) {
            if (stats.coreOnline[coreId]) {
                totalFreq += stats.coreFrequencies[coreId];
                onlineCores++;
            }
        }
        
        if (cluster == KernelManagerUtils.EFFICIENCY_CLUSTER) {
            stats.efficiencyAvgFreq = onlineCores > 0 ? totalFreq / onlineCores : 0;
            stats.efficiencyMaxFreq = maxFreq;
            stats.efficiencyOnlineCores = onlineCores;
            stats.efficiencyUsagePercent = maxFreq > 0 ? 
                (int) ((stats.efficiencyAvgFreq * 100) / maxFreq) : 0;
        } else {
            stats.performanceAvgFreq = onlineCores > 0 ? totalFreq / onlineCores : 0;
            stats.performanceMaxFreq = maxFreq;
            stats.performanceOnlineCores = onlineCores;
            stats.performanceUsagePercent = maxFreq > 0 ? 
                (int) ((stats.performanceAvgFreq * 100) / maxFreq) : 0;
        }
    }

    private String formatGovernorName(String governor) {
        if (TextUtils.isEmpty(governor)) {
            return getString(R.string.cpu_tile_unknown);
        }
        
        switch (governor) {
            case "schedhorizon":
                return getString(R.string.cpu_tile_governor_schedhorizon);
            case "schedutil":
                return getString(R.string.cpu_tile_governor_schedutil);
            case "performance":
                return getString(R.string.cpu_tile_governor_performance);
            case "powersave":
                return getString(R.string.cpu_tile_governor_powersave);
            case "ondemand":
                return getString(R.string.cpu_tile_governor_ondemand);
            case "conservative":
                return getString(R.string.cpu_tile_governor_conservative);
            default:
                return governor.substring(0, 1).toUpperCase() + governor.substring(1);
        }
    }

    private String formatCpuInfo(CpuStats stats) {
        if (mShowPercentage) {
            return getString(R.string.cpu_tile_usage_percent, stats.overallUsagePercent);
        } else {
            return getString(R.string.cpu_tile_freq_ghz, stats.overallAvgFreq / 1000000.0);
        }
    }

    private void cycleGovernor() {
        try {
            String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            String[] availableGovernors = mKernelUtils.getAvailableGovernors();
            
            // Find current governor in available list
            int currentIndex = -1;
            for (int i = 0; i < availableGovernors.length; i++) {
                if (currentGovernor.equals(availableGovernors[i])) {
                    currentIndex = i;
                    break;
                }
            }
            
            // Cycle to next available governor
            String nextGovernor;
            if (currentIndex >= 0 && currentIndex < availableGovernors.length - 1) {
                nextGovernor = availableGovernors[currentIndex + 1];
            } else {
                nextGovernor = availableGovernors[0]; // Wrap around
            }
            
            // Apply the new governor
            boolean success = mKernelUtils.setGovernor(nextGovernor);
            if (success) {
                // Save to preferences
                SharedPreferences.Editor editor = mSharedPrefs.edit();
                editor.putString("cpu_governor", nextGovernor);
                editor.apply();
                
                // Update tile immediately
                updateTile();
                
                Log.d(TAG, "Governor changed from " + currentGovernor + " to " + nextGovernor);
            } else {
                Log.e(TAG, "Failed to change governor to " + nextGovernor);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cycling governor", e);
        }
    }

    private Icon createDynamicIcon(CpuStats stats, String governor) {
        try {
            int size = getTileIconSize();
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            switch (mCurrentStyle) {
                case STYLE_SPEEDOMETER:
                    drawSpeedometerIcon(canvas, stats, size);
                    break;
                case STYLE_BAR_CHART:
                    drawBarChartIcon(canvas, stats, size);
                    break;
                case STYLE_SIMPLE:
                default:
                    drawSimpleIcon(canvas, stats, governor, size);
                    break;
            }
            
            return Icon.createWithBitmap(bitmap);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating dynamic icon", e);
            return null;
        }
    }

    private int getTileIconSize() {
        switch (mCurrentSize) {
            case SIZE_SMALL:
                return 64;
            case SIZE_LARGE:
                return 128;
            case SIZE_MEDIUM:
            default:
                return 96;
        }
    }

    private void drawSpeedometerIcon(Canvas canvas, CpuStats stats, int size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centerX = size / 2f;
        float centerY = size / 2f;
        float radius = size * 0.35f;
        
        // Draw speedometer background
        paint.setColor(Color.parseColor("#FF424242"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.08f);
        
        RectF oval = new RectF(centerX - radius, centerY - radius, 
                              centerX + radius, centerY + radius);
        canvas.drawArc(oval, 135, 270, false, paint);
        
        // Draw usage arc
        float usageAngle = (stats.overallUsagePercent / 100f) * 270;
        paint.setColor(getUsageColor(stats.overallUsagePercent));
        canvas.drawArc(oval, 135, usageAngle, false, paint);
        
        // Draw center percentage
        if (mShowPercentage) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextSize(size * 0.25f);
            paint.setTextAlign(Paint.Align.CENTER);
            
            String text = stats.overallUsagePercent + "%";
            Rect textBounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), textBounds);
            
            canvas.drawText(text, centerX, centerY + textBounds.height() / 2f, paint);
        }
    }

    private void drawBarChartIcon(Canvas canvas, CpuStats stats, int size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float barWidth = size / 10f;
        float spacing = size / 20f;
        float maxHeight = size * 0.7f;
        float baseY = size * 0.9f;
        
        // Draw 8 bars for CPU cores
        for (int i = 0; i < 8; i++) {
            float x = spacing + i * (barWidth + spacing);
            
            if (!stats.coreOnline[i]) {
                // Offline core - draw gray bar
                paint.setColor(Color.parseColor("#FF616161"));
                float height = maxHeight * 0.1f;
                canvas.drawRect(x, baseY - height, x + barWidth, baseY, paint);
                continue;
            }
            
            // Online core - draw colored bar based on frequency
            long maxFreq = i < 4 ? stats.efficiencyMaxFreq : stats.performanceMaxFreq;
            float usage = maxFreq > 0 ? (float) stats.coreFrequencies[i] / maxFreq : 0;
            float height = maxHeight * Math.max(0.1f, usage);
            
            paint.setColor(getCoreColor(i, usage));
            canvas.drawRect(x, baseY - height, x + barWidth, baseY, paint);
        }
        
        // Draw overall percentage in corner
        if (mShowPercentage) {
            paint.setColor(Color.WHITE);
            paint.setTextSize(size * 0.2f);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(stats.overallUsagePercent + "%", size - spacing, size * 0.25f, paint);
        }
    }

    private void drawSimpleIcon(Canvas canvas, CpuStats stats, String governor, int size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centerX = size / 2f;
        float centerY = size / 2f;
        
        // Draw background circle
        paint.setColor(getGovernorColor(governor));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, size * 0.4f, paint);
        
        // Draw border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.05f);
        canvas.drawCircle(centerX, centerY, size * 0.4f, paint);
        
        // Draw percentage or governor initial
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        
        String text;
        if (mShowPercentage) {
            text = String.valueOf(stats.overallUsagePercent);
            paint.setTextSize(size * 0.3f);
        } else {
            text = governor.substring(0, 1).toUpperCase();
            paint.setTextSize(size * 0.35f);
        }
        
        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);
        canvas.drawText(text, centerX, centerY + textBounds.height() / 2f, paint);
    }

    private int getUsageColor(int usagePercent) {
        if (usagePercent < 30) {
            return Color.parseColor("#FF4CAF50"); // Green
        } else if (usagePercent < 70) {
            return Color.parseColor("#FFFF9800"); // Orange
        } else {
            return Color.parseColor("#FFF44336"); // Red
        }
    }

    private int getCoreColor(int coreIndex, float usage) {
        // Different colors for efficiency (0-3) and performance (4-7) cores
        if (coreIndex < 4) {
            // Efficiency cores - Blue tones
            if (usage < 0.3f) return Color.parseColor("#FF2196F3");
            else if (usage < 0.7f) return Color.parseColor("#FF03A9F4");
            else return Color.parseColor("#FF00BCD4");
        } else {
            // Performance cores - Red tones
            if (usage < 0.3f) return Color.parseColor("#FFFF5722");
            else if (usage < 0.7f) return Color.parseColor("#FFFF9800");
            else return Color.parseColor("#FFF44336");
        }
    }

    private int getGovernorColor(String governor) {
        switch (governor) {
            case "performance":
                return Color.parseColor("#FFF44336");
            case "powersave":
                return Color.parseColor("#FF4CAF50");
            case "schedutil":
                return Color.parseColor("#FF2196F3");
            case "schedhorizon":
                return Color.parseColor("#FF9C27B0");
            case "ondemand":
                return Color.parseColor("#FFFF9800");
            case "conservative":
                return Color.parseColor("#FF607D8B");
            default:
                return Color.parseColor("#FF757575");
        }
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

    // CPU Statistics helper class
    private static class CpuStats {
        long[] coreFrequencies = new long[8];
        boolean[] coreOnline = new boolean[8];
        
        long efficiencyAvgFreq = 0;
        long efficiencyMaxFreq = 0;
        int efficiencyOnlineCores = 0;
        int efficiencyUsagePercent = 0;
        
        long performanceAvgFreq = 0;
        long performanceMaxFreq = 0;
        int performanceOnlineCores = 0;
        int performanceUsagePercent = 0;
        
        long overallAvgFreq = 0;
        int overallUsagePercent = 0;
        
        void calculateOverallAverage() {
            long totalFreq = 0;
            int totalOnline = 0;
            long totalMaxFreq = 0;
            
            // Efficiency cluster contribution
            if (efficiencyOnlineCores > 0) {
                totalFreq += efficiencyAvgFreq * efficiencyOnlineCores;
                totalOnline += efficiencyOnlineCores;
                totalMaxFreq += efficiencyMaxFreq * 4; // 4 efficiency cores
            }
            
            // Performance cluster contribution
            if (performanceOnlineCores > 0) {
                totalFreq += performanceAvgFreq * performanceOnlineCores;
                totalOnline += performanceOnlineCores;
                totalMaxFreq += performanceMaxFreq * 4; // 4 performance cores
            }
            
            if (totalOnline > 0) {
                overallAvgFreq = totalFreq / totalOnline;
                overallUsagePercent = totalMaxFreq > 0 ? 
                    (int) ((totalFreq * 100) / totalMaxFreq) : 0;
            } else {
                overallAvgFreq = 0;
                overallUsagePercent = 0;
            }
            
            // Clamp percentage to 0-100 range
            overallUsagePercent = Math.max(0, Math.min(100, overallUsagePercent));
        }
    }
}
