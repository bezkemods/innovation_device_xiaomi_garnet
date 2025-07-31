package org.lineageos.settings.logcatviewer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import org.lineageos.settings.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends Activity {
    private static final String TAG = "LogcatViewer";
    private static final int UPDATE_INTERVAL_MS = 500;
    private static final int MAX_UI_LOGS = 15000; // Increased limit for UI display
    private static final int BATCH_REQUEST_INTERVAL = 1000; // Request batch every 1 second
    
    private ListView logcatListView;
    private EditText filterEditText;
    private Spinner levelSpinner;
    private TextView logCountText;
    private TextView statusText;
    private Button pauseButton;
    private Button autoScrollButton;
    private Button saveButton;
    private Button clearButton;
    
    private LogcatAdapter logcatAdapter;
    private List<LogEntry> allLogEntries;
    private List<LogEntry> filteredEntries;
    private final ConcurrentLinkedQueue<String> pendingLines = new ConcurrentLinkedQueue<>();
    
    private String currentFilter = "";
    private char currentMinLevel = 'V';
    private boolean isAutoScroll = true;
    private boolean isPaused = false;
    private boolean isReceiverRegistered = false;
    
    private Handler uiHandler;
    private Runnable updateRunnable;
    private Runnable batchRequestRunnable;
    
    private long totalLogCount = 0; // Total logs processed by background service
    
    private final BroadcastReceiver logcatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (LogcatBackgroundService.ACTION_LOG_BATCH_RESPONSE.equals(action)) {
                String[] batch = intent.getStringArrayExtra(LogcatBackgroundService.EXTRA_LOG_BATCH);
                if (batch != null && !isPaused) {
                    for (String line : batch) {
                        if (line != null && !line.trim().isEmpty()) {
                            pendingLines.offer(line);
                        }
                    }
                    Log.d(TAG, "Received log batch with " + batch.length + " entries");
                }
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logcat_viewer);
        
        initializeViews();
        setupLists();
        setupSpinner();
        setupFilter();
        setupButtons();
        setupUI();
        
        // Check notification permission for Android 13+
        checkNotificationPermission();
        
        // Register receiver for log batch responses
        registerLogcatReceiver();
        
        // Start background service if not already running
        if (!LogcatBackgroundService.isServiceRunning()) {
            Intent serviceIntent = new Intent(this, LogcatBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
        
        // Get current state from background service
        totalLogCount = LogcatReader.getLogCount();
        
        // Start UI update handler
        uiHandler = new Handler(Looper.getMainLooper());
        startUIUpdates();
        startBatchRequests();
        
        updateStatusText(LogcatBackgroundService.isLogging() ? 
            "Connected - Background logging active" : "Connected - Background logging stopped");
    }
    
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted");
            }
        }
    }
    
    private void registerLogcatReceiver() {
        if (!isReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(LogcatBackgroundService.ACTION_LOG_BATCH_RESPONSE);
                
                // Use LocalBroadcastManager for better security and performance
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(this)
                    .registerReceiver(logcatReceiver, filter);
                    
                isReceiverRegistered = true;
                Log.d(TAG, "Local logcat receiver registered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to register logcat receiver", e);
            }
        }
    }
    
    private void unregisterLogcatReceiver() {
        if (isReceiverRegistered) {
            try {
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(this)
                    .unregisterReceiver(logcatReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Local logcat receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }
    
    private void startBatchRequests() {
        batchRequestRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPaused && LogcatBackgroundService.isServiceRunning()) {
                    requestLogBatch();
                }
                
                if (!isDestroyed()) {
                    uiHandler.postDelayed(this, BATCH_REQUEST_INTERVAL);
                }
            }
        };
        uiHandler.postDelayed(batchRequestRunnable, BATCH_REQUEST_INTERVAL);
    }
    
    private void requestLogBatch() {
        Intent batchIntent = new Intent(this, LogcatBackgroundService.class);
        batchIntent.setAction(LogcatBackgroundService.ACTION_GET_LOG_BATCH);
        startService(batchIntent);
    }
    
    private void initializeViews() {
        logcatListView = findViewById(R.id.logcat_listview);
        filterEditText = findViewById(R.id.filter_edittext);
        levelSpinner = findViewById(R.id.level_spinner);
        logCountText = findViewById(R.id.log_count);
        statusText = findViewById(R.id.status_text);
        
        // Find buttons
        pauseButton = findViewById(R.id.pause_button);
        autoScrollButton = findViewById(R.id.autoscroll_button);
        saveButton = findViewById(R.id.save_button);
        clearButton = findViewById(R.id.clear_button);
    }
    
    private void setupLists() {
        allLogEntries = new ArrayList<>();
        filteredEntries = new ArrayList<>(); 
        logcatAdapter = new LogcatAdapter(this, filteredEntries);
        logcatListView.setAdapter(logcatAdapter);
        
        // Set up long click listener for copying logs to clipboard
        logcatListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filteredEntries.size()) {
                LogEntry entry = filteredEntries.get(position);
                copyToClipboard(entry);
                return true;
            }
            return false;
        });
    }
    
    private void setupSpinner() {
        String[] levels = {"Verbose", "Debug", "Info", "Warning", "Error"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, levels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        levelSpinner.setAdapter(adapter);
        
        levelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                char[] levelChars = {'V', 'D', 'I', 'W', 'E'};
                currentMinLevel = levelChars[position];
                applyFilters();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void setupFilter() {
        filterEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                currentFilter = s.toString().trim();
                logcatAdapter.setHighlightFilter(currentFilter);
                applyFilters();
            }
        });
    }
    
    private void setupButtons() {
        // Pause/Resume button - this only pauses UI updates, not background logging
        pauseButton.setOnClickListener(v -> togglePause());
        
        // Auto scroll button
        autoScrollButton.setOnClickListener(v -> toggleAutoScroll());
        
        // Save button - enable it
        saveButton.setEnabled(true);
        saveButton.setAlpha(1.0f);
        saveButton.setOnClickListener(v -> saveLogcat());
        
        // Clear button
        clearButton.setOnClickListener(v -> clearLogs());
    }
    
    private void setupUI() {
        updateAutoScrollButton();
        updatePauseButton();
        updateLogCount();
    }
    
    private void startUIUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPaused) {
                    processPendingLines();
                }
                
                // Update total log count from background service
                totalLogCount = LogcatReader.getLogCount();
                updateLogCount();
                
                if (!isDestroyed()) {
                    uiHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
        uiHandler.post(updateRunnable);
    }
    
    private void processPendingLines() {
        boolean hasNewLines = false;
        String line;
        int processedCount = 0;
        
        // Process batches to prevent UI blocking
        while ((line = pendingLines.poll()) != null && processedCount < 300) {
            LogEntry entry = LogEntry.parse(line);
            if (entry != null) {
                allLogEntries.add(entry);
                hasNewLines = true;
                processedCount++;
                
                // Keep only recent logs in UI display to prevent UI slowdown
                if (allLogEntries.size() > MAX_UI_LOGS) {
                    // Remove older entries in batches for better performance
                    int removeCount = MAX_UI_LOGS / 10; // Remove 10%
                    for (int i = 0; i < removeCount && !allLogEntries.isEmpty(); i++) {
                        allLogEntries.remove(0);
                    }
                }
            }
        }
        
        if (hasNewLines) {
            applyFilters();
        }
    }
    
    private void applyFilters() {
        filteredEntries.clear();
        
        for (LogEntry entry : allLogEntries) {
            if (entry.matchesFilter(currentFilter, currentMinLevel)) {
                filteredEntries.add(entry);
            }
        }
        
        logcatAdapter.notifyDataSetChanged();
        updateLogCount();
        
        if (isAutoScroll && !filteredEntries.isEmpty()) {
            logcatListView.setSelection(filteredEntries.size() - 1);
        }
    }
    
    private void updateLogCount() {
        String countText = String.format(Locale.US, "UI: %d/%d | Total: %s", 
            filteredEntries.size(), allLogEntries.size(), formatLogCount(totalLogCount));
        logCountText.setText(countText);
    }
    
    private String formatLogCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format(Locale.US, "%.1fK", count / 1000.0);
        } else {
            return String.format(Locale.US, "%.1fM", count / 1000000.0);
        }
    }
    
    private void updateAutoScrollButton() {
        autoScrollButton.setText(isAutoScroll ? "AutoScroll: ON" : "AutoScroll: OFF");
        autoScrollButton.setBackgroundColor(isAutoScroll ? 0xFF4CAF50 : 0xFF757575);
    }
    
    private void updatePauseButton() {
        pauseButton.setText(isPaused ? "RESUME UI" : "PAUSE UI");
        pauseButton.setBackgroundColor(isPaused ? 0xFF4CAF50 : 0xFFFF9800);
    }
    
    private void updateStatusText(String status) {
        statusText.setText(status);
    }
    
    private void togglePause() {
        isPaused = !isPaused;
        updatePauseButton();
        updateStatusText(isPaused ? "UI Paused (background still logging)" : 
            "UI Active (background logging continues)");
    }
    
    private void toggleAutoScroll() {
        isAutoScroll = !isAutoScroll;
        updateAutoScrollButton();
        
        if (isAutoScroll && !filteredEntries.isEmpty()) {
            logcatListView.setSelection(filteredEntries.size() - 1);
        }
    }
    
    private void clearLogs() {
        // Clear UI logs
        allLogEntries.clear();
        filteredEntries.clear();
        pendingLines.clear();
        logcatAdapter.notifyDataSetChanged();
        updateLogCount();
        updateStatusText("UI logs cleared");
        
        // Also clear background logcat
        LogcatReader.clearLogs();
        totalLogCount = 0;
        
        Toast.makeText(this, "All logs cleared (UI and background)", Toast.LENGTH_SHORT).show();
    }
    
    private void saveLogcat() {
        if (allLogEntries.isEmpty()) {
            Toast.makeText(this, "No logs to save in UI buffer", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show immediate feedback
        Toast.makeText(this, "Saving UI logs...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String timestamp = sdf.format(new Date());
                String fileName = "logcat_ui_" + timestamp + ".txt";
                
                File dir = new File(Environment.getExternalStorageDirectory(), "Logcat");
                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                File file = new File(dir, fileName);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    // Save filtered logs if filter is active, otherwise all UI logs
                    List<LogEntry> logsToSave = currentFilter.isEmpty() && currentMinLevel == 'V' 
                        ? allLogEntries : filteredEntries;
                    
                    for (LogEntry entry : logsToSave) {
                        writer.write(entry.rawLine);
                        writer.newLine();
                    }
                    
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            "Saved " + logsToSave.size() + " UI logs to " + fileName, 
                            Toast.LENGTH_LONG).show();
                        updateStatusText("UI logs saved to " + fileName);
                    });
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to save logcat", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to save logs: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_share) {
            shareLogcat();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void shareLogcat() {
        if (filteredEntries.isEmpty()) {
            Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder logText = new StringBuilder();
        int maxLines = Math.min(filteredEntries.size(), 1000); // Limit shared logs
        
        for (int i = filteredEntries.size() - maxLines; i < filteredEntries.size(); i++) {
            if (i >= 0) {
                LogEntry entry = filteredEntries.get(i);
                logText.append(entry.rawLine).append("\n");
            }
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, logText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Logcat Export (" + maxLines + " recent entries)");
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share logcat"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share logs", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyToClipboard(LogEntry entry) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            
            // Create detailed clipboard content
            String clipText = String.format(
                "Time: %s\nLevel: %c\nTag: %s\nPID: %s\nMessage: %s\n\nFull line:\n%s",
                entry.timestamp.isEmpty() ? "N/A" : entry.timestamp,
                entry.level,
                entry.tag.isEmpty() ? "N/A" : entry.tag,
                entry.pid.isEmpty() ? "N/A" : entry.pid,
                entry.message.isEmpty() ? entry.rawLine : entry.message,
                entry.rawLine
            );
            
            ClipData clip = ClipData.newPlainText("Logcat Entry", clipText);
            clipboard.setPrimaryClip(clip);
            
            // Show confirmation with log details
            String toastMsg = String.format("Copied log entry: %c/%s", 
                entry.level, 
                entry.tag.isEmpty() ? "Unknown" : entry.tag);
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy to clipboard", e);
            Toast.makeText(this, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Don't stop anything when activity goes to background
        // Background service continues running
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Re-register receiver if needed
        registerLogcatReceiver();
        
        // Update status based on background service state
        updateStatusText(LogcatBackgroundService.isLogging() ? 
            "Connected - Background logging active" : "Connected - Background logging stopped");
        
        // Get latest count from background service
        totalLogCount = LogcatReader.getLogCount();
        updateLogCount();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop UI updates
        if (uiHandler != null) {
            if (updateRunnable != null) {
                uiHandler.removeCallbacks(updateRunnable);
            }
            if (batchRequestRunnable != null) {
                uiHandler.removeCallbacks(batchRequestRunnable);
            }
        }
        
        // Unregister receiver
        unregisterLogcatReceiver();
        
        // DON'T stop the background service - let it continue running
        
        Log.d(TAG, "MainActivity destroyed - background logging continues");
    }
}
