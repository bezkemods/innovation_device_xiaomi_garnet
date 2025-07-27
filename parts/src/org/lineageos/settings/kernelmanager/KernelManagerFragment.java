/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceCategory;
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

    private static final int DEFAULT_UPDATE_INTERVAL_MS = 1000;

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
    
    private boolean mMonitoringEnabled = true;
    private int mUpdateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);
        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mUpdateHandler = new Handler(Looper.getMainLooper());

        // Load monitoring preferences
        mMonitoringEnabled = mSharedPrefs.getBoolean(KEY_ENABLE_MONITORING, true);
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

        // Set listeners
        if (mGovernorPreference != null) {
            mGovernorPreference.setOnPreferenceChangeListener(this);
        }

        setFrequencyPreferenceListeners();

        // Apply and Reset buttons
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
        
        // Debug info preference (hidden by default)
        Preference debugPref = new Preference(getContext());
        debugPref.setKey("debug_info");
        debugPref.setTitle("Debug Info");
        debugPref.setSummary("Show kernel manager debug information");
        debugPref.setVisible(false); // Set to true for debugging
        debugPref.setOnPreferenceClickListener(preference -> {
            showDebugInfo();
            return true;
        });
    }

    private void initializeCpuMonitoring() {
        // CPU monitor kategória létrehozása
        mCpuMonitorCategory = new PreferenceCategory(getContext());
        mCpuMonitorCategory.setKey(KEY_CPU_MONITOR);
        mCpuMonitorCategory.setTitle(getString(R.string.kernel_manager_cpu_monitor));
        getPreferenceScreen().addPreference(mCpuMonitorCategory);

        // Monitoring engedélyezése/letiltása
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

        // Frissítési intervallum beállítása
        mUpdateIntervalPreference = new ListPreference(getContext());
        mUpdateIntervalPreference.setKey(KEY_UPDATE_INTERVAL);
        mUpdateIntervalPreference.setTitle(getString(R.string.kernel_manager_update_interval));
        mUpdateIntervalPreference.setSummary(getString(R.string.kernel_manager_update_interval_summary));
        mUpdateIntervalPreference.setEntries(new String[]{
            getString(R.string.update_interval_500ms), 
            getString(R.string.update_interval_1000ms), 
            getString(R.string.update_interval_2000ms), 
            getString(R.string.update_interval_5000ms)
        });
        mUpdateIntervalPreference.setEntryValues(new String[]{"500", "1000", "2000", "5000"});
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

        // Klaszter összefoglalók
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

        // CPU magok preference-jeinek létrehozása
        for (int i = 0; i < 8; i++) {
            mCpuCorePreferences[i] = new Preference(getContext());
            mCpuCorePreferences[i].setKey("cpu_core_" + i);
            
            // Mag típus meghatározása
            String coreType;
            if (i < 4) {
                coreType = "Efficiency (A55)";
            } else {
                coreType = "Performance (A78)";
            }
            
            mCpuCorePreferences[i].setTitle("CPU " + i + " (" + coreType + ")");
            mCpuCorePreferences[i].setSummary(getString(R.string.kernel_manager_loading));
            mCpuCorePreferences[i].setSelectable(false);
            mCpuMonitorCategory.addPreference(mCpuCorePreferences[i]);
        }

        updateMonitoringVisibility();
    }

    private void updateMonitoringVisibility() {
        boolean visible = mMonitoringEnabled;
        
        mUpdateIntervalPreference.setVisible(visible);
        mClusterSummaryEfficiency.setVisible(visible);
        mClusterSummaryPerformance.setVisible(visible);
        
        for (Preference pref : mCpuCorePreferences) {
            if (pref != null) {
                pref.setVisible(visible);
            }
        }
    }

    private void setFrequencyPreferenceListeners() {
        if (mEfficiencyMinFreq != null) mEfficiencyMinFreq.setOnPreferenceChangeListener(this);
        if (mEfficiencyMaxFreq != null) mEfficiencyMaxFreq.setOnPreferenceChangeListener(this);
        if (mPerformanceMinFreq != null) mPerformanceMinFreq.setOnPreferenceChangeListener(this);
        if (mPerformanceMaxFreq != null) mPerformanceMaxFreq.setOnPreferenceChangeListener(this);
    }

    private void loadCurrentSettings() {
        // Load current governor and frequencies for both clusters
        if (mGovernorPreference != null) {
            String savedGovernor = mSharedPrefs.getString(KEY_CPU_GOVERNOR, 
                mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mGovernorPreference.setValue(savedGovernor);
            mGovernorPreference.setEntries(mKernelUtils.getAvailableGovernors());
            mGovernorPreference.setEntryValues(mKernelUtils.getAvailableGovernors());
            updateGovernorSummary();
        }
        
        loadFrequencySettings();
    }

    private void loadFrequencySettings() {
        if (mEfficiencyMinFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_EFFICIENCY_MIN_FREQ,
                mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMinFreq.setValue(savedFreq);
            mEfficiencyMinFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMinFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            updateFrequencySummary(mEfficiencyMinFreq, KernelManagerUtils.EFFICIENCY_CLUSTER, true);
        }
        
        if (mEfficiencyMaxFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_EFFICIENCY_MAX_FREQ,
                mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMaxFreq.setValue(savedFreq);
            mEfficiencyMaxFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMaxFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            updateFrequencySummary(mEfficiencyMaxFreq, KernelManagerUtils.EFFICIENCY_CLUSTER, false);
        }
        
        if (mPerformanceMinFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_PERFORMANCE_MIN_FREQ,
                mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMinFreq.setValue(savedFreq);
            mPerformanceMinFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMinFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            updateFrequencySummary(mPerformanceMinFreq, KernelManagerUtils.PERFORMANCE_CLUSTER, true);
        }
        
        if (mPerformanceMaxFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_PERFORMANCE_MAX_FREQ,
                mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMaxFreq.setValue(savedFreq);
            mPerformanceMaxFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMaxFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            updateFrequencySummary(mPerformanceMaxFreq, KernelManagerUtils.PERFORMANCE_CLUSTER, false);
        }
    }

    private void updateGovernorSummary() {
        if (mGovernorPreference != null) {
            String currentGovernor = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            String baseSummary = getString(R.string.kernel_manager_governor_summary);
            mGovernorPreference.setSummary(getString(R.string.kernel_manager_governor_current, 
                baseSummary, currentGovernor));
        }
    }

    private void updateFrequencySummary(ListPreference pref, int cluster, boolean isMin) {
        if (pref != null) {
            String currentFreq = isMin ? 
                mKernelUtils.getCurrentMinFrequency(cluster) : 
                mKernelUtils.getCurrentMaxFrequency(cluster);
            
            String freqMHz = formatFrequency(currentFreq);
            String baseSummary = isMin ? 
                getString(R.string.kernel_manager_min_freq_summary) :
                getString(R.string.kernel_manager_max_freq_summary);
            
            pref.setSummary(getString(R.string.kernel_manager_freq_current, baseSummary, freqMHz));
        }
    }

    private void startCpuMonitoring() {
        if (mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
        
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mMonitoringEnabled) {
                    updateCpuFrequencies();
                    updateClusterSummaries();
                    updateFrequencySummaries();
                    updateGovernorSummary();
                    mUpdateHandler.postDelayed(this, mUpdateIntervalMs);
                }
            }
        };
        mUpdateHandler.post(mUpdateRunnable);
    }

    private void updateCpuFrequencies() {
        for (int i = 0; i < 8; i++) {
            if (mCpuCorePreferences[i] != null) {
                String currentFreq = mKernelUtils.getCurrentCoreFrequency(i);
                String freqMHz = formatFrequency(currentFreq);
                
                boolean isOnline = mKernelUtils.isCoreOnline(i);
                
                if (isOnline) {
                    mCpuCorePreferences[i].setSummary(getString(R.string.cpu_core_status_online, freqMHz));
                } else {
                    mCpuCorePreferences[i].setSummary(getString(R.string.cpu_core_status_offline, freqMHz));
                }
            }
        }
    }

    private void updateClusterSummaries() {
        // Efficiency cluster summary (CPU 0-3)
        int onlineEfficiency = 0;
        for (int i = 0; i < 4; i++) {
            if (mKernelUtils.isCoreOnline(i)) {
                onlineEfficiency++;
            }
        }
        
        mClusterSummaryEfficiency.setSummary(getString(R.string.kernel_manager_cluster_status,
            onlineEfficiency, 4,
            formatFrequency(mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER)),
            formatFrequency(mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER))
        ));

        // Performance cluster summary (CPU 4-7)
        int onlinePerformance = 0;
        for (int i = 4; i < 8; i++) {
            if (mKernelUtils.isCoreOnline(i)) {
                onlinePerformance++;
            }
        }
        
        mClusterSummaryPerformance.setSummary(getString(R.string.kernel_manager_cluster_status,
            onlinePerformance, 4,
            formatFrequency(mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER)),
            formatFrequency(mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER))
        ));
    }

    private void updateFrequencySummaries() {
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

        SharedPreferences.Editor editor = mSharedPrefs.edit();
        boolean allSuccess = true;
        
        // Validate frequency ranges before applying
        if (mEfficiencyMinFreq != null && mEfficiencyMaxFreq != null) {
            String minFreq = mEfficiencyMinFreq.getValue();
            String maxFreq = mEfficiencyMaxFreq.getValue();
            if (minFreq != null && maxFreq != null) {
                if (!mKernelUtils.validateFrequencyRange(KernelManagerUtils.EFFICIENCY_CLUSTER, minFreq, maxFreq)) {
                    Toast.makeText(getContext(), "Invalid efficiency cluster frequency range", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        
        if (mPerformanceMinFreq != null && mPerformanceMaxFreq != null) {
            String minFreq = mPerformanceMinFreq.getValue();
            String maxFreq = mPerformanceMaxFreq.getValue();
            if (minFreq != null && maxFreq != null) {
                if (!mKernelUtils.validateFrequencyRange(KernelManagerUtils.PERFORMANCE_CLUSTER, minFreq, maxFreq)) {
                    Toast.makeText(getContext(), "Invalid performance cluster frequency range", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        
        // Apply governor
        if (mGovernorPreference != null) {
            String governor = mGovernorPreference.getValue();
            if (governor != null) {
                if (mKernelUtils.setGovernor(governor)) {
                    editor.putString(KEY_CPU_GOVERNOR, governor);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        // Apply efficiency cluster frequencies
        if (mEfficiencyMinFreq != null) {
            String freq = mEfficiencyMinFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq)) {
                    editor.putString(KEY_EFFICIENCY_MIN_FREQ, freq);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        if (mEfficiencyMaxFreq != null) {
            String freq = mEfficiencyMaxFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq)) {
                    editor.putString(KEY_EFFICIENCY_MAX_FREQ, freq);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        // Apply performance cluster frequencies
        if (mPerformanceMinFreq != null) {
            String freq = mPerformanceMinFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq)) {
                    editor.putString(KEY_PERFORMANCE_MIN_FREQ, freq);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        if (mPerformanceMaxFreq != null) {
            String freq = mPerformanceMaxFreq.getValue();
            if (freq != null) {
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq)) {
                    editor.putString(KEY_PERFORMANCE_MAX_FREQ, freq);
                } else {
                    allSuccess = false;
                }
            }
        }
        
        editor.apply();
        
        if (allSuccess) {
            Toast.makeText(getContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), R.string.kernel_manager_error_write, Toast.LENGTH_LONG).show();
        }
    }

    private void resetSettings() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.remove(KEY_CPU_GOVERNOR);
        editor.remove(KEY_EFFICIENCY_MIN_FREQ);
        editor.remove(KEY_EFFICIENCY_MAX_FREQ);
        editor.remove(KEY_PERFORMANCE_MIN_FREQ);
        editor.remove(KEY_PERFORMANCE_MAX_FREQ);
        editor.apply();
        
        loadCurrentSettings();
        Toast.makeText(getContext(), R.string.kernel_manager_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putString(key, (String) newValue);
        editor.apply();
        
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCpuMonitoring();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMonitoringEnabled) {
            startCpuMonitoring();
        }
    }

    private void stopCpuMonitoring() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
    }
    
    private void showDebugInfo() {
        String debugInfo = mKernelUtils.getDebugInfo();
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Kernel Manager Debug Info");
        builder.setMessage(debugInfo);
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Copy", (dialog, which) -> {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Debug Info", debugInfo);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Debug info copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }
}
