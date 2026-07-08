/*
 * Copyright (C) 2025 bezke
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

package org.lineageos.settings.performance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PerformanceBootReceiver extends BroadcastReceiver {

    private static final String TAG = "PerformanceBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, restoring performance profile");

            // NOTE: BootCompletedReceiver already restores the profile at
            // ACTION_LOCKED_BOOT_COMPLETED. If both receivers are registered
            // in the manifest, remove this one to avoid a double apply.
            try {
                PerformanceUtils performanceUtils = new PerformanceUtils(context);
                int currentMode = performanceUtils.getCurrentMode();

                // Restore silently (no haptic feedback at boot)
                boolean success = performanceUtils.setPerformanceMode(currentMode, false);

                Log.d(TAG, "Performance profile restored to: " +
                    performanceUtils.getModeLabel(currentMode) + ", success: " + success);

            } catch (Exception e) {
                Log.e(TAG, "Error restoring performance profile on boot", e);
            }
        }
    }
}
