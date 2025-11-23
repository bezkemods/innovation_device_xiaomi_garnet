/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.gpumanager;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class GpuManagerUtils {
    private static final String TAG = "GpuManagerUtils";
    private static final String GPU_BASE_PATH = "/sys/class/kgsl/kgsl-3d0";
    
    // Default governor for Adreno 7xx is usually msm-adreno-tz
    private static final String DEFAULT_GOVERNOR = "msm-adreno-tz";
    private static final String TURBO_GOVERNOR = "performance";

    // Adreno 710 gyári frekvenciák (Hz-ben)
    private static final String[] FALLBACK_FREQUENCIES = {
        "180000000", "265000000", "370000000", "465000000", "550000000",
        "670000000", "800000000", "940000000"
    };

    // Paths
    private static final String GPU_MODEL = "/gpu_model";
    private static final String GPU_AVAILABLE_FREQUENCIES = "/gpu_available_frequencies";
    private static final String GPU_CURRENT_FREQ = "/gpuclk";
    private static final String GPU_MIN_FREQ = "/devfreq/min_freq";
    private static final String GPU_MAX_FREQ = "/devfreq/max_freq";
    private static final String GPU_GOVERNOR = "/devfreq/governor";
    private static final String GPU_AVAILABLE_GOVERNORS = "/devfreq/available_governors";
    private static final String GPU_BUSY_PERCENTAGE = "/gpu_busy_percentage";
    private static final String GPU_TEMPERATURE = "/temp";
    private static final String GPU_THERMAL_PWRLEVEL = "/thermal_pwrlevel";
    private static final String GPU_FORCE_CLK_ON = "/force_clk_on";
    private static final String GPU_FORCE_BUS_ON = "/force_bus_on";
    private static final String GPU_FORCE_RAIL_ON = "/force_rail_on";
    private static final String GPU_FORCE_NO_NAP = "/force_no_nap";
    private static final String GPU_BUS_SPLIT = "/bus_split";
    private static final String GPU_RESET_COUNT = "/reset_count";
    private static final String GPU_PREEMPT_COUNT = "/preempt_count";
    private static final String GPU_PREEMPT = "/preempt";
    private static final String GPU_MAX_GPUCLK = "/max_gpuclk";
    
    // Newer kernels might symlink devfreq differently
    private static final String DEVFREQ_BASE = "/sys/class/devfreq/"; 

    // Kamera/video boost
    private static final String POWERHINT_TRIGGER_PATH = "/proc/powerhint";
    private static final String CAMERA_BOOST_HINT_ID = "0x00001340";

    public boolean isGpuManagerSupported() {
        // Ellenőrizzük a KGSL mappát, ami a legtöbb Adreno drivernél alap
        File gpuBase = new File(GPU_BASE_PATH);
        return gpuBase.exists() && gpuBase.isDirectory();
    }

    public String getGpuModel() {
        String model = readFile(GPU_BASE_PATH + GPU_MODEL);
        if (model != null && !model.trim().isEmpty())
            return model.trim();
        return "Adreno 710";
    }

    public String[] getAvailableGovernors() {
        String governors = readFile(GPU_BASE_PATH + GPU_AVAILABLE_GOVERNORS);
        if (governors != null && !governors.trim().isEmpty()) {
            return governors.trim().split("\\s+");
        }
        return new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"};
    }

    public String[] getAvailableFrequencies() {
        String[] frequencyPaths = {
            GPU_BASE_PATH + GPU_AVAILABLE_FREQUENCIES,
            GPU_BASE_PATH + "/freq_table_mhz",
            GPU_BASE_PATH + "/devfreq/available_frequencies"
        };
        
        for (String path : frequencyPaths) {
            String frequencies = readFile(path);
            if (frequencies != null && !frequencies.trim().isEmpty()) {
                String[] freqArray = frequencies.trim().split("\\s+");
                
                // Biztonság és rendezés
                try {
                    Arrays.sort(freqArray, (a, b) -> {
                        // Fontos: Számként hasonlítjuk össze, nem stringként!
                        return Long.compare(Long.parseLong(a), Long.parseLong(b));
                    });
                    
                    // Szűrés max 940 MHz-re
                    return Arrays.stream(freqArray)
                        .filter(f -> {
                            try { return Long.parseLong(f) <= 940000000L; }
                            catch (Exception e) { return false; }
                        })
                        .toArray(String[]::new);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing frequencies", e);
                }
            }
        }
        return FALLBACK_FREQUENCIES.clone();
    }

    public String getCurrentGovernor() {
        String governor = readFile(GPU_BASE_PATH + GPU_GOVERNOR);
        return governor != null ? governor.trim() : DEFAULT_GOVERNOR;
    }

    public String getCurrentFrequency() {
        String[] frequencyPaths = {
            GPU_BASE_PATH + GPU_CURRENT_FREQ,
            GPU_BASE_PATH + "/devfreq/cur_freq"
        };
        for (String path : frequencyPaths) {
            String freq = readFile(path);
            if (freq != null && !freq.trim().isEmpty()) {
                // Néha a driver 0-t ad vissza deep sleepben, ezt kezeljük le
                if (freq.trim().equals("0")) continue;
                return freq.trim();
            }
        }
        return "0";
    }

    public String getCurrentMinFrequency() {
        String freq = readFile(GPU_BASE_PATH + GPU_MIN_FREQ);
        return freq != null ? freq.trim() : getLowestFrequency();
    }

    public String getCurrentMaxFrequency() {
        String freq = readFile(GPU_BASE_PATH + GPU_MAX_FREQ);
        return freq != null ? freq.trim() : getHighestFrequency();
    }

    private String getLowestFrequency() {
        String[] freqs = getAvailableFrequencies();
        return (freqs != null && freqs.length > 0) ? freqs[0] : FALLBACK_FREQUENCIES[0];
    }

    private String getHighestFrequency() {
        String[] freqs = getAvailableFrequencies();
        return (freqs != null && freqs.length > 0) ? freqs[freqs.length - 1] : FALLBACK_FREQUENCIES[FALLBACK_FREQUENCIES.length - 1];
    }

    public String getGpuBusyPercentage() {
        String busy = readFile(GPU_BASE_PATH + GPU_BUSY_PERCENTAGE);
        if (busy != null && !busy.trim().isEmpty()) {
            // Néha két szám van (pl. "12 45"), az első az érdekes
            String[] parts = busy.trim().split("\\s+");
            return parts[0] + "%";
        }
        return "0%";
    }

    public String getGpuTemperature() {
        String rawTemp = readFile(GPU_BASE_PATH + GPU_TEMPERATURE);
        if (rawTemp != null && !rawTemp.trim().isEmpty()) {
            try {
                int tempMilli = Integer.parseInt(rawTemp.trim());
                return String.format("%.1f", tempMilli / 1000.0);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return "0";
    }

    public String getThermalPowerLevel() {
        String level = readFile(GPU_BASE_PATH + GPU_THERMAL_PWRLEVEL);
        return level != null ? level.trim() : "0";
    }

    public String getResetCount() {
        String value = readFile(GPU_BASE_PATH + GPU_RESET_COUNT);
        return value != null ? value.trim() : "0";
    }

    public String getPreemptCount() {
        String value = readFile(GPU_BASE_PATH + GPU_PREEMPT_COUNT);
        return value != null ? value.trim() : "0";
    }

    public boolean getPreemptStatus() { return getBooleanValue(GPU_BASE_PATH + GPU_PREEMPT); }
    public boolean setPreempt(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_PREEMPT, enabled); }

    public boolean setMaxGpuClk(String clk) {
        try {
            long value = Long.parseLong(clk);
            if (value > 940000000L) clk = "940000000"; 
        } catch (Exception e) {
            clk = "940000000";
        }
        return writeFile(GPU_BASE_PATH + GPU_MAX_GPUCLK, clk);
    }
    
    public String getMaxGpuClk() {
        String clk = readFile(GPU_BASE_PATH + GPU_MAX_GPUCLK);
        // Ha nincs ilyen node, térjen vissza a max frekvenciával
        if (clk == null) return getHighestFrequency();
        return clk.trim();
    }

    public boolean getForceClkOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON); }
    public boolean getForceBusOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON); }
    public boolean getForceRailOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON); }
    public boolean getForceNoNap() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP); }
    public boolean getBusSplit() { return getBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT); }

    private boolean getBooleanValue(String path) {
        String value = readFile(path);
        return value != null && "1".equals(value.trim());
    }

    public boolean setGovernor(String governor) {
        if (governor == null || governor.isEmpty()) return false;
        return writeFile(GPU_BASE_PATH + GPU_GOVERNOR, governor);
    }

    public boolean setFrequencyRange(String minFreq, String maxFreq) {
        if (minFreq == null || maxFreq == null) return false;
        
        try {
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            if (min > max) {
                // Csere, ha fordítva adták meg
                String temp = minFreq; minFreq = maxFreq; maxFreq = temp;
            }
            if (Long.parseLong(maxFreq) > 940000000L) maxFreq = "940000000";
        } catch (NumberFormatException e) {
            return false;
        }

        // Fontos sorrend: Először a Max-ot írjuk, utána a Min-t, 
        // különben ha a jelenlegi min nagyobb mint az új max, hiba lehet.
        boolean s1 = writeFile(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq);
        boolean s2 = writeFile(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq);
        return s1 && s2;
    }

    public boolean setForceClkOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON, enabled); }
    public boolean setForceBusOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON, enabled); }
    public boolean setForceRailOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON, enabled); }
    public boolean setForceNoNap(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP, enabled); }
    public boolean setBusSplit(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT, enabled); }

    private boolean setBooleanValue(String path, boolean enabled) {
        return writeFile(path, enabled ? "1" : "0");
    }

    public boolean triggerCameraBoost() {
        return writeFile(POWERHINT_TRIGGER_PATH, CAMERA_BOOST_HINT_ID);
    }

    public boolean applyTurboPreset() {
        boolean success = true;
        success &= setGovernor(TURBO_GOVERNOR);
        success &= setFrequencyRange("940000000", "940000000"); // Lock max
        success &= setForceClkOn(true);
        success &= setForceBusOn(true);
        success &= setForceRailOn(true);
        success &= setForceNoNap(true);
        success &= setBusSplit(true);
        success &= setPreempt(true);
        return success;
    }

    public boolean resetToDefaults() {
        boolean success = true;
        success &= setGovernor(DEFAULT_GOVERNOR);
        success &= setFrequencyRange(FALLBACK_FREQUENCIES[0], FALLBACK_FREQUENCIES[FALLBACK_FREQUENCIES.length - 1]);
        success &= setForceClkOn(false);
        success &= setForceBusOn(false);
        success &= setForceRailOn(false);
        success &= setForceNoNap(false);
        success &= setBusSplit(false);
        success &= setPreempt(false);
        
        // Reset Max GPUCLK override too
        writeFile(GPU_BASE_PATH + GPU_MAX_GPUCLK, FALLBACK_FREQUENCIES[FALLBACK_FREQUENCIES.length - 1]);
        
        return success;
    }

    private String readFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException e) {
            // Log.w(TAG, "Read failed: " + path); // Reduce log spam
            return null;
        }
    }

    private boolean writeFile(String path, String value) {
        File file = new File(path);
        if (!file.exists() || !file.canWrite()) return false;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Write failed: " + path + " Val: " + value, e);
            return false;
        }
    }
}
