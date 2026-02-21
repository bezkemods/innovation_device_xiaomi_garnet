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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager; // FIX: was android.preference.PreferenceManager
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CoreControlUtils {
    private static final String TAG = "CoreControlUtils";
    private static final int NUM_CORES = 8;
    private static final String CORE_ONLINE_PATH = "/sys/devices/system/cpu/cpu%d/online";
    private static final String PREF_PREFIX = "core_control_";

    /**
     * Check if core control is supported on this device.
     * FIX: Removed canWrite() check — canWrite() does not reflect SELinux
     * permissions and would return false even when writes are allowed by policy.
     * Existence of the sysfs node is sufficient to consider it supported.
     */
    public static boolean isSupported() {
        File core1File = new File(String.format(CORE_ONLINE_PATH, 1));
        return core1File.exists();
    }

    /**
     * Get the current state of a CPU core
     */
    public static boolean isCoreOnline(int core) {
        if (core < 0 || core >= NUM_CORES) {
            return false;
        }
        if (core == 0) {
            return true; // Boot core is always online
        }
        String path = String.format(CORE_ONLINE_PATH, core);
        String value = readFile(path);
        return "1".equals(value.trim());
    }

    /**
     * Set the state of a CPU core
     */
    public static boolean setCoreState(int core, boolean online) {
        if (core < 1 || core >= NUM_CORES) {
            Log.w(TAG, "Cannot change state of core " + core);
            return false;
        }
        String path = String.format(CORE_ONLINE_PATH, core);
        return writeFile(path, online ? "1" : "0");
    }

    /**
     * Check if a core can be safely taken offline
     */
    public static boolean canOffline(int core) {
        if (core == 0) {
            return false; // Boot core cannot be taken offline
        }
        // For little cores (1-3), ensure at least 2 will remain online
        if (core >= 1 && core <= 3) {
            int onlineCount = 1; // Core 0 is always online
            for (int i = 1; i <= 3; i++) {
                if (i != core && isCoreOnline(i)) {
                    onlineCount++;
                }
            }
            return onlineCount >= 2;
        }
        return true; // Big cores (4-7) can be taken offline
    }

    /**
     * Get statistics about active cores
     */
    public static CoreStats getCoreStatistics() {
        int activeCores = 0;
        int activeLittleCores = 0;
        int activeBigCores = 0;

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

        return new CoreStats(activeCores, activeLittleCores, activeBigCores, 0);
    }

    /**
     * Save core preferences
     */
    public static void saveCorePreferences(Context context) {
        if (!isSupported()) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        for (int i = 1; i < NUM_CORES; i++) {
            editor.putBoolean(PREF_PREFIX + i, isCoreOnline(i));
        }

        editor.apply();
        Log.d(TAG, "Core preferences saved");
    }

    /**
     * Restore core preferences
     */
    public static void restoreCorePreferences(Context context) {
        if (!isSupported()) {
            Log.d(TAG, "Core control not supported, skipping restore");
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        for (int i = 1; i < NUM_CORES; i++) {
            String key = PREF_PREFIX + i;
            if (prefs.contains(key)) {
                boolean savedState = prefs.getBoolean(key, true);
                boolean currentState = isCoreOnline(i);

                if (savedState != currentState) {
                    if (!savedState && canOffline(i)) {
                        setCoreState(i, false);
                        Log.d(TAG, "Restored core " + i + " to offline");
                    } else if (savedState) {
                        setCoreState(i, true);
                        Log.d(TAG, "Restored core " + i + " to online");
                    }
                }
            }
        }
    }

    /**
     * FIX: Removed canRead() check.
     * canRead() uses POSIX stat() and does not reflect SELinux MAC policy.
     * Simply attempt the read and return empty string on failure.
     */
    private static String readFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
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
     * canWrite() does not reflect SELinux permissions — it would return false
     * even when the platform_app domain is allowed to write, silently blocking
     * all core online/offline operations. Let IOException report real failures.
     */
    private static boolean writeFile(String path, String value) {
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

    /**
     * Data class for core statistics
     */
    public static class CoreStats {
        public final int totalActive;
        public final int littleActive;
        public final int bigActive;
        public final int primeActive; // Kept for structure compatibility, unused on SM7435

        public CoreStats(int totalActive, int littleActive, int bigActive, int primeActive) {
            this.totalActive = totalActive;
            this.littleActive = littleActive;
            this.bigActive = bigActive;
            this.primeActive = primeActive;
        }

        @Override
        public String toString() {
            return String.format("%d/%d cores active (Little: %d/4, Big: %d/4)",
                    totalActive, NUM_CORES, littleActive, bigActive);
        }
    }
}
