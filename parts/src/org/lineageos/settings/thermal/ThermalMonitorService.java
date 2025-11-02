package org.lineageos.settings.thermal;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;

public class ThermalMonitorService extends Service {
    private static final String TAG = "ThermalMonitorService";
    private static final String THERMAL_ZONE_PATH = "/sys/class/thermal/thermal_zone0/temp";
    private Handler handler = new Handler();
    private Runnable monitorRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                int temp = readThermal();
                Log.d(TAG, "Current device temp: " + temp + " °C");
                // TODO: notification/toast, performance fallback, etc.
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(monitorRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int readThermal() {
        try (BufferedReader br = new BufferedReader(new FileReader(THERMAL_ZONE_PATH))) {
            String line = br.readLine();
            return Integer.parseInt(line.trim()) / 1000; // milliCelsius -> Celsius
        } catch (Exception e) {
            Log.e(TAG, "Error reading thermal zone", e);
            return -1;
        }
    }
}