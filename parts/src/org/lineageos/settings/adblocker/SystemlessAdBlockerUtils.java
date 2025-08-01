package org.lineageos.settings.adblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class SystemlessAdBlockerUtils {
    private static final String TAG = "SystemlessAdBlocker";
    private static final String MAGISK_MODULE_PATH = "/data/adb/modules/adblocker_hosts";
    private static final String KSU_MODULE_PATH = "/data/adb/ksu/modules/adblocker_hosts";
    private static final String MODULE_HOSTS_PATH = "/system/etc/hosts";
    private static final String HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    
    private Context mContext;
    private SharedPreferences mPrefs;
    private String mModulePath;

    public SystemlessAdBlockerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        detectModulePath();
    }

    private void detectModulePath() {
        // Check for KernelSU first, then Magisk
        if (new File(KSU_MODULE_PATH).exists() || isKSUAvailable()) {
            mModulePath = KSU_MODULE_PATH;
            Log.d(TAG, "Using KernelSU module path");
        } else if (new File("/data/adb/magisk").exists() || isMagiskAvailable()) {
            mModulePath = MAGISK_MODULE_PATH;
            Log.d(TAG, "Using Magisk module path");
        } else {
            mModulePath = null;
            Log.w(TAG, "No supported root manager found");
        }
    }

    private boolean isKSUAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "which ksud"});
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMagiskAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "which magisk"});
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public interface UpdateCallback {
        void onUpdateStart();
        void onUpdateSuccess(int blockedCount);
        void onUpdateError(String error);
    }

    public boolean isEnabled() {
        if (mModulePath == null) return false;
        
        // Check if module exists and is enabled
        File moduleDir = new File(mModulePath);
        File disableFile = new File(mModulePath, "disable");
        File removeFile = new File(mModulePath, "remove");
        
        return moduleDir.exists() && !disableFile.exists() && !removeFile.exists();
    }

    public boolean hasRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }

    public boolean enableAdBlocker() {
        if (!hasRootAccess() || mModulePath == null) {
            return false;
        }

        try {
            createModule();
            setEnabled(true);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable adblocker", e);
            return false;
        }
    }

    public boolean disableAdBlocker() {
        if (!hasRootAccess() || mModulePath == null) {
            return false;
        }

        try {
            disableModule();
            setEnabled(false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable adblocker", e);
            return false;
        }
    }

    private void createModule() throws Exception {
        // Create module directory structure
        Process process = Runtime.getRuntime().exec(new String[]{
            "su", "-c",
            "mkdir -p " + mModulePath + "/system/etc && " +
            "echo 'id=adblocker_hosts' > " + mModulePath + "/module.prop && " +
            "echo 'name=AdBlocker Hosts' >> " + mModulePath + "/module.prop && " +
            "echo 'version=v1.0' >> " + mModulePath + "/module.prop && " +
            "echo 'versionCode=1' >> " + mModulePath + "/module.prop && " +
            "echo 'author=LineageOS Settings' >> " + mModulePath + "/module.prop && " +
            "echo 'description=Systemless hosts file for ad blocking' >> " + mModulePath + "/module.prop"
        });
        
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new Exception("Failed to create module structure");
        }
    }

    private void disableModule() throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{
            "su", "-c", "touch " + mModulePath + "/disable"
        });
        
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new Exception("Failed to disable module");
        }
    }

    public void updateHostsFile(UpdateCallback callback) {
        new UpdateHostsTask(callback).execute();
    }

    public void updateHostsFileFromContent(String hostsContent, UpdateCallback callback) {
        new UpdateHostsFromContentTask(callback).execute(hostsContent);
    }

    private class UpdateHostsTask extends AsyncTask<Void, Void, String> {
        private UpdateCallback mCallback;
        private int mBlockedCount = 0;
        private String mError = null;

        public UpdateHostsTask(UpdateCallback callback) {
            mCallback = callback;
        }

        @Override
        protected void onPreExecute() {
            if (mCallback != null) {
                mCallback.onUpdateStart();
            }
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if (!hasRootAccess() || mModulePath == null) {
                    mError = "Root access or module path not available";
                    return null;
                }

                // Download hosts file
                String hostsContent = downloadHostsFile();
                if (hostsContent == null) {
                    mError = "Failed to download hosts file";
                    return null;
                }

                // Apply hosts file to module
                return applyHostsFileToModule(hostsContent);
            } catch (Exception e) {
                Log.e(TAG, "Update failed", e);
                mError = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && mCallback != null) {
                mPrefs.edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .putInt(PREF_BLOCKED_COUNT, mBlockedCount)
                    .apply();
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                mCallback.onUpdateError(mError != null ? mError : "Unknown error");
            }
        }

        private String downloadHostsFile() {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(HOSTS_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "SystemlessAdBlocker/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }

                reader.close();
                return content.toString();
                
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private String applyHostsFileToModule(String hostsContent) throws Exception {
            mBlockedCount = countBlockedDomains(hostsContent);

            // Create temporary file
            File tempFile = new File(mContext.getCacheDir(), "hosts_systemless");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(hostsContent);
            }

            // Copy to module directory
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "mkdir -p " + mModulePath + "/system/etc && " +
                "cp " + tempFile.getAbsolutePath() + " " + mModulePath + "/system/etc/hosts && " +
                "chmod 644 " + mModulePath + "/system/etc/hosts && " +
                "rm -f " + mModulePath + "/disable"  // Remove disable flag if exists
            });

            process.waitFor();
            tempFile.delete();

            if (process.exitValue() != 0) {
                throw new Exception("Failed to apply hosts file to module");
            }

            return "Success";
        }
    }

    private class UpdateHostsFromContentTask extends AsyncTask<String, Void, String> {
        private UpdateCallback mCallback;
        private int mBlockedCount = 0;
        private String mError = null;

        public UpdateHostsFromContentTask(UpdateCallback callback) {
            mCallback = callback;
        }

        @Override
        protected void onPreExecute() {
            if (mCallback != null) {
                mCallback.onUpdateStart();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                if (!hasRootAccess() || mModulePath == null) {
                    mError = "Root access or module path not available";
                    return null;
                }

                String hostsContent = params[0];
                if (hostsContent == null || hostsContent.trim().isEmpty()) {
                    mError = "Invalid hosts content";
                    return null;
                }

                return applyHostsFileToModule(hostsContent);
            } catch (Exception e) {
                Log.e(TAG, "Manual update failed", e);
                mError = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && mCallback != null) {
                mPrefs.edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .putInt(PREF_BLOCKED_COUNT, mBlockedCount)
                    .apply();
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                mCallback.onUpdateError(mError != null ? mError : "Unknown error");
            }
        }

        private String applyHostsFileToModule(String hostsContent) throws Exception {
            mBlockedCount = countBlockedDomains(hostsContent);

            File tempFile = new File(mContext.getCacheDir(), "hosts_manual_systemless");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(hostsContent);
            }

            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "mkdir -p " + mModulePath + "/system/etc && " +
                "cp " + tempFile.getAbsolutePath() + " " + mModulePath + "/system/etc/hosts && " +
                "chmod 644 " + mModulePath + "/system/etc/hosts && " +
                "rm -f " + mModulePath + "/disable"
            });

            process.waitFor();
            tempFile.delete();

            if (process.exitValue() != 0) {
                throw new Exception("Failed to apply manual hosts file to module");
            }

            return "Success";
        }
    }

    public String getLastUpdateTime() {
        long timestamp = mPrefs.getLong(PREF_LAST_UPDATE, 0);
        if (timestamp == 0) {
            return "Never updated";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public int getBlockedDomainsCount() {
        return mPrefs.getInt(PREF_BLOCKED_COUNT, 0);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean("adblocker_enabled", enabled).apply();
    }

    public boolean isValidHostsFile(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String[] lines = content.split("\n");
        int validEntries = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            if (line.matches("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+\\S+.*")) {
                validEntries++;
                if (validEntries >= 5) {
                    return true;
                }
            }
        }
        
        return validEntries > 0;
    }

    private int countBlockedDomains(String hostsContent) {
        int count = 0;
        String[] lines = hostsContent.split("\n");
        Pattern blockedPattern = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+)");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#") && blockedPattern.matcher(line).matches()) {
                if (!line.contains("localhost") && !line.contains("local") && 
                    !line.contains("broadcasthost") && !line.contains("0.0.0.0 0.0.0.0")) {
                    count++;
                }
            }
        }
        
        return count;
    }

    public String getModuleInfo() {
        if (mModulePath == null) {
            return "No supported root manager found";
        }
        
        String rootManager = mModulePath.contains("ksu") ? "KernelSU" : "Magisk";
        return "Using " + rootManager + " systemless module";
    }
}
