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
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
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

    private KernelManagerUtils mKernelUtils;
    private ListPreference mGovernorPreference;
    private ListPreference mEfficiencyMinFreq, mEfficiencyMaxFreq;
    private ListPreference mPerformanceMinFreq, mPerformanceMaxFreq;
    private SharedPreferences mSharedPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);
        mKernelUtils = new KernelManagerUtils();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        initializePreferences();
        loadCurrentSettings();
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
            // First try to load from saved preferences, then current system value
            String savedGovernor = mSharedPrefs.getString(KEY_CPU_GOVERNOR, 
                mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mGovernorPreference.setValue(savedGovernor);
            mGovernorPreference.setEntries(mKernelUtils.getAvailableGovernors());
            mGovernorPreference.setEntryValues(mKernelUtils.getAvailableGovernors());
        }
        
        if (mEfficiencyMinFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_EFFICIENCY_MIN_FREQ,
                mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMinFreq.setValue(savedFreq);
            mEfficiencyMinFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMinFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
        }
        
        if (mEfficiencyMaxFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_EFFICIENCY_MAX_FREQ,
                mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMaxFreq.setValue(savedFreq);
            mEfficiencyMaxFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
            mEfficiencyMaxFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.EFFICIENCY_CLUSTER));
        }
        
        if (mPerformanceMinFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_PERFORMANCE_MIN_FREQ,
                mKernelUtils.getCurrentMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMinFreq.setValue(savedFreq);
            mPerformanceMinFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMinFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
        }
        
        if (mPerformanceMaxFreq != null) {
            String savedFreq = mSharedPrefs.getString(KEY_PERFORMANCE_MAX_FREQ,
                mKernelUtils.getCurrentMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMaxFreq.setValue(savedFreq);
            mPerformanceMaxFreq.setEntries(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
            mPerformanceMaxFreq.setEntryValues(mKernelUtils.getAvailableFrequencies(KernelManagerUtils.PERFORMANCE_CLUSTER));
        }
    }

    private void applySettings() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        
        if (mGovernorPreference != null) {
            String governor = mGovernorPreference.getValue();
            mKernelUtils.setGovernor(governor);
            editor.putString(KEY_CPU_GOVERNOR, governor);
        }
        
        if (mEfficiencyMinFreq != null) {
            String freq = mEfficiencyMinFreq.getValue();
            mKernelUtils.setMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq);
            editor.putString(KEY_EFFICIENCY_MIN_FREQ, freq);
        }
        
        if (mEfficiencyMaxFreq != null) {
            String freq = mEfficiencyMaxFreq.getValue();
            mKernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, freq);
            editor.putString(KEY_EFFICIENCY_MAX_FREQ, freq);
        }
        
        if (mPerformanceMinFreq != null) {
            String freq = mPerformanceMinFreq.getValue();
            mKernelUtils.setMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq);
            editor.putString(KEY_PERFORMANCE_MIN_FREQ, freq);
        }
        
        if (mPerformanceMaxFreq != null) {
            String freq = mPerformanceMaxFreq.getValue();
            mKernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, freq);
            editor.putString(KEY_PERFORMANCE_MAX_FREQ, freq);
        }
        
        editor.apply(); // Save to SharedPreferences
        Toast.makeText(getContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show();
    }

    private void resetSettings() {
        // Clear saved preferences and restore defaults
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.remove(KEY_CPU_GOVERNOR);
        editor.remove(KEY_EFFICIENCY_MIN_FREQ);
        editor.remove(KEY_EFFICIENCY_MAX_FREQ);
        editor.remove(KEY_PERFORMANCE_MIN_FREQ);
        editor.remove(KEY_PERFORMANCE_MAX_FREQ);
        editor.apply();
        
        // Reload current system settings
        loadCurrentSettings();
        
        Toast.makeText(getContext(), R.string.kernel_manager_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Auto-save when preferences change
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putString(key, (String) newValue);
        editor.apply();
        
        return true;
    }
}
