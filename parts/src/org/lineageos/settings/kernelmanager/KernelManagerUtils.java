/*
 * Copyright (C) 2025 bezke
 * Optimized for Garnet (Snapdragon 7s Gen 2, SM7435)
 * Battery-optimized version
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.kernelmanager;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class KernelManagerUtils {

    private static final String TAG = "KernelManagerUtils";
    
    // SM7435: 4+4 cluster config
    public static final int EFFICIENCY_CLUSTER = 0;  // Policy 0 - Little cores (A55)
    public static final int PERFORMANCE_CLUSTER = 4; // Policy 4 - Big cores (A78)

    private static final int[] POLICIES = {EFFICIENCY_CLUSTER, PERFORMANCE_CLUSTER};
    private static final String DEFAULT_GOVERNOR = "walt";

    // Sysfs paths
    private static final String CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq/policy";
    private static final String CPU_CORE_PATH = "/sys/devices/system/cpu/cpu";
    private static final String SCALING_GOVERNOR = "/scaling_governor";
    private static final String SCALING_MIN_FREQ = "/scaling_min_freq";
    private static final String SCALING_MAX_FREQ = "/scaling_max_freq";
    private static final String SCALING_AVAILABLE_GOVERNORS = "/scaling_available_governors";
    private static final String SCALING_AVAILABLE_FREQUENCIES = "/scaling_available_frequencies";
    private static final String SCALING_CUR_FREQ = "/scaling_cur_freq";
    private static final String CPUINFO_CUR_FREQ = "/cpuinfo_cur_freq";
    private static final String ONLINE = "/online";

    // SM7435 frequency defaults (Snapdragon 7s Gen 2)
    // A55 Cluster: 691 MHz - 1.96 GHz
    private static final String[] EFFICIENCY_CLUSTER_FREQUENCIES = {
        "691200", "806400", "940800", "1113600", "1324800", 
        "1497600", "1651200", "1804800", "1958400"
    };
    
    // A78 Cluster: 691 MHz - 2.40 GHz
    private static final String[] PERFORMANCE_CLUSTER_FREQUENCIES = {
        "691200", "960000", "1190400", "1344000", "1497600", "1651200",
        "1900800", "2054400", "2112000", "2208000", "2304000", "2400000"
    };

    private static final String[] FALLBACK_GOVERNORS = {
        "walt", "schedutil", "performance", "powersave", "ondemand", "conservative"
    };

    // Cache for optimization - extended validity for battery saving
    // Per-cluster caches to avoid returning efficiency freqs for performance cluster and vice versa
    private String[] mCachedFrequenciesEfficiency = null;
    private String[] mCachedFrequenciesPerformance = null;
    private String[] mCachedGovernors = null;
    private long mLastCacheTime = 0;
    private static final long CACHE_VALIDITY_MS = 10000; // Extended to 10 seconds for battery

    public boolean isKernelManagerSupported() {
        try {
            File efficiencyPolicy = new File(CPU_BASE_PATH + EFFICIENCY_CLUSTER);
            File performancePolicy = new File(CPU_BASE_PATH + PERFORMANCE_CLUSTER);
            return efficiencyPolicy.exists() && performancePolicy.exists();
        } catch (Exception e) {
            Log.e(TAG, "Error checking kernel manager support", e);
            return false;
        }
    }

    public String[] getAvailableGovernors() {
        long currentTime = System.currentTimeMillis();
        if (mCachedGovernors != null && (currentTime - mLastCacheTime) < CACHE_VALIDITY_MS) {
            return mCachedGovernors;
        }

        try {
            String governors = readFile(CPU_BASE_PATH + EFFICIENCY_CLUSTER + SCALING_AVAILABLE_GOVERNORS);
            if (governors != null && !governors.isEmpty()) {
                mCachedGovernors = governors.trim().split("\\s+");
                mLastCacheTime = currentTime;
                Log.d(TAG, "Available governors: " + java.util.Arrays.toString(mCachedGovernors));
                return mCachedGovernors;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available governors", e);
        }
        
        mCachedGovernors = FALLBACK_GOVERNORS.clone();
        return mCachedGovernors;
    }

    public String[] getAvailableFrequencies(int cluster) {
        long currentTime = System.currentTimeMillis();
        // Use per-cluster cache to avoid returning wrong cluster's frequencies
        String[] cachedForCluster = (cluster == EFFICIENCY_CLUSTER)
                ? mCachedFrequenciesEfficiency : mCachedFrequenciesPerformance;
        if (cachedForCluster != null && (currentTime - mLastCacheTime) < CACHE_VALIDITY_MS) {
            return cachedForCluster;
        }

        try {
            String frequencies = readFile(CPU_BASE_PATH + cluster + SCALING_AVAILABLE_FREQUENCIES);
            if (frequencies != null && !frequencies.isEmpty()) {
                String[] freqArray = frequencies.trim().split("\\s+");
                java.util.Arrays.sort(freqArray, (a, b) -> {
                    try {
                        return Long.compare(Long.parseLong(a), Long.parseLong(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                });
                if (cluster == EFFICIENCY_CLUSTER) {
                    mCachedFrequenciesEfficiency = freqArray;
                } else {
                    mCachedFrequenciesPerformance = freqArray;
                }
                mLastCacheTime = currentTime;
                Log.d(TAG, "Available frequencies for cluster " + cluster + ": " + freqArray.length);
                return freqArray;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available frequencies for cluster " + cluster, e);
        }

        if (cluster == EFFICIENCY_CLUSTER) {
            return EFFICIENCY_CLUSTER_FREQUENCIES.clone();
        } else {
            return PERFORMANCE_CLUSTER_FREQUENCIES.clone();
        }
    }

    public String getCurrentGovernor(int cluster) {
        try {
            String governor = readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR);
            return governor != null ? governor.trim() : DEFAULT_GOVERNOR;
        } catch (Exception e) {
            Log.w(TAG, "Could not read current governor for cluster " + cluster, e);
            return DEFAULT_GOVERNOR;
        }
    }

    public String getCurrentMinFrequency(int cluster) {
        try {
            String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ);
            if (freq != null && !freq.isEmpty()) {
                return freq.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read min frequency for cluster " + cluster, e);
        }
        String[] frequencies = getAvailableFrequencies(cluster);
        return (frequencies != null && frequencies.length > 0) ? frequencies[0] : "691200";
    }

    public String getCurrentMaxFrequency(int cluster) {
        try {
            String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ);
            if (freq != null && !freq.isEmpty()) {
                return freq.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read max frequency for cluster " + cluster, e);
        }
        String[] frequencies = getAvailableFrequencies(cluster);
        if (frequencies != null && frequencies.length > 0) {
            return frequencies[frequencies.length - 1];
        }
        return (cluster == EFFICIENCY_CLUSTER) ? "1958400" : "2400000";
    }

    public String getCurrentCoreFrequency(int coreId) {
        if (coreId < 0 || coreId > 7) {
            return "0";
        }
        if (!isCoreOnline(coreId)) {
            return "0";
        }
        
        // Battery optimization: Only try one path instead of multiple
        try {
            String freq = readFile(CPU_CORE_PATH + coreId + "/cpufreq" + SCALING_CUR_FREQ);
            if (freq != null && !freq.isEmpty() && !freq.equals("0")) {
                return freq.trim();
            }
        } catch (Exception e) {
            // Fall through to policy check
        }
        
        int policy = getCpuPolicy(coreId);
        if (policy != -1) {
            try {
                String freq = readFile(CPU_BASE_PATH + policy + SCALING_CUR_FREQ);
                if (freq != null && !freq.isEmpty()) {
                    return freq.trim();
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read frequency for core " + coreId, e);
            }
        }
        return "0";
    }

    public boolean isCoreOnline(int coreId) {
        if (coreId < 0 || coreId > 7) {
            return false;
        }
        if (coreId == 0) {
            return true; // CPU0 is always online
        }
        
        try {
            String online = readFile(CPU_CORE_PATH + coreId + ONLINE);
            return online != null && online.trim().equals("1");
        } catch (Exception e) {
            return false;
        }
    }

    public int getCpuPolicy(int coreId) {
        if (coreId >= 0 && coreId < 4) {
            return EFFICIENCY_CLUSTER;
        } else if (coreId >= 4 && coreId < 8) {
            return PERFORMANCE_CLUSTER;
        }
        return -1;
    }

    public String getClusterName(int cluster) {
        if (cluster == EFFICIENCY_CLUSTER) {
            return "Efficiency (A55)";
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return "Performance (A78)";
        }
        return "Unknown";
    }

    public int[] getClusterCores(int cluster) {
        if (cluster == EFFICIENCY_CLUSTER) {
            return new int[]{0, 1, 2, 3};
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return new int[]{4, 5, 6, 7};
        }
        return new int[0];
    }

    public boolean setGovernor(String governor) {
        if (governor == null || governor.isEmpty()) {
            Log.e(TAG, "Governor cannot be null or empty");
            return false;
        }

        boolean success = true;
        for (int cluster : POLICIES) {
            try {
                boolean result = writeFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR, governor);
                if (result) {
                    Log.d(TAG, "Set governor for cluster " + cluster + " to: " + governor);
                } else {
                    success = false;
                    Log.e(TAG, "Failed to set governor for cluster " + cluster);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting governor for cluster " + cluster, e);
                success = false;
            }
        }
        
        if (success) {
            invalidateCache();
        }
        return success;
    }

    public boolean setMinFrequency(int cluster, String freq) {
        if (freq == null || freq.isEmpty()) {
            Log.e(TAG, "Frequency cannot be null or empty");
            return false;
        }

        try {
            // Battery optimization: Validate before write
            if (!isFrequencyValid(cluster, freq)) {
                Log.e(TAG, "Invalid frequency: " + freq + " for cluster " + cluster);
                return false;
            }

            boolean success = writeFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ, freq);
            if (success) {
                Log.d(TAG, "Set min frequency for cluster " + cluster + " to " + freq);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error setting min frequency for cluster " + cluster, e);
            return false;
        }
    }

    public boolean setMaxFrequency(int cluster, String freq) {
        if (freq == null || freq.isEmpty()) {
            Log.e(TAG, "Frequency cannot be null or empty");
            return false;
        }

        try {
            // Battery optimization: Validate before write
            if (!isFrequencyValid(cluster, freq)) {
                Log.e(TAG, "Invalid frequency: " + freq + " for cluster " + cluster);
                return false;
            }

            boolean success = writeFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ, freq);
            if (success) {
                Log.d(TAG, "Set max frequency for cluster " + cluster + " to " + freq);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error setting max frequency for cluster " + cluster, e);
            return false;
        }
    }

    private boolean isFrequencyValid(int cluster, String freq) {
        String[] availableFreqs = getAvailableFrequencies(cluster);
        if (availableFreqs != null) {
            for (String availableFreq : availableFreqs) {
                if (freq.equals(availableFreq)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean validateFrequencyRange(int cluster, String minFreq, String maxFreq) {
        try {
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            if (min > max) {
                Log.e(TAG, "Min frequency (" + min + ") is higher than max frequency (" + max + ")");
                return false;
            }
            if (!isFrequencyValid(cluster, minFreq) || !isFrequencyValid(cluster, maxFreq)) {
                Log.e(TAG, "One or both frequencies not available for cluster " + cluster);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid frequency format", e);
            return false;
        }
    }

    /**
     * Apply battery saver profile.
     * Governor intentionally NOT changed — walt must stay active so PowerHAL
     * powerhint.json hints continue to function. Only frequency ceilings are capped.
     */
    public boolean applyBatterySaverProfile() {
        Log.d(TAG, "Applying battery saver CPU profile");
        boolean success = true;

        // Efficiency cluster: cap at 1.5 GHz
        if (!setMaxFrequency(EFFICIENCY_CLUSTER, "1497600")) success = false;
        // Performance cluster: cap at 1.9 GHz
        if (!setMaxFrequency(PERFORMANCE_CLUSTER, "1900800")) success = false;

        // Keep min at lowest
        if (!setMinFrequency(EFFICIENCY_CLUSTER, "691200")) success = false;
        if (!setMinFrequency(PERFORMANCE_CLUSTER, "691200")) success = false;

        Log.d(TAG, "Battery saver profile " + (success ? "applied" : "partially failed"));
        return success;
    }

    /**
     * Apply balanced profile
     * Full frequency range with walt governor
     */
    public boolean applyBalancedProfile() {
        Log.d(TAG, "Applying balanced CPU profile");
        boolean success = true;

        // Set walt governor (default)
        if (!setGovernor("walt")) {
            success = false;
        }

        // Full frequency range
        String[] effFreqs = getAvailableFrequencies(EFFICIENCY_CLUSTER);
        String[] perfFreqs = getAvailableFrequencies(PERFORMANCE_CLUSTER);

        if (effFreqs != null && effFreqs.length > 0) {
            if (!setMinFrequency(EFFICIENCY_CLUSTER, effFreqs[0])) {
                success = false;
            }
            if (!setMaxFrequency(EFFICIENCY_CLUSTER, effFreqs[effFreqs.length - 1])) {
                success = false;
            }
        }

        if (perfFreqs != null && perfFreqs.length > 0) {
            if (!setMinFrequency(PERFORMANCE_CLUSTER, perfFreqs[0])) {
                success = false;
            }
            if (!setMaxFrequency(PERFORMANCE_CLUSTER, perfFreqs[perfFreqs.length - 1])) {
                success = false;
            }
        }

        Log.d(TAG, "Balanced profile " + (success ? "applied" : "partially failed"));
        return success;
    }

    /**
     * Apply performance profile.
     * Governor intentionally NOT changed — walt must stay active so PowerHAL
     * powerhint.json hints continue to function. Full frequency range is unlocked.
     */
    public boolean applyPerformanceProfile() {
        Log.d(TAG, "Applying performance CPU profile");
        boolean success = true;

        // Full frequency range
        String[] effFreqs = getAvailableFrequencies(EFFICIENCY_CLUSTER);
        String[] perfFreqs = getAvailableFrequencies(PERFORMANCE_CLUSTER);

        if (effFreqs != null && effFreqs.length > 0) {
            if (!setMinFrequency(EFFICIENCY_CLUSTER, effFreqs[0])) success = false;
            if (!setMaxFrequency(EFFICIENCY_CLUSTER, effFreqs[effFreqs.length - 1])) success = false;
        }

        if (perfFreqs != null && perfFreqs.length > 0) {
            if (!setMinFrequency(PERFORMANCE_CLUSTER, perfFreqs[0])) success = false;
            if (!setMaxFrequency(PERFORMANCE_CLUSTER, perfFreqs[perfFreqs.length - 1])) success = false;
        }

        Log.d(TAG, "Performance profile " + (success ? "applied" : "partially failed"));
        return success;
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Kernel Manager Debug Info:\n");
        sb.append("============================\n");
        sb.append("Supported: ").append(isKernelManagerSupported()).append("\n");
        sb.append("Device: SM7435 (Snapdragon 7s Gen 2)\n");
        sb.append("Cluster Config: 4+4 (A55 + A78)\n\n");
        
        sb.append("Available Governors:\n");
        String[] governors = getAvailableGovernors();
        for (String governor : governors) {
            sb.append("  - ").append(governor).append("\n");
        }
        sb.append("\n");
        
        for (int cluster : POLICIES) {
            sb.append("Cluster ").append(cluster).append(" (").append(getClusterName(cluster)).append("):\n");
            sb.append("  Current Governor: ").append(getCurrentGovernor(cluster)).append("\n");
            sb.append("  Min Frequency: ").append(getCurrentMinFrequency(cluster)).append(" kHz\n");
            sb.append("  Max Frequency: ").append(getCurrentMaxFrequency(cluster)).append(" kHz\n");
            sb.append("  Available Frequencies:\n");
            String[] frequencies = getAvailableFrequencies(cluster);
            for (String freq : frequencies) {
                sb.append("    - ").append(freq).append(" kHz\n");
            }
            sb.append("\n");
        }
        
        sb.append("CPU Cores:\n");
        for (int i = 0; i < 8; i++) {
            int cluster = getCpuPolicy(i);
            sb.append("  CPU").append(i).append(" (").append(getClusterName(cluster)).append("): ");
            sb.append(isCoreOnline(i) ? "Online" : "Offline");
            sb.append(" @ ").append(getCurrentCoreFrequency(i)).append(" kHz\n");
        }
        
        return sb.toString();
    }

    public boolean resetToDefaults() {
        Log.d(TAG, "Resetting CPU settings to defaults");
        return applyBalancedProfile();
    }

    public CpuStats getCpuStatistics() {
        CpuStats stats = new CpuStats();
        int[] efficiencyCores = getClusterCores(EFFICIENCY_CLUSTER);
        int[] performanceCores = getClusterCores(PERFORMANCE_CLUSTER);
        
        for (int coreId : efficiencyCores) {
            if (isCoreOnline(coreId)) {
                stats.efficiencyOnline++;
            }
        }
        for (int coreId : performanceCores) {
            if (isCoreOnline(coreId)) {
                stats.performanceOnline++;
            }
        }
        
        stats.efficiencyTotal = efficiencyCores.length;
        stats.performanceTotal = performanceCores.length;
        stats.totalOnline = stats.efficiencyOnline + stats.performanceOnline;
        stats.totalCores = stats.efficiencyTotal + stats.performanceTotal;
        
        return stats;
    }

    public static class CpuStats {
        public int efficiencyOnline = 0;
        public int efficiencyTotal = 0;
        public int performanceOnline = 0;
        public int performanceTotal = 0;
        public int totalOnline = 0;
        public int totalCores = 0;
        
        @Override
        public String toString() {
            return String.format("CPU Stats - Efficiency: %d/%d, Performance: %d/%d, Total: %d/%d",
                efficiencyOnline, efficiencyTotal, 
                performanceOnline, performanceTotal,
                totalOnline, totalCores);
        }
    }

    public boolean isFileReadable(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIX: Removed canWrite() check.
     * File.canWrite() only checks POSIX DAC bits via stat(), it does NOT
     * consult SELinux/MAC policies. On Android sysfs nodes, canWrite() can
     * return false even when the SELinux domain allows the write, causing
     * all governor and frequency writes to silently fail.
     * We now simply attempt the write and let IOException report real failures.
     */
    private String readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing reader", e);
                }
            }
        }
    }

    private boolean writeFile(String path, String value) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        // FIX: Removed !file.canWrite() check — canWrite() does not reflect
        // SELinux permissions, causing legitimate writes to be blocked before
        // even attempting the I/O. Let FileWriter throw if access is truly denied.
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(value);
            writer.flush();
            return true;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing writer", e);
                }
            }
        }
    }

    public void invalidateCache() {
        mCachedFrequenciesEfficiency = null;
        mCachedFrequenciesPerformance = null;
        mCachedGovernors = null;
        mLastCacheTime = 0;
    }
}
