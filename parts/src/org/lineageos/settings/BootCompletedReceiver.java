/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import org.lineageos.settings.gpumanager.GpuManagerUtils;
import org.lineageos.settings.corecontrol.CoreControlUtils;
import org.lineageos.settings.logcatviewer.LogcatBackgroundService;
import org.lineageos.settings.adblocker.AdBlockerUtils;
import org.lineageos.settings.performance.PerformanceUtils;
import org.lineageos.settings.videoenhancer.VideoEnhancerUtils;
import org.lineageos.settings.utils.FileUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "XiaomiParts";

    // Governor/freq paths: SM7435 mapping
    private static final String POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
    private static final String POLICY4_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";
    private static final String POLICY6_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy6/scaling_governor";
    private static final String DEFAULT_GOVERNOR = "walt";

    // Preference keys
    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFFICIENCY_MIN_FREQ = "efficiency_min_freq";
    private static final String KEY_EFFICIENCY_MAX_FREQ = "efficiency_max_freq";
    private static final String KEY_PERFORMANCE_MIN_FREQ = "performance_min_freq";
    private static final String KEY_PERFORMANCE_MAX_FREQ = "performance_max_freq";

    private static final String KEY_GPU_GOVERNOR = "gpu_governor";
    private static final String KEY_GPU_MIN_FREQ = "gpu_min_freq";
    private static final String KEY_GPU_MAX_FREQ = "gpu_max_freq";
    private static final String KEY_GPU_FORCE_CLK_ON = "gpu_force_clk_on";
    private static final String KEY_GPU_FORCE_BUS_ON = "gpu_force_bus_on";
    private static final String KEY_GPU_FORCE_RAIL_ON = "gpu_force_rail_on";
    private static final String KEY_GPU_FORCE_NO_NAP = "gpu_force_no_nap";
    private static final String KEY_GPU_BUS_SPLIT = "gpu_bus_split";

    private static final String KEY_PERFORMANCE_PROFILE = "performance_profile";
    private static final String KEY_CORE_CONTROL_ENABLED = "core_control_enabled";
    private static final String KEY_AUTO_START_LOGCAT = "auto_start_logcat";
    private static final String KEY_ADBLOCKER_ENABLED = "adblocker_enabled";
    private static final String KEY_SMOOTH_MOTION_ENABLED = "smooth_motion_enabled";
    private static final String KEY_OPTIMIZE_REFRESH_ENABLED = "optimize_refresh_enabled";
    private static final String KEY_SKIAGL_RENDERER_ENABLED = "skiagl_renderer_enabled";
    private static final String KEY_SKIAVK_RENDERER_ENABLED = "skiavk_renderer_enabled";
    private static final String KEY_FORCE_VULKAN_ENABLED = "force_vulkan_enabled";
    private static final String KEY_PURGEABLE_ASSETS_ENABLED = "purgeable_assets_enabled";
    private static final String KEY_GFX_ACCEL_ENABLED = "gfx_accel_enabled";
    private static final String KEY_ADPF_CPU_HINT_ENABLED = "adpf_cpu_hint_enabled";
    private static final String KEY_CPU_TILE_ENABLED = "cpu_tile_enabled";

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "Context or intent is null");
            return;
        }

        String action = intent.getAction();
        if (DEBUG) {
            Log.d(TAG, "Received intent: " + action);
        }

        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            handleLockedBootCompleted(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            handleBootCompleted(context);
        }
    }

    private void handleLockedBootCompleted(Context context) {
        initializeBackgroundThread();
        mBackgroundHandler.post(() -> {
            try {
                startServices(context);
                Log.d(TAG, "Starting RefreshService");
                org.lineageos.settings.refreshrate.RefreshUtils.startService(context);
                ensureDefaultGovernorIfNeeded(context);
                restorePerformanceProfile(context);
                restoreKernelSettings(context);
                restoreGpuSettings(context);
                restoreCoreControlSettings(context);
                restoreLogcatService(context);
                restoreAdBlockerSettings(context);
                restoreVideoEnhancerSettings(context);
                initializeCpuTileService(context);
                Log.i(TAG, "Locked boot completed initialization finished");
            } catch (Exception e) {
                Log.e(TAG, "Error during locked boot initialization", e);
            }
        });
    }

    private void handleBootCompleted(Context context) {
        if (mBackgroundHandler == null) {
            initializeBackgroundThread();
        }
        mBackgroundHandler.post(() -> {
            try {
                Log.i(TAG, "Boot completed initialization finished");
            } catch (Exception e) {
                Log.e(TAG, "Error during boot completed initialization", e);
            } finally {
                cleanupBackgroundThread();
            }
        });
    }

