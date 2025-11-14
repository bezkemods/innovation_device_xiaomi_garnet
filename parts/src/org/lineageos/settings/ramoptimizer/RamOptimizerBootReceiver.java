/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.ramoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Boot receiver to restore RAM Optimizer settings after device boot
 */
public class RamOptimizerBootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "RamOptimizerBoot";
    private static final int RESTORE_DELAY_MS = 10000; // 10 seconds delay
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) {
            return;
        }
        
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && 
            !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        
        Log.i(TAG, "Boot completed, scheduling RAM Optimizer restore");
        
        // Check if supported
        if (!RamOptimizerUtils.isSupported()) {
            Log.w(TAG, "RAM Optimizer not supported on this device");
            return;
        }
        
        // Restore settings with delay to allow system to stabilize
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            try {
                Log.i(TAG, "Restoring RAM Optimizer settings...");
                RamOptimizerUtils.restorePreferences(context);
                Log.i(TAG, "RAM Optimizer settings restored successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore RAM Optimizer settings", e);
            }
        }, RESTORE_DELAY_MS);
    }
}