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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AdBlockerUtils {
    private static final String TAG = "AdBlockerUtils";

    // Preference keys
    private static final String PREF_LAST_UPDATE = "adblocker_last_update";
    private static final String PREF_BLOCKED_COUNT = "adblocker_blocked_count";
    private static final String PREF_BLOCKED_DOMAINS = "adblocker_blocked_domains";
    private static final String PREF_DEBUG_LOG = "adblocker_debug_log";
    private static final String PREF_DNS_MODE = "adblocker_dns_mode";

    // DNS providers
    private static final String PREF_DNS_PROVIDER = "adblocker_dns_provider";
    private static final String PREF_DOH_ENABLED = "adblocker_doh_enabled";
    private static final String PREF_DNS_FALLBACK = "adblocker_dns_fallback";
    
    // DNS server hostnames
    private static final String ADGUARD_DNS_HOSTNAME = "dns.adguard-dns.com";
    private static final String CLOUDFLARE_DNS_HOSTNAME = "one.one.one.one";
    private static final String QUAD9_DNS_HOSTNAME = "dns.quad9.net";
    private static final String GOOGLE_DNS_HOSTNAME = "dns.google";
    
    // VPN settings keys
    private static final String PREF_VPN_ENABLED = "adblocker_vpn_enabled";
    private static final String PREF_VPN_PROVIDER = "adblocker_vpn_provider";
    private static final String PREF_VPN_AUTO_CONNECT = "adblocker_vpn_auto_connect";
    
    // Proxy settings keys
    private static final String PREF_PROXY_ENABLED = "adblocker_proxy_enabled";
    private static final String PREF_PROXY_HOST = "adblocker_proxy_host";
    private static final String PREF_PROXY_PORT = "adblocker_proxy_port";
    private static final String PREF_PROXY_AUTO_SELECT = "adblocker_proxy_auto_select";
    private static final String PREF_PROXY_LIST = "adblocker_proxy_list";
    
    // Filter settings keys
    private static final String PREF_WHITELIST = "adblocker_whitelist";
    private static final String PREF_BLACKLIST = "adblocker_blacklist";
    private static final String PREF_FILTERED_APPS = "adblocker_filtered_apps";
    
    // Statistics keys
    private static final String PREF_TOTAL_BLOCKED = "adblocker_total_blocked";
    private static final String PREF_SAVED_BANDWIDTH = "adblocker_saved_bandwidth";
    private static final String PREF_LAST_STATS_RESET = "adblocker_last_stats_reset";
    
    // Proxy list sources
    private static final String[] PROXY_SOURCES = {
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/http/data.txt",
        "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt"
    };

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

    // Callback interfaces
    public interface UpdateCallback {
        void onUpdateStart();
        void onUpdateSuccess(int blockedCount);
        void onUpdateError(String error);
    }
    
    public interface ProxyTestCallback {
        void onProxyTested(String host, int port, long latency);
        void onError(String error);
    }
    
    public interface ProxyListCallback {
        void onListUpdated(int count);
        void onError(String error);
    }

    // Debug logging
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

    // Basic enable/disable
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

    // DNS Management
    public boolean enableAdBlocker() {
        try {
            logDebug("enableAdBlocker() called");
            String provider = getDnsProvider();
            String hostname = getDnsHostname(provider);
            boolean success = setPrivateDNS(hostname, true);
            if (success) {
                setEnabled(true);
                mPrefs.edit().putString(PREF_DNS_MODE, provider).apply();
                logDebug("AdBlocker enabled successfully with " + provider);
                return true;
            } else {
                logDebug("Failed to set DNS");
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
            boolean success = setPrivateDNS(CLOUDFLARE_DNS_HOSTNAME, false);
            if (success) {
                setEnabled(false);
                mPrefs.edit().putString(PREF_DNS_MODE, "cloudflare").apply();
                logDebug("AdBlocker disabled successfully");
                return true;
            } else {
                logDebug("Failed to reset DNS");
                return false;
            }
        } catch (Exception e) {
            logDebug("disableAdBlocker() exception: " + e.getMessage());
            return false;
        }
    }

    private boolean setPrivateDNS(String hostname, boolean isBlocking) {
        logDebug("setPrivateDNS(" + hostname + ", isBlocking=" + isBlocking + ")");
        try {
            // Set Private DNS mode to hostname mode
            Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.PRIVATE_DNS_MODE, "hostname");
            
            // Set the DNS hostname
            Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.PRIVATE_DNS_SPECIFIER, hostname);
            
            logDebug("Private DNS set to: " + hostname);
            
            // Additional root optimization if available
            if (hasRootAccess()) {
                setDNSWithRoot(isBlocking);
            }
            
            return true;
        } catch (Exception e) {
            logDebug("setPrivateDNS() failed: " + e.getMessage());
            
            // Fallback: try to set it anyway
            try {
                Settings.Global.putString(mContext.getContentResolver(),
                    Settings.Global.PRIVATE_DNS_MODE, isBlocking ? "hostname" : "off");
                if (isBlocking) {
                    Settings.Global.putString(mContext.getContentResolver(),
                        Settings.Global.PRIVATE_DNS_SPECIFIER, hostname);
                }
                logDebug("Private DNS set via fallback method");
                return true;
            } catch (Exception e2) {
                logDebug("Fallback also failed: " + e2.getMessage());
                return false;
            }
        }
    }

    private boolean setDNSWithRoot(boolean isBlocking) {
        try {
            logDebug("Attempting to set DNS with root...");
            
            // Clear existing iptables rules
            String[] clearCommands = {"su", "-c", "iptables -t nat -F OUTPUT 2>/dev/null"};
            Process clearProcess = Runtime.getRuntime().exec(clearCommands);
            clearProcess.waitFor();

            if (isBlocking) {
                // Add iptables rules for AdGuard DNS
                String[] commands = {"su", "-c",
                    "iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 94.140.14.14:53"};
                Process process = Runtime.getRuntime().exec(commands);
                int result = process.waitFor();
                logDebug("iptables command result: " + result);
                return result == 0;
            } else {
                logDebug("iptables rules cleared for neutral DNS");
                return true;
            }
        } catch (Exception e) {
            logDebug("setDNSWithRoot() failed: " + e.getMessage());
            return false;
        }
    }
    
    // DNS Provider Management
    public String getDnsProvider() {
        return mPrefs.getString(PREF_DNS_PROVIDER, "adguard");
    }
    
    public boolean setDnsProvider(String provider) {
        String hostname = getDnsHostname(provider);
        if (hostname == null) {
            logDebug("Invalid DNS provider: " + provider);
            return false;
        }
        
        mPrefs.edit().putString(PREF_DNS_PROVIDER, provider).apply();
        
        if (isEnabled()) {
            return setPrivateDNS(hostname, true);
        }
        
        return true;
    }
    
    private String getDnsHostname(String provider) {
        switch (provider) {
            case "adguard": return ADGUARD_DNS_HOSTNAME;
            case "cloudflare": return CLOUDFLARE_DNS_HOSTNAME;
            case "quad9": return QUAD9_DNS_HOSTNAME;
            case "google": return GOOGLE_DNS_HOSTNAME;
            default: return ADGUARD_DNS_HOSTNAME;
        }
    }
    
    public boolean isDohEnabled() {
        return mPrefs.getBoolean(PREF_DOH_ENABLED, true);
    }
    
    public void setDohEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_DOH_ENABLED, enabled).apply();
        logDebug("DoH enabled: " + enabled);
    }
    
    public boolean isDnsFallbackEnabled() {
        return mPrefs.getBoolean(PREF_DNS_FALLBACK, true);
    }
    
    public void setDnsFallback(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_DNS_FALLBACK, enabled).apply();
        logDebug("DNS fallback enabled: " + enabled);
    }

    // VPN Management
    public boolean isVpnEnabled() {
        return mPrefs.getBoolean(PREF_VPN_ENABLED, false);
    }

    public void setVpnEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_VPN_ENABLED, enabled).apply();
        logDebug("VPN enabled set to: " + enabled);
    }

    public String getVpnProvider() {
        return mPrefs.getString(PREF_VPN_PROVIDER, "none");
    }

    public void setVpnProvider(String provider) {
        mPrefs.edit().putString(PREF_VPN_PROVIDER, provider).apply();
        logDebug("VPN provider set to: " + provider);
    }

    public boolean isVpnConnected() {
        try {
            NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                return activeNetwork.getType() == ConnectivityManager.TYPE_VPN;
            }
            return false;
        } catch (Exception e) {
            logDebug("VPN connection check failed: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isVpnAutoConnectEnabled() {
        return mPrefs.getBoolean(PREF_VPN_AUTO_CONNECT, false);
    }
    
    public void setVpnAutoConnect(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_VPN_AUTO_CONNECT, enabled).apply();
        logDebug("VPN auto-connect set to: " + enabled);
    }

    // Proxy Management
    public boolean isProxyEnabled() {
        return mPrefs.getBoolean(PREF_PROXY_ENABLED, false);
    }

    public void setProxyEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_PROXY_ENABLED, enabled).apply();
        logDebug("Proxy enabled set to: " + enabled);
    }

    public String getProxyHost() {
        return mPrefs.getString(PREF_PROXY_HOST, "");
    }

    public void setProxyHost(String host) {
        mPrefs.edit().putString(PREF_PROXY_HOST, host).apply();
        logDebug("Proxy host set to: " + host);
    }

    public int getProxyPort() {
        return mPrefs.getInt(PREF_PROXY_PORT, 8080);
    }

    public void setProxyPort(int port) {
        mPrefs.edit().putInt(PREF_PROXY_PORT, port).apply();
        logDebug("Proxy port set to: " + port);
    }
    
    public boolean isProxyAutoSelectEnabled() {
        return mPrefs.getBoolean(PREF_PROXY_AUTO_SELECT, false);
    }
    
    public void setProxyAutoSelect(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_PROXY_AUTO_SELECT, enabled).apply();
        logDebug("Proxy auto-select set to: " + enabled);
    }

    public boolean setGlobalProxy(String host, int port) {
        if (!hasRootAccess()) {
            logDebug("Root access required for global proxy settings");
            return false;
        }

        try {
            String command = String.format(
                "settings put global http_proxy %s:%d",
                host, port
            );
            
            String[] rootCommand = {"su", "-c", command};
            Process process = Runtime.getRuntime().exec(rootCommand);
            int result = process.waitFor();
            
            boolean success = result == 0;
            logDebug("Global proxy set: " + success);
            return success;
        } catch (Exception e) {
            logDebug("Failed to set global proxy: " + e.getMessage());
            return false;
        }
    }

    public boolean clearGlobalProxy() {
        if (!hasRootAccess()) {
            logDebug("Root access required to clear global proxy");
            return false;
        }

        try {
            String[] clearCommand = {"su", "-c", "settings put global http_proxy :0"};
            Process process = Runtime.getRuntime().exec(clearCommand);
            int result = process.waitFor();
            
            boolean success = result == 0;
            logDebug("Global proxy cleared: " + success);
            return success;
        } catch (Exception e) {
            logDebug("Failed to clear global proxy: " + e.getMessage());
            return false;
        }
    }
    
    // Proxy testing and auto-selection
    public void testProxy(String host, int port, ProxyTestCallback callback) {
        new ProxyTestTask(callback).execute(host, String.valueOf(port));
    }
    
    public void findBestProxy(ProxyTestCallback callback) {
        new FindBestProxyTask(callback).execute();
    }
    
    public void updateProxyList(ProxyListCallback callback) {
        new UpdateProxyListTask(callback).execute();
    }
    
    private class ProxyTestTask extends AsyncTask<String, Void, Long> {
        private ProxyTestCallback mCallback;
        private String mHost;
        private int mPort;
        private String mError;

        public ProxyTestTask(ProxyTestCallback callback) {
            mCallback = callback;
        }

        @Override
        protected Long doInBackground(String... params) {
            mHost = params[0];
            try {
                mPort = Integer.parseInt(params[1]);
            } catch (NumberFormatException e) {
                mError = "Invalid port number";
                return null;
            }

            try {
                long startTime = System.currentTimeMillis();
                
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(mHost, mPort));
                Socket socket = new Socket(proxy);
                socket.connect(new InetSocketAddress("www.google.com", 80), 5000);
                socket.close();
                
                long latency = System.currentTimeMillis() - startTime;
                logDebug("Proxy test successful: " + mHost + ":" + mPort + " (" + latency + "ms)");
                return latency;
            } catch (Exception e) {
                mError = e.getMessage();
                logDebug("Proxy test failed: " + mHost + ":" + mPort + " - " + mError);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Long latency) {
            if (mCallback != null) {
                if (latency != null) {
                    mCallback.onProxyTested(mHost, mPort, latency);
                } else {
                    mCallback.onError(mError != null ? mError : "Connection failed");
                }
            }
        }
    }
    
    private class FindBestProxyTask extends AsyncTask<Void, Void, ProxyInfo> {
        private ProxyTestCallback mCallback;
        private String mError;

        public FindBestProxyTask(ProxyTestCallback callback) {
            mCallback = callback;
        }

        @Override
        protected ProxyInfo doInBackground(Void... params) {
            Set<String> proxyList = getProxyList();
            if (proxyList.isEmpty()) {
                mError = "No proxies available. Please update proxy list first.";
                return null;
            }

            ProxyInfo bestProxy = null;
            long bestLatency = Long.MAX_VALUE;

            for (String proxyStr : proxyList) {
                if (isCancelled()) break;
                
                String[] parts = proxyStr.split(":");
                if (parts.length != 2) continue;

                try {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);

                    long startTime = System.currentTimeMillis();
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                    Socket socket = new Socket(proxy);
                    socket.connect(new InetSocketAddress("www.google.com", 80), 3000);
                    socket.close();

                    long latency = System.currentTimeMillis() - startTime;
                    if (latency < bestLatency) {
                        bestLatency = latency;
                        bestProxy = new ProxyInfo(host, port, latency);
                    }

                    if (bestLatency < 100) break; // Good enough
                } catch (Exception e) {
                    // Try next proxy
                }
            }

            if (bestProxy == null) {
                mError = "No working proxies found";
            }

            return bestProxy;
        }

        @Override
        protected void onPostExecute(ProxyInfo proxy) {
            if (mCallback != null) {
                if (proxy != null) {
                    mCallback.onProxyTested(proxy.host, proxy.port, proxy.latency);
                } else {
                    mCallback.onError(mError != null ? mError : "Failed to find working proxy");
                }
            }
        }
    }
    
    private class UpdateProxyListTask extends AsyncTask<Void, Void, Integer> {
        private ProxyListCallback mCallback;
        private String mError;

        public UpdateProxyListTask(ProxyListCallback callback) {
            mCallback = callback;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            Set<String> proxySet = new HashSet<>();

            for (String source : PROXY_SOURCES) {
                if (isCancelled()) break;

                try {
                    URL url = new URL(source);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (isValidProxyFormat(line)) {
                            proxySet.add(line);
                        }
                    }
                    reader.close();
                    conn.disconnect();

                    logDebug("Fetched " + proxySet.size() + " proxies from " + source);
                } catch (Exception e) {
                    logDebug("Failed to fetch from " + source + ": " + e.getMessage());
                }
            }

            if (proxySet.isEmpty()) {
                mError = "Failed to fetch proxy list from all sources";
                return 0;
            }

            saveProxyList(proxySet);
            return proxySet.size();
        }

        @Override
        protected void onPostExecute(Integer count) {
            if (mCallback != null) {
                if (count > 0) {
                    mCallback.onListUpdated(count);
                } else {
                    mCallback.onError(mError != null ? mError : "No proxies fetched");
                }
            }
        }

        private boolean isValidProxyFormat(String proxy) {
            return proxy.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}$");
        }
    }
    
    private static class ProxyInfo {
        String host;
        int port;
        long latency;

        ProxyInfo(String host, int port, long latency) {
            this.host = host;
            this.port = port;
            this.latency = latency;
        }
    }
    
    private Set<String> getProxyList() {
        return mPrefs.getStringSet(PREF_PROXY_LIST, new HashSet<String>());
    }
    
    private void saveProxyList(Set<String> proxyList) {
        mPrefs.edit().putStringSet(PREF_PROXY_LIST, proxyList).apply();
        logDebug("Saved " + proxyList.size() + " proxies to list");
    }

    // Whitelist/Blacklist Management
    public Set<String> getWhitelist() {
        return mPrefs.getStringSet(PREF_WHITELIST, new HashSet<String>());
    }
    
    public Set<String> getBlacklist() {
        return mPrefs.getStringSet(PREF_BLACKLIST, new HashSet<String>());
    }
    
    public int getWhitelistCount() {
        return getWhitelist().size();
    }
    
    public int getBlacklistCount() {
        return getBlacklist().size();
    }
    
    public void addToWhitelist(String domain) {
        Set<String> whitelist = new HashSet<>(getWhitelist());
        whitelist.add(domain.toLowerCase().trim());
        mPrefs.edit().putStringSet(PREF_WHITELIST, whitelist).apply();
        logDebug("Added to whitelist: " + domain);
    }
    
    public void addToBlacklist(String domain) {
        Set<String> blacklist = new HashSet<>(getBlacklist());
        blacklist.add(domain.toLowerCase().trim());
        mPrefs.edit().putStringSet(PREF_BLACKLIST, blacklist).apply();
        logDebug("Added to blacklist: " + domain);
    }
    
    public void removeFromWhitelist(String domain) {
        Set<String> whitelist = new HashSet<>(getWhitelist());
        whitelist.remove(domain);
        mPrefs.edit().putStringSet(PREF_WHITELIST, whitelist).apply();
        logDebug("Removed from whitelist: " + domain);
    }
    
    public void removeFromBlacklist(String domain) {
        Set<String> blacklist = new HashSet<>(getBlacklist());
        blacklist.remove(domain);
        mPrefs.edit().putStringSet(PREF_BLACKLIST, blacklist).apply();
        logDebug("Removed from blacklist: " + domain);
    }
    
    public boolean isWhitelisted(String domain) {
        return getWhitelist().contains(domain);
    }
    
    public boolean isBlacklisted(String domain) {
        return getBlacklist().contains(domain);
    }
    
    // App filtering
    public int getFilteredAppsCount() {
        return mPrefs.getStringSet(PREF_FILTERED_APPS, new HashSet<String>()).size();
    }

    // Statistics
    public long getTotalBlockedRequests() {
        return mPrefs.getLong(PREF_TOTAL_BLOCKED, 0);
    }
    
    public void incrementBlockedRequests() {
        long count = getTotalBlockedRequests();
        mPrefs.edit().putLong(PREF_TOTAL_BLOCKED, count + 1).apply();
    }
    
    public long getSavedBandwidth() {
        return mPrefs.getLong(PREF_SAVED_BANDWIDTH, 0);
    }
    
    public void addSavedBandwidth(long bytes) {
        long total = getSavedBandwidth();
        mPrefs.edit().putLong(PREF_SAVED_BANDWIDTH, total + bytes).apply();
    }
    
    public void resetStatistics() {
        mPrefs.edit()
            .putLong(PREF_TOTAL_BLOCKED, 0)
            .putLong(PREF_SAVED_BANDWIDTH, 0)
            .putLong(PREF_LAST_STATS_RESET, System.currentTimeMillis())
            .apply();
        logDebug("Statistics reset");
    }

    // Hosts file management
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
                // Check whitelist/blacklist
                if (isWhitelisted(domain)) {
                    continue; // Skip whitelisted domains
                }
                
                if (blockedDomains != null) {
                    blockedDomains.add(domain);
                }
                count++;
            }

            if (i > 0 && i % 10000 == 0) {
                logDebug("Processed " + i + " lines, found " + count + " domains so far");
            }
        }

        // Add blacklisted domains
        if (blockedDomains != null) {
            blockedDomains.addAll(getBlacklist());
            count += getBlacklist().size();
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
        // Check whitelist first
        if (isWhitelisted(domain)) {
            return false;
        }
        
        // Check blacklist
        if (isBlacklisted(domain)) {
            incrementBlockedRequests();
            addSavedBandwidth(1024); // Estimate 1KB per blocked request
            return true;
        }
        
        // Check blocked domains
        Set<String> blockedDomains = getBlockedDomains();
        if (blockedDomains.contains(domain)) {
            incrementBlockedRequests();
            addSavedBandwidth(1024);
            return true;
        }

        for (String blockedDomain : blockedDomains) {
            if (domain.endsWith("." + blockedDomain) || domain.equals(blockedDomain)) {
                incrementBlockedRequests();
                addSavedBandwidth(1024);
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
        boolean vpnEnabled = isVpnEnabled();
        boolean proxyEnabled = isProxyEnabled();
        long totalBlocked = getTotalBlockedRequests();
        long savedBandwidth = getSavedBandwidth();

        StringBuilder stats = new StringBuilder();
        stats.append("Status: ").append(isEnabled ? "Active" : "Inactive").append("\n");
        stats.append("Blocked domains: ").append(blockedCount).append("\n");
        stats.append("Total blocked: ").append(totalBlocked).append(" requests\n");
        stats.append("Bandwidth saved: ").append(formatBytes(savedBandwidth)).append("\n");
        stats.append("Last update: ").append(lastUpdate).append("\n");
        stats.append("Root: ").append(hasRoot ? "Yes" : "No").append("\n");
        stats.append("VPN: ").append(vpnEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Proxy: ").append(proxyEnabled ? "Enabled" : "Disabled").append("\n");
        stats.append("Method: DNS-based with manual hosts file");

        return stats.toString();
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp-1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public String getCurrentDNSMode() {
        return mPrefs.getString(PREF_DNS_MODE, "none");
    }
}
