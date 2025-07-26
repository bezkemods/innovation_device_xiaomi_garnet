package org.lineageos.settings.turbocharging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TurboChargingBootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TurboChargingConstants.TAG, "Context or intent is null in TurboChargingBootReceiver");
            return;
        }
        
        String action = intent.getAction();
        Log.d(TurboChargingConstants.TAG, "TurboChargingBootReceiver received: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            
            // Use background thread to avoid blocking main thread
            new Thread(() -> {
                try {
                    // Small delay to ensure system is ready
                    Thread.sleep(1000);
                    
                    // Apply turbo and sports settings
                    TurboChargingUtil.applyTurboAndSportsSettings(context);
                    
                    Log.i(TurboChargingConstants.TAG, "Turbo charging settings applied after boot");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TurboChargingConstants.TAG, "Thread interrupted during boot initialization", e);
                } catch (Exception e) {
                    Log.e(TurboChargingConstants.TAG, "Error applying turbo settings after boot", e);
                }
            }).start();
        }
    }
}