private void startServices(Context context) {
    try {
        Log.d(TAG, "Starting necessary services");
        
        // Start Thermal services
        try {
            // Start ThermalMonitorService for background monitoring
            Intent thermalMonitorIntent = new Intent(context, 
                    org.lineageos.settings.thermal.ThermalMonitorService.class);
            context.startService(thermalMonitorIntent);
            Log.d(TAG, "ThermalMonitorService started");
            
            // Start ThermalService if enabled
            org.lineageos.settings.thermal.ThermalUtils thermalUtils = 
                    org.lineageos.settings.thermal.ThermalUtils.getInstance(context);
            if (thermalUtils.isEnabled()) {
                thermalUtils.startService();
                Log.d(TAG, "ThermalService started (enabled)");
            } else {
                Log.d(TAG, "ThermalService not started (disabled)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Thermal services failed to start", e);
        }
        
        // Start RefreshService
        try {
            Log.d(TAG, "Starting RefreshService");
            org.lineageos.settings.refreshrate.RefreshUtils.startService(context);
        } catch (Exception e) {
            Log.e(TAG, "RefreshService failed to start", e);
        }
        
        Log.d(TAG, "Services started successfully");
    } catch (Exception e) {
        Log.e(TAG, "Failed to start services", e);
    }
}

    private void ensureDefaultGovernorIfNeeded(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean hasPerformanceProfile = prefs.contains(KEY_PERFORMANCE_PROFILE);
            if (!hasPerformanceProfile) {
                if (FileUtils.isFileWritable(POLICY0_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY0_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                if (FileUtils.isFileWritable(POLICY4_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY4_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                if (FileUtils.isFileWritable(POLICY6_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY6_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                Log.d(TAG, "Set default governor to " + DEFAULT_GOVERNOR + " (no performance profile found)");
            } else {
                Log.d(TAG, "Performance profile found, skipping default governor setup");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set default governor", e);
        }
    }

    private void restorePerformanceProfile(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.contains(KEY_PERFORMANCE_PROFILE)) {
                Log.d(TAG, "No performance profile saved, skipping restore");
                return;
            }
            int savedMode = prefs.getInt(KEY_PERFORMANCE_PROFILE, PerformanceUtils.MODE_BALANCED);
            Thread.sleep(1500);
            PerformanceUtils performanceUtils = new PerformanceUtils(context);
            boolean success = performanceUtils.setPerformanceMode(savedMode);
            if (success) {
                Log.d(TAG, "Performance profile restored to: " + performanceUtils.getModeLabel(savedMode));
            } else {
                Log.w(TAG, "Failed to restore performance profile to: " + savedMode);
                success = performanceUtils.setPerformanceMode(PerformanceUtils.MODE_BALANCED);
                if (success) {
                    Log.d(TAG, "Performance profile fallback to balanced mode successful");
                } else {
                    Log.e(TAG, "Performance profile fallback also failed");
                }
            }
            if (DEBUG) {
                Thread.sleep(500);
                int currentMode = performanceUtils.getCurrentMode();
                Log.d(TAG, "Performance profile verification - Expected: " + savedMode + 
                     ", Current: " + currentMode + " (" + performanceUtils.getModeLabel(currentMode) + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Performance profile restore interrupted", e);
            return;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore performance profile", e);
            try {
                PerformanceUtils performanceUtils = new PerformanceUtils(context);
                performanceUtils.setPerformanceMode(PerformanceUtils.MODE_BALANCED);
                Log.d(TAG, "Set safe default performance mode");
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set safe default performance mode", ex);
            }
        }
    }

    private void restoreCoreControlSettings(Context context) {
        try {
            if (!CoreControlUtils.isSupported()) {
                Log.d(TAG, "Core control not supported on this device");
                return;
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean coreControlEnabled = prefs.getBoolean(KEY_CORE_CONTROL_ENABLED, false);
            if (!coreControlEnabled) {
                Log.d(TAG, "Core control disabled, skipping restore");
                return;
            }
            Thread.sleep(2000);
            Log.d(TAG, "Restoring core control settings");
            CoreControlUtils.restoreCorePreferences(context);
            if (DEBUG) {
                CoreControlUtils.CoreStats stats = CoreControlUtils.getCoreStatistics();
                Log.d(TAG, "Core control restored - " + stats.toString());
                for (int i = 0; i < 8; i++) {
                    boolean online = CoreControlUtils.isCoreOnline(i);
                    Log.d(TAG, "Core " + i + ": " + (online ? "online" : "offline"));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Core control restore interrupted", e);
            return;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore core control settings", e);
        }
    }

    private void restoreLogcatService(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean autoStartLogcat = prefs.getBoolean(KEY_AUTO_START_LOGCAT, false);
            if (autoStartLogcat) {
                Intent logcatIntent = new Intent(context, LogcatBackgroundService.class);
                context.startServiceAsUser(logcatIntent, UserHandle.CURRENT);
                Log.d(TAG, "Logcat service started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore logcat service", e);
        }
    }

    private void restoreAdBlockerSettings(Context context) {
        try {
            Log.d(TAG, "Restoring AdBlocker settings...");
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean adBlockerEnabled = prefs.getBoolean(KEY_ADBLOCKER_ENABLED, false);
            
            // Add delay to ensure network services are ready
            Thread.sleep(3000);
            
            AdBlockerUtils adBlockerUtils = new AdBlockerUtils(context);
            
            // Check if hosts file is loaded
            int blockedCount = adBlockerUtils.getBlockedDomainsCount();
            Log.d(TAG, "AdBlocker blocked count: " + blockedCount);
            
            if (adBlockerEnabled) {
                if (blockedCount > 0) {
                    boolean success = adBlockerUtils.enableAdBlocker();
                    if (success) {
                        Log.d(TAG, "AdBlocker enabled successfully on boot");
                    } else {
                        Log.w(TAG, "Failed to enable AdBlocker on boot, retrying...");
                        // Retry after additional delay
                        Thread.sleep(2000);
                        success = adBlockerUtils.enableAdBlocker();
                        if (success) {
                            Log.d(TAG, "AdBlocker enabled successfully on retry");
                        } else {
                            Log.e(TAG, "AdBlocker enable failed on retry");
                        }
                    }
                } else {
                    Log.w(TAG, "AdBlocker is enabled but no hosts file loaded");
                }
            } else {
                // Ensure AdBlocker is disabled and DNS is reset
                adBlockerUtils.disableAdBlocker();
                Log.d(TAG, "AdBlocker disabled, DNS reset to neutral");
            }
            
            // Restore proxy settings if enabled
            boolean proxyEnabled = adBlockerUtils.isProxyEnabled();
            if (proxyEnabled && adBlockerUtils.hasRootAccess()) {
                String proxyHost = adBlockerUtils.getProxyHost();
                int proxyPort = adBlockerUtils.getProxyPort();
                if (!proxyHost.isEmpty()) {
                    Thread.sleep(1000);
                    boolean proxySuccess = adBlockerUtils.setGlobalProxy(proxyHost, proxyPort);
                    if (proxySuccess) {
                        Log.d(TAG, "Global proxy restored: " + proxyHost + ":" + proxyPort);
                    } else {
                        Log.w(TAG, "Failed to restore global proxy");
                    }
                }
            }
            
            Log.d(TAG, "AdBlocker settings restore completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "AdBlocker restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore AdBlocker settings", e);
            
            // Fallback: try to set safe default (disabled state)
            try {
                AdBlockerUtils fallbackUtils = new AdBlockerUtils(context);
                fallbackUtils.disableAdBlocker();
                Log.d(TAG, "AdBlocker fallback to disabled state");
            } catch (Exception ex) {
                Log.e(TAG, "Fallback also failed", ex);
            }
        }
    }

    private void restoreKernelSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            if (prefs.contains(KEY_PERFORMANCE_PROFILE)) {
                Log.d(TAG, "Performance profile active, skipping manual kernel restore");
                return;
            }

            KernelManagerUtils kernelUtils = new KernelManagerUtils();
            if (!kernelUtils.isKernelManagerSupported()) return;

            String savedGov = prefs.getString(KEY_CPU_GOVERNOR, null);
            if (savedGov != null) {
                kernelUtils.setGovernor(savedGov);
                Log.d(TAG, "Restored governor: " + savedGov);
            }

            String eMin = prefs.getString(KEY_EFFICIENCY_MIN_FREQ, null);
            String eMax = prefs.getString(KEY_EFFICIENCY_MAX_FREQ, null);
            if (eMin != null && eMax != null) {
                kernelUtils.setFrequency(KernelManagerUtils.CLUSTER_LITTLE, eMax, false);
                kernelUtils.setFrequency(KernelManagerUtils.CLUSTER_LITTLE, eMin, true);
            }

            String pMin = prefs.getString(KEY_PERFORMANCE_MIN_FREQ, null);
            String pMax = prefs.getString(KEY_PERFORMANCE_MAX_FREQ, null);
            if (pMin != null && pMax != null) {
                kernelUtils.setFrequency(KernelManagerUtils.CLUSTER_BIG, pMax, false);
                kernelUtils.setFrequency(KernelManagerUtils.CLUSTER_BIG, pMin, true);
            }
            
            Log.d(TAG, "Kernel settings restored");

        } catch (Exception e) {
            Log.e(TAG, "Failed to restore kernel settings", e);
        }
    }

    private void restoreClusterFrequencies(SharedPreferences prefs, KernelManagerUtils kernelUtils,
                                         int cluster, String minKey, String maxKey, String clusterName) {
        try {
            String minFreq = prefs.getString(minKey, null);
            if (minFreq != null && !minFreq.isEmpty()) {
                try {
                    kernelUtils.setMinFrequency(cluster, minFreq);
                    Log.d(TAG, "Restored " + clusterName + " min freq: " + minFreq);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore " + clusterName + " min freq: " + minFreq, e);
                }
            }
            String maxFreq = prefs.getString(maxKey, null);
            if (maxFreq != null && !maxFreq.isEmpty()) {
                try {
                    kernelUtils.setMaxFrequency(cluster, maxFreq);
                    Log.d(TAG, "Restored " + clusterName + " max freq: " + maxFreq);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore " + clusterName + " max freq: " + maxFreq, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring " + clusterName + " cluster frequencies", e);
        }
    }

    private void restoreGpuSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Ha a rendszer kezeli a teljesítmény profilt, ne írjuk felül manuálisan a GPU-t
            boolean hasPerformanceProfile = prefs.contains(KEY_PERFORMANCE_PROFILE);
            if (hasPerformanceProfile) {
                Log.d(TAG, "Performance profile active, skipping individual GPU settings restore");
                return;
            }
            
            Log.d(TAG, "Restoring GPU settings...");
            GpuManagerUtils gpuUtils = new GpuManagerUtils();
            
            if (!gpuUtils.isGpuManagerSupported()) {
                Log.w(TAG, "GPU Manager not supported, skipping restore");
                return;
            }

            // Restore Governor
            String savedGpuGovernor = prefs.getString(KEY_GPU_GOVERNOR, null);
            if (savedGpuGovernor != null) {
                gpuUtils.setGovernor(savedGpuGovernor);
                Log.d(TAG, "Restored GPU governor: " + savedGpuGovernor);
            }

            // Restore Frequencies
            String gpuMinFreq = prefs.getString(KEY_GPU_MIN_FREQ, null);
            String gpuMaxFreq = prefs.getString(KEY_GPU_MAX_FREQ, null);
            if (gpuMinFreq != null && gpuMaxFreq != null) {
                gpuUtils.setFrequencyRange(gpuMinFreq, gpuMaxFreq);
                Log.d(TAG, "Restored GPU freq range: " + gpuMinFreq + " - " + gpuMaxFreq);
            }
            
            // Restore Max GPUCLK Override
            String gpuMaxClk = prefs.getString("gpu_max_gpuclk", null);
            if (gpuMaxClk != null) {
                gpuUtils.setMaxGpuClk(gpuMaxClk);
                Log.d(TAG, "Restored GPU Max CLK: " + gpuMaxClk);
            }

            // Restore Power Settings
            if (prefs.getBoolean(KEY_GPU_FORCE_CLK_ON, false)) gpuUtils.setForceClkOn(true);
            if (prefs.getBoolean(KEY_GPU_FORCE_BUS_ON, false)) gpuUtils.setForceBusOn(true);
            if (prefs.getBoolean(KEY_GPU_FORCE_RAIL_ON, false)) gpuUtils.setForceRailOn(true);
            if (prefs.getBoolean(KEY_GPU_FORCE_NO_NAP, false)) gpuUtils.setForceNoNap(true);
            if (prefs.getBoolean(KEY_GPU_BUS_SPLIT, false)) gpuUtils.setBusSplit(true);
            
            // Restore Preemption
            if (prefs.contains("gpu_preempt")) {
                boolean preempt = prefs.getBoolean("gpu_preempt", false);
                gpuUtils.setPreempt(preempt);
                Log.d(TAG, "Restored GPU preempt: " + preempt);
            }

            Log.d(TAG, "GPU settings restoration completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore GPU settings", e);
        }
    }
    
    private void restoreGpuFrequencies(SharedPreferences prefs, GpuManagerUtils gpuUtils) {
        try {
            String gpuMinFreq = prefs.getString(KEY_GPU_MIN_FREQ, null);
            String gpuMaxFreq = prefs.getString(KEY_GPU_MAX_FREQ, null);
            if (gpuMinFreq != null && !gpuMinFreq.isEmpty() &&
                gpuMaxFreq != null && !gpuMaxFreq.isEmpty()) {
                try {
                    gpuUtils.setFrequencyRange(gpuMinFreq, gpuMaxFreq);
                    Log.d(TAG, "Restored GPU freq range: " + gpuMinFreq + " - " + gpuMaxFreq);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU freq range: " + gpuMinFreq + " - " + gpuMaxFreq, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring GPU frequencies", e);
        }
    }

    private void restoreGpuPowerSettings(SharedPreferences prefs, GpuManagerUtils gpuUtils) {
        try {
            if (prefs.contains(KEY_GPU_FORCE_CLK_ON)) {
                boolean forceClkOn = prefs.getBoolean(KEY_GPU_FORCE_CLK_ON, false);
                try {
                    gpuUtils.setForceClkOn(forceClkOn);
                    Log.d(TAG, "Restored GPU force clk on: " + forceClkOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force clk on: " + forceClkOn, e);
                }
            }
            if (prefs.contains(KEY_GPU_FORCE_BUS_ON)) {
                boolean forceBusOn = prefs.getBoolean(KEY_GPU_FORCE_BUS_ON, false);
                try {
                    gpuUtils.setForceBusOn(forceBusOn);
                    Log.d(TAG, "Restored GPU force bus on: " + forceBusOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force bus on: " + forceBusOn, e);
                }
            }
            if (prefs.contains(KEY_GPU_FORCE_RAIL_ON)) {
                boolean forceRailOn = prefs.getBoolean(KEY_GPU_FORCE_RAIL_ON, false);
                try {
                    gpuUtils.setForceRailOn(forceRailOn);
                    Log.d(TAG, "Restored GPU force rail on: " + forceRailOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force rail on: " + forceRailOn, e);
                }
            }
            if (prefs.contains(KEY_GPU_FORCE_NO_NAP)) {
                boolean forceNoNap = prefs.getBoolean(KEY_GPU_FORCE_NO_NAP, false);
                try {
                    gpuUtils.setForceNoNap(forceNoNap);
                    Log.d(TAG, "Restored GPU force no nap: " + forceNoNap);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force no nap: " + forceNoNap, e);
                }
            }
            if (prefs.contains(KEY_GPU_BUS_SPLIT)) {
                boolean busSplit = prefs.getBoolean(KEY_GPU_BUS_SPLIT, false);
                try {
                    gpuUtils.setBusSplit(busSplit);
                    Log.d(TAG, "Restored GPU bus split: " + busSplit);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU bus split: " + busSplit, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring GPU power settings", e);
        }
    }

    private void restoreVideoEnhancerSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null for Video Enhancer settings");
                return;
            }
            Log.d(TAG, "Restoring Video Enhancer settings...");
            Thread.sleep(1000);
            VideoEnhancerUtils videoUtils = new VideoEnhancerUtils(context);
            if (!videoUtils.isRootAvailable()) {
                Log.w(TAG, "Root not available, skipping Video Enhancer restore");
                return;
            }
            videoUtils.applyOnBoot();
            Log.d(TAG, "Video Enhancer settings restored successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Video Enhancer restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore Video Enhancer settings", e);
        }
    }

    private void initializeBackgroundThread() {
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void cleanupBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
        }
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            mBackgroundThread = null;
        }
    }

    private void initializeCpuTileService(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean cpuTileEnabled = prefs.getBoolean(KEY_CPU_TILE_ENABLED, false);
            if (cpuTileEnabled) {
                // Start CPU Tile service if enabled
                // Intent cpuTileIntent = new Intent(context, CpuTileService.class);
                // context.startService(cpuTileIntent);
                Log.d(TAG, "CPU Tile service initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU Tile service", e);
        }
    }
}
