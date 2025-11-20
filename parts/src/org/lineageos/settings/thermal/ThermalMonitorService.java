package org.lineageos.settings.thermal;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Background service for monitoring thermal states
 * This service can be used by other components (like GpuManager) 
 * to access thermal data without constantly creating new instances
 */
public class ThermalMonitorService extends Service {

    private static final String TAG = "ThermalMonitorService";
    private static final long UPDATE_INTERVAL_MS = 2000; // 2 seconds
    
    private Handler mHandler;
    private Runnable mUpdateRunnable;
    private boolean mIsMonitoring = false;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mHandler = new Handler(Looper.getMainLooper());
            mUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mIsMonitoring) {
                        updateThermalData();
                        mHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                    }
                }
            };
            Log.d(TAG, "ThermalMonitorService created");
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
            
            // This data can be used by other components
            // For now, just log it periodically for debugging
            if (cpuTemp > 0 || gpuTemp > 0 || batteryTemp > 0) {
                Log.v(TAG, String.format("Thermal: CPU=%.1f°C GPU=%.1f°C BAT=%.1f°C",
                        cpuTemp, gpuTemp, batteryTemp));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error updating thermal data", e);
        }
    }
}
