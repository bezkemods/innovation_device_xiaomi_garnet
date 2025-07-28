package org.lineageos.settings.logcatviewer;

import android.app.Activity;
import android.content.BroadcastReceiver;
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
    private static final int MAX_LOG_LINES = 5000;
    private static final int UPDATE_INTERVAL_MS = 100;
    
    private ListView logcatListView;
    private EditText filterEditText;
    private Spinner levelSpinner;
    private TextView logCountText;
    private TextView statusText;
    private TextView autoScrollStatusText;
    
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
    private Menu currentMenu;
    
    private final BroadcastReceiver logcatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LogcatReader.ACTION_LOGCAT_UPDATE.equals(intent.getAction())) {
                String line = intent.getStringExtra(LogcatReader.EXTRA_LOGCAT_LINE);
                if (line != null && !isPaused) {
                    pendingLines.offer(line);
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
        setupUI();
        
        // Check notification permission for Android 13+
        checkNotificationPermission();
        
        // Register receiver for logcat updates
        registerLogcatReceiver();
        
        // Start background service
        Intent serviceIntent = new Intent(this, LogcatBackgroundService.class);
        startService(serviceIntent);
        
        // Start UI update handler
        uiHandler = new Handler(Looper.getMainLooper());
        startUIUpdates();
        
        updateStatusText("Started - Capturing logs...");
    }
    
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted");
                // For system apps, this might not be needed, but good to check
            }
        }
    }
    
    private void registerLogcatReceiver() {
        if (!isReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter(LogcatReader.ACTION_LOGCAT_UPDATE);
                ContextCompat.registerReceiver(this, logcatReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
                isReceiverRegistered = true;
                Log.d(TAG, "Logcat receiver registered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to register logcat receiver", e);
            }
        }
    }
    
    private void unregisterLogcatReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(logcatReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Logcat receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }
    
    private void initializeViews() {
        logcatListView = findViewById(R.id.logcat_listview);
        filterEditText = findViewById(R.id.filter_edittext);
        levelSpinner = findViewById(R.id.level_spinner);
        logCountText = findViewById(R.id.log_count);
        statusText = findViewById(R.id.status_text);
        autoScrollStatusText = findViewById(R.id.autoscroll_status);
    }
    
    private void setupLists() {
        allLogEntries = new ArrayList<>();
        filteredEntries = new ArrayList<>();
        logcatAdapter = new LogcatAdapter(this, filteredEntries);
        logcatListView.setAdapter(logcatAdapter);
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
    
    private void setupUI() {
        updateAutoScrollStatus();
        updateLogCount();
    }
    
    private void startUIUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                processPendingLines();
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
        
        while ((line = pendingLines.poll()) != null) {
            LogEntry entry = LogEntry.parse(line);
            if (entry != null) {
                allLogEntries.add(entry);
                hasNewLines = true;
                
                // Keep only recent logs to prevent memory issues
                if (allLogEntries.size() > MAX_LOG_LINES) {
                    allLogEntries.remove(0);
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
        String countText = String.format(Locale.US, "%d/%d logs", 
            filteredEntries.size(), allLogEntries.size());
        logCountText.setText(countText);
    }
    
    private void updateAutoScrollStatus() {
        autoScrollStatusText.setText(isAutoScroll ? "AutoScroll: ON" : "AutoScroll: OFF");
    }
    
    private void updateStatusText(String status) {
        statusText.setText(status);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        this.currentMenu = menu;
        updateMenuItems(menu);
        return true;
    }
    
    private void updateMenuItems(Menu menu) {
        if (menu == null) return;
        
        MenuItem autoScrollItem = menu.findItem(R.id.action_autoscroll);
        if (autoScrollItem != null) {
            autoScrollItem.setTitle(isAutoScroll ? 
                R.string.logcat_autoscroll_on : R.string.logcat_autoscroll_off);
        }
        
        MenuItem pauseItem = menu.findItem(R.id.action_pause);
        if (pauseItem != null) {
            pauseItem.setTitle(isPaused ? 
                R.string.logcat_resume : R.string.logcat_pause);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_save_log) {
            saveLogcat();
            return true;
        } else if (id == R.id.action_autoscroll) {
            toggleAutoScroll();
            updateMenuItems(currentMenu);
            return true;
        } else if (id == R.id.action_clear) {
            clearLogs();
            return true;
        } else if (id == R.id.action_pause) {
            togglePause();
            updateMenuItems(currentMenu);
            return true;
        } else if (id == R.id.action_share) {
            shareLogcat();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void togglePause() {
        isPaused = !isPaused;
        updateStatusText(isPaused ? "Paused" : "Running");
    }
    
    private void toggleAutoScroll() {
        isAutoScroll = !isAutoScroll;
        updateAutoScrollStatus();
        
        if (isAutoScroll && !filteredEntries.isEmpty()) {
            logcatListView.setSelection(filteredEntries.size() - 1);
        }
    }
    
    private void clearLogs() {
        allLogEntries.clear();
        filteredEntries.clear();
        pendingLines.clear();
        logcatAdapter.notifyDataSetChanged();
        updateLogCount();
        updateStatusText("Logs cleared");
        
        // Also clear system logcat
        LogcatReader.clearLogs();
    }
    
    private void shareLogcat() {
        if (allLogEntries.isEmpty()) {
            Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder logText = new StringBuilder();
        for (LogEntry entry : filteredEntries) {
            logText.append(entry.rawLine).append("\n");
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, logText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Logcat Export");
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Share logcat"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share logs", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void saveLogcat() {
        if (allLogEntries.isEmpty()) {
            Toast.makeText(this, "No logs to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String timestamp = sdf.format(new Date());
                String fileName = "logcat_" + timestamp + ".txt";
                
                File dir = new File(Environment.getExternalStorageDirectory(), "Logcat");
                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                File file = new File(dir, fileName);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    for (LogEntry entry : allLogEntries) {
                        writer.write(entry.rawLine);
                        writer.newLine();
                    }
                    
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            "Saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        updateStatusText("Logs saved to " + fileName);
                    });
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to save logcat", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to save logs", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        updateStatusText("Paused");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        updateStatusText("Running");
        
        // Re-register receiver if needed
        registerLogcatReceiver();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop UI updates
        if (uiHandler != null && updateRunnable != null) {
            uiHandler.removeCallbacks(updateRunnable);
        }
        
        // Unregister receiver
        unregisterLogcatReceiver();
        
        Log.d(TAG, "MainActivity destroyed");
    }
}
