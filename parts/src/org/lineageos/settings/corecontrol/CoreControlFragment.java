/*
 * Copyright (C) 2025 The LineageOS Project
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

package org.lineageos.settings.corecontrol;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;

public class CoreControlFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "CoreControlFragment";
    private static final int NUM_CORES = 8;
    private static final String STATS_KEY = "core_stats";

    private SwitchPreference[] mCorePrefs = new SwitchPreference[NUM_CORES];
    private Preference mStatsPreference;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.core_control_settings);

        // Add statistics preference at the top
        PreferenceCategory statsCategory = findPreference("core_stats_category");
        if (statsCategory == null) {
            statsCategory = new PreferenceCategory(getContext());
            statsCategory.setKey("core_stats_category");
            statsCategory.setTitle("Core Statistics");
            getPreferenceScreen().addPreference(statsCategory);
        }

        mStatsPreference = new Preference(getContext());
        mStatsPreference.setKey(STATS_KEY);
        mStatsPreference.setTitle("Active Cores");
        mStatsPreference.setSelectable(false);
        statsCategory.addPreference(mStatsPreference);

        for (int i = 0; i < NUM_CORES; i++) {
            String key = "core_" + i;
            mCorePrefs[i] = (SwitchPreference) findPreference(key);
            if (mCorePrefs[i] != null) {
                mCorePrefs[i].setOnPreferenceChangeListener(this);
                mCorePrefs[i].setChecked(isCoreOnline(i));
            }
        }
        
        updateCoreStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCoreStatistics();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean requestedState = (Boolean) newValue;

        for (int i = 0; i < NUM_CORES; i++) {
            if (preference == mCorePrefs[i]) {
                if (!requestedState && !canOffline(i)) {
                    Toast.makeText(getContext(), "At least 2 little cores must remain online", Toast.LENGTH_SHORT).show();
                    return false;
                }
                
                setCoreState(i, requestedState);
                
                // Show toast with current status
                final int coreIndex = i; // Make variable final for lambda
                final boolean finalRequestedState = requestedState;
                mHandler.postDelayed(() -> {
                    updateCoreStatistics();
                    showCoreStatusToast(coreIndex, finalRequestedState);
                }, 100); // Small delay to ensure state change is applied
                
                return true;
            }
        }
        return false;
    }

    private void updateCoreStatistics() {
        int activeCores = 0;
        int activeLittleCores = 0; // cores 0-3
        int activeBigCores = 0;    // cores 4-7
        
        for (int i = 0; i < NUM_CORES; i++) {
            if (isCoreOnline(i)) {
                activeCores++;
                if (i <= 3) {
                    activeLittleCores++;
                } else {
                    activeBigCores++;
                }
            }
        }
        
        String statsText = String.format("%d/%d cores active (Little: %d/4, Big: %d/4)", 
            activeCores, NUM_CORES, activeLittleCores, activeBigCores);
        
        if (mStatsPreference != null) {
            mStatsPreference.setSummary(statsText);
        }
    }
    
    private void showCoreStatusToast(int core, boolean isOnline) {
        int activeCores = 0;
        for (int i = 0; i < NUM_CORES; i++) {
            if (isCoreOnline(i)) activeCores++;
        }
        
        String coreType = (core <= 3) ? "Little" : "Big";
        String status = isOnline ? "enabled" : "disabled";
        String message = String.format("%s Core %d %s - Total active: %d/%d cores", 
            coreType, core, status, activeCores, NUM_CORES);
        
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean isCoreOnline(int core) {
        String path = "/sys/devices/system/cpu/cpu" + core + "/online";
        String value = readFile(path);
        return "1".equals(value);
    }

    private void setCoreState(int core, boolean online) {
        String path = "/sys/devices/system/cpu/cpu" + core + "/online";
        writeFileAsRoot(path, online ? "1" : "0");
    }

    private boolean canOffline(int core) {
        if (core >= 0 && core <= 3) {
            int onlineCount = 0;
            for (int i = 0; i <= 3; i++) {
                if (i != core && isCoreOnline(i)) onlineCount++;
            }
            return onlineCount >= 2;
        }
        return true;
    }

    private String readFile(String path) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(path)))) {
            return br.readLine().trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + path, e);
            return "";
        }
    }

    private void writeFileAsRoot(String path, String value) {
        Process suProcess = null;
        DataOutputStream os = null;
        try {
            suProcess = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(suProcess.getOutputStream());
            os.writeBytes("echo " + value + " > " + path + "\n");
            os.writeBytes("exit\n");
            os.flush();
            suProcess.waitFor();
        } catch (Exception ex) {
            Log.e(TAG, "Failed to write " + path, ex);
        } finally {
            try {
                if (os != null) os.close();
                if (suProcess != null) suProcess.destroy();
            } catch (Exception ignored) {}
        }
    }
}
