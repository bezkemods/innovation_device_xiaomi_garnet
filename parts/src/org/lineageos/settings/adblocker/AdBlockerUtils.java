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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AdBlockerUtils {
    private static final String TAG = "AdBlockerUtils";

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
        String currentLog = mPrefs.getString(PREF_DEBUG_LOG, "");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String newLog = timestamp + ": " + message + "\n" + currentLog;

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
        return mPrefs.getString(PREF_DEBUG_LOG, "No debug information");
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
            return "Never updated";
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
            if (setGlobalDNS(primary, secondary)) {
                logDebug("DNS set via Settings.Global");
                return true;
            }
            if (hasRootAccess()) {
                boolean rootSuccess = setDNSWithRoot(primary, secondary);
                logDebug("DNS set via root: " + rootSuccess);
                return rootSuccess;
            }
            logDebug("No DNS setting method available, but continuing anyway");
            return true;
        } catch (Exception e) {
            logDebug("setDNSServers() exception: " + e.getMessage());
            return true;
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
            String[] clearCommands = {"su", "-c", "iptables -t nat -F OUTPUT 2>/dev/null"};
            Process clearProcess = Runtime.getRuntime().exec(clearCommands);
            clearProcess.waitFor();

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

    // Removed automatic download methods - now only manual loading is supported

    public void updateHostsFileFromContent(String hostsContent, UpdateCallback callback) {
        logDebug("updateHostsFileFromContent() called, content length: " +
            (hostsContent != null ? hostsContent.length() : 0));
        new UpdateHostsFromContentTask(callback).execute(hostsContent);
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
                    mError = "Invalid hosts content";
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
                mPrefs.edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .putInt(PREF_BLOCKED_COUNT, mBlockedCount)
                    .apply();
                logDebug("Manual update successful, blocked count: " + mBlockedCount);
                mCallback.onUpdateSuccess(mBlockedCount);
            } else if (mCallback != null) {
                logDebug("Manual update failed: " + (mError != null ? mError : "Unknown error"));
                mCallback.onUpdateError(mError != null ? mError : "Unknown error");
            }
        }

        private String parseHostsFile(String hostsContent) throws Exception {
            Set<String> blockedDomains = new HashSet<>();
            mBlockedCount = countBlockedDomains(hostsContent, blockedDomains);
            logDebug("Manually parsed " + mBlockedCount + " blocked domains");

            // Store up to 5000 domains in preferences for quick access
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

        Pattern hostsPattern = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+).*$");
        Pattern adblockPattern = Pattern.compile("^\\|\\|([^\\^\\s]+)\\^.*$");
        Pattern domainPattern = Pattern.compile("^([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\s*$");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }

            String domain = null;
            java.util.regex.Matcher hostsMatcher = hostsPattern.matcher(line);
            if (hostsMatcher.matches()) {
                domain = hostsMatcher.group(2);
            } else {
                java.util.regex.Matcher adblockMatcher = adblockPattern.matcher(line);
                if (adblockMatcher.matches()) {
                    domain = adblockMatcher.group(1);
                } else {
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

        if (domain.contains("localhost") || domain.contains("local") ||
            domain.equals("0.0.0.0") || domain.equals("127.0.0.1") ||
            domain.contains(" ") || domain.length() < 4 ||
            !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }

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

        StringBuilder stats = new StringBuilder();
        stats.append("Status: ").append(isEnabled ? "Active" : "Inactive").append("\n");
        stats.append("Blocked domains: ").append(blockedCount).append("\n");
        stats.append("Last update: ").append(lastUpdate).append("\n");
        stats.append("Root: ").append(hasRoot ? "Yes" : "No").append("\n");
        stats.append("Method: Manual hosts file loading");

        return stats.toString();
    }
}
