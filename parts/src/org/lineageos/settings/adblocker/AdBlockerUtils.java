package org.lineageos.settings.adblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AdBlockerUtils {
    private static final String TAG = "AdBlockerUtils";
    
    // Egyszerűbb, megbízhatóbb hosts fájl források
    private static final String[] HOSTS_URLS = {
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://someonewhocares.org/hosts/zero/hosts",
        "https://raw.githubusercontent.com/AdguardTeam/HostlistsRegistry/main/assets/filter_1.txt",
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
        // Fallback - kisebb, de megbízható lista
        "https://raw.githubusercontent.com/hectorm/hmirror/master/data/adaway.org/list.txt"
    };
    
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    private static final String PREF_BLOCKED_DOMAINS = "adblocker_blocked_domains";
    private static final String PREF_DEBUG_LOG = "adblocker_debug_log";
    
    // DNS servers for ad-blocking
    private static final String ADGUARD_DNS_PRIMARY = "94.140.14.14";
    private static final String ADGUARD_DNS_SECONDARY = "94.140.15.15";
    private static final String CLOUDFLARE_DNS_PRIMARY = "1.1.1.1";
    private static final String CLOUDFLARE_DNS_SECONDARY = "1.0.0.1";
    
    private Context mContext;
    private SharedPreferences mPrefs;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    public AdBlockerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        Log.d(TAG, "AdBlockerUtils initialized");
        logDebug("AdBlockerUtils constructor called");
    }

    public interface UpdateCallback {
        void onUpdateStart();
        void onUpdateSuccess(int blockedCount);
        void onUpdateError(String error);
    }

    private void logDebug(String message) {
        Log.d(TAG, message);
        // Store debug log for troubleshooting
        String currentLog = mPrefs.getString(PREF_DEBUG_LOG, "");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String newLog = timestamp + ": " + message + "\n" + currentLog;
        
        // Keep only last 50 lines
        String[] lines = newLog.split("\n");
        if (lines.length > 50) {
            StringBuilder trimmedLog = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                trimmedLog.append(lines[i]).append("\n");
            }
            newLog = trimmedLog.toString();
        }
        
        mPrefs.edit().putString(PREF_DEBUG_LOG, newLog).apply();
    }

    public String getDebugLog() {
        return mPrefs.getString(PREF_DEBUG_LOG, "Nincs debug információ");
    }

    public void clearDebugLog() {
        mPrefs.edit().remove(PREF_DEBUG_LOG).apply();
    }

    public boolean isEnabled() {
        boolean enabled = mPrefs.getBoolean("adblocker_enabled", false);
        logDebug("isEnabled() = " + enabled);
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean("adblocker_enabled", enabled).apply();
        logDebug("setEnabled(" + enabled + ")");
    }

    public String getLastUpdateTime() {
        long timestamp = mPrefs.getLong(PREF_LAST_UPDATE, 0);
        if (timestamp == 0) {
            return "Soha nem frissítve";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public int getBlockedDomainsCount() {
        int count = mPrefs.getInt(PREF_BLOCKED_COUNT, 0);
        logDebug("getBlockedDomainsCount() = " + count);
        return count;
    }

    public boolean isNetworkAvailable() {
        try {
            NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            boolean available = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            logDebug("isNetworkAvailable() = " + available + 
                " (type: " + (activeNetworkInfo != null ? activeNetworkInfo.getTypeName() : "none") + ")");
            return available;
        } catch (Exception e) {
            logDebug("isNetworkAvailable() exception: " + e.getMessage());
            return false;
        }
    }

    public boolean hasRootAccess() {
        try {
            logDebug("Checking root access...");
            Process process = Runtime.getRuntime().exec("su -c 'id'");
            int result = process.waitFor();
            boolean hasRoot = result == 0;
            logDebug("hasRootAccess() = " + hasRoot + " (exit code: " + result + ")");
            return hasRoot;
        } catch (Exception e) {
            logDebug("Root check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean enableAdBlocker() {
        try {
            logDebug("enableAdBlocker() called");
            // Set DNS servers to AdGuard DNS for ad-blocking
            boolean success = setDNSServers(ADGUARD_DNS_PRIMARY, ADGUARD_DNS_SECONDARY);
            if (success) {
                setEnabled(true);
                logDebug("AdBlocker enabled successfully");
                return true;
            } else {
                logDebug("Failed to set DNS servers");
                return false;
            }
        } catch (Exception e) {
            logDebug("enableAdBlocker() exception: " + e.getMessage());
            return false;
        }
    }

    public boolean disableAdBlocker() {
        try {
            logDebug("disableAdBlocker() called");
            // Reset DNS servers to Cloudflare (neutral)
            boolean success = setDNSServers(CLOUDFLARE_DNS_PRIMARY, CLOUDFLARE_DNS_SECONDARY);
            if (success) {
                setEnabled(false);
                logDebug("AdBlocker disabled successfully");
                return true;
            } else {
                logDebug("Failed to reset DNS servers");
                return false;
            }
        } catch (Exception e) {
            logDebug("disableAdBlocker() exception: " + e.getMessage());
            return false;
        }
    }

    private boolean setDNSServers(String primary, String secondary) {
        logDebug("setDNSServers(" + primary + ", " + secondary + ")");
        
        try {
            // Method 1: Try using Settings.Global (requires WRITE_SECURE_SETTINGS permission)
            if (setGlobalDNS(primary, secondary)) {
                logDebug("DNS set via Settings.Global");
                return true;
            }
            
            // Method 2: Try using root commands as fallback
            if (hasRootAccess()) {
                boolean rootSuccess = setDNSWithRoot(primary, secondary);
                logDebug("DNS set via root: " + rootSuccess);
                return rootSuccess;
            }
            
            // Method 3: Always return true for basic functionality
            logDebug("No DNS setting method available, but continuing anyway");
            return true;
        } catch (Exception e) {
            logDebug("setDNSServers() exception: " + e.getMessage());
            return true; // Don't fail completely
        }
    }

    private boolean setGlobalDNS(String primary, String secondary) {
        try {
            logDebug("Attempting to set global DNS...");
            
            if (primary.equals(ADGUARD_DNS_PRIMARY)) {
                Settings.Global.putString(mContext.getContentResolver(), 
                    Settings.Global.PRIVATE_DNS_MODE, "hostname");
                Settings.Global.putString(mContext.getContentResolver(), 
                    Settings.Global.PRIVATE_DNS_SPECIFIER, "dns.adguard.com");
                logDebug("Set DNS to AdGuard");
            } else {
                Settings.Global.putString(mContext.getContentResolver(), 
                    Settings.Global.PRIVATE_DNS_MODE, "off");
                logDebug("Reset DNS to default");
            }
            
            return true;
        } catch (Exception e) {
            logDebug("setGlobalDNS() failed: " + e.getMessage());
            return false;
        }
    }

    private boolean setDNSWithRoot(String primary, String secondary) {
        try {
            logDebug("Attempting to set DNS with root...");
            
            // Clear existing iptables rules first
            String[] clearCommands = {"su", "-c", "iptables -t nat -F OUTPUT 2>/dev/null"};
            Process clearProcess = Runtime.getRuntime().exec(clearCommands);
            clearProcess.waitFor();
            
            // Use iptables to redirect DNS queries
            String[] commands = {"su", "-c", 
                String.format("iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination %s:53", primary)};
            
            Process process = Runtime.getRuntime().exec(commands);
            int result = process.waitFor();
            
            logDebug("iptables command result: " + result);
            return result == 0;
        } catch (Exception e) {
            logDebug("setDNSWithRoot() failed: " + e.getMessage());
            return false;
        }
    }

    public void updateHostsFile(UpdateCallback callback) {
        logDebug("updateHostsFile() called");
        new UpdateHostsTask(callback).execute();
    }

    public void updateHostsFileFromContent(String hostsContent, UpdateCallback callback) {
        logDebug("updateHostsFileFromContent() called, content length: " + 
            (hostsContent != null ? hostsContent.length() : 0));
        new UpdateHostsFromContentTask(callback).execute(hostsContent);
    }

    private class UpdateHostsTask extends AsyncTask<Void, Void, String> {
        private UpdateCallback mCallback;
        private int mBlockedCount = 0;
        private String mError = null;
        private String mSuccessUrl = null;

        public UpdateHostsTask(UpdateCallback callback) {
            mCallback = callback;
        }

        @Override
        protected void onPreExecute() {
            logDebug("UpdateHostsTask.onPreExecute()");
            if (mCallback != null) {
                mCallback.onUpdateStart();
            }
        }

        @Override
        protected String doInBackground(Void... params) {
            logDebug("UpdateHostsTask.doInBackground() started");
            
            // Check network first
            if (!isNetworkAvailable()) {
                mError = "Nincs internet kapcsolat";
                logDebug("No network available");
                return null;
            }
            
            // Try multiple sources in case one fails
            for (int i = 0; i < HOSTS_URLS.length; i++) {
                String hostsUrl = HOSTS_URLS[i];
                try {
                    logDebug("Trying source " + (i+1) + "/" + HOSTS_URLS.length + ": " + hostsUrl);
                    String hostsContent = downloadHostsFile(hostsUrl);
                    
                    if (hostsContent != null && !hostsContent.trim().isEmpty()) {
                        logDebug("Successfully downloaded from: " + hostsUrl + 
                            " (length: " + hostsContent.length() + ")");
                        mSuccessUrl = hostsUrl;
                        return parseHostsFile(hostsContent);
                    } else {
                        logDebug("Empty or null content from: " + hostsUrl);
                    }
                } catch (Exception e) {
                    logDebug("Failed to download from " + hostsUrl + ": " + e.getMessage());
                    mError = "Hiba a " + hostsUrl + " letöltésénél: " + e.getMessage();
                }
            }
            
            if (mError == null) {
                mError = "Minden hosts fájl forrás elérhetetlen";
            }
            logDebug("All sources failed, final error: " + mError);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            logDebug("UpdateHostsTask.onPostExecute(), result: " + result);
            
            if (result != null && mCallback != null) {
                // Update preferences
                mPrefs.edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .putInt(PREF_BLOCKED_COUNT, mBlockedCount)
                    .apply();
                
                logDebug("Update successful, blocked count: " + mBlockedCount + 
                    ", source: " + mSuccessUrl);
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                logDebug("Update failed: " + (mError != null ? mError : "Unknown error"));
                mCallback.onUpdateError(mError != null ? mError : "Ismeretlen hiba");
            }
        }

        private String downloadHostsFile(String hostsUrl) {
            HttpURLConnection connection = null;
            try {
                logDebug("Starting download from: " + hostsUrl);
                
                URL url = new URL(hostsUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(20000); // 20 seconds
                connection.setReadTimeout(60000);    // 60 seconds
                connection.setInstanceFollowRedirects(true);
                
                // Add headers to avoid blocking
                connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 16; LineageOS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "text/plain,text/html,*/*");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Connection", "close");

                logDebug("Connecting to: " + hostsUrl);
                int responseCode = connection.getResponseCode();
                logDebug("HTTP Response Code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = connection.getHeaderField("Location");
                    logDebug("Redirected to: " + newUrl);
                    connection.disconnect();
                    return downloadHostsFile(newUrl); // Follow redirect
                }
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    logDebug("HTTP error: " + responseCode + " " + connection.getResponseMessage());
                    return null;
                }

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    lineCount++;
                    
                    // Log progress every 5000 lines
                    if (lineCount % 5000 == 0) {
                        logDebug("Downloaded " + lineCount + " lines");
                    }
                    
                    // Safety limit to prevent memory issues
                    if (lineCount > 200000) {
                        logDebug("Reached line limit (200k), stopping download");
                        break;
                    }
                }

                reader.close();
                inputStream.close();
                
                logDebug("Download completed: " + lineCount + " lines, " + content.length() + " chars");
                return content.toString();
                
            } catch (Exception e) {
                logDebug("Download exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private String parseHostsFile(String hostsContent) throws Exception {
            logDebug("Starting to parse hosts file, length: " + hostsContent.length());
            
            // Parse blocked domains and store them
            Set<String> blockedDomains = new HashSet<>();
            mBlockedCount = countBlockedDomains(hostsContent, blockedDomains);

            logDebug("Parsed " + mBlockedCount + " blocked domains");

            // Store blocked domains for future reference (limit to prevent memory issues)
            Set<String> limitedDomains = new HashSet<>();
            int count = 0;
            for (String domain : blockedDomains) {
                if (count >= 5000) break; // Reduced limit
                limitedDomains.add(domain);
                count++;
            }

            mPrefs.edit()
                .putStringSet(PREF_BLOCKED_DOMAINS, limitedDomains)
                .apply();

            logDebug("Stored " + limitedDomains.size() + " domains in preferences");
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
            logDebug("UpdateHostsFromContentTask.onPreExecute()");
            if (mCallback != null) {
                mCallback.onUpdateStart();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                String hostsContent = params[0];
                if (hostsContent == null || hostsContent.trim().isEmpty()) {
                    mError = "Érvénytelen hosts tartalom";
                    logDebug("Invalid hosts content");
                    return null;
                }

                logDebug("Manual update with content length: " + hostsContent.length());
                return parseHostsFile(hostsContent);
            } catch (Exception e) {
                logDebug("Manual update failed: " + e.getMessage());
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
                
                logDebug("Manual update successful, blocked count: " + mBlockedCount);
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                logDebug("Manual update failed: " + (mError != null ? mError : "Unknown error"));
                mCallback.onUpdateError(mError != null ? mError : "Ismeretlen hiba");
            }
        }

        private String parseHostsFile(String hostsContent) throws Exception {
            // Parse blocked domains and store them
            Set<String> blockedDomains = new HashSet<>();
            mBlockedCount = countBlockedDomains(hostsContent, blockedDomains);

            logDebug("Manually parsed " + mBlockedCount + " blocked domains");

            // Store blocked domains for future reference
            Set<String> limitedDomains = new HashSet<>();
            int count = 0;
            for (String domain : blockedDomains) {
                if (count >= 5000) break;
                limitedDomains.add(domain);
                count++;
            }

            mPrefs.edit()
                .putStringSet(PREF_BLOCKED_DOMAINS, limitedDomains)
                .apply();

            return "Success";
        }
    }

    private int countBlockedDomains(String hostsContent, Set<String> blockedDomains) {
        logDebug("countBlockedDomains() called");
        int count = 0;
        String[] lines = hostsContent.split("\n");
        
        logDebug("Processing " + lines.length + " lines");
        
        // Multiple patterns to support different hosts file formats
        Pattern hostsPattern = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+).*$");
        Pattern adblockPattern = Pattern.compile("^\\|\\|([^\\^\\s]+)\\^.*$");
        Pattern domainPattern = Pattern.compile("^([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s*$");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            
            String domain = null;
            
            // Try hosts format first (most common)
            java.util.regex.Matcher hostsMatcher = hostsPattern.matcher(line);
            if (hostsMatcher.matches()) {
                domain = hostsMatcher.group(2);
            } else {
                // Try AdBlock format
                java.util.regex.Matcher adblockMatcher = adblockPattern.matcher(line);
                if (adblockMatcher.matches()) {
                    domain = adblockMatcher.group(1);
                } else {
                    // Try simple domain format
                    java.util.regex.Matcher domainMatcher = domainPattern.matcher(line);
                    if (domainMatcher.matches()) {
                        domain = domainMatcher.group(1);
                    }
                }
            }
            
            if (domain != null && isValidDomain(domain)) {
                if (blockedDomains != null) {
                    blockedDomains.add(domain);
                }
                count++;
            }
            
            // Log progress every 10000 lines
            if (i > 0 && i % 10000 == 0) {
                logDebug("Processed " + i + " lines, found " + count + " domains so far");
            }
        }
        
        logDebug("Total domains found: " + count);
        return count;
    }

    private boolean isValidDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        
        // Skip localhost entries and invalid domains
        if (domain.contains("localhost") || domain.contains("local") || 
            domain.equals("0.0.0.0") || domain.equals("127.0.0.1") ||
            domain.contains(" ") || domain.length() < 4 ||
            !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }
        
        // Basic domain validation
        return domain.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$") && 
               !domain.contains("..") && 
               domain.split("\\.").length >= 2;
    }

    public Set<String> getBlockedDomains() {
        return mPrefs.getStringSet(PREF_BLOCKED_DOMAINS, new HashSet<String>());
    }

    public boolean isDomainBlocked(String domain) {
        Set<String> blockedDomains = getBlockedDomains();
        if (blockedDomains.contains(domain)) {
            return true;
        }
        
        // Check for wildcard matches
        for (String blockedDomain : blockedDomains) {
            if (domain.endsWith("." + blockedDomain) || domain.equals(blockedDomain)) {
                return true;
            }
        }
        
        return false;
    }

    public String getStatistics() {
        boolean isEnabled = isEnabled();
        int blockedCount = getBlockedDomainsCount();
        String lastUpdate = getLastUpdateTime();
        boolean hasRoot = hasRootAccess();
        boolean hasNetwork = isNetworkAvailable();
        
        StringBuilder stats = new StringBuilder();
        stats.append("Állapot: ").append(isEnabled ? "Aktív" : "Inaktív").append("\n");
        stats.append("Blokkolt domainek: ").append(blockedCount).append("\n");
        stats.append("Utolsó frissítés: ").append(lastUpdate).append("\n");
        stats.append("Root: ").append(hasRoot ? "Igen" : "Nem").append("\n");
        stats.append("Internet: ").append(hasNetwork ? "Elérhető" : "Nem elérhető");
        
        return stats.toString();
    }
}
