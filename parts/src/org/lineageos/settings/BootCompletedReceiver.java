/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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
import org.lineageos.settings.utils.FileUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "XiaomiParts";

    // Performance Mode paths
    private static final String POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor";
    private static final String POLICY4_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor";
    private static final String POLICY6_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy6/scaling_governor";
    private static final String DEFAULT_GOVERNOR = "walt";  // Modified to walt

    // Kernel Manager preference keys
    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFFICIENCY_MIN_FREQ = "efficiency_min_freq";
    private static final String KEY_EFFICIENCY_MAX_FREQ = "efficiency_max_freq";
    private static final String KEY_PERFORMANCE_MIN_FREQ = "performance_min_freq";
    private static final String KEY_PERFORMANCE_MAX_FREQ = "performance_max_freq";

    // GPU Manager preference keys
    private static final String KEY_GPU_GOVERNOR = "gpu_governor";
    private static final String KEY_GPU_MIN_FREQ = "gpu_min_freq";
    private static final String KEY_GPU_MAX_FREQ = "gpu_max_freq";
    private static final String KEY_GPU_FORCE_CLK_ON = "gpu_force_clk_on";
    private static final String KEY_GPU_FORCE_BUS_ON = "gpu_force_bus_on";
    private static final String KEY_GPU_FORCE_RAIL_ON = "gpu_force_rail_on";
    private static final String KEY_GPU_FORCE_NO_NAP = "gpu_force_no_nap";
    private static final String KEY_GPU_BUS_SPLIT = "gpu_bus_split";

    // Performance Profile preference key
    private static final String KEY_PERFORMANCE_PROFILE = "current_performance_mode";

    // Core Control preference key
    private static final String KEY_CORE_CONTROL_ENABLED = "core_control_enabled";

    // Logcat preference key
    private static final String KEY_AUTO_START_LOGCAT = "auto_start_logcat";

    // AdBlocker preference key
    private static final String KEY_ADBLOCKER_ENABLED = "adblocker_enabled";

    // CPU Tile preference keys
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
        // Initialize background thread for heavy operations
        initializeBackgroundThread();
        
        mBackgroundHandler.post(() -> {
            try {
                // Start services
                startServices(context);
                
                // Performance Mode - Ensure default governor on boot (only if no profile is saved)
                ensureDefaultGovernorIfNeeded(context);
                
                // Restore Performance Profile settings first (has priority over individual settings)
                restorePerformanceProfile(context);
                
                // Restore other settings
                restoreKernelSettings(context);
                restoreGpuSettings(context);
                restoreCoreControlSettings(context);
                restoreLogcatService(context);
                restoreAdBlockerSettings(context);
                
                // Initialize CPU Tile Service
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
        
        // Clean up background thread after operations
        mBackgroundHandler.post(() -> {
            try {
                Log.i(TAG, "Boot completed initialization finished");
            } catch (Exception e) {
                Log.e(TAG, "Error during boot completed initialization", e);
            } finally {
                // Clean up background thread after all operations
                cleanupBackgroundThread();
            }
        });
    }

    private void startServices(Context context) {
        try {
            Log.d(TAG, "Starting necessary services");
            
            // Start any required services here
            // For example, if there are background services that need to be started
            
            // Example: Starting a background service if needed
            // Intent serviceIntent = new Intent(context, SomeBackgroundService.class);
            // context.startService(serviceIntent);
            
            Log.d(TAG, "Services started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start services", e);
        }
    }

    private void ensureDefaultGovernorIfNeeded(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Check if performance profile is saved
            boolean hasPerformanceProfile = prefs.contains(KEY_PERFORMANCE_PROFILE);
            
            if (!hasPerformanceProfile) {
                // No performance profile saved, set default governor
                if (FileUtils.isFileWritable(POLICY0_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY0_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                if (FileUtils.isFileWritable(POLICY4_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY4_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                if (FileUtils.isFileWritable(POLICY6_GOVERNOR_PATH)) {
                    FileUtils.writeLine(POLICY6_GOVERNOR_PATH, DEFAULT_GOVERNOR);
                }
                if (DEBUG) {
                    Log.d(TAG, "Set default governor to " + DEFAULT_GOVERNOR + " (no performance profile found)");
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Performance profile found, skipping default governor setup");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set default governor", e);
        }
    }

    private void restorePerformanceProfile(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.contains(KEY_PERFORMANCE_PROFILE) ) {
                Log.d(TAG, "No performance profile saved, skipping restore");
                return;
            }

            int savedMode = prefs.getInt(KEY_PERFORMANCE_PROFILE, PerformanceUtils.MODE_BALANCED);
            
            // Wait a bit for the system to stabilize
            Thread.sleep(1500);
            
            PerformanceUtils performanceUtils = new PerformanceUtils(context);
            boolean success = performanceUtils.setPerformanceMode(savedMode);
            
            if (success) {
                Log.d(TAG, "Performance profile restored to: " + performanceUtils.getModeLabel(savedMode));
            } else {
                Log.w(TAG, "Failed to restore performance profile to: " + savedMode);
                
                // Fallback to balanced mode
                success = performanceUtils.setPerformanceMode(PerformanceUtils.MODE_BALANCED);
                if (success) {
                    Log.d(TAG, "Performance profile fallback to balanced mode successful");
                } else {
                    Log.e(TAG, "Performance profile fallback also failed");
                }
            }
            
            // Verify the state after a short delay
            if (DEBUG) {
                Thread.sleep(500);
                int currentMode = performanceUtils.getCurrentMode();
                Log.d(TAG, "Performance profile verification - Expected: " + savedMode + 
                     ", Current: " + currentMode + " (" + performanceUtils.getModeLabel(currentMode) + ")");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Performance profile restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore performance profile", e);
            
            // Try to set a safe default
            try {
                PerformanceUtils performanceUtils = new PerformanceUtils(context);
                performanceUtils.setPerformanceMode(PerformanceUtils.MODE_BALANCED);
                Log.d(TAG, "Set safe default performance mode");
            } catch (Exception ex) {
                Log.e(TAG, "Failed to set safe default performance mode", ex);
            }
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean adBlockerEnabled = prefs.getBoolean(KEY_ADBLOCKER_ENABLED, false);
            
            // Create instance of AdBlockerUtils
            AdBlockerUtils adBlockerUtils = new AdBlockerUtils(context);
            
            if (adBlockerEnabled) {
                adBlockerUtils.enableAdBlocker();
                Log.d(TAG, "AdBlocker enabled");
            } else {
                adBlockerUtils.disableAdBlocker();
                Log.d(TAG, "AdBlocker disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore AdBlocker settings", e);
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

            // Wait a bit for the system to stabilize
            Thread.sleep(2000);
            
            Log.d(TAG, "Restoring core control settings");
            CoreControlUtils.restoreCorePreferences(context);
            
            // Log current state for debugging
            if (DEBUG) {
                CoreControlUtils.CoreStats stats = CoreControlUtils.getCoreStatistics();
                Log.d(TAG, "Core control restored - " + stats.toString());
                
                // Log individual core states
                for (int i = 0; i < 8; i++) {
                    boolean online = CoreControlUtils.isCoreOnline(i);
                    Log.d(TAG, "Core " + i + ": " + (online ? "online" : "offline"));
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Core control restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore core control settings", e);
        }
    }

    private void restoreKernelSettings(Context context) {
        try {
            // Check if performance profile is managing these settings
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

            // Restore CPU Governor
            String savedGovernor = prefs.getString(KEY_CPU_GOVERNOR, null);
            if (savedGovernor != null && !savedGovernor.isEmpty()) {
                try {
                    kernelUtils.setGovernor(savedGovernor);
                    Log.d(TAG, "Restored CPU governor: " + savedGovernor);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore CPU governor: " + savedGovernor, e);
                }
            }

            // Restore Efficiency cluster frequencies
            restoreClusterFrequencies(prefs, kernelUtils, 
                KernelManagerUtils.EFFICIENCY_CLUSTER, 
                KEY_EFFICIENCY_MIN_FREQ, KEY_EFFICIENCY_MAX_FREQ,
                "efficiency");

            // Restore Performance cluster frequencies
            restoreClusterFrequencies(prefs, kernelUtils,
                KernelManagerUtils.PERFORMANCE_CLUSTER,
                KEY_PERFORMANCE_MIN_FREQ, KEY_PERFORMANCE_MAX_FREQ,
                "performance");

            Log.d(TAG, "Kernel settings restoration completed");
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
            // Check if performance profile is managing these settings
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

            // Restore GPU Governor
            String savedGpuGovernor = prefs.getString(KEY_GPU_GOVERNOR, null);
            if (savedGpuGovernor != null && !savedGpuGovernor.isEmpty()) {
                try {
                    gpuUtils.setGovernor(savedGpuGovernor);
                    Log.d(TAG, "Restored GPU governor: " + savedGpuGovernor);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU governor: " + savedGpuGovernor, e);
                }
            }

            // Restore GPU frequencies
            restoreGpuFrequencies(prefs, gpuUtils);

            // Restore GPU power settings
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
                    Log.w(TAG, "Failed to restore GPU freq range: " + gpuMinFreq + " - " + gpuMaxFreq, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring GPU frequencies", e);
        }
    }

    private void restoreGpuPowerSettings(SharedPreferences prefs, GpuManagerUtils gpuUtils) {
        try {
            // Restore force clock on
            if (prefs.contains(KEY_GPU_FORCE_CLK_ON)) {
                boolean forceClkOn = prefs.getBoolean(KEY_GPU_FORCE_CLK_ON, false);
                try {
                    gpuUtils.setForceClkOn(forceClkOn);
                    Log.d(TAG, "Restored GPU force clk on: " + forceClkOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force clk on: " + forceClkOn, e);
                }
            }

            // Restore force bus on
            if (prefs.contains(KEY_GPU_FORCE_BUS_ON)) {
                boolean forceBusOn = prefs.getBoolean(KEY_GPU_FORCE_BUS_ON, false);
                try {
                    gpuUtils.setForceBusOn(forceBusOn);
                    Log.d(TAG, "Restored GPU force bus on: " + forceBusOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force bus on: " + forceBusOn, e);
                }
            }

            // Restore force rail on
            if (prefs.contains(KEY_GPU_FORCE_RAIL_ON)) {
                boolean forceRailOn = prefs.getBoolean(KEY_GPU_FORCE_RAIL_ON, false);
                try {
                    gpuUtils.setForceRailOn(forceRailOn);
                    Log.d(TAG, "Restored GPU force rail on: " + forceRailOn);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force rail on: " + forceRailOn, e);
                }
            }

            // Restore force no nap
            if (prefs.contains(KEY_GPU_FORCE_NO_NAP)) {
                boolean forceNoNap = prefs.getBoolean(KEY_GPU_FORCE_NO_NAP, false);
                try {
                    gpuUtils.setForceNoNap(forceNoNap);
                    Log.d(TAG, "Restored GPU force no nap: " + forceNoNap);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restore GPU force no nap: " + forceNoNap, e);
                }
            }

            // Restore bus split
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
