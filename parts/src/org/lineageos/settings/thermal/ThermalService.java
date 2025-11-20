package org.lineageos.settings.thermal;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class ThermalService extends Service {

    private static final String TAG = "ThermalService";
    private boolean mScreenOn = true;
    private String mCurrentApp = "";
    private ThermalUtils mThermalUtils;
    private boolean mIsReceiverRegistered = false;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                setThermalProfile();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                setThermalProfile();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mThermalUtils = ThermalUtils.getInstance(this);
            ActivityTaskManager.getService().registerTaskStackListener(mTaskListener);
            registerReceiver();
            Log.d(TAG, "ThermalService created successfully");
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register task stack listener", e);
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (mIsReceiverRegistered) {
                unregisterReceiver(mIntentReceiver);
                mIsReceiverRegistered = false;
            }
            ActivityTaskManager.getService().unregisterTaskStackListener(mTaskListener);
            Log.d(TAG, "ThermalService destroyed");
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unregister task stack listener", e);
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ThermalService started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerReceiver() {
        if (!mIsReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                registerReceiver(mIntentReceiver, filter);
                mIsReceiverRegistered = true;
                Log.d(TAG, "Broadcast receiver registered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to register broadcast receiver", e);
            }
        }
    }

    private void setThermalProfile() {
        try {
            if (mThermalUtils == null) {
                mThermalUtils = ThermalUtils.getInstance(this);
            }
            
            if (mScreenOn) {
                mThermalUtils.setThermalProfile(mCurrentApp);
                Log.d(TAG, "Set thermal profile for app: " + mCurrentApp);
            } else {
                mThermalUtils.setDefaultThermalProfile();
                Log.d(TAG, "Set default thermal profile (screen off)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting thermal profile", e);
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            try {
                final ActivityTaskManager.RootTaskInfo focusedTask =
                        ActivityTaskManager.getService().getFocusedRootTaskInfo();
                if (focusedTask != null && focusedTask.topActivity != null) {
                    ComponentName taskComponentName = focusedTask.topActivity;
                    String foregroundApp = taskComponentName.getPackageName();
                    if (!foregroundApp.equals(mCurrentApp)) {
                        mCurrentApp = foregroundApp;
                        setThermalProfile();
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in task listener", e);
            } catch (Exception e) {
                Log.e(TAG, "Error in task listener", e);
            }
        }
    };
}
