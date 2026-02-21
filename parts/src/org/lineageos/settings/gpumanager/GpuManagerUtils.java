/*
 * Copyright (C) 2025 KamiKaonashi, Copilot
 * Optimized for Garnet (Snapdragon 7s Gen 2, SM7435)
 * Battery-optimized version by bezke
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

public class GpuManagerUtils
{
    private static final String TAG = "GpuManagerUtils";
    private static final String GPU_BASE_PATH = "/sys/class/kgsl/kgsl-3d0";
    private static final String DEVFREQ_PATH = GPU_BASE_PATH + "/devfreq";
    private static final String DEFAULT_GOVERNOR = "msm-adreno-tz";

    // Adreno 710 (SM7435) optimized frequencies
    // Base: 295 MHz, Max: 940 MHz
    private static final String[] FALLBACK_FREQUENCIES = {
        "295000000", "345000000", "500000000", "600000000", "650000000",
        "734000000", "816000000", "875000000", "940000000"
    };

    // Sysfs paths
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
    private static final String GPU_IDLE_TIMER = "/idle_timer";

    // Performance limits for SM7435
    private static final long MAX_GPU_FREQ = 940000000L; // 940 MHz max
    private static final long DEFAULT_MIN_GPU_FREQ = 295000000L; // 295 MHz default idle

    // Camera boost is handled by the PowerHAL via powerhint.json CAMERA_LAUNCH hint.
    // Manual triggering from app is not needed on garnet.

    // Cache for frequently accessed values (optimized)
    private String[] mCachedFrequencies = null;
    private String[] mCachedGovernors = null;
    private long mLastCacheTime = 0;
    private static final long CACHE_VALIDITY_MS = 5000; // 5 seconds

    // Idle timer settings (milliseconds)
    private static final String IDLE_TIMER_DEFAULT = "64"; // Default balanced
    private static final String IDLE_TIMER_AGGRESSIVE = "40"; // Quick power down for battery
    private static final String IDLE_TIMER_PERFORMANCE = "100"; // Allow longer active time

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
        long currentTime = System.currentTimeMillis();
        if (mCachedGovernors != null && (currentTime - mLastCacheTime) < CACHE_VALIDITY_MS) {
            return mCachedGovernors;
        }

        try
        {
            String governors = readFile(GPU_BASE_PATH + GPU_AVAILABLE_GOVERNORS);
            if (governors != null && !governors.trim().isEmpty())
            {
                mCachedGovernors = governors.trim().split("\\s+");
                mLastCacheTime = currentTime;
                return mCachedGovernors;
            }
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read available governors", e);
        }
        mCachedGovernors = new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"};
        return mCachedGovernors;
    }

    public String[] getAvailableFrequencies()
    {
        long currentTime = System.currentTimeMillis();
        if (mCachedFrequencies != null && (currentTime - mLastCacheTime) < CACHE_VALIDITY_MS) {
            return mCachedFrequencies;
        }

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
                    
                    // Optimize: filter and sort in one pass
                    java.util.List<String> validFreqs = new java.util.ArrayList<>();
                    for (String f : freqArray) {
                        try {
                            long freq = Long.parseLong(f);
                            if (freq <= MAX_GPU_FREQ) {
                                validFreqs.add(f);
                            }
                        } catch (Exception e) {
                            // Skip invalid frequencies
                        }
                    }
                    
                    // Sort numerically
                    java.util.Collections.sort(validFreqs, (a, b) -> {
                        try {
                            return Long.compare(Long.parseLong(a), Long.parseLong(b));
                        } catch (NumberFormatException e) {
                            return a.compareTo(b);
                        }
                    });
                    
                    mCachedFrequencies = validFreqs.toArray(new String[0]);
                    mLastCacheTime = currentTime;
                    return mCachedFrequencies;
                }
            }
            catch (Exception e)
            {
                Log.w(TAG, "Could not read frequencies from: " + path, e);
            }
        }
        
        mCachedFrequencies = FALLBACK_FREQUENCIES.clone();
        return mCachedFrequencies;
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
                // Try next path
            }
        }
        return "0";
    }

    public String getCurrentMinFrequency()
    {
        try
        {
            String freq = readFile(GPU_BASE_PATH + GPU_MIN_FREQ);
            return freq != null ? freq.trim() : String.valueOf(DEFAULT_MIN_GPU_FREQ);
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read min frequency", e);
            return String.valueOf(DEFAULT_MIN_GPU_FREQ);
        }
    }

    public String getCurrentMaxFrequency()
    {
        try
        {
            String freq = readFile(GPU_BASE_PATH + GPU_MAX_FREQ);
            return freq != null ? freq.trim() : String.valueOf(MAX_GPU_FREQ);
        }
        catch (Exception e)
        {
            Log.w(TAG, "Could not read max frequency", e);
            return String.valueOf(MAX_GPU_FREQ);
        }
    }

    public String getMaxGpuClk()
    {
        try
        {
            String freq = readFile(GPU_BASE_PATH + GPU_MAX_GPUCLK);
            return freq != null ? freq.trim() : String.valueOf(MAX_GPU_FREQ);
        }
        catch (Exception e)
        {
            return String.valueOf(MAX_GPU_FREQ);
        }
    }

    public String getGpuBusyPercentage()
    {
        try
        {
            String busy = readFile(GPU_BASE_PATH + GPU_BUSY_PERCENTAGE);
            return busy != null ? busy.trim() + "%" : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public String getGpuTemperature()
    {
        try
        {
            String temp = readFile(GPU_BASE_PATH + GPU_TEMPERATURE);
            return temp != null ? temp.trim() : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public String getThermalPowerLevel()
    {
        try
        {
            String level = readFile(GPU_BASE_PATH + GPU_THERMAL_PWRLEVEL);
            return level != null ? level.trim() : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public boolean getForceClkOn()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_FORCE_CLK_ON);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public boolean getForceBusOn()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_FORCE_BUS_ON);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public boolean getForceRailOn()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_FORCE_RAIL_ON);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public boolean getForceNoNap()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_FORCE_NO_NAP);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public boolean getBusSplit()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_BUS_SPLIT);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public String getResetCount()
    {
        try
        {
            String count = readFile(GPU_BASE_PATH + GPU_RESET_COUNT);
            return count != null ? count.trim() : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public String getPreemptCount()
    {
        try
        {
            String count = readFile(GPU_BASE_PATH + GPU_PREEMPT_COUNT);
            return count != null ? count.trim() : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public boolean getPreemptStatus()
    {
        try
        {
            String value = readFile(GPU_BASE_PATH + GPU_PREEMPT);
            return value != null && value.trim().equals("1");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public String getIdleTimer()
    {
        try
        {
            String timer = readFile(GPU_BASE_PATH + GPU_IDLE_TIMER);
            return timer != null ? timer.trim() + " ms" : "N/A";
        }
        catch (Exception e)
        {
            return "N/A";
        }
    }

    public boolean setGovernor(String governor)
    {
        try
        {
            boolean success = writeFile(GPU_BASE_PATH + GPU_GOVERNOR, governor);
            if (success) {
                Log.d(TAG, "GPU governor set to: " + governor);
                invalidateCache();
            } else {
                Log.e(TAG, "Failed to set GPU governor to: " + governor);
            }
            return success;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU governor", e);
            return false;
        }
    }

    public boolean setFrequencyRange(String minFreq, String maxFreq)
    {
        boolean success = true;
        try
        {
            // Validate frequencies
            long min = Long.parseLong(minFreq);
            long max = Long.parseLong(maxFreq);
            
            if (min > max) {
                Log.e(TAG, "Min frequency cannot be greater than max frequency");
                return false;
            }
            
            if (max > MAX_GPU_FREQ) {
                Log.w(TAG, "Max frequency exceeds hardware limit, capping to " + MAX_GPU_FREQ);
                maxFreq = String.valueOf(MAX_GPU_FREQ);
            }
            
            // Set max first to avoid conflicts
            if (!writeFile(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq)) {
                Log.e(TAG, "Failed to set max GPU frequency");
                success = false;
            }
            
            // Then set min
            if (!writeFile(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq)) {
                Log.e(TAG, "Failed to set min GPU frequency");
                success = false;
            }
            
            if (success) {
                Log.d(TAG, "GPU frequency range set: " + minFreq + " - " + maxFreq);
            }
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "Invalid frequency format", e);
            return false;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU frequency range", e);
            return false;
        }
        return success;
    }

    public boolean setPreempt(boolean enabled)
    {
        return setBooleanValue(GPU_BASE_PATH + GPU_PREEMPT, enabled);
    }

    public boolean setForceClkOn(boolean enabled) { 
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_CLK_ON, enabled); 
    }
    
    public boolean setForceBusOn(boolean enabled) { 
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_BUS_ON, enabled); 
    }
    
    public boolean setForceRailOn(boolean enabled) { 
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_RAIL_ON, enabled); 
    }
    
    public boolean setForceNoNap(boolean enabled) { 
        return setBooleanValue(GPU_BASE_PATH + GPU_FORCE_NO_NAP, enabled); 
    }
    
    public boolean setBusSplit(boolean enabled) { 
        return setBooleanValue(GPU_BASE_PATH + GPU_BUS_SPLIT, enabled); 
    }

    public boolean setMaxGpuClk(String value)
    {
        try
        {
            boolean success = writeFile(GPU_BASE_PATH + GPU_MAX_GPUCLK, value);
            if (success)
                Log.d(TAG, "Set GPU max GPUCLK to " + value);
            else
                Log.e(TAG, "Failed to set GPU max GPUCLK");
            return success;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU max GPUCLK", e);
            return false;
        }
    }

    public boolean setIdleTimer(String value)
    {
        try
        {
            boolean success = writeFile(GPU_BASE_PATH + GPU_IDLE_TIMER, value);
            if (success)
                Log.d(TAG, "Set GPU idle timer to " + value + " ms");
            else
                Log.e(TAG, "Failed to set GPU idle timer");
            return success;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error setting GPU idle timer", e);
            return false;
        }
    }

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

    /**
     * Camera boost on garnet is handled automatically by the PowerHAL via
     * powerhint.json CAMERA_LAUNCH / CAMERA_STREAMING_* hints.
     * Manual triggering from the app is not required and the /proc/powerhint
     * path does not exist on this kernel.
     */
    public boolean triggerCameraBoost()
    {
        Log.d(TAG, "Camera boost is managed by PowerHAL (CAMERA_LAUNCH hint), no manual trigger needed");
        return true;
    }

    /**
     * TURBO PROFILE: max freq ceiling, high min floor, optimized power options.
     * Governor intentionally NOT changed — msm-adreno-tz must stay active so the
     * PowerHAL EXPENSIVE_RENDERING and CAMERA_* hints continue to function.
     */
    public boolean applyTurboPreset()
    {
        boolean success = true;
        String maxFreq = String.valueOf(MAX_GPU_FREQ);
        
        // Use mid-high frequency as min for better responsiveness while saving battery
        String minFreq = "650000000"; // 650 MHz instead of max
        
        if (!setFrequencyRange(minFreq, maxFreq)) success = false;
        
        // Only enable clock forcing, not bus/rail for better battery life
        if (!setForceClkOn(true)) success = false;
        if (!setForceBusOn(false)) success = false; // Battery optimization
        if (!setForceRailOn(false)) success = false; // Battery optimization
        if (!setForceNoNap(false)) success = false;
        if (!setBusSplit(true)) success = false;
        if (!setPreempt(true)) success = false;
        
        // Set performance idle timer
        if (!setIdleTimer(IDLE_TIMER_PERFORMANCE)) success = false;
        
        Log.d(TAG, "Turbo preset enabled (governor unchanged, battery-optimized): " + (success ? "OK" : "FAILED"));
        return success;
    }

    /**
     * BALANCED PROFILE: default governor with optimized idle behavior
     * Good balance between performance and battery
     */
    public boolean applyBalancedPreset()
    {
        Log.d(TAG, "Applying balanced GPU preset");
        boolean success = true;
        
        if (!setGovernor(DEFAULT_GOVERNOR)) success = false;
        
        String minFreq = String.valueOf(DEFAULT_MIN_GPU_FREQ);
        String maxFreq = String.valueOf(MAX_GPU_FREQ);
        
        if (!setFrequencyRange(minFreq, maxFreq)) success = false;
        
        // All force options off for balanced mode
        if (!setForceClkOn(false)) success = false;
        if (!setForceBusOn(false)) success = false;
        if (!setForceRailOn(false)) success = false;
        if (!setForceNoNap(false)) success = false;
        if (!setBusSplit(false)) success = false;
        if (!setPreempt(false)) success = false;
        
        // Balanced idle timer
        if (!setIdleTimer(IDLE_TIMER_DEFAULT)) success = false;
        
        Log.d(TAG, "Balanced preset " + (success ? "successful" : "partially failed"));
        return success;
    }

    /**
     * BATTERY SAVER PROFILE: optimized for maximum battery life
     * Lower frequencies and aggressive power management
     */
    public boolean applyBatterySaverPreset()
    {
        Log.d(TAG, "Applying battery saver GPU preset");
        boolean success = true;
        
        if (!setGovernor("powersave")) success = false;
        
        String minFreq = String.valueOf(DEFAULT_MIN_GPU_FREQ);
        // Cap max frequency to 650 MHz for battery saving
        String maxFreq = "650000000";
        
        if (!setFrequencyRange(minFreq, maxFreq)) success = false;
        
        // All force options off
        if (!setForceClkOn(false)) success = false;
        if (!setForceBusOn(false)) success = false;
        if (!setForceRailOn(false)) success = false;
        if (!setForceNoNap(false)) success = false;
        if (!setBusSplit(false)) success = false;
        if (!setPreempt(false)) success = false;
        
        // Aggressive idle timer for quick power down
        if (!setIdleTimer(IDLE_TIMER_AGGRESSIVE)) success = false;
        
        Log.d(TAG, "Battery saver preset " + (success ? "successful" : "partially failed"));
        return success;
    }

    /**
     * Reset GPU settings to factory defaults
     * Adreno 710 defaults: 295MHz - 940MHz
     */
    public boolean resetToDefaults()
    {
        Log.d(TAG, "Resetting GPU to defaults");
        return applyBalancedPreset();
    }

    public String getDebugInfo()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("GPU Manager Debug Info:\n");
        sb.append("======================\n");
        sb.append("Supported: ").append(isGpuManagerSupported()).append("\n");
        sb.append("GPU Model: ").append(getGpuModel()).append("\n");
        sb.append("Device: Xiaomi Redmi Note 13 Pro 5G (Garnet) - SM7435\n\n");
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
        sb.append("Preempt Count: ").append(getPreemptCount()).append("\n");
        sb.append("Idle Timer: ").append(getIdleTimer()).append("\n\n");
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

    /**
     * FIX: readFile() — removed canRead() check as a strict gate.
     * canRead() uses POSIX stat() which does not reflect SELinux MAC policy.
     * Silently returns null on failure so callers can use fallback values.
     */
    private String readFile(String path)
    {
        try
        {
            File file = new File(path);
            if (!file.exists())
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

    /**
     * FIX: Removed !file.canWrite() check from writeFile().
     *
     * File.canWrite() queries POSIX DAC permission bits via stat() and does NOT
     * consult SELinux / MAC policies. On Android sysfs nodes this means canWrite()
     * can return false even when the platform_app SELinux domain is allowed to
     * write — blocking the attempt before any I/O is attempted.
     *
     * Removing the check and letting FileWriter throw on real permission failures
     * is the correct approach, consistent with PerformanceUtils and KernelManagerUtils.
     */
    private boolean writeFile(String path, String value)
    {
        try
        {
            File file = new File(path);
            if (!file.exists())
            {
                Log.w(TAG, "writeFile: file not found: " + path);
                return false;
            }
            // canWrite() intentionally NOT checked — see javadoc above.
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
            Log.e(TAG, "writeFile error (SELinux or read-only?): " + path, e);
            return false;
        }
    }

    /**
     * Invalidate cache to force refresh from sysfs
     * Call this when settings are changed externally
     */
    public void invalidateCache() {
        mCachedFrequencies = null;
        mCachedGovernors = null;
        mLastCacheTime = 0;
        Log.d(TAG, "Cache invalidated");
    }
}
