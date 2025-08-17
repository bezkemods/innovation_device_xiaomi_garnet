/*
 * Copyright (C) 2025 bezke
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
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import org.lineageos.settings.R;

public class PerformanceFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PerformanceFragment";
    private static final String KEY_PERFORMANCE_PROFILE = "performance_profile";
    
    private ListPreference mPerformanceProfilePreference;
    private PerformanceUtils mPerformanceUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            setPreferencesFromResource(R.xml.performance_settings, rootKey);
            mPerformanceUtils = new PerformanceUtils(getContext());

            mPerformanceProfilePreference = (ListPreference) findPreference(KEY_PERFORMANCE_PROFILE);
            if (mPerformanceProfilePreference != null) {
                int currentMode = mPerformanceUtils.getCurrentMode();
                mPerformanceProfilePreference.setValue(String.valueOf(currentMode));
                mPerformanceProfilePreference.setSummary(mPerformanceUtils.getModeLabel(currentMode));
                mPerformanceProfilePreference.setOnPreferenceChangeListener(this);
                
                Log.d(TAG, "Performance profile preference initialized, current mode: " + currentMode);
            } else {
                Log.e(TAG, "Performance profile preference not found!");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating PerformanceFragment", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            if (KEY_PERFORMANCE_PROFILE.equals(preference.getKey())) {
                int mode = Integer.parseInt((String) newValue);
                Log.d(TAG, "Setting performance profile to: " + mode);
                
                boolean success = mPerformanceUtils.setPerformanceMode(mode);
                if (success) {
                    mPerformanceProfilePreference.setSummary(mPerformanceUtils.getModeLabel(mode));
                    
                    // Show toast message
                    String modeLabel = mPerformanceUtils.getModeLabel(mode);
                    Toast.makeText(getContext(), 
                        getString(R.string.performance_profile_applied, modeLabel),
                        Toast.LENGTH_SHORT).show();
                    
                    Log.d(TAG, "Performance profile successfully set to: " + mode);
                    return true;
                } else {
                    Log.e(TAG, "Failed to set performance profile to: " + mode);
                    Toast.makeText(getContext(), 
                        R.string.performance_profile_failed,
                        Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPreferenceChange", e);
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mPerformanceProfilePreference != null && mPerformanceUtils != null) {
                // Refresh state when returning to fragment
                int currentMode = mPerformanceUtils.getCurrentMode();
                mPerformanceProfilePreference.setValue(String.valueOf(currentMode));
                mPerformanceProfilePreference.setSummary(mPerformanceUtils.getModeLabel(currentMode));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }
}
