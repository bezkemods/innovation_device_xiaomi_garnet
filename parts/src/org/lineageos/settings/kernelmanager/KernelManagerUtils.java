/*
 * Copyright (C) 2025 bezke
 * Optimized for Garnet (Snapdragon 7s Gen 2, SM7435)
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
        "691200", "806400", "940800", "1113600", "1324800", "1497600", 
        "1651200", "1804800", "1958400", "2112000", "2208000", "2400000"
    };

    private static final String[] FALLBACK_GOVERNORS = {
        "walt", "schedutil", "performance", "powersave", "ondemand", "conservative"
    };

    // Cache for optimization
    private String[] mCachedFrequencies = null;
    private String[] mCachedGovernors = null;
    private long mLastCacheTime = 0;
    private static final long CACHE_VALIDITY_MS = 5000;

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
        if (mCachedFrequencies != null && (currentTime - mLastCacheTime) < CACHE_VALIDITY_MS) {
            return mCachedFrequencies;
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
                mCachedFrequencies = freqArray;
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
        
        String[] paths = {
            CPU_CORE_PATH + coreId + "/cpufreq" + SCALING_CUR_FREQ,
            CPU_CORE_PATH + coreId + "/cpufreq" + CPUINFO_CUR_FREQ
        };
        
        for (String path : paths) {
            try {
                String freq = readFile(path);
                if (freq != null && !freq.isEmpty() && !freq.equals("0")) {
                    return freq.trim();
                }
            } catch (Exception e) {
                // Try next path
            }
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
            return true;
        }
        try {
            String online = readFile(CPU_CORE_PATH + coreId + ONLINE);
            return online != null && "1".equals(online.trim());
        } catch (Exception e) {
            Log.w(TAG, "Could not read online status for core " + coreId, e);
            return true;
        }
    }

    public int getCpuPolicy(int coreId) {
        if (coreId >= 0 && coreId <= 3) {
            return EFFICIENCY_CLUSTER;
        } else if (coreId >= 4 && coreId <= 7) {
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
            Log.e(TAG, "Invalid governor: " + governor);
            return false;
        }
        
        String[] availableGovernors = getAvailableGovernors();
        boolean isValid = false;
        for (String availableGovernor : availableGovernors) {
            if (governor.equals(availableGovernor)) {
                isValid = true;
                break;
            }
        }
        
        if (!isValid) {
            Log.e(TAG, "Governor not available: " + governor);
            return false;
        }
        
        boolean success = true;
        for (int cluster : POLICIES) {
            try {
                if (!writeFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR, governor)) {
                    success = false;
                    Log.e(TAG, "Failed to set governor for cluster " + cluster);
                                } else {
                    Log.d(TAG, "Successfully set governor " + governor + " for cluster " + cluster);
                }
            } catch (Exception e) {
                success = false;
                Log.e(TAG, "Error setting governor for cluster " + cluster, e);
            }
        }
        
        if (success) {
            Log.i(TAG, "Governor successfully set to: " + governor);
        }
        return success;
    }

    public boolean setMinFrequency(int cluster, String freq) {
        if (freq == null || freq.isEmpty()) {
            Log.e(TAG, "Invalid frequency: " + freq);
            return false;
        }
        if (!isFrequencyValid(cluster, freq)) {
            Log.e(TAG, "Frequency not available for cluster " + cluster + ": " + freq);
            return false;
        }
        try {
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
            Log.e(TAG, "Invalid frequency: " + freq);
            return false;
        }
        if (!isFrequencyValid(cluster, freq)) {
            Log.e(TAG, "Frequency not available for cluster " + cluster + ": " + freq);
            return false;
        }
        try {
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
        boolean success = true;
        
        if (!setGovernor(DEFAULT_GOVERNOR)) {
            success = false;
        }
        
        for (int cluster : POLICIES) {
            String[] frequencies = getAvailableFrequencies(cluster);
            if (frequencies != null && frequencies.length > 0) {
                String minFreq = frequencies[0];
                String maxFreq = frequencies[frequencies.length - 1];
                if (!setMinFrequency(cluster, minFreq)) {
                    success = false;
                }
                if (!setMaxFrequency(cluster, maxFreq)) {
                    success = false;
                }
            }
        }
        
        Log.d(TAG, "CPU settings reset to defaults " + (success ? "successful" : "partially failed"));
        return success;
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

    public boolean isFileWritable(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    private String readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            throw new IOException("Cannot read file: " + path);
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
        if (!file.exists() || !file.canWrite()) {
            throw new IOException("Cannot write to file: " + path);
        }
        
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
        mCachedFrequencies = null;
        mCachedGovernors = null;
        mLastCacheTime = 0;
    }
}
