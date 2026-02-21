/*
 * Copyright (C) 2025 KamiKaonashi
 * Optimized for Garnet (Snapdragon 7s Gen 2)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.gpumanager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager; // FIX: was android.preference.PreferenceManager
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class GpuManagerFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

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

    private static final int UPDATE_INTERVAL = 2000; // Optimized for SM7435

    private GpuManagerUtils mGpuUtils;
    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;
    private SharedPreferences mSharedPrefs;

    // Preferences
    private ListPreference mGovernorPreference;
    private ListPreference mMinFreqPreference, mMaxFreqPreference, mMaxGpuClkPreference;
    private Preference mCurrentFreqPreference;
    private Preference mGpuModelPreference;
    private Preference mGpuBusyPreference;
    private Preference mGpuTemperaturePreference;
    private Preference mThermalPowerLevelPreference;
    private SwitchPreference mForceClkOnPreference;
    private SwitchPreference mForceBusOnPreference;
    private SwitchPreference mForceRailOnPreference;
    private SwitchPreference mForceNoNapPreference;
    private SwitchPreference mBusSplitPreference;
    private SwitchPreference mPreemptPreference;
    private SwitchPreference mCameraBoostPreference;
    private Preference mResetCountPreference;
    private Preference mPreemptCountPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gpu_manager_settings, rootKey);
        mGpuUtils = new GpuManagerUtils();
        mUpdateHandler = new Handler(Looper.getMainLooper());
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        initializePreferences();
        loadCurrentSettings();
        startPeriodicUpdates();
    }

    // FIX: Stop periodic updates when the fragment is no longer visible.
    // Previously the 2-second sysfs polling continued running even after the
    // user left the screen (until onDestroy), draining battery unnecessarily.
    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPeriodicUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPeriodicUpdates();
    }

    private void initializePreferences() {
        mGovernorPreference = (ListPreference) findPreference(KEY_GPU_GOVERNOR);
        mMinFreqPreference = (ListPreference) findPreference(KEY_GPU_MIN_FREQ);
        mMaxFreqPreference = (ListPreference) findPreference(KEY_GPU_MAX_FREQ);
        mMaxGpuClkPreference = (ListPreference) findPreference(KEY_GPU_MAX_GPUCLK);
        mCurrentFreqPreference = findPreference(KEY_GPU_CURRENT_FREQ);
        mGpuModelPreference = findPreference(KEY_GPU_MODEL);
        mGpuBusyPreference = findPreference(KEY_GPU_BUSY_PERCENTAGE);
        mGpuTemperaturePreference = findPreference(KEY_GPU_TEMPERATURE);
        mThermalPowerLevelPreference = findPreference(KEY_GPU_THERMAL_PWRLEVEL);
        mForceClkOnPreference = (SwitchPreference) findPreference(KEY_GPU_FORCE_CLK_ON);
        mForceBusOnPreference = (SwitchPreference) findPreference(KEY_GPU_FORCE_BUS_ON);
        mForceRailOnPreference = (SwitchPreference) findPreference(KEY_GPU_FORCE_RAIL_ON);
        mForceNoNapPreference = (SwitchPreference) findPreference(KEY_GPU_FORCE_NO_NAP);
        mBusSplitPreference = (SwitchPreference) findPreference(KEY_GPU_BUS_SPLIT);
        mPreemptPreference = (SwitchPreference) findPreference(KEY_GPU_PREEMPT);
        mCameraBoostPreference = (SwitchPreference) findPreference(KEY_GPU_CAMERA_BOOST);
        mResetCountPreference = findPreference(KEY_GPU_RESET_COUNT);
        mPreemptCountPreference = findPreference(KEY_GPU_PREEMPT_COUNT);

        // Set listeners
        attachListeners();
        setupButtonListeners();
    }

    private void attachListeners() {
        if (mGovernorPreference != null) mGovernorPreference.setOnPreferenceChangeListener(this);
        if (mMinFreqPreference != null) mMinFreqPreference.setOnPreferenceChangeListener(this);
        if (mMaxFreqPreference != null) mMaxFreqPreference.setOnPreferenceChangeListener(this);
        if (mMaxGpuClkPreference != null) mMaxGpuClkPreference.setOnPreferenceChangeListener(this);

        if (mForceClkOnPreference != null) mForceClkOnPreference.setOnPreferenceChangeListener(this);
        if (mForceBusOnPreference != null) mForceBusOnPreference.setOnPreferenceChangeListener(this);
        if (mForceRailOnPreference != null) mForceRailOnPreference.setOnPreferenceChangeListener(this);
        if (mForceNoNapPreference != null) mForceNoNapPreference.setOnPreferenceChangeListener(this);
        if (mBusSplitPreference != null) mBusSplitPreference.setOnPreferenceChangeListener(this);
        if (mPreemptPreference != null) mPreemptPreference.setOnPreferenceChangeListener(this);

        if (mCameraBoostPreference != null) {
            mCameraBoostPreference.setOnPreferenceChangeListener((pref, value) -> {
                if ((Boolean)value) {
                    mGpuUtils.triggerCameraBoost();
                    Toast.makeText(getContext(), R.string.camera_boost_triggered, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    private void setupButtonListeners() {
        Preference turboPresetPref = findPreference(KEY_GPU_TURBO_PRESET);
        if (turboPresetPref != null) {
            turboPresetPref.setOnPreferenceClickListener(preference -> {
                mGpuUtils.applyTurboPreset();
                Toast.makeText(getContext(), R.string.gpu_turbo_enabled, Toast.LENGTH_SHORT).show();
                loadCurrentSettings();
                return true;
            });
        }

        Preference applyPref = findPreference(KEY_APPLY_GPU_SETTINGS);
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                applySettings();
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

    private void loadCurrentSettings() {
        // GPU model
        if (mGpuModelPreference != null)
            mGpuModelPreference.setSummary(mGpuUtils.getGpuModel());

        // Governor
        String[] governors = mGpuUtils.getAvailableGovernors();
        if (governors != null && mGovernorPreference != null) {
            mGovernorPreference.setEntries(governors);
            mGovernorPreference.setEntryValues(governors);
            String savedGovernor = mSharedPrefs.getString(KEY_GPU_GOVERNOR, mGpuUtils.getCurrentGovernor());
            mGovernorPreference.setValue(savedGovernor);
            mGovernorPreference.setSummary(getString(R.string.gpu_governor_summary, savedGovernor));
        }

        // Frequencies
        loadFrequencySettings();
        
        // Switches
        if (mForceClkOnPreference != null) mForceClkOnPreference.setChecked(mGpuUtils.getForceClkOn());
        if (mForceBusOnPreference != null) mForceBusOnPreference.setChecked(mGpuUtils.getForceBusOn());
        if (mForceRailOnPreference != null) mForceRailOnPreference.setChecked(mGpuUtils.getForceRailOn());
        if (mForceNoNapPreference != null) mForceNoNapPreference.setChecked(mGpuUtils.getForceNoNap());
        if (mBusSplitPreference != null) mBusSplitPreference.setChecked(mGpuUtils.getBusSplit());
        if (mPreemptPreference != null) mPreemptPreference.setChecked(mGpuUtils.getPreemptStatus());

        updateDynamicInfo();
    }

    private void loadFrequencySettings() {
        String[] frequencies = mGpuUtils.getAvailableFrequencies();
        if (frequencies != null) {
            String[] frequencyLabels = new String[frequencies.length];
            for (int i = 0; i < frequencies.length; i++) {
                int freqMhz = Integer.parseInt(frequencies[i]) / 1000000;
                frequencyLabels[i] = freqMhz + " MHz";
            }
            
            if (mMinFreqPreference != null) {
                mMinFreqPreference.setEntries(frequencyLabels);
                mMinFreqPreference.setEntryValues(frequencies);
                String savedMinFreq = mSharedPrefs.getString(KEY_GPU_MIN_FREQ, mGpuUtils.getCurrentMinFrequency());
                mMinFreqPreference.setValue(savedMinFreq);
                int minFreqMhz = Integer.parseInt(savedMinFreq) / 1000000;
                mMinFreqPreference.setSummary(minFreqMhz + " MHz");
            }
            
            if (mMaxFreqPreference != null) {
                mMaxFreqPreference.setEntries(frequencyLabels);
                mMaxFreqPreference.setEntryValues(frequencies);
                String savedMaxFreq = mSharedPrefs.getString(KEY_GPU_MAX_FREQ, mGpuUtils.getCurrentMaxFrequency());
                mMaxFreqPreference.setValue(savedMaxFreq);
                int maxFreqMhz = Integer.parseInt(savedMaxFreq) / 1000000;
                mMaxFreqPreference.setSummary(maxFreqMhz + " MHz");
            }
            
            if (mMaxGpuClkPreference != null) {
                mMaxGpuClkPreference.setEntries(frequencyLabels);
                mMaxGpuClkPreference.setEntryValues(frequencies);
                String savedMaxClk = mSharedPrefs.getString(KEY_GPU_MAX_GPUCLK, mGpuUtils.getMaxGpuClk());
                if (savedMaxClk == null || savedMaxClk.length() < 3) savedMaxClk = frequencies[frequencies.length - 1];
                mMaxGpuClkPreference.setValue(savedMaxClk);
                int maxClkMhz = Integer.parseInt(savedMaxClk) / 1000000;
                mMaxGpuClkPreference.setSummary(maxClkMhz + " MHz");
            }
        }
    }

    private void updateDynamicInfo() {
        if (mCurrentFreqPreference != null) {
            String currentFreq = mGpuUtils.getCurrentFrequency();
            if (!currentFreq.equals("0")) {
                int freqMhz = Integer.parseInt(currentFreq) / 1000000;
                mCurrentFreqPreference.setSummary(freqMhz + " MHz");
            } else {
                mCurrentFreqPreference.setSummary(getString(R.string.unknown));
            }
        }
        if (mGpuBusyPreference != null) mGpuBusyPreference.setSummary(mGpuUtils.getGpuBusyPercentage());
        if (mGpuTemperaturePreference != null) {
            String temperature = mGpuUtils.getGpuTemperature();
            mGpuTemperaturePreference.setSummary(temperature.equals("0") ? getString(R.string.unknown) : temperature + "°C");
        }
        if (mThermalPowerLevelPreference != null) {
            String thermalLevel = mGpuUtils.getThermalPowerLevel();
            mThermalPowerLevelPreference.setSummary(getString(R.string.thermal_level_summary, thermalLevel));
        }
        if (mResetCountPreference != null) mResetCountPreference.setSummary(mGpuUtils.getResetCount());
        if (mPreemptCountPreference != null) mPreemptCountPreference.setSummary(mGpuUtils.getPreemptCount());
    }

    private void startPeriodicUpdates() {
        // Avoid double-scheduling if called from both onCreatePreferences and onResume
        if (mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDynamicInfo();
                mUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        mUpdateHandler.post(mUpdateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();

        if (KEY_GPU_GOVERNOR.equals(key)) {
            String value = (String) newValue;
            mGovernorPreference.setSummary(getString(R.string.gpu_governor_summary, value));
            mGpuUtils.setGovernor(value);
            editor.putString(key, value);
        } else if (KEY_GPU_MIN_FREQ.equals(key) || KEY_GPU_MAX_FREQ.equals(key)) {
            String value = (String) newValue;
            int freqMhz = Integer.parseInt(value) / 1000000;
            preference.setSummary(freqMhz + " MHz");
            if (KEY_GPU_MIN_FREQ.equals(key)) {
                mGpuUtils.setFrequencyRange(value, mMaxFreqPreference.getValue());
            } else {
                mGpuUtils.setFrequencyRange(mMinFreqPreference.getValue(), value);
            }
            editor.putString(key, value);
        } else if (KEY_GPU_MAX_GPUCLK.equals(key)) {
            String value = (String) newValue;
            int freqMhz = Integer.parseInt(value) / 1000000;
            preference.setSummary(freqMhz + " MHz");
            mGpuUtils.setMaxGpuClk(value);
            editor.putString(key, value);
        } else if (key.startsWith("gpu_force_") || key.equals(KEY_GPU_BUS_SPLIT) || key.equals(KEY_GPU_PREEMPT)) {
            boolean value = (Boolean) newValue;
            applyBooleanSetting(key, value);
            editor.putBoolean(key, value);
        }
        editor.apply();
        return true;
    }

    private void applyBooleanSetting(String key, boolean value) {
        if (KEY_GPU_FORCE_CLK_ON.equals(key)) mGpuUtils.setForceClkOn(value);
        else if (KEY_GPU_FORCE_BUS_ON.equals(key)) mGpuUtils.setForceBusOn(value);
        else if (KEY_GPU_FORCE_RAIL_ON.equals(key)) mGpuUtils.setForceRailOn(value);
        else if (KEY_GPU_FORCE_NO_NAP.equals(key)) mGpuUtils.setForceNoNap(value);
        else if (KEY_GPU_BUS_SPLIT.equals(key)) mGpuUtils.setBusSplit(value);
        else if (KEY_GPU_PREEMPT.equals(key)) mGpuUtils.setPreempt(value);
    }

    private void applySettings() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        if (mGovernorPreference != null) {
            String governor = mGovernorPreference.getValue();
            mGpuUtils.setGovernor(governor);
            editor.putString(KEY_GPU_GOVERNOR, governor);
        }
        if (mMinFreqPreference != null && mMaxFreqPreference != null) {
            String minFreq = mMinFreqPreference.getValue();
            String maxFreq = mMaxFreqPreference.getValue();
            mGpuUtils.setFrequencyRange(minFreq, maxFreq);
            editor.putString(KEY_GPU_MIN_FREQ, minFreq);
            editor.putString(KEY_GPU_MAX_FREQ, maxFreq);
        }
        if (mMaxGpuClkPreference != null) {
            String maxClk = mMaxGpuClkPreference.getValue();
            mGpuUtils.setMaxGpuClk(maxClk);
            editor.putString(KEY_GPU_MAX_GPUCLK, maxClk);
        }
        
        applySwitchSettings(editor);
        editor.apply();
        Toast.makeText(getContext(), R.string.gpu_settings_applied, Toast.LENGTH_SHORT).show();
    }

    private void applySwitchSettings(SharedPreferences.Editor editor) {
        if (mForceClkOnPreference != null) mGpuUtils.setForceClkOn(mForceClkOnPreference.isChecked());
        if (mForceBusOnPreference != null) mGpuUtils.setForceBusOn(mForceBusOnPreference.isChecked());
        if (mForceRailOnPreference != null) mGpuUtils.setForceRailOn(mForceRailOnPreference.isChecked());
        if (mForceNoNapPreference != null) mGpuUtils.setForceNoNap(mForceNoNapPreference.isChecked());
        if (mBusSplitPreference != null) mGpuUtils.setBusSplit(mBusSplitPreference.isChecked());
        if (mPreemptPreference != null) mGpuUtils.setPreempt(mPreemptPreference.isChecked());
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
        loadCurrentSettings();
        Toast.makeText(getContext(), R.string.gpu_settings_reset, Toast.LENGTH_SHORT).show();
    }

    private void triggerCameraBoost() {
        mGpuUtils.triggerCameraBoost();
    }
}
