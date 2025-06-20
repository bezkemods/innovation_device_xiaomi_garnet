package org.lineageos.settings.turbocharging;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.UEventObserver;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class TurboChargingService extends Service {
    private static final String TAG = "TurboChargingService";

    private UEventObserver mObserver;
    private Handler mHandler = new Handler();
    private Runnable mMonitorRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        mObserver = new UEventObserver() {
            @Override
            public void onUEvent(UEvent event) {
                String chargerStatus = event.get("POWER_SUPPLY_ONLINE");
                if (chargerStatus != null && chargerStatus.equals("1")) {
                    TurboChargingUtil.applyTurboAndSportsSettings(TurboChargingService.this);
                }
            }
        };
        mObserver.startObserving("DEVPATH=/sys/class/power_supply/usb");
        TurboChargingUtil.applyTurboAndSportsSettings(this);
        startMonitoring();
    }

    private void startMonitoring() {
        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                TurboChargingUtil.applyTurboAndSportsSettings(TurboChargingService.this);
                mHandler.postDelayed(this, 5000);
            }
        };
        mHandler.post(mMonitorRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mObserver.stopObserving();
        mHandler.removeCallbacks(mMonitorRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
