package org.lineageos.settings.ramoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.util.Log;
import org.lineageos.settings.thermal.ThermalUtils;

public class RamOptimizerBootReceiver extends BroadcastReceiver {

    private static final String TAG = "RamOptimizerBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // restorePreferences() executes root commands — must run off main thread
            HandlerThread thread = new HandlerThread("RamOptimizerBoot");
            thread.start();
            new android.os.Handler(thread.getLooper()).post(() -> {
                try {
                    Log.d(TAG, "Boot completed, restoring RAM optimizer preferences");
                    RamOptimizerUtils.restorePreferences(context);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring RAM preferences on boot", e);
                } finally {
                    thread.quitSafely();
                }
            });

            // Thermal service start is lightweight, keep on calling thread
            try {
                Log.d(TAG, "Boot completed, starting thermal service");
                ThermalUtils thermalUtils = ThermalUtils.getInstance(context);
                if (thermalUtils.isEnabled()) {
                    thermalUtils.startService();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting thermal service on boot", e);
            }
        }
    }
}
