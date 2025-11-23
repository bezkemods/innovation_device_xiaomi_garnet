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
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

import java.util.Arrays;
import java.util.Locale;

public class KernelManagerFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "KernelManagerFragment";
    private static final int UPDATE_INTERVAL = 2000; // 2 másodperc

    // Preference Keys
    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFFICIENCY_MIN_FREQ = "efficiency_min_freq";
    private static final String KEY_EFFICIENCY_MAX_FREQ = "efficiency_max_freq";
    private static final String KEY_PERFORMANCE_MIN_FREQ = "performance_min_freq";
    private static final String KEY_PERFORMANCE_MAX_FREQ = "performance_max_freq";
    private static final String KEY_APPLY_SETTINGS = "apply_settings";
    private static final String KEY_RESET_SETTINGS = "reset_settings";
    private static final String KEY_CPU_MONITOR = "cpu_monitor";
    private static final String KEY_EFFICIENCY_CURRENT_FREQ = "efficiency_current_freq";
    private static final String KEY_PERFORMANCE_CURRENT_FREQ = "performance_current_freq";
    private static final String KEY_DEBUG_INFO = "debug_info";

    // Preferences
    private ListPreference mGovernorPreference;
    private ListPreference mEfficiencyMinFreq;
    private ListPreference mEfficiencyMaxFreq;
    private ListPreference mPerformanceMinFreq;
    private ListPreference mPerformanceMaxFreq;
    private Preference mApplySettings;
    private Preference mResetSettings;
    private SwitchPreference mCpuMonitor;
    private Preference mEfficiencyCurrentFreq;
    private Preference mPerformanceCurrentFreq;
    private Preference mDebugInfo;

    private KernelManagerUtils mKernelUtils;
    private SharedPreferences mSharedPrefs;

    // CPU monitoring
    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;
    private boolean mMonitoringEnabled = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);

        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mUpdateHandler = new Handler(Looper.getMainLooper());

        mGovernorPreference = findPreference(KEY_CPU_GOVERNOR);
        mEfficiencyMinFreq = findPreference(KEY_EFFICIENCY_MIN_FREQ);
        mEfficiencyMaxFreq = findPreference(KEY_EFFICIENCY_MAX_FREQ);
        mPerformanceMinFreq = findPreference(KEY_PERFORMANCE_MIN_FREQ);
        mPerformanceMaxFreq = findPreference(KEY_PERFORMANCE_MAX_FREQ);
        mApplySettings = findPreference(KEY_APPLY_SETTINGS);
        mResetSettings = findPreference(KEY_RESET_SETTINGS);
        mCpuMonitor = findPreference(KEY_CPU_MONITOR);
        mEfficiencyCurrentFreq = findPreference(KEY_EFFICIENCY_CURRENT_FREQ);
        mPerformanceCurrentFreq = findPreference(KEY_PERFORMANCE_CURRENT_FREQ);
        mDebugInfo = findPreference(KEY_DEBUG_INFO);

        if (!mKernelUtils.isKernelManagerSupported()) {
            Toast.makeText(getContext(), R.string.kernel_manager_error_not_supported, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Setup Preferences
        setupGovernorPreference();
        setupFrequencyPreferences();

        // Set listeners
        mGovernorPreference.setOnPreferenceChangeListener(this);
        mEfficiencyMinFreq.setOnPreferenceChangeListener(this);
        mEfficiencyMaxFreq.setOnPreferenceChangeListener(this);
        mPerformanceMinFreq.setOnPreferenceChangeListener(this);
        mPerformanceMaxFreq.setOnPreferenceChangeListener(this);
        mCpuMonitor.setOnPreferenceChangeListener(this);
        
        mApplySettings.setOnPreferenceClickListener(preference -> {
            applySettings();
            return true;
        });

        mResetSettings.setOnPreferenceClickListener(preference -> {
            resetSettings();
            return true;
        });
        
        if (mDebugInfo != null) {
             mDebugInfo.setOnPreferenceClickListener(preference -> {
                 showDebugInfo();
                 return true;
             });
        }


        // Load initial state for CPU monitoring
        mMonitoringEnabled = mSharedPrefs.getBoolean(KEY_CPU_MONITOR, false);
        mCpuMonitor.setChecked(mMonitoringEnabled);
        
        // Initialize Monitoring Runnable
        mUpdateRunnable = () -> {
            updateCpuStats();
            if (mMonitoringEnabled) {
                mUpdateHandler.postDelayed(mUpdateRunnable, UPDATE_INTERVAL);
            }
        };
        
        // Update summaries and load current values
        updatePreferences();
        
        // Start monitoring if enabled
        if (mMonitoringEnabled) {
            startCpuMonitoring();
        }
    }
    
    // --- Setup Helper Methods ---

    private void setupGovernorPreference() {
        String[] availableGovernors = mKernelUtils.getAvailableGovernors();
        if (availableGovernors.length > 0) {
            mGovernorPreference.setEntries(availableGovernors);
            mGovernorPreference.setEntryValues(availableGovernors);
        } else {
            // Remove the preference if no governors are available
            getPreferenceScreen().removePreference(mGovernorPreference.getParent());
        }
    }

    private void setupFrequencyPreferences() {
        // Efficiency Cluster (Policy 0)
        String[] effFreqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER);
        if (effFreqs.length > 0) {
            mEfficiencyMinFreq.setEntries(effFreqs);
            mEfficiencyMinFreq.setEntryValues(effFreqs);
            mEfficiencyMaxFreq.setEntries(effFreqs);
            mEfficiencyMaxFreq.setEntryValues(effFreqs);
        } else {
            // Remove category if not supported
            getPreferenceScreen().removePreference(mEfficiencyMinFreq.getParent());
        }

        // Performance Cluster (Policy 4)
        String[] perfFreqs = mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER);
        if (perfFreqs.length > 0) {
            mPerformanceMinFreq.setEntries(perfFreqs);
            mPerformanceMinFreq.setEntryValues(perfFreqs);
            mPerformanceMaxFreq.setEntries(perfFreqs);
            mPerformanceMaxFreq.setEntryValues(perfFreqs);
        } else {
            // Remove category if not supported
            getPreferenceScreen().removePreference(mPerformanceMinFreq.getParent());
        }
    }

    // --- Core Logic ---

    private void applySettings() {
        if (!mKernelUtils.isKernelManagerSupported()) {
            Toast.makeText(getContext(), R.string.kernel_manager_error_read, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = mSharedPrefs.edit();
        boolean allSuccess = true;
        StringBuilder errorMessage = new StringBuilder();

        // 1. Validate Ranges First (Basic check: Min <= Max)
        if (mEfficiencyMinFreq != null && mEfficiencyMaxFreq != null) {
            String minFreq = mEfficiencyMinFreq.getValue();
            String maxFreq = mEfficiencyMaxFreq.getValue();
            if (minFreq != null && maxFreq != null && !mKernelUtils.validateFrequencyRange(KernelManagerUtils.EFFICIENCY_CLUSTER, minFreq, maxFreq)) {
                errorMessage.append("Invalid efficiency cluster frequency range (Min > Max)\n");
                allSuccess = false;
            }
        }
        if (mPerformanceMinFreq != null && mPerformanceMaxFreq != null) {
            String minFreq = mPerformanceMinFreq.getValue();
            String maxFreq = mPerformanceMaxFreq.getValue();
            if (minFreq != null && maxFreq != null && !mKernelUtils.validateFrequencyRange(KernelManagerUtils.PERFORMANCE_CLUSTER, minFreq, maxFreq)) {
                errorMessage.append("Invalid performance cluster frequency range (Min > Max)\n");
                allSuccess = false;
            }
        }
        
        // Ha a validáció sikertelen, megáll
        if (!allSuccess) {
            Toast.makeText(getContext(), errorMessage.toString().trim(), Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Apply Settings in correct order

        // Governor
        if (mGovernorPreference != null) {
            String governor = mGovernorPreference.getValue();
            if (governor != null) {
                if (!mKernelUtils.setGovernor(governor)) {
                    allSuccess = false;
                    errorMessage.append("Failed to set governor: ").append(governor).append("\n");
                } else {
                    editor.putString(KEY_CPU_GOVERNOR, governor);
                }
            }
        }

        // Efficiency Cluster (MAX then MIN - JAVÍTOTT SORREND)
        if (mEfficiencyMinFreq != null && mEfficiencyMaxFreq != null) {
            String min = mEfficiencyMinFreq.getValue();
            String max = mEfficiencyMaxFreq.getValue();
            if (min != null && max != null) {
                // Set Max (FIRST - CRITICAL FIX)
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, max)) {
                    editor.putString(KEY_EFFICIENCY_MAX_FREQ, max);
                } else {
                    allSuccess = false;
                    errorMessage.append("Failed to set efficiency max freq\n");
                }
                // Set Min (SECOND)
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, min)) {
                    editor.putString(KEY_EFFICIENCY_MIN_FREQ, min);
                } else {
                    allSuccess = false;
                    errorMessage.append("Failed to set efficiency min freq\n");
                }
            }
        }

        // Performance Cluster (MAX then MIN - JAVÍTOTT SORREND)
        if (mPerformanceMinFreq != null && mPerformanceMaxFreq != null) {
            String min = mPerformanceMinFreq.getValue();
            String max = mPerformanceMaxFreq.getValue();
            if (min != null && max != null) {
                // Set Max (FIRST - CRITICAL FIX)
                if (mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, max)) {
                    editor.putString(KEY_PERFORMANCE_MAX_FREQ, max);
                } else {
                    allSuccess = false;
                    errorMessage.append("Failed to set performance max freq\n");
                }
                // Set Min (SECOND)
                if (mKernelUtils.setMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, min)) {
                    editor.putString(KEY_PERFORMANCE_MIN_FREQ, min);
                } else {
                    allSuccess = false;
                    errorMessage.append("Failed to set performance min freq\n");
                }
            }
        }

        editor.apply();
        updatePreferences(); // Frissíti a kijelzést, hogy az új értékek megjelenjenek

        if (allSuccess) {
            Toast.makeText(getContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show();
        } else {
            String finalError = errorMessage.length() > 0 ? 
                errorMessage.toString().trim() : 
                getString(R.string.kernel_manager_error_write);
            Toast.makeText(getContext(), finalError, Toast.LENGTH_LONG).show();
        }
    }

    private void resetSettings() {
        if (!mKernelUtils.isKernelManagerSupported()) return;

        SharedPreferences.Editor editor = mSharedPrefs.edit();
        
        // Clear all saved settings
        editor.remove(KEY_CPU_GOVERNOR);
        editor.remove(KEY_EFFICIENCY_MIN_FREQ);
        editor.remove(KEY_EFFICIENCY_MAX_FREQ);
        editor.remove(KEY_PERFORMANCE_MIN_FREQ);
        editor.remove(KEY_PERFORMANCE_MAX_FREQ);
        editor.apply();

        // Apply default settings (by writing default values)
        mKernelUtils.resetKernelSettings();

        // Frissíti a kijelzést
        updatePreferences();
        
        Toast.makeText(getContext(), R.string.settings_reset, Toast.LENGTH_SHORT).show();
    }

    // --- Monitoring Logic ---

    private void startCpuMonitoring() {
        if (mMonitoringEnabled) {
            mUpdateHandler.post(mUpdateRunnable);
            Log.d(TAG, "CPU monitoring started");
        }
    }

    private void stopCpuMonitoring() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
            Log.d(TAG, "CPU monitoring stopped");
        }
    }

    private void updateCpuStats() {
        // Read current frequencies
        String effCurFreq = mKernelUtils.getCurrentFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER);
        String perfCurFreq = mKernelUtils.getCurrentFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER);

        // Read CPU core stats
        KernelManagerUtils.CpuStats stats = mKernelUtils.getCpuStats();

        // Format and update Efficiency cluster
        if (mEfficiencyCurrentFreq != null && effCurFreq != null) {
            String freqMhz = mKernelUtils.formatFrequency(effCurFreq);
            String summary = String.format(Locale.getDefault(),
                "%s (%d/%d online)", freqMhz, stats.efficiencyOnline, stats.efficiencyTotal);
            mEfficiencyCurrentFreq.setSummary(summary);
        }

        // Format and update Performance cluster
        if (mPerformanceCurrentFreq != null && perfCurFreq != null) {
            String freqMhz = mKernelUtils.formatFrequency(perfCurFreq);
            String summary = String.format(Locale.getDefault(),
                "%s (%d/%d online)", freqMhz, stats.performanceOnline, stats.performanceTotal);
            mPerformanceCurrentFreq.setSummary(summary);
        }
    }
    
    // --- Lifecycle and Helper Methods ---

    private void updatePreferences() {
        // Load values from SharedPreferences or current kernel state
        String gov = mSharedPrefs.getString(KEY_CPU_GOVERNOR, mKernelUtils.getCurrentGovernor());
        String effMin = mSharedPrefs.getString(KEY_EFFICIENCY_MIN_FREQ, mKernelUtils.getMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
        String effMax = mSharedPrefs.getString(KEY_EFFICIENCY_MAX_FREQ, mKernelUtils.getMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
        String perfMin = mSharedPrefs.getString(KEY_PERFORMANCE_MIN_FREQ, mKernelUtils.getMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));
        String perfMax = mSharedPrefs.getString(KEY_PERFORMANCE_MAX_FREQ, mKernelUtils.getMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));

        // Update Governor
        if (mGovernorPreference != null && gov != null) {
            mGovernorPreference.setValue(gov);
            mGovernorPreference.setSummary(gov);
        }

        // Update Efficiency Freq
        if (mEfficiencyMinFreq != null && effMin != null) {
            mEfficiencyMinFreq.setValue(effMin);
            mEfficiencyMinFreq.setSummary(mKernelUtils.formatFrequency(effMin));
        }
        if (mEfficiencyMaxFreq != null && effMax != null) {
            mEfficiencyMaxFreq.setValue(effMax);
            mEfficiencyMaxFreq.setSummary(mKernelUtils.formatFrequency(effMax));
        }

        // Update Performance Freq
        if (mPerformanceMinFreq != null && perfMin != null) {
            mPerformanceMinFreq.setValue(perfMin);
            mPerformanceMinFreq.setSummary(mKernelUtils.formatFrequency(perfMin));
        }
        if (mPerformanceMaxFreq != null && perfMax != null) {
            mPerformanceMaxFreq.setValue(perfMax);
            mPerformanceMaxFreq.setSummary(mKernelUtils.formatFrequency(perfMax));
        }
        
        // Initial stat update
        updateCpuStats();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String stringValue = (String) newValue;

        if (KEY_CPU_MONITOR.equals(key)) {
            mMonitoringEnabled = (boolean) newValue;
            mSharedPrefs.edit().putBoolean(KEY_CPU_MONITOR, mMonitoringEnabled).apply();
            if (mMonitoringEnabled) {
                startCpuMonitoring();
            } else {
                stopCpuMonitoring();
            }
            return true;
        } else if (key.endsWith("_freq") || KEY_CPU_GOVERNOR.equals(key)) {
            // Update summary immediately for ListPreferences
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                CharSequence summary = index >= 0 ? listPreference.getEntries()[index] : null;
                
                // Format summary for frequencies
                if (key.endsWith("_freq") && summary != null) {
                    summary = mKernelUtils.formatFrequency(stringValue);
                }
                
                listPreference.setSummary(summary);
            }
        }
        // Settings are applied only via the explicit "Apply Settings" button
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
        updatePreferences(); // Always refresh to show current kernel state
        if (mMonitoringEnabled) {
            startCpuMonitoring();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCpuMonitoring();
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
