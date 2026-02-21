/*
 * Copyright (C) 2015 The CyanogenMod Project
 * 2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import androidx.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import org.lineageos.settings.gpumanager.GpuManagerUtils;
import org.lineageos.settings.corecontrol.CoreControlUtils;
import org.lineageos.settings.logcatviewer.LogcatBackgroundService;
import org.lineageos.settings.performance.PerformanceUtils;
import org.lineageos.settings.utils.FileUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = false; // Disabled for production
    private static final String TAG = "XiaomiParts";

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
                // Restore performance profile first (sets msm_performance freq floors)
                startServices(context);
                restorePerformanceProfile(context);
                restoreKernelSettings(context);
                restoreGpuSettings(context);
                restoreCoreControlSettings(context);
                restoreRamOptimizerSettings(context);
                initializeCpuTileService(context);
                
                // Auto-start logcat if enabled
                restoreLogcatService(context);
                
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
            if (DEBUG) Log.d(TAG, "Starting necessary services");
            
            // Start Thermal services
            try {
                Intent thermalMonitorIntent = new Intent(context, 
                        org.lineageos.settings.thermal.ThermalMonitorService.class);
                context.startService(thermalMonitorIntent);
                
                org.lineageos.settings.thermal.ThermalUtils thermalUtils = 
                        org.lineageos.settings.thermal.ThermalUtils.getInstance(context);
                if (thermalUtils.isEnabled()) {
                    thermalUtils.startService();
                }
            } catch (Exception e) {
                Log.e(TAG, "Thermal services failed to start", e);
            }
            
            // Start RefreshService
            try {
                org.lineageos.settings.refreshrate.RefreshUtils.startService(context);
            } catch (Exception e) {
                Log.e(TAG, "RefreshService failed to start", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start services", e);
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
            
            // Optimized delay for SM7435
            Thread.sleep(500);
            
            PerformanceUtils performanceUtils = new PerformanceUtils(context);
            boolean success = performanceUtils.setPerformanceMode(savedMode);
            if (success) {
                Log.d(TAG, "Performance profile restored to: " + performanceUtils.getModeLabel(savedMode));
            } else {
                // Fallback to balanced
                performanceUtils.setPerformanceMode(PerformanceUtils.MODE_BALANCED);
                Log.d(TAG, "Performance profile fallback to balanced mode");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Performance profile restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore performance profile", e);
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
            
            // Optimized delay for SM7435
            Thread.sleep(1000);
            
            Log.d(TAG, "Restoring core control settings");
            CoreControlUtils.restoreCorePreferences(context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Core control restore interrupted", e);
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

    private void restoreKernelSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean hasPerformanceProfile = prefs.contains(KEY_PERFORMANCE_PROFILE);
            if (hasPerformanceProfile) {
                Log.d(TAG, "Performance profile active, skipping individual kernel settings restore");
                return;
            }
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null for kernel settings");
                return;
            }
            KernelManagerUtils kernelUtils = new KernelManagerUtils();
            if (!kernelUtils.isKernelManagerSupported()) {
                Log.w(TAG, "Kernel Manager not supported");
                return;
            }
            
            String savedGovernor = prefs.getString(KEY_CPU_GOVERNOR, null);
            if (savedGovernor != null && !savedGovernor.isEmpty()) {
                try {
                    kernelUtils.setGovernor(savedGovernor);
                    Log.d(TAG, "Restored CPU governor: " + savedGovernor);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore CPU governor: " + savedGovernor, e);
                }
            }
            
            restoreClusterFrequencies(prefs, kernelUtils, 
                KernelManagerUtils.EFFICIENCY_CLUSTER, 
                KEY_EFFICIENCY_MIN_FREQ, KEY_EFFICIENCY_MAX_FREQ, "efficiency");
            restoreClusterFrequencies(prefs, kernelUtils,
                KernelManagerUtils.PERFORMANCE_CLUSTER,
                KEY_PERFORMANCE_MIN_FREQ, KEY_PERFORMANCE_MAX_FREQ, "performance");
            
            Log.d(TAG, "Kernel settings restoration completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore kernel settings", e);
        }
    }

    private void restoreClusterFrequencies(SharedPreferences prefs, KernelManagerUtils kernelUtils,
                                         int cluster, String minKey, String maxKey, String clusterName) {
        try {
            String minFreq = prefs.getString(minKey, null);
            String maxFreq = prefs.getString(maxKey, null);
            
            if (minFreq != null && !minFreq.isEmpty() && 
                maxFreq != null && !maxFreq.isEmpty()) {
                if (kernelUtils.setMinFrequency(cluster, minFreq)) {
                    Log.d(TAG, "Restored " + clusterName + " min freq: " + minFreq);
                }
                if (kernelUtils.setMaxFrequency(cluster, maxFreq)) {
                    Log.d(TAG, "Restored " + clusterName + " max freq: " + maxFreq);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring " + clusterName + " cluster frequencies", e);
        }
    }

    private void restoreGpuSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean hasPerformanceProfile = prefs.contains(KEY_PERFORMANCE_PROFILE);
            if (hasPerformanceProfile) {
                Log.d(TAG, "Performance profile active, skipping individual GPU settings restore");
                return;
            }
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null for GPU settings");
                return;
            }
            GpuManagerUtils gpuUtils = new GpuManagerUtils();

            String savedGpuGovernor = prefs.getString(KEY_GPU_GOVERNOR, null);
            if (savedGpuGovernor != null && !savedGpuGovernor.isEmpty()) {
                try {
                    gpuUtils.setGovernor(savedGpuGovernor);
                    Log.d(TAG, "Restored GPU governor: " + savedGpuGovernor);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU governor: " + savedGpuGovernor, e);
                }
            }
            
            restoreGpuFrequencies(prefs, gpuUtils);
            restoreGpuPowerSettings(prefs, gpuUtils);

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
                    Log.w(TAG, "Failed to restore GPU freq range", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring GPU frequencies", e);
        }
    }

    private void restoreGpuPowerSettings(SharedPreferences prefs, GpuManagerUtils gpuUtils) {
        try {
            applyGpuBooleanSetting(prefs, gpuUtils, KEY_GPU_FORCE_CLK_ON, gpuUtils::setForceClkOn, "force clk on");
            applyGpuBooleanSetting(prefs, gpuUtils, KEY_GPU_FORCE_BUS_ON, gpuUtils::setForceBusOn, "force bus on");
            applyGpuBooleanSetting(prefs, gpuUtils, KEY_GPU_FORCE_RAIL_ON, gpuUtils::setForceRailOn, "force rail on");
            applyGpuBooleanSetting(prefs, gpuUtils, KEY_GPU_FORCE_NO_NAP, gpuUtils::setForceNoNap, "force no nap");
            applyGpuBooleanSetting(prefs, gpuUtils, KEY_GPU_BUS_SPLIT, gpuUtils::setBusSplit, "bus split");
        } catch (Exception e) {
            Log.e(TAG, "Error restoring GPU power settings", e);
        }
    }

    private void applyGpuBooleanSetting(SharedPreferences prefs, GpuManagerUtils gpuUtils, 
                                       String key, java.util.function.Consumer<Boolean> setter, String name) {
        if (prefs.contains(key)) {
            boolean value = prefs.getBoolean(key, false);
            try {
                setter.accept(value);
                Log.d(TAG, "Restored GPU " + name + ": " + value);
            } catch (Exception e) {
                Log.w(TAG, "Failed to restore GPU " + name, e);
            }
        }
    }

    private void initializeBackgroundThread() {
        mBackgroundThread = new HandlerThread("BootCompletedReceiver");
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

    private void restoreRamOptimizerSettings(Context context) {
        try {
            if (!org.lineageos.settings.ramoptimizer.RamOptimizerUtils.isSupported()) {
                Log.d(TAG, "RAM Optimizer not supported, skipping restore");
                return;
            }
            // Runs on mBackgroundHandler thread — root commands are safe here
            org.lineageos.settings.ramoptimizer.RamOptimizerUtils.restorePreferences(context);
            Log.d(TAG, "RAM Optimizer preferences restored");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore RAM Optimizer settings", e);
        }
    }

    private void initializeCpuTileService(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean cpuTileEnabled = prefs.getBoolean(KEY_CPU_TILE_ENABLED, false);
            if (cpuTileEnabled) {
                Log.d(TAG, "CPU Tile service initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU Tile service", e);
        }
    }
}
