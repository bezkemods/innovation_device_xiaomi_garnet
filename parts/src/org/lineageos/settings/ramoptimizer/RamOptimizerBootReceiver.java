package org.lineageos.settings.ramoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.lineageos.settings.thermal.ThermalUtils;

public class RamOptimizerBootReceiver extends BroadcastReceiver {

    private static final String TAG = "RamOptimizerBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
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
