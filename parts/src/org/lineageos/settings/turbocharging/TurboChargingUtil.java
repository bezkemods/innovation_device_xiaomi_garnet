package org.lineageos.settings.turbocharging;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

public class TurboChargingUtil {

    private static final String TAG = "TurboChargingUtil";
    private static final String PREF_TURBO_ENABLED = "turbo_enable";
    private static final String PREF_TURBO_CURRENT = "turbo_current";
    private static final String PREF_SPORTS_MODE = "sports_mode";
    private static final String PROP_TURBO_CURRENT = "persist.sys.turbo_charge_current";
    private static final String DEFAULT_OFF_VALUE = "4700000";
    private static final String DEFAULT_ON_VALUE = "6700000";
    private static final String SPORTS_MODE_NODE = "/sys/class/qcom-battery/sport_mode";

    public static void applyTurboAndSportsSettings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean turboEnabled = prefs.getBoolean(PREF_TURBO_ENABLED, false);
        String turboValue = turboEnabled ? prefs.getString(PREF_TURBO_CURRENT, DEFAULT_ON_VALUE)
                                         : DEFAULT_OFF_VALUE;
        boolean sportsEnabled = turboEnabled && prefs.getBoolean(PREF_SPORTS_MODE, false);

        setSystemProperty(PROP_TURBO_CURRENT, turboValue);
        setSportsModeNode(sportsEnabled ? "1" : "0");
    }

    public static void setSystemProperty(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method setProp = sp.getMethod("set", String.class, String.class);
            setProp.invoke(null, key, value);
            Log.i(TAG, "System property " + key + " set to " + value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system property", e);
        }
    }

    public static void setSportsModeNode(String value) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SPORTS_MODE_NODE))) {
            writer.write(value);
            Log.i(TAG, "Sports mode node set to " + value);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write sports mode node", e);
        }
    }
}
