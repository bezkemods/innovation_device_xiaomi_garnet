package org.lineageos.settings.turbocharging;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.UEventObserver;
import android.util.Log;

public class TurboChargingService extends Service {

    private UEventObserver mObserver;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Runnable mMonitorRunnable;
    private volatile boolean mServiceRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            initializeService();
            Log.i(TurboChargingConstants.TAG, "TurboChargingService created successfully");
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error creating TurboChargingService", e);
            stopSelf();
        }
    }

    private void initializeService() {
        mServiceRunning = true;
        
        // Create dedicated thread for background operations
        mHandlerThread = new HandlerThread("TurboChargingThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        
        // Initialize UEvent observer
        initializeUEventObserver();
        
        // Apply initial settings
        TurboChargingUtil.applyTurboAndSportsSettings(this);
        
        // Start monitoring
        startMonitoring();
    }

    private void initializeUEventObserver() {
        mObserver = new UEventObserver() {
            @Override
            public void onUEvent(UEvent event) {
                if (!mServiceRunning) return;
                
                try {
                    String chargerStatus = event.get("POWER_SUPPLY_ONLINE");
                    if ("1".equals(chargerStatus)) {
                        Log.d(TurboChargingConstants.TAG, "Charger connected, applying turbo settings");
                        TurboChargingUtil.applyTurboAndSportsSettings(TurboChargingService.this);
                    }
                } catch (Exception e) {
                    Log.e(TurboChargingConstants.TAG, "Error handling UEvent", e);
                }
            }
        };
        
        try {
            mObserver.startObserving(TurboChargingConstants.USB_POWER_SUPPLY_PATH);
            Log.d(TurboChargingConstants.TAG, "UEvent observer started");
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Failed to start UEvent observer", e);
        }
    }

    private void startMonitoring() {
        if (mHandler == null) return;
        
        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mServiceRunning) return;
                
                try {
                    TurboChargingUtil.applyTurboAndSportsSettings(TurboChargingService.this);
                } catch (Exception e) {
                    Log.e(TurboChargingConstants.TAG, "Error in monitoring runnable", e);
                }
                
                if (mServiceRunning && mHandler != null) {
                    mHandler.postDelayed(this, TurboChargingConstants.MONITORING_INTERVAL_MS);
                }
            }
        };
        
        mHandler.post(mMonitorRunnable);
        Log.d(TurboChargingConstants.TAG, "Monitoring started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TurboChargingConstants.TAG, "TurboChargingService onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TurboChargingConstants.TAG, "TurboChargingService destroying");
        
        mServiceRunning = false;
        
        // Stop UEvent observer
        if (mObserver != null) {
            try {
                mObserver.stopObserving();
                Log.d(TurboChargingConstants.TAG, "UEvent observer stopped");
            } catch (Exception e) {
                Log.e(TurboChargingConstants.TAG, "Error stopping UEvent observer", e);
            }
            mObserver = null;
        }
        
        // Clean up handler and thread
        if (mHandler != null && mMonitorRunnable != null) {
            mHandler.removeCallbacks(mMonitorRunnable);
        }
        
        if (mHandlerThread != null) {
            try {
                mHandlerThread.quitSafely();
                mHandlerThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Log.w(TurboChargingConstants.TAG, "Thread interrupted during shutdown", e);
                Thread.currentThread().interrupt();
            }
            mHandlerThread = null;
        }
        
        mHandler = null;
        mMonitorRunnable = null;
        
        super.onDestroy();
        Log.i(TurboChargingConstants.TAG, "TurboChargingService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
