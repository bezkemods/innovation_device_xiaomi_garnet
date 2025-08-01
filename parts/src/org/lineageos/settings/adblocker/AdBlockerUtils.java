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

public class AdBlockerUtils {
    private static final String TAG = "AdBlockerUtils";
    private static final String HOSTS_FILE_PATH = "/system/etc/hosts";
    private static final String HOSTS_BACKUP_PATH = "/system/etc/hosts.backup";
    private static final String HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    
    private Context mContext;
    private SharedPreferences mPrefs;

    public AdBlockerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public interface UpdateCallback {
        void onUpdateStart();
        void onUpdateSuccess(int blockedCount);
        void onUpdateError(String error);
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean("adblocker_enabled", false);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean("adblocker_enabled", enabled).apply();
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

    public boolean hasRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            process.waitFor();
            int exitValue = process.exitValue();
            Log.d(TAG, "Root check exit value: " + exitValue);
            return exitValue == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }

    public boolean isSystemMounted() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "mount -o remount,rw /system"});
            process.waitFor();
            int exitValue = process.exitValue();
            Log.d(TAG, "System mount exit value: " + exitValue);
            return exitValue == 0;
        } catch (Exception e) {
            Log.e(TAG, "System mount check failed", e);
            return false;
        }
    }

    public boolean isValidHostsFile(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check if it contains at least some host entries
        String[] lines = content.split("\n");
        int validEntries = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Check for valid host entry format
            if (line.matches("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+\\S+.*")) {
                validEntries++;
                if (validEntries >= 5) { // At least 5 valid entries
                    return true;
                }
            }
        }
        
        return validEntries > 0;
    }

    public boolean enableAdBlocker() {
        if (!hasRootAccess()) {
            Log.e(TAG, "No root access");
            return false;
        }

        if (!isSystemMounted()) {
            Log.e(TAG, "System not writable");
            return false;
        }

        try {
            // Create backup if it doesn't exist
            createBackup();
            setEnabled(true);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable adblocker", e);
            return false;
        }
    }

    public boolean disableAdBlocker() {
        if (!hasRootAccess()) {
            Log.e(TAG, "No root access");
            return false;
        }

        if (!isSystemMounted()) {
            Log.e(TAG, "System not writable");
            return false;
        }

        try {
            restoreBackup();
            setEnabled(false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable adblocker", e);
            return false;
        }
    }

    private void createBackup() throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{
            "su", "-c", 
            "if [ ! -f " + HOSTS_BACKUP_PATH + " ]; then " +
            "cp " + HOSTS_FILE_PATH + " " + HOSTS_BACKUP_PATH + " && " +
            "chmod 644 " + HOSTS_BACKUP_PATH + "; fi"
        });
        
        process.waitFor();
        int exitValue = process.exitValue();
        Log.d(TAG, "Backup creation exit value: " + exitValue);
        
        if (exitValue != 0) {
            // Read error output
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            errorReader.close();
            
            Log.e(TAG, "Backup creation error: " + errorOutput.toString());
            throw new Exception("Failed to create backup: " + errorOutput.toString());
        }
    }

    private void restoreBackup() throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{
            "su", "-c",
            "if [ -f " + HOSTS_BACKUP_PATH + " ]; then " +
            "cp " + HOSTS_BACKUP_PATH + " " + HOSTS_FILE_PATH + " && " +
            "chmod 644 " + HOSTS_FILE_PATH + "; " +
            "else echo 'localhost' > " + HOSTS_FILE_PATH + " && " +
            "chmod 644 " + HOSTS_FILE_PATH + "; fi"
        });
        
        process.waitFor();
        int exitValue = process.exitValue();
        Log.d(TAG, "Backup restoration exit value: " + exitValue);
        
        if (exitValue != 0) {
            // Read error output
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            errorReader.close();
            
            Log.e(TAG, "Backup restoration error: " + errorOutput.toString());
            throw new Exception("Failed to restore backup: " + errorOutput.toString());
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
                if (!hasRootAccess()) {
                    mError = "Root access required";
                    return null;
                }

                if (!isSystemMounted()) {
                    mError = "System partition not writable";
                    return null;
                }

                // Download hosts file
                String hostsContent = downloadHostsFile();
                if (hostsContent == null) {
                    mError = "Failed to download hosts file";
                    return null;
                }

                // Apply hosts file
                return applyHostsFile(hostsContent);
            } catch (Exception e) {
                Log.e(TAG, "Update failed", e);
                mError = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && mCallback != null) {
                // Update preferences
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
                connection.setRequestProperty("User-Agent", "AdBlocker-LineageOS/1.0");

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "HTTP response code: " + responseCode);
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error: " + responseCode);
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
                
                String result = content.toString();
                Log.d(TAG, "Downloaded " + result.length() + " characters");
                return result;
                
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private String applyHostsFile(String hostsContent) throws Exception {
            // Count blocked domains
            mBlockedCount = countBlockedDomains(hostsContent);
            Log.d(TAG, "Blocked domains count: " + mBlockedCount);

            // Create temporary file
            File tempFile = new File(mContext.getCacheDir(), "hosts_temp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(hostsContent);
            }

            // Copy to system hosts file with root
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "cp " + tempFile.getAbsolutePath() + " " + HOSTS_FILE_PATH + " && " +
                "chmod 644 " + HOSTS_FILE_PATH + " && " +
                "chown root:root " + HOSTS_FILE_PATH
            });

            process.waitFor();
            int exitValue = process.exitValue();
            Log.d(TAG, "Apply hosts file exit value: " + exitValue);

            // Clean up temp file
            tempFile.delete();

            if (exitValue != 0) {
                // Read error output
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
                errorReader.close();
                
                Log.e(TAG, "Apply hosts file error: " + errorOutput.toString());
                throw new Exception("Failed to apply hosts file: " + errorOutput.toString());
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
                if (!hasRootAccess()) {
                    mError = "Root access required";
                    return null;
                }

                if (!isSystemMounted()) {
                    mError = "System partition not writable";
                    return null;
                }

                String hostsContent = params[0];
                if (hostsContent == null || hostsContent.trim().isEmpty()) {
                    mError = "Invalid hosts content";
                    return null;
                }

                return applyHostsFile(hostsContent);
            } catch (Exception e) {
                Log.e(TAG, "Manual update failed", e);
                mError = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && mCallback != null) {
                // Update preferences
                mPrefs.edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .putInt(PREF_BLOCKED_COUNT, mBlockedCount)
                    .apply();
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                mCallback.onUpdateError(mError != null ? mError : "Unknown error");
            }
        }

        private String applyHostsFile(String hostsContent) throws Exception {
            // Count blocked domains
            mBlockedCount = countBlockedDomains(hostsContent);
            Log.d(TAG, "Manual update blocked domains count: " + mBlockedCount);

            // Create temporary file
            File tempFile = new File(mContext.getCacheDir(), "hosts_temp_manual");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(hostsContent);
            }

            // Copy to system hosts file with root
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "cp " + tempFile.getAbsolutePath() + " " + HOSTS_FILE_PATH + " && " +
                "chmod 644 " + HOSTS_FILE_PATH + " && " +
                "chown root:root " + HOSTS_FILE_PATH
            });

            process.waitFor();
            int exitValue = process.exitValue();
            Log.d(TAG, "Manual apply hosts file exit value: " + exitValue);

            // Clean up temp file
            tempFile.delete();

            if (exitValue != 0) {
                // Read error output
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
                errorReader.close();
                
                Log.e(TAG, "Manual apply hosts file error: " + errorOutput.toString());
                throw new Exception("Failed to apply hosts file: " + errorOutput.toString());
            }

            return "Success";
        }
    }

    private int countBlockedDomains(String hostsContent) {
        int count = 0;
        String[] lines = hostsContent.split("\n");
        Pattern blockedPattern = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+)");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#") && blockedPattern.matcher(line).matches()) {
                // Skip localhost entries
                if (!line.contains("localhost") && !line.contains("local") && 
                    !line.contains("broadcasthost") && !line.contains("0.0.0.0 0.0.0.0")) {
                    count++;
                }
            }
        }
        
        Log.d(TAG, "Counted " + count + " blocked domains");
        return count;
    }
}
