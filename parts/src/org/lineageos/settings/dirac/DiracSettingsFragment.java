/*
 * Copyright (C) 2018,2020 The LineageOS Project
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

package org.lineageos.settings.dirac;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;

import org.lineageos.settings.R;

public class DiracSettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener, OnCheckedChangeListener {

    private static final String TAG = "DiracSettingsFragment";
    private static final String PREF_ENABLE = "dirac_enable";
    private static final String PREF_HEADSET = "dirac_headset_pref";
    private static final String PREF_HIFI = "dirac_hifi_pref";
    private static final String PREF_PRESET = "dirac_preset_pref";
    private static final String PREF_SCENE = "scenario_selection";

    private MainSwitchPreference mSwitchBar;

    private ListPreference mHeadsetType;
    private ListPreference mPreset;
    private ListPreference mScenes;
    private SwitchPreferenceCompat mHifi;
    private DiracUtils mDiracUtils;
    private boolean mDiracSupported = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.dirac_settings);

        try {
            mDiracUtils = DiracUtils.getInstance(getActivity());
            mDiracSupported = mDiracUtils.isDiracSupported();
            
            if (!mDiracSupported) {
                Log.w(TAG, "Dirac is not supported on this device");
                showNotSupportedMessage();
                return;
            }
            
            Log.d(TAG, "Dirac is supported and initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Dirac", e);
            mDiracSupported = false;
            showNotSupportedMessage();
            return;
        }

        setupPreferences();
    }
    
    private void showNotSupportedMessage() {
        // Show toast or disable preferences
        if (getActivity() != null) {
            Toast.makeText(getActivity(), "Dirac audio enhancement is not supported on this device", 
                          Toast.LENGTH_LONG).show();
        }
        
        // Disable all preferences
        disableAllPreferences();
    }
    
    private void disableAllPreferences() {
        mSwitchBar = (MainSwitchPreference) findPreference(PREF_ENABLE);
        if (mSwitchBar != null) {
            mSwitchBar.setEnabled(false);
            mSwitchBar.setSummary("Dirac audio enhancement is not supported on this device");
        }
        
        mHeadsetType = (ListPreference) findPreference(PREF_HEADSET);
        if (mHeadsetType != null) mHeadsetType.setEnabled(false);
        
        mPreset = (ListPreference) findPreference(PREF_PRESET);
        if (mPreset != null) mPreset.setEnabled(false);
        
        mHifi = (SwitchPreferenceCompat) findPreference(PREF_HIFI);
        if (mHifi != null) mHifi.setEnabled(false);
        
        mScenes = (ListPreference) findPreference(PREF_SCENE);
        if (mScenes != null) mScenes.setEnabled(false);
    }
    
    private void setupPreferences() {
        boolean enhancerEnabled = false;
        
        try {
            enhancerEnabled = mDiracUtils.isDiracEnabled();
        } catch (Exception e) {
            Log.w(TAG, "Error getting Dirac enabled state", e);
            enhancerEnabled = false;
        }
        
        mSwitchBar = (MainSwitchPreference) findPreference(PREF_ENABLE);
        if (mSwitchBar != null) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mSwitchBar.setChecked(enhancerEnabled);
        }

        mHeadsetType = (ListPreference) findPreference(PREF_HEADSET);
        if (mHeadsetType != null) {
            mHeadsetType.setOnPreferenceChangeListener(this);
            mHeadsetType.setEnabled(enhancerEnabled);
        }

        mPreset = (ListPreference) findPreference(PREF_PRESET);
        if (mPreset != null) {
            mPreset.setOnPreferenceChangeListener(this);
            mPreset.setEnabled(enhancerEnabled);
        }

        mHifi = (SwitchPreferenceCompat) findPreference(PREF_HIFI);
        if (mHifi != null) {
            mHifi.setOnPreferenceChangeListener(this);
            mHifi.setEnabled(enhancerEnabled);
        }

        mScenes = (ListPreference) findPreference(PREF_SCENE);
        if (mScenes != null) {
            mScenes.setOnPreferenceChangeListener(this);
            mScenes.setEnabled(enhancerEnabled);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!mDiracSupported || mDiracUtils == null) {
            Log.w(TAG, "Dirac not supported, ignoring preference change");
            return false;
        }
        
        try {
            switch (preference.getKey()) {
                case PREF_HEADSET:
                    mDiracUtils.setHeadsetType(Integer.parseInt(newValue.toString()));
                    return true;
                case PREF_HIFI:
                    mDiracUtils.setHifiMode((Boolean) newValue ? 1 : 0);
                    return true;
                case PREF_PRESET:
                    mDiracUtils.setLevel((String) newValue);
                    return true;
                case PREF_SCENE:
                    mDiracUtils.setScenario(Integer.parseInt(newValue.toString()));
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling preference change for " + preference.getKey(), e);
            return false;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mSwitchBar != null) {
            mSwitchBar.setChecked(isChecked);
        }

        if (!mDiracSupported || mDiracUtils == null) {
            Log.w(TAG, "Dirac not supported, ignoring switch change");
            return;
        }
        
        try {
            mDiracUtils.setEnabled(isChecked);
            
            if (mHifi != null) mHifi.setEnabled(isChecked);
            if (mHeadsetType != null) mHeadsetType.setEnabled(isChecked);
            if (mPreset != null) mPreset.setEnabled(isChecked);
            if (mScenes != null) mScenes.setEnabled(isChecked);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac enabled state: " + isChecked, e);
        }
    }
}
