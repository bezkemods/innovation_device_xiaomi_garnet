package org.lineageos.settings.ramoptimizer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RamOptimizerFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "RamOptimizerFragment";

    // Keys
    private static final String STATS_CATEGORY_KEY = "ram_stats_category";
    private static final String STATS_KEY = "ram_stats";
    private static final String ZRAM_ENABLE_KEY = "zram_enable";
    private static final String ZRAM_SIZE_KEY = "zram_size";
    private static final String ZRAM_SWAPPINESS_KEY = "zram_swappiness";
    private static final String ZRAM_ALGO_KEY = "zram_compression_algorithm";
    private static final String LMK_PROFILE_KEY = "lmk_profile";
    private static final String APP_HIBERNATION_KEY = "app_hibernation";
    private static final String IO_SCHEDULER_KEY = "io_scheduler";

    // Storage Keys
    private static final String STORAGE_STATS_KEY = "storage_stats";
    private static final String CLEAN_APP_CACHE_KEY = "clean_app_cache";
    private static final String CLEAN_SYSTEM_CACHE_KEY = "clean_system_cache";
    private static final String CLEAN_THUMBNAILS_KEY = "clean_thumbnails";
    private static final String CLEAN_DOWNLOADS_KEY = "clean_downloads";
    private static final String CLEAN_TEMP_FILES_KEY = "clean_temp_files";
    private static final String CLEAN_LOG_FILES_KEY = "clean_log_files";
    private static final String CLEAN_APK_FILES_KEY = "clean_apk_files";
    private static final String CLEAN_EMPTY_FOLDERS_KEY = "clean_empty_folders";
    private static final String CLEAN_DUPLICATES_KEY = "clean_duplicates";
    private static final String CLEAN_ALL_KEY = "clean_all";

    // Advanced
    private static final String AUTO_CLEAN_KEY = "auto_clean_enabled";
    private static final String AUTO_CLEAN_INTERVAL_KEY = "auto_clean_interval";
    private static final String ANALYZE_STORAGE_KEY = "analyze_storage";

    // UI Elements
    private Preference mStatsPreference;
    private SwitchPreference mZramEnablePref;
    private SeekBarPreference mZramSizePref;
    private SeekBarPreference mZramSwappinessPref;
    private ListPreference mZramAlgoPref;
    private ListPreference mLmkProfilePref;
    private SwitchPreference mAppHibernationPref;
    private ListPreference mIoSchedulerPref;

    // Storage UI
    private Preference mStorageStatsPref;
    private Preference mCleanAppCachePref;
    private Preference mCleanSystemCachePref;
    private Preference mCleanThumbnailsPref;
    private Preference mCleanDownloadsPref;
    private Preference mCleanTempFilesPref;
    private Preference mCleanLogFilesPref;
    private Preference mCleanApkFilesPref;
    private Preference mCleanEmptyFoldersPref;
    private Preference mCleanDuplicatesPref;
    private Preference mCleanAllPref;

    private SwitchPreference mAutoCleanPref;
    private ListPreference mAutoCleanIntervalPref;
    private Preference mAnalyzeStoragePref;

    // Threading
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mUpdateStatsRunnable;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.ram_optimizer_settings);

        if (!RamOptimizerUtils.isSupported()) {
            showErrorAndFinish("RAM Optimizer not supported on this device");
            return;
        }

        initializePreferences();
        setupUpdateTask();
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void initializePreferences() {
        // RAM Stats
        PreferenceCategory statsCategory = findPreference(STATS_CATEGORY_KEY);
        if (statsCategory != null) {
            mStatsPreference = new Preference(requireContext());
            mStatsPreference.setKey(STATS_KEY);
            mStatsPreference.setTitle("System Status");
            mStatsPreference.setSummary("Loading statistics...");
            mStatsPreference.setSelectable(false);
            statsCategory.addPreference(mStatsPreference);
        }

        // Initialize standard preferences
        mZramEnablePref = findPreference(ZRAM_ENABLE_KEY);
        if (mZramEnablePref != null) mZramEnablePref.setOnPreferenceChangeListener(this);

        mZramSizePref = findPreference(ZRAM_SIZE_KEY);
        if (mZramSizePref != null) {
            mZramSizePref.setMin(512);
            mZramSizePref.setMax(8192);
            mZramSizePref.setUpdatesContinuously(false);
            mZramSizePref.setOnPreferenceChangeListener(this);
        }

        mZramSwappinessPref = findPreference(ZRAM_SWAPPINESS_KEY);
        if (mZramSwappinessPref != null) {
            mZramSwappinessPref.setMin(0);
            mZramSwappinessPref.setMax(200);
            mZramSwappinessPref.setUpdatesContinuously(false);
            mZramSwappinessPref.setOnPreferenceChangeListener(this);
        }

        mZramAlgoPref = findPreference(ZRAM_ALGO_KEY);
        if (mZramAlgoPref != null) mZramAlgoPref.setOnPreferenceChangeListener(this);

        mLmkProfilePref = findPreference(LMK_PROFILE_KEY);
        if (mLmkProfilePref != null) mLmkProfilePref.setOnPreferenceChangeListener(this);

        mIoSchedulerPref = findPreference(IO_SCHEDULER_KEY);
        if (mIoSchedulerPref != null) mIoSchedulerPref.setOnPreferenceChangeListener(this);

        mAppHibernationPref = findPreference(APP_HIBERNATION_KEY);
        if (mAppHibernationPref != null) mAppHibernationPref.setOnPreferenceChangeListener(this);

        // Storage Cleaners
        mStorageStatsPref = findPreference(STORAGE_STATS_KEY);
        
        // Helper to set listener
        setClickListener(CLEAN_APP_CACHE_KEY);
        setClickListener(CLEAN_SYSTEM_CACHE_KEY);
        setClickListener(CLEAN_THUMBNAILS_KEY);
        setClickListener(CLEAN_DOWNLOADS_KEY);
        setClickListener(CLEAN_TEMP_FILES_KEY);
        setClickListener(CLEAN_LOG_FILES_KEY);
        setClickListener(CLEAN_APK_FILES_KEY);
        setClickListener(CLEAN_EMPTY_FOLDERS_KEY);
        setClickListener(CLEAN_DUPLICATES_KEY);
        setClickListener(CLEAN_ALL_KEY);
        setClickListener(ANALYZE_STORAGE_KEY);
        setClickListener("zram_algorithm_info");
        setClickListener("io_scheduler_info");

        // Assign local variables for storage prefs
        mCleanAppCachePref = findPreference(CLEAN_APP_CACHE_KEY);
        mCleanSystemCachePref = findPreference(CLEAN_SYSTEM_CACHE_KEY);
        mCleanThumbnailsPref = findPreference(CLEAN_THUMBNAILS_KEY);
        mCleanDownloadsPref = findPreference(CLEAN_DOWNLOADS_KEY);
        mCleanTempFilesPref = findPreference(CLEAN_TEMP_FILES_KEY);
        mCleanLogFilesPref = findPreference(CLEAN_LOG_FILES_KEY);
        mCleanApkFilesPref = findPreference(CLEAN_APK_FILES_KEY);
        mCleanEmptyFoldersPref = findPreference(CLEAN_EMPTY_FOLDERS_KEY);
        mCleanDuplicatesPref = findPreference(CLEAN_DUPLICATES_KEY);
        mCleanAllPref = findPreference(CLEAN_ALL_KEY);
        mAnalyzeStoragePref = findPreference(ANALYZE_STORAGE_KEY);

        // Advanced
        mAutoCleanPref = findPreference(AUTO_CLEAN_KEY);
        if (mAutoCleanPref != null) mAutoCleanPref.setOnPreferenceChangeListener(this);
        
        mAutoCleanIntervalPref = findPreference(AUTO_CLEAN_INTERVAL_KEY);
        if (mAutoCleanIntervalPref != null) {
            mAutoCleanIntervalPref.setOnPreferenceChangeListener(this);
            mAutoCleanIntervalPref.setEnabled(mAutoCleanPref != null && mAutoCleanPref.isChecked());
        }
    }

    private void setClickListener(String key) {
        Preference pref = findPreference(key);
        if (pref != null) pref.setOnPreferenceClickListener(this);
    }

    private void setupUpdateTask() {
        mUpdateStatsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                updateRamStatistics();
                // Only update storage periodically if fragment is visible, 
                // but not every 3 seconds to save battery.
                // Storage stats are updated onResume or after clean.
                mHandler.postDelayed(this, 3000); 
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPreferenceStates();
        mHandler.post(mUpdateStatsRunnable);
        // Run heavy storage stats on background thread
        updateStorageStats(); 
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUpdateStatsRunnable);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            mExecutor.shutdown();
        }
    }

    private void refreshPreferenceStates() {
        // Do this in background to prevent UI blocking
        mExecutor.execute(() -> {
            if (!isAdded()) return;
            
            final boolean zramEnabled = RamOptimizerUtils.isZramEnabled();
            final int zramSize = RamOptimizerUtils.getZramSize();
            final int swappiness = RamOptimizerUtils.getZramSwappiness();
            final String algo = RamOptimizerUtils.getZramCompressionAlgorithm();
            final String lmk = RamOptimizerUtils.getLmkProfile(getContext());
            final String scheduler = RamOptimizerUtils.getIoScheduler();
            final boolean hibernate = RamOptimizerUtils.isAppHibernationEnabled(getContext());

            mHandler.post(() -> {
                if (!isAdded()) return;
                if (mZramEnablePref != null) mZramEnablePref.setChecked(zramEnabled);
                
                if (mZramSizePref != null) {
                    mZramSizePref.setValue(zramSize > 0 ? zramSize : 1024);
                    mZramSizePref.setEnabled(zramEnabled);
                }
                
                if (mZramSwappinessPref != null) {
                    mZramSwappinessPref.setValue(swappiness);
                    mZramSwappinessPref.setEnabled(zramEnabled);
                }
                
                if (mZramAlgoPref != null) {
                    mZramAlgoPref.setValue(algo);
                    mZramAlgoPref.setEnabled(zramEnabled);
                }

                if (mLmkProfilePref != null) mLmkProfilePref.setValue(lmk);
                if (mIoSchedulerPref != null) mIoSchedulerPref.setValue(scheduler);
                if (mAppHibernationPref != null) mAppHibernationPref.setChecked(hibernate);
                
                if (mAutoCleanPref != null && mAutoCleanIntervalPref != null) {
                    mAutoCleanIntervalPref.setEnabled(mAutoCleanPref.isChecked());
                }
            });
        });
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        
        // Handle logic that doesn't need background threads or specific handling
        if (key.equals(AUTO_CLEAN_KEY)) {
             boolean enabled = (Boolean) newValue;
             if (mAutoCleanIntervalPref != null) mAutoCleanIntervalPref.setEnabled(enabled);
             return true;
        }
        if (key.equals(AUTO_CLEAN_INTERVAL_KEY)) return true;

        // For heavy operations, use background executor and show progress
        applySettingAsync(key, newValue);
        return false; // We update the UI manually after success
    }

    private void applySettingAsync(String key, Object newValue) {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage("Applying settings...");
        dialog.setCancelable(false);
        dialog.show();

        mExecutor.execute(() -> {
            boolean success = false;
            String resultMsg = "";
            Context context = getContext();
            
            if (context == null) {
                dialog.dismiss();
                return;
            }

            try {
                switch (key) {
                    case ZRAM_ENABLE_KEY:
                        boolean enable = (Boolean) newValue;
                        success = RamOptimizerUtils.setZramEnabled(context, enable);
                        resultMsg = enable ? "zRAM Enabled" : "zRAM Disabled";
                        break;
                    case ZRAM_SIZE_KEY:
                        int size = (Integer) newValue;
                        success = RamOptimizerUtils.setZramSize(context, size);
                        resultMsg = "Size set to " + size + "MB";
                        break;
                    case ZRAM_SWAPPINESS_KEY:
                        int swap = (Integer) newValue;
                        success = RamOptimizerUtils.setZramSwappiness(swap);
                        resultMsg = "Swappiness updated";
                        break;
                    case ZRAM_ALGO_KEY:
                        String algo = (String) newValue;
                        success = RamOptimizerUtils.setZramCompressionAlgorithm(context, algo);
                        resultMsg = "Algorithm set to " + algo.toUpperCase();
                        break;
                    case LMK_PROFILE_KEY:
                        String profile = (String) newValue;
                        success = RamOptimizerUtils.setLmkProfile(context, profile);
                        resultMsg = "LMK Profile updated";
                        break;
                    case APP_HIBERNATION_KEY:
                        boolean hib = (Boolean) newValue;
                        success = RamOptimizerUtils.setAppHibernationEnabled(context, hib);
                        resultMsg = "Hibernation updated";
                        break;
                    case IO_SCHEDULER_KEY:
                        String sched = (String) newValue;
                        success = RamOptimizerUtils.setIoScheduler(sched);
                        resultMsg = success ? "I/O Scheduler set to " + sched : "Failed to set " + sched;
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting preference " + key, e);
                resultMsg = "Error: " + e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalMsg = resultMsg;

            mHandler.post(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (!isAdded()) return;

                if (finalSuccess) {
                    showToast(finalMsg);
                    // Update the UI preference state manually
                    updatePreferenceUI(key, newValue);
                    // Refresh stats
                    updateRamStatistics();
                } else {
                    showToast("Failed to apply: " + finalMsg);
                    refreshPreferenceStates(); // Revert UI to actual state
                }
            });
        });
    }

    private void updatePreferenceUI(String key, Object value) {
        Preference pref = findPreference(key);
        if (pref == null) return;

        if (pref instanceof SwitchPreference) {
            ((SwitchPreference) pref).setChecked((Boolean) value);
        } else if (pref instanceof SeekBarPreference) {
            ((SeekBarPreference) pref).setValue((Integer) value);
        } else if (pref instanceof ListPreference) {
            ((ListPreference) pref).setValue((String) value);
        }
        
        // Special logic for dependencies
        if (key.equals(ZRAM_ENABLE_KEY)) {
            boolean enabled = (Boolean) value;
            if (mZramSizePref != null) mZramSizePref.setEnabled(enabled);
            if (mZramSwappinessPref != null) mZramSwappinessPref.setEnabled(enabled);
            if (mZramAlgoPref != null) mZramAlgoPref.setEnabled(enabled);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if ("zram_algorithm_info".equals(key)) {
            showInfoDialog(R.string.zram_algorithms_comparison_title, R.string.zram_algorithms_comparison);
            return true;
        }
        if ("io_scheduler_info".equals(key)) {
            showInfoDialog(R.string.io_scheduler_info_title, R.string.io_scheduler_info_text);
            return true;
        }
        if (ANALYZE_STORAGE_KEY.equals(key)) {
            analyzeStorage();
            return true;
        }

        int cleanType = -1;
        String title = "";

        switch (key) {
            case CLEAN_APP_CACHE_KEY: cleanType = CleanTask.TYPE_APP_CACHE; title = "App Cache"; break;
            case CLEAN_SYSTEM_CACHE_KEY: cleanType = CleanTask.TYPE_SYSTEM_CACHE; title = "System Cache"; break;
            case CLEAN_THUMBNAILS_KEY: cleanType = CleanTask.TYPE_THUMBNAILS; title = "Thumbnails"; break;
            case CLEAN_DOWNLOADS_KEY: cleanType = CleanTask.TYPE_DOWNLOADS; title = "Old Downloads"; break;
            case CLEAN_TEMP_FILES_KEY: cleanType = CleanTask.TYPE_TEMP_FILES; title = "Temp Files"; break;
            case CLEAN_LOG_FILES_KEY: cleanType = CleanTask.TYPE_LOG_FILES; title = "Log Files"; break;
            case CLEAN_APK_FILES_KEY: cleanType = CleanTask.TYPE_APK_FILES; title = "APK Files"; break;
            case CLEAN_EMPTY_FOLDERS_KEY: cleanType = CleanTask.TYPE_EMPTY_FOLDERS; title = "Empty Folders"; break;
            case CLEAN_DUPLICATES_KEY: cleanType = CleanTask.TYPE_DUPLICATES; title = "Duplicates"; break;
            case CLEAN_ALL_KEY: cleanType = CleanTask.TYPE_ALL; title = "All Junk"; break;
        }

        if (cleanType != -1) {
            confirmAndClean(title, cleanType);
        }
        return true;
    }

    private void showInfoDialog(int titleRes, int msgRes) {
        new AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(msgRes)
            .setPositiveButton("OK", null)
            .show();
    }

    private void confirmAndClean(String cleanType, int cleanTaskType) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clean " + cleanType + "?")
                .setMessage("Are you sure you want to clean " + cleanType.toLowerCase() + "?")
                .setPositiveButton("Clean", (dialog, which) -> executeCleanTask(cleanTaskType))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeCleanTask(int type) {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage("Cleaning...");
        dialog.setCancelable(false);
        dialog.show();

        mExecutor.execute(() -> {
            final RamOptimizerUtils.CleanResult result = new CleanTask(type).perform(getContext());
            
            mHandler.post(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (!isAdded()) return;

                showToast(result.success ? result.message : "Failed: " + result.message);
                // Refresh storage stats after clean
                updateStorageStats();
            });
        });
    }

    private void analyzeStorage() {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage("Analyzing storage...");
        dialog.setCancelable(false);
        dialog.show();

        mExecutor.execute(() -> {
            RamOptimizerUtils.StorageStats stats = RamOptimizerUtils.getStorageStats(getContext());
            mHandler.post(() -> {
                dialog.dismiss();
                if (!isAdded()) return;
                
                String message = String.format(
                        "Storage Analysis:\n\n" +
                        "💾 Total: %d MB\n" +
                        "📦 Used: %d MB\n" +
                        "⚪ Free: %d MB\n\n" +
                        "Cleanable:\n" +
                        "• App Cache: %d MB\n" +
                        "• System Cache: %d MB\n" +
                        "• Thumbnails: %d MB\n" +
                        "• Old Downloads: %d MB\n" +
                        "• Duplicates: %d MB\n\n" +
                        "♻️ Total Cleanable: %d MB",
                        stats.totalStorage, stats.usedStorage, stats.freeStorage,
                        stats.appCacheSize, stats.systemCacheSize, stats.thumbnailsSize,
                        stats.downloadsSize, stats.duplicateFilesSize, stats.getTotalCleanable()
                );
                new AlertDialog.Builder(requireContext())
                        .setTitle("Storage Analysis")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        });
    }

    private void updateRamStatistics() {
        // Use background thread for file reads
        mExecutor.execute(() -> {
            if (!isAdded()) return;
            
            try {
                RamOptimizerUtils.RamStats stats = RamOptimizerUtils.getRamStatistics();
                String currentAlgo = RamOptimizerUtils.getZramCompressionAlgorithm().toUpperCase();
                String currentLmk = RamOptimizerUtils.getLmkProfile(getContext());
                // Add IO Scheduler
                String currentIo = RamOptimizerUtils.getIoScheduler().toUpperCase();
                
                mHandler.post(() -> {
                    if (mStatsPreference == null || !isAdded()) return;
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("RAM: %s / %s (Free: %s)\n", 
                        formatSize(stats.usedRam), formatSize(stats.totalRam), formatSize(stats.freeRam)));
                    sb.append(String.format("Swap/zRAM: %s (%s)\n", 
                        stats.zramEnabled ? formatSize(stats.zramSize) : "OFF", currentAlgo));
                    
                    if (stats.zramEnabled && stats.zramStats.compressionRatio > 0) {
                       sb.append(String.format("Compression Ratio: %.2f:1\n", stats.zramStats.compressionRatio));
                    }
                    
                    // Display IO Scheduler in the RAM stats as requested
                    sb.append("\n⚙️ LMK: ").append(currentLmk.substring(0, 1).toUpperCase() + currentLmk.substring(1));
                    sb.append(" | I/O: ").append(currentIo);
                    
                    mStatsPreference.setSummary(sb.toString());
                });
            } catch (Exception e) {
                Log.e(TAG, "Stats update failed", e);
            }
        });
    }
    
    private String formatSize(long mb) {
        return mb + " MB";
    }

    private void updateStorageStats() {
        if (mStorageStatsPref == null) return;
        
        // Set loading state
        mHandler.post(() -> {
            if(mCleanAllPref != null) mCleanAllPref.setSummary("Scanning...");
        });

        mExecutor.execute(() -> {
            if (!isAdded()) return;
            RamOptimizerUtils.StorageStats stats = RamOptimizerUtils.getStorageStats(getContext());
            
            mHandler.post(() -> {
                if (!isAdded()) return;
                
                String summary = String.format("Used: %d%% | Free: %s\nPotential cleanup: %s",
                        (int)((float)stats.usedStorage/stats.totalStorage * 100),
                        formatSize(stats.freeStorage),
                        formatSize(stats.getTotalCleanable()));
                
                mStorageStatsPref.setSummary(summary);

                // Update visuals with emojis for better design
                updateCleanerPref(mCleanAppCachePref, stats.appCacheSize, "🗑️");
                updateCleanerPref(mCleanSystemCachePref, stats.systemCacheSize, "⚙️");
                updateCleanerPref(mCleanThumbnailsPref, stats.thumbnailsSize, "🖼️");
                updateCleanerPref(mCleanDownloadsPref, stats.downloadsSize, "⬇️");
                updateCleanerPref(mCleanTempFilesPref, stats.tempFilesSize, "📁");
                updateCleanerPref(mCleanLogFilesPref, stats.logFilesSize, "📝");
                updateCleanerPref(mCleanApkFilesPref, stats.apkSize, "📦");
                updateCleanerPref(mCleanDuplicatesPref, stats.duplicateFilesSize, "📄");
                
                if (mCleanEmptyFoldersPref != null) {
                    mCleanEmptyFoldersPref.setSummary("📂 Remove empty directories");
                }
                if (mCleanAllPref != null) {
                    long total = stats.getTotalCleanable();
                    mCleanAllPref.setSummary(total > 0 ? 
                        "✨ Clean all junk (" + formatSize(total) + ")" : "✨ System is clean");
                }
            });
        });
    }

    private void updateCleanerPref(Preference pref, long sizeMB, String icon) {
        if (pref != null) {
            if (sizeMB > 0) {
                pref.setSummary(icon + " Can free: " + sizeMB + " MB");
                pref.setEnabled(true);
            } else {
                pref.setSummary(icon + " Clean");
                pref.setEnabled(false);
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Cleaner Task Class (Non-AsyncTask version)
    private static class CleanTask {
        static final int TYPE_APP_CACHE = 1;
        static final int TYPE_SYSTEM_CACHE = 2;
        static final int TYPE_THUMBNAILS = 3;
        static final int TYPE_DOWNLOADS = 4;
        static final int TYPE_TEMP_FILES = 5;
        static final int TYPE_LOG_FILES = 6;
        static final int TYPE_APK_FILES = 7;
        static final int TYPE_EMPTY_FOLDERS = 8;
        static final int TYPE_DUPLICATES = 9;
        static final int TYPE_ALL = 10;

        private final int mType;

        CleanTask(int type) {
            mType = type;
        }

        RamOptimizerUtils.CleanResult perform(Context context) {
            switch (mType) {
                case TYPE_APP_CACHE: return RamOptimizerUtils.cleanAppCache(context);
                case TYPE_SYSTEM_CACHE: return RamOptimizerUtils.cleanSystemCache();
                case TYPE_THUMBNAILS: return RamOptimizerUtils.cleanThumbnails();
                case TYPE_DOWNLOADS: return RamOptimizerUtils.cleanOldDownloads();
                case TYPE_TEMP_FILES: return RamOptimizerUtils.cleanTempFiles();
                case TYPE_LOG_FILES: return RamOptimizerUtils.cleanLogFiles();
                case TYPE_APK_FILES: return RamOptimizerUtils.cleanApkFiles();
                case TYPE_EMPTY_FOLDERS: return RamOptimizerUtils.cleanEmptyFolders();
                case TYPE_DUPLICATES: return RamOptimizerUtils.cleanDuplicateFiles();
                case TYPE_ALL: return RamOptimizerUtils.cleanAll(context);
                default:
                    RamOptimizerUtils.CleanResult r = new RamOptimizerUtils.CleanResult();
                    r.message = "Unknown type";
                    return r;
            }
        }
    }
}
