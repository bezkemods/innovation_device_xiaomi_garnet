package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.telecom.TelecomManager;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.android.settingslib.applications.AppUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class ThermalUtils {

    private static final String TAG = "ThermalUtils";
    private static final String THERMAL_CONTROL = "thermal_control_v2";
    private static final String THERMAL_ENABLED = "thermal_enabled";

    // States
    public static final int STATE_DEFAULT = 0;
    public static final int STATE_BENCHMARK = 1;
    public static final int STATE_BROWSER = 2;
    public static final int STATE_CAMERA = 3;
    public static final int STATE_DIALER = 4;
    public static final int STATE_GAMING = 5;
    public static final int STATE_NAVIGATION = 6;
    public static final int STATE_STREAMING = 7;
    public static final int STATE_VIDEO = 8;

    // Kernel mapping for thermal_message/sconfig
    private static final Map<Integer, String> THERMAL_STATE_MAP = Map.of(
        STATE_DEFAULT, "0",
        STATE_BENCHMARK, "10",
        STATE_BROWSER, "11",
        STATE_CAMERA, "12",
        STATE_DIALER, "8",
        STATE_GAMING, "13",
        STATE_NAVIGATION, "19",
        STATE_STREAMING, "4",
        STATE_VIDEO, "21"
    );

    // Package storage keys
    private static final String THERMAL_BENCHMARK = "thermal.benchmark=";
    private static final String THERMAL_BROWSER = "thermal.browser=";
    private static final String THERMAL_CAMERA = "thermal.camera=";
    private static final String THERMAL_DIALER = "thermal.dialer=";
    private static final String THERMAL_GAMING = "thermal.gaming=";
    private static final String THERMAL_NAVIGATION = "thermal.navigation=";
    private static final String THERMAL_STREAMING = "thermal.streaming=";
    private static final String THERMAL_VIDEO = "thermal.video=";
    private static final String THERMAL_DEFAULT = "thermal.default=";

    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";
    
    // Temperature paths for Monitoring
    private static final String ZONE_CPU = "/sys/class/thermal/thermal_zone0/temp";
    private static final String ZONE_GPU = "/sys/class/kgsl/kgsl-3d0/temp";
    private static final String ZONE_BATTERY = "/sys/class/power_supply/battery/temp";

    // Well-known app packages for automatic detection
    private static final String GMAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String GMEET_PACKAGE = "com.google.android.apps.tachyon";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String NETFLIX_PACKAGE = "com.netflix.mediaclient";
    private static final String CHROME_PACKAGE = "com.android.chrome";
    
    // Optimized: High load games for SM7435 (Adreno 710)
    private static final String GENSHIN_PACKAGE = "com.miHoYo.GenshinImpact";
    private static final String COD_PACKAGE = "com.activision.callofduty.shooter";
    private static final String PUBG_PACKAGE = "com.tencent.ig";
    private static final String MLBB_PACKAGE = "com.mobile.legends";

    private Context mContext;
    private SharedPreferences mSharedPrefs;
    private Intent mServiceIntent;

    private static ThermalUtils sInstance;

    private ThermalUtils(Context context) {
        mContext = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mServiceIntent = new Intent(mContext, ThermalService.class);
    }

    public static synchronized ThermalUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ThermalUtils(context);
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return mSharedPrefs.getBoolean(THERMAL_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        mSharedPrefs.edit().putBoolean(THERMAL_ENABLED, enabled).apply();
        if (enabled) {
            startService();
        } else {
            setDefaultThermalProfile();
            stopService();
        }
        Log.d(TAG, "Thermal profiles " + (enabled ? "enabled" : "disabled"));
    }

    public void startService() {
        if (isEnabled()) {
            try {
                mContext.startServiceAsUser(mServiceIntent, UserHandle.CURRENT);
                Log.d(TAG, "ThermalService started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service", e);
            }
        }
    }

    private void stopService() {
        try {
            mContext.stopService(mServiceIntent);
            Log.d(TAG, "ThermalService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop service", e);
        }
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);
        if (value == null || value.isEmpty()) {
            value = THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":" +
                    THERMAL_DIALER + ":" + THERMAL_GAMING + ":" + THERMAL_NAVIGATION + ":" +
                    THERMAL_STREAMING + ":" + THERMAL_VIDEO + ":" + THERMAL_DEFAULT;
            writeValue(value);
        }
        return value;
    }

    public void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "Invalid package name");
            return;
        }

        try {
            String value = getValue();
            // Remove package from all categories
            value = value.replace(packageName + ",", "");
            String[] modes = value.split(":");

            if (modes.length != 9) {
                Log.e(TAG, "Invalid thermal control data structure");
                resetThermalControl();
                value = getValue();
                modes = value.split(":");
            }

            // Add package to selected mode
            if (mode >= 0 && mode < modes.length) {
                modes[mode] = modes[mode] + packageName + ",";
            } else {
                modes[STATE_DEFAULT] = modes[STATE_DEFAULT] + packageName + ",";
            }

            StringBuilder finalStringBuilder = new StringBuilder();
            for (int i = 0; i < modes.length; i++) {
                finalStringBuilder.append(modes[i]);
                if (i < modes.length - 1) {
                    finalStringBuilder.append(":");
                }
            }
            writeValue(finalStringBuilder.toString());
            Log.d(TAG, "Package " + packageName + " assigned to mode " + mode);
        } catch (Exception e) {
            Log.e(TAG, "Error writing package mode", e);
        }
    }

    public int getStateForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return STATE_DEFAULT;
        }

        try {
            String value = getValue();
            String[] modes = value.split(":");
            
            if (modes.length != 9) {
                Log.e(TAG, "Invalid thermal control data structure");
                resetThermalControl();
                return getDefaultStateForPackage(packageName);
            }

            int state = STATE_DEFAULT;

            if (modes[STATE_BENCHMARK].contains(packageName + ",")) {
                state = STATE_BENCHMARK;
            } else if (modes[STATE_BROWSER].contains(packageName + ",")) {
                state = STATE_BROWSER;
            } else if (modes[STATE_CAMERA].contains(packageName + ",")) {
                state = STATE_CAMERA;
            } else if (modes[STATE_DIALER].contains(packageName + ",")) {
                state = STATE_DIALER;
            } else if (modes[STATE_GAMING].contains(packageName + ",")) {
                state = STATE_GAMING;
            } else if (modes[STATE_NAVIGATION].contains(packageName + ",")) {
                state = STATE_NAVIGATION;
            } else if (modes[STATE_STREAMING].contains(packageName + ",")) {
                state = STATE_STREAMING;
            } else if (modes[STATE_VIDEO].contains(packageName + ",")) {
                state = STATE_VIDEO;
            } else if (modes[STATE_DEFAULT].contains(packageName + ",")) {
                state = STATE_DEFAULT;
            } else {
                // Not explicitly set, use intelligent defaults
                state = getDefaultStateForPackage(packageName);
            }

            return state;
        } catch (Exception e) {
            Log.e(TAG, "Error getting state for package", e);
            return STATE_DEFAULT;
        }
    }

    public void setDefaultThermalProfile() {
        String value = THERMAL_STATE_MAP.get(STATE_DEFAULT);
        if (value != null) {
            writeLine(THERMAL_SCONFIG, value);
            Log.d(TAG, "Applied default thermal profile");
        }
    }

    public void setThermalProfile(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            setDefaultThermalProfile();
            return;
        }

        try {
            final int state = getStateForPackage(packageName);
            String value = THERMAL_STATE_MAP.get(state);
            if (value != null) {
                writeLine(THERMAL_SCONFIG, value);
                Log.d(TAG, "Applied thermal profile " + state + " for " + packageName);
            } else {
                setDefaultThermalProfile();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting thermal profile", e);
            setDefaultThermalProfile();
        }
    }
    
    // === Monitoring Methods for GpuManager ===
    
    public static float getCpuTemp() {
        return readTemp(ZONE_CPU) / 1000.0f;
    }

    public static float getGpuTemp() {
        float temp = readTemp(ZONE_GPU);
        if (temp > 1000) temp /= 1000.0f;
        if (temp > 200) temp /= 10.0f;
        return temp;
    }

    public static float getBatteryTemp() {
        return readTemp(ZONE_BATTERY) / 10.0f;
    }
    
    private static float readTemp(String path) {
        try {
            String line = readFile(path);
            if (line != null && !line.isEmpty()) {
                return Float.parseFloat(line.trim());
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid temperature value from " + path);
        } catch (Exception e) {
            Log.w(TAG, "Error reading temperature from " + path, e);
        }
        return 0;
    }

    private int getDefaultStateForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return STATE_DEFAULT;
        }
        
        // Check hardcoded apps first
        switch (packageName) {
            case GMAPS_PACKAGE:
                return STATE_NAVIGATION;
            case GMEET_PACKAGE:
                return STATE_STREAMING;
            case YOUTUBE_PACKAGE:
            case NETFLIX_PACKAGE:
                return STATE_VIDEO;
            case CHROME_PACKAGE:
                return STATE_BROWSER;
            case GENSHIN_PACKAGE:
            case COD_PACKAGE:
            case PUBG_PACKAGE:
            case MLBB_PACKAGE:
                return STATE_GAMING;
        }

        final PackageManager pm = mContext.getPackageManager();
        final ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + packageName);
            return STATE_DEFAULT;
        }

        // Check app categories
        if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
            return STATE_GAMING;
        } else if (appInfo.category == ApplicationInfo.CATEGORY_VIDEO) {
            return STATE_VIDEO;
        } else if (appInfo.category == ApplicationInfo.CATEGORY_MAPS) {
            return STATE_NAVIGATION;
        }

        // Check specific app types
        if (AppUtils.isBrowserApp(mContext, packageName, UserHandle.myUserId())) {
            return STATE_BROWSER;
        } else if (isDialerApp(packageName)) {
            return STATE_DIALER;
        } else if (isCameraApp(packageName)) {
            return STATE_CAMERA;
        }
        
        return STATE_DEFAULT;
    }

    private boolean isDialerApp(String packageName) {
        try {
            TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
            if (telecomManager != null) {
                String defaultDialer = telecomManager.getDefaultDialerPackage();
                return packageName.equals(defaultDialer);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking dialer app", e);
        }
        return false;
    }

    private boolean isCameraApp(String packageName) {
        try {
            final Intent cameraIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .setPackage(packageName);
            final List<ResolveInfo> list = mContext.getPackageManager()
                    .queryIntentActivitiesAsUser(cameraIntent, PackageManager.MATCH_ALL, 
                            UserHandle.myUserId());
            return list != null && !list.isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "Error checking camera app", e);
            return false;
        }
    }

    private void resetThermalControl() {
        String value = THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":" +
                THERMAL_DIALER + ":" + THERMAL_GAMING + ":" + THERMAL_NAVIGATION + ":" +
                THERMAL_STREAMING + ":" + THERMAL_VIDEO + ":" + THERMAL_DEFAULT;
        writeValue(value);
        Log.d(TAG, "Thermal control data reset");
    }

    public static boolean isThermalNodeAvailable() {
        return new File(THERMAL_SCONFIG).exists();
    }

    private static void writeLine(String path, String value) {
        if (value == null || value.isEmpty()) {
            Log.w(TAG, "Cannot write null/empty value to " + path);
            return;
        }

        try (FileWriter writer = new FileWriter(new File(path))) {
            writer.write(value);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to " + path, e);
        }
    }
    
    private static String readFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.readLine();
        } catch (IOException e) {
            Log.w(TAG, "Error reading file: " + path, e);
            return null;
        }
    }
}
