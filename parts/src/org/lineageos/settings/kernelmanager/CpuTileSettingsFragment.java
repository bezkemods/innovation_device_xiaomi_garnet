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

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.quicksettings.TileService;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class CpuTileSettingsFragment extends PreferenceFragment 
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_TILE_SIZE = "cpu_tile_size";
    private static final String KEY_TILE_STYLE = "cpu_tile_style";
    private static final String KEY_UPDATE_SPEED = "cpu_tile_update_speed";
    private static final String KEY_SHOW_PERCENTAGE = "cpu_tile_show_percentage";
    private static final String KEY_PREVIEW_TILE = "cpu_tile_preview";

    private ListPreference mTileSizePreference;
    private ListPreference mTileStylePreference;
    private ListPreference mUpdateSpeedPreference;
    private SwitchPreference mShowPercentagePreference;
    private Preference mPreviewTilePreference;
    
    private SharedPreferences mSharedPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.cpu_tile_settings, rootKey);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        
        initializePreferences();
        loadCurrentSettings();
    }

    private void initializePreferences() {
        mTileSizePreference = (ListPreference) findPreference(KEY_TILE_SIZE);
        mTileStylePreference = (ListPreference) findPreference(KEY_TILE_STYLE);
        mUpdateSpeedPreference = (ListPreference) findPreference(KEY_UPDATE_SPEED);
        mShowPercentagePreference = (SwitchPreference) findPreference(KEY_SHOW_PERCENTAGE);
        mPreviewTilePreference = findPreference(KEY_PREVIEW_TILE);

        if (mTileSizePreference != null) {
            mTileSizePreference.setOnPreferenceChangeListener(this);
        }
        
        if (mTileStylePreference != null) {
            mTileStylePreference.setOnPreferenceChangeListener(this);
        }
        
        if (mUpdateSpeedPreference != null) {
            mUpdateSpeedPreference.setOnPreferenceChangeListener(this);
        }
        
        if (mShowPercentagePreference != null) {
            mShowPercentagePreference.setOnPreferenceChangeListener(this);
        }
        
        if (mPreviewTilePreference != null) {
            mPreviewTilePreference.setOnPreferenceClickListener(preference -> {
                requestTileUpdate();
                return true;
            });
        }
    }

    private void loadCurrentSettings() {
        // Load tile size
        if (mTileSizePreference != null) {
            int currentSize = mSharedPrefs.getInt(KEY_TILE_SIZE, CpuGovernorTileService.SIZE_MEDIUM);
            mTileSizePreference.setValue(String.valueOf(currentSize));
            updateTileSizeSummary(currentSize);
        }
        
        // Load tile style
        if (mTileStylePreference != null) {
            int currentStyle = mSharedPrefs.getInt(KEY_TILE_STYLE, CpuGovernorTileService.STYLE_SPEEDOMETER);
            mTileStylePreference.setValue(String.valueOf(currentStyle));
            updateTileStyleSummary(currentStyle);
        }
        
        // Load update speed
        if (mUpdateSpeedPreference != null) {
            int currentSpeed = mSharedPrefs.getInt(KEY_UPDATE_SPEED, 1000);
            mUpdateSpeedPreference.setValue(String.valueOf(currentSpeed));
            updateUpdateSpeedSummary(currentSpeed);
        }
        
        // Load show percentage
        if (mShowPercentagePreference != null) {
            boolean showPercentage = mSharedPrefs.getBoolean(KEY_SHOW_PERCENTAGE, true);
            mShowPercentagePreference.setChecked(showPercentage);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        
        switch (key) {
            case KEY_TILE_SIZE:
                int size = Integer.parseInt((String) newValue);
                editor.putInt(KEY_TILE_SIZE, size);
                updateTileSizeSummary(size);
                break;
                
            case KEY_TILE_STYLE:
                int style = Integer.parseInt((String) newValue);
                editor.putInt(KEY_TILE_STYLE, style);
                updateTileStyleSummary(style);
                break;
                
            case KEY_UPDATE_SPEED:
                int speed = Integer.parseInt((String) newValue);
                editor.putInt(KEY_UPDATE_SPEED, speed);
                updateUpdateSpeedSummary(speed);
                break;
                
            case KEY_SHOW_PERCENTAGE:
                boolean showPercentage = (Boolean) newValue;
                editor.putBoolean(KEY_SHOW_PERCENTAGE, showPercentage);
                break;
        }
        
        editor.apply();
        requestTileUpdate();
        return true;
    }

    private void updateTileSizeSummary(int size) {
        if (mTileSizePreference != null) {
            String[] entries = getResources().getStringArray(R.array.cpu_tile_size_entries);
            if (size >= 0 && size < entries.length) {
                mTileSizePreference.setSummary(entries[size]);
            }
        }
    }

    private void updateTileStyleSummary(int style) {
        if (mTileStylePreference != null) {
            String[] entries = getResources().getStringArray(R.array.cpu_tile_style_entries);
            if (style >= 0 && style < entries.length) {
                mTileStylePreference.setSummary(entries[style]);
            }
        }
    }

    private void updateUpdateSpeedSummary(int speed) {
        if (mUpdateSpeedPreference != null) {
            String summary;
            switch (speed) {
                case 500:
                    summary = getString(R.string.cpu_tile_update_speed_fast);
                    break;
                case 1000:
                    summary = getString(R.string.cpu_tile_update_speed_normal);
                    break;
                case 2000:
                    summary = getString(R.string.cpu_tile_update_speed_slow);
                    break;
                case 5000:
                    summary = getString(R.string.cpu_tile_update_speed_very_slow);
                    break;
                default:
                    summary = speed + "ms";
                    break;
            }
            mUpdateSpeedPreference.setSummary(summary);
        }
    }

    private void requestTileUpdate() {
        try {
            ComponentName componentName = new ComponentName(getContext(), CpuGovernorTileService.class);
            TileService.requestListeningState(getContext(), componentName);
        } catch (Exception e) {
            // Tile update request failed, not critical
        }
    }
}
