/*
 * Copyright (C) 2025 bezke
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
import androidx.preference.PreferenceManager;
import org.lineageos.settings.R;
import java.io.BufferedReader;
import java.io.FileReader;
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

    // Notification
    private static final int NOTIFICATION_ID = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "performance_profile_channel";

    // CPU paths
    private static final String POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
    private static final String POLICY4_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";
    private static final String POLICY6_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy6/scaling_governor";

    // CPU Governors
    private static final String PERFORMANCE_GOVERNOR = "performance";
    private static final String POWERSAVE_GOVERNOR = "powersave";
    private static final String DEFAULT_GOVERNOR = "walt";

    // GPU paths
    private static final String GPU_MAX_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/max_clock_mhz";
    private static final String GPU_MIN_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/min_clock_mhz";
    private static final String GPU_DEFAULT_PWRLEVEL_PATH = "/sys/class/kgsl/kgsl-3d0/default_pwrlevel";
    private static final String GPU_FORCE_CLK_ON_PATH = "/sys/class/kgsl/kgsl-3d0/force_clk_on";
    private static final String GPU_FORCE_RAIL_ON_PATH = "/sys/class/kgsl/kgsl-3d0/force_rail_on";

    // Default values
    private static final String GPU_MIN_FREQ_DEFAULT = "180";
    private static final String GPU_DEFAULT_POWER_LEVEL = "5";
    private static final String PERF_MODE_PROP = "sys.performance.mode";
    private static final String PREFS_KEY_CURRENT_MODE = "current_performance_mode";
    
    // Lock to prevent concurrent modifications
    private static final Object sLock = new Object();

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
            case MODE_BATTERY_SAVER:
                return mContext.getString(R.string.performance_mode_battery_saver);
            case MODE_BALANCED:
                return mContext.getString(R.string.performance_mode_balanced);
            case MODE_PERFORMANCE:
                return mContext.getString(R.string.performance_mode_performance);
            default:
                return mContext.getString(R.string.performance_mode_balanced);
        }
    }

    public boolean setPerformanceMode(int mode) {
        synchronized (sLock) {
            try {
                Log.d(TAG, "Setting performance mode to: " + mode);
                
                // Don't reapply the same mode
                int currentMode = getCurrentMode();
                if (currentMode == mode) {
                    Log.d(TAG, "Mode already set to: " + mode);
                    return true;
                }
                
                // Vibrate on mode change
                if (mVibrator != null && mVibrator.hasVibrator()) {
                    mVibrator.vibrate(50); // Reduced from 100ms
                }

                boolean success = false;

                switch (mode) {
                    case MODE_BATTERY_SAVER:
                        success = applyBatterySaverMode();
                        break;
                    case MODE_BALANCED:
                        success = applyBalancedMode();
                        break;
                    case MODE_PERFORMANCE:
                        success = applyPerformanceMode();
                        break;
                }

                if (success) {
                    // Save current mode to preferences
                    mSharedPrefs.edit().putInt(PREFS_KEY_CURRENT_MODE, mode).apply();
                    
                    // Set system property
                    SystemProperties.set(PERF_MODE_PROP, String.valueOf(mode));
                    
                    // Update notification (single notification, not multiple)
                    showNotification(mode);
                    
                    Log.d(TAG, "Performance mode successfully set to: " + getModeLabel(mode));
                    return true;
                } else {
                    Log.e(TAG, "Failed to set performance mode to: " + mode);
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting performance mode", e);
                return false;
            }
        }
    }

    private boolean applyBatterySaverMode() {
        try {
            boolean success = true;
            
            // Set CPU governors to powersave
            success &= setGovernor(POLICY0_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
            
            // Try policy4 first, then policy6
            if (fileExists(POLICY4_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY4_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
            } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY6_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
            }

            // Apply WALT settings only for battery saver
            applyWaltBatterySaverSettings();

            // Set GPU to lowest performance
            setGpuSetting(GPU_FORCE_CLK_ON_PATH, "0");
            setGpuSetting(GPU_FORCE_RAIL_ON_PATH, "0");
            setGpuSetting(GPU_DEFAULT_PWRLEVEL_PATH, "7"); // Lowest power level
            setGpuSetting(GPU_MIN_CLOCK_PATH, GPU_MIN_FREQ_DEFAULT);

            // Enable system battery saver
            enableSystemBatterySaver(true);

            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting battery saver mode", e);
            return false;
        }
    }

    private boolean applyBalancedMode() {
        try {
            boolean success = true;
            
            // Set CPU governors to default
            success &= setGovernor(POLICY0_GOVERNOR_PATH, DEFAULT_GOVERNOR);
            
            // Try policy4 first, then policy6
            if (fileExists(POLICY4_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY4_GOVERNOR_PATH, DEFAULT_GOVERNOR);
            } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY6_GOVERNOR_PATH, DEFAULT_GOVERNOR);
            }

            // Apply WALT settings for balanced mode
            applyWaltBalancedSettings();

            // Set GPU to balanced settings
            setGpuSetting(GPU_FORCE_CLK_ON_PATH, "0");
            setGpuSetting(GPU_FORCE_RAIL_ON_PATH, "0");
            setGpuSetting(GPU_DEFAULT_PWRLEVEL_PATH, GPU_DEFAULT_POWER_LEVEL);
            setGpuSetting(GPU_MIN_CLOCK_PATH, GPU_MIN_FREQ_DEFAULT);

            // Disable system battery saver
            enableSystemBatterySaver(false);

            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting balanced mode", e);
            return false;
        }
    }

    private boolean applyPerformanceMode() {
        try {
            boolean success = true;
            
            // Set CPU governors to performance
            success &= setGovernor(POLICY0_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
            
            // Try policy4 first, then policy6
            if (fileExists(POLICY4_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY4_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
            } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                success &= setGovernor(POLICY6_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
            }

            // NO WALT settings for performance mode - let performance governor handle it

            // Set GPU to maximum performance
            setGpuSetting(GPU_FORCE_CLK_ON_PATH, "1");
            setGpuSetting(GPU_FORCE_RAIL_ON_PATH, "1");
            setGpuSetting(GPU_DEFAULT_PWRLEVEL_PATH, "0");
            
            if (fileExists(GPU_MIN_CLOCK_PATH) && fileExists(GPU_MAX_CLOCK_PATH)) {
                String maxClock = readLine(GPU_MAX_CLOCK_PATH).trim();
                if (!maxClock.isEmpty()) {
                    setGpuSetting(GPU_MIN_CLOCK_PATH, maxClock);
                }
            }

            // Disable system battery saver
            enableSystemBatterySaver(false);

            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting performance mode", e);
            return false;
        }
    }

    private boolean setGovernor(String path, String governor) {
        try {
            if (fileExists(path)) {
                writeLine(path, governor);
                Log.d(TAG, "Set " + path + " governor to " + governor);
                return true;
            } else {
                Log.w(TAG, "Governor path not found: " + path);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set governor " + governor + " on " + path, e);
            return false;
        }
    }

    private void setGpuSetting(String path, String value) {
        try {
            if (fileExists(path)) {
                writeLine(path, value);
                Log.d(TAG, "Set GPU setting: " + path + " = " + value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set GPU setting: " + path, e);
        }
    }

    private void applyWaltBatterySaverSettings() {
        // Conservative WALT settings for battery saver
        String[][] settings = {
            // CPU0
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_freq", "691200"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_load", "95"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/target_load_shift", "6"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/down_rate_limit_us", "30000"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/up_rate_limit_us", "1000"},
            // CPU4
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_freq", "691200"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_load", "95"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/target_load_shift", "6"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/down_rate_limit_us", "30000"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/up_rate_limit_us", "1000"}
        };
        
        applyWaltSettingsArray(settings);
    }

    private void applyWaltBalancedSettings() {
        // Standard WALT settings for balanced mode
        String[][] settings = {
            // CPU0
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_freq", "940800"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_load", "90"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/target_load_shift", "4"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/down_rate_limit_us", "20000"},
            {"/sys/devices/system/cpu/cpu0/cpufreq/walt/up_rate_limit_us", "500"},
            // CPU4
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_freq", "960000"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_load", "90"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/target_load_shift", "4"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/down_rate_limit_us", "10000"},
            {"/sys/devices/system/cpu/cpu4/cpufreq/walt/up_rate_limit_us", "500"}
        };
        
        applyWaltSettingsArray(settings);
    }

    private void applyWaltSettingsArray(String[][] settings) {
        for (String[] setting : settings) {
            String path = setting[0];
            String value = setting[1];
            
            if (fileExists(path)) {
                try {
                    writeLine(path, value);
                } catch (Exception e) {
                    // Silent failure - WALT settings are optional
                    Log.d(TAG, "Skipped WALT setting: " + path);
                }
            }
        }
    }

    private void enableSystemBatterySaver(boolean enable) {
        try {
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isPowerSaveMode() != enable) {
                Settings.Global.putInt(mContext.getContentResolver(), 
                    Settings.Global.LOW_POWER_MODE, enable ? 1 : 0);
                Log.d(TAG, "System battery saver " + (enable ? "enabled" : "disabled"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to toggle system battery saver", e);
        }
    }

    private void setupNotificationChannel() {
        if (mNotificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                mContext.getString(R.string.performance_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // Changed from DEFAULT to LOW
            );
            channel.setDescription(mContext.getString(R.string.performance_notification_channel_desc));
            channel.setBlockable(true);
            channel.setShowBadge(false); // Don't show badge
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification(int mode) {
        if (mNotificationManager == null) return;

        String title, text;
        int icon;

        switch (mode) {
            case MODE_BATTERY_SAVER:
                title = mContext.getString(R.string.performance_mode_battery_saver);
                text = mContext.getString(R.string.performance_notification_battery_saver);
                icon = R.drawable.ic_performance_battery_saver;
                break;
            case MODE_BALANCED:
                title = mContext.getString(R.string.performance_mode_balanced);
                text = mContext.getString(R.string.performance_notification_balanced);
                icon = R.drawable.ic_performance_balanced;
                break;
            case MODE_PERFORMANCE:
                title = mContext.getString(R.string.performance_mode_performance);
                text = mContext.getString(R.string.performance_notification_performance);
                icon = R.drawable.ic_performance_performance;
                break;
            default:
                return;
        }

        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true) // Don't alert on updates
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    // Helper methods for file operations
    private static String readLine(String path) throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            String s = br.readLine();
            return s == null ? "" : s;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeLine(String path, String value) throws IOException {
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
            fw.write(value);
            fw.flush();
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static boolean fileExists(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead() && file.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    // Legacy methods for compatibility
    public boolean isPerformanceModeEnabled() {
        return getCurrentMode() == MODE_PERFORMANCE;
    }

    public boolean setPerformanceMode(boolean enabled) {
        return setPerformanceMode(enabled ? MODE_PERFORMANCE : MODE_BALANCED);
    }
}
