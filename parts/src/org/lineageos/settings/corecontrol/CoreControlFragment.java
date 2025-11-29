/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CoreControlFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "CoreControlFragment";
    private static final int NUM_CORES = 8;
    private static final String STATS_KEY = "core_stats";
    private static final String CORE_ONLINE_PATH = "/sys/devices/system/cpu/cpu%d/online";

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
                
                // Core 0 cannot be disabled (boot core)
                if (i == 0) {
                    mCorePrefs[i].setEnabled(false);
                    mCorePrefs[i].setChecked(true);
                    mCorePrefs[i].setSummary("Boot core - cannot be disabled");
                } else {
                    mCorePrefs[i].setChecked(isCoreOnline(i));
                }
            }
        }
        
        updateCoreStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Update all core states when resuming
        for (int i = 1; i < NUM_CORES; i++) { // Skip core 0
            if (mCorePrefs[i] != null) {
                mCorePrefs[i].setChecked(isCoreOnline(i));
            }
        }
        updateCoreStatistics();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean requestedState = (Boolean) newValue;

        for (int i = 1; i < NUM_CORES; i++) { // Skip core 0
            if (preference == mCorePrefs[i]) {
                // Check if we can safely disable this little core
                if (!requestedState && !canOffline(i)) {
                    Toast.makeText(getContext(), 
                        "At least 2 little cores must remain online", 
                        Toast.LENGTH_SHORT).show();
                    return false;
                }
                
                boolean success = setCoreState(i, requestedState);
                
                if (success) {
                    // Show toast with current status
                    final int coreIndex = i;
                    final boolean finalRequestedState = requestedState;
                    mHandler.postDelayed(() -> {
                        updateCoreStatistics();
                        showCoreStatusToast(coreIndex, finalRequestedState);
                    }, 100);
                    
                    return true;
                } else {
                    Toast.makeText(getContext(), 
                        "Failed to change core " + i + " state", 
                        Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return false;
    }

    private void updateCoreStatistics() {
        int activeCores = 0;
        int activeLittleCores = 0; // cores 0-3 (Cortex-A55)
        int activeBigCores = 0;    // cores 4-7 (Cortex-A78)
        
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
        
        // Updated formatting for 4+4 Topology of Snapdragon 7s Gen 2
        String statsText = String.format(
            "%d/%d cores active (Little: %d/4, Big: %d/4)", 
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
        
        String coreType;
        if (core <= 3) {
            coreType = "Little (A55)";
        } else {
            coreType = "Big (A78)";
        }
        
        String status = isOnline ? "enabled" : "disabled";
        String message = String.format("%s Core %d %s - Total active: %d/%d cores", 
            coreType, core, status, activeCores, NUM_CORES);
        
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean isCoreOnline(int core) {
        // Core 0 is always online (boot core)
        if (core == 0) {
            return true;
        }
        
        String path = String.format(CORE_ONLINE_PATH, core);
        String value = readFile(path);
        return "1".equals(value.trim());
    }

    private boolean setCoreState(int core, boolean online) {
        // Cannot change core 0 state
        if (core == 0) {
            return false;
        }
        
        String path = String.format(CORE_ONLINE_PATH, core);
        String value = online ? "1" : "0";
        
        boolean success = writeFile(path, value);
        
        if (success) {
            Log.d(TAG, "Successfully set core " + core + " to " + (online ? "online" : "offline"));
        } else {
            Log.e(TAG, "Failed to set core " + core + " to " + (online ? "online" : "offline"));
        }
        
        return success;
    }

    private boolean canOffline(int core) {
        // Boot core cannot be taken offline
        if (core == 0) {
            return false;
        }
        
        // For little cores (1-3), ensure at least 2 will remain online
        if (core >= 1 && core <= 3) {
            int onlineCount = 1; // Core 0 is always online
            for (int i = 1; i <= 3; i++) {
                if (i != core && isCoreOnline(i)) {
                    onlineCount++;
                }
            }
            return onlineCount >= 2; // At least 2 little cores must remain online
        }
        
        // Big cores (4-7) can be taken offline without restriction on 7s Gen 2
        return true;
    }

    private String readFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "Cannot read file: " + path);
                return "";
            }
            
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            reader.close();
            
            return line != null ? line.trim() : "";
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + path, e);
            return "";
        }
    }

    private boolean writeFile(String path, String value) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.canWrite()) {
                Log.w(TAG, "Cannot write to file: " + path);
                return false;
            }
            
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(value);
            writer.close();
            
            // Verify the write was successful
            String readBack = readFile(path);
            boolean success = value.equals(readBack);
            
            if (!success) {
                Log.w(TAG, "Write verification failed for " + path + 
                      ". Wrote: " + value + ", Read: " + readBack);
            }
            
            return success;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + path + " with value " + value, e);
            return false;
        }
    }
}
