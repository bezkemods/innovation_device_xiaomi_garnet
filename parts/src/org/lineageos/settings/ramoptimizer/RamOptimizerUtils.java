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

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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

    // I/O Scheduler paths - SM7435 UFS storage uses /sys/block/sda
    private static final String IO_SCHEDULER_PATH = "/sys/block/sda/queue/scheduler";
    private static final String PREF_IO_SCHEDULER = "io_scheduler";
    
    // Preference keys
    private static final String PREF_ZRAM_ENABLED = "zram_enable";
    private static final String PREF_ZRAM_SIZE = "zram_size";
    private static final String PREF_ZRAM_SWAPPINESS = "zram_swappiness";
    private static final String PREF_ZRAM_ALGO = "zram_compression_algorithm";
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
    public static boolean setZramEnabled(Context context, boolean enabled) {
        try {
            if (enabled) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                int size = prefs.getInt(PREF_ZRAM_SIZE, 4096); // Default 4GB for SM7435
                return setZramSize(context, size);
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
    public static boolean setZramSize(Context context, int sizeMb) {
        try {
            if (sizeMb < 512 || sizeMb > 12288) { // Updated max size for 12/16GB models
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
            // Updated priority for SM7435: zstd (balance) > lz4 (speed) > lzo (legacy)
            String[] algos = {"zstd", "lz4", "lzo-rle", "lzo"};
            String preferredAlgo = "zstd";
            
            if (context != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                preferredAlgo = prefs.getString(PREF_ZRAM_ALGO, "zstd");
            }
            
            String selectedAlgo = "lzo"; // Fallback

            if (availableAlgos != null) {
                if (availableAlgos.contains(preferredAlgo)) {
                    selectedAlgo = preferredAlgo;
                } else {
                    for (String algo : algos) {
                        if (availableAlgos.contains(algo)) {
                            selectedAlgo = algo;
                            break;
                        }
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
            if (value == null || value.isEmpty()) return 60; // Standard default
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
     * Get current compression algorithm
     */
    public static String getZramCompressionAlgorithm() {
        try {
            String value = readFile(ZRAM_COMP_ALGORITHM_PATH);
            if (value == null) return "lzo";
            
            String[] parts = value.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("[") && part.endsWith("]")) {
                    return part.substring(1, part.length() - 1);
                }
            }
            return "lzo";
        } catch (Exception e) {
            Log.e(TAG, "Failed to get compression algorithm", e);
            return "lzo";
        }
    }

    /**
     * Set compression algorithm
     */
    public static boolean setZramCompressionAlgorithm(Context context, String algo) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString(PREF_ZRAM_ALGO, algo).apply();

            if (isZramEnabled()) {
                // Changing algo requires reset, re-enable to apply
                setZramEnabled(context, false);
                setZramEnabled(context, true);
            } else {
                executeRootCommand("echo " + algo + " > " + ZRAM_COMP_ALGORITHM_PATH);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set compression algorithm", e);
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
     * Get current LMK profile
     */
    public static String getLmkProfile(Context context) {
        try {
            String minfree = readFile(LMK_MINFREE_PATH);
            if (minfree != null) {
                minfree = minfree.trim();
                if (minfree.equals("0,0,0,0,0,0")) {
                    return "disabled";
                } else if (minfree.equals("18432,23040,27648,32256,55296,80640")) {
                    return "basic";
                } else if (minfree.equals("27648,32256,49152,65536,98304,131072")) {
                    return "aggressive";
                } else {
                    return "balanced";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get LMK profile", e);
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_LMK_PROFILE, "balanced");
    }

    /**
     * Set LMK profile
     */
    public static boolean setLmkProfile(Context context, String profile) {
        try {
            String minfree;
            String adaptiveLmk = "1";
            
            // Profiles tuned for 8GB-12GB RAM devices
            switch (profile) {
                case "disabled":
                    minfree = "0,0,0,0,0,0";
                    adaptiveLmk = "0";
                    break;
                case "basic": // Conservative
                    minfree = "18432,23040,27648,32256,55296,80640"; 
                    break;
                case "aggressive": // Keep more free RAM
                    minfree = "27648,32256,49152,65536,98304,131072";
                    break;
                case "balanced":
                default: // Standard for mid-range
                    minfree = "21816,29088,36360,43632,58176,72720";
                    profile = "balanced";
                    break;
            }
            
            StringBuilder commands = new StringBuilder();
            
            // Check if files exist before writing
            File minfreeFile = new File(LMK_MINFREE_PATH);
            File adaptiveFile = new File(LMK_ADAPTIVE_PATH);
            
            if (minfreeFile.exists()) {
                commands.append("echo ").append(minfree).append(" > ").append(LMK_MINFREE_PATH).append("\n");
            }
            
            if (adaptiveFile.exists()) {
                commands.append("echo ").append(adaptiveLmk).append(" > ").append(LMK_ADAPTIVE_PATH).append("\n");
            }
            
            boolean success = true;
            if (commands.length() > 0) {
                success = executeRootCommand(commands.toString());
            }
            
            if (success) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                prefs.edit().putString(PREF_LMK_PROFILE, profile).apply();
                Log.d(TAG, "LMK profile set to " + profile);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set LMK profile", e);
            return false;
        }
    }

    /**
     * Get current I/O scheduler
     */
    public static String getIoScheduler() {
        try {
            String value = readFile(IO_SCHEDULER_PATH);
            if (value == null) return "mq-deadline"; // Default for newer kernels
            
            // Parse format: "noop deadline [mq-deadline] kyber"
            String[] parts = value.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("[") && part.endsWith("]")) {
                    return part.substring(1, part.length() - 1);
                }
            }
            return "mq-deadline";
        } catch (Exception e) {
            Log.e(TAG, "Failed to get I/O scheduler", e);
            return "mq-deadline";
        }
    }

    /**
     * Set I/O scheduler
     */
    public static boolean setIoScheduler(String scheduler) {
        try {
            // Prioritize UFS storage (sda) for Redmi Note 13 Pro 5G
            String[] devicePaths = {
                "/sys/block/sda/queue/scheduler",     // UFS
                "/sys/block/sdb/queue/scheduler",     // UFS
                "/sys/block/mmcblk0/queue/scheduler", // eMMC (Legacy)
                "/sys/block/nvme0n1/queue/scheduler"  // NVMe
            };
        
            boolean success = false;
            for (String path : devicePaths) {
                File schedulerFile = new File(path);
                if (schedulerFile.exists()) {
                    String command = "echo " + scheduler + " > " + path + "\n";
                    if (executeRootCommand(command)) {
                        Log.d(TAG, "I/O scheduler set to " + scheduler + " for " + path);
                        success = true;
                    }
                }
            }
        
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set I/O scheduler", e);
            return false;
        }
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
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !usedPackages.contains(app.packageName)) {
                    executeRootCommand("am force-stop " + app.packageName);
                    count++;
                }
            }
            Log.d(TAG, "Hibernated " + count + " unused apps");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hibernate apps", e);
        }
    }

    /**
     * Get storage stats
     */
    public static StorageStats getStorageStats(Context context) {
        StorageStats stats = new StorageStats();
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = statFs.getBlockSizeLong();
            stats.totalStorage = statFs.getBlockCountLong() * blockSize / (1024 * 1024);
            stats.freeStorage = statFs.getAvailableBlocksLong() * blockSize / (1024 * 1024);
            stats.usedStorage = stats.totalStorage - stats.freeStorage;

            // App cache size — pm.getPackageSizeInfo() is a hidden/removed API.
            // Approximate from the cache directory sizes instead.
            stats.appCacheSize = getDirSize(new File("/data/data")) > 0
                    ? getDirSize(new File("/data/data")) / (1024 * 1024) : 0;

            // System cache - approximate
            stats.systemCacheSize = getDirSize(new File("/data/cache")) / (1024 * 1024);

            // Thumbnails
            File thumbnails = new File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails");
            stats.thumbnailsSize = getDirSize(thumbnails) / (1024 * 1024);

            // Old downloads
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            stats.downloadsSize = getOldFilesSize(downloads, 30) / (1024 * 1024);

            // Temp files
            stats.tempFilesSize = (getDirSize(new File("/data/local/tmp")) + getDirSize(new File("/tmp"))) / (1024 * 1024);

            // Log files
            stats.logFilesSize = (getDirSize(new File("/data/log")) + getDirSize(new File("/data/tombstones"))) / (1024 * 1024);

            // APK files
            stats.apkSize = getFileTypeSize(downloads, ".apk") / (1024 * 1024);

            // Duplicates
            stats.duplicateFilesSize = getDuplicatesSize(Environment.getExternalStorageDirectory()) / (1024 * 1024);

        } catch (Exception e) {
            Log.e(TAG, "Failed to get storage stats", e);
        }
        return stats;
    }

    private static long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += getDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    private static long getOldFilesSize(File dir, int days) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoff) {
                    size += file.isDirectory() ? getDirSize(file) : file.length();
                }
            }
        }
        return size;
    }

    private static long getFileTypeSize(File dir, String extension) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(extension);
            }
        });
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }

    private static long getDuplicatesSize(File dir) {
        long size = 0;
        Map<String, List<File>> hashMap = new HashMap<>();
        scanForDuplicates(dir, hashMap);
        for (List<File> dups : hashMap.values()) {
            if (dups.size() > 1) {
                for (int i = 1; i < dups.size(); i++) {
                    size += dups.get(i).length();
                }
            }
        }
        return size;
    }

    private static void scanForDuplicates(File dir, Map<String, List<File>> hashMap) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanForDuplicates(file, hashMap);
                } else {
                    String hash = getFileHash(file);
                    hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                }
            }
        }
    }

    private static String getFileHash(File file) {
        return file.length() + "_" + file.getName();
    }

    /**
     * Clean app cache
     */
    public static CleanResult cleanAppCache(Context context) {
        CleanResult result = new CleanResult();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            long freed = 0;
            for (ApplicationInfo app : apps) {
                pm.deleteApplicationCacheFiles(app.packageName, null);
                freed += 1;
            }
            result.success = true;
            result.freedSpace = freed;
            result.message = "Cleared app cache, freed " + freed + " MB";
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
            executeRootCommand("echo 3 > /proc/sys/vm/drop_caches\n" +
                    "rm -rf /data/cache/*\n");
            result.success = true;
            result.freedSpace = 0;
            result.message = "System cache cleared";
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
            File thumbnails = new File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails");
            long freed = getDirSize(thumbnails);
            deleteRecursive(thumbnails);
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = "Cleared thumbnails, freed " + result.freedSpace + " MB";
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
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
            File[] files = downloads.listFiles();
            long freed = 0;
            int count = 0;
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() < cutoff) {
                        freed += file.length();
                        if (file.delete()) count++;
                    }
                }
            }
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = "Removed " + count + " old downloads, freed " + result.freedSpace + " MB";
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
            File tmp1 = new File("/data/local/tmp");
            File tmp2 = new File("/tmp");
            long freed = getDirSize(tmp1) + getDirSize(tmp2);
            deleteRecursive(tmp1);
            deleteRecursive(tmp2);
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = "Cleared temp files, freed " + result.freedSpace + " MB";
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
            File log1 = new File("/data/log");
            File log2 = new File("/data/tombstones");
            long freed = getDirSize(log1) + getDirSize(log2);
            deleteRecursive(log1);
            deleteRecursive(log2);
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = "Cleared log files, freed " + result.freedSpace + " MB";
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
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] apks = downloads.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().toLowerCase().endsWith(".apk");
                }
            });
            long freed = 0;
            int count = 0;
            if (apks != null) {
                for (File apk : apks) {
                    freed += apk.length();
                    if (apk.delete()) count++;
                }
            }
            result.success = true;
            result.freedSpace = freed / (1024 * 1024);
            result.message = "Removed " + count + " APK files, freed " + result.freedSpace + " MB";
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
            result.message = "Removed " + count + " empty folders";
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
                if (file.isDirectory()) {
                    count += deleteEmptyFolders(file);
                    if (file.list().length == 0) {
                        if (file.delete()) count++;
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
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            Map<String, List<File>> hashMap = new HashMap<>();
            scanForDuplicates(sdcard, hashMap);

            long freed = 0;
            int count = 0;
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
            editor.putString(PREF_ZRAM_ALGO, getZramCompressionAlgorithm());
            editor.putString(PREF_LMK_PROFILE, getLmkProfile(context));
            editor.putString(PREF_IO_SCHEDULER, getIoScheduler());

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

            // Restore zRAM algo
            if (prefs.contains(PREF_ZRAM_ALGO)) {
                String algo = prefs.getString(PREF_ZRAM_ALGO, "zstd");
                setZramCompressionAlgorithm(context, algo);
            }

            // Restore zRAM
            boolean shouldEnable = prefs.getBoolean(PREF_ZRAM_ENABLED, false);
            if (shouldEnable) {
                int size = prefs.getInt(PREF_ZRAM_SIZE, 4096);
                setZramSize(context, size);
            }

            // Restore LMK profile
            if (prefs.contains(PREF_LMK_PROFILE)) {
                String profile = prefs.getString(PREF_LMK_PROFILE, "balanced");
                setLmkProfile(context, profile);
            }

            // Restore I/O Scheduler
            if (prefs.contains(PREF_IO_SCHEDULER)) {
                String scheduler = prefs.getString(PREF_IO_SCHEDULER, "mq-deadline");
                setIoScheduler(scheduler);
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
