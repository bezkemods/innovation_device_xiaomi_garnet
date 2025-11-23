/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.gpumanager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class GpuManagerFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    // Keys must match gpu_manager_settings.xml
    private static final String KEY_GPU_GOVERNOR = "gpu_governor";
    private static final String KEY_GPU_MIN_FREQ = "gpu_min_freq";
    private static final String KEY_GPU_MAX_FREQ = "gpu_max_freq";
    private static final String KEY_GPU_MAX_GPUCLK = "gpu_max_gpuclk";
    private static final String KEY_GPU_CURRENT_FREQ = "gpu_current_freq";
    private static final String KEY_GPU_MODEL = "gpu_model";
    private static final String KEY_GPU_BUSY_PERCENTAGE = "gpu_busy_percentage";
    private static final String KEY_GPU_TEMPERATURE = "gpu_temperature";
    private static final String KEY_GPU_THERMAL_PWRLEVEL = "gpu_thermal_pwrlevel";
    private static final String KEY_GPU_FORCE_CLK_ON = "gpu_force_clk_on";
    private static final String KEY_GPU_FORCE_BUS_ON = "gpu_force_bus_on";
    private static final String KEY_GPU_FORCE_RAIL_ON = "gpu_force_rail_on";
    private static final String KEY_GPU_FORCE_NO_NAP = "gpu_force_no_nap";
    private static final String KEY_GPU_BUS_SPLIT = "gpu_bus_split";
    private static final String KEY_GPU_PREEMPT = "gpu_preempt";
    private static final String KEY_GPU_CAMERA_BOOST = "gpu_camera_boost";
    private static final String KEY_GPU_RESET_COUNT = "gpu_reset_count";
    private static final String KEY_GPU_PREEMPT_COUNT = "gpu_preempt_count";
    private static final String KEY_APPLY_GPU_SETTINGS = "apply_gpu_settings";
    private static final String KEY_RESET_GPU_SETTINGS = "reset_gpu_settings";
    private static final String KEY_GPU_TURBO_PRESET = "gpu_turbo_preset";

    private GpuManagerUtils mGpuUtils;
    private Handler mHandler;
    private Runnable mUpdateRunnable;
    private SharedPreferences mSharedPrefs;

    // UI Elements
    private ListPreference mGovernorPreference;
    private ListPreference mMinFreqPreference, mMaxFreqPreference, mMaxGpuClkPreference;
    private Preference mCurrentFreqPreference, mGpuBusyPreference, mGpuTemperaturePreference;
    private Preference mThermalPowerLevelPreference, mResetCountPreference, mPreemptCountPreference;
    private SwitchPreference mForceClkOnPreference, mForceBusOnPreference, mForceRailOnPreference;
    private SwitchPreference mForceNoNapPreference, mBusSplitPreference, mPreemptPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gpu_manager_settings, rootKey);
        
        mGpuUtils = new GpuManagerUtils();
        mHandler = new Handler(Looper.getMainLooper());
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        initializePreferences();
        
        // Load initial values
        if (mGpuUtils.isGpuManagerSupported()) {
            loadCurrentSettings();
        } else {
            Toast.makeText(getContext(), "GPU Manager not supported on this device/kernel", Toast.LENGTH_LONG).show();
            getPreferenceScreen().setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start updates only when visible
        startPeriodicUpdates();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop updates to save battery
        stopPeriodicUpdates();
    }

    private void initializePreferences() {
        mGovernorPreference = findPreference(KEY_GPU_GOVERNOR);
        mMinFreqPreference = findPreference(KEY_GPU_MIN_FREQ);
        mMaxFreqPreference = findPreference(KEY_GPU_MAX_FREQ);
        mMaxGpuClkPreference = findPreference(KEY_GPU_MAX_GPUCLK);
        mCurrentFreqPreference = findPreference(KEY_GPU_CURRENT_FREQ);
        mGpuBusyPreference = findPreference(KEY_GPU_BUSY_PERCENTAGE);
        mGpuTemperaturePreference = findPreference(KEY_GPU_TEMPERATURE);
        mThermalPowerLevelPreference = findPreference(KEY_GPU_THERMAL_PWRLEVEL);
        mForceClkOnPreference = findPreference(KEY_GPU_FORCE_CLK_ON);
        mForceBusOnPreference = findPreference(KEY_GPU_FORCE_BUS_ON);
        mForceRailOnPreference = findPreference(KEY_GPU_FORCE_RAIL_ON);
        mForceNoNapPreference = findPreference(KEY_GPU_FORCE_NO_NAP);
        mBusSplitPreference = findPreference(KEY_GPU_BUS_SPLIT);
        mPreemptPreference = findPreference(KEY_GPU_PREEMPT);
        mResetCountPreference = findPreference(KEY_GPU_RESET_COUNT);
        mPreemptCountPreference = findPreference(KEY_GPU_PREEMPT_COUNT);

        Preference mGpuModelPreference = findPreference(KEY_GPU_MODEL);
        if (mGpuModelPreference != null) mGpuModelPreference.setSummary(mGpuUtils.getGpuModel());

        // Set listeners
        setOnChangeListener(mGovernorPreference);
        setOnChangeListener(mMinFreqPreference);
        setOnChangeListener(mMaxFreqPreference);
        setOnChangeListener(mMaxGpuClkPreference);
        setOnChangeListener(mForceClkOnPreference);
        setOnChangeListener(mForceBusOnPreference);
        setOnChangeListener(mForceRailOnPreference);
        setOnChangeListener(mForceNoNapPreference);
        setOnChangeListener(mBusSplitPreference);
        setOnChangeListener(mPreemptPreference);

        SwitchPreference mCameraBoostPreference = findPreference(KEY_GPU_CAMERA_BOOST);
        if (mCameraBoostPreference != null) {
            mCameraBoostPreference.setOnPreferenceChangeListener((pref, value) -> {
                if ((Boolean)value) {
                    mGpuUtils.triggerCameraBoost();
                    Toast.makeText(getContext(), R.string.camera_boost_triggered, Toast.LENGTH_SHORT).show();
                    // Reset switch after a short delay
                    mHandler.postDelayed(() -> mCameraBoostPreference.setChecked(false), 1000);
                }
                return false; // Don't actually keep the switch "on"
            });
        }

        Preference mTurboPresetPreference = findPreference(KEY_GPU_TURBO_PRESET);
        if (mTurboPresetPreference != null) {
            mTurboPresetPreference.setOnPreferenceClickListener(preference -> {
                if (mGpuUtils.applyTurboPreset()) {
                    Toast.makeText(getContext(), R.string.gpu_turbo_enabled, Toast.LENGTH_SHORT).show();
                    loadCurrentSettings(); // Refresh UI
                }
                return true;
            });
        }

        Preference applyPref = findPreference(KEY_APPLY_GPU_SETTINGS);
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                // Settings are applied immediately on change, this acts as a confirmation/refresh
                loadCurrentSettings();
                Toast.makeText(getContext(), R.string.gpu_settings_applied, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        Preference resetPref = findPreference(KEY_RESET_GPU_SETTINGS);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                resetSettings();
                return true;
            });
        }
    }
    
    private void setOnChangeListener(Preference pref) {
        if (pref != null) pref.setOnPreferenceChangeListener(this);
    }

    private void loadCurrentSettings() {
        // Governor
        String[] governors = mGpuUtils.getAvailableGovernors();
        if (governors != null && mGovernorPreference != null) {
            mGovernorPreference.setEntries(governors);
            mGovernorPreference.setEntryValues(governors);
            String current = mGpuUtils.getCurrentGovernor();
            mGovernorPreference.setValue(current);
            mGovernorPreference.setSummary(getString(R.string.gpu_governor_summary, current));
        }

        // Frequencies
        String[] frequencies = mGpuUtils.getAvailableFrequencies();
        if (frequencies != null && frequencies.length > 0) {
            String[] frequencyLabels = new String[frequencies.length];
            for (int i = 0; i < frequencies.length; i++) {
                int freqMhz = Integer.parseInt(frequencies[i]) / 1000000;
                frequencyLabels[i] = freqMhz + " MHz";
            }
            
            updateFreqList(mMinFreqPreference, frequencies, frequencyLabels, mGpuUtils.getCurrentMinFrequency());
            updateFreqList(mMaxFreqPreference, frequencies, frequencyLabels, mGpuUtils.getCurrentMaxFrequency());
            updateFreqList(mMaxGpuClkPreference, frequencies, frequencyLabels, mGpuUtils.getMaxGpuClk());
        }

        // Switches
        if (mForceClkOnPreference != null) mForceClkOnPreference.setChecked(mGpuUtils.getForceClkOn());
        if (mForceBusOnPreference != null) mForceBusOnPreference.setChecked(mGpuUtils.getForceBusOn());
        if (mForceRailOnPreference != null) mForceRailOnPreference.setChecked(mGpuUtils.getForceRailOn());
        if (mForceNoNapPreference != null) mForceNoNapPreference.setChecked(mGpuUtils.getForceNoNap());
        if (mBusSplitPreference != null) mBusSplitPreference.setChecked(mGpuUtils.getBusSplit());
        if (mPreemptPreference != null) mPreemptPreference.setChecked(mGpuUtils.getPreemptStatus());

        updateDynamicInfo();
    }
    
    private void updateFreqList(ListPreference pref, String[] values, String[] labels, String currentVal) {
        if (pref != null) {
            pref.setEntries(labels);
            pref.setEntryValues(values);
            pref.setValue(currentVal);
            // Fallback for summary if value not found in list
            try {
                int mhz = Integer.parseInt(currentVal) / 1000000;
                pref.setSummary(mhz + " MHz");
            } catch (Exception e) {
                pref.setSummary(currentVal);
            }
        }
    }

    private void updateDynamicInfo() {
        if (getActivity() == null) return;

        if (mCurrentFreqPreference != null) {
            String currentFreq = mGpuUtils.getCurrentFrequency();
            if (!currentFreq.equals("0")) {
                int freqMhz = Integer.parseInt(currentFreq) / 1000000;
                mCurrentFreqPreference.setSummary(freqMhz + " MHz");
            } else {
                mCurrentFreqPreference.setSummary(R.string.unknown);
            }
        }
        if (mGpuBusyPreference != null) mGpuBusyPreference.setSummary(mGpuUtils.getGpuBusyPercentage());
        if (mGpuTemperaturePreference != null) {
            String temp = mGpuUtils.getGpuTemperature();
            mGpuTemperaturePreference.setSummary(temp.equals("0") ? getString(R.string.unknown) : temp + "°C");
        }
        if (mThermalPowerLevelPreference != null) {
            mThermalPowerLevelPreference.setSummary(getString(R.string.thermal_level_summary, mGpuUtils.getThermalPowerLevel()));
        }
        if (mResetCountPreference != null) mResetCountPreference.setSummary(mGpuUtils.getResetCount());
        if (mPreemptCountPreference != null) mPreemptCountPreference.setSummary(mGpuUtils.getPreemptCount());
    }

    private void startPeriodicUpdates() {
        if (mUpdateRunnable == null) {
            mUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    updateDynamicInfo();
                    if (isVisible()) { // Check if fragment is visible
                        mHandler.postDelayed(this, 1500);
                    }
                }
            };
        }
        mHandler.removeCallbacks(mUpdateRunnable);
        mHandler.post(mUpdateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (mHandler != null && mUpdateRunnable != null) {
            mHandler.removeCallbacks(mUpdateRunnable);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        boolean success = true;

        if (KEY_GPU_GOVERNOR.equals(key)) {
            String value = (String) newValue;
            mGovernorPreference.setSummary(getString(R.string.gpu_governor_summary, value));
            success = mGpuUtils.setGovernor(value);
            editor.putString(key, value);
        } else if (KEY_GPU_MIN_FREQ.equals(key)) {
            String value = (String) newValue;
            String currentMax = mMaxFreqPreference.getValue();
            success = mGpuUtils.setFrequencyRange(value, currentMax);
            updateFreqSummary(preference, value);
            editor.putString(key, value);
        } else if (KEY_GPU_MAX_FREQ.equals(key)) {
            String value = (String) newValue;
            String currentMin = mMinFreqPreference.getValue();
            success = mGpuUtils.setFrequencyRange(currentMin, value);
            updateFreqSummary(preference, value);
            editor.putString(key, value);
        } else if (KEY_GPU_MAX_GPUCLK.equals(key)) {
            String value = (String) newValue;
            success = mGpuUtils.setMaxGpuClk(value);
            updateFreqSummary(preference, value);
            editor.putString(key, value);
        } else if (key.startsWith("gpu_force_") || key.equals(KEY_GPU_BUS_SPLIT)) {
            boolean value = (Boolean) newValue;
            if (KEY_GPU_FORCE_CLK_ON.equals(key)) mGpuUtils.setForceClkOn(value);
            if (KEY_GPU_FORCE_BUS_ON.equals(key)) mGpuUtils.setForceBusOn(value);
            if (KEY_GPU_FORCE_RAIL_ON.equals(key)) mGpuUtils.setForceRailOn(value);
            if (KEY_GPU_FORCE_NO_NAP.equals(key)) mGpuUtils.setForceNoNap(value);
            if (KEY_GPU_BUS_SPLIT.equals(key)) mGpuUtils.setBusSplit(value);
            editor.putBoolean(key, value);
        } else if (KEY_GPU_PREEMPT.equals(key)) {
            boolean value = (Boolean) newValue;
            mGpuUtils.setPreempt(value);
            editor.putBoolean(key, value);
        }
        
        editor.apply();
        
        if (!success) {
            Toast.makeText(getContext(), "Failed to apply setting", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    
    private void updateFreqSummary(Preference pref, String val) {
        try {
            int mhz = Integer.parseInt(val) / 1000000;
            pref.setSummary(mhz + " MHz");
        } catch (Exception e) {}
    }

    private void resetSettings() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.remove(KEY_GPU_GOVERNOR);
        editor.remove(KEY_GPU_MIN_FREQ);
        editor.remove(KEY_GPU_MAX_FREQ);
        editor.remove(KEY_GPU_MAX_GPUCLK);
        editor.remove(KEY_GPU_FORCE_CLK_ON);
        editor.remove(KEY_GPU_FORCE_BUS_ON);
        editor.remove(KEY_GPU_FORCE_RAIL_ON);
        editor.remove(KEY_GPU_FORCE_NO_NAP);
        editor.remove(KEY_GPU_BUS_SPLIT);
        editor.remove(KEY_GPU_PREEMPT);
        editor.apply();
        
        mGpuUtils.resetToDefaults();
        loadCurrentSettings(); // Reload UI
        Toast.makeText(getContext(), R.string.gpu_settings_reset, Toast.LENGTH_SHORT).show();
    }
}
