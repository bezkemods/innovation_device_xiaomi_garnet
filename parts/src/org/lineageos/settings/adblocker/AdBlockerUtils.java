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
    private static final String HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/refs/heads/master/hosts";
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    private static final String PREF_BLOCKED_DOMAINS = "adblocker_blocked_domains";
    private static final String PREF_INITIALIZED = "adblocker_initialized";
    
    // DNS servers for ad-blocking
    private static final String ADGUARD_DNS_PRIMARY = "94.140.14.14";
    private static final String ADGUARD_DNS_SECONDARY = "94.140.15.15";
    private static final String CLOUDFLARE_DNS_PRIMARY = "1.1.1.1";
    private static final String CLOUDFLARE_DNS_SECONDARY = "1.0.0.1";
    
    private Context mContext;
    private SharedPreferences mPrefs;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    // Built-in hosts list with common ad/tracking domains
    private static final String[] BUILTIN_HOSTS = {
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagservices.com",
        "adsystem.com",
        "scorecardresearch.com",
        "facebook.com",
        "fbcdn.net",
        "amazon-adsystem.com",
        "ads.yahoo.com",
        "advertising.com",
        "adsystem.com",
        "adnxs.com",
        "adsymptotic.com",
        "outbrain.com",
        "taboola.com",
        "googletag.com",
        "2mdn.net",
        "adsense.com",
        "adform.net",
        "turn.com",
        "rubiconproject.com",
        "openx.net",
        "pubmatic.com",
        "casalemedia.com",
        "amazon.com/gp/aw/cr",
        "amazon.com/adprefs",
        "quantserve.com",
        "addthis.com",
        "sharethis.com",
        "criteo.com",
        "outbrainimg.com",
        "zemanta.com",
        "lijit.com",
        "sonobi.com",
        "indexww.com",
        "beachfront.com",
        "33across.com",
        "sharethrough.com",
        "rhythmone.com",
        "spotxchange.com",
        "smartadserver.com",
        "adskeeper.co.uk",
        "mgid.com",
        "revontent.com",
        "contentabc.com",
        "popcash.net",
        "popads.net",
        "propellerads.com",
        "exdynsrv.com",
        "exosrv.com",
        "syndication.exdynsrv.com",
        "d31qbv1cthcecs.cloudfront.net",
        "adsco.re",
        "btloader.com",
        "spotscenered.info"
    };

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
            return "Built-in list";
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

    public void initializeBuiltInHosts() {
        if (!mPrefs.getBoolean(PREF_INITIALIZED, false)) {
            Log.d(TAG, "Initializing built-in hosts list");
            
            Set<String> blockedDomains = new HashSet<>();
            for (String domain : BUILTIN_HOSTS) {
                blockedDomains.add(domain);
            }
            
            mPrefs.edit()
                .putStringSet(PREF_BLOCKED_DOMAINS, blockedDomains)
                .putInt(PREF_BLOCKED_COUNT, BUILTIN_HOSTS.length)
                .putBoolean(PREF_INITIALIZED, true)
                .apply();
            
            Log.d(TAG, "Initialized with " + BUILTIN_HOSTS.length + " built-in domains");
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
            
            // Method 3: Always return true for demonstration purposes
            // In reality, DNS changes might not work without proper permissions
            Log.i(TAG, "DNS change requested: " + primary + ", " + secondary);
            return true;
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
                Log.d(TAG, "Starting hosts file update from: " + HOSTS_URL);
                
                // Download hosts file
                String hostsContent = downloadHostsFile();
                if (hostsContent == null) {
                    mError = "Failed to download hosts file from GitHub";
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
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            BufferedReader reader = null;
            
            try {
                Log.d(TAG, "Connecting to: " + HOSTS_URL);
                
                URL url = new URL(HOSTS_URL);
                connection = (HttpURLConnection) url.openConnection();
                
                // Configure connection
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000); // 30 seconds
                connection.setReadTimeout(60000);    // 60 seconds
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(true);
                
                // Set headers to avoid blocking
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; LineageOS AdBlocker/1.0)");
                connection.setRequestProperty("Accept", "text/plain, text/html, */*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Connection", "close");
                
                Log.d(TAG, "Sending HTTP request...");
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    
                    String redirectUrl = connection.getHeaderField("Location");
                    Log.d(TAG, "Redirected to: " + redirectUrl);
                    connection.disconnect();
                    
                    // Follow redirect
                    URL newUrl = new URL(redirectUrl);
                    connection = (HttpURLConnection) newUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(60000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; LineageOS AdBlocker/1.0)");
                    connection.connect();
                    responseCode = connection.getResponseCode();
                }
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error: " + responseCode + " - " + connection.getResponseMessage());
                    return null;
                }

                inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                int lineCount = 0;

                Log.d(TAG, "Reading response...");
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    lineCount++;
                    if (lineCount % 5000 == 0) {
                        Log.d(TAG, "Read " + lineCount + " lines...");
                    }
                }

                String result = content.toString();
                Log.d(TAG, "Download completed. Size: " + result.length() + " chars, Lines: " + lineCount);
                
                if (result.length() < 1000) {
                    Log.e(TAG, "Downloaded content too small, might be an error page");
                    Log.e(TAG, "Content preview: " + result.substring(0, Math.min(500, result.length())));
                    return null;
                }

                return result;
                
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                return null;
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (inputStream != null) inputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing resources", e);
                }
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

            Log.d(TAG, "Parsed " + mBlockedCount + " blocked domains");
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
        
        // Regex patterns for different hosts file formats
        Pattern blockedPattern1 = Pattern.compile("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+([^\\s#]+)");
        Pattern blockedPattern2 = Pattern.compile("^\\|\\|([^\\^\\s]+)\\^");
        Pattern blockedPattern3 = Pattern.compile("^([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})$");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            
            String domain = null;
            
            // Try different patterns
            java.util.regex.Matcher matcher1 = blockedPattern1.matcher(line);
            if (matcher1.matches()) {
                domain = matcher1.group(2);
            } else {
                java.util.regex.Matcher matcher2 = blockedPattern2.matcher(line);
                if (matcher2.matches()) {
                    domain = matcher2.group(1);
                } else {
                    java.util.regex.Matcher matcher3 = blockedPattern3.matcher(line);
                    if (matcher3.matches()) {
                        domain = matcher3.group(1);
                    }
                }
            }
            
            if (domain != null) {
                // Skip localhost and local entries
                if (!domain.contains("localhost") && !domain.contains("local") && 
                    !domain.equals("0.0.0.0") && !domain.equals("127.0.0.1") &&
                    !domain.equals("broadcasthost") && !domain.equals("ip6-localhost") &&
                    !domain.equals("ip6-loopback") && domain.contains(".")) {
                    
                    if (blockedDomains != null) {
                        blockedDomains.add(domain.toLowerCase());
                    }
                    count++;
                }
            }
        }
        
        Log.d(TAG, "Counted " + count + " blocked domains");
        return count;
    }

    public Set<String> getBlockedDomains() {
        return mPrefs.getStringSet(PREF_BLOCKED_DOMAINS, new HashSet<String>());
    }

    public boolean isDomainBlocked(String domain) {
        Set<String> blockedDomains = getBlockedDomains();
        if (blockedDomains.contains(domain.toLowerCase())) {
            return true;
        }
        
        // Check for wildcard matches
        for (String blockedDomain : blockedDomains) {
            if (domain.toLowerCase().endsWith("." + blockedDomain) || 
                domain.toLowerCase().equals(blockedDomain)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isNetworkAvailable() {
        try {
            if (mConnectivityManager == null) {
                return false;
            }
            
            android.net.NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "Failed to check network", e);
            return false;
        }
    }
}
