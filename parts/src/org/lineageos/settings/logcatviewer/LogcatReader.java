package org.lineageos.settings.logcatviewer;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LogcatReader {
    private static final String TAG = "LogcatReader";
    public static final String ACTION_LOGCAT_UPDATE = "org.lineageos.settings.logcatviewer.LOGCAT_UPDATE";
    public static final String ACTION_LOG_COUNT_UPDATE = "org.lineageos.settings.logcatviewer.LOG_COUNT_UPDATE";
    public static final String EXTRA_LOGCAT_LINE = "logcat_line";
    public static final String EXTRA_LOG_COUNT = "log_count";
    
    private static Process process;
    private static Thread readerThread;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final AtomicLong logCount = new AtomicLong(0);
    private static Context context;
    
    public static synchronized void start(Context ctx) {
        if (isRunning.get()) {
            Log.d(TAG, "LogcatReader already running");
            return;
        }
        
        // Always use application context to prevent memory leaks
        context = ctx.getApplicationContext();
        isRunning.set(true);
        
        readerThread = new Thread(LogcatReader::readLogcat, "LogcatReader");
        readerThread.setDaemon(true); // Make it a daemon thread so it doesn't prevent app from closing
        readerThread.start();
        Log.d(TAG, "LogcatReader started");
    }
    
    public static synchronized void stop() {
        if (!isRunning.get()) {
            return;
        }
        
        Log.d(TAG, "Stopping LogcatReader");
        isRunning.set(false);
        
        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(1000); // Wait max 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }
        
        if (process != null) {
            process.destroy();
            process = null;
        }
        
        // Clear context reference to prevent memory leaks
        context = null;
        
        Log.d(TAG, "LogcatReader stopped");
    }
    
    public static boolean isRunning() {
        return isRunning.get();
    }
    
    public static long getLogCount() {
        return logCount.get();
    }
    
    public static void resetLogCount() {
        logCount.set(0);
        broadcastLogCount();
    }
    
    private static void readLogcat() {
        BufferedReader reader = null;
        try {
            // Clear existing logs and start fresh
            process = Runtime.getRuntime().exec("logcat -c");
            process.waitFor();
            
            // Start reading new logs with time format
            process = Runtime.getRuntime().exec("logcat -v time");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            while (isRunning.get() && (line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    long currentCount = logCount.incrementAndGet();
                    broadcastLogLine(line);
                    
                    // Broadcast count update every 100 logs to avoid too many broadcasts
                    if (currentCount % 100 == 0) {
                        broadcastLogCount();
                    }
                }
            }
            
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "Error reading logcat", e);
                // Try to restart after a delay
                try {
                    Thread.sleep(2000);
                    if (isRunning.get()) {
                        readLogcat(); // Recursive restart
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader", e);
                }
            }
        }
    }
    
    private static void broadcastLogLine(String line) {
        if (context != null) {
            try {
                Intent intent = new Intent(ACTION_LOGCAT_UPDATE);
                intent.putExtra(EXTRA_LOGCAT_LINE, line);
                context.sendBroadcast(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting log line", e);
            }
        }
    }
    
    private static void broadcastLogCount() {
        if (context != null) {
            try {
                Intent intent = new Intent(ACTION_LOG_COUNT_UPDATE);
                intent.putExtra(EXTRA_LOG_COUNT, logCount.get());
                context.sendBroadcast(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting log count", e);
            }
        }
    }
    
    public static void clearLogs() {
        new Thread(() -> {
            try {
                Process clearProcess = Runtime.getRuntime().exec("logcat -c");
                clearProcess.waitFor();
                resetLogCount(); // Reset our internal counter
                Log.d(TAG, "Logcat cleared");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Failed to clear logcat", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }
}
