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

public class KernelManagerUtils {

    private static final String TAG = "KernelManagerUtils";
    
    // SM7435 Topology: 4x A55 (Little) + 4x A78 (Big)
    public static final int CLUSTER_LITTLE = 0; // policy0
    public static final int CLUSTER_BIG = 4;    // policy4
    
    // Nincs Prime mag (policy6 vagy policy7) ezen a chipen
    private static final int[] CLUSTERS = {CLUSTER_LITTLE, CLUSTER_BIG};
    
    private static final String DEFAULT_GOVERNOR = "walt"; // Modern Qualcomm alapértelmezett

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
        
        // Fallback if sysfs read fails
        return (cluster == CLUSTER_BIG) ? FREQS_BIG : FREQS_LITTLE;
    }

    public String getCurrentGovernor(int cluster) {
        String gov = readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR);
        return gov != null ? gov.trim() : DEFAULT_GOVERNOR;
    }

    public String getCurrentMinFrequency(int cluster) {
        String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ);
        return freq != null ? freq.trim() : getAvailableFrequencies(cluster)[0];
    }

    public String getCurrentMaxFrequency(int cluster) {
        String freq = readFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ);
        if (freq != null) return freq.trim();
        String[] freqs = getAvailableFrequencies(cluster);
        return freqs[freqs.length - 1];
    }

    public String getCurrentCoreFrequency(int core) {
        // Próbáljuk meg először a scaling_cur_freq-et a policy mappából (gyorsabb/stabilabb)
        int policy = getPolicyForCore(core);
        String freq = readFile(CPU_BASE_PATH + policy + SCALING_CUR_FREQ);
        
        // Ha nem sikerül, próbáljuk a cpuinfo_cur_freq-et
        if (freq == null) {
            freq = readFile(CPU_CORE_PATH + core + "/cpufreq/cpuinfo_cur_freq");
        }
        
        return (freq != null) ? freq.trim() : "0";
    }

    public boolean isCoreOnline(int core) {
        // A CPU0 általában mindig online, kivéve hotplug esetén
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

    public boolean setFrequency(int cluster, String value, boolean isMin) {
        String path = CPU_BASE_PATH + cluster + (isMin ? SCALING_MIN_FREQ : SCALING_MAX_FREQ);
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
            sb.append("  Freq: ").append(getCurrentMinFrequency(cluster)).append(" - ")
              .append(getCurrentMaxFrequency(cluster)).append(" kHz\n");
        }
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
