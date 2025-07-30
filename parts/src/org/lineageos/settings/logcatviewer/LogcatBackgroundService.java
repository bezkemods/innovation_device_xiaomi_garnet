package org.lineageos.settings.logcatviewer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.lineageos.settings.R;

public class LogcatBackgroundService extends Service {
    private static final String TAG = "LogcatBackgroundService";
    private static final String CHANNEL_ID = "logcat_channel";
    private static final int NOTIFICATION_ID = 101;
    
    // Actions for notification buttons
    public static final String ACTION_START_LOGGING = "org.lineageos.settings.logcatviewer.START_LOGGING";
    public static final String ACTION_STOP_LOGGING = "org.lineageos.settings.logcatviewer.STOP_LOGGING";
    public static final String ACTION_OPEN_VIEWER = "org.lineageos.settings.logcatviewer.OPEN_VIEWER";
    
    private static boolean isServiceRunning = false;
    private static volatile long logCount = 0;
    private static boolean isLogging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        isServiceRunning = true;

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted");
            }
        }

        createNotificationChannel();
        startForegroundWithNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started with intent: " + (intent != null ? intent.getAction() : "null"));

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_LOGGING.equals(action)) {
                startLogging();
            } else if (ACTION_STOP_LOGGING.equals(action)) {
                stopLogging();
            } else if (ACTION_OPEN_VIEWER.equals(action)) {
                openViewer();
            }
        } else {
            // Default behavior - only start logging if not already running and auto-start is enabled
            if (!LogcatReader.isRunning()) {
                // Check if we should auto-start
                boolean autoStart = LogcatSettingsPreference.isAutoStartEnabled(this);
                if (autoStart) {
                    startLogging();
                }
            } else {
                // LogcatReader is already running, sync our state
                isLogging = true;
                logCount = LogcatReader.getLogCount();
                updateNotification();
            }
        }

        return START_STICKY; // Restart if killed
    }

    private void startLogging() {
        if (!LogcatReader.isRunning()) {
            LogcatReader.start(this);
            isLogging = true;
            logCount = 0; // Reset count when starting fresh
            updateNotification();
            Log.d(TAG, "Logcat logging started");
        } else {
            // Already running, just sync state
            isLogging = true;
            logCount = LogcatReader.getLogCount();
            updateNotification();
            Log.d(TAG, "Logcat already running, synced state");
        }
    }

    private void stopLogging() {
        if (LogcatReader.isRunning()) {
            LogcatReader.stop();
            isLogging = false;
            updateNotification();
            Log.d(TAG, "Logcat logging stopped");
        }
    }

    private void openViewer() {
        Intent viewerIntent = new Intent(this, MainActivity.class);
        viewerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(viewerIntent);
    }

    private void startForegroundWithNotification() {
        Notification notification = createNotification();

        // Start foreground service with proper type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        isServiceRunning = false;
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
            channel.setDescription("Background logcat logging service with controls");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // Create custom notification layout
        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_logcat);
        
        // Update the content based on current state
        String statusText = isLogging ? "Logging: " + formatLogCount(logCount) + " entries" : "Stopped";
        notificationLayout.setTextViewText(R.id.notification_status, statusText);
        
        // Set button text based on current state
        notificationLayout.setTextViewText(R.id.notification_toggle_button, 
            isLogging ? "STOP" : "START");

        // Create pending intents for buttons
        PendingIntent toggleIntent = PendingIntent.getService(
                this, 0,
                new Intent(this, LogcatBackgroundService.class)
                        .setAction(isLogging ? ACTION_STOP_LOGGING : ACTION_START_LOGGING),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        PendingIntent openIntent = PendingIntent.getService(
                this, 1,
                new Intent(this, LogcatBackgroundService.class)
                        .setAction(ACTION_OPEN_VIEWER),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Set click listeners
        notificationLayout.setOnClickPendingIntent(R.id.notification_toggle_button, toggleIntent);
        notificationLayout.setOnClickPendingIntent(R.id.notification_open_button, openIntent);

        // Create the main notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Logcat Viewer")
                .setSmallIcon(R.drawable.ic_logcat)
                .setCustomContentView(notificationLayout)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }

    public void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            // Update log count from LogcatReader
            logCount = LogcatReader.getLogCount();
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    private String formatLogCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format("%.1fK", count / 1000.0);
        } else {
            return String.format("%.1fM", count / 1000000.0);
        }
    }

    // Static methods to update log count from LogcatReader
    public static void incrementLogCount() {
        logCount++;
        // Update notification every 100 logs to avoid too frequent updates
        if ((logCount % 100) == 0) {
            updateNotificationStatic();
        }
    }

    public static void resetLogCount() {
        logCount = 0;
        updateNotificationStatic();
    }

    private static void updateNotificationStatic() {
        // We can't directly update notification from static context
        // LogcatReader will handle this through broadcasts
    }

    public static boolean isServiceRunning() {
        return isServiceRunning;
    }

    public static boolean isLogging() {
        return isLogging;
    }

    public static long getLogCount() {
        return logCount;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Service should continue running even if task is removed
        Log.d(TAG, "Task removed, but service continues");
        super.onTaskRemoved(rootIntent);
    }
}
