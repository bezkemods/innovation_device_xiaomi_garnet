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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.lineageos.settings.R;

import java.util.concurrent.ConcurrentLinkedQueue;

public class LogcatBackgroundService extends Service implements LogcatReader.LogcatCallback {
    private static final String TAG = "LogcatBackgroundService";
    private static final String CHANNEL_ID = "logcat_channel";
    private static final int NOTIFICATION_ID = 101;
    
    // Actions for notification buttons
    public static final String ACTION_START_LOGGING = "org.lineageos.settings.logcatviewer.START_LOGGING";
    public static final String ACTION_STOP_LOGGING = "org.lineageos.settings.logcatviewer.STOP_LOGGING";
    public static final String ACTION_OPEN_VIEWER = "org.lineageos.settings.logcatviewer.OPEN_VIEWER";
    
    // Internal actions for MainActivity communication
    public static final String ACTION_GET_LOG_BATCH = "org.lineageos.settings.logcatviewer.GET_LOG_BATCH";
    public static final String ACTION_LOG_BATCH_RESPONSE = "org.lineageos.settings.logcatviewer.LOG_BATCH_RESPONSE";
    public static final String EXTRA_LOG_BATCH = "log_batch";
    
    private static boolean isServiceRunning = false;
    private static volatile long logCount = 0;
    private static boolean isLogging = false;
    
    private Handler uiHandler;
    private final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_SIZE = 5000;
    
    // Notification update throttling
    private long lastNotificationUpdate = 0;
    private static final long NOTIFICATION_UPDATE_INTERVAL = 2000; // 2 seconds

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        isServiceRunning = true;
        uiHandler = new Handler(Looper.getMainLooper());

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
            } else if (ACTION_GET_LOG_BATCH.equals(action)) {
                sendLogBatch();
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
                updateNotificationThrottled();
            }
        }

        return START_STICKY; // Restart if killed
    }

    private void startLogging() {
        if (!LogcatReader.isRunning()) {
            // Set this service as the callback for LogcatReader
            LogcatReader.start(this, this);
            isLogging = true;
            logCount = 0; // Reset count when starting fresh
            logBuffer.clear(); // Clear buffer
            updateNotificationThrottled();
            Log.d(TAG, "Started logcat logging with direct callback");
        } else {
            // Already running, just sync state and set callback
            LogcatReader.setCallback(this);
            isLogging = true;
            logCount = LogcatReader.getLogCount();
            updateNotificationThrottled();
            Log.d(TAG, "Logcat already running, synced state and set callback");
        }
    }

    private void stopLogging() {
        if (LogcatReader.isRunning()) {
            LogcatReader.stop();
            isLogging = false;
            updateNotificationThrottled();
            Log.d(TAG, "Logcat logging stopped");
        }
    }

    private void openViewer() {
        Intent viewerIntent = new Intent(this, MainActivity.class);
        viewerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(viewerIntent);
    }
    
    private void sendLogBatch() {
        // Send available logs to MainActivity
        if (!logBuffer.isEmpty()) {
            String[] batch = logBuffer.toArray(new String[0]);
            
            Intent responseIntent = new Intent(ACTION_LOG_BATCH_RESPONSE);
            responseIntent.putExtra(EXTRA_LOG_BATCH, batch);
            responseIntent.setPackage(getPackageName());
            
            // Send as local broadcast
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .sendBroadcast(responseIntent);
                
            Log.d(TAG, "Sent log batch with " + batch.length + " entries");
        }
    }

    // LogcatReader.LogcatCallback implementation
    @Override
    public void onLogLine(String line) {
        // Add to buffer for MainActivity
        logBuffer.offer(line);
        
        // Maintain buffer size
        while (logBuffer.size() > MAX_BUFFER_SIZE) {
            logBuffer.poll();
        }
        
        // Increment count
        logCount++;
        
        // Update notification periodically
        if (logCount % 100 == 0) {
            updateNotificationThrottled();
        }
    }
    
    @Override
    public void onLogCountUpdate(long count) {
        logCount = count;
        updateNotificationThrottled();
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

    private void updateNotificationThrottled() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationUpdate > NOTIFICATION_UPDATE_INTERVAL) {
            lastNotificationUpdate = currentTime;
            uiHandler.post(this::updateNotification);
        }
    }

    public void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
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

    // Static methods for external access
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
