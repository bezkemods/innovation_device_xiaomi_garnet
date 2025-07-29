package org.lineageos.settings.logcatviewer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class LogcatSettingsPreference {
    private static final String TAG = "LogcatSettingsPreference";
    private static final String PREF_AUTO_START_LOGCAT = "auto_start_logcat";
    
    public static void handleLogcatViewerClick(Context context) {
        // Always start the MainActivity, don't restart service if already running
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
    
    public static void handleAutoStartToggle(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_AUTO_START_LOGCAT, enabled).apply();
        
        if (enabled) {
            startLogcatService(context);
        } else {
            stopLogcatService(context);
        }
        
        Log.d(TAG, "Auto-start logcat " + (enabled ? "enabled" : "disabled"));
    }
    
    public static boolean isAutoStartEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_AUTO_START_LOGCAT, false);
    }
    
    public static void startLogcatService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, LogcatBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Logcat service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start logcat service", e);
        }
    }
    
    public static void stopLogcatService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, LogcatBackgroundService.class);
            context.stopService(serviceIntent);
            Log.d(TAG, "Logcat service stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop logcat service", e);
        }
    }
    
    // Call this from your main settings activity's onCreate or boot receiver
    public static void initializeOnBoot(Context context) {
        if (isAutoStartEnabled(context)) {
            startLogcatService(context);
            Log.d(TAG, "Auto-started logcat service on boot");
        }
    }
}
