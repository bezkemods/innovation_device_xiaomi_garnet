/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.performance;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.lineageos.settings.R;

public class PerformanceFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PerformanceFragment";
    private static final String KEY_PERFORMANCE_PROFILE = "performance_profile";
    
    private ListPreference mPerformanceProfilePreference;
    private PerformanceUtils mPerformanceUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.performance_settings, rootKey);
        
        try {
            mPerformanceUtils = new PerformanceUtils(getContext());

            mPerformanceProfilePreference = findPreference(KEY_PERFORMANCE_PROFILE);
            if (mPerformanceProfilePreference != null) {
                int currentMode = mPerformanceUtils.getCurrentMode();
                mPerformanceProfilePreference.setValue(String.valueOf(currentMode));
                mPerformanceProfilePreference.setSummary(mPerformanceUtils.getModeLabel(currentMode));
                mPerformanceProfilePreference.setOnPreferenceChangeListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PerformanceFragment", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_PERFORMANCE_PROFILE.equals(preference.getKey())) {
            try {
                int mode = Integer.parseInt((String) newValue);
                boolean success = mPerformanceUtils.setPerformanceMode(mode);
                
                if (success) {
                    String modeLabel = mPerformanceUtils.getModeLabel(mode);
                    mPerformanceProfilePreference.setSummary(modeLabel);
                    Toast.makeText(getContext(), 
                        getString(R.string.performance_profile_applied, modeLabel),
                        Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    Toast.makeText(getContext(), 
                        R.string.performance_profile_failed,
                        Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error changing performance mode", e);
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPerformanceProfilePreference != null && mPerformanceUtils != null) {
            // Update UI in case changed from Tile
            int currentMode = mPerformanceUtils.getCurrentMode();
            mPerformanceProfilePreference.setValue(String.valueOf(currentMode));
            mPerformanceProfilePreference.setSummary(mPerformanceUtils.getModeLabel(currentMode));
        }
    }
}
