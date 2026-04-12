/*
 * Copyright (C) 2025 KamiKaonashi
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

package org.lineageos.settings.resolution;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class ResolutionService extends Service {

    private static final String TAG   = "ResolutionService";
    private static final boolean DEBUG = false;

    private String              mPreviousApp = "";
    private ResolutionUtils     mResolutionUtils;
    private IActivityTaskManager mActivityTaskManager;

    // Reset "previous app" state on screen-off so the resolution is correctly
    // re-applied when the user returns to any app after unlock.
    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mPreviousApp = "";
            }
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate");

        mResolutionUtils = new ResolutionUtils(this);

        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register TaskStackListener", e);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenReceiver, filter);

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (RemoteException e) {
            // ignore
        }
        unregisterReceiver(mScreenReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            try {
                final RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
                if (info == null || info.topActivity == null) return;

                final String foregroundApp = info.topActivity.getPackageName();

                // If app is NOT in any per-app list, restore the global baseline.
                // This must run before the foregroundApp equality check so that
                // switching from a per-app-overridden app to an unlisted one
                // always triggers a restore.
                if (!ResolutionUtils.isAppInList) {
                    mResolutionUtils.restoreDefaultResolution();
                }

                if (!foregroundApp.equals(mPreviousApp)) {
                    mResolutionUtils.setResolution(foregroundApp);
                    mPreviousApp = foregroundApp;
                }
            } catch (Exception e) {
                Log.e(TAG, "onTaskStackChanged error", e);
            }
        }
    };
}
