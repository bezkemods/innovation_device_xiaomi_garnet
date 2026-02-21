package org.lineageos.settings.thermal;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Background service for monitoring thermal states.
 * Uses a HandlerThread so file I/O never runs on the main thread.
 */
public class ThermalMonitorService extends Service {

    private static final String TAG = "ThermalMonitorService";
    private static final long UPDATE_INTERVAL_MS = 5000; // 5 seconds

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Runnable mUpdateRunnable;
    private volatile boolean mIsMonitoring = false;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Background thread — thermal sysfs reads must NOT run on the main thread
            mHandlerThread = new HandlerThread("ThermalMonitor",
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());

            mUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mIsMonitoring) {
                        updateThermalData();
                        mHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                    }
                }
            };
            Log.d(TAG, "ThermalMonitorService created (background thread)");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mIsMonitoring) {
            startMonitoring();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        super.onDestroy();
        Log.d(TAG, "ThermalMonitorService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startMonitoring() {
        mIsMonitoring = true;
        mHandler.post(mUpdateRunnable);
        Log.d(TAG, "Thermal monitoring started");
    }

    private void stopMonitoring() {
        mIsMonitoring = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateRunnable);
        }
        Log.d(TAG, "Thermal monitoring stopped");
    }

    private void updateThermalData() {
        try {
            // Read current thermal data
            float cpuTemp = ThermalUtils.getCpuTemp();
            float gpuTemp = ThermalUtils.getGpuTemp();
            float batteryTemp = ThermalUtils.getBatteryTemp();
            
            // Battery optimization: Only log if temps are significant
            // This reduces log I/O and wake locks
            if (cpuTemp > 45 || gpuTemp > 45 || batteryTemp > 35) {
                Log.v(TAG, String.format("Thermal: CPU=%.1f°C GPU=%.1f°C BAT=%.1f°C",
                        cpuTemp, gpuTemp, batteryTemp));
            }
        } catch (Exception e) {
            // Battery optimization: Silent error handling
            // Avoid excessive logging that causes wake locks
        }
    }
}
