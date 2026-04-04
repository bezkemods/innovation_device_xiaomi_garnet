/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.ramoptimizer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import org.lineageos.settings.R;

public class RamOptimizerFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "RamOptimizerFragment";

    // Preference keys
    private static final String STATS_CATEGORY_KEY = "ram_stats_category";
    private static final String STATS_KEY = "ram_stats";
    private static final String ZRAM_ENABLE_KEY = "zram_enable";
    private static final String ZRAM_SIZE_KEY = "zram_size";
    private static final String ZRAM_SWAPPINESS_KEY = "zram_swappiness";
    private static final String ZRAM_ALGO_KEY = "zram_compression_algorithm";
    private static final String LMK_PROFILE_KEY = "lmk_profile";
    private static final String APP_HIBERNATION_KEY = "app_hibernation";
    private static final String IO_SCHEDULER_KEY = "io_scheduler";
    
    // Storage cleaner keys
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

    // Advanced keys
    private static final String AUTO_CLEAN_KEY = "auto_clean_enabled";
    private static final String AUTO_CLEAN_INTERVAL_KEY = "auto_clean_interval";
    private static final String ANALYZE_STORAGE_KEY = "analyze_storage";

    private Preference mStatsPreference;
    private SwitchPreference mZramEnablePref;
    private SeekBarPreference mZramSizePref;
    private SeekBarPreference mZramSwappinessPref;
    private ListPreference mZramAlgoPref;
    private ListPreference mLmkProfilePref;
    private SwitchPreference mAppHibernationPref;
    private ListPreference mIoSchedulerPref;

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

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mUpdateStatsRunnable;
    private RamOptimizerUtils.StorageStats mStorageStats;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // JAVÍTÁS: SharedPreferences-ben tárolt helytelen típusú értékek javítása
        fixCorruptedPreferences();

        try {
            addPreferencesFromResource(R.xml.ram_optimizer_settings);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preferences XML", e);
            Toast.makeText(getContext(), "Error loading RAM Optimizer settings", Toast.LENGTH_LONG).show();
            if (getActivity() != null) getActivity().finish();
            return;
        }

        initializePreferences();
        setupUpdateTask();
    }

    /**
     * Javítja a SharedPreferences-ben esetlegesen rossz típusú értékeket.
     * Különösen a SeekBarPreference-eknél váltott ki ClassCastException-t.
     */
    private void fixCorruptedPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;

        // zram_size: int kell, 512-12288
        if (prefs.contains(ZRAM_SIZE_KEY)) {
            try {
                prefs.getInt(ZRAM_SIZE_KEY, 4096);
            } catch (ClassCastException e) {
                String stringValue = prefs.getString(ZRAM_SIZE_KEY, "4096");
                int intValue;
                try {
                    intValue = Integer.parseInt(stringValue);
                } catch (NumberFormatException ex) {
                    intValue = 4096;
                }
                editor.putInt(ZRAM_SIZE_KEY, intValue);
                changed = true;
                Log.w(TAG, "Fixed corrupted zram_size from String to int: " + intValue);
            }
        }

        // zram_swappiness: int kell, 0-200
        if (prefs.contains(ZRAM_SWAPPINESS_KEY)) {
            try {
                prefs.getInt(ZRAM_SWAPPINESS_KEY, 60);
            } catch (ClassCastException e) {
                String stringValue = prefs.getString(ZRAM_SWAPPINESS_KEY, "60");
                int intValue;
                try {
                    intValue = Integer.parseInt(stringValue);
                } catch (NumberFormatException ex) {
                    intValue = 60;
                }
                editor.putInt(ZRAM_SWAPPINESS_KEY, intValue);
                changed = true;
                Log.w(TAG, "Fixed corrupted zram_swappiness from String to int: " + intValue);
            }
        }

        if (changed) {
            editor.apply();
        }
    }

    private void initializePreferences() {
        // RAM Stats kategória – ha nincs, akkor létrehozzuk programozottan
        PreferenceCategory statsCategory = findPreference(STATS_CATEGORY_KEY);
        if (statsCategory == null) {
            statsCategory = new PreferenceCategory(requireContext());
            statsCategory.setKey(STATS_CATEGORY_KEY);
            statsCategory.setTitle("RAM Statistics");
            getPreferenceScreen().addPreference(statsCategory);
        }

        if (findPreference(STATS_KEY) == null) {
            mStatsPreference = new Preference(requireContext());
            mStatsPreference.setKey(STATS_KEY);
            mStatsPreference.setTitle("RAM Usage");
            mStatsPreference.setSelectable(false);
            statsCategory.addPreference(mStatsPreference);
        } else {
            mStatsPreference = findPreference(STATS_KEY);
        }

        // zRAM preferenciák – csak akkor jelennek meg, ha a rendszer támogatja
        boolean zramSupported = RamOptimizerUtils.isZramSupported();
        if (!zramSupported) {
            PreferenceCategory zramCat = findPreference("zram_category");
            if (zramCat != null) getPreferenceScreen().removePreference(zramCat);
        } else {
            mZramEnablePref = findPreference(ZRAM_ENABLE_KEY);
            if (mZramEnablePref != null) {
                mZramEnablePref.setOnPreferenceChangeListener(this);
                mZramEnablePref.setChecked(RamOptimizerUtils.isZramEnabled());
            }

            mZramSizePref = findPreference(ZRAM_SIZE_KEY);
            if (mZramSizePref != null) {
                mZramSizePref.setMin(512);
                mZramSizePref.setMax(12288);
                mZramSizePref.setUpdatesContinuously(false);
                mZramSizePref.setOnPreferenceChangeListener(this);
                int currentSize = RamOptimizerUtils.getZramSize();
                mZramSizePref.setValue(currentSize > 0 ? currentSize : 4096);
                mZramSizePref.setEnabled(RamOptimizerUtils.isZramEnabled());
            }

            mZramSwappinessPref = findPreference(ZRAM_SWAPPINESS_KEY);
            if (mZramSwappinessPref != null) {
                mZramSwappinessPref.setMin(0);
                mZramSwappinessPref.setMax(200);
                mZramSwappinessPref.setUpdatesContinuously(false);
                mZramSwappinessPref.setOnPreferenceChangeListener(this);
                mZramSwappinessPref.setValue(RamOptimizerUtils.getZramSwappiness());
                mZramSwappinessPref.setEnabled(RamOptimizerUtils.isZramEnabled());
            }

            mZramAlgoPref = findPreference(ZRAM_ALGO_KEY);
            if (mZramAlgoPref != null) {
                mZramAlgoPref.setOnPreferenceChangeListener(this);
                String currentAlgo = RamOptimizerUtils.getZramCompressionAlgorithm();
                mZramAlgoPref.setValue(currentAlgo);
                mZramAlgoPref.setEnabled(RamOptimizerUtils.isZramEnabled());
            }

            Preference mZramAlgoInfoPref = findPreference("zram_algorithm_info");
            if (mZramAlgoInfoPref != null) mZramAlgoInfoPref.setOnPreferenceClickListener(this);
        }

        // LMK
        mLmkProfilePref = findPreference(LMK_PROFILE_KEY);
        if (mLmkProfilePref != null) {
            mLmkProfilePref.setOnPreferenceChangeListener(this);
            String currentProfile = RamOptimizerUtils.getLmkProfile(requireContext());
            mLmkProfilePref.setValue(currentProfile);
        }

        // I/O Scheduler
        mIoSchedulerPref = findPreference(IO_SCHEDULER_KEY);
        if (mIoSchedulerPref != null) {
            mIoSchedulerPref.setOnPreferenceChangeListener(this);
            String currentScheduler = RamOptimizerUtils.getIoScheduler();
            mIoSchedulerPref.setValue(currentScheduler);
        }

        Preference mIoSchedulerInfoPref = findPreference("io_scheduler_info");
        if (mIoSchedulerInfoPref != null) mIoSchedulerInfoPref.setOnPreferenceClickListener(this);

        // App hibernation
        mAppHibernationPref = findPreference(APP_HIBERNATION_KEY);
        if (mAppHibernationPref != null) {
            mAppHibernationPref.setOnPreferenceChangeListener(this);
            mAppHibernationPref.setChecked(RamOptimizerUtils.isAppHibernationEnabled(requireContext()));
        }

        // Storage cleaner preferenciák
        mStorageStatsPref = findPreference(STORAGE_STATS_KEY);
        mCleanAppCachePref = findPreference(CLEAN_APP_CACHE_KEY);
        if (mCleanAppCachePref != null) mCleanAppCachePref.setOnPreferenceClickListener(this);
        mCleanSystemCachePref = findPreference(CLEAN_SYSTEM_CACHE_KEY);
        if (mCleanSystemCachePref != null) mCleanSystemCachePref.setOnPreferenceClickListener(this);
        mCleanThumbnailsPref = findPreference(CLEAN_THUMBNAILS_KEY);
        if (mCleanThumbnailsPref != null) mCleanThumbnailsPref.setOnPreferenceClickListener(this);
        mCleanDownloadsPref = findPreference(CLEAN_DOWNLOADS_KEY);
        if (mCleanDownloadsPref != null) mCleanDownloadsPref.setOnPreferenceClickListener(this);
        mCleanTempFilesPref = findPreference(CLEAN_TEMP_FILES_KEY);
        if (mCleanTempFilesPref != null) mCleanTempFilesPref.setOnPreferenceClickListener(this);
        mCleanLogFilesPref = findPreference(CLEAN_LOG_FILES_KEY);
        if (mCleanLogFilesPref != null) mCleanLogFilesPref.setOnPreferenceClickListener(this);
        mCleanApkFilesPref = findPreference(CLEAN_APK_FILES_KEY);
        if (mCleanApkFilesPref != null) mCleanApkFilesPref.setOnPreferenceClickListener(this);
        mCleanEmptyFoldersPref = findPreference(CLEAN_EMPTY_FOLDERS_KEY);
        if (mCleanEmptyFoldersPref != null) mCleanEmptyFoldersPref.setOnPreferenceClickListener(this);
        mCleanDuplicatesPref = findPreference(CLEAN_DUPLICATES_KEY);
        if (mCleanDuplicatesPref != null) mCleanDuplicatesPref.setOnPreferenceClickListener(this);
        mCleanAllPref = findPreference(CLEAN_ALL_KEY);
        if (mCleanAllPref != null) mCleanAllPref.setOnPreferenceClickListener(this);

        // Advanced
        mAutoCleanPref = findPreference(AUTO_CLEAN_KEY);
        if (mAutoCleanPref != null) mAutoCleanPref.setOnPreferenceChangeListener(this);
        mAutoCleanIntervalPref = findPreference(AUTO_CLEAN_INTERVAL_KEY);
        if (mAutoCleanIntervalPref != null) {
            mAutoCleanIntervalPref.setOnPreferenceChangeListener(this);
            mAutoCleanIntervalPref.setEnabled(mAutoCleanPref != null && mAutoCleanPref.isChecked());
        }
        mAnalyzeStoragePref = findPreference(ANALYZE_STORAGE_KEY);
        if (mAnalyzeStoragePref != null) mAnalyzeStoragePref.setOnPreferenceClickListener(this);

        updateRamStatistics();
        updateStorageStats();
    }

    private void setupUpdateTask() {
        mUpdateStatsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    updateRamStatistics();
                    updateStorageStats();
                    mHandler.postDelayed(this, 3000);
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // Frissítés a legfrissebb értékekkel
        if (mZramEnablePref != null && RamOptimizerUtils.isZramSupported()) {
            mZramEnablePref.setChecked(RamOptimizerUtils.isZramEnabled());
        }
        if (mZramSizePref != null && RamOptimizerUtils.isZramSupported()) {
            int size = RamOptimizerUtils.getZramSize();
            mZramSizePref.setValue(size > 0 ? size : 4096);
            mZramSizePref.setEnabled(RamOptimizerUtils.isZramEnabled());
        }
        if (mZramSwappinessPref != null && RamOptimizerUtils.isZramSupported()) {
            mZramSwappinessPref.setValue(RamOptimizerUtils.getZramSwappiness());
            mZramSwappinessPref.setEnabled(RamOptimizerUtils.isZramEnabled());
        }
        if (mZramAlgoPref != null && RamOptimizerUtils.isZramSupported()) {
            mZramAlgoPref.setValue(RamOptimizerUtils.getZramCompressionAlgorithm());
            mZramAlgoPref.setEnabled(RamOptimizerUtils.isZramEnabled());
        }
        if (mLmkProfilePref != null) {
            mLmkProfilePref.setValue(RamOptimizerUtils.getLmkProfile(requireContext()));
        }
        if (mAppHibernationPref != null) {
            mAppHibernationPref.setChecked(RamOptimizerUtils.isAppHibernationEnabled(requireContext()));
        }
        if (mIoSchedulerPref != null) {
            mIoSchedulerPref.setValue(RamOptimizerUtils.getIoScheduler());
        }
        updateRamStatistics();
        updateStorageStats();
        mHandler.post(mUpdateStatsRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUpdateStatsRunnable);
        RamOptimizerUtils.savePreferences(requireContext());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        try {
            switch (key) {
                case ZRAM_ENABLE_KEY:
                    handleZramEnableChange((Boolean) newValue);
                    return true;
                case ZRAM_SIZE_KEY:
                    handleZramSizeChange((Integer) newValue);
                    return true;
                case ZRAM_SWAPPINESS_KEY:
                    handleZramSwappinessChange((Integer) newValue);
                    return true;
                case ZRAM_ALGO_KEY:
                    handleZramAlgoChange((String) newValue);
                    return true;
                case LMK_PROFILE_KEY:
                    handleLmkProfileChange((String) newValue);
                    return true;
                case APP_HIBERNATION_KEY:
                    handleAppHibernationChange((Boolean) newValue);
                    return true;
                case AUTO_CLEAN_KEY:
                    handleAutoCleanChange((Boolean) newValue);
                    return true;
                case AUTO_CLEAN_INTERVAL_KEY:
                    handleAutoCleanIntervalChange((String) newValue);
                    return true;
                case IO_SCHEDULER_KEY:
                    handleIoSchedulerChange((String) newValue);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling preference change for " + key, e);
        }
        return false;
    }

    // --- zRAM kezelés (háttérszálon) ---
    private void handleZramEnableChange(boolean enabled) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setZramEnabled(requireContext(), enabled);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    if (mZramSizePref != null) mZramSizePref.setEnabled(enabled);
                    if (mZramSwappinessPref != null) mZramSwappinessPref.setEnabled(enabled);
                    if (mZramAlgoPref != null) mZramAlgoPref.setEnabled(enabled);
                    if (enabled && mZramSizePref != null) {
                        int currentSize = RamOptimizerUtils.getZramSize();
                        mZramSizePref.setValue(currentSize > 0 ? currentSize : 4096);
                    }
                    showToast(enabled ? "zRAM enabled" : "zRAM disabled");
                    mHandler.postDelayed(this::updateRamStatistics, 1000);
                } else {
                    if (mZramEnablePref != null) mZramEnablePref.setChecked(!enabled);
                    showToast("Failed to " + (enabled ? "enable" : "disable") + " zRAM");
                }
            });
        }).start();
    }

    private void handleZramSizeChange(int size) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setZramSize(requireContext(), size);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    showToast("zRAM size set to " + size + " MB");
                    mHandler.postDelayed(this::updateRamStatistics, 1000);
                } else {
                    if (mZramSizePref != null) {
                        int currentSize = RamOptimizerUtils.getZramSize();
                        mZramSizePref.setValue(currentSize > 0 ? currentSize : 4096);
                    }
                    showToast("Failed to set zRAM size");
                }
            });
        }).start();
    }

    private void handleZramSwappinessChange(int swappiness) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setZramSwappiness(swappiness);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    showToast("Swappiness set to " + swappiness);
                    mHandler.postDelayed(this::updateRamStatistics, 500);
                } else {
                    showToast("Failed to set swappiness");
                }
            });
        }).start();
    }

    private void handleZramAlgoChange(String algo) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setZramCompressionAlgorithm(requireContext(), algo);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    showToast("Compression algorithm set to " + algo.toUpperCase());
                    mHandler.postDelayed(this::updateRamStatistics, 1000);
                } else {
                    showToast("Failed to set compression algorithm");
                }
            });
        }).start();
    }

    private void handleLmkProfileChange(String profile) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setLmkProfile(requireContext(), profile);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    showToast("LMK profile set to " + profile);
                    mHandler.postDelayed(this::updateRamStatistics, 500);
                } else {
                    showToast("Failed to set LMK profile");
                }
            });
        }).start();
    }

    private void handleAppHibernationChange(boolean enabled) {
        boolean success = RamOptimizerUtils.setAppHibernationEnabled(requireContext(), enabled);
        showToast(success ? (enabled ? "App hibernation enabled" : "App hibernation disabled")
                          : "Failed to change app hibernation");
    }

    private void handleIoSchedulerChange(String scheduler) {
        new Thread(() -> {
            boolean success = RamOptimizerUtils.setIoScheduler(scheduler);
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    showToast("I/O Scheduler set to " + scheduler.toUpperCase());
                } else {
                    showToast("Failed to set I/O scheduler");
                }
            });
        }).start();
    }

    private void handleAutoCleanChange(boolean enabled) {
        if (mAutoCleanIntervalPref != null) mAutoCleanIntervalPref.setEnabled(enabled);
        showToast(enabled ? "Auto clean enabled" : "Auto clean disabled");
    }

    private void handleAutoCleanIntervalChange(String value) {
        showToast("Auto clean interval updated");
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case CLEAN_APP_CACHE_KEY:
                confirmAndClean("App Cache", CleanTask.TYPE_APP_CACHE);
                break;
            case CLEAN_SYSTEM_CACHE_KEY:
                confirmAndClean("System Cache", CleanTask.TYPE_SYSTEM_CACHE);
                break;
            case CLEAN_THUMBNAILS_KEY:
                confirmAndClean("Thumbnails", CleanTask.TYPE_THUMBNAILS);
                break;
            case CLEAN_DOWNLOADS_KEY:
                confirmAndClean("Old Downloads (30+ days)", CleanTask.TYPE_DOWNLOADS);
                break;
            case CLEAN_TEMP_FILES_KEY:
                confirmAndClean("Temp Files", CleanTask.TYPE_TEMP_FILES);
                break;
            case CLEAN_LOG_FILES_KEY:
                confirmAndClean("Log Files", CleanTask.TYPE_LOG_FILES);
                break;
            case CLEAN_APK_FILES_KEY:
                confirmAndClean("APK Files", CleanTask.TYPE_APK_FILES);
                break;
            case CLEAN_EMPTY_FOLDERS_KEY:
                confirmAndClean("Empty Folders", CleanTask.TYPE_EMPTY_FOLDERS);
                break;
            case CLEAN_DUPLICATES_KEY:
                confirmAndClean("Duplicate Files", CleanTask.TYPE_DUPLICATES);
                break;
            case CLEAN_ALL_KEY:
                confirmAndClean("All Junk Files", CleanTask.TYPE_ALL);
                break;
            case ANALYZE_STORAGE_KEY:
                analyzeStorage();
                break;
            case "zram_algorithm_info":
                showAlgorithmInfo();
                return true;
            case "io_scheduler_info":
                showIoSchedulerInfo();
                return true;
        }
        return true;
    }

    private void confirmAndClean(String cleanType, int cleanTaskType) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clean " + cleanType + "?")
                .setMessage("This will remove " + cleanType.toLowerCase() + ". Continue?")
                .setPositiveButton("Clean", (dialog, which) -> new CleanTask(cleanTaskType).execute())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void analyzeStorage() {
        ProgressDialog dialog = showProgress("Analyzing storage...");
        new Thread(() -> {
            RamOptimizerUtils.StorageStats stats = RamOptimizerUtils.getStorageStats(requireContext());
            mHandler.post(() -> {
                dialog.dismiss();
                String message = String.format(
                        "Storage Analysis:\n\nTotal: %d MB\nUsed: %d MB\nFree: %d MB\n\nCleanable Files:\n" +
                        "• App Cache: %d MB\n• System Cache: %d MB\n• Thumbnails: %d MB\n" +
                        "• Old Downloads: %d MB\n• Temp Files: %d MB\n• Log Files: %d MB\n" +
                        "• APK Files: %d MB\n• Duplicates: %d MB\n\nTotal Cleanable: %d MB",
                        stats.totalStorage, stats.usedStorage, stats.freeStorage,
                        stats.appCacheSize, stats.systemCacheSize, stats.thumbnailsSize,
                        stats.downloadsSize, stats.tempFilesSize, stats.logFilesSize,
                        stats.apkSize, stats.duplicateFilesSize, stats.getTotalCleanable()
                );
                new AlertDialog.Builder(requireContext())
                        .setTitle("Storage Analysis")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }

    private void updateRamStatistics() {
        if (mStatsPreference == null || !isAdded()) return;
        new Thread(() -> {
            try {
                RamOptimizerUtils.RamStats stats = RamOptimizerUtils.getRamStatistics();
                String currentAlgo = RamOptimizerUtils.getZramCompressionAlgorithm().toUpperCase();
                String currentLmk = RamOptimizerUtils.getLmkProfile(requireContext()).toUpperCase();

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Total: %d MB | Used: %d MB | Free: %d MB\nAvailable: %d MB | Cached: %d MB\n",
                        stats.totalRam, stats.usedRam, stats.freeRam, stats.availableRam, stats.cachedRam));
                if (stats.zramEnabled) {
                    sb.append(String.format("zRAM: On (%d MB)", stats.zramSize));
                    if (stats.zramStats.compressionRatio > 0) {
                        sb.append(" | Ratio: ").append(stats.zramStats.getCompressionRatioString());
                    }
                    sb.append("\nAlgorithm: ").append(currentAlgo);
                } else {
                    sb.append("zRAM: Off");
                }
                sb.append(" | LMK: ").append(currentLmk);

                final String summary = sb.toString();
                mHandler.post(() -> {
                    if (isAdded() && mStatsPreference != null) mStatsPreference.setSummary(summary);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to update RAM statistics", e);
                mHandler.post(() -> {
                    if (isAdded() && mStatsPreference != null) mStatsPreference.setSummary("Error retrieving stats");
                });
            }
        }).start();
    }

    private void updateStorageStats() {
        if (mStorageStatsPref == null || !isAdded()) return;
        new Thread(() -> {
            mStorageStats = RamOptimizerUtils.getStorageStats(requireContext());
            mHandler.post(() -> {
                if (!isAdded()) return;
                String statsText = String.format("Total: %d MB | Used: %d MB | Free: %d MB\nCleanable: %d MB",
                        mStorageStats.totalStorage, mStorageStats.usedStorage, mStorageStats.freeStorage, mStorageStats.getTotalCleanable());
                mStorageStatsPref.setSummary(statsText);
                updateCleanerSummary(mCleanAppCachePref, mStorageStats.appCacheSize);
                updateCleanerSummary(mCleanSystemCachePref, mStorageStats.systemCacheSize);
                updateCleanerSummary(mCleanThumbnailsPref, mStorageStats.thumbnailsSize);
                updateCleanerSummary(mCleanDownloadsPref, mStorageStats.downloadsSize);
                updateCleanerSummary(mCleanTempFilesPref, mStorageStats.tempFilesSize);
                updateCleanerSummary(mCleanLogFilesPref, mStorageStats.logFilesSize);
                updateCleanerSummary(mCleanApkFilesPref, mStorageStats.apkSize);
                updateCleanerSummary(mCleanDuplicatesPref, mStorageStats.duplicateFilesSize);
                if (mCleanEmptyFoldersPref != null) mCleanEmptyFoldersPref.setSummary("Remove empty directories");
                if (mCleanAllPref != null) mCleanAllPref.setSummary(String.format("Total: %d MB", mStorageStats.getTotalCleanable()));
            });
        }).start();
    }

    private void updateCleanerSummary(Preference pref, long size) {
        if (pref != null) pref.setSummary(String.format("Can free: %d MB", size));
    }

    private void showAlgorithmInfo() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.zram_algorithms_comparison_title)
                .setMessage(R.string.zram_algorithms_comparison)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showIoSchedulerInfo() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.io_scheduler_info_title)
                .setMessage(R.string.io_scheduler_info_text)
                .setPositiveButton("OK", null)
                .show();
    }

    private ProgressDialog showProgress(String message) {
        ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();
        return dialog;
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // --- Belső CleanTask osztály (AsyncTask helyett) ---
    private class CleanTask implements Runnable {
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
        private final ProgressDialog mDialog;

        CleanTask(int type) {
            mType = type;
            mDialog = isAdded() ? showProgress("Cleaning...") : null;
        }

        @Override
        public void run() {
            RamOptimizerUtils.CleanResult result;
            switch (mType) {
                case TYPE_APP_CACHE:    result = RamOptimizerUtils.cleanAppCache(requireContext()); break;
                case TYPE_SYSTEM_CACHE: result = RamOptimizerUtils.cleanSystemCache(); break;
                case TYPE_THUMBNAILS:   result = RamOptimizerUtils.cleanThumbnails(); break;
                case TYPE_DOWNLOADS:    result = RamOptimizerUtils.cleanOldDownloads(); break;
                case TYPE_TEMP_FILES:   result = RamOptimizerUtils.cleanTempFiles(); break;
                case TYPE_LOG_FILES:    result = RamOptimizerUtils.cleanLogFiles(); break;
                case TYPE_APK_FILES:    result = RamOptimizerUtils.cleanApkFiles(); break;
                case TYPE_EMPTY_FOLDERS:result = RamOptimizerUtils.cleanEmptyFolders(); break;
                case TYPE_DUPLICATES:   result = RamOptimizerUtils.cleanDuplicateFiles(); break;
                case TYPE_ALL:          result = RamOptimizerUtils.cleanAll(requireContext()); break;
                default:
                    result = new RamOptimizerUtils.CleanResult();
                    result.success = false;
                    result.message = "Unknown clean type";
            }
            final RamOptimizerUtils.CleanResult finalResult = result;
            mHandler.post(() -> {
                if (!isAdded()) return;
                if (mDialog != null && mDialog.isShowing()) mDialog.dismiss();
                if (finalResult.success) {
                    showToast(finalResult.message);
                    mHandler.postDelayed(() -> updateStorageStats(), 1000);
                } else {
                    showToast("Cleaning failed: " + finalResult.message);
                }
            });
        }

        void execute() {
            new Thread(this).start();
        }
    }
}
