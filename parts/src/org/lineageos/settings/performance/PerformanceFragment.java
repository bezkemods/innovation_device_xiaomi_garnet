/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.performance;

import android.os.Bundle;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.TwoStatePreference;
import org.lineageos.settings.R;

public class PerformanceFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PerformanceFragment";
    private static final String KEY_PERFORMANCE_MODE = "performance_mode";
    
    private TwoStatePreference mPerformanceModePreference;
    private PerformanceUtils mPerformanceUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            setPreferencesFromResource(R.xml.performance_settings, rootKey);
            mPerformanceUtils = new PerformanceUtils();

            mPerformanceModePreference = (TwoStatePreference) findPreference(KEY_PERFORMANCE_MODE);
            if (mPerformanceModePreference != null) {
                boolean isEnabled = mPerformanceUtils.isPerformanceModeEnabled();
                mPerformanceModePreference.setChecked(isEnabled);
                mPerformanceModePreference.setOnPreferenceChangeListener(this);
                updateSummary(isEnabled);
                
                Log.d(TAG, "Performance mode preference initialized, current state: " + isEnabled);
            } else {
                Log.e(TAG, "Performance mode preference not found!");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating PerformanceFragment", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            if (KEY_PERFORMANCE_MODE.equals(preference.getKey())) {
                boolean enabled = (Boolean) newValue;
                Log.d(TAG, "Setting performance mode to: " + enabled);
                
                boolean success = mPerformanceUtils.setPerformanceMode(enabled);
                if (success) {
                    updateSummary(enabled);
                    Log.d(TAG, "Performance mode successfully set to: " + enabled);
                    return true;
                } else {
                    Log.e(TAG, "Failed to set performance mode to: " + enabled);
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPreferenceChange", e);
        }
        return false;
    }

    private void updateSummary(boolean enabled) {
        if (mPerformanceModePreference != null) {
            try {
                String summary = enabled ? 
                    getString(R.string.performance_mode_enabled_summary) :
                    getString(R.string.performance_mode_disabled_summary);
                mPerformanceModePreference.setSummary(summary);
            } catch (Exception e) {
                Log.e(TAG, "Error updating summary", e);
                // Fallback summaries
                mPerformanceModePreference.setSummary(enabled ? 
                    "Performance mode is enabled" : "Performance mode is disabled");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mPerformanceModePreference != null && mPerformanceUtils != null) {
                // Refresh state when returning to fragment
                boolean currentState = mPerformanceUtils.isPerformanceModeEnabled();
                mPerformanceModePreference.setChecked(currentState);
                updateSummary(currentState);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }
}
