/*
 * Copyright (C) 2025 KamiKaonashi
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

    // GPU paths
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
    private static final String GPU_MAX_GPUCLK = "/max_gpuclk";
    private static final String GPU_MIN_CLOCK_MHZ = "/min_clock_mhz";
    private static final String GPU_MAX_CLOCK_MHZ = "/max_clock_mhz";

    // Fallback frequencies (in Hz) for SM7435 Adreno 730
    private static final String[] FALLBACK_FREQUENCIES = {
        "180000000",  // 180 MHz
        "265000000",  // 265 MHz
        "370000000",  // 370 MHz
        "465000000",  // 465 MHz
        "550000000",  // 550 MHz
        "670000000",  // 670 MHz
        "800000000",  // 800 MHz
        "920000000"   // 920 MHz
    };

    /**
     * Check if GPU management is supported
     * @return true if GPU control files exist
     */
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
            return model != null ? model.trim() : "Adreno 730";
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read GPU model", e);
            return "Adreno 730"; // Default for SM7435
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
        // Fallback governors for Adreno
        return new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"};
    }

    public String[] getAvailableFrequencies()
    {
        // Try multiple paths for frequencies
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
                    // Sort frequencies in ascending order
                    java.util.Arrays.sort(freqArray, (a, b) -> {
                        try
                        {
                            return Long.compare(Long.parseLong(a), Long.parseLong(b));
                        }
                        catch (NumberFormatException e)
                        {
                            return a.compareTo(b);
                        }
                    });
                    return freqArray;
                }
            }
            catch (Exception e)
            {
                Log.w(TAG, "Could not read frequencies from: " + path, e);
            }
        }
        
        // Return fallback frequencies
        Log.d(TAG, "Using fallback frequencies");
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
        // Try multiple paths for current frequency
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
                {
                    return freq.trim();
                }
            }
            catch (Exception e)
            {
                // Continue to next path
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
        return (frequencies != null && frequencies.length > 0) ? 
            frequencies[frequencies.length - 1] : 
            FALLBACK_FREQUENCIES[FALLBACK_FREQUENCIES.length - 1];
    }

    public String getGpuBusyPercentage()
    {
        try
        {
            String busy = readFile(GPU_BASE_PATH + GPU_BUSY_PERCENTAGE);
            if (busy != null && !busy.trim().isEmpty())
            {
                return busy.trim() + "%";
            }
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
                    // Convert millidegrees Celsius to degrees Celsius
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

    public boolean getForceClkOn()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON);
    }

    public boolean getForceBusOn()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON);
    }

    public boolean getForceRailOn()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON);
    }

    public boolean getForceNoNap()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP);
    }

    public boolean getBusSplit()
    {
        return getBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT);
    }

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

        // Validate governor against available ones
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
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "Invalid frequency format", e);
            return false;
        }

        boolean success = true;
        
        try
        {
            // Set max frequency first to avoid conflicts
            if (!writeFile(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq))
            {
                Log.e(TAG, "Failed to set GPU max frequency: " + maxFreq);
                success = false;
            }
            
            if (!writeFile(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq))
            {
                Log.e(TAG, "Failed to set GPU min frequency: " + minFreq);
                success = false;
            }
            
            Log.d(TAG, "GPU frequency range set: " + minFreq + " - " + maxFreq);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU frequency range", e);
            return false;
        }
        
        return success;
    }

    public boolean setForceClkOn(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON, enabled);
    }

    public boolean setForceBusOn(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON, enabled);
    }

    public boolean setForceRailOn(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON, enabled);
    }

    public boolean setForceNoNap(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP, enabled);
    }

    public boolean setBusSplit(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT, enabled);
    }

    private boolean setBooleanValue(String path, boolean enabled)
    {
        try
        {
            boolean success = writeFile(path, enabled ? "1" : "0");
            if (success)
            {
                Log.d(TAG, "Set " + path + " to " + enabled);
            }
            else
            {
                Log.e(TAG, "Failed to set " + path + " to " + enabled);
            }
            return success;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting boolean value for " + path, e);
            return false;
        }
    }

    public boolean resetToDefaults()
    {
        Log.d(TAG, "Resetting GPU to defaults");
        boolean success = true;
        
        // Reset governor
        if (!setGovernor(DEFAULT_GOVERNOR))
        {
            success = false;
        }
        
        // Reset frequency range
        String[] frequencies = getAvailableFrequencies();
        if (frequencies != null && frequencies.length > 0)
        {
            String minFreq = frequencies[0];
            String maxFreq = frequencies[frequencies.length - 1];
            if (!setFrequencyRange(minFreq, maxFreq))
            {
                success = false;
            }
        }
        
        // Reset power settings
        if (!setForceClkOn(false)) success = false;
        if (!setForceBusOn(false)) success = false;
        if (!setForceRailOn(false)) success = false;
        if (!setForceNoNap(false)) success = false;
        if (!setBusSplit(false)) success = false;
        
        Log.d(TAG, "GPU reset to defaults " + (success ? "successful" : "partially failed"));
        return success;
    }

    /**
     * Get debug information for troubleshooting
     * @return Debug information string
     */
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
        sb.append("Temperature: ").append(getGpuTemperature()).append("°C\n");
        sb.append("Busy: ").append(getGpuBusyPercentage()).append("\n");
        sb.append("Thermal Level: ").append(getThermalPowerLevel()).append("\n\n");
        
        sb.append("Power Settings:\n");
        sb.append("Force CLK On: ").append(getForceClkOn()).append("\n");
        sb.append("Force BUS On: ").append(getForceBusOn()).append("\n");
        sb.append("Force Rail On: ").append(getForceRailOn()).append("\n");
        sb.append("Force No Nap: ").append(getForceNoNap()).append("\n");
        sb.append("Bus Split: ").append(getBusSplit()).append("\n\n");
        
        sb.append("Available Governors:\n");
        String[] governors = getAvailableGovernors();
        for (String governor : governors)
        {
            sb.append("  - ").append(governor).append("\n");
        }
        sb.append("\n");
        
        sb.append("Available Frequencies:\n");
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

    /**
     * Read content from a file
     * @param path File path to read
     * @return File content as string, null if error
     */
    private String readFile(String path) throws IOException
    {
        File file = new File(path);
        if (!file.exists() || !file.canRead())
        {
            throw new IOException("Cannot read file: " + path);
        }

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
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
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
    private boolean writeFile(String path, String value) throws IOException
    {
        File file = new File(path);
        if (!file.exists() || !file.canWrite())
        {
            throw new IOException("Cannot write to file: " + path);
        }

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
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {
                    Log.w(TAG, "Error closing writer", e);
                }
            }
        }
    }
}
