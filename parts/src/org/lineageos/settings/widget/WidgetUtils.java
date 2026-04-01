package org.lineageos.settings.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Method;

public final class WidgetUtils {

    private static final String TAG = "XiaomiPartsWidget";

    // CPU Governor
    private static final String GOV_AVAILABLE_PATH =
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors";
    private static final String GOV_PATH_FMT =
            "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor";

    // Thermal
    private static final String[] THERMAL_PATHS = {
        "/sys/class/thermal/thermal_message/sconfig",
        "/sys/module/msm_thermal/parameters/enabled",
        "/sys/class/thermal/thermal_zone0/policy"
    };

    // HBM
    private static final String HBM_BRIGHTNESS_NODE = "/sys/class/backlight/panel0-backlight/brightness";
    private static final int HBM_MAX_BRIGHTNESS = 4000;
    private static final int FALLBACK_BRIGHTNESS = 200;

    // Hz értékek
    public static final int[] HZ_VALUES = { 60, 90, 120 };

    // -------------------------------------------------------------------
    //  CPU Governor
    // -------------------------------------------------------------------

    public static String[] getAvailableGovernors() {
        String content = readSysfs(GOV_AVAILABLE_PATH);
        if (content == null || content.isEmpty()) {
            return new String[]{"performance", "schedutil", "walt"};
        }
        return content.split("\\s+");
    }

    public static boolean applyGovernor(Context context, String governor) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString("cpu_governor", governor).apply();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append("echo ").append(governor)
                  .append(" > ").append(String.format(GOV_PATH_FMT, i)).append("\n");
            }
            boolean success = runAsRoot(sb.toString());
            Log.d(TAG, "Governor set: " + governor + " -> success: " + success);
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error setting governor", e);
            return false;
        }
    }

    // -------------------------------------------------------------------
    //  Display Hz
    // -------------------------------------------------------------------

    public static boolean applyRefreshRate(Context context, int hz) {
        try {
            Settings.System.putFloat(context.getContentResolver(), "min_refresh_rate",  (float) hz);
            Settings.System.putFloat(context.getContentResolver(), "peak_refresh_rate", (float) hz);
            Log.d(TAG, "Refresh rate set: " + hz + " Hz");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting refresh rate", e);
            return false;
        }
    }

    // -------------------------------------------------------------------
    //  Thermal
    // -------------------------------------------------------------------

    public static boolean applyThermal(Context context, boolean on) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean("thermal_enabled", on).apply();

            String value   = on ? "1" : "0";
            String command = null;
            String usedPath = null;

            for (String path : THERMAL_PATHS) {
                if (fileExistsAndWritable(path)) {
                    if (path.contains("sconfig")) {
                        value = on ? "5" : "0";
                    } else if (path.contains("policy")) {
                        value = on ? "performance" : "balanced";
                    }
                    command  = "echo " + value + " > " + path;
                    usedPath = path;
                    break;
                }
            }

            if (command != null) {
                boolean success = runAsRoot(command);
                Log.d(TAG, "Thermal set (" + usedPath + "): " + on + " -> success: " + success);
                return success;
            } else {
                Log.w(TAG, "No writable thermal path found!");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting thermal", e);
            return false;
        }
    }

    // -------------------------------------------------------------------
    //  HBM (High Brightness Mode)
    // -------------------------------------------------------------------

    public static boolean setHbm(Context context, boolean enable) {
        SharedPreferences prefs = context.getSharedPreferences(
                XiaomiPartsWidget.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (enable) {
            String current = readSysfs(HBM_BRIGHTNESS_NODE);
            int currentBrightness = FALLBACK_BRIGHTNESS;
            try {
                currentBrightness = Integer.parseInt(current.trim());
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse current brightness", e);
            }
            editor.putInt(XiaomiPartsWidget.KEY_LAST_BRIGHTNESS, currentBrightness);
            editor.putBoolean(XiaomiPartsWidget.KEY_HBM_ENABLED, true);
            editor.apply();

            boolean success = runAsRoot("echo " + HBM_MAX_BRIGHTNESS + " > " + HBM_BRIGHTNESS_NODE);
            Log.d(TAG, "HBM enabled: " + success);
            return success;
        } else {
            int last = prefs.getInt(XiaomiPartsWidget.KEY_LAST_BRIGHTNESS, FALLBACK_BRIGHTNESS);
            editor.putBoolean(XiaomiPartsWidget.KEY_HBM_ENABLED, false);
            editor.apply();
            boolean success = runAsRoot("echo " + last + " > " + HBM_BRIGHTNESS_NODE);
            Log.d(TAG, "HBM disabled, restored to " + last);
            return success;
        }
    }

    public static boolean isHbmEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                XiaomiPartsWidget.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(XiaomiPartsWidget.KEY_HBM_ENABLED, false);
    }

    // -------------------------------------------------------------------
    //  Sysfs helpers
    // -------------------------------------------------------------------

    public static String readSysfs(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return "";
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line = br.readLine();
                return (line != null) ? line.trim() : "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean fileExistsAndWritable(String path) {
        File f = new File(path);
        return f.exists() && f.canWrite();
    }

    public static boolean runAsRoot(String command) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(p.getErrorStream()));

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            int code = p.waitFor();

            StringBuilder err = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                err.append(line).append("\n");
            }
            if (err.length() > 0) Log.e(TAG, "Root stderr: " + err);
            if (code != 0)        Log.w(TAG, "su command error, code: " + code);

            return code == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root command execution failed: " + command, e);
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    private WidgetUtils() {}
}
