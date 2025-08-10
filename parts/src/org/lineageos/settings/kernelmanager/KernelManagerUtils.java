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
    private static final String DEFAULT_GOVERNOR = "schedhorizon";

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

    /**
     * Check if kernel manager functionality is available
     * @return true if basic CPU control files exist
     */
    public boolean isKernelManagerSupported() {
        try {
            // Check essential files exist
            File efficiencyPolicy = new File(CPU_BASE_PATH + EFFICIENCY_CLUSTER);
            File performancePolicy = new File(CPU_BASE_PATH + PERFORMANCE_CLUSTER);
            
            return efficiencyPolicy.exists() && performancePolicy.exists();
        } catch (Exception e) {
            Log.e(TAG, "Error checking kernel manager support", e);
            return false;
        }
    }

    public String[] getAvailableGovernors() {
        try {
            String governors = readFile(CPU_BASE_PATH + EFFICIENCY_CLUSTER + SCALING_AVAILABLE_GOVERNORS);
            if (governors != null && !governors.isEmpty()) {
                return governors.trim().split("\\s+");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available governors", e);
        }
        
        // Fallback governors if reading fails
        return new String[]{"schedhorizon", "schedutil", "performance", "powersave", "ondemand", "conservative"};
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
                return freqArray;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read available frequencies for cluster " + cluster, e);
        }
        return new String[]{"300000", "576000", "768000", "1017600", "1248000", "1324800", "1516800", "1612800"};
    }

    public String getCurrentGovernor(int cluster) {
        try {
            String governor = readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR);
            if (governor != null && !governor.isEmpty()) {
                return governor.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read current governor for cluster " + cluster, e);
        }
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
        
        // Fallback: try to get the lowest available frequency
        String[] frequencies = getAvailableFrequencies(cluster);
        if (frequencies != null && frequencies.length > 0) {
            return frequencies[0];
        }
        return "0";
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
        
        // Fallback: try to get the highest available frequency
        String[] frequencies = getAvailableFrequencies(cluster);
        if (frequencies != null && frequencies.length > 0) {
            return frequencies[frequencies.length - 1];
        }
        return "0";
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
    private int getCpuPolicy(int coreId) {
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

        boolean success = true;
        for (int cluster : POLICIES) {
            try {
                if (!writeFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR, governor)) {
                    success = false;
                    Log.e(TAG, "Failed to set governor for cluster " + cluster);
                }
            } catch (Exception e) {
                success = false;
                Log.e(TAG, "Error setting governor for cluster " + cluster, e);
            }
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

        try {
            return writeFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ, freq);
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

        try {
            return writeFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ, freq);
        } catch (Exception e) {
            Log.e(TAG, "Error setting max frequency for cluster " + cluster, e);
            return false;
        }
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
            String[] availableFreqs = getAvailableFrequencies(cluster);
            if (availableFreqs != null) {
                boolean minFound = false, maxFound = false;
                for (String freq : availableFreqs) {
                    if (freq.equals(minFreq)) minFound = true;
                    if (freq.equals(maxFreq)) maxFound = true;
                }
                
                if (!minFound || !maxFound) {
                    Log.w(TAG, "Frequency not found in available list - min: " + minFound + ", max: " + maxFound);
                }
            }
            
            return true;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid frequency format", e);
            return false;
        }
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
        sb.append("Supported: ").append(isKernelManagerSupported()).append("\n");
        
        for (int cluster : POLICIES) {
            sb.append("Cluster ").append(cluster).append(":\n");
            sb.append("  Governor: ").append(getCurrentGovernor(cluster)).append("\n");
            sb.append("  Min Freq: ").append(getCurrentMinFrequency(cluster)).append("\n");
            sb.append("  Max Freq: ").append(getCurrentMaxFrequency(cluster)).append("\n");
        }
        
        sb.append("CPU Cores:\n");
        for (int i = 0; i < 8; i++) {
            sb.append("  CPU").append(i).append(": ");
            sb.append(isCoreOnline(i) ? "Online" : "Offline");
            sb.append(" @ ").append(getCurrentCoreFrequency(i)).append(" kHz\n");
        }
        
        return sb.toString();
    }
}
