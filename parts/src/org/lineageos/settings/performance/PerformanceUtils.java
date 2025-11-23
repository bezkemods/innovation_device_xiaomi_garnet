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

    // Notification IDs
    private static final int NOTIFICATION_ID_BATTERY_SAVER = 1001;
    private static final int NOTIFICATION_ID_BALANCED = 1002;
    private static final int NOTIFICATION_ID_PERFORMANCE = 1003;
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
        try {
            Log.d(TAG, "Setting performance mode to: " + mode);
            
            // Vibrate on mode change
            if (mVibrator != null && mVibrator.hasVibrator()) {
                mVibrator.vibrate(100);
            }

            boolean success = false;

            switch (mode) {
                case MODE_BATTERY_SAVER:
                    success = setBatterySaverMode();
                    break;
                case MODE_BALANCED:
                    success = setBalancedMode();
                    break;
                case MODE_PERFORMANCE:
                    success = setPerformanceMode();
                    break;
            }

            if (success) {
                // Save current mode to preferences
                mSharedPrefs.edit().putInt(PREFS_KEY_CURRENT_MODE, mode).apply();
                
                // Set system property
                SystemProperties.set(PERF_MODE_PROP, String.valueOf(mode));
                
                // Update status bar icon and notification
                updateStatusBarIcon(mode);
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

    private boolean setBatterySaverMode() {
        try {
            // Set CPU governors to powersave
            boolean cpuSuccess = true;
            try {
                writeLine(POLICY0_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
                Log.d(TAG, "Set policy0 governor to powersave");
            } catch (Exception e) {
                Log.w(TAG, "Failed to set policy0 governor to powersave", e);
                cpuSuccess = false;
            }

            // Try policy4 first, then policy6
            try {
                if (fileExists(POLICY4_GOVERNOR_PATH)) {
                    writeLine(POLICY4_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
                    Log.d(TAG, "Set policy4 governor to powersave");
                } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                    writeLine(POLICY6_GOVERNOR_PATH, POWERSAVE_GOVERNOR);
                    Log.d(TAG, "Set policy6 governor to powersave");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set performance cluster governor to powersave", e);
                cpuSuccess = false;
            }

            // Apply WALT settings
            applyWaltSettings();

            // Set GPU to lowest performance
            boolean gpuSuccess = true;
            try {
                if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                    writeLine(GPU_FORCE_CLK_ON_PATH, "0");
                }
                if (fileExists(GPU_FORCE_RAIL_ON_PATH)) {
                    writeLine(GPU_FORCE_RAIL_ON_PATH, "0");
                }
                if (fileExists(GPU_DEFAULT_PWRLEVEL_PATH)) {
                    writeLine(GPU_DEFAULT_PWRLEVEL_PATH, "7"); // Lowest power level
                }
                if (fileExists(GPU_MIN_CLOCK_PATH)) {
                    writeLine(GPU_MIN_CLOCK_PATH, GPU_MIN_FREQ_DEFAULT);
                }
                Log.d(TAG, "GPU set to battery saver mode");
            } catch (Exception e) {
                Log.w(TAG, "Error setting GPU to battery saver mode", e);
                gpuSuccess = false;
            }

            // Enable system battery saver
            enableSystemBatterySaver(true);

            return cpuSuccess || gpuSuccess;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting battery saver mode", e);
            return false;
        }
    }

    private boolean setBalancedMode() {
        try {
            // Set CPU governors to default
            boolean cpuSuccess = true;
            try {
                writeLine(POLICY0_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                Log.d(TAG, "Set policy0 governor to default");
            } catch (Exception e) {
                Log.w(TAG, "Failed to set policy0 governor to default", e);
                cpuSuccess = false;
            }

            // Try policy4 first, then policy6
            try {
                if (fileExists(POLICY4_GOVERNOR_PATH)) {
                    writeLine(POLICY4_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                    Log.d(TAG, "Set policy4 governor to default");
                } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                    writeLine(POLICY6_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                    Log.d(TAG, "Set policy6 governor to default");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set performance cluster governor to default", e);
                cpuSuccess = false;
            }

            // Apply WALT settings
            applyWaltSettings();

            // Set GPU to balanced settings
            boolean gpuSuccess = true;
            try {
                if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                    writeLine(GPU_FORCE_CLK_ON_PATH, "0");
                }
                if (fileExists(GPU_FORCE_RAIL_ON_PATH)) {
                    writeLine(GPU_FORCE_RAIL_ON_PATH, "0");
                }
                if (fileExists(GPU_DEFAULT_PWRLEVEL_PATH)) {
                    writeLine(GPU_DEFAULT_PWRLEVEL_PATH, GPU_DEFAULT_POWER_LEVEL);
                }
                if (fileExists(GPU_MIN_CLOCK_PATH)) {
                    writeLine(GPU_MIN_CLOCK_PATH, GPU_MIN_FREQ_DEFAULT);
                }
                Log.d(TAG, "GPU set to balanced mode");
            } catch (Exception e) {
                Log.w(TAG, "Error setting GPU to balanced mode", e);
                gpuSuccess = false;
            }

            // Disable system battery saver
            enableSystemBatterySaver(false);

            return cpuSuccess || gpuSuccess;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting balanced mode", e);
            return false;
        }
    }

    private boolean setPerformanceMode() {
        try {
            // Set CPU governors to performance
            boolean cpuSuccess = true;
            try {
                writeLine(POLICY0_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
                Log.d(TAG, "Set policy0 governor to performance");
            } catch (Exception e) {
                Log.w(TAG, "Failed to set policy0 governor to performance", e);
                cpuSuccess = false;
            }

            // Try policy4 first, then policy6
            try {
                if (fileExists(POLICY4_GOVERNOR_PATH)) {
                    writeLine(POLICY4_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
                    Log.d(TAG, "Set policy4 governor to performance");
                } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                    writeLine(POLICY6_GOVERNOR_PATH, PERFORMANCE_GOVERNOR);
                    Log.d(TAG, "Set policy6 governor to performance");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set performance cluster governor to performance", e);
                cpuSuccess = false;
            }

            // Set GPU to maximum performance
            boolean gpuSuccess = true;
            try {
                if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                    writeLine(GPU_FORCE_CLK_ON_PATH, "1");
                }
                if (fileExists(GPU_FORCE_RAIL_ON_PATH)) {
                    writeLine(GPU_FORCE_RAIL_ON_PATH, "1");
                }
                if (fileExists(GPU_DEFAULT_PWRLEVEL_PATH)) {
                    writeLine(GPU_DEFAULT_PWRLEVEL_PATH, "0");
                }
                if (fileExists(GPU_MIN_CLOCK_PATH) && fileExists(GPU_MAX_CLOCK_PATH)) {
                    String maxClock = readLine(GPU_MAX_CLOCK_PATH).trim();
                    writeLine(GPU_MIN_CLOCK_PATH, maxClock);
                }
                Log.d(TAG, "GPU set to performance mode");
            } catch (Exception e) {
                Log.w(TAG, "Error setting GPU to performance mode", e);
                gpuSuccess = false;
            }

            // Disable system battery saver
            enableSystemBatterySaver(false);

            return cpuSuccess || gpuSuccess;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting performance mode", e);
            return false;
        }
    }

    private void applyWaltSettings() {
        String[] waltPathsCpu0 = {
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_freq", "940800",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/hispeed_load", "90",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/target_load_shift", "4",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/target_load_thresh", "1024",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/down_rate_limit_us", "20000",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/pl", "0",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/boost", "0",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/adaptive_low_freq", "0",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/rtg_boost_freq", "480000",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/up_rate_limit_us", "500",
            "/sys/devices/system/cpu/cpu0/cpufreq/walt/adaptive_high_freq", "0"
        };

        String[] waltPathsCpu4 = {
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_freq", "960000",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/hispeed_load", "90",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/target_load_shift", "4",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/target_load_thresh", "1024",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/down_rate_limit_us", "10000",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/pl", "0",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/boost", "-10",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/adaptive_low_freq", "0",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/rtg_boost_freq", "0",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/up_rate_limit_us", "500",
            "/sys/devices/system/cpu/cpu4/cpufreq/walt/adaptive_high_freq", "0"
        };

        // Apply for cpu0
        for (int i = 0; i < waltPathsCpu0.length; i += 2) {
            String path = waltPathsCpu0[i];
            String value = waltPathsCpu0[i + 1];
            if (fileExists(path)) {
                try {
                    writeLine(path, value);
                    Log.d(TAG, "Applied WALT setting: " + path + " = " + value);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to apply WALT setting: " + path, e);
                }
            } else {
                Log.d(TAG, "WALT path not found, skipping: " + path);
            }
        }

        // Apply for cpu4
        for (int i = 0; i < waltPathsCpu4.length; i += 2) {
            String path = waltPathsCpu4[i];
            String value = waltPathsCpu4[i + 1];
            if (fileExists(path)) {
                try {
                    writeLine(path, value);
                    Log.d(TAG, "Applied WALT setting: " + path + " = " + value);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to apply WALT setting: " + path, e);
                }
            } else {
                Log.d(TAG, "WALT path not found, skipping: " + path);
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
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(mContext.getString(R.string.performance_notification_channel_desc));
            channel.setBlockable(true);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification(int mode) {
        if (mNotificationManager == null) return;

        // Cancel all other notifications first
        cancelAllNotifications();

        String title, text;
        int icon, notificationId;

        switch (mode) {
            case MODE_BATTERY_SAVER:
                title = mContext.getString(R.string.performance_mode_battery_saver);
                text = mContext.getString(R.string.performance_notification_battery_saver);
                icon = R.drawable.ic_performance_battery_saver;
                notificationId = NOTIFICATION_ID_BATTERY_SAVER;
                break;
            case MODE_BALANCED:
                title = mContext.getString(R.string.performance_mode_balanced);
                text = mContext.getString(R.string.performance_notification_balanced);
                icon = R.drawable.ic_performance_balanced;
                notificationId = NOTIFICATION_ID_BALANCED;
                break;
            case MODE_PERFORMANCE:
                title = mContext.getString(R.string.performance_mode_performance);
                text = mContext.getString(R.string.performance_notification_performance);
                icon = R.drawable.ic_performance_performance;
                notificationId = NOTIFICATION_ID_PERFORMANCE;
                break;
            default:
                return;
        }

        // Open XiaomiParts instead of battery settings
        Intent intent = new Intent();
        intent.setClassName("org.lineageos.settings", "org.lineageos.settings.xiaomiparts.XiaomiPartsActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();

        mNotificationManager.notify(notificationId, notification);
    }

    private void cancelAllNotifications() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID_BATTERY_SAVER);
            mNotificationManager.cancel(NOTIFICATION_ID_BALANCED);
            mNotificationManager.cancel(NOTIFICATION_ID_PERFORMANCE);
        }
    }

    private void updateStatusBarIcon(int mode) {
        // Set system property for status bar icon
        String iconMode;
        switch (mode) {
            case MODE_BATTERY_SAVER:
                iconMode = "battery_saver";
                break;
            case MODE_BALANCED:
                iconMode = "balanced";
                break;
            case MODE_PERFORMANCE:
                iconMode = "performance";
                break;
            default:
                iconMode = "balanced";
                break;
        }
        SystemProperties.set("sys.performance.icon", iconMode);
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
                    Log.w(TAG, "Error closing BufferedReader", e);
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
                    Log.w(TAG, "Error closing FileWriter", e);
                }
            }
        }
    }

    private static boolean fileExists(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead();
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
