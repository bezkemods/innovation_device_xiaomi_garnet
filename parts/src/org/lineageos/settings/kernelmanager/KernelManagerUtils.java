/*
 * Copyright (C) 2025 bezke
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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class KernelManagerUtils {

    private static final String TAG = "KernelManagerUtils";
    
    // SM7435 Topology: 4x A55 (Little) + 4x A78 (Big)
    public static final int CLUSTER_LITTLE = 0; // policy0
    public static final int CLUSTER_BIG = 4;    // policy4
    
    // Aliases for better code readability
    public static final int EFFICIENCY_CLUSTER = CLUSTER_LITTLE;
    public static final int PERFORMANCE_CLUSTER = CLUSTER_BIG;
    
    private static final int[] CLUSTERS = {CLUSTER_LITTLE, CLUSTER_BIG};
    
    private static final String DEFAULT_GOVERNOR = "walt";

    // Paths
    private static final String CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq/policy";
    private static final String CPU_CORE_PATH = "/sys/devices/system/cpu/cpu";
    private static final String SCALING_GOVERNOR = "/scaling_governor";
    private static final String SCALING_MIN_FREQ = "/scaling_min_freq";
    private static final String SCALING_MAX_FREQ = "/scaling_max_freq";
    private static final String SCALING_AVAILABLE_GOVERNORS = "/scaling_available_governors";
    private static final String SCALING_AVAILABLE_FREQUENCIES = "/scaling_available_frequencies";
    private static final String SCALING_CUR_FREQ = "/scaling_cur_freq";
    private static final String ONLINE = "/online";

    // SM7435 Frequencies (kHz) - Fallback values
    private static final String[] FREQS_LITTLE = {
        "691200", "806400", "940800", "1113600", "1324800", "1497600", "1651200", "1804800", "1958400"
    };
    private static final String[] FREQS_BIG = {
        "691200", "960000", "1190400", "1344000", "1497600", "1651200", "1900800", "2054400", "2208000", "2400000"
    };

    // Inner class for CPU statistics
    public static class CpuStats {
        public int efficiencyOnline;
        public int efficiencyTotal;
        public int performanceOnline;
        public int performanceTotal;

        public CpuStats() {
            this.efficiencyTotal = 4;
            this.performanceTotal = 4;
            this.efficiencyOnline = 0;
            this.performanceOnline = 0;
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(),
                "Efficiency: %d/%d, Performance: %d/%d",
                efficiencyOnline, efficiencyTotal,
                performanceOnline, performanceTotal);
        }
    }

    public boolean isKernelManagerSupported() {
        return fileExists(CPU_BASE_PATH + CLUSTER_LITTLE) && 
               fileExists(CPU_BASE_PATH + CLUSTER_BIG);
    }

    public String[] getAvailableGovernors() {
        String governors = readFile(CPU_BASE_PATH + CLUSTER_LITTLE + SCALING_AVAILABLE_GOVERNORS);
        if (governors != null && !governors.trim().isEmpty()) {
            return governors.trim().split("\\s+");
        }
        return new String[]{"walt", "schedutil", "performance", "powersave", "conservative"};
    }

    public String[] getAvailableFrequencies(int cluster) {
        String path = CPU_BASE_PATH + cluster + SCALING_AVAILABLE_FREQUENCIES;
        String content = readFile(path);
        
        if (content != null && !content.trim().isEmpty()) {
            String[] freqs = content.trim().split("\\s+");
            Arrays.sort(freqs, (a, b) -> {
                try {
                    return Long.compare(Long.parseLong(a), Long.parseLong(b));
                } catch (NumberFormatException e) {
                    return 0;
                }
            });
            return freqs;
        }
        
        return (cluster == CLUSTER_BIG) ? FREQS_BIG : FREQS_LITTLE;
    }

    public String getCurrentGovernor(int cluster) {
        String gov = readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR);
        return gov != null ? gov.trim() : DEFAULT_GOVERNOR;
    }

    // Overload without parameter for backward compatibility
    public String getCurrentGovernor() {
        return getCurrentGovernor(CLUSTER_LITTLE);
    }

    public String getMinFrequency(int cluster) {
        String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ);
        return freq != null ? freq.trim() : getAvailableFrequencies(cluster)[0];
    }

    public String getMaxFrequency(int cluster) {
        String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ);
        if (freq != null) return freq.trim();
        String[] freqs = getAvailableFrequencies(cluster);
        return freqs[freqs.length - 1];
    }

    public String getCurrentFrequency(int cluster) {
        String freq = readFile(CPU_BASE_PATH + cluster + SCALING_CUR_FREQ);
        return (freq != null) ? freq.trim() : "0";
    }

    public boolean isCoreOnline(int core) {
        if (core == 0) return true;
        String status = readFile(CPU_CORE_PATH + core + ONLINE);
        return status != null && status.trim().equals("1");
    }

    public boolean setGovernor(String governor) {
        boolean success = true;
        for (int cluster : CLUSTERS) {
            if (!writeFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR, governor)) {
                success = false;
            }
        }
        return success;
    }

    public boolean setMinFrequency(int cluster, String value) {
        String path = CPU_BASE_PATH + cluster + SCALING_MIN_FREQ;
        return writeFile(path, value);
    }

    public boolean setMaxFrequency(int cluster, String value) {
        String path = CPU_BASE_PATH + cluster + SCALING_MAX_FREQ;
        return writeFile(path, value);
    }
    
    public boolean validateFrequencyRange(int cluster, String minFreq, String maxFreq) {
        try {
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            return min <= max;
        } catch (Exception e) {
            return false;
        }
    }

    public void resetKernelSettings() {
        // Reset to default governor
        setGovernor(DEFAULT_GOVERNOR);
        
        // Reset frequencies to default range for each cluster
        for (int cluster : CLUSTERS) {
            String[] freqs = getAvailableFrequencies(cluster);
            if (freqs.length > 0) {
                setMinFrequency(cluster, freqs[0]);
                setMaxFrequency(cluster, freqs[freqs.length - 1]);
            }
        }
        Log.d(TAG, "Kernel settings reset to defaults");
    }

    public CpuStats getCpuStats() {
        CpuStats stats = new CpuStats();
        
        // Count online cores for efficiency cluster (0-3)
        for (int i = 0; i < 4; i++) {
            if (isCoreOnline(i)) {
                stats.efficiencyOnline++;
            }
        }
        
        // Count online cores for performance cluster (4-7)
        for (int i = 4; i < 8; i++) {
            if (isCoreOnline(i)) {
                stats.performanceOnline++;
            }
        }
        
        return stats;
    }

    public String formatFrequency(String freqKhz) {
        try {
            long khz = Long.parseLong(freqKhz);
            double mhz = khz / 1000.0;
            if (mhz >= 1000) {
                return String.format(Locale.getDefault(), "%.2f GHz", mhz / 1000.0);
            } else {
                return String.format(Locale.getDefault(), "%.0f MHz", mhz);
            }
        } catch (NumberFormatException e) {
            return freqKhz;
        }
    }

    public int getPolicyForCore(int core) {
        if (core >= 0 && core <= 3) return CLUSTER_LITTLE;
        if (core >= 4 && core <= 7) return CLUSTER_BIG;
        return CLUSTER_LITTLE;
    }
    
    public String getClusterName(int cluster) {
        return (cluster == CLUSTER_BIG) ? "Performance (A78)" : "Efficiency (A55)";
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Kernel Manager Debug Info\n=======================\n");
        sb.append("Supported: ").append(isKernelManagerSupported()).append("\n");
        
        for (int cluster : CLUSTERS) {
            sb.append("\nCluster ").append(cluster).append(" (").append(getClusterName(cluster)).append("):\n");
            sb.append("  Governor: ").append(getCurrentGovernor(cluster)).append("\n");
            sb.append("  Min Freq: ").append(formatFrequency(getMinFrequency(cluster))).append("\n");
            sb.append("  Max Freq: ").append(formatFrequency(getMaxFrequency(cluster))).append("\n");
            sb.append("  Current: ").append(formatFrequency(getCurrentFrequency(cluster))).append("\n");
        }
        
        CpuStats stats = getCpuStats();
        sb.append("\n").append(stats.toString()).append("\n");
        
        return sb.toString();
    }

    // --- Helper Methods ---

    private boolean fileExists(String path) {
        return new File(path).exists();
    }

    private String readFile(String path) {
        if (!fileExists(path)) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean writeFile(String path, String value) {
        if (!fileExists(path)) return false;
        try (FileWriter fw = new FileWriter(path)) {
            fw.write(value);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Write failed to " + path, e);
            return false;
        }
    }
}
