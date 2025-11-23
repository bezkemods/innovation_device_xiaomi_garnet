/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.kernelmanager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class KernelManagerFragment extends PreferenceFragmentCompat 
        implements Preference.OnPreferenceChangeListener {

    // Keys matching XML
    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFF_MIN = "efficiency_min_freq";
    private static final String KEY_EFF_MAX = "efficiency_max_freq";
    private static final String KEY_PERF_MIN = "performance_min_freq";
    private static final String KEY_PERF_MAX = "performance_max_freq";
    private static final String KEY_MONITORING = "enable_monitoring";
    private static final String KEY_UPDATE_INTERVAL = "update_interval";
    private static final String KEY_CPU_MONITOR_CAT = "cpu_monitor_category";

    private KernelManagerUtils mUtils;
    private Handler mHandler;
    private Runnable mUpdateRunnable;
    private boolean mMonitoringEnabled;
    private int mUpdateInterval = 1000;

    // UI Elements
    private ListPreference mGovPref;
    private ListPreference mEffMin, mEffMax, mPerfMin, mPerfMax;
    private SwitchPreference mMonitoringPref;
    private PreferenceCategory mMonitorCategory;
    private Preference[] mCorePrefs = new Preference[8];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);
        
        mUtils = new KernelManagerUtils();
        mHandler = new Handler(Looper.getMainLooper());
        
        initPreferences();
        
        if (mUtils.isKernelManagerSupported()) {
            loadSettings();
        } else {
            Toast.makeText(getContext(), R.string.kernel_manager_error_read, Toast.LENGTH_LONG).show();
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void initPreferences() {
        mGovPref = findPreference(KEY_CPU_GOVERNOR);
        mEffMin = findPreference(KEY_EFF_MIN);
        mEffMax = findPreference(KEY_EFF_MAX);
        mPerfMin = findPreference(KEY_PERF_MIN);
        mPerfMax = findPreference(KEY_PERF_MAX);
        
        mMonitorCategory = findPreference(KEY_CPU_MONITOR_CAT);
        mMonitoringPref = findPreference(KEY_MONITORING);
        ListPreference intervalPref = findPreference(KEY_UPDATE_INTERVAL);

        // Listeners
        setOnChangeListener(mGovPref);
        setOnChangeListener(mEffMin);
        setOnChangeListener(mEffMax);
        setOnChangeListener(mPerfMin);
        setOnChangeListener(mPerfMax);

        // Monitoring setup
        if (mMonitoringPref != null) {
            mMonitoringPref.setOnPreferenceChangeListener((p, v) -> {
                mMonitoringEnabled = (Boolean) v;
                toggleMonitoring(mMonitoringEnabled);
                return true;
            });
            mMonitoringEnabled = mMonitoringPref.isChecked();
        }

        if (intervalPref != null) {
            intervalPref.setOnPreferenceChangeListener((p, v) -> {
                mUpdateInterval = Integer.parseInt((String) v);
                if (mMonitoringEnabled) restartMonitoring();
                return true;
            });
            try {
                mUpdateInterval = Integer.parseInt(intervalPref.getValue());
            } catch (NumberFormatException e) { mUpdateInterval = 1000; }
        }

        // Initialize dynamic core preferences
        if (mMonitorCategory != null) {
            for (int i = 0; i < 8; i++) {
                Preference p = new Preference(getContext());
                p.setKey("cpu_core_" + i);
                p.setTitle("CPU " + i);
                p.setSummary(R.string.kernel_manager_loading);
                p.setIcon(R.drawable.ic_cpu_governor_active); // Opcionális
                p.setSelectable(false);
                mMonitorCategory.addPreference(p);
                mCorePrefs[i] = p;
            }
        }

        // Buttons
        findPreference("apply_settings").setOnPreferenceClickListener(p -> {
            applySettings();
            return true;
        });
        findPreference("reset_settings").setOnPreferenceClickListener(p -> {
            resetSettings();
            return true;
        });
    }

    private void setOnChangeListener(Preference p) {
        if (p != null) p.setOnPreferenceChangeListener(this);
    }

    private void loadSettings() {
        // Governor
        String[] govs = mUtils.getAvailableGovernors();
        if (mGovPref != null && govs != null) {
            mGovPref.setEntries(govs);
            mGovPref.setEntryValues(govs);
            String current = mUtils.getCurrentGovernor(KernelManagerUtils.CLUSTER_LITTLE);
            mGovPref.setValue(current);
            mGovPref.setSummary(getString(R.string.kernel_manager_governor_current, 
                getString(R.string.kernel_manager_governor_summary), current));
        }

        // Frequencies
        updateFreqList(mEffMin, KernelManagerUtils.CLUSTER_LITTLE, true);
        updateFreqList(mEffMax, KernelManagerUtils.CLUSTER_LITTLE, false);
        updateFreqList(mPerfMin, KernelManagerUtils.CLUSTER_BIG, true);
        updateFreqList(mPerfMax, KernelManagerUtils.CLUSTER_BIG, false);
    }

    private void updateFreqList(ListPreference pref, int cluster, boolean isMin) {
        if (pref == null) return;
        String[] freqs = mUtils.getAvailableFrequencies(cluster);
        if (freqs == null) return;

        String[] labels = new String[freqs.length];
        for (int i = 0; i < freqs.length; i++) {
            labels[i] = formatFreq(freqs[i]);
        }
        
        pref.setEntries(labels);
        pref.setEntryValues(freqs);
        
        String current = isMin ? mUtils.getCurrentMinFrequency(cluster) : 
                                 mUtils.getCurrentMaxFrequency(cluster);
        pref.setValue(current);
        
        String baseSummary = getString(isMin ? R.string.kernel_manager_min_freq_summary : 
                                               R.string.kernel_manager_max_freq_summary);
        pref.setSummary(getString(R.string.kernel_manager_freq_current, baseSummary, formatFreq(current)));
    }

    private String formatFreq(String freq) {
        try {
            long f = Long.parseLong(freq);
            if (f >= 1000000) return String.format("%.2f GHz", f / 1000000.0);
            return String.format("%d MHz", f / 1000);
        } catch (Exception e) { return freq; }
    }

    // --- Monitoring ---

    private void toggleMonitoring(boolean enable) {
        if (enable) startMonitoring();
        else stopMonitoring();
    }

    private void startMonitoring() {
        if (mUpdateRunnable == null) {
            mUpdateRunnable = () -> {
                updateCpuStats();
                if (mMonitoringEnabled) mHandler.postDelayed(mUpdateRunnable, mUpdateInterval);
            };
        }
        mHandler.removeCallbacks(mUpdateRunnable);
        mHandler.post(mUpdateRunnable);
    }

    private void stopMonitoring() {
        if (mUpdateRunnable != null) mHandler.removeCallbacks(mUpdateRunnable);
    }

    private void restartMonitoring() {
        stopMonitoring();
        startMonitoring();
    }

    private void updateCpuStats() {
        for (int i = 0; i < 8; i++) {
            if (mCorePrefs[i] == null) continue;
            
            boolean online = mUtils.isCoreOnline(i);
            String freq = mUtils.getCurrentCoreFrequency(i);
            String formattedFreq = formatFreq(freq);
            
            String status;
            if (online) {
                status = getString(R.string.cpu_core_status_online, formattedFreq);
            } else {
                status = getString(R.string.cpu_core_status_offline, getString(R.string.kernel_manager_core_offline));
            }
            mCorePrefs[i].setSummary(status);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String val = (String) newValue;

        if (KEY_CPU_GOVERNOR.equals(key)) {
            mGovPref.setSummary(getString(R.string.kernel_manager_governor_current, 
                getString(R.string.kernel_manager_governor_summary), val));
        } else if (preference instanceof ListPreference) {
             // Frequency updates summary
             String base = "";
             if (key.contains("min")) base = getString(R.string.kernel_manager_min_freq_summary);
             else base = getString(R.string.kernel_manager_max_freq_summary);
             preference.setSummary(getString(R.string.kernel_manager_freq_current, base, formatFreq(val)));
        }
        return true;
    }

    private void applySettings() {
        boolean s = true;
        s &= mUtils.setGovernor(mGovPref.getValue());
        
        // Efficiency
        String eMin = mEffMin.getValue(), eMax = mEffMax.getValue();
        if (mUtils.validateFrequencyRange(KernelManagerUtils.CLUSTER_LITTLE, eMin, eMax)) {
            s &= mUtils.setFrequency(KernelManagerUtils.CLUSTER_LITTLE, eMax, false);
            s &= mUtils.setFrequency(KernelManagerUtils.CLUSTER_LITTLE, eMin, true);
        }

        // Performance
        String pMin = mPerfMin.getValue(), pMax = mPerfMax.getValue();
        if (mUtils.validateFrequencyRange(KernelManagerUtils.CLUSTER_BIG, pMin, pMax)) {
            s &= mUtils.setFrequency(KernelManagerUtils.CLUSTER_BIG, pMax, false);
            s &= mUtils.setFrequency(KernelManagerUtils.CLUSTER_BIG, pMin, true);
        }

        if (s) {
            Toast.makeText(getContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show();
            // Save to prefs for boot restore
            saveToPrefs();
        } else {
            Toast.makeText(getContext(), R.string.kernel_manager_error_write, Toast.LENGTH_LONG).show();
        }
    }

    private void saveToPrefs() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.putString(KEY_CPU_GOVERNOR, mGovPref.getValue());
        editor.putString(KEY_EFF_MIN, mEffMin.getValue());
        editor.putString(KEY_EFF_MAX, mEffMax.getValue());
        editor.putString(KEY_PERF_MIN, mPerfMin.getValue());
        editor.putString(KEY_PERF_MAX, mPerfMax.getValue());
        editor.apply();
    }

    private void resetSettings() {
        // Reset logic - set defaults
        mUtils.setGovernor("walt");
        // Reset prefs
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        editor.remove(KEY_CPU_GOVERNOR);
        editor.remove(KEY_EFF_MIN);
        editor.remove(KEY_EFF_MAX);
        editor.remove(KEY_PERF_MIN);
        editor.remove(KEY_PERF_MAX);
        editor.apply();
        
        loadSettings();
        Toast.makeText(getContext(), R.string.settings_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMonitoringEnabled) startMonitoring();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMonitoring();
    }
}
