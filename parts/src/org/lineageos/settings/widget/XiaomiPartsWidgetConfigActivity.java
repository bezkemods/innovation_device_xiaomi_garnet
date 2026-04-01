package org.lineageos.settings.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import org.lineageos.settings.performance.PerformanceUtils;

public class XiaomiPartsWidgetConfigActivity extends Activity {

    private static final String TAG = "XiaomiPartsWidget";
    private int mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        try {
            initPrefsFromSystem();
        } catch (Exception e) {
            Log.e(TAG, "Error reading system settings, applying fallback", e);
            applyFallbackDefaults();
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        XiaomiPartsWidget.updateWidget(this, appWidgetManager, mWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void initPrefsFromSystem() {
        SharedPreferences prefs =
                getSharedPreferences(XiaomiPartsWidget.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        KernelManagerUtils km = new KernelManagerUtils();
        PerformanceUtils    pm = new PerformanceUtils(this);

        // Governor
        String[] govs      = WidgetUtils.getAvailableGovernors();
        String   currentGov = PreferenceManager.getDefaultSharedPreferences(this)
                                .getString("cpu_governor", "walt");
        int govIdx = 0;
        for (int i = 0; i < govs.length; i++) {
            if (govs[i].equals(currentGov)) { govIdx = i; break; }
        }
        editor.putInt(XiaomiPartsWidget.KEY_GOV_INDEX, govIdx);

        // Refresh rate
        float currentHz = Settings.System.getFloat(
                getContentResolver(), "peak_refresh_rate", 120f);
        int hzIdx = WidgetUtils.HZ_VALUES.length - 1;
        for (int i = 0; i < WidgetUtils.HZ_VALUES.length; i++) {
            if (WidgetUtils.HZ_VALUES[i] == (int) currentHz) { hzIdx = i; break; }
        }
        editor.putInt(XiaomiPartsWidget.KEY_HZ_INDEX, hzIdx);

        // A55 max freq
        String a55Max = km.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER);
        if (a55Max != null && !a55Max.isEmpty()) {
            editor.putString(XiaomiPartsWidget.KEY_A55_MAX, a55Max);
        } else {
            String[] freqs = km.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER);
            if (freqs != null && freqs.length > 0)
                editor.putString(XiaomiPartsWidget.KEY_A55_MAX, freqs[freqs.length - 1]);
        }

        // A78 max freq
        String a78Max = km.getCurrentMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER);
        if (a78Max != null && !a78Max.isEmpty()) {
            editor.putString(XiaomiPartsWidget.KEY_A78_MAX, a78Max);
        } else {
            String[] freqs = km.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER);
            if (freqs != null && freqs.length > 0)
                editor.putString(XiaomiPartsWidget.KEY_A78_MAX, freqs[freqs.length - 1]);
        }

        // Thermal
        boolean thermalOn = PreferenceManager.getDefaultSharedPreferences(this)
                              .getBoolean("thermal_enabled", false);
        editor.putBoolean(XiaomiPartsWidget.KEY_THERMAL, thermalOn);

        // HBM – alapértelmezetten kikapcsolva
        editor.putBoolean(XiaomiPartsWidget.KEY_HBM_ENABLED, false);
        editor.putInt(XiaomiPartsWidget.KEY_LAST_BRIGHTNESS, 200);

        editor.apply();
        Log.d(TAG, "Widget prefs synced with system.");
    }

    private void applyFallbackDefaults() {
        getSharedPreferences(XiaomiPartsWidget.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(XiaomiPartsWidget.KEY_GOV_INDEX,  0)
            .putInt(XiaomiPartsWidget.KEY_HZ_INDEX,   WidgetUtils.HZ_VALUES.length - 1)
            .putBoolean(XiaomiPartsWidget.KEY_THERMAL, false)
            .putBoolean(XiaomiPartsWidget.KEY_HBM_ENABLED, false)
            .putInt(XiaomiPartsWidget.KEY_LAST_BRIGHTNESS, 200)
            .remove(XiaomiPartsWidget.KEY_A55_MAX)
            .remove(XiaomiPartsWidget.KEY_A78_MAX)
            .apply();
    }
}
