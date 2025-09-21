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

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class KernelManagerUtils {

    private static final String TAG = "KernelManagerUtils";
    
    public static final int EFFICIENCY_CLUSTER = 0;  // Policy 0 - Little cores (A55)
    public static final int PERFORMANCE_CLUSTER = 4; // Policy 4 - Big cores (A78)

    private static final int[] POLICIES = {EFFICIENCY_CLUSTER, PERFORMANCE_CLUSTER};
    private static final String DEFAULT_GOVERNOR = "walt";

    // CPU frequency and governor paths
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

    // Fallback frequency values aligned with power profile for SM7435
    private static final String[] EFFICIENCY_CLUSTER_FREQUENCIES = {
        "691200", "806400", "940800", "1113600", "1324800", 
        "1497600", "1651200", "1804800", "1958400"
    };
    
    private static final String[] PERFORMANCE_CLUSTER_FREQUENCIES = {
        "691200", "960000", "1190400", "1344000", "1497600", 
        "1651200", "1900800", "2054400", "2112000", "2208000", 
        "2304000", "2400000"
    };

    // Fallback governors if reading fails
    private static final String[] FALLBACK_GOVERNORS = {
        "walt", "schedutil", "performance", "powersave", "ondemand", "conservative"
    };

    /**
     * Check if kernel manager functionality is available
     * @return true if basic CPU control files exist
     */
    public boolean isKernelManagerSupported() {
        try {
            // Check essential files exist
            File efficiencyPolicy = new File(CPU_BASE_PATH + EFFICIENCY_CLUSTER);
            File performancePolicy = new File(CPU_BASE_PATH + PERFORMANCE_CLUSTER);
            
            boolean supported = efficiencyPolicy.exists() && performancePolicy.exists();
            Log.d(TAG, "Kernel manager supported: " + supported);
            return supported;
        } catch (Exception e) {
            Log.e(TAG, "Error checking kernel manager support", e);
            return false;
        }
    }

    public String[] getAvailableGovernors() {
        try {
            String governors = readFile(CPU_BASE_PATH + EFFICIENCY_CLUSTER + SCALING_AVAILABLE_GOVERNORS);
            if (governors != null && !governors.isEmpty()) {
                String[] governorArray = governors.trim().split("\\s+");
                Log.d(TAG, "Available governors: " + java.util.Arrays.toString(governorArray));
                return governorArray;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available governors", e);
        }
        
        // Fallback governors if reading fails
        Log.d(TAG, "Using fallback governors");
        return FALLBACK_GOVERNORS.clone();
    }

    public String[] getAvailableFrequencies(int cluster) {
        try {
            String frequencies = readFile(CPU_BASE_PATH + cluster + SCALING_AVAILABLE_FREQUENCIES);
            if (frequencies != null && !frequencies.isEmpty()) {
                String[] freqArray = frequencies.trim().split("\\s+");
                // Sort frequencies in ascending order
                java.util.Arrays.sort(freqArray, (a, b) -> {
                    try {
                        return Long.compare(Long.parseLong(a), Long.parseLong(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                });
                Log.d(TAG, "Available frequencies for cluster " + cluster + ": " + freqArray.length + " frequencies");
                return freqArray;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available frequencies for cluster " + cluster, e);
        }
        
        // Return fallback frequencies aligned with power profile
        Log.d(TAG, "Using fallback frequencies for cluster " + cluster);
        if (cluster == EFFICIENCY_CLUSTER) {
            return EFFICIENCY_CLUSTER_FREQUENCIES.clone();
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return PERFORMANCE_CLUSTER_FREQUENCIES.clone();
        }
        
        // Generic fallback for unknown clusters
        return EFFICIENCY_CLUSTER_FREQUENCIES.clone();
    }

    public String getCurrentGovernor(int cluster) {
        try {
            String governor = readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR);
            if (governor != null && !governor.isEmpty()) {
                String currentGov = governor.trim();
                Log.d(TAG, "Current governor for cluster " + cluster + ": " + currentGov);
                return currentGov;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read current governor for cluster " + cluster, e);
        }
        
        Log.d(TAG, "Returning default governor: " + DEFAULT_GOVERNOR);
        return DEFAULT_GOVERNOR;
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
        
        // Fallback: return the lowest available frequency for the cluster
        String[] frequencies = getAvailableFrequencies(cluster);
        if (frequencies != null && frequencies.length > 0) {
            return frequencies[0];
        }
        return "691200"; // Lowest frequency from power profile
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
        
        // Fallback: return the highest available frequency for the cluster
        String[] frequencies = getAvailableFrequencies(cluster);
        if (frequencies != null && frequencies.length > 0) {
            return frequencies[frequencies.length - 1];
        }
        
        // Return appropriate max frequency based on cluster
        if (cluster == EFFICIENCY_CLUSTER) {
            return "1958400"; // Efficiency cluster max from power profile
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return "2400000"; // Performance cluster max from power profile
        }
        
        return "1958400";
    }

    /**
     * Get current frequency of a specific CPU core
     * @param coreId CPU core ID (0-7)
     * @return Current frequency in kHz as string
     */
    public String getCurrentCoreFrequency(int coreId) {
        if (coreId < 0 || coreId > 7) {
            Log.w(TAG, "Invalid core ID: " + coreId);
            return "0";
        }

        // First check if core is online
        if (!isCoreOnline(coreId)) {
            return "0";
        }

        // Try multiple paths in order of preference
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
                // Continue to next path
            }
        }

        // Final fallback: try policy-based frequency
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

    /**
     * Check if a CPU core is online
     * @param coreId CPU core ID (0-7)
     * @return true if online, false if offline
     */
    public boolean isCoreOnline(int coreId) {
        if (coreId < 0 || coreId > 7) {
            return false;
        }
        
        // CPU0 is always online
        if (coreId == 0) {
            return true;
        }
        
        try {
            String online = readFile(CPU_CORE_PATH + coreId + ONLINE);
            return online != null && "1".equals(online.trim());
        } catch (Exception e) {
            // If we can't read the online status, assume it's online
            Log.w(TAG, "Could not read online status for core " + coreId + ", assuming online", e);
            return true;
        }
    }

    /**
     * Get the policy (cluster) for a given CPU core
     * @param coreId CPU core ID (0-7)
     * @return Policy ID or -1 if invalid
     */
    public int getCpuPolicy(int coreId) {
        // Snapdragon 7s Gen 2 (SM7435) CPU core mapping:
        // CPU 0-3: Efficiency cluster (A55) - Policy 0
        // CPU 4-7: Performance cluster (A78) - Policy 4
        if (coreId >= 0 && coreId <= 3) {
            return EFFICIENCY_CLUSTER;
        } else if (coreId >= 4 && coreId <= 7) {
            return PERFORMANCE_CLUSTER;
        }
        return -1;
    }

    /**
     * Get frequencies for all CPU cores
     * @return Array of frequency strings (0-7 indices)
     */
    public String[] getAllCoreFrequencies() {
        String[] frequencies = new String[8];
        for (int i = 0; i < 8; i++) {
            frequencies[i] = getCurrentCoreFrequency(i);
        }
        return frequencies;
    }

    /**
     * Get online status for all CPU cores
     * @return Array of boolean status (0-7 indices)
     */
    public boolean[] getAllCoreOnlineStatus() {
        boolean[] status = new boolean[8];
        for (int i = 0; i < 8; i++) {
            status[i] = isCoreOnline(i);
        }
        return status;
    }

    /**
     * Set CPU governor for all clusters
     * @param governor Governor name to set
     * @return true if successful, false otherwise
     */
    public boolean setGovernor(String governor) {
        if (governor == null || governor.isEmpty()) {
            Log.e(TAG, "Invalid governor: " + governor);
            return false;
        }

        // Validate governor against available ones
        String[] availableGovernors = getAvailableGovernors();
        boolean isValid = false;
        for (String availableGovernor : availableGovernors) {
            if (governor.equals(availableGovernor)) {
                isValid = true;
                break;
            }
        }
        
        if (!isValid) {
            Log.e(TAG, "Governor not available: " + governor + ", available: " + 
                java.util.Arrays.toString(availableGovernors));
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

    /**
     * Set minimum frequency for a cluster
     * @param cluster Cluster ID
     * @param freq Frequency in kHz as string
     * @return true if successful, false otherwise
     */
    public boolean setMinFrequency(int cluster, String freq) {
        if (freq == null || freq.isEmpty()) {
            Log.e(TAG, "Invalid frequency: " + freq);
            return false;
        }

        // Validate frequency against available ones
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

    /**
     * Set maximum frequency for a cluster
     * @param cluster Cluster ID
     * @param freq Frequency in kHz as string
     * @return true if successful, false otherwise
     */
    public boolean setMaxFrequency(int cluster, String freq) {
        if (freq == null || freq.isEmpty()) {
            Log.e(TAG, "Invalid frequency: " + freq);
            return false;
        }

        // Validate frequency against available ones
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

    /**
     * Check if a frequency is valid for a given cluster
     * @param cluster Cluster ID
     * @param freq Frequency to validate
     * @return true if valid, false otherwise
     */
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

    /**
     * Validate that min frequency is not higher than max frequency
     * @param cluster Cluster ID
     * @param minFreq Minimum frequency in kHz
     * @param maxFreq Maximum frequency in kHz
     * @return true if valid, false otherwise
     */
    public boolean validateFrequencyRange(int cluster, String minFreq, String maxFreq) {
        try {
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            
            if (min > max) {
                Log.e(TAG, "Min frequency (" + min + ") is higher than max frequency (" + max + ")");
                return false;
            }
            
            // Check if frequencies are available
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
     * Get cluster name for display purposes
     * @param cluster Cluster ID
     * @return Human-readable cluster name
     */
    public String getClusterName(int cluster) {
        if (cluster == EFFICIENCY_CLUSTER) {
            return "Efficiency (A55)";
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return "Performance (A78)";
        }
        return "Unknown";
    }

    /**
     * Get the number of cores in a cluster
     * @param cluster Cluster ID
     * @return Number of cores
     */
    public int getClusterCoreCount(int cluster) {
        // Both clusters have 4 cores according to power profile
        return 4;
    }

    /**
     * Get all core IDs for a given cluster
     * @param cluster Cluster ID
     * @return Array of core IDs
     */
    public int[] getClusterCores(int cluster) {
        if (cluster == EFFICIENCY_CLUSTER) {
            return new int[]{0, 1, 2, 3};
        } else if (cluster == PERFORMANCE_CLUSTER) {
            return new int[]{4, 5, 6, 7};
        }
        return new int[0];
    }

    /**
     * Read content from a file
     * @param path File path to read
     * @return File content as string, null if error
     */
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

    /**
     * Write content to a file
     * @param path File path to write
     * @param value Content to write
     * @return true if successful, false otherwise
     */
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

    /**
     * Get system information for debugging
     * @return Debug information string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Kernel Manager Debug Info:\n");
        sb.append("============================\n");
        sb.append("Supported: ").append(isKernelManagerSupported()).append("\n\n");
        
        // Available governors
        sb.append("Available Governors:\n");
        String[] governors = getAvailableGovernors();
        for (String governor : governors) {
            sb.append("  - ").append(governor).append("\n");
        }
        sb.append("\n");
        
        // Cluster information
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
        
        // CPU core information
        sb.append("CPU Cores:\n");
        for (int i = 0; i < 8; i++) {
            int cluster = getCpuPolicy(i);
            sb.append("  CPU").append(i).append(" (").append(getClusterName(cluster)).append("): ");
            sb.append(isCoreOnline(i) ? "Online" : "Offline");
            sb.append(" @ ").append(getCurrentCoreFrequency(i)).append(" kHz\n");
        }
        
        // Power profile alignment info
        sb.append("\nPower Profile Alignment:\n");
        sb.append("Efficiency Cluster Frequencies: ").append(EFFICIENCY_CLUSTER_FREQUENCIES.length).append(" steps\n");
        sb.append("Performance Cluster Frequencies: ").append(PERFORMANCE_CLUSTER_FREQUENCIES.length).append(" steps\n");
        
        return sb.toString();
    }

    /**
     * Reset all CPU settings to defaults
     * @return true if successful, false otherwise
     */
    public boolean resetToDefaults() {
        Log.d(TAG, "Resetting CPU settings to defaults");
        boolean success = true;
        
        // Reset governor to default
        if (!setGovernor(DEFAULT_GOVERNOR)) {
            success = false;
        }
        
        // Reset frequencies to full range for both clusters
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

    /**
     * Get CPU statistics for monitoring
     * @return CPU statistics object
     */
    public CpuStats getCpuStatistics() {
        CpuStats stats = new CpuStats();
        
        // Count online cores per cluster
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

    /**
     * CPU statistics helper class
     */
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

    /**
     * Check if a file exists and is readable
     * @param path File path to check
     * @return true if exists and readable, false otherwise
     */
    public boolean isFileReadable(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a file exists and is writable
     * @param path File path to check
     * @return true if exists and writable, false otherwise
     */
    public boolean isFileWritable(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canWrite();
        } catch (Exception e) {
            return false;
        }
    }
}
