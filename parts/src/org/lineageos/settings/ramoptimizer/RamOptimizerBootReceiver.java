/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.ramoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Boot receiver to restore RAM Optimizer settings after device boot
 */
public class RamOptimizerBootReceiver extends BroadcastReceiver {
    private static final String TAG = "RamOptimizerBoot";
    // Delay increased slightly to ensure system is fully settled
    private static final int RESTORE_DELAY_MS = 10000; 

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
            !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }

        Log.i(TAG, "Boot completed, scheduling RAM Optimizer restore");

        if (!RamOptimizerUtils.isSupported()) {
            Log.w(TAG, "RAM Optimizer not supported on this device");
            return;
        }

        // Use Handler to delay, but execute heavy work on background thread
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // CRITICAL FIX: Run root commands on background thread to avoid boot lag/ANR
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    Log.i(TAG, "Restoring RAM Optimizer settings...");
                    RamOptimizerUtils.restorePreferences(context);
                    Log.i(TAG, "RAM Optimizer settings restored successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restore RAM Optimizer settings", e);
                } finally {
                    executor.shutdown();
                }
            });
        }, RESTORE_DELAY_MS);
    }
}
