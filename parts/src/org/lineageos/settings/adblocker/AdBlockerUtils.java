package org.lineageos.settings.adblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
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
    private static final String HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    private static final String PREF_BLOCKED_DOMAINS = "adblocker_blocked_domains";
    
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
            Process process = Runtime.getRuntime().exec("su -c 'id'");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }

    public boolean enableAdBlocker() {
        try {
            // Set DNS servers to AdGuard DNS for ad-blocking
            boolean success = setDNSServers(ADGUARD_DNS_PRIMARY, ADGUARD_DNS_SECONDARY);
            if (success) {
                setEnabled(true);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable adblocker", e);
            return false;
        }
    }

    public boolean disableAdBlocker() {
        try {
            // Reset DNS servers to Cloudflare (neutral)
            boolean success = setDNSServers(CLOUDFLARE_DNS_PRIMARY, CLOUDFLARE_DNS_SECONDARY);
            if (success) {
                setEnabled(false);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable adblocker", e);
            return false;
        }
    }

    private boolean setDNSServers(String primary, String secondary) {
        try {
            // Method 1: Try using Settings.Global (requires WRITE_SECURE_SETTINGS permission)
            if (setGlobalDNS(primary, secondary)) {
                return true;
            }
            
            // Method 2: Try using root commands as fallback
            if (hasRootAccess()) {
                return setDNSWithRoot(primary, secondary);
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set DNS servers", e);
            return false;
        }
    }

    private boolean setGlobalDNS(String primary, String secondary) {
        try {
            // Set global DNS settings
            Settings.Global.putString(mContext.getContentResolver(), 
                Settings.Global.PRIVATE_DNS_MODE, "hostname");
            Settings.Global.putString(mContext.getContentResolver(), 
                Settings.Global.PRIVATE_DNS_SPECIFIER, "dns.adguard.com");
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set global DNS", e);
            return false;
        }
    }

    private boolean setDNSWithRoot(String primary, String secondary) {
        try {
            // Use iptables to redirect DNS queries (more reliable on modern Android)
            String[] commands = {
                "su",
                "-c",
                String.format("iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination %s:53", primary)
            };
            
            Process process = Runtime.getRuntime().exec(commands);
            process.waitFor();
            
            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set DNS with root", e);
            return false;
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
                // Download hosts file
                String hostsContent = downloadHostsFile();
                if (hostsContent == null) {
                    mError = "Failed to download hosts file";
                    return null;
                }

                // Parse and store blocked domains
                return parseHostsFile(hostsContent);
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
            try {
                URL url = new URL(HOSTS_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
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
                connection.disconnect();

                return content.toString();
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                return null;
            }
        }

        private String parseHostsFile(String hostsContent) throws Exception {
            // Parse blocked domains and store them
            Set<String> blockedDomains = new HashSet<>();
            mBlockedCount = countBlockedDomains(hostsContent, blockedDomains);

            // Store blocked domains for future reference
            mPrefs.edit()
                .putStringSet(PREF_BLOCKED_DOMAINS, blockedDomains)
                .apply();

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
                String hostsContent = params[0];
                if (hostsContent == null || hostsContent.trim().isEmpty()) {
                    mError = "Invalid hosts content";
                    return null;
                }

                return parseHostsFile(hostsContent);
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

        private String parseHostsFile(String hostsContent) throws Exception {
            // Parse blocked domains and store them
            Set<String> blockedDomains = new HashSet<>();
            mBlockedCount = countBlockedDomains(hostsContent, blockedDomains);

            // Store blocked domains for future reference
            mPrefs.edit()
                .putStringSet(PREF_BLOCKED_DOMAINS, blockedDomains)
                .apply();

            return "Success";
        }
    }

    private int countBlockedDomains(String hostsContent, Set<String> blockedDomains) {
        int count = 0;
        String[] lines = hostsContent.split("\n");
        Pattern blockedPattern = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+)");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                java.util.regex.Matcher matcher = blockedPattern.matcher(line);
                if (matcher.matches()) {
                    String domain = matcher.group(2);
                    // Skip localhost entries
                    if (!domain.contains("localhost") && !domain.contains("local") && 
                        !domain.equals("0.0.0.0") && !domain.equals("127.0.0.1")) {
                        if (blockedDomains != null) {
                            blockedDomains.add(domain);
                        }
                        count++;
                    }
                }
            }
        }
        
        return count;
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
}
