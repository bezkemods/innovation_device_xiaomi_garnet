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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class KernelManagerUtils {

    public static final int EFFICIENCY_CLUSTER = 0;  // Policy 4 - Little cores (A55)
    public static final int PERFORMANCE_CLUSTER = 4; // Policy 0 - Big cores (A78)

    private static final int[] POLICIES = {EFFICIENCY_CLUSTER, PERFORMANCE_CLUSTER};
    private static final String DEFAULT_GOVERNOR = "schedutil";

    // CPU frequency and governor paths
    private static final String CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq/policy";
    private static final String SCALING_GOVERNOR = "/scaling_governor";
    private static final String SCALING_MIN_FREQ = "/scaling_min_freq";
    private static final String SCALING_MAX_FREQ = "/scaling_max_freq";
    private static final String SCALING_AVAILABLE_GOVERNORS = "/scaling_available_governors";
    private static final String SCALING_AVAILABLE_FREQUENCIES = "/scaling_available_frequencies";

    public String[] getAvailableGovernors() {
        try {
            String governors = readFile(CPU_BASE_PATH + EFFICIENCY_CLUSTER + SCALING_AVAILABLE_GOVERNORS);
            return governors.trim().split("\\s+");
        } catch (Exception e) {
            return new String[]{"schedutil", "performance", "powersave", "ondemand", "conservative"};
        }
    }

    public String[] getAvailableFrequencies(int cluster) {
        try {
            String frequencies = readFile(CPU_BASE_PATH + cluster + SCALING_AVAILABLE_FREQUENCIES);
            return frequencies.trim().split("\\s+");
        } catch (Exception e) {
            return null;
        }
    }

    public String getCurrentGovernor(int cluster) {
        try {
            return readFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR).trim();
        } catch (Exception e) {
            return DEFAULT_GOVERNOR;
        }
    }

    public String getCurrentMinFrequency(int cluster) {
        try {
            return readFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ).trim();
        } catch (Exception e) {
            // If we can't read, try to get the lowest available frequency
            String[] frequencies = getAvailableFrequencies(cluster);
            if (frequencies != null && frequencies.length > 0) {
                return frequencies[0];
            }
            return "0";
        }
    }

    public String getCurrentMaxFrequency(int cluster) {
        try {
            return readFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ).trim();
        } catch (Exception e) {
            // If we can't read, try to get the highest available frequency
            String[] frequencies = getAvailableFrequencies(cluster);
            if (frequencies != null && frequencies.length > 0) {
                return frequencies[frequencies.length - 1];
            }
            return "0";
        }
    }

    public void setGovernor(String governor) {
        for (int cluster : POLICIES) {
            try {
                writeFile(CPU_BASE_PATH + cluster + SCALING_GOVERNOR, governor);
            } catch (Exception ignored) {}
        }
    }

    public void setMinFrequency(int cluster, String freq) {
        try {
            writeFile(CPU_BASE_PATH + cluster + SCALING_MIN_FREQ, freq);
        } catch (Exception ignored) {}
    }

    public void setMaxFrequency(int cluster, String freq) {
        try {
            writeFile(CPU_BASE_PATH + cluster + SCALING_MAX_FREQ, freq);
        } catch (Exception ignored) {}
    }

    private String readFile(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    private void writeFile(String path, String value) throws IOException {
        FileWriter writer = new FileWriter(path);
        try {
            writer.write(value);
        } finally {
            writer.close();
        }
    }
}
