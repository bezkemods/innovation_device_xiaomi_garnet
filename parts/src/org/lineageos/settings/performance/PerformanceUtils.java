/*
 * Copyright (C) 2025 bezke
 * Optimized for Garnet (Snapdragon 7s Gen 2, SM7435 / Adreno 710)
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
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.util.Log;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.R;
import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class PerformanceUtils {

    private static final String TAG = "PerformanceUtils";

    // =========================================================================
    // Mode constants
    // =========================================================================
    public static final int MODE_BATTERY_SAVER = 0;
    public static final int MODE_BALANCED      = 1;
    public static final int MODE_PERFORMANCE   = 2;

    // =========================================================================
    // SharedPreferences keys
    // =========================================================================
    private static final String PREFS_KEY_CURRENT_MODE = "performance_profile_int";
    public  static final String PREFS_KEY_LIST         = "performance_profile";
    private static final String PREFS_KEY_USER_NIGHT   = "performance_saved_night_mode";

    private static final String PERF_MODE_PROP = "sys.performance.mode";

    // =========================================================================
    // CPU governor paths
    // =========================================================================
    private static final String CPU_GOVERNOR_BASE = "/sys/devices/system/cpu/cpufreq";

    // =========================================================================
    // CPU paths — atomic cluster writes via msm_performance
    // =========================================================================
    private static final String CPU_MIN_FREQ_PATH =
            "/sys/kernel/msm_performance/parameters/cpu_min_freq";
    private static final String CPU_MAX_FREQ_PATH =
            "/sys/kernel/msm_performance/parameters/cpu_max_freq";

    private static final String CPU_MIN_DEFAULT  = "0:0 1:0 2:0 3:0 4:0 5:0 6:0 7:0";
    private static final String CPU_MAX_UNLOCKED =
            "0:9999999 1:9999999 2:9999999 3:9999999 4:9999999 5:9999999 6:9999999 7:9999999";
    private static final String CPU_MIN_PERFORMANCE =
            "0:1651200 1:1651200 2:1651200 3:1651200 4:1900800 5:1900800 6:1900800 7:1900800";
    private static final String CPU_MAX_BATTERY =
            "0:1324800 1:1324800 2:1324800 3:1324800 4:1190400 5:1190400 6:1190400 7:1190400";

    // =========================================================================
    // GPU paths — Adreno 710
    // =========================================================================
    private static final String GPU_MIN_FREQ_PATH   = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq";
    private static final String GPU_MAX_FREQ_PATH   = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";
    private static final String GPU_FORCE_RAIL_PATH = "/sys/class/kgsl/kgsl-3d0/force_rail_on";
    private static final String GPU_FORCE_CLK_PATH  = "/sys/class/kgsl/kgsl-3d0/force_clk_on";
    private static final String GPU_IDLE_TIMER_PATH = "/sys/class/kgsl/kgsl-3d0/idle_timer";

    private static final String GPU_MIN_DEFAULT  = "295000000";
    private static final String GPU_MAX_DEFAULT  = "940000000";
    private static final String GPU_MAX_BATTERY  = "650000000";
    private static final String GPU_MIN_PERF     = "500000000";
    private static final String GPU_IDLE_DEFAULT = "64";
    private static final String GPU_IDLE_BATTERY = "40";
    private static final String GPU_IDLE_PERF    = "100";

    // =========================================================================
    // Scheduler / DDR / uclamp
    // =========================================================================
    private static final String SCHED_BOOST_PATH = "/proc/sys/walt/sched_boost";
    private static final String DDR_MIN_FREQ_PATH =
            "/sys/devices/system/cpu/bus_dcvs/DDR/19091000.qcom,bwmon-ddr/min_freq";
    private static final String DDR_MIN_DEFAULT  = "547000";
    private static final String DDR_MIN_PERF     = "1708000";

    private static final String UCLAMP_TA_MIN_PATH     = "/dev/cpuctl/top-app/cpu.uclamp.min";
    private static final String UCLAMP_TA_LATENCY_PATH =
            "/dev/cpuctl/top-app/cpu.uclamp.latency_sensitive";
    private static final String UCLAMP_FG_MIN_PATH     = "/dev/cpuctl/foreground/cpu.uclamp.min";

    // =========================================================================
    // Notification
    // =========================================================================
    private static final String NOTIFICATION_CHANNEL_ID = "performance_profile_channel";
    private static final int    NOTIFICATION_ID          = 1001;

    // =========================================================================
    // Fields
    // =========================================================================
    private final Context             mContext;
    private final SharedPreferences   mSharedPrefs;
    private final NotificationManager mNotificationManager;
    private final UiModeManager       mUiModeManager;
    private final Vibrator            mVibrator;

    public PerformanceUtils(Context context) {
        mContext             = context.getApplicationContext();
        mSharedPrefs         = PreferenceManager.getDefaultSharedPreferences(mContext);
        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mUiModeManager       = (UiModeManager)
                mContext.getSystemService(Context.UI_MODE_SERVICE);
        mVibrator            = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        setupNotificationChannel();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public int getCurrentMode() {
        return mSharedPrefs.getInt(PREFS_KEY_CURRENT_MODE, MODE_BALANCED);
    }

    public String getModeLabel(int mode) {
        switch (mode) {
            case MODE_BATTERY_SAVER: return mContext.getString(R.string.performance_mode_battery_saver);
            case MODE_PERFORMANCE:   return mContext.getString(R.string.performance_mode_performance);
            default:                 return mContext.getString(R.string.performance_mode_balanced);
        }
    }

    /**
     * Apply a performance profile. Always returns true (best-effort sysfs writes).
     */
    public boolean setPerformanceMode(int mode) {
        return setPerformanceMode(mode, true);
    }

    /**
     * Apply a performance profile.
     *
     * @param userInitiated false when called from boot restore — skips haptic
     *                      feedback so the phone does not buzz during boot.
     */
    public boolean setPerformanceMode(int mode, boolean userInitiated) {
        Log.d(TAG, "Setting performance mode: " + getModeLabel(mode)
                + (userInitiated ? "" : " (boot restore)"));

        if (userInitiated && mVibrator != null && mVibrator.hasVibrator()) {
            mVibrator.vibrate(50);
        }

        // Apply kernel/sysfs tuning + governor
        switch (mode) {
            case MODE_BATTERY_SAVER:
                applyBatterySaver();
                break;
            case MODE_PERFORMANCE:
                applyPerformance();
                break;
            default:
                mode = MODE_BALANCED;
                applyBalanced();
                break;
        }

        // Apply UI night mode change
        applyNightMode(mode);

        // Persist (int key + string key so ListPreference stays in sync)
        mSharedPrefs.edit()
                .putInt(PREFS_KEY_CURRENT_MODE, mode)
                .putString(PREFS_KEY_LIST, String.valueOf(mode))
                .apply();

        try {
            SystemProperties.set(PERF_MODE_PROP, String.valueOf(mode));
        } catch (Exception e) {
            Log.w(TAG, "Could not set system property " + PERF_MODE_PROP, e);
        }

        showNotification(mode);
        Log.d(TAG, "Performance mode applied: " + getModeLabel(mode));
        return true;
    }

    /** Legacy boolean overload kept for existing call sites. */
    public boolean setPerformanceMode(boolean enabled) {
        return setPerformanceMode(enabled ? MODE_PERFORMANCE : MODE_BALANCED);
    }

    public boolean isPerformanceModeEnabled() {
        return getCurrentMode() == MODE_PERFORMANCE;
    }

    // =========================================================================
    // Governor handling
    // =========================================================================

    /**
     * Sets the scaling governor for all CPU policy directories using KernelManagerUtils.
     * This ensures that the governor change is properly applied and persisted.
     */
    private void setGovernor(String desiredGovernor) {
        KernelManagerUtils km = new KernelManagerUtils();
        boolean success = km.setGovernor(desiredGovernor);
        if (success) {
            Log.d(TAG, "Governor set to: " + desiredGovernor);
        } else {
            Log.w(TAG, "Failed to set governor to: " + desiredGovernor);
        }
    }

    // =========================================================================
    // Night mode (Dark Theme)
    // =========================================================================

    private void applyNightMode(int newMode) {
        if (mUiModeManager == null) return;

        try {
            int previousMode = getCurrentMode(); // read BEFORE prefs are written

            if (newMode == MODE_BATTERY_SAVER) {
                if (previousMode != MODE_BATTERY_SAVER) {
                    int currentNightMode = mUiModeManager.getNightMode();
                    mSharedPrefs.edit()
                            .putInt(PREFS_KEY_USER_NIGHT, currentNightMode)
                            .apply();
                    Log.d(TAG, "Saved night mode: " + currentNightMode);
                }
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
                Log.d(TAG, "Dark mode enabled for Battery Saver");
            } else if (previousMode == MODE_BATTERY_SAVER) {
                int savedNightMode = mSharedPrefs.getInt(
                        PREFS_KEY_USER_NIGHT, UiModeManager.MODE_NIGHT_AUTO);
                mUiModeManager.setNightMode(savedNightMode);
                Log.d(TAG, "Restored night mode: " + savedNightMode);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not change night mode: " + e.getMessage());
        }
    }

    // =========================================================================
    // Profile implementations
    // =========================================================================

    private void applyPerformance() {
        // CPU — freq floors via msm_performance; governor stays walt so
        // PowerHAL powerhint.json hints (INTERACTION, LAUNCH, CAMERA_*,
        // EXPENSIVE_RENDERING) keep working. The "performance" governor
        // would bypass WALT and break hint processing (see
        // KernelManagerUtils.applyPerformanceProfile()).
        writeSafe(CPU_MIN_FREQ_PATH, CPU_MIN_PERFORMANCE);
        writeSafe(CPU_MAX_FREQ_PATH, CPU_MAX_UNLOCKED);
        setGovernor("walt");

        // GPU
        writeSafe(GPU_MIN_FREQ_PATH,   GPU_MIN_PERF);
        writeSafe(GPU_MAX_FREQ_PATH,   GPU_MAX_DEFAULT);
        writeSafe(GPU_FORCE_RAIL_PATH, "1");
        writeSafe(GPU_FORCE_CLK_PATH,  "1");
        writeSafe(GPU_IDLE_TIMER_PATH, GPU_IDLE_PERF);

        // Scheduler & DDR
        writeSafe(SCHED_BOOST_PATH,       "1");
        writeSafe(DDR_MIN_FREQ_PATH,      DDR_MIN_PERF);
        writeSafe(UCLAMP_TA_MIN_PATH,     "30");
        writeSafe(UCLAMP_TA_LATENCY_PATH, "1");
        writeSafe(UCLAMP_FG_MIN_PATH,     "20");
    }

    private void applyBalanced() {
        // CPU
        writeSafe(CPU_MIN_FREQ_PATH, CPU_MIN_DEFAULT);
        writeSafe(CPU_MAX_FREQ_PATH, CPU_MAX_UNLOCKED);
        setGovernor("walt");

        // GPU
        writeSafe(GPU_MIN_FREQ_PATH,   GPU_MIN_DEFAULT);
        writeSafe(GPU_MAX_FREQ_PATH,   GPU_MAX_DEFAULT);
        writeSafe(GPU_FORCE_RAIL_PATH, "0");
        writeSafe(GPU_FORCE_CLK_PATH,  "0");
        writeSafe(GPU_IDLE_TIMER_PATH, GPU_IDLE_DEFAULT);

        // Scheduler & DDR
        writeSafe(SCHED_BOOST_PATH,       "0");
        writeSafe(DDR_MIN_FREQ_PATH,      DDR_MIN_DEFAULT);
        writeSafe(UCLAMP_TA_MIN_PATH,     "0");
        writeSafe(UCLAMP_TA_LATENCY_PATH, "0");
        writeSafe(UCLAMP_FG_MIN_PATH,     "0");
    }

    private void applyBatterySaver() {
        // CPU: lower max frequencies; governor stays walt — the "powersave"
        // governor would pin all cores to min freq (making the caps below
        // meaningless), cause visible jank, and break PowerHAL hints.
        writeSafe(CPU_MIN_FREQ_PATH, CPU_MIN_DEFAULT);
        writeSafe(CPU_MAX_FREQ_PATH, CPU_MAX_BATTERY);
        setGovernor("walt");

        // GPU: lower max frequency, faster idle, no force rail/clk
        writeSafe(GPU_MIN_FREQ_PATH,   GPU_MIN_DEFAULT);
        writeSafe(GPU_MAX_FREQ_PATH,   GPU_MAX_BATTERY);
        writeSafe(GPU_FORCE_RAIL_PATH, "0");
        writeSafe(GPU_FORCE_CLK_PATH,  "0");
        writeSafe(GPU_IDLE_TIMER_PATH, GPU_IDLE_BATTERY);

        // Scheduler & DDR: most conservative
        writeSafe(SCHED_BOOST_PATH,       "0");
        writeSafe(DDR_MIN_FREQ_PATH,      DDR_MIN_DEFAULT);
        writeSafe(UCLAMP_TA_MIN_PATH,     "0");
        writeSafe(UCLAMP_TA_LATENCY_PATH, "0");
        writeSafe(UCLAMP_FG_MIN_PATH,     "0");
    }

    // =========================================================================
    // File I/O
    // =========================================================================

    private void writeSafe(String path, String value) {
        File f = new File(path);
        if (!f.exists()) {
            Log.v(TAG, "Node not present, skipping: " + path);
            return;
        }
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(value);
            fw.flush();
            Log.v(TAG, "Wrote '" + value + "' -> " + path);
        } catch (IOException e) {
            Log.w(TAG, "Write failed (SELinux or read-only?): " + path, e);
        }
    }

    // =========================================================================
    // Notification
    // =========================================================================

    private void setupNotificationChannel() {
        if (mNotificationManager == null) return;
        NotificationChannel ch = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                mContext.getString(R.string.performance_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(mContext.getString(R.string.performance_notification_channel_desc));
        ch.setShowBadge(false);
        ch.setBlockable(true);
        mNotificationManager.createNotificationChannel(ch);
    }

    private void showNotification(int mode) {
        if (mNotificationManager == null) return;

        mNotificationManager.cancel(NOTIFICATION_ID);

        int iconRes;
        String title, text;

        switch (mode) {
            case MODE_BATTERY_SAVER:
                iconRes = R.drawable.ic_performance_battery_saver;
                title   = mContext.getString(R.string.performance_mode_battery_saver);
                text    = mContext.getString(R.string.performance_notification_battery_saver);
                break;
            case MODE_PERFORMANCE:
                iconRes = R.drawable.ic_performance_performance;
                title   = mContext.getString(R.string.performance_mode_performance);
                text    = mContext.getString(R.string.performance_notification_performance);
                break;
            default:
                iconRes = R.drawable.ic_performance_balanced;
                title   = mContext.getString(R.string.performance_mode_balanced);
                text    = mContext.getString(R.string.performance_notification_balanced);
                break;
        }

        Intent intent = new Intent();
        intent.setClassName("org.lineageos.settings",
                "org.lineageos.settings.xiaomiparts.XiaomiPartsActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(iconRes)
                .setContentIntent(pi)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, n);
        Log.d(TAG, "Notification updated: " + title);
    }
}
