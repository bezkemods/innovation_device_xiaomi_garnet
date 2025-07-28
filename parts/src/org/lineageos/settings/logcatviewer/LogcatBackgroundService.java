package org.lineageos.settings.logcatviewer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.lineageos.settings.R;

public class LogcatBackgroundService extends Service {
    private static final String TAG = "LogcatBackgroundService";
    private static final String CHANNEL_ID = "logcat_channel";
    private static final int NOTIFICATION_ID = 101;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted");
                // For system apps, this might not be needed, but good to check
            }
        }
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        if (!LogcatReader.isRunning()) {
            LogcatReader.start(this);
            updateNotification("Logcat logging active");
        }
        
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        LogcatReader.stop();
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Logcat Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background logcat logging service");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        return createNotification("Logcat logging starting...");
    }
    
    private Notification createNotification(String contentText) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Logcat Viewer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_logcat)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
    
    private void updateNotification(String contentText) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(contentText));
        }
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Service should continue running even if task is removed
        Log.d(TAG, "Task removed, but service continues");
        super.onTaskRemoved(rootIntent);
    }
}
