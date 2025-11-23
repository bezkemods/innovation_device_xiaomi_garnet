/*
 * Copyright (C) 2025 KamiKaonashi, Copilot
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.gpumanager;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class GpuManagerUtils
{
    private static final String TAG = "GpuManagerUtils";
    private static final String GPU_BASE_PATH = "/sys/class/kgsl/kgsl-3d0";
    private static final String DEVFREQ_PATH = GPU_BASE_PATH + "/devfreq";
    private static final String DEFAULT_GOVERNOR = "msm-adreno-tz";
    private static final String TURBO_GOVERNOR = "performance";

    // Adreno 710 gyári frekvenciák, max 940 MHz
    private static final String[] FALLBACK_FREQUENCIES = {
        "180000000", "265000000", "370000000", "465000000", "550000000",
        "670000000", "800000000", "940000000" // Max 940 MHz
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

    // Kamera/video boost: Powerhint trigger (gyári XML alapján)
    private static final String POWERHINT_TRIGGER_PATH = "/proc/powerhint";
    private static final String CAMERA_BOOST_HINT_ID = "0x00001340";

    public boolean isGpuManagerSupported()
    {
        try
        {
            File gpuBase = new File(GPU_BASE_PATH);
            File devfreqPath = new File(DEVFREQ_PATH);
            return gpuBase.exists() && devfreqPath.exists();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error checking GPU manager support", e);
            return false;
        }
    }

    public String getGpuModel()
    {
        try
        {
            String model = readFile(GPU_BASE_PATH + GPU_MODEL);
            if (model != null && !model.trim().isEmpty())
                return model.trim();
            // Adreno 710 fallback
            return "Adreno 710";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU model", e);
            return "Adreno 710";
        }
    }

    public String[] getAvailableGovernors()
    {
        try
        {
            String governors = readFile(GPU_BASE_PATH + GPU_AVAILABLE_GOVERNORS);
            if (governors != null && !governors.trim().isEmpty())
            {
                return governors.trim().split("\\s+");
            }
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read available governors", e);
        }
        return new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"};
    }

    public String[] getAvailableFrequencies()
    {
        String[] frequencyPaths = {
            GPU_BASE_PATH + GPU_AVAILABLE_FREQUENCIES,
            GPU_BASE_PATH + "/freq_table_mhz",
            GPU_BASE_PATH + "/devfreq/available_frequencies"
        };
        for (String path : frequencyPaths)
        {
            try
            {
                String frequencies = readFile(path);
                if (frequencies != null && !frequencies.trim().isEmpty())
                {
                    String[] freqArray = frequencies.trim().split("\\s+");
                    // Biztonság: max 940 MHz legyen a legnagyobb
                    freqArray = java.util.Arrays.stream(freqArray)
                        .filter(f -> {
                            try { return Long.parseLong(f) <= 940000000; }
                            catch (Exception e) { return false; }
                        })
                        .toArray(String[]::new);
                    java.util.Arrays.sort(freqArray, (a, b) -> {
                        try { return Long.compare(Long.parseLong(a), Long.parseLong(b)); }
                        catch (NumberFormatException e) { return a.compareTo(b); }
                    });
                    return freqArray;
                }
            }
            catch (Exception e)
            {
                Log.w(TAG, "Could not read frequencies from: " + path, e);
            }
        }
        return FALLBACK_FREQUENCIES.clone();
    }

    public String getCurrentGovernor()
    {
        try
        {
            String governor = readFile(GPU_BASE_PATH + GPU_GOVERNOR);
            return governor != null ? governor.trim() : DEFAULT_GOVERNOR;
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read current governor", e);
            return DEFAULT_GOVERNOR;
        }
    }

    public String getCurrentFrequency()
    {
        String[] frequencyPaths = {
            GPU_BASE_PATH + GPU_CURRENT_FREQ,
            GPU_BASE_PATH + "/devfreq/cur_freq",
            GPU_BASE_PATH + "/gpuclk"
        };
        for (String path : frequencyPaths)
        {
            try
            {
                String freq = readFile(path);
                if (freq != null && !freq.trim().isEmpty() && !freq.equals("0"))
                    return freq.trim();
            }
            catch (Exception e)
            {
                // Next path
            }
        }
        Log.w(TAG, "Could not read current frequency from any path");
        return "0";
    }

    public String getCurrentMinFrequency()
    {
        try
        {
            String freq = readFile(GPU_BASE_PATH + GPU_MIN_FREQ);
            return freq != null ? freq.trim() : getLowestFrequency();
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read min frequency", e);
            return getLowestFrequency();
        }
    }

    public String getCurrentMaxFrequency()
    {
        try
        {
            String freq = readFile(GPU_BASE_PATH + GPU_MAX_FREQ);
            return freq != null ? freq.trim() : getHighestFrequency();
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read max frequency", e);
            return getHighestFrequency();
        }
    }

    private String getLowestFrequency()
    {
        String[] frequencies = getAvailableFrequencies();
        return (frequencies != null && frequencies.length > 0) ? frequencies[0] : FALLBACK_FREQUENCIES[0];
    }

    private String getHighestFrequency()
    {
        String[] frequencies = getAvailableFrequencies();
        return (frequencies != null && frequencies.length > 0) ? frequencies[frequencies.length - 1] : FALLBACK_FREQUENCIES[FALLBACK_FREQUENCIES.length - 1];
    }

    public String getGpuBusyPercentage()
    {
        try
        {
            String busy = readFile(GPU_BASE_PATH + GPU_BUSY_PERCENTAGE);
            if (busy != null && !busy.trim().isEmpty())
                return busy.trim() + "%";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU busy percentage", e);
        }
        return "0%";
    }

    public String getGpuTemperature()
    {
        try
        {
            String rawTemp = readFile(GPU_BASE_PATH + GPU_TEMPERATURE);
            if (rawTemp != null && !rawTemp.trim().isEmpty())
            {
                try
                {
                    int tempMilliCelsius = Integer.parseInt(rawTemp.trim());
                    double tempCelsius = tempMilliCelsius / 1000.0;
                    return String.format("%.1f", tempCelsius);
                }
                catch (NumberFormatException e)
                {
                    Log.w(TAG, "Invalid temperature format: " + rawTemp, e);
                }
            }
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU temperature", e);
        }
        return "0";
    }

    public String getThermalPowerLevel()
    {
        try
        {
            String level = readFile(GPU_BASE_PATH + GPU_THERMAL_PWRLEVEL);
            return level != null ? level.trim() : "0";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read thermal power level", e);
            return "0";
        }
    }

    public String getResetCount()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_RESET_COUNT);
            return value != null ? value.trim() : "0";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU reset count", e);
            return "0";
        }
    }

    public String getPreemptCount()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_PREEMPT_COUNT);
            return value != null ? value.trim() : "0";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU preempt count", e);
            return "0";
        }
    }

    public boolean getPreemptStatus()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_PREEMPT);
    }
    public boolean setPreempt(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_PREEMPT, enabled);
    }

    public boolean setMaxGpuClk(String clk)
    {
        try {
            long value = Long.parseLong(clk);
            if (value > 940000000) clk = "940000000"; // Biztonság: max 940 MHz
        } catch (Exception e) {
            clk = "940000000";
        }
        return writeFile(GPU_BASE_PATH + GPU_MAX_GPUCLK, clk);
    }
    public String getMaxGpuClk()
    {
        String clk = readFile(GPU_BASE_PATH + GPU_MAX_GPUCLK);
        try {
            if (clk != null && Long.parseLong(clk) > 940000000) return "940000000";
        } catch (Exception e) { }
        return clk;
    }

    public boolean getForceClkOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON); }
    public boolean getForceBusOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON); }
    public boolean getForceRailOn() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON); }
    public boolean getForceNoNap() { return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP); }
    public boolean getBusSplit() { return getBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT); }

    private boolean getBooleanValue(String path)
    {
        try
        {
            String value = readFile(path);
            return value != null && "1".equals(value.trim());
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read boolean value from: " + path, e);
            return false;
        }
    }

    public boolean setGovernor(String governor)
    {
        if (governor == null || governor.isEmpty())
        {
            Log.e(TAG, "Invalid governor: " + governor);
            return false;
        }
        String[] availableGovernors = getAvailableGovernors();
        boolean isValid = false;
        for (String availableGovernor : availableGovernors)
        {
            if (governor.equals(availableGovernor))
            {
                isValid = true;
                break;
            }
        }
        if (!isValid)
        {
            Log.e(TAG, "Governor not available: " + governor);
            return false;
        }
        try
        {
            return writeFile(GPU_BASE_PATH + GPU_GOVERNOR, governor);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU governor", e);
            return false;
        }
    }

    public boolean setFrequencyRange(String minFreq, String maxFreq)
    {
        if (minFreq == null || maxFreq == null || minFreq.isEmpty() || maxFreq.isEmpty())
        {
            Log.e(TAG, "Invalid frequency values: min=" + minFreq + ", max=" + maxFreq);
            return false;
        }
        try
        {
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            if (min > max)
            {
                Log.e(TAG, "Min frequency (" + min + ") is higher than max frequency (" + max + ")");
                return false;
            }
            if (min > 940000000) minFreq = "940000000";
            if (max > 940000000) maxFreq = "940000000";
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "Invalid frequency format", e);
            minFreq = "180000000";
            maxFreq = "940000000";
        }
        boolean success = true;
        try
        {
            if (!writeFile(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq)) success = false;
            if (!writeFile(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq)) success = false;
            Log.d(TAG, "GPU frequency range set: " + minFreq + " - " + maxFreq);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU frequency range", e);
            return false;
        }
        return success;
    }

    public boolean setForceClkOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON, enabled); }
    public boolean setForceBusOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON, enabled); }
    public boolean setForceRailOn(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON, enabled); }
    public boolean setForceNoNap(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP, enabled); }
    public boolean setBusSplit(boolean enabled) { return setBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT, enabled); }

    private boolean setBooleanValue(String path, boolean enabled)
    {
        try
        {
            boolean success = writeFile(path, enabled ? "1" : "0");
            if (success)
                Log.d(TAG, "Set " + path + " to " + enabled);
            else
                Log.e(TAG, "Failed to set " + path + " to " + enabled);
            return success;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting boolean value for " + path, e);
            return false;
        }
    }

    public boolean triggerCameraBoost()
    {
        try
        {
            return writeFile(POWERHINT_TRIGGER_PATH, CAMERA_BOOST_HINT_ID);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Camera boost trigger failed", e);
            return false;
        }
    }

    // TURBO PROFILE: performance governor, max freq, minden ON, preempt ON (max 940 MHz)
    public boolean applyTurboPreset()
    {
        boolean success = true;
        String maxFreq = "940000000";
        String minFreq = "940000000";
        if (!setGovernor(TURBO_GOVERNOR)) success = false;
        if (!setFrequencyRange(minFreq, maxFreq)) success = false;
        if (!setForceClkOn(true)) success = false;
        if (!setForceBusOn(true)) success = false;
        if (!setForceRailOn(true)) success = false;
        if (!setForceNoNap(true)) success = false;
        if (!setBusSplit(true)) success = false;
        if (!setPreempt(true)) success = false;
        Log.d(TAG, "Turbo preset enabled: " + (success ? "OK" : "FAILED"));
        return success;
    }

    public boolean resetToDefaults()
    {
        Log.d(TAG, "Resetting GPU to defaults");
        boolean success = true;
        if (!setGovernor(DEFAULT_GOVERNOR)) success = false;
        String minFreq = "180000000";
        String maxFreq = "940000000";
        if (!setFrequencyRange(minFreq, maxFreq)) success = false;
        if (!setForceClkOn(false)) success = false;
        if (!setForceBusOn(false)) success = false;
        if (!setForceRailOn(false)) success = false;
        if (!setForceNoNap(false)) success = false;
        if (!setBusSplit(false)) success = false;
        if (!setPreempt(false)) success = false;
        Log.d(TAG, "GPU reset to defaults " + (success ? "successful" : "partially failed"));
        return success;
    }

    public String getDebugInfo()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("GPU Manager Debug Info:\n");
        sb.append("======================\n");
        sb.append("Supported: ").append(isGpuManagerSupported()).append("\n");
        sb.append("GPU Model: ").append(getGpuModel()).append("\n\n");
        sb.append("Current Settings:\n");
        sb.append("Governor: ").append(getCurrentGovernor()).append("\n");
        sb.append("Current Freq: ").append(getCurrentFrequency()).append(" Hz\n");
        sb.append("Min Freq: ").append(getCurrentMinFrequency()).append(" Hz\n");
        sb.append("Max Freq: ").append(getCurrentMaxFrequency()).append(" Hz\n");
        sb.append("Max GPUCLK: ").append(getMaxGpuClk()).append(" Hz\n");
        sb.append("Temperature: ").append(getGpuTemperature()).append("°C\n");
        sb.append("Busy: ").append(getGpuBusyPercentage()).append("\n");
        sb.append("Thermal Level: ").append(getThermalPowerLevel()).append("\n");
        sb.append("Reset Count: ").append(getResetCount()).append("\n");
        sb.append("Preempt Count: ").append(getPreemptCount()).append("\n\n");
        sb.append("Power Settings:\n");
        sb.append("Force CLK On: ").append(getForceClkOn()).append("\n");
        sb.append("Force BUS On: ").append(getForceBusOn()).append("\n");
        sb.append("Force Rail On: ").append(getForceRailOn()).append("\n");
        sb.append("Force No Nap: ").append(getForceNoNap()).append("\n");
        sb.append("Bus Split: ").append(getBusSplit()).append("\n");
        sb.append("Preemption: ").append(getPreemptStatus()).append("\n\n");
        sb.append("Available Governors:\n");
        String[] governors = getAvailableGovernors();
        for (String governor : governors)
            sb.append("  - ").append(governor).append("\n");
        sb.append("\nAvailable Frequencies:\n");
        String[] frequencies = getAvailableFrequencies();
        for (String freq : frequencies)
        {
            try
            {
                long freqHz = Long.parseLong(freq);
                int freqMhz = (int)(freqHz / 1000000);
                sb.append("  - ").append(freqMhz).append(" MHz (").append(freq).append(" Hz)\n");
            }
            catch (NumberFormatException e)
            {
                sb.append("  - ").append(freq).append(" Hz\n");
            }
        }
        return sb.toString();
    }

    private String readFile(String path)
    {
        try
        {
            File file = new File(path);
            if (!file.exists() || !file.canRead())
                return null;
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                return line;
            }
            finally
            {
                if (reader != null)
                    try { reader.close(); } catch (IOException e) { Log.w(TAG, "Error closing reader", e); }
            }
        }
        catch (Exception e)
        {
            Log.w(TAG, "readFile error: " + path, e);
            return null;
        }
    }

    private boolean writeFile(String path, String value)
    {
        try
        {
            File file = new File(path);
            if (!file.exists() || !file.canWrite())
                throw new IOException("Cannot write to file: " + path);
            FileWriter writer = null;
            try
            {
                writer = new FileWriter(file);
                writer.write(value);
                writer.flush();
                return true;
            }
            finally
            {
                if (writer != null)
                    try { writer.close(); } catch (IOException e) { Log.w(TAG, "Error closing writer", e); }
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "writeFile error: " + path, e);
            return false;
        }
    }
}
