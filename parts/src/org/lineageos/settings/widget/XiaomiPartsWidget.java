package org.lineageos.settings.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.lineageos.settings.R;
import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import org.lineageos.settings.performance.PerformanceUtils;

public class XiaomiPartsWidget extends AppWidgetProvider {

    private static final String TAG = "XiaomiPartsWidget";

    // Actions
    public static final String ACTION_GOV_PREV         = "org.lineageos.settings.widget.GOV_PREV";
    public static final String ACTION_GOV_NEXT         = "org.lineageos.settings.widget.GOV_NEXT";
    public static final String ACTION_HZ_DEC           = "org.lineageos.settings.widget.HZ_DEC";
    public static final String ACTION_HZ_INC           = "org.lineageos.settings.widget.HZ_INC";
    public static final String ACTION_THERMAL_TOGGLE   = "org.lineageos.settings.widget.THERMAL_TOGGLE";
    public static final String ACTION_PERFORMANCE_MODE = "org.lineageos.settings.widget.PERFORMANCE_MODE";
    public static final String ACTION_OPEN_PARTS       = "org.lineageos.settings.widget.OPEN_PARTS";
    public static final String ACTION_A55_DEC          = "org.lineageos.settings.widget.A55_DEC";
    public static final String ACTION_A55_INC          = "org.lineageos.settings.widget.A55_INC";
    public static final String ACTION_A78_DEC          = "org.lineageos.settings.widget.A78_DEC";
    public static final String ACTION_A78_INC          = "org.lineageos.settings.widget.A78_INC";
    public static final String ACTION_TOGGLE_THEME     = "org.lineageos.settings.widget.TOGGLE_THEME";
    public static final String ACTION_HBM_TOGGLE       = "org.lineageos.settings.widget.HBM_TOGGLE";

    // Request codes
    private static final int RC_GOV_PREV     = 101, RC_GOV_NEXT    = 102;
    private static final int RC_HZ_DEC       = 103, RC_HZ_INC      = 104;
    private static final int RC_THERMAL      = 105, RC_PERF        = 106, RC_OPEN = 107;
    private static final int RC_A55_DEC      = 108, RC_A55_INC     = 109;
    private static final int RC_A78_DEC      = 110, RC_A78_INC     = 111;
    private static final int RC_TOGGLE_THEME = 112;
    private static final int RC_HBM_TOGGLE   = 119;

    // SharedPreferences keys
    public static final String PREFS_NAME          = "XiaomiPartsWidgetPrefs";
    public static final String KEY_GOV_INDEX       = "gov_index";
    public static final String KEY_HZ_INDEX        = "hz_index";
    public static final String KEY_THERMAL         = "thermal_on";
    public static final String KEY_A55_MAX         = "a55_max_freq";
    public static final String KEY_A78_MAX         = "a78_max_freq";
    public static final String KEY_THEME_ORANGE    = "widget_theme_orange";
    public static final String KEY_HBM_ENABLED     = "hbm_enabled";
    public static final String KEY_LAST_BRIGHTNESS = "last_brightness";

    private static final String KM_KEY_EFFICIENCY_MAX  = "efficiency_max_freq";
    private static final String KM_KEY_PERFORMANCE_MAX = "performance_max_freq";
    private static final String KM_KEY_CPU_GOVERNOR    = "cpu_governor";

    private static final int COLOR_ORANGE = 0xFFFF9800;

