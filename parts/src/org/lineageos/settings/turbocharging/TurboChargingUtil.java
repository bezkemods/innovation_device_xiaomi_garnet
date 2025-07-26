package org.lineageos.settings.turbocharging;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

public class TurboChargingUtil {

    public static void applyTurboAndSportsSettings(Context context) {
        if (context == null) {
            Log.w(TurboChargingConstants.TAG, "Context is null, cannot apply settings");
            return;
        }
        
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs == null) {
                Log.w(TurboChargingConstants.TAG, "SharedPreferences is null");
                return;
            }
            
            boolean turboEnabled = prefs.getBoolean(TurboChargingConstants.PREF_TURBO_ENABLED, false);
            String turboValue = turboEnabled ? 
                prefs.getString(TurboChargingConstants.PREF_TURBO_CURRENT, TurboChargingConstants.DEFAULT_ON_VALUE) :
                TurboChargingConstants.DEFAULT_OFF_VALUE;
            boolean sportsEnabled = turboEnabled && prefs.getBoolean(TurboChargingConstants.PREF_SPORTS_MODE, false);

            // Apply system property
            boolean propertySet = setSystemProperty(TurboChargingConstants.PROP_TURBO_CURRENT, turboValue);
            
            // Apply sports mode
            boolean sportsModeSet = setSportsModeNode(sportsEnabled ? "1" : "0");
            
            Log.i(TurboChargingConstants.TAG, 
                String.format("Settings applied - Turbo: %s (%s), Sports: %s, Property: %s, SportsNode: %s",
                    turboEnabled, turboValue, sportsEnabled, propertySet, sportsModeSet));
                    
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error applying turbo and sports settings", e);
        }
    }

    public static boolean setSystemProperty(String key, String value) {
        if (key == null || value == null) {
            Log.w(TurboChargingConstants.TAG, "System property key or value is null");
            return false;
        }
        
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method setProp = sp.getMethod("set", String.class, String.class);
            setProp.invoke(null, key, value);
            Log.i(TurboChargingConstants.TAG, "System property " + key + " set to " + value);
            return true;
        } catch (ClassNotFoundException e) {
            Log.e(TurboChargingConstants.TAG, "SystemProperties class not found", e);
        } catch (NoSuchMethodException e) {
            Log.e(TurboChargingConstants.TAG, "SystemProperties.set method not found", e);
        } catch (SecurityException e) {
            Log.e(TurboChargingConstants.TAG, "Security exception setting system property", e);
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Failed to set system property " + key, e);
        }
        return false;
    }

    public static boolean setSportsModeNode(String value) {
        if (value == null) {
            Log.w(TurboChargingConstants.TAG, "Sports mode value is null");
            return false;
        }
        
        File nodeFile = new File(TurboChargingConstants.SPORTS_MODE_NODE);
        
        // Check if node exists
        if (!nodeFile.exists()) {
            Log.w(TurboChargingConstants.TAG, "Sports mode node does not exist: " + TurboChargingConstants.SPORTS_MODE_NODE);
            return false;
        }
        
        // Check if we can write to the node
        if (!nodeFile.canWrite()) {
            Log.w(TurboChargingConstants.TAG, "Cannot write to sports mode node: " + TurboChargingConstants.SPORTS_MODE_NODE);
            return false;
        }
        
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(nodeFile));
            writer.write(value);
            writer.flush();
            Log.i(TurboChargingConstants.TAG, "Sports mode node set to " + value);
            return true;
        } catch (IOException e) {
            Log.e(TurboChargingConstants.TAG, "Failed to write sports mode node", e);
        } catch (SecurityException e) {
            Log.e(TurboChargingConstants.TAG, "Security exception writing sports mode node", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TurboChargingConstants.TAG, "Error closing sports mode node writer", e);
                }
            }
        }
        return false;
    }

    /**
     * Check if turbo charging is supported on this device
     */
    public static boolean isTurboChargingSupported() {
        File sportsModeNode = new File(TurboChargingConstants.SPORTS_MODE_NODE);
        boolean supported = sportsModeNode.exists();
        Log.d(TurboChargingConstants.TAG, "Turbo charging supported: " + supported);
        return supported;
    }

    /**
     * Get current system property value
     */
    public static String getSystemProperty(String key, String defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method getProp = sp.getMethod("get", String.class, String.class);
            return (String) getProp.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Failed to get system property " + key, e);
            return defaultValue;
        }
    }

    /**
     * Validate turbo current value
     */
    public static boolean isValidTurboCurrentValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        try {
            long currentValue = Long.parseLong(value);
            // Reasonable range check (between 1A and 20A in microamps)
            return currentValue >= 1000000 && currentValue <= 20000000;
        } catch (NumberFormatException e) {
            Log.w(TurboChargingConstants.TAG, "Invalid turbo current value: " + value);
            return false;
        }
    }
}
