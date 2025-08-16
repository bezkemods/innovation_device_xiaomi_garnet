/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.performance;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class PerformanceUtils {

    private static final String TAG = "PerformanceUtils";

    // CPU paths
    private static final String POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
    private static final String POLICY4_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";
    private static final String POLICY6_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy6/scaling_governor";

    private static final String PERFORMANCE_GOVERNOR = "performance";
    private static final String DEFAULT_GOVERNOR = "schedhorizon";

    // GPU paths
    private static final String GPU_MAX_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/max_clock_mhz";
    private static final String GPU_MIN_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/min_clock_mhz";
    private static final String GPU_DEFAULT_PWRLEVEL_PATH = "/sys/class/kgsl/kgsl-3d0/default_pwrlevel";
    private static final String GPU_FORCE_CLK_ON_PATH = "/sys/class/kgsl/kgsl-3d0/force_clk_on";
    private static final String GPU_FORCE_RAIL_ON_PATH = "/sys/class/kgsl/kgsl-3d0/force_rail_on";

    // Default values
    private static final String GPU_MIN_FREQ_DEFAULT = "180";
    private static final String GPU_DEFAULT_POWER_LEVEL = "5";

    public boolean isPerformanceModeEnabled() {
        try {
            // Check CPU governors
            String g0 = readLine(POLICY0_GOVERNOR_PATH);
            
            // Try both policy4 and policy6 (different devices may use different policies)
            String g4or6 = null;
            if (fileExists(POLICY4_GOVERNOR_PATH)) {
                g4or6 = readLine(POLICY4_GOVERNOR_PATH);
            } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                g4or6 = readLine(POLICY6_GOVERNOR_PATH);
            }
            
            // Check GPU force clock state
            String clk = "0"; // default if not accessible
            if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                clk = readLine(GPU_FORCE_CLK_ON_PATH);
            }
            
            boolean cpuPerf = PERFORMANCE_GOVERNOR.equals(g0.trim()) && 
                             (g4or6 == null || PERFORMANCE_GOVERNOR.equals(g4or6.trim()));
            boolean gpuPerf = "1".equals(clk.trim());
            
            Log.d(TAG, "Performance check - CPU: " + cpuPerf + ", GPU: " + gpuPerf);
            return cpuPerf && gpuPerf;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking performance mode", e);
            return false;
        }
    }

    public boolean setPerformanceMode(boolean enabled) {
        try {
            String cpuGovernor = enabled ? PERFORMANCE_GOVERNOR : DEFAULT_GOVERNOR;
            
            // Set CPU governors
            boolean cpuSuccess = true;
            try {
                writeLine(POLICY0_GOVERNOR_PATH, cpuGovernor);
                Log.d(TAG, "Set policy0 governor to: " + cpuGovernor);
            } catch (Exception e) {
                Log.w(TAG, "Failed to set policy0 governor", e);
                cpuSuccess = false;
            }

            // Try policy4 first, then policy6
            try {
                if (fileExists(POLICY4_GOVERNOR_PATH)) {
                    writeLine(POLICY4_GOVERNOR_PATH, cpuGovernor);
                    Log.d(TAG, "Set policy4 governor to: " + cpuGovernor);
                } else if (fileExists(POLICY6_GOVERNOR_PATH)) {
                    writeLine(POLICY6_GOVERNOR_PATH, cpuGovernor);
                    Log.d(TAG, "Set policy6 governor to: " + cpuGovernor);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set performance cluster governor", e);
                cpuSuccess = false;
            }

            // Set GPU performance settings
            boolean gpuSuccess = true;
            try {
                if (enabled) {
                    // Lock GPU to high performance
                    if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                        writeLine(GPU_FORCE_CLK_ON_PATH, "1");
                    }
                    if (fileExists(GPU_FORCE_RAIL_ON_PATH)) {
                        writeLine(GPU_FORCE_RAIL_ON_PATH, "1");
                    }
                    if (fileExists(GPU_DEFAULT_PWRLEVEL_PATH)) {
                        writeLine(GPU_DEFAULT_PWRLEVEL_PATH, "0");
                    }
                    if (fileExists(GPU_MIN_CLOCK_PATH) && fileExists(GPU_MAX_CLOCK_PATH)) {
                        String maxClock = readLine(GPU_MAX_CLOCK_PATH).trim();
                        writeLine(GPU_MIN_CLOCK_PATH, maxClock);
                    }
                    Log.d(TAG, "GPU set to performance mode");
                } else {
                    // Restore GPU defaults
                    if (fileExists(GPU_FORCE_CLK_ON_PATH)) {
                        writeLine(GPU_FORCE_CLK_ON_PATH, "0");
                    }
                    if (fileExists(GPU_FORCE_RAIL_ON_PATH)) {
                        writeLine(GPU_FORCE_RAIL_ON_PATH, "0");
                    }
                    if (fileExists(GPU_DEFAULT_PWRLEVEL_PATH)) {
                        writeLine(GPU_DEFAULT_PWRLEVEL_PATH, GPU_DEFAULT_POWER_LEVEL);
                    }
                    if (fileExists(GPU_MIN_CLOCK_PATH)) {
                        writeLine(GPU_MIN_CLOCK_PATH, GPU_MIN_FREQ_DEFAULT);
                    }
                    Log.d(TAG, "GPU restored to default mode");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error setting GPU performance mode", e);
                gpuSuccess = false;
            }

            boolean success = cpuSuccess || gpuSuccess; // Success if at least one worked
            Log.d(TAG, "Performance mode set to " + enabled + " - Success: " + success);
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting performance mode", e);
            return false;
        }
    }

    private static String readLine(String path) throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            String s = br.readLine();
            return s == null ? "" : s;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing BufferedReader", e);
                }
            }
        }
    }

    private static void writeLine(String path, String value) throws IOException {
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
            fw.write(value);
            fw.flush();
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing FileWriter", e);
                }
            }
        }
    }

    private static boolean fileExists(String path) {
        try {
            File file = new File(path);
            return file.exists() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }
}
