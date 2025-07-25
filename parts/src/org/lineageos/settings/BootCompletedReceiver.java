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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.thermal.ThermalTileService;
import org.lineageos.settings.turbocharging.TurboChargingService;
import org.lineageos.settings.chargecontrol.ChargeControlService;
import org.lineageos.settings.kernelmanager.KernelManagerUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "XiaomiParts";

    // Kernel Manager preference keys
    private static final String KEY_CPU_GOVERNOR = "cpu_governor";
    private static final String KEY_EFFICIENCY_MIN_FREQ = "efficiency_min_freq";
    private static final String KEY_EFFICIENCY_MAX_FREQ = "efficiency_max_freq";
    private static final String KEY_PERFORMANCE_MIN_FREQ = "performance_min_freq";
    private static final String KEY_PERFORMANCE_MAX_FREQ = "performance_max_freq";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "Received intent: " + intent.getAction());
        }

        if (!intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            return;
        }
              
        // Start TurboChargingService
        Intent turboChargingIntent = new Intent(context, TurboChargingService.class);
        context.startService(turboChargingIntent);
        
        // Start Charge Control Service
        context.startServiceAsUser(new Intent(context, ChargeControlService.class), UserHandle.CURRENT);
        
        // Start Thermal Management Services
        ThermalUtils.startService(context);
        context.startServiceAsUser(new Intent(context, ThermalTileService.class), UserHandle.CURRENT);

        // Restore Kernel Manager settings
        restoreKernelSettings(context);

        // Try to initialize Dirac if present
        Log.d(TAG, "Received boot completed intent");
        try {
            DiracUtils.getInstance(context);
        } catch (Exception e) {
            Log.d(TAG, "Dirac is not present in system");
        }
    }

    private void restoreKernelSettings(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            KernelManagerUtils kernelUtils = new KernelManagerUtils();

            // Restore CPU Governor
            String savedGovernor = prefs.getString(KEY_CPU_GOVERNOR, null);
            if (savedGovernor != null && !savedGovernor.isEmpty()) {
                kernelUtils.setGovernor(savedGovernor);
                Log.d(TAG, "Restored CPU governor: " + savedGovernor);
            }

            // Restore Efficiency cluster frequencies
            String efficiencyMinFreq = prefs.getString(KEY_EFFICIENCY_MIN_FREQ, null);
            if (efficiencyMinFreq != null && !efficiencyMinFreq.isEmpty()) {
                kernelUtils.setMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, efficiencyMinFreq);
                Log.d(TAG, "Restored efficiency min freq: " + efficiencyMinFreq);
            }

            String efficiencyMaxFreq = prefs.getString(KEY_EFFICIENCY_MAX_FREQ, null);
            if (efficiencyMaxFreq != null && !efficiencyMaxFreq.isEmpty()) {
                kernelUtils.setMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER, efficiencyMaxFreq);
                Log.d(TAG, "Restored efficiency max freq: " + efficiencyMaxFreq);
            }

            // Restore Performance cluster frequencies
            String performanceMinFreq = prefs.getString(KEY_PERFORMANCE_MIN_FREQ, null);
            if (performanceMinFreq != null && !performanceMinFreq.isEmpty()) {
                kernelUtils.setMinFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, performanceMinFreq);
                Log.d(TAG, "Restored performance min freq: " + performanceMinFreq);
            }

            String performanceMaxFreq = prefs.getString(KEY_PERFORMANCE_MAX_FREQ, null);
            if (performanceMaxFreq != null && !performanceMaxFreq.isEmpty()) {
                kernelUtils.setMaxFrequency(KernelManagerUtils.PERFORMANCE_CLUSTER, performanceMaxFreq);
                Log.d(TAG, "Restored performance max freq: " + performanceMaxFreq);
            }

            Log.d(TAG, "Kernel settings restoration completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore kernel settings", e);
        }
    }
}
