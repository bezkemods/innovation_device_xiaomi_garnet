/*
 * Copyright (C) 2025 kenway214
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

package org.lineageos.settings.chargecontrol;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;

public class ChargeControlService extends Service {

    private static final String TAG = "ChargeControlService";
    // Periodic re-check interval (ms) — battery broadcast covers most cases,
    // this is a safety net for missed events.
    private static final long MONITOR_INTERVAL_MS = 60_000L;

    private Handler mHandler;
    private Runnable mMonitorRunnable;
    private BroadcastReceiver mBatteryReceiver;
    private int mLastBatteryLevel = -1;
    private boolean mChargingSuspended = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        registerBatteryReceiver();
        startMonitoring();
    }

    private void registerBatteryReceiver() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                mLastBatteryLevel = (int) ((level / (float) scale) * 100);
                // Sysfs write must not happen on main thread — post to worker
                mHandler.post(() -> new Thread(ChargeControlService.this::checkAndControl).start());
            }
        };
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void startMonitoring() {
        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(ChargeControlService.this::checkAndControl).start();
                mHandler.postDelayed(this, MONITOR_INTERVAL_MS);
            }
        };
        mHandler.post(mMonitorRunnable);
    }

    private void checkAndControl() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean(Constants.KEY_CHARGE_CONTROL, false);

        if (!enabled) {
            if (mChargingSuspended) {
                ChargeControlUtils.setChargingSuspended(false);
                mChargingSuspended = false;
                Log.d(TAG, "Charge control disabled — charging re-enabled");
            }
            return;
        }

        if (mLastBatteryLevel < 0) return; // not yet received a battery update

        int threshold = prefs.getInt(Constants.KEY_STOP_CHARGING, Constants.DEFAULT_STOP_CHARGING);
        boolean shouldSuspend = mLastBatteryLevel >= threshold;

        if (shouldSuspend && !mChargingSuspended) {
            if (ChargeControlUtils.setChargingSuspended(true)) {
                mChargingSuspended = true;
                Log.d(TAG, "Charging suspended at " + mLastBatteryLevel + "% (threshold " + threshold + "%)");
            }
        } else if (!shouldSuspend && mChargingSuspended) {
            if (ChargeControlUtils.setChargingSuspended(false)) {
                mChargingSuspended = false;
                Log.d(TAG, "Charging resumed at " + mLastBatteryLevel + "% (threshold " + threshold + "%)");
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBatteryReceiver != null) unregisterReceiver(mBatteryReceiver);
        if (mHandler != null && mMonitorRunnable != null) {
            mHandler.removeCallbacks(mMonitorRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
