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
        for (int i = 1; i < NUM_CORES; i++) {
            if (mCorePrefs[i] != null) {
                mCorePrefs[i].setChecked(isCoreOnline(i));
            }
        }
        updateCoreStatistics();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean requestedState = (Boolean) newValue;

        for (int i = 1; i < NUM_CORES; i++) {
            if (preference == mCorePrefs[i]) {
                if (!requestedState && !canOffline(i)) {
                    Toast.makeText(getContext(),
                        "At least 2 little cores must remain online",
                        Toast.LENGTH_SHORT).show();
                    return false;
                }

                boolean success = setCoreState(i, requestedState);

                if (success) {
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
        int activeLittleCores = 0;
        int activeBigCores = 0;

        for (int i = 0; i < NUM_CORES; i++) {
            if (isCoreOnline(i)) {
                activeCores++;
                if (i <= 3) activeLittleCores++;
                else activeBigCores++;
            }
        }

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

        String coreType = (core <= 3) ? "Little (A55)" : "Big (A78)";
        String status = isOnline ? "enabled" : "disabled";
        String message = String.format("%s Core %d %s - Total active: %d/%d cores",
            coreType, core, status, activeCores, NUM_CORES);

        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private boolean isCoreOnline(int core) {
        if (core == 0) return true;
        String path = String.format(CORE_ONLINE_PATH, core);
        String value = readFile(path);
        return "1".equals(value.trim());
    }

    private boolean setCoreState(int core, boolean online) {
        if (core == 0) return false;
        String path = String.format(CORE_ONLINE_PATH, core);
        boolean success = writeFile(path, online ? "1" : "0");
        if (success) {
            Log.d(TAG, "Successfully set core " + core + " to " + (online ? "online" : "offline"));
        } else {
            Log.e(TAG, "Failed to set core " + core + " to " + (online ? "online" : "offline"));
        }
        return success;
    }

    private boolean canOffline(int core) {
        if (core == 0) return false;
        if (core >= 1 && core <= 3) {
            int onlineCount = 1;
            for (int i = 1; i <= 3; i++) {
                if (i != core && isCoreOnline(i)) onlineCount++;
            }
            return onlineCount >= 2;
        }
        return true;
    }

    /**
     * FIX: Removed canRead() check.
     * canRead() uses POSIX stat() and does not reflect SELinux MAC policy.
     * Simply attempt the read and return empty string on IOException.
     */
    private String readFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Log.w(TAG, "File not found: " + path);
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

    /**
     * FIX: Removed canWrite() check.
     * canWrite() does not reflect SELinux permissions and would silently block
     * all core online/offline writes even when the policy allows them.
     * Let IOException report real failures.
     */
    private boolean writeFile(String path, String value) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Log.w(TAG, "File not found: " + path);
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
