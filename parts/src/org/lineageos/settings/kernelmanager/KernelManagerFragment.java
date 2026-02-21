/*
 * Copyright (C) 2025 bezke
 * Optimized for Garnet (Snapdragon 7s Gen 2)
 * Battery-optimized version
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
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager; // FIX: was android.preference.PreferenceManager
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class KernelManagerFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFFICIENCY_MIN_FREQ = "efficiency_min_freq";
    private static final String KEY_EFFICIENCY_MAX_FREQ = "efficiency_max_freq";
    private static final String KEY_PERFORMANCE_MIN_FREQ = "performance_min_freq";
    private static final String KEY_PERFORMANCE_MAX_FREQ = "performance_max_freq";
    private static final String KEY_APPLY_SETTINGS = "apply_settings";
    private static final String KEY_RESET_SETTINGS = "reset_settings";
    private static final String KEY_CPU_MONITOR = "cpu_monitor_category";
    private static final String KEY_ENABLE_MONITORING = "enable_monitoring";
    private static final String KEY_UPDATE_INTERVAL = "update_interval";

    // Battery-optimized default update interval
    private static final int DEFAULT_UPDATE_INTERVAL_MS = 2000; // Increased from 1000 to 2000 ms

    private KernelManagerUtils mKernelUtils;
    private ListPreference mGovernorPreference;
    private ListPreference mEfficiencyMinFreq, mEfficiencyMaxFreq;
    private ListPreference mPerformanceMinFreq, mPerformanceMaxFreq;
    private ListPreference mUpdateIntervalPreference;
    private SwitchPreference mEnableMonitoringPreference;
    private SharedPreferences mSharedPrefs;
    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;
    
    // CPU monitoring preferences
    private PreferenceCategory mCpuMonitorCategory;
    private Preference[] mCpuCorePreferences = new Preference[8];
    private Preference mClusterSummaryEfficiency;
    private Preference mClusterSummaryPerformance;
    
    // Battery optimization: Default monitoring OFF to save battery
    private boolean mMonitoringEnabled = false;
    private int mUpdateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);
        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mUpdateHandler = new Handler(Looper.getMainLooper());

        // Battery optimization: Default to monitoring disabled
        mMonitoringEnabled = mSharedPrefs.getBoolean(KEY_ENABLE_MONITORING, false);
        mUpdateIntervalMs = mSharedPrefs.getInt(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL_MS);

        initializePreferences();
        initializeCpuMonitoring();
        loadCurrentSettings();
        
        if (mMonitoringEnabled) {
            startCpuMonitoring();
        }
    }

    private void initializePreferences() {
        mGovernorPreference = (ListPreference) findPreference(KEY_CPU_GOVERNOR);
        mEfficiencyMinFreq = (ListPreference) findPreference(KEY_EFFICIENCY_MIN_FREQ);
        mEfficiencyMaxFreq = (ListPreference) findPreference(KEY_EFFICIENCY_MAX_FREQ);
        mPerformanceMinFreq = (ListPreference) findPreference(KEY_PERFORMANCE_MIN_FREQ);
        mPerformanceMaxFreq = (ListPreference) findPreference(KEY_PERFORMANCE_MAX_FREQ);

        if (mGovernorPreference != null) {
            mGovernorPreference.setOnPreferenceChangeListener(this);
        }

        setFrequencyPreferenceListeners();

        Preference applyPref = findPreference(KEY_APPLY_SETTINGS);
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                applySettings();
                return true;
            });
        }

        Preference resetPref = findPreference(KEY_RESET_SETTINGS);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                resetSettings();
                return true;
            });
        }
    }

    private void initializeCpuMonitoring() {
        mCpuMonitorCategory = new PreferenceCategory(getContext());
        mCpuMonitorCategory.setKey(KEY_CPU_MONITOR);
        mCpuMonitorCategory.setTitle(getString(R.string.kernel_manager_cpu_monitor));
        getPreferenceScreen().addPreference(mCpuMonitorCategory);

        mEnableMonitoringPreference = new SwitchPreference(getContext());
        mEnableMonitoringPreference.setKey(KEY_ENABLE_MONITORING);
        mEnableMonitoringPreference.setTitle(getString(R.string.kernel_manager_enable_monitoring));
        mEnableMonitoringPreference.setSummary(getString(R.string.kernel_manager_enable_monitoring_summary));
        mEnableMonitoringPreference.setChecked(mMonitoringEnabled);
        mEnableMonitoringPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            mMonitoringEnabled = (Boolean) newValue;
            mSharedPrefs.edit().putBoolean(KEY_ENABLE_MONITORING, mMonitoringEnabled).apply();
            
            if (mMonitoringEnabled) {
                startCpuMonitoring();
            } else {
                stopCpuMonitoring();
            }
            
            updateMonitoringVisibility();
            return true;
        });
        mCpuMonitorCategory.addPreference(mEnableMonitoringPreference);

        mUpdateIntervalPreference = new ListPreference(getContext());
        mUpdateIntervalPreference.setKey(KEY_UPDATE_INTERVAL);
        mUpdateIntervalPreference.setTitle(getString(R.string.kernel_manager_update_interval));
        mUpdateIntervalPreference.setSummary(getString(R.string.kernel_manager_update_interval_summary));
        // Battery optimization: Removed 500ms option, minimum is now 1000ms
        mUpdateIntervalPreference.setEntries(new String[]{
            getString(R.string.update_interval_1000ms), 
            getString(R.string.update_interval_2000ms), 
            getString(R.string.update_interval_5000ms)
        });
        mUpdateIntervalPreference.setEntryValues(new String[]{"1000", "2000", "5000"});
        mUpdateIntervalPreference.setValue(String.valueOf(mUpdateIntervalMs));
        mUpdateIntervalPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            mUpdateIntervalMs = Integer.parseInt((String) newValue);
            mSharedPrefs.edit().putInt(KEY_UPDATE_INTERVAL, mUpdateIntervalMs).apply();
            
            if (mMonitoringEnabled) {
                stopCpuMonitoring();
                startCpuMonitoring();
            }
            return true;
        });
        mCpuMonitorCategory.addPreference(mUpdateIntervalPreference);

        mClusterSummaryEfficiency = new Preference(getContext());
        mClusterSummaryEfficiency.setKey("efficiency_cluster_summary");
        mClusterSummaryEfficiency.setTitle(getString(R.string.kernel_manager_efficiency_cluster_summary));
        mClusterSummaryEfficiency.setSummary(getString(R.string.kernel_manager_loading));
        mClusterSummaryEfficiency.setSelectable(false);
        mCpuMonitorCategory.addPreference(mClusterSummaryEfficiency);

        mClusterSummaryPerformance = new Preference(getContext());
        mClusterSummaryPerformance.setKey("performance_cluster_summary");
        mClusterSummaryPerformance.setTitle(getString(R.string.kernel_manager_performance_cluster_summary));
        mClusterSummaryPerformance.setSummary(getString(R.string.kernel_manager_loading));
        mClusterSummaryPerformance.setSelectable(false);
        mCpuMonitorCategory.addPreference(mClusterSummaryPerformance);

        // Create CPU core preferences
        for (int i = 0; i < 8; i++) {
            mCpuCorePreferences[i] = new Preference(getContext());
            mCpuCorePreferences[i].setKey("cpu_core_" + i);
            String coreType = mKernelUtils.getClusterName(mKernelUtils.getCpuPolicy(i));
            mCpuCorePreferences[i].setTitle("CPU " + i + " (" + coreType + ")");
            mCpuCorePreferences[i].setSummary(getString(R.string.kernel_manager_loading));
            mCpuCorePreferences[i].setSelectable(false);
            mCpuMonitorCategory.addPreference(mCpuCorePreferences[i]);
        }

        updateMonitoringVisibility();
    }

    private void updateMonitoringVisibility() {
        if (mCpuMonitorCategory != null) {
            for (int i = 0; i < mCpuMonitorCategory.getPreferenceCount(); i++) {
                Preference pref = mCpuMonitorCategory.getPreference(i);
                if (pref != mEnableMonitoringPreference) {
                    pref.setVisible(mMonitoringEnabled);
                }
            }
        }
    }

    private void setFrequencyPreferenceListeners() {
        if (mEfficiencyMinFreq != null) {
            mEfficiencyMinFreq.setOnPreferenceChangeListener(this);
        }
        if (mEfficiencyMaxFreq != null) {
            mEfficiencyMaxFreq.setOnPreferenceChangeListener(this);
        }
        if (mPerformanceMinFreq != null) {
            mPerformanceMinFreq.setOnPreferenceChangeListener(this);
        }
        if (mPerformanceMaxFreq != null) {
            mPerformanceMaxFreq.setOnPreferenceChangeListener(this);
        }
    }

    private void loadCurrentSettings() {
        if (!mKernelUtils.isKernelManagerSupported()) {
            return;
        }

        // Load governors
        String[] availableGovernors = mKernelUtils.getAvailableGovernors();
        if (mGovernorPreference != null && availableGovernors != null) {
            String[] governorLabels = new String[availableGovernors.length];
            for (int i = 0; i < availableGovernors.length; i++) {
                governorLabels[i] = formatGovernorLabel(availableGovernors[i]);
            }
            mGovernorPreference.setEntries(governorLabels);
            mGovernorPreference.setEntryValues(availableGovernors);
            
            String savedGovernor = mSharedPrefs.getString(KEY_CPU_GOVERNOR, null);
            if (savedGovernor == null) {
                savedGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            }
            mGovernorPreference.setValue(savedGovernor);
            mGovernorPreference.setSummary(formatGovernorLabel(savedGovernor));
        }

        // Load frequencies
        loadFrequencyPreferences();
    }

    private String formatGovernorLabel(String governor) {
        switch (governor) {
            case "walt":
                return "Walt (Default)";
            case "schedutil":
                return "Schedutil (Responsive)";
            case "performance":
                return "Performance (Fast)";
            case "powersave":
                return "Powersave (Battery)";
            case "ondemand":
                return "OnDemand (Dynamic)";
            case "conservative":
                return "Conservative (Smooth)";
            default:
                return governor.substring(0, 1).toUpperCase() + 
                       (governor.length() > 1 ? governor.substring(1) : "");
        }
    }

    private void loadFrequencyPreferences() {
        loadClusterFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER, 
            mEfficiencyMinFreq, mEfficiencyMaxFreq, 
            KEY_EFFICIENCY_MIN_FREQ, KEY_EFFICIENCY_MAX_FREQ);
        
        loadClusterFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER, 
            mPerformanceMinFreq, mPerformanceMaxFreq, 
            KEY_PERFORMANCE_MIN_FREQ, KEY_PERFORMANCE_MAX_FREQ);
    }

    private void loadClusterFrequencies(int cluster, 
            ListPreference minPref, ListPreference maxPref, 
            String minKey, String maxKey) {
        
        String[] availableFreqs = mKernelUtils.getAvailableFrequencies(cluster);
        if (availableFreqs == null || minPref == null || maxPref == null) {
            return;
        }

        String[] freqLabels = new String[availableFreqs.length];
        for (int i = 0; i < availableFreqs.length; i++) {
            freqLabels[i] = formatFrequency(availableFreqs[i]);
        }

        minPref.setEntries(freqLabels);
        minPref.setEntryValues(availableFreqs);
        maxPref.setEntries(freqLabels);
        maxPref.setEntryValues(availableFreqs);

        String savedMinFreq = mSharedPrefs.getString(minKey, null);
        if (savedMinFreq == null) {
            savedMinFreq = mKernelUtils.getCurrentMinFrequency(cluster);
        }
        minPref.setValue(savedMinFreq);
        minPref.setSummary(formatFrequency(savedMinFreq));

        String savedMaxFreq = mSharedPrefs.getString(maxKey, null);
        if (savedMaxFreq == null) {
            savedMaxFreq = mKernelUtils.getCurrentMaxFrequency(cluster);
        }
        maxPref.setValue(savedMaxFreq);
        maxPref.setSummary(formatFrequency(savedMaxFreq));
    }

    private void startCpuMonitoring() {
        if (mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }

        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCpuInfo();
                if (mMonitoringEnabled) {
                    mUpdateHandler.postDelayed(this, mUpdateIntervalMs);
                }
            }
        };

        mUpdateHandler.post(mUpdateRunnable);
    }

    private void updateCpuInfo() {
        if (!mMonitoringEnabled || !isResumed()) {
            return;
        }

        try {
            // Update cluster summaries
            updateClusterSummary(mClusterSummaryEfficiency, KernelManagerUtils.EFFICIENCY_CLUSTER);
            updateClusterSummary(mClusterSummaryPerformance, KernelManagerUtils.PERFORMANCE_CLUSTER);

            // Update individual core info
            for (int i = 0; i < 8; i++) {
                updateCoreInfo(i);
            }
        } catch (Exception e) {
            // Silently handle errors to avoid excessive logging
        }
    }

    private void updateClusterSummary(Preference pref, int cluster) {
        if (pref == null) return;

        try {
            String governor = mKernelUtils.getCurrentGovernor(cluster);
            String minFreq = formatFrequency(mKernelUtils.getCurrentMinFrequency(cluster));
            String maxFreq = formatFrequency(mKernelUtils.getCurrentMaxFrequency(cluster));
            
            int[] cores = mKernelUtils.getClusterCores(cluster);
            int onlineCount = 0;
            for (int coreId : cores) {
                if (mKernelUtils.isCoreOnline(coreId)) {
                    onlineCount++;
                }
            }

            String summary = String.format("Governor: %s | %s - %s | Cores: %d/%d online", 
                formatGovernorLabel(governor), minFreq, maxFreq, onlineCount, cores.length);
            
            pref.setSummary(summary);
        } catch (Exception e) {
            pref.setSummary("Error reading cluster info");
        }
    }

    private void updateCoreInfo(int coreId) {
        if (coreId < 0 || coreId >= mCpuCorePreferences.length) {
            return;
        }

        Preference pref = mCpuCorePreferences[coreId];
        if (pref == null) return;

        try {
            boolean isOnline = mKernelUtils.isCoreOnline(coreId);
            String status = isOnline ? "Online" : "Offline";
            String freq = isOnline ? formatFrequency(mKernelUtils.getCurrentCoreFrequency(coreId)) : "---";
            
            pref.setSummary(status + " @ " + freq);
        } catch (Exception e) {
            pref.setSummary("Error");
        }
    }

    private void updateFrequencySummary(ListPreference pref, int cluster, boolean isMin) {
        if (pref == null) return;

        try {
            String currentFreq = isMin ? 
                mKernelUtils.getCurrentMinFrequency(cluster) : 
                mKernelUtils.getCurrentMaxFrequency(cluster);
            pref.setSummary(formatFrequency(currentFreq));
        } catch (Exception e) {
            pref.setSummary("Error");
        }
    }

    private void updateAllFrequencySummaries() {
        updateFrequencySummary(mEfficiencyMinFreq, KernelManagerUtils.EFFICIENCY_CLUSTER, true);
        updateFrequencySummary(mEfficiencyMaxFreq, KernelManagerUtils.EFFICIENCY_CLUSTER, false);
        updateFrequencySummary(mPerformanceMinFreq, KernelManagerUtils.PERFORMANCE_CLUSTER, true);
        updateFrequencySummary(mPerformanceMaxFreq, KernelManagerUtils.PERFORMANCE_CLUSTER, false);
    }

    private String formatFrequency(String freqKHz) {
        if (freqKHz == null || freqKHz.equals("0")) {
            return getString(R.string.kernel_manager_frequency_na);
        }
        try {
            long freq = Long.parseLong(freqKHz);
            if (freq >= 1000000) {
                return String.format("%.2f GHz", freq / 1000000.0);
            } else {
                return String.format("%.0f MHz", freq / 1000.0);
            }
        } catch (NumberFormatException e) {
            return freqKHz + " kHz";
        }
    }

    private void applySettings() {
        if (!mKernelUtils.isKernelManagerSupported()) {
            Toast.makeText(getContext(), R.string.kernel_manager_error_read, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Validate frequency ranges first
        if (!validateFrequencyRanges()) {
            return;
        }

        SharedPreferences.Editor editor = mSharedPrefs.edit();
        boolean allSuccess = true;
        StringBuilder errorMessage = new StringBuilder();

        if (mGovernorPreference != null) {
            String governor = mGovernorPreference.getValue();
            if (governor != null) {
                if (mKernelUtils.setGovernor(governor)) {
                    editor.putString(KEY_CPU_GOVERNOR, governor);
                } else {
                    allSuccess = false;
                    errorMessage.append("Failed to set governor: ").append(governor).append("\n");
                }
            }
        }

        // FIX: applyFrequencySettings now returns boolean so the result is
        // correctly reflected in allSuccess. Previously it received allSuccess
        // by value (Java primitives are pass-by-value), so failures inside the
        // method never propagated back and the "success" toast always showed.
        if (!applyFrequencySettings(editor, errorMessage)) {
            allSuccess = false;
        }

        editor.apply();

        if (allSuccess) {
            Toast.makeText(getContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show();
        } else {
            String finalError = errorMessage.length() > 0 ? 
                errorMessage.toString().trim() : 
                getString(R.string.kernel_manager_error_write);
            Toast.makeText(getContext(), finalError, Toast.LENGTH_LONG).show();
        }
    }

    private boolean validateFrequencyRanges() {
        if (mEfficiencyMinFreq != null && mEfficiencyMaxFreq != null) {
            String minFreq = mEfficiencyMinFreq.getValue();
            String maxFreq = mEfficiencyMaxFreq.getValue();
            if (minFreq != null && maxFreq != null) {
                if (!mKernelUtils.validateFrequencyRange(KernelManagerUtils.EFFICIENCY_CLUSTER, minFreq, maxFreq)) {
                    Toast.makeText(getContext(), "Invalid efficiency cluster frequency range", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        
        if (mPerformanceMinFreq != null && mPerformanceMaxFreq != null) {
            String minFreq = mPerformanceMinFreq.getValue();
            String maxFreq = mPerformanceMaxFreq.getValue();
            if (minFreq != null && maxFreq != null) {
                if (!mKernelUtils.validateFrequencyRange(KernelManagerUtils.PERFORMANCE_CLUSTER, minFreq, maxFreq)) {
                    Toast.makeText(getContext(), "Invalid performance cluster frequency range", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * FIX: Now returns boolean instead of void.
     * Previously allSuccess was passed by value so write failures inside this
     * method were invisible to the caller — applySettings() always showed the
     * "success" toast even when frequency writes failed.
     */
    private boolean applyFrequencySettings(SharedPreferences.Editor editor, StringBuilder errorMessage) {
        boolean success = true;

        if (mEfficiencyMinFreq != null) {
            String freq = mEfficiencyMinFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq)) {
                    editor.putString(KEY_EFFICIENCY_MIN_FREQ, freq);
                } else {
                    success = false;
                    errorMessage.append("Failed to set efficiency min frequency\n");
                }
            }
        }
        if (mEfficiencyMaxFreq != null) {
            String freq = mEfficiencyMaxFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq)) {
                    editor.putString(KEY_EFFICIENCY_MAX_FREQ, freq);
                } else {
                    success = false;
                    errorMessage.append("Failed to set efficiency max frequency\n");
                }
            }
        }
        if (mPerformanceMinFreq != null) {
            String freq = mPerformanceMinFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq)) {
                    editor.putString(KEY_PERFORMANCE_MIN_FREQ, freq);
                } else {
                    success = false;
                    errorMessage.append("Failed to set performance min frequency\n");
                }
            }
        }
        if (mPerformanceMaxFreq != null) {
            String freq = mPerformanceMaxFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq)) {
                    editor.putString(KEY_PERFORMANCE_MAX_FREQ, freq);
                } else {
                    success = false;
                    errorMessage.append("Failed to set performance max frequency\n");
                }
            }
        }

        return success;
    }

    private void resetSettings() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.remove(KEY_CPU_GOVERNOR);
        editor.remove(KEY_EFFICIENCY_MIN_FREQ);
        editor.remove(KEY_EFFICIENCY_MAX_FREQ);
        editor.remove(KEY_PERFORMANCE_MIN_FREQ);
        editor.remove(KEY_PERFORMANCE_MAX_FREQ);
        editor.apply();
        mKernelUtils.resetToDefaults();
        loadCurrentSettings();
        Toast.makeText(getContext(), R.string.kernel_manager_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putString(key, (String) newValue);
        editor.apply();
        if (preference instanceof ListPreference) {
            ListPreference listPref = (ListPreference) preference;
            int index = listPref.findIndexOfValue((String) newValue);
            if (index >= 0 && index < listPref.getEntries().length) {
                CharSequence summary = listPref.getEntries()[index];
                listPref.setSummary(summary);
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Battery optimization: Stop monitoring when not visible
        stopCpuMonitoring();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Restart monitoring only if enabled
        if (mMonitoringEnabled) {
            startCpuMonitoring();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCpuMonitoring();
    }

    private void stopCpuMonitoring() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
    }
}
