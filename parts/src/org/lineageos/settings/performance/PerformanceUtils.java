/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.performance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat; // Használd az AndroidX-et
import androidx.preference.PreferenceManager;
import org.lineageos.settings.R;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class PerformanceUtils {

    private static final String TAG = "PerformanceUtils";
    private Context mContext;
    private SharedPreferences mSharedPrefs;
    private NotificationManager mNotificationManager;
    private Vibrator mVibrator;

    // Performance modes
    public static final int MODE_BATTERY_SAVER = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_PERFORMANCE = 2;

    // Notification IDs
    private static final int NOTIFICATION_ID = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "performance_profile_channel";

    // CPU paths for SM7435 (4x A55 + 4x A78)
    private static final String POLICY0_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
    private static final String POLICY4_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";

    // CPU Governors
    private static final String GOV_PERFORMANCE = "performance";
    private static final String GOV_POWERSAVE = "powersave";
    private static final String GOV_DEFAULT = "walt"; // Or schedutil depending on kernel

    // GPU paths (Adreno 710)
    private static final String GPU_BASE = "/sys/class/kgsl/kgsl-3d0";
    private static final String GPU_MIN_FREQ = GPU_BASE + "/devfreq/min_freq";
    private static final String GPU_MAX_FREQ = GPU_BASE + "/devfreq/max_freq";
    private static final String GPU_GOVERNOR = GPU_BASE + "/devfreq/governor";
    private static final String GPU_FORCE_CLK = GPU_BASE + "/force_clk_on";
    private static final String GPU_FORCE_RAIL = GPU_BASE + "/force_rail_on";
    private static final String GPU_PWRLEVEL = GPU_BASE + "/default_pwrlevel";

    // Frequencies (Hz) for Adreno 710
    private static final String FREQ_MIN_HZ = "180000000"; // 180 MHz
    private static final String FREQ_MAX_HZ = "940000000"; // 940 MHz
    
    private static final String PREFS_KEY_CURRENT_MODE = "current_performance_mode";
    private static final String PROP_PERF_MODE = "sys.performance.mode";

    public PerformanceUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        setupNotificationChannel();
    }

    public int getCurrentMode() {
        return mSharedPrefs.getInt(PREFS_KEY_CURRENT_MODE, MODE_BALANCED);
    }

    public String getModeLabel(int mode) {
        switch (mode) {
            case MODE_BATTERY_SAVER: return mContext.getString(R.string.performance_mode_battery_saver);
            case MODE_PERFORMANCE: return mContext.getString(R.string.performance_mode_performance);
            default: return mContext.getString(R.string.performance_mode_balanced);
        }
    }

    public boolean setPerformanceMode(int mode) {
        try {
            if (mVibrator != null && mVibrator.hasVibrator()) {
                mVibrator.vibrate(50);
            }

            boolean success = false;
            switch (mode) {
                case MODE_BATTERY_SAVER:
                    success = applyBatterySaver();
                    break;
                case MODE_BALANCED:
                    success = applyBalanced();
                    break;
                case MODE_PERFORMANCE:
                    success = applyPerformance();
                    break;
            }

            if (success) {
                mSharedPrefs.edit().putInt(PREFS_KEY_CURRENT_MODE, mode).apply();
                SystemProperties.set(PROP_PERF_MODE, String.valueOf(mode));
                updateStatusBarIcon(mode);
                showNotification(mode);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error setting mode: " + mode, e);
            return false;
        }
    }

    private boolean applyBatterySaver() {
        boolean s = true;
        // CPU
        s &= writeFile(POLICY0_GOVERNOR, GOV_POWERSAVE);
        s &= writeFile(POLICY4_GOVERNOR, GOV_POWERSAVE);
        
        // GPU (Low Power)
        s &= writeFile(GPU_GOVERNOR, "powersave");
        s &= writeFile(GPU_MIN_FREQ, FREQ_MIN_HZ);
        s &= writeFile(GPU_MAX_FREQ, "370000000"); // Cap at 370 MHz
        s &= writeFile(GPU_PWRLEVEL, "7"); // Min power level
        s &= writeFile(GPU_FORCE_CLK, "0");
        
        enableSystemBatterySaver(true);
        return s;
    }

    private boolean applyBalanced() {
        boolean s = true;
        // CPU
        s &= writeFile(POLICY0_GOVERNOR, GOV_DEFAULT);
        s &= writeFile(POLICY4_GOVERNOR, GOV_DEFAULT);
        
        // GPU (Default)
        s &= writeFile(GPU_GOVERNOR, "msm-adreno-tz");
        s &= writeFile(GPU_MIN_FREQ, FREQ_MIN_HZ);
        s &= writeFile(GPU_MAX_FREQ, FREQ_MAX_HZ);
        s &= writeFile(GPU_PWRLEVEL, "5"); // Default balanced level
        s &= writeFile(GPU_FORCE_CLK, "0");
        
        enableSystemBatterySaver(false);
        return s;
    }

    private boolean applyPerformance() {
        boolean s = true;
        // CPU
        s &= writeFile(POLICY0_GOVERNOR, GOV_PERFORMANCE);
        s &= writeFile(POLICY4_GOVERNOR, GOV_PERFORMANCE);
        
        // GPU (Max Power)
        s &= writeFile(GPU_GOVERNOR, "performance");
        s &= writeFile(GPU_MIN_FREQ, FREQ_MAX_HZ); // Lock min to max
        s &= writeFile(GPU_MAX_FREQ, FREQ_MAX_HZ);
        s &= writeFile(GPU_PWRLEVEL, "0"); // Max power level
        s &= writeFile(GPU_FORCE_CLK, "1"); // Force clock on
        s &= writeFile(GPU_FORCE_RAIL, "1");
        
        enableSystemBatterySaver(false);
        return s;
    }

    private void enableSystemBatterySaver(boolean enable) {
        try {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode() != enable) {
                Settings.Global.putInt(mContext.getContentResolver(), 
                    Settings.Global.LOW_POWER_MODE, enable ? 1 : 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to toggle system battery saver");
        }
    }

    private void setupNotificationChannel() {
        if (mNotificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                mContext.getString(R.string.performance_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // Low importance to avoid sound/popups
            );
            channel.setDescription(mContext.getString(R.string.performance_notification_channel_desc));
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification(int mode) {
        if (mNotificationManager == null) return;
        
        // Don't show notification for Balanced mode to keep UI clean
        if (mode == MODE_BALANCED) {
            mNotificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        String title = getModeLabel(mode);
        String text = (mode == MODE_BATTERY_SAVER) ? 
            mContext.getString(R.string.performance_notification_battery_saver) :
            mContext.getString(R.string.performance_notification_performance);
            
        int icon = (mode == MODE_BATTERY_SAVER) ? 
            R.drawable.ic_performance_battery_saver : R.drawable.ic_performance_performance;

        Intent intent = new Intent();
        intent.setClassName("org.lineageos.settings", "org.lineageos.settings.xiaomiparts.XiaomiPartsActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void updateStatusBarIcon(int mode) {
        String iconMode = "balanced";
        if (mode == MODE_BATTERY_SAVER) iconMode = "battery_saver";
        else if (mode == MODE_PERFORMANCE) iconMode = "performance";
        SystemProperties.set("sys.performance.icon", iconMode);
    }

    private boolean writeFile(String path, String value) {
        File file = new File(path);
        if (!file.exists() || !file.canWrite()) return false;
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
