package org.lineageos.settings.logcatviewer;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogcatReader {
    private static final String TAG = "LogcatReader";
    public static final String ACTION_LOGCAT_UPDATE = "org.lineageos.settings.logcatviewer.LOGCAT_UPDATE";
    public static final String EXTRA_LOGCAT_LINE = "logcat_line";
    
    private static Process process;
    private static Thread readerThread;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static Context context;
    
    public static synchronized void start(Context ctx) {
        if (isRunning.get()) {
            Log.d(TAG, "LogcatReader already running");
            return;
        }
        
        context = ctx.getApplicationContext();
        isRunning.set(true);
        
        readerThread = new Thread(LogcatReader::readLogcat, "LogcatReader");
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
        
        Log.d(TAG, "LogcatReader stopped");
    }
    
    public static boolean isRunning() {
        return isRunning.get();
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
                    broadcastLogLine(line);
                }
            }
            
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "Error reading logcat", e);
                // Try to restart after a delay
                try {
                    Thread.sleep(2000);
                    if (isRunning.get()) {
                        readLogcat();
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
            Intent intent = new Intent(ACTION_LOGCAT_UPDATE);
            intent.putExtra(EXTRA_LOGCAT_LINE, line);
            context.sendBroadcast(intent);
        }
    }
    
    public static void clearLogs() {
        new Thread(() -> {
            try {
                Process clearProcess = Runtime.getRuntime().exec("logcat -c");
                clearProcess.waitFor();
                Log.d(TAG, "Logcat cleared");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Failed to clear logcat", e);
            }
        }).start();
    }
}
