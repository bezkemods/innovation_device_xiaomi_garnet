package org.lineageos.settings.turbocharging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TurboChargingBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TurboChargingUtil.applyTurboAndSportsSettings(context);
    }
}
