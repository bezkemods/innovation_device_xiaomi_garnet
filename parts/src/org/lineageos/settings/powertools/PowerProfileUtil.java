/*
 * Copyright (C) 2025 kenway214
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

package org.lineageos.settings.powertools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class PowerProfileUtil {

    private static final String TAG = "PowerProfileUtil";
    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";
    public static final String THERMAL_ENABLED_KEY = "thermal_enabled";
    private static final String SYS_PROP = "sys.perf_mode_active";
    private static final int NOTIFICATION_ID_PERFORMANCE = 1001;
    private static final int NOTIFICATION_ID_GAMING = 1002;
    private static final int NOTIFICATION_ID_BALANCE = 1003;
    private static final int NOTIFICATION_ID_BATTERY_SAVER = 1004;

    public static final int MODE_BALANCE = 0;
    public static final int MODE_GAMING = 1;
    public static final int MODE_PERFORMANCE = 2;
    public static final int MODE_BATTERY_SAVER = 3;

    private static final int POWERPROFILE_BALANCE = 0;
    private static final int POWERPROFILE_GAMING = 10;
    private static final int POWERPROFILE_PERFORMANCE = 23;
    private static final int POWERPROFILE_BATTERY_SAVER = 3;

    private Context mContext;
    private SharedPreferences mSharedPrefs;
    private NotificationManager mNotificationManager;
    private int mCurrentMode = MODE_BALANCE;
    private String[] mModes;

    public PowerProfileUtil(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Resources res = mContext.getResources();
        mModes = new String[]{
                mContext.getString(R.string.powerprofile_mode_balance),
                mContext.getString(R.string.powerprofile_mode_gaming),
                mContext.getString(R.string.powerprofile_mode_performance),
                mContext.getString(R.string.powerprofile_mode_battery_saver)
        };

        if (!mSharedPrefs.contains(THERMAL_ENABLED_KEY)) {
            mSharedPrefs.edit().putBoolean(THERMAL_ENABLED_KEY, false).apply();
        }

        setupNotificationChannel();
    }

    public int getCurrentMode() {
        String line = FileUtils.readOneLine(THERMAL_SCONFIG);
        if (line == null) {
            Log.e(TAG, "Failed to read thermal mode from " + THERMAL_SCONFIG);
            return MODE_BALANCE;
        }
        try {
            int value = Integer.parseInt(line.trim());
            switch (value) {
                case POWERPROFILE_BALANCE:
                    return MODE_BALANCE;
                case POWERPROFILE_GAMING:
                    return MODE_GAMING;
                case POWERPROFILE_PERFORMANCE:
                    return MODE_PERFORMANCE;
                case POWERPROFILE_BATTERY_SAVER:
                    return MODE_BATTERY_SAVER;
                default:
                    return MODE_BALANCE;
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing thermal mode value: ", e);
            return MODE_BALANCE;
        }
    }

    public void setMode(int mode) {
        mCurrentMode = mode;
        int thermalValue;
        switch (mode) {
            case MODE_BALANCE:
                thermalValue = POWERPROFILE_BALANCE;
                setPerformanceModeActive(1);
                break;
            case MODE_GAMING:
                thermalValue = POWERPROFILE_GAMING;
                setPerformanceModeActive(3);
                optimizeGameLaunch();
                break;
            case MODE_PERFORMANCE:
                thermalValue = POWERPROFILE_PERFORMANCE;
                setPerformanceModeActive(2);
                break;
            case MODE_BATTERY_SAVER:
                thermalValue = POWERPROFILE_BATTERY_SAVER;
                setPerformanceModeActive(0);
                break;
            default:
                thermalValue = POWERPROFILE_BALANCE;
                setPerformanceModeActive(1);
                break;
        }

        boolean success = FileUtils.writeLine(THERMAL_SCONFIG, String.valueOf(thermalValue));
        Log.d(TAG, mContext.getString(R.string.thermal_mode_changed, mModes[mode], success));

        cancelPerformanceNotification();
        cancelGamingNotification();
        mNotificationManager.cancel(NOTIFICATION_ID_BALANCE);
        mNotificationManager.cancel(NOTIFICATION_ID_BATTERY_SAVER);

        if (mode == MODE_BATTERY_SAVER) {
            enableBatterySaver(true);
            showBatterySaverNotification();
        } else {
            enableBatterySaver(false);
            if (mode == MODE_PERFORMANCE) {
                showPerformanceNotification();
            } else if (mode == MODE_GAMING) {
                showGamingNotification();
            } else if (mode == MODE_BALANCE) {
                showBalanceNotification();
            }
        }
    }

    public int getManagedMode() {
        mCurrentMode = getCurrentMode();
        return mCurrentMode;
    }

    public boolean isMasterEnabled() {
        return mSharedPrefs.getBoolean(THERMAL_ENABLED_KEY, false);
    }

    public String getModeLabel() {
        if (mCurrentMode >= 0 && mCurrentMode < mModes.length) {
            return mModes[mCurrentMode];
        }
        return mModes[MODE_BALANCE];
    }

    public void toggleMode() {
        int currentMode = getManagedMode();
        int newMode;
        switch (currentMode) {
            case MODE_BALANCE:
                newMode = MODE_GAMING;
                break;
            case MODE_GAMING:
                newMode = MODE_PERFORMANCE;
                break;
            case MODE_PERFORMANCE:
                newMode = MODE_BATTERY_SAVER;
                break;
            case MODE_BATTERY_SAVER:
            default:
                newMode = MODE_BALANCE;
                break;
        }
        Log.d(TAG, "Toggling mode: " + currentMode + " -> " + newMode);
        setMode(newMode);
    }

    private void optimizeGameLaunch() {
        Log.d(TAG, "Gaming mode activated. Game optimizations are currently not implemented.");
    }

    private void enableBatterySaver(boolean enable) {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            boolean isBatterySaverEnabled = powerManager.isPowerSaveMode();
            if (enable && !isBatterySaverEnabled) {
                powerManager.setPowerSaveModeEnabled(true);
                Log.d(TAG, "Battery Saver mode enabled.");
            } else if (!enable && isBatterySaverEnabled) {
                powerManager.setPowerSaveModeEnabled(false);
                Log.d(TAG, "Battery Saver mode disabled.");
            }
        }
    }

    private void setupNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                TAG,
                mContext.getString(R.string.perf_mode_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setBlockable(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void showPerformanceNotification() {
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(mContext, TAG)
                .setContentTitle(mContext.getString(R.string.perf_mode_title))
                .setContentText(mContext.getString(R.string.perf_mode_notification))
                .setSmallIcon(R.drawable.ic_thermal_performance)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID_PERFORMANCE, notification);
    }

    private void showGamingNotification() {
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(mContext, TAG)
                .setContentTitle(mContext.getString(R.string.gaming_mode_title))
                .setContentText(mContext.getString(R.string.gaming_mode_notification))
                .setSmallIcon(R.drawable.ic_thermal_gaming)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID_GAMING, notification);
    }

    private void showBalanceNotification() {
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(mContext, TAG)
                .setContentTitle(mContext.getString(R.string.powerprofile_mode_balance))
                .setContentText(mContext.getString(R.string.balance_mode_notification))
                .setSmallIcon(R.drawable.ic_thermal_balance)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID_BALANCE, notification);
    }

    private void showBatterySaverNotification() {
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(mContext, TAG)
                .setContentTitle(mContext.getString(R.string.powerprofile_mode_battery_saver))
                .setContentText(mContext.getString(R.string.battery_saver_mode_notification))
                .setSmallIcon(R.drawable.ic_thermal_battery_saver)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID_BATTERY_SAVER, notification);
    }

    private void cancelPerformanceNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID_PERFORMANCE);
    }

    private void cancelGamingNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID_GAMING);
    }

    private void setPerformanceModeActive(int mode) {
        SystemProperties.set(SYS_PROP, String.valueOf(mode));
        Log.d(TAG, "Performance mode active set to: " + mode);
    }

    public void cleanup() {
        // No cleanup needed at the moment
    }
}