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

import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.thermal.ThermalTileService;
import org.lineageos.settings.turbocharging.TurboChargingService;
import org.lineageos.settings.chargecontrol.ChargeControlService;
import org.lineageos.settings.kernelmanager.KernelManagerUtils;
import org.lineageos.settings.gpumanager.GpuManagerUtils;
import org.lineageos.settings.charge.ChargeUtils;
import org.lineageos.settings.corecontrol.CoreControlUtils;
import org.lineageos.settings.logcatviewer.LogcatBackgroundService;
import org.lineageos.settings.adblocker.AdBlockerUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "XiaomiParts";

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

    // Bypass Charge preference key
    private static final String KEY_BYPASS_CHARGE = "bypass_charge";

    // Core Control preference key
    private static final String KEY_CORE_CONTROL_ENABLED = "core_control_enabled";

    // Logcat preference key
    private static final String KEY_AUTO_START_LOGCAT = "auto_start_logcat";

    // AdBlocker preference key
    private static final String KEY_ADBLOCKER_ENABLED = "adblocker_enabled";

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
                
                // Restore settings
                restoreKernelSettings(context);
                restoreGpuSettings(context);
                restoreBypassCharge(context);
                restoreCoreControlSettings(context);
                restoreLogcatService(context);
                restoreAdBlockerSettings(context);
                
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
        
        // Only initialize Dirac here (after user unlock)
        mBackgroundHandler.post(() -> {
            try {
                initializeDirac(context);
                Log.i(TAG, "Boot completed initialization finished");
            } catch (Exception e) {
                Log.e(TAG, "Error during boot completed initialization", e);
            } finally {
                // Clean up background thread after all operations
                cleanupBackgroundThread();
            }
        });
    }

    private void initializeBackgroundThread() {
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("BootInitialization");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void cleanupBackgroundThread() {
        if (mBackgroundThread != null) {
            try {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                Log.w(TAG, "Thread interrupted during cleanup", e);
                Thread.currentThread().interrupt();
            } finally {
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }
        }
    }

    private void startServices(Context context) {
        try {
            // Start TurboChargingService
            Intent turboChargingIntent = new Intent(context, TurboChargingService.class);
            context.startService(turboChargingIntent);
            Log.d(TAG, "TurboChargingService started");

            // Start Charge Control Service
            context.startServiceAsUser(
                new Intent(context, ChargeControlService.class), 
                UserHandle.CURRENT
            );
            Log.d(TAG, "ChargeControlService started");

            // Start Thermal Management Services
            ThermalUtils.startService(context);
            context.startServiceAsUser(
                new Intent(context, ThermalTileService.class), 
                UserHandle.CURRENT
            );
            Log.d(TAG, "Thermal services started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start services", e);
        }
    }

    private void restoreLogcatService(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean autoStart = prefs.getBoolean(KEY_AUTO_START_LOGCAT, false);
            if (autoStart) {
                Intent serviceIntent = new Intent(context, LogcatBackgroundService.class);
                context.startService(serviceIntent);
                Log.d(TAG, "LogcatBackgroundService started on boot");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start LogcatBackgroundService", e);
        }
    }

    private void restoreAdBlockerSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean adBlockerEnabled = prefs.getBoolean(KEY_ADBLOCKER_ENABLED, false);
            
            if (!adBlockerEnabled) {
                Log.d(TAG, "AdBlocker disabled, skipping restore");
                return;
            }

            AdBlockerUtils adBlockerUtils = new AdBlockerUtils(context);
            
            // Check if root access is available
            if (!adBlockerUtils.hasRootAccess()) {
                Log.w(TAG, "AdBlocker enabled but no root access available");
                return;
            }

            // Wait a bit for the system to stabilize
            Thread.sleep(3000);
            
            Log.d(TAG, "Restoring AdBlocker settings");
            
            // Check if we have a hosts file to restore
            int blockedCount = adBlockerUtils.getBlockedDomainsCount();
            if (blockedCount > 0) {
                // AdBlocker was previously enabled and has data, ensure it stays enabled
                boolean success = adBlockerUtils.enableAdBlocker();
                if (success) {
                    Log.d(TAG, "AdBlocker restored successfully - blocking " + blockedCount + " domains");
                } else {
                    Log.w(TAG, "Failed to restore AdBlocker settings");
                }
            } else {
                Log.d(TAG, "AdBlocker enabled but no hosts data found, may need manual update");
                // Still try to enable to ensure proper state
                adBlockerUtils.enableAdBlocker();
            }
            
            // Log current state for debugging
            if (DEBUG) {
                boolean currentState = adBlockerUtils.isEnabled();
                String lastUpdate = adBlockerUtils.getLastUpdateTime();
                Log.d(TAG, "AdBlocker restore completed - enabled: " + currentState + 
                     ", blocked domains: " + blockedCount + ", last update: " + lastUpdate);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "AdBlocker restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore AdBlocker settings", e);
        }
    }

    private void initializeDirac(final Context context) {
        Log.d(TAG, "Initializing Dirac audio enhancement");

        try {
            // Wait for audio system to be fully loaded
            Thread.sleep(3000);

            DiracUtils diracUtils = DiracUtils.getInstance(context);
            if (diracUtils == null) {
                Log.w(TAG, "DiracUtils instance is null");
                return;
            }

            // Force reinitialize to ensure proper state after boot
            diracUtils.reinitialize();

            Log.d(TAG, "Dirac initialized successfully, enabled: " + diracUtils.isDiracEnabled());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Dirac initialization interrupted", e);
        } catch (Exception e) {
            Log.w(TAG, "Dirac is not present in system or failed to initialize", e);

            // Retry once after additional delay
            try {
                Thread.sleep(2000);
                DiracUtils diracUtils = DiracUtils.getInstance(context);
                if (diracUtils != null) {
                    diracUtils.reinitialize();
                    Log.d(TAG, "Dirac initialization retry successful");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Dirac retry interrupted", ie);
            } catch (Exception e2) {
                Log.e(TAG, "Dirac initialization failed after retry", e2);
            }
        }
    }

 private void restoreBypassCharge(Context context) {
        try {
            ChargeUtils chargeUtils = new ChargeUtils(context);
            
            if (!chargeUtils.isBypassChargeSupported()) {
                Log.d(TAG, "Bypass charge not supported on this device");
                if (DEBUG) {
                    Log.d(TAG, "Bypass charge debug info:\n" + chargeUtils.getDebugInfo());
                }
                return;
            }

            // Get the saved preference
            boolean shouldEnable = chargeUtils.getBypassChargePreference();
            
            // Wait a bit for the system to stabilize
            Thread.sleep(1000);
            
            boolean currentState = chargeUtils.isBypassChargeEnabled();
            
            Log.d(TAG, "Bypass charge - saved preference: " + shouldEnable + ", current state: " + currentState);
            
            // Always try to restore the setting to ensure consistency
            boolean success = chargeUtils.enableBypassCharge(shouldEnable);
            if (success) {
                Log.d(TAG, "Bypass charge restored to: " + shouldEnable);
                
                // Verify after a delay
                Thread.sleep(500);
                boolean verifyState = chargeUtils.isBypassChargeEnabled();
                if (verifyState != shouldEnable) {
                    Log.w(TAG, "Bypass charge verification failed. Expected: " + shouldEnable + ", Actual: " + verifyState);
                    // Try once more with a different approach
                    Thread.sleep(500);
                    chargeUtils.refreshActiveNode(); // Refresh active node
                    boolean retrySuccess = chargeUtils.enableBypassCharge(shouldEnable);
                    if (retrySuccess) {
                        Log.d(TAG, "Bypass charge retry successful");
                    } else {
                        Log.e(TAG, "Bypass charge retry failed");
                    }
                }
            } else {
                Log.w(TAG, "Failed to restore bypass charge to: " + shouldEnable);
                
                // Try to find a different working node
                chargeUtils.refreshActiveNode();
                if (chargeUtils.isBypassChargeSupported()) {
                    Log.d(TAG, "Found alternative node, retrying bypass charge restore");
                    boolean retrySuccess = chargeUtils.enableBypassCharge(shouldEnable);
                    if (retrySuccess) {
                        Log.d(TAG, "Bypass charge restore successful with alternative node");
                    } else {
                        Log.e(TAG, "Bypass charge restore failed even with alternative node");
                    }
                }
            }
            
            // Log final state and debug info for troubleshooting
            if (DEBUG) {
                Thread.sleep(200);
                boolean finalState = chargeUtils.isBypassChargeEnabled();
                Log.d(TAG, "Final bypass charge state: " + finalState);
                Log.d(TAG, "Bypass charge debug info:\n" + chargeUtils.getDebugInfo());
                
                // Start monitor service for debugging
                try {
                    Intent monitorIntent = new Intent();
                    monitorIntent.setAction("start");
                    monitorIntent.setComponent(new android.content.ComponentName("com.android.shell", 
                        "bypass_charge_monitor"));
                    context.sendBroadcast(monitorIntent);
                } catch (Exception e) {
                    Log.d(TAG, "Could not start bypass charge monitor: " + e.getMessage());
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Bypass charge restore interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore bypass charge settings", e);
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null for kernel settings");
                return;
            }

            KernelManagerUtils kernelUtils = new KernelManagerUtils();
            if (kernelUtils == null) {
                Log.w(TAG, "KernelManagerUtils is null");
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null for GPU settings");
                return;
            }

            GpuManagerUtils gpuUtils = new GpuManagerUtils();
            if (gpuUtils == null) {
                Log.w(TAG, "GpuManagerUtils is null");
                return;
            }

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
}