    private KernelManagerUtils mKernelUtils;
    private PerformanceUtils   mPerfUtils;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        SharedPreferences kmPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);

        if (mKernelUtils == null) mKernelUtils = new KernelManagerUtils();
        if (mPerfUtils   == null) mPerfUtils   = new PerformanceUtils(context);

        boolean success      = false;
        String  toastMessage = null;

        switch (action) {
            case ACTION_GOV_PREV: {
                String[] govs = WidgetUtils.getAvailableGovernors();
                int idx = (prefs.getInt(KEY_GOV_INDEX, 0) - 1 + govs.length) % govs.length;
                editor.putInt(KEY_GOV_INDEX, idx).apply();
                success = WidgetUtils.applyGovernor(context, govs[idx]);
                kmPrefs.edit().putString(KM_KEY_CPU_GOVERNOR, govs[idx]).apply();
                toastMessage = "Governor: " + govs[idx] + (success ? "" : " (failed)");
                break;
            }
            case ACTION_GOV_NEXT: {
                String[] govs = WidgetUtils.getAvailableGovernors();
                int idx = (prefs.getInt(KEY_GOV_INDEX, 0) + 1) % govs.length;
                editor.putInt(KEY_GOV_INDEX, idx).apply();
                success = WidgetUtils.applyGovernor(context, govs[idx]);
                kmPrefs.edit().putString(KM_KEY_CPU_GOVERNOR, govs[idx]).apply();
                toastMessage = "Governor: " + govs[idx] + (success ? "" : " (failed)");
                break;
            }
            case ACTION_HZ_DEC: {
                int idx = clamp(prefs.getInt(KEY_HZ_INDEX, WidgetUtils.HZ_VALUES.length - 1) - 1,
                        0, WidgetUtils.HZ_VALUES.length - 1);
                editor.putInt(KEY_HZ_INDEX, idx).apply();
                success = WidgetUtils.applyRefreshRate(context, WidgetUtils.HZ_VALUES[idx]);
                toastMessage = "Refresh rate: " + WidgetUtils.HZ_VALUES[idx] + " Hz" + (success ? "" : " (failed)");
                break;
            }
            case ACTION_HZ_INC: {
                int idx = clamp(prefs.getInt(KEY_HZ_INDEX, WidgetUtils.HZ_VALUES.length - 1) + 1,
                        0, WidgetUtils.HZ_VALUES.length - 1);
                editor.putInt(KEY_HZ_INDEX, idx).apply();
                success = WidgetUtils.applyRefreshRate(context, WidgetUtils.HZ_VALUES[idx]);
                toastMessage = "Refresh rate: " + WidgetUtils.HZ_VALUES[idx] + " Hz" + (success ? "" : " (failed)");
                break;
            }
            case ACTION_THERMAL_TOGGLE: {
                boolean on = !prefs.getBoolean(KEY_THERMAL, false);
                editor.putBoolean(KEY_THERMAL, on).apply();
                success = WidgetUtils.applyThermal(context, on);
                toastMessage = (on ? "Thermal ON" : "Thermal OFF") + (success ? "" : " (failed)");
                break;
            }
            case ACTION_PERFORMANCE_MODE: {
                int currentMode = mPerfUtils.getCurrentMode();
                int newMode;
                if      (currentMode == PerformanceUtils.MODE_BATTERY_SAVER) newMode = PerformanceUtils.MODE_BALANCED;
                else if (currentMode == PerformanceUtils.MODE_BALANCED)       newMode = PerformanceUtils.MODE_PERFORMANCE;
                else                                                           newMode = PerformanceUtils.MODE_BATTERY_SAVER;
                success = mPerfUtils.setPerformanceMode(newMode);
                toastMessage = "Performance mode: " + mPerfUtils.getModeLabel(newMode) + (success ? "" : " (failed)");
                // After mode change, sync governor index in widget prefs
                String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
                String[] govs = WidgetUtils.getAvailableGovernors();
                int newGovIdx = 0;
                for (int i = 0; i < govs.length; i++) {
                    if (govs[i].equals(currentGovernor)) {
                        newGovIdx = i;
                        break;
                    }
                }
                editor.putInt(KEY_GOV_INDEX, newGovIdx).apply();
                break;
            }
            case ACTION_A55_DEC: {
                String[] freqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER);
                if (freqs != null && freqs.length > 0) {
                    String current = prefs.getString(KEY_A55_MAX, freqs[freqs.length - 1]);
                    int idx = getIndexInArray(freqs, current);
                    if (idx > 0) idx--;
                    String newFreq = freqs[idx];
                    editor.putString(KEY_A55_MAX, newFreq).apply();
                    success = mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, newFreq);
                    kmPrefs.edit().putString(KM_KEY_EFFICIENCY_MAX, newFreq).apply();
                    toastMessage = "A55 max: " + formatFrequency(Long.parseLong(newFreq)) + (success ? "" : " (failed)");
                }
                break;
            }
            case ACTION_A55_INC: {
                String[] freqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER);
                if (freqs != null && freqs.length > 0) {
                    String current = prefs.getString(KEY_A55_MAX, freqs[freqs.length - 1]);
                    int idx = getIndexInArray(freqs, current);
                    if (idx < freqs.length - 1) idx++;
                    String newFreq = freqs[idx];
                    editor.putString(KEY_A55_MAX, newFreq).apply();
                    success = mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, newFreq);
                    kmPrefs.edit().putString(KM_KEY_EFFICIENCY_MAX, newFreq).apply();
                    toastMessage = "A55 max: " + formatFrequency(Long.parseLong(newFreq)) + (success ? "" : " (failed)");
                }
                break;
            }
            case ACTION_A78_DEC: {
                String[] freqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER);
                if (freqs != null && freqs.length > 0) {
                    String current = prefs.getString(KEY_A78_MAX, freqs[freqs.length - 1]);
                    int idx = getIndexInArray(freqs, current);
                    if (idx < 0) idx = freqs.length - 1;
                    if (idx > 0) idx--;
                    String newFreq = freqs[idx];
                    editor.putString(KEY_A78_MAX, newFreq).apply();
                    success = mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, newFreq);
                    kmPrefs.edit().putString(KM_KEY_PERFORMANCE_MAX, newFreq).apply();
                    toastMessage = "A78 max: " + formatFrequency(Long.parseLong(newFreq)) + (success ? "" : " (failed)");
                }
                break;
            }
            case ACTION_A78_INC: {
                String[] freqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER);
                if (freqs != null && freqs.length > 0) {
                    String current = prefs.getString(KEY_A78_MAX, freqs[freqs.length - 1]);
                    int idx = getIndexInArray(freqs, current);
                    if (idx < 0) idx = freqs.length - 1;
                    if (idx < freqs.length - 1) idx++;
                    String newFreq = freqs[idx];
                    editor.putString(KEY_A78_MAX, newFreq).apply();
                    success = mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, newFreq);
                    kmPrefs.edit().putString(KM_KEY_PERFORMANCE_MAX, newFreq).apply();
                    toastMessage = "A78 max: " + formatFrequency(Long.parseLong(newFreq)) + (success ? "" : " (failed)");
                }
                break;
            }
            case ACTION_HBM_TOGGLE: {
                boolean current = WidgetUtils.isHbmEnabled(context);
                boolean newState = !current;
                success = WidgetUtils.setHbm(context, newState);
                toastMessage = (newState ? "HBM ON" : "HBM OFF") + (success ? "" : " (failed)");
                break;
            }
            case ACTION_TOGGLE_THEME: {
                boolean isOrange = prefs.getBoolean(KEY_THEME_ORANGE, false);
                editor.putBoolean(KEY_THEME_ORANGE, !isOrange).apply();
                AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                int[] ids = mgr.getAppWidgetIds(new ComponentName(context, XiaomiPartsWidget.class));
                for (int id : ids) updateWidget(context, mgr, id);
                return;
            }
            case ACTION_OPEN_PARTS:
                try {
                    Intent partsIntent = new Intent();
                    partsIntent.setClassName("org.lineageos.settings",
                            "org.lineageos.settings.xiaomiparts.XiaomiPartsActivity");
                    partsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(partsIntent);
                    Toast.makeText(context, "Opening XiaomiParts", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open parts", e);
                    Toast.makeText(context, "Cannot open XiaomiParts", Toast.LENGTH_SHORT).show();
                }
                return;
        }

        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, XiaomiPartsWidget.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isOrange = prefs.getBoolean(KEY_THEME_ORANGE, false);

            int layoutRes = isOrange ? R.layout.widget_xiaomi_parts_orange : R.layout.widget_xiaomi_parts;
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutRes);

            KernelManagerUtils km = new KernelManagerUtils();
            PerformanceUtils   pm = new PerformanceUtils(context);

            int cardBgRes       = isOrange ? R.drawable.widget_card_bg_orange       : R.drawable.widget_card_bg;
            int activeCardBgRes = isOrange ? R.drawable.widget_card_active_bg_orange : R.drawable.widget_card_active_bg;
            int btnCircleBgRes  = isOrange ? R.drawable.widget_btn_circle_bg_orange_selector : R.drawable.widget_btn_circle_bg;

            views.setInt(R.id.widget_root,       "setBackgroundResource", isOrange ? R.drawable.widget_root_bg_orange : R.drawable.widget_root_bg);
            views.setInt(R.id.card_governor,     "setBackgroundResource", cardBgRes);
            views.setInt(R.id.card_hz,           "setBackgroundResource", cardBgRes);
            views.setInt(R.id.card_cpu_freq_row, "setBackgroundResource", cardBgRes);

            int headerIconRes = isOrange ? R.drawable.ic_mi_logo : R.drawable.ic_xiaomiparts;
            views.setImageViewResource(R.id.iv_header_icon, headerIconRes);
            // Csak orange módban színezzük az ikonokat
            if (isOrange) {
                views.setInt(R.id.iv_header_icon,  "setColorFilter", COLOR_ORANGE);
                views.setInt(R.id.iv_header_arrow, "setColorFilter", COLOR_ORANGE);
            }
            views.setOnClickPendingIntent(R.id.iv_header_icon, buildPI(context, ACTION_TOGGLE_THEME, RC_TOGGLE_THEME));
            views.setOnClickPendingIntent(R.id.iv_header_arrow, buildPI(context, ACTION_OPEN_PARTS, RC_OPEN));

            // Governor ikon színezése csak orange módban
            if (isOrange) {
                views.setInt(R.id.ic_gov_label, "setColorFilter", COLOR_ORANGE);
            }
            // Gombok beállítása orange módban narancs színnel, normál módban színezés nélkül
            if (isOrange) {
                setButton(views, R.id.btn_gov_prev, R.drawable.ic_chevron_left, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_gov_next, R.drawable.ic_chevron_right, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_hz_dec, R.drawable.ic_remove, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_hz_inc, R.drawable.ic_add, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_a55_dec, R.drawable.ic_remove, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_a55_inc, R.drawable.ic_add, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_a78_dec, R.drawable.ic_remove, btnCircleBgRes, COLOR_ORANGE);
                setButton(views, R.id.btn_a78_inc, R.drawable.ic_add, btnCircleBgRes, COLOR_ORANGE);
            } else {
                setButton(views, R.id.btn_gov_prev, R.drawable.ic_chevron_left, btnCircleBgRes, 0);
                setButton(views, R.id.btn_gov_next, R.drawable.ic_chevron_right, btnCircleBgRes, 0);
                setButton(views, R.id.btn_hz_dec, R.drawable.ic_remove, btnCircleBgRes, 0);
                setButton(views, R.id.btn_hz_inc, R.drawable.ic_add, btnCircleBgRes, 0);
                setButton(views, R.id.btn_a55_dec, R.drawable.ic_remove, btnCircleBgRes, 0);
                setButton(views, R.id.btn_a55_inc, R.drawable.ic_add, btnCircleBgRes, 0);
                setButton(views, R.id.btn_a78_dec, R.drawable.ic_remove, btnCircleBgRes, 0);
                setButton(views, R.id.btn_a78_inc, R.drawable.ic_add, btnCircleBgRes, 0);
            }

            String[] govs = WidgetUtils.getAvailableGovernors();
            int govIdx = clamp(prefs.getInt(KEY_GOV_INDEX, 0), 0, govs.length - 1);
            views.setTextViewText(R.id.tv_gov_value, govs[govIdx]);
            views.setTextViewText(R.id.tv_gov_index, (govIdx + 1) + "/" + govs.length);
            views.setOnClickPendingIntent(R.id.btn_gov_prev, buildPI(context, ACTION_GOV_PREV, RC_GOV_PREV));
            views.setOnClickPendingIntent(R.id.btn_gov_next, buildPI(context, ACTION_GOV_NEXT, RC_GOV_NEXT));

            // Hz ikon színezése csak orange módban
            if (isOrange) {
                views.setInt(R.id.ic_hz_label, "setColorFilter", COLOR_ORANGE);
            }
            int hzIdx = clamp(prefs.getInt(KEY_HZ_INDEX, WidgetUtils.HZ_VALUES.length - 1), 0, WidgetUtils.HZ_VALUES.length - 1);
            views.setTextViewText(R.id.tv_hz_value, WidgetUtils.HZ_VALUES[hzIdx] + " Hz");
            views.setOnClickPendingIntent(R.id.btn_hz_dec, buildPI(context, ACTION_HZ_DEC, RC_HZ_DEC));
            views.setOnClickPendingIntent(R.id.btn_hz_inc, buildPI(context, ACTION_HZ_INC, RC_HZ_INC));

            boolean hbmOn = WidgetUtils.isHbmEnabled(context);
            views.setTextViewText(R.id.tv_hbm_state, hbmOn ? "ON" : "OFF");
            views.setImageViewResource(R.id.iv_hbm_icon, hbmOn ? R.drawable.ic_hbm_on : R.drawable.ic_hbm_off);
            if (isOrange) {
                views.setInt(R.id.iv_hbm_icon, "setColorFilter", COLOR_ORANGE);
            }
            views.setInt(R.id.card_hbm, "setBackgroundResource", hbmOn ? activeCardBgRes : cardBgRes);
            views.setOnClickPendingIntent(R.id.card_hbm, buildPI(context, ACTION_HBM_TOGGLE, RC_HBM_TOGGLE));

            String[] a55Freqs = km.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER);
            if (a55Freqs != null && a55Freqs.length > 0) {
                String savedA55 = prefs.getString(KEY_A55_MAX, a55Freqs[a55Freqs.length - 1]);
                String currentA55 = km.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER);
                if (currentA55 == null || currentA55.isEmpty()) currentA55 = savedA55;
                int a55Idx = getIndexInArray(a55Freqs, currentA55);
                if (a55Idx < 0) a55Idx = a55Freqs.length - 1;
                views.setTextViewText(R.id.tv_a55_value, formatFrequency(Long.parseLong(currentA55)));
                views.setProgressBar(R.id.pb_a55_slider, a55Freqs.length - 1, a55Idx, false);
                views.setOnClickPendingIntent(R.id.btn_a55_dec, buildPI(context, ACTION_A55_DEC, RC_A55_DEC));
                views.setOnClickPendingIntent(R.id.btn_a55_inc, buildPI(context, ACTION_A55_INC, RC_A55_INC));
            }

            String[] a78Freqs = km.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER);
            if (a78Freqs != null && a78Freqs.length > 0) {
                String savedA78 = prefs.getString(KEY_A78_MAX, a78Freqs[a78Freqs.length - 1]);
                String currentA78 = km.getCurrentMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER);
                if (currentA78 == null || currentA78.isEmpty()) currentA78 = savedA78;
                int a78Idx = getIndexInArray(a78Freqs, currentA78);
                if (a78Idx < 0) a78Idx = a78Freqs.length - 1;
                views.setTextViewText(R.id.tv_a78_value, formatFrequency(Long.parseLong(currentA78)));
                views.setProgressBar(R.id.pb_a78_slider, a78Freqs.length - 1, a78Idx, false);
                views.setOnClickPendingIntent(R.id.btn_a78_dec, buildPI(context, ACTION_A78_DEC, RC_A78_DEC));
                views.setOnClickPendingIntent(R.id.btn_a78_inc, buildPI(context, ACTION_A78_INC, RC_A78_INC));
            }

            boolean thermalOn = prefs.getBoolean(KEY_THERMAL, false);
            views.setTextViewText(R.id.tv_thermal_state, thermalOn ? "ON" : "OFF");
            views.setImageViewResource(R.id.iv_thermal_icon, thermalOn ? R.drawable.ic_thermal_on : R.drawable.ic_thermal);
            if (isOrange) {
                views.setInt(R.id.iv_thermal_icon, "setColorFilter", COLOR_ORANGE);
            }
            views.setInt(R.id.card_thermal, "setBackgroundResource", thermalOn ? activeCardBgRes : cardBgRes);
            views.setOnClickPendingIntent(R.id.card_thermal, buildPI(context, ACTION_THERMAL_TOGGLE, RC_THERMAL));

            int perfMode = pm.getCurrentMode();
            String perfLabel = pm.getModeLabel(perfMode);
            views.setTextViewText(R.id.tv_perf_state, perfLabel);
            int iconRes;
            if (perfMode == PerformanceUtils.MODE_BATTERY_SAVER) iconRes = R.drawable.ic_performance_battery_saver;
            else if (perfMode == PerformanceUtils.MODE_PERFORMANCE) iconRes = R.drawable.ic_performance_performance;
            else iconRes = R.drawable.ic_performance_balanced;
            views.setImageViewResource(R.id.iv_perf_icon, iconRes);
            if (isOrange) {
                views.setInt(R.id.iv_perf_icon, "setColorFilter", COLOR_ORANGE);
            }
            views.setInt(R.id.card_performance, "setBackgroundResource", cardBgRes);
            views.setOnClickPendingIntent(R.id.card_performance, buildPI(context, ACTION_PERFORMANCE_MODE, RC_PERF));

            manager.updateAppWidget(widgetId, views);

        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    private static void setButton(RemoteViews views, int viewId, int iconRes, int bgRes, int tintColor) {
        views.setImageViewResource(viewId, iconRes);
        views.setInt(viewId, "setBackgroundResource", bgRes);
        if (tintColor != 0) {
            views.setInt(viewId, "setColorFilter", tintColor);
        }
    }

    private static PendingIntent buildPI(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, XiaomiPartsWidget.class);
        intent.setAction(action);
        intent.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int getIndexInArray(String[] array, String value) {
        if (array == null || value == null) return 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }

    private static String formatFrequency(long freqHz) {
        if (freqHz >= 1_000_000_000) return String.format("%.2f GHz", freqHz / 1_000_000_000.0);
        else if (freqHz >= 1_000_000) return String.format("%.2f MHz", freqHz / 1_000_000.0);
        else if (freqHz >= 1_000) return String.format("%.2f kHz", freqHz / 1_000.0);
        else return freqHz + " Hz";
    }
}
