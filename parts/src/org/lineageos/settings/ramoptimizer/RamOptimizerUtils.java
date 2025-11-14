/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.lineageos.settings.ramoptimizer;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RamOptimizerUtils {
    private static final String TAG = "RamOptimizerUtils";
    
    // zRAM paths
    private static final String ZRAM_DISK_SIZE_PATH = "/sys/block/zram0/disksize";
    private static final String ZRAM_SWAPPINESS_PATH = "/proc/sys/vm/swappiness";
    private static final String ZRAM_COMP_ALGORITHM_PATH = "/sys/block/zram0/comp_algorithm";
    private static final String ZRAM_RESET_PATH = "/sys/block/zram0/reset";
    private static final String ZRAM_INITSTATE_PATH = "/sys/block/zram0/initstate";
    private static final String ZRAM_MEM_USED_PATH = "/sys/block/zram0/mem_used_total";
    private static final String ZRAM_ORIG_SIZE_PATH = "/sys/block/zram0/orig_data_size";
    
    // LMK paths
    private static final String LMK_MINFREE_PATH = "/sys/module/lowmemorykiller/parameters/minfree";
    private static final String LMK_ADAPTIVE_PATH = "/sys/module/lowmemorykiller/parameters/enable_adaptive_lmk";
    
    // Preference keys
    private static final String PREF_ZRAM_ENABLED = "zram_enable";
    private static final String PREF_ZRAM_SIZE = "zram_size";
    private static final String PREF_ZRAM_SWAPPINESS = "zram_swappiness";
    private static final String PREF_APP_HIBERNATION = "app_hibernation";
    private static final String PREF_AUTO_CLEAN = "auto_clean_enabled";
    private static final String PREF_AUTO_CLEAN_INTERVAL = "auto_clean_interval";
    private static final String PREF_LMK_PROFILE = "lmk_profile";
    private static final String PREF_LAST_CLEAN_TIME = "last_clean_time";

    /**
     * Check if RAM optimizer features are supported
     */
    public static boolean isSupported() {
        File zramFile = new File(ZRAM_DISK_SIZE_PATH);
        File swappinessFile = new File(ZRAM_SWAPPINESS_PATH);
        return zramFile.exists() || swappinessFile.exists();
    }

    /**
     * Check if zRAM is enabled
     */
    public static boolean isZramEnabled() {
        try {
            String value = readFile(ZRAM_DISK_SIZE_PATH);
            if (value == null || value.isEmpty()) return false;
            long size = Long.parseLong(value.trim());
            return size > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check zRAM status", e);
            return false;
        }
    }

    /**
     * Enable/disable zRAM
     */
    public static boolean setZramEnabled(boolean enabled) {
        try {
            if (enabled) {
                return setZramSize(1024);
            } else {
                return disableZram();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set zRAM enabled state", e);
            return false;
        }
    }

    /**
     * Disable zRAM properly
     */
    private static boolean disableZram() {
        StringBuilder commands = new StringBuilder();
        commands.append("echo 0 > ").append(ZRAM_SWAPPINESS_PATH).append("\n");
        commands.append("sleep 1\n");
        commands.append("swapoff /dev/block/zram0 2>/dev/null\n");
        commands.append("sleep 0.5\n");
        commands.append("echo 1 > ").append(ZRAM_RESET_PATH).append(" 2>/dev/null\n");
        commands.append("sleep 0.5\n");
        commands.append("echo 0 > ").append(ZRAM_DISK_SIZE_PATH).append("\n");
        
        boolean success = executeRootCommand(commands.toString());
        if (success) {
            Log.d(TAG, "zRAM disabled successfully");
        }
        return success;
    }

    /**
     * Get current zRAM size (in MB)
     */
    public static int getZramSize() {
        try {
            String value = readFile(ZRAM_DISK_SIZE_PATH);
            if (value == null || value.isEmpty()) return 0;
            long sizeBytes = Long.parseLong(value.trim());
            return (int) (sizeBytes / (1024 * 1024));
        } catch (Exception e) {
            Log.e(TAG, "Failed to get zRAM size", e);
            return 0;
        }
    }

    /**
     * Set zRAM size (in MB)
     */
    public static boolean setZramSize(int sizeMb) {
        try {
            if (sizeMb < 512 || sizeMb > 8192) {
                Log.e(TAG, "Invalid zRAM size: " + sizeMb);
                return false;
            }

            long sizeBytes = (long) sizeMb * 1024 * 1024;
            
            StringBuilder commands = new StringBuilder();
            
            // Disable existing zRAM
            disableZram();
            commands.append("sleep 0.5\n");
            
            // Set compression algorithm
            String availableAlgos = readFile(ZRAM_COMP_ALGORITHM_PATH);
            String[] algos = {"zstd", "lz4", "lzo-rle", "lzo"};
            String selectedAlgo = "lzo";
            
            if (availableAlgos != null) {
                for (String algo : algos) {
                    if (availableAlgos.contains(algo)) {
                        selectedAlgo = algo;
                        break;
                    }
                }
            }
            
            commands.append("echo ").append(selectedAlgo).append(" > ")
                    .append(ZRAM_COMP_ALGORITHM_PATH).append(" 2>/dev/null\n");
            commands.append("sleep 0.2\n");
            
            // Set size
            commands.append("echo ").append(sizeBytes).append(" > ")
                    .append(ZRAM_DISK_SIZE_PATH).append("\n");
            commands.append("sleep 0.3\n");
            
            // Format and enable
            commands.append("mkswap /dev/block/zram0 2>/dev/null\n");
            commands.append("sleep 0.2\n");
            commands.append("swapon /dev/block/zram0 -p 32767 2>/dev/null\n");
            
            boolean success = executeRootCommand(commands.toString());
            
            if (success) {
                Log.d(TAG, "zRAM configured: " + sizeMb + " MB with " + selectedAlgo);
                // Verify
                Thread.sleep(500);
                int actualSize = getZramSize();
                if (actualSize > 0) {
                    Log.d(TAG, "Verified zRAM size: " + actualSize + " MB");
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set zRAM size", e);
            return false;
        }
    }

    /**
     * Get current swappiness
     */
    public static int getZramSwappiness() {
        try {
            String value = readFile(ZRAM_SWAPPINESS_PATH);
            if (value == null || value.isEmpty()) return 60;
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get swappiness", e);
            return 60;
        }
    }

    /**
     * Set swappiness
     */
    public static boolean setZramSwappiness(int swappiness) {
        try {
            if (swappiness < 0 || swappiness > 200) {
                Log.e(TAG, "Invalid swappiness value: " + swappiness);
                return false;
            }
            
            String command = "echo " + swappiness + " > " + ZRAM_SWAPPINESS_PATH + "\n";
            boolean success = executeRootCommand(command);
            
            if (success) {
                Log.d(TAG, "Swappiness set to: " + swappiness);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set swappiness", e);
            return false;
        }
    }

    /**
     * Get zRAM compression stats
     */
    public static ZramStats getZramStats() {
        ZramStats stats = new ZramStats();
        try {
            String memUsed = readFile(ZRAM_MEM_USED_PATH);
            String origSize = readFile(ZRAM_ORIG_SIZE_PATH);
            
            if (memUsed != null && origSize != null) {
                stats.memUsedTotal = Long.parseLong(memUsed.trim()) / (1024 * 1024);
                stats.origDataSize = Long.parseLong(origSize.trim()) / (1024 * 1024);
                
                if (stats.origDataSize > 0) {
                    stats.compressionRatio = (float) stats.origDataSize / stats.memUsedTotal;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get zRAM stats", e);
        }
        return stats;
    }

    /**
     * Check if app hibernation is enabled
     */
    public static boolean isAppHibernationEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_APP_HIBERNATION, false);
    }

    /**
     * Enable/disable app hibernation
     */
    public static boolean setAppHibernationEnabled(Context context, boolean enabled) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean(PREF_APP_HIBERNATION, enabled).apply();
            
            if (enabled) {
                hibernateUnusedApps(context);
            }
            
            Log.d(TAG, "App hibernation " + (enabled ? "enabled" : "disabled"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set app hibernation", e);
            return false;
        }
    }

    /**
     * Hibernate unused apps
     */
    private static void hibernateUnusedApps(Context context) {
        try {
            UsageStatsManager usm = (UsageStatsManager) 
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -30);
            
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    cal.getTimeInMillis(),
                    System.currentTimeMillis());
            
            Set<String> usedPackages = new HashSet<>();
            if (stats != null) {
                for (UsageStats us : stats) {
                    if (us.getTotalTimeInForeground() > 0) {
                        usedPackages.add(us.getPackageName());
                    }
                }
            }
            
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            int count = 0;
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && 
                    !usedPackages.contains(app.packageName)) {
                    try {
                        pm.setApplicationEnabledSetting(
                                app.packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                0);
                        count++;
                    } catch (Exception e) {
                        // Skip if can't disable
                    }
                }
            }
            
            Log.d(TAG, "Hibernated " + count + " unused apps");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hibernate apps", e);
        }
    }

    /**
     * Get storage statistics
     */
    public static StorageStats getStorageStats(Context context) {
        StorageStats stats = new StorageStats();
        
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            
            stats.totalStorage = (totalBlocks * blockSize) / (1024 * 1024);
            stats.freeStorage = (availableBlocks * blockSize) / (1024 * 1024);
            stats.usedStorage = stats.totalStorage - stats.freeStorage;
            
            // Calculate cleanable sizes
            stats.appCacheSize = calculateAppCacheSize(context);
            stats.systemCacheSize = calculateDirectorySize(new File("/data/cache"));
            stats.thumbnailsSize = calculateDirectorySize(
                    new File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails"));
            stats.downloadsSize = calculateOldDownloadsSize();
            stats.tempFilesSize = calculateDirectorySize(new File("/data/local/tmp"));
            stats.logFilesSize = calculateDirectorySize(new File("/data/log")) +
                                 calculateDirectorySize(new File("/data/tombstones"));
            stats.apkSize = calculateApkFilesSize();
            stats.duplicateFilesSize = calculateDuplicateFilesSize();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get storage stats", e);
        }
        
        return stats;
    }

    private static long calculateAppCacheSize(Context context) {
        long totalSize = 0;
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            
            for (ApplicationInfo app : apps) {
                File cacheDir = new File(app.dataDir, "cache");
                totalSize += calculateDirectorySize(cacheDir);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating app cache size", e);
        }
        return totalSize;
    }

    private static long calculateDirectorySize(File dir) {
        long size = 0;
        try {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            size += calculateDirectorySize(file);
                        } else {
                            size += file.length();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore permission errors
        }
        return size / (1024 * 1024); // Convert to MB
    }

    private static long calculateOldDownloadsSize() {
        long size = 0;
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloadsDir.listFiles();
            if (files != null) {
                long thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                for (File file : files) {
                    if (file.lastModified() < thirtyDaysAgo) {
                        size += file.length();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate old downloads size", e);
        }
        return size / (1024 * 1024);
    }

    private static long calculateApkFilesSize() {
        long size = 0;
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloadsDir.listFiles((dir, name) -> name.endsWith(".apk"));
            if (files != null) {
                for (File file : files) {
                    size += file.length();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate APK files size", e);
        }
        return size / (1024 * 1024);
    }

    private static long calculateDuplicateFilesSize() {
        long size = 0;
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            Map<String, List<File>> hashMap = new HashMap<>();
            scanForDuplicates(sdcard, hashMap);
            
            for (List<File> dups : hashMap.values()) {
                if (dups.size() > 1) {
                    for (int i = 1; i < dups.size(); i++) {
                        size += dups.get(i).length();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate duplicate files size", e);
        }
        return size / (1024 * 1024);
    }

    private static void scanForDuplicates(File dir, Map<String, List<File>> hashMap) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles(pathname -> 
                !pathname.getName().startsWith(".") &&
                (pathname.isDirectory() || pathname.length() > 1024)); // Only files > 1KB
        
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanForDuplicates(file, hashMap);
                } else {
                    try {
                        String hash = file.length() + "_" + file.getName();
                        hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                    } catch (Exception e) {
                        // Skip file
                    }
                }
            }
        }
    }

    /**
     * Clean app cache
     */
    public static CleanResult cleanAppCache(Context context) {
        CleanResult result = new CleanResult();
        try {
            long sizeBefore = calculateAppCacheSize(context);
            
            String commands = "find /data/data/*/cache -type f -delete 2>/dev/null\n" +
                            "find /data/data/*/cache -type d -empty -delete 2>/dev/null\n";
            result.success = executeRootCommand(commands);
            
            if (result.success) {
                result.freedSpace = sizeBefore;
                result.message = String.format("Cleaned %d MB of app cache", result.freedSpace);
            } else {
                result.message = "Failed to clean app cache";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean app cache", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean system cache
     */
    public static CleanResult cleanSystemCache() {
        CleanResult result = new CleanResult();
        try {
            long sizeBefore = calculateDirectorySize(new File("/data/cache"));
            
            String commands = "sync\n" +
                            "echo 3 > /proc/sys/vm/drop_caches\n" +
                            "rm -rf /data/cache/* 2>/dev/null\n" +
                            "sync\n";
            result.success = executeRootCommand(commands);
            
            if (result.success) {
                result.freedSpace = sizeBefore;
                result.message = String.format("Cleaned %d MB of system cache", result.freedSpace);
            } else {
                result.message = "Failed to clean system cache";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean system cache", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean thumbnails
     */
    public static CleanResult cleanThumbnails() {
        CleanResult result = new CleanResult();
        try {
            File thumbDir = new File(Environment.getExternalStorageDirectory(), 
                    "DCIM/.thumbnails");
            long sizeBefore = calculateDirectorySize(thumbDir);
            
            deleteRecursive(thumbDir);
            
            result.success = true;
            result.freedSpace = sizeBefore;
            result.message = String.format("Cleaned %d MB of thumbnails", result.freedSpace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean thumbnails", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean old downloads
     */
    public static CleanResult cleanOldDownloads() {
        CleanResult result = new CleanResult();
        long freed = 0;
        int count = 0;
        
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloadsDir.listFiles();
            
            if (files != null) {
                long thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                for (File file : files) {
                    if (file.lastModified() < thirtyDaysAgo) {
                        freed += file.length();
                        if (file.delete()) {
                            count++;
                        }
                    }
                }
            }
            
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = String.format("Removed %d old files, freed %d MB", 
                    count, result.freedSpace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean old downloads", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean temp files
     */
    public static CleanResult cleanTempFiles() {
        CleanResult result = new CleanResult();
        try {
            long sizeBefore = calculateDirectorySize(new File("/data/local/tmp"));
            
            String commands = "rm -rf /data/local/tmp/* 2>/dev/null\n";
            result.success = executeRootCommand(commands);
            
            if (result.success) {
                result.freedSpace = sizeBefore;
                result.message = String.format("Cleaned %d MB of temp files", result.freedSpace);
            } else {
                result.message = "Failed to clean temp files";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean temp files", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean log files
     */
    public static CleanResult cleanLogFiles() {
        CleanResult result = new CleanResult();
        try {
            long sizeBefore = calculateDirectorySize(new File("/data/log")) +
                            calculateDirectorySize(new File("/data/tombstones"));
            
            String commands = "rm -rf /data/log/* /data/tombstones/* 2>/dev/null\n";
            result.success = executeRootCommand(commands);
            
            if (result.success) {
                result.freedSpace = sizeBefore;
                result.message = String.format("Cleaned %d MB of log files", result.freedSpace);
            } else {
                result.message = "Failed to clean log files";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean log files", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean APK files
     */
    public static CleanResult cleanApkFiles() {
        CleanResult result = new CleanResult();
        long freed = 0;
        int count = 0;
        
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloadsDir.listFiles((dir, name) -> name.endsWith(".apk"));
            
            if (files != null) {
                for (File file : files) {
                    freed += file.length();
                    if (file.delete()) {
                        count++;
                    }
                }
            }
            
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = String.format("Removed %d APK files, freed %d MB", 
                    count, result.freedSpace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean APK files", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean empty folders
     */
    public static CleanResult cleanEmptyFolders() {
        CleanResult result = new CleanResult();
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            int count = deleteEmptyFolders(sdcard);
            
            result.success = true;
            result.message = String.format("Removed %d empty folders", count);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean empty folders", e);
            result.message = e.getMessage();
        }
        return result;
    }

    private static int deleteEmptyFolders(File dir) {
        int count = 0;
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    count += deleteEmptyFolders(file);
                    File[] contents = file.listFiles();
                    if (contents == null || contents.length == 0) {
                        if (file.delete()) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Clean duplicate files
     */
    public static CleanResult cleanDuplicateFiles() {
        CleanResult result = new CleanResult();
        long freed = 0;
        int count = 0;
        
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            Map<String, List<File>> hashMap = new HashMap<>();
            scanForDuplicates(sdcard, hashMap);
            
            for (List<File> dups : hashMap.values()) {
                if (dups.size() > 1) {
                    for (int i = 1; i < dups.size(); i++) {
                        freed += dups.get(i).length();
                        if (dups.get(i).delete()) {
                            count++;
                        }
                    }
                }
            }
            
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = String.format("Removed %d duplicate files, freed %d MB", 
                    count, result.freedSpace);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean duplicate files", e);
            result.message = e.getMessage();
        }
        return result;
    }

    /**
     * Clean all
     */
    public static CleanResult cleanAll(Context context) {
        CleanResult result = new CleanResult();
        long totalFreed = 0;
        StringBuilder message = new StringBuilder("Cleanup Results:\n");
        
        CleanResult appCache = cleanAppCache(context);
        totalFreed += appCache.freedSpace;
        message.append("• App Cache: ").append(appCache.freedSpace).append(" MB\n");
        
        CleanResult systemCache = cleanSystemCache();
        totalFreed += systemCache.freedSpace;
        message.append("• System Cache: ").append(systemCache.freedSpace).append(" MB\n");
        
        CleanResult thumbnails = cleanThumbnails();
        totalFreed += thumbnails.freedSpace;
        message.append("• Thumbnails: ").append(thumbnails.freedSpace).append(" MB\n");
        
        CleanResult downloads = cleanOldDownloads();
        totalFreed += downloads.freedSpace;
        message.append("• Old Downloads: ").append(downloads.freedSpace).append(" MB\n");
        
        CleanResult tempFiles = cleanTempFiles();
        totalFreed += tempFiles.freedSpace;
        message.append("• Temp Files: ").append(tempFiles.freedSpace).append(" MB\n");
        
        CleanResult logFiles = cleanLogFiles();
        totalFreed += logFiles.freedSpace;
        message.append("• Log Files: ").append(logFiles.freedSpace).append(" MB\n");
        
        CleanResult apkFiles = cleanApkFiles();
        totalFreed += apkFiles.freedSpace;
        message.append("• APK Files: ").append(apkFiles.freedSpace).append(" MB\n");
        
        CleanResult emptyFolders = cleanEmptyFolders();
        message.append("• ").append(emptyFolders.message).append("\n");
        
        CleanResult duplicates = cleanDuplicateFiles();
        totalFreed += duplicates.freedSpace;
        message.append("• Duplicates: ").append(duplicates.freedSpace).append(" MB\n");
        
        message.append("\nTotal Freed: ").append(totalFreed).append(" MB");
        
        result.success = true;
        result.freedSpace = totalFreed;
        result.message = message.toString();
        
        // Update last clean time
        if (context != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putLong(PREF_LAST_CLEAN_TIME, System.currentTimeMillis()).apply();
        }
        
        return result;
    }

    /**
     * Get RAM statistics
     */
    public static RamStats getRamStatistics() {
        int totalRam = 0;
        int freeRam = 0;
        int usedRam = 0;
        int availableRam = 0;
        int cachedRam = 0;
        
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.split("\\s+");
                    totalRam = Integer.parseInt(parts[1]) / 1024;
                } else if (line.startsWith("MemFree:")) {
                    String[] parts = line.split("\\s+");
                    freeRam = Integer.parseInt(parts[1]) / 1024;
                } else if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.split("\\s+");
                    availableRam = Integer.parseInt(parts[1]) / 1024;
                } else if (line.startsWith("Cached:")) {
                    String[] parts = line.split("\\s+");
                    cachedRam = Integer.parseInt(parts[1]) / 1024;
                }
            }
            reader.close();
            
            usedRam = totalRam - availableRam;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to read memory info", e);
        }
        
        boolean zramEnabled = isZramEnabled();
        int zramSize = getZramSize();
        ZramStats zramStats = getZramStats();
        
        return new RamStats(totalRam, usedRam, freeRam, availableRam, cachedRam,
                zramEnabled, zramSize, zramStats, false);
    }

    /**
     * Save preferences
     */
    public static void savePreferences(Context context) {
        if (!isSupported() || context == null) {
            return;
        }
        
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            
            editor.putBoolean(PREF_ZRAM_ENABLED, isZramEnabled());
            editor.putInt(PREF_ZRAM_SIZE, getZramSize());
            editor.putInt(PREF_ZRAM_SWAPPINESS, getZramSwappiness());
            
            editor.apply();
            Log.d(TAG, "Preferences saved");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save preferences", e);
        }
    }

    /**
     * Restore preferences on boot
     */
    public static void restorePreferences(Context context) {
        if (!isSupported() || context == null) {
            return;
        }
        
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            
            // Restore swappiness first
            if (prefs.contains(PREF_ZRAM_SWAPPINESS)) {
                int swappiness = prefs.getInt(PREF_ZRAM_SWAPPINESS, 60);
                setZramSwappiness(swappiness);
            }
            
            // Restore zRAM
            boolean shouldEnable = prefs.getBoolean(PREF_ZRAM_ENABLED, false);
            if (shouldEnable) {
                int size = prefs.getInt(PREF_ZRAM_SIZE, 1024);
                setZramSize(size);
            }
            
            Log.d(TAG, "Preferences restored");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore preferences", e);
        }
    }

    /**
     * Execute command with root privileges
     */
    private static boolean executeRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader reader = null;
        
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute root command", e);
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (reader != null) reader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Read file content
     */
    private static String readFile(String path) {
        BufferedReader reader = null;
        try {
            File file = new File(path);
            if (!file.exists()) return null;
            
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line != null ? line.trim() : null;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Delete directory recursively
     */
    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    /**
     * Data class for RAM statistics
     */
    public static class RamStats {
        public final int totalRam;
        public final int usedRam;
        public final int freeRam;
        public final int availableRam;
        public final int cachedRam;
        public final boolean zramEnabled;
        public final int zramSize;
        public final ZramStats zramStats;
        public final boolean appHibernationEnabled;
        
        public RamStats(int totalRam, int usedRam, int freeRam, int availableRam, int cachedRam,
                       boolean zramEnabled, int zramSize, ZramStats zramStats, 
                       boolean appHibernationEnabled) {
            this.totalRam = totalRam;
            this.usedRam = usedRam;
            this.freeRam = freeRam;
            this.availableRam = availableRam;
            this.cachedRam = cachedRam;
            this.zramEnabled = zramEnabled;
            this.zramSize = zramSize;
            this.zramStats = zramStats;
            this.appHibernationEnabled = appHibernationEnabled;
        }
    }

    /**
     * Data class for zRAM statistics
     */
    public static class ZramStats {
        public long memUsedTotal = 0;
        public long origDataSize = 0;
        public float compressionRatio = 0;
        
        public String getCompressionRatioString() {
            if (compressionRatio > 0) {
                return String.format("%.2f:1", compressionRatio);
            }
            return "N/A";
        }
    }

    /**
     * Data class for storage statistics
     */
    public static class StorageStats {
        public long totalStorage = 0;
        public long usedStorage = 0;
        public long freeStorage = 0;
        public long appCacheSize = 0;
        public long systemCacheSize = 0;
        public long thumbnailsSize = 0;
        public long downloadsSize = 0;
        public long tempFilesSize = 0;
        public long logFilesSize = 0;
        public long apkSize = 0;
        public long emptyFoldersSize = 0;
        public long duplicateFilesSize = 0;
        
        public long getTotalCleanable() {
            return appCacheSize + systemCacheSize + thumbnailsSize + 
                   downloadsSize + tempFilesSize + logFilesSize + 
                   apkSize + duplicateFilesSize;
        }
    }

    /**
     * Data class for clean results
     */
    public static class CleanResult {
        public boolean success = false;
        public long freedSpace = 0;
        public String message = "";
    }
}
