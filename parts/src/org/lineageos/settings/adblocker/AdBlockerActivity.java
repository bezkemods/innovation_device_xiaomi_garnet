package org.lineageos.settings.adblocker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import org.lineageos.settings.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AdBlockerActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "AdBlockerActivity";
    private static final int REQUEST_PICK_FILE = 1001;

    private static final String KEY_ADBLOCKER_ENABLED = "adblocker_enabled";
    private static final String KEY_ADBLOCKER_STATUS = "adblocker_status";
    private static final String KEY_ADBLOCKER_LAST_UPDATE = "adblocker_last_update";
    private static final String KEY_ADBLOCKER_UPDATE = "adblocker_update";
    private static final String KEY_ADBLOCKER_MANUAL_UPDATE = "adblocker_manual_update";
    private static final String KEY_ADBLOCKER_INFO = "adblocker_info";
    private static final String KEY_ADBLOCKER_METHOD = "adblocker_method";
    private static final String KEY_ADBLOCKER_GITHUB = "adblocker_github";
    
    // VPN & Proxy keys
    private static final String KEY_VPN_CATEGORY = "vpn_category";
    private static final String KEY_VPN_STATUS = "vpn_status";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_VPN_AUTO_CONNECT = "vpn_auto_connect";
    private static final String KEY_VPN_FREE_SERVERS = "vpn_free_servers";
    
    private static final String KEY_PROXY_CATEGORY = "proxy_category";
    private static final String KEY_PROXY_ENABLED = "proxy_enabled";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final String KEY_PROXY_AUTO_SELECT = "proxy_auto_select";
    private static final String KEY_PROXY_UPDATE_LIST = "proxy_update_list";
    private static final String KEY_PROXY_TEST = "proxy_test";
    
    // DNS keys
    private static final String KEY_DNS_CATEGORY = "dns_category";
    private static final String KEY_DNS_PROVIDER = "dns_provider";
    private static final String KEY_DOH_ENABLED = "doh_enabled";
    private static final String KEY_DNS_FALLBACK = "dns_fallback";
    
    // Whitelist/Blacklist keys
    private static final String KEY_FILTER_CATEGORY = "filter_category";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_APP_FILTER = "app_filter";
    
    // Statistics keys
    private static final String KEY_STATS_CATEGORY = "stats_category";
    private static final String KEY_STATS_BLOCKED = "stats_blocked";
    private static final String KEY_STATS_BANDWIDTH = "stats_bandwidth";
    private static final String KEY_STATS_RESET = "stats_reset";

    private SwitchPreference mAdBlockerEnabled;
    private Preference mAdBlockerStatus;
    private Preference mLastUpdate;
    private Preference mUpdate;
    private Preference mManualUpdate;
    private Preference mInfo;
    private Preference mMethod;
    private Preference mGitHub;
    
    // VPN & Proxy preferences
    private Preference mVpnStatus;
    private Preference mVpnSettings;
    private SwitchPreference mVpnAutoConnect;
    private Preference mVpnFreeServers;
    
    private SwitchPreference mProxyEnabled;
    private EditTextPreference mProxyHost;
    private EditTextPreference mProxyPort;
    private SwitchPreference mProxyAutoSelect;
    private Preference mProxyUpdateList;
    private Preference mProxyTest;
    
    // DNS preferences
    private ListPreference mDnsProvider;
    private SwitchPreference mDohEnabled;
    private SwitchPreference mDnsFallback;
    
    // Filter preferences
    private Preference mWhitelist;
    private Preference mBlacklist;
    private Preference mAppFilter;
    
    // Statistics preferences
    private Preference mStatsBlocked;
    private Preference mStatsBandwidth;
    private Preference mStatsReset;

    private AdBlockerUtils mAdBlockerUtils;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() called");

        addPreferencesFromResource(R.xml.adblocker_settings);

        mAdBlockerUtils = new AdBlockerUtils(this);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        initializePreferences();
        updateUI();

        Log.d(TAG, "onCreate() completed");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
        updateUI();
    }

    private void initializePreferences() {
        Log.d(TAG, "initializePreferences() called");

        // Basic preferences
        mAdBlockerEnabled = (SwitchPreference) findPreference(KEY_ADBLOCKER_ENABLED);
        mAdBlockerStatus = findPreference(KEY_ADBLOCKER_STATUS);
        mLastUpdate = findPreference(KEY_ADBLOCKER_LAST_UPDATE);
        mUpdate = findPreference(KEY_ADBLOCKER_UPDATE);
        mManualUpdate = findPreference(KEY_ADBLOCKER_MANUAL_UPDATE);
        mInfo = findPreference(KEY_ADBLOCKER_INFO);
        mMethod = findPreference(KEY_ADBLOCKER_METHOD);
        mGitHub = findPreference(KEY_ADBLOCKER_GITHUB);
        
        // VPN & Proxy preferences
        mVpnStatus = findPreference(KEY_VPN_STATUS);
        mVpnSettings = findPreference(KEY_VPN_SETTINGS);
        mVpnAutoConnect = (SwitchPreference) findPreference(KEY_VPN_AUTO_CONNECT);
        mVpnFreeServers = findPreference(KEY_VPN_FREE_SERVERS);
        
        mProxyEnabled = (SwitchPreference) findPreference(KEY_PROXY_ENABLED);
        mProxyHost = (EditTextPreference) findPreference(KEY_PROXY_HOST);
        mProxyPort = (EditTextPreference) findPreference(KEY_PROXY_PORT);
        mProxyAutoSelect = (SwitchPreference) findPreference(KEY_PROXY_AUTO_SELECT);
        mProxyUpdateList = findPreference(KEY_PROXY_UPDATE_LIST);
        mProxyTest = findPreference(KEY_PROXY_TEST);
        
        // DNS preferences
        mDnsProvider = (ListPreference) findPreference(KEY_DNS_PROVIDER);
        mDohEnabled = (SwitchPreference) findPreference(KEY_DOH_ENABLED);
        mDnsFallback = (SwitchPreference) findPreference(KEY_DNS_FALLBACK);
        
        // Filter preferences
        mWhitelist = findPreference(KEY_WHITELIST);
        mBlacklist = findPreference(KEY_BLACKLIST);
        mAppFilter = findPreference(KEY_APP_FILTER);
        
        // Statistics preferences
        mStatsBlocked = findPreference(KEY_STATS_BLOCKED);
        mStatsBandwidth = findPreference(KEY_STATS_BANDWIDTH);
        mStatsReset = findPreference(KEY_STATS_RESET);

        // Set up listeners
        if (mAdBlockerEnabled != null) {
            mAdBlockerEnabled.setOnPreferenceChangeListener(this);
        }

        if (mUpdate != null) {
            mUpdate.setOnPreferenceClickListener(this);
        }

        if (mManualUpdate != null) {
            mManualUpdate.setOnPreferenceClickListener(this);
        }

        if (mInfo != null) {
            mInfo.setOnPreferenceClickListener(this);
        }

        if (mMethod != null) {
            mMethod.setOnPreferenceClickListener(this);
        }

        if (mGitHub != null) {
            mGitHub.setOnPreferenceClickListener(this);
        }
        
        // VPN & Proxy listeners
        if (mVpnSettings != null) {
            mVpnSettings.setOnPreferenceClickListener(this);
        }
        
        if (mVpnAutoConnect != null) {
            mVpnAutoConnect.setOnPreferenceChangeListener(this);
        }
        
        if (mVpnFreeServers != null) {
            mVpnFreeServers.setOnPreferenceClickListener(this);
        }
        
        if (mProxyEnabled != null) {
            mProxyEnabled.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyHost != null) {
            mProxyHost.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyPort != null) {
            mProxyPort.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyAutoSelect != null) {
            mProxyAutoSelect.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyUpdateList != null) {
            mProxyUpdateList.setOnPreferenceClickListener(this);
        }
        
        if (mProxyTest != null) {
            mProxyTest.setOnPreferenceClickListener(this);
        }
        
        // DNS listeners
        if (mDnsProvider != null) {
            mDnsProvider.setOnPreferenceChangeListener(this);
        }
        
        if (mDohEnabled != null) {
            mDohEnabled.setOnPreferenceChangeListener(this);
        }
        
        if (mDnsFallback != null) {
            mDnsFallback.setOnPreferenceChangeListener(this);
        }
        
        // Filter listeners
        if (mWhitelist != null) {
            mWhitelist.setOnPreferenceClickListener(this);
        }
        
        if (mBlacklist != null) {
            mBlacklist.setOnPreferenceClickListener(this);
        }
        
        if (mAppFilter != null) {
            mAppFilter.setOnPreferenceClickListener(this);
        }
        
        // Statistics listeners
        if (mStatsReset != null) {
            mStatsReset.setOnPreferenceClickListener(this);
        }

        Log.d(TAG, "All preferences initialized");
    }

    private void updateUI() {
        Log.d(TAG, "updateUI() called");

        boolean isEnabled = mAdBlockerUtils.isEnabled();
        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mAdBlockerUtils.getLastUpdateTime();

        Log.d(TAG, "UI Update - Enabled: " + isEnabled + ", Blocked: " + blockedCount + ", LastUpdate: " + lastUpdate);

        // Basic UI updates
        if (mAdBlockerEnabled != null) {
            mAdBlockerEnabled.setChecked(isEnabled);
        }

        if (mAdBlockerStatus != null) {
            String statusText = isEnabled ?
                getString(R.string.adblocker_status_enabled) :
                getString(R.string.adblocker_status_disabled);
            String dnsMode = mAdBlockerUtils.getCurrentDNSMode();
            mAdBlockerStatus.setSummary(statusText + " (DNS: " + dnsMode + ")");
        }

        if (mLastUpdate != null) {
            mLastUpdate.setSummary(lastUpdate);
        }

        if (mInfo != null) {
            if (blockedCount > 0) {
                mInfo.setSummary(getString(R.string.adblocker_blocked_domains, blockedCount));
            } else {
                mInfo.setSummary("No hosts file loaded - Load manually!");
            }
        }

        if (mMethod != null) {
            boolean hasRoot = mAdBlockerUtils.hasRootAccess();
            String methodText = hasRoot ? "DNS + Root optimization" : "DNS-based blocking";
            mMethod.setSummary(methodText);
        }
        
        // Update VPN status
        if (mVpnStatus != null) {
            boolean vpnConnected = mAdBlockerUtils.isVpnConnected();
            mVpnStatus.setSummary(vpnConnected ? "VPN Connected" : "VPN Not Connected");
        }
        
        if (mVpnAutoConnect != null) {
            boolean autoConnect = mAdBlockerUtils.isVpnAutoConnectEnabled();
            mVpnAutoConnect.setChecked(autoConnect);
        }
        
        // Update Proxy settings
        if (mProxyEnabled != null) {
            boolean proxyEnabled = mAdBlockerUtils.isProxyEnabled();
            mProxyEnabled.setChecked(proxyEnabled);
        }
        
        if (mProxyHost != null) {
            String host = mAdBlockerUtils.getProxyHost();
            mProxyHost.setText(host);
            mProxyHost.setSummary(host.isEmpty() ? "Not set" : host);
        }
        
        if (mProxyPort != null) {
            int port = mAdBlockerUtils.getProxyPort();
            mProxyPort.setText(String.valueOf(port));
            mProxyPort.setSummary(String.valueOf(port));
        }
        
        if (mProxyAutoSelect != null) {
            boolean autoSelect = mAdBlockerUtils.isProxyAutoSelectEnabled();
            mProxyAutoSelect.setChecked(autoSelect);
        }
        
        // Update DNS settings
        if (mDnsProvider != null) {
            String provider = mAdBlockerUtils.getDnsProvider();
            mDnsProvider.setValue(provider);
            mDnsProvider.setSummary(getDnsProviderName(provider));
        }
        
        if (mDohEnabled != null) {
            boolean dohEnabled = mAdBlockerUtils.isDohEnabled();
            mDohEnabled.setChecked(dohEnabled);
        }
        
        if (mDnsFallback != null) {
            boolean fallbackEnabled = mAdBlockerUtils.isDnsFallbackEnabled();
            mDnsFallback.setChecked(fallbackEnabled);
        }
        
        // Update filter counts
        if (mWhitelist != null) {
            int whitelistCount = mAdBlockerUtils.getWhitelistCount();
            mWhitelist.setSummary(whitelistCount + " domains whitelisted");
        }
        
        if (mBlacklist != null) {
            int blacklistCount = mAdBlockerUtils.getBlacklistCount();
            mBlacklist.setSummary(blacklistCount + " additional domains blocked");
        }
        
        if (mAppFilter != null) {
            int filteredApps = mAdBlockerUtils.getFilteredAppsCount();
            mAppFilter.setSummary(filteredApps + " apps filtered");
        }
        
        // Update statistics
        if (mStatsBlocked != null) {
            long totalBlocked = mAdBlockerUtils.getTotalBlockedRequests();
            mStatsBlocked.setSummary(String.format("%,d requests blocked", totalBlocked));
        }
        
        if (mStatsBandwidth != null) {
            long savedBytes = mAdBlockerUtils.getSavedBandwidth();
            String savedText = formatBytes(savedBytes);
            mStatsBandwidth.setSummary(savedText + " bandwidth saved");
        }

        Log.d(TAG, "updateUI() completed");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceChange() - Key: " + key + ", Value: " + newValue);

        switch (key) {
            case KEY_ADBLOCKER_ENABLED:
                handleAdBlockerToggle((Boolean) newValue);
                return false;
                
            case KEY_PROXY_ENABLED:
                handleProxyToggle((Boolean) newValue);
                return true;
                
            case KEY_PROXY_HOST:
                mAdBlockerUtils.setProxyHost((String) newValue);
                mProxyHost.setSummary(((String) newValue).isEmpty() ? "Not set" : (String) newValue);
                return true;
                
            case KEY_PROXY_PORT:
                try {
                    int port = Integer.parseInt((String) newValue);
                    mAdBlockerUtils.setProxyPort(port);
                    mProxyPort.setSummary(String.valueOf(port));
                    return true;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
                    return false;
                }
                
            case KEY_PROXY_AUTO_SELECT:
                handleProxyAutoSelect((Boolean) newValue);
                return true;
                
            case KEY_VPN_AUTO_CONNECT:
                mAdBlockerUtils.setVpnAutoConnect((Boolean) newValue);
                return true;
                
            case KEY_DNS_PROVIDER:
                handleDnsProviderChange((String) newValue);
                return true;
                
            case KEY_DOH_ENABLED:
                mAdBlockerUtils.setDohEnabled((Boolean) newValue);
                updateUI();
                return true;
                
            case KEY_DNS_FALLBACK:
                mAdBlockerUtils.setDnsFallback((Boolean) newValue);
                return true;
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceClick() - Key: " + key);

        switch (key) {
            case KEY_ADBLOCKER_UPDATE:
                handleUpdate();
                return true;
            case KEY_ADBLOCKER_MANUAL_UPDATE:
                handleManualUpdate();
                return true;
            case KEY_ADBLOCKER_INFO:
                showInfoDialog();
                return true;
            case KEY_ADBLOCKER_METHOD:
                showMethodDialog();
                return true;
            case KEY_ADBLOCKER_GITHUB:
                openGitHubPage();
                return true;
            case KEY_VPN_SETTINGS:
                openVpnSettings();
                return true;
            case KEY_VPN_FREE_SERVERS:
                showFreeVpnServers();
                return true;
            case KEY_PROXY_UPDATE_LIST:
                updateProxyList();
                return true;
            case KEY_PROXY_TEST:
                testProxy();
                return true;
            case KEY_WHITELIST:
                showWhitelistDialog();
                return true;
            case KEY_BLACKLIST:
                showBlacklistDialog();
                return true;
            case KEY_APP_FILTER:
                showAppFilterDialog();
                return true;
            case KEY_STATS_RESET:
                resetStatistics();
                return true;
        }

        return false;
    }

    private void handleAdBlockerToggle(boolean enable) {
        Log.d(TAG, "handleAdBlockerToggle(" + enable + ")");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_confirm_title);

        if (enable) {
            builder.setMessage(getString(R.string.adblocker_confirm_enable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                if (mAdBlockerUtils.getBlockedDomainsCount() == 0) {
                    showFirstTimeSetupDialog();
                } else {
                    enableAdBlocker();
                }
            });
        } else {
            builder.setMessage(getString(R.string.adblocker_confirm_disable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> disableAdBlocker());
        }

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
    
    private void handleProxyToggle(boolean enable) {
        Log.d(TAG, "handleProxyToggle(" + enable + ")");
        
        if (enable) {
            String host = mAdBlockerUtils.getProxyHost();
            int port = mAdBlockerUtils.getProxyPort();
            
            if (host.isEmpty()) {
                Toast.makeText(this, "Please set proxy host first", Toast.LENGTH_SHORT).show();
                mProxyEnabled.setChecked(false);
                return;
            }
            
            if (mAdBlockerUtils.hasRootAccess()) {
                boolean success = mAdBlockerUtils.setGlobalProxy(host, port);
                if (success) {
                    mAdBlockerUtils.setProxyEnabled(true);
                    Toast.makeText(this, "Global proxy enabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to set global proxy", Toast.LENGTH_LONG).show();
                    mProxyEnabled.setChecked(false);
                }
            } else {
                mAdBlockerUtils.setProxyEnabled(true);
                Toast.makeText(this, "Proxy settings saved (root required for global proxy)", Toast.LENGTH_LONG).show();
            }
        } else {
            if (mAdBlockerUtils.hasRootAccess()) {
                mAdBlockerUtils.clearGlobalProxy();
            }
            mAdBlockerUtils.setProxyEnabled(false);
            Toast.makeText(this, "Proxy disabled", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleProxyAutoSelect(boolean enable) {
        if (enable) {
            Toast.makeText(this, "Fetching best proxy server...", Toast.LENGTH_SHORT).show();
            mAdBlockerUtils.findBestProxy(new AdBlockerUtils.ProxyTestCallback() {
                @Override
                public void onProxyTested(String host, int port, long latency) {
                    runOnUiThread(() -> {
                        mProxyHost.setText(host);
                        mProxyPort.setText(String.valueOf(port));
                        Toast.makeText(AdBlockerActivity.this, 
                            String.format("Best proxy: %s:%d (%dms)", host, port, latency), 
                            Toast.LENGTH_LONG).show();
                        updateUI();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            "Failed to find proxy: " + error, 
                            Toast.LENGTH_LONG).show();
                        mProxyAutoSelect.setChecked(false);
                    });
                }
            });
        }
        mAdBlockerUtils.setProxyAutoSelect(enable);
    }
    
    private void handleDnsProviderChange(String provider) {
        boolean success = mAdBlockerUtils.setDnsProvider(provider);
        if (success) {
            Toast.makeText(this, "DNS provider changed to " + getDnsProviderName(provider), 
                Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, "Failed to change DNS provider", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFirstTimeSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("First-time setup");
        builder.setMessage("To use AdBlocker, you need to load a hosts file first.\n\n" +
                "1. Tap 'Download hosts file' to open GitHub\n" +
                "2. On GitHub page, tap the 'Download raw file' button\n" +
                "3. Come back here and tap 'Load hosts file'\n" +
                "4. Select the downloaded 'hosts' file\n\n" +
                "Would you like to open GitHub now?");
        
        builder.setPositiveButton("Download hosts file", (dialog, which) -> {
            openGitHubHostsFile();
        });
        
        builder.setNegativeButton("Load hosts file", (dialog, which) -> {
            handleManualUpdate();
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void enableAdBlocker() {
        Log.d(TAG, "enableAdBlocker() called");

        if (mAdBlockerUtils.enableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_enabled, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "AdBlocker enabled successfully");
            updateUI();
        } else {
            Toast.makeText(this, "Failed to enable AdBlocker!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to enable AdBlocker");
        }
    }

    private void disableAdBlocker() {
        Log.d(TAG, "disableAdBlocker() called");

        if (mAdBlockerUtils.disableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_disabled, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "AdBlocker disabled successfully");
            updateUI();
        } else {
            Toast.makeText(this, "Failed to disable AdBlocker!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to disable AdBlocker");
        }
    }

    private void handleUpdate() {
        Log.d(TAG, "handleUpdate() called");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manual hosts file update");
        builder.setMessage("To update the hosts file:\n\n" +
                "1. Tap 'Download hosts file' to open GitHub\n" +
                "2. On GitHub page, tap the 'Download raw file' button\n" +
                "3. Come back here and tap 'Load hosts file'\n" +
                "4. Select the downloaded 'hosts' file\n\n" +
                "The hosts file blocks ads and trackers across your device.");

        builder.setPositiveButton("Download hosts file", (dialog, which) -> {
            openGitHubHostsFile();
        });

        builder.setNegativeButton("Load hosts file", (dialog, which) -> {
            handleManualUpdate();
        });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void handleManualUpdate() {
        Log.d(TAG, "handleManualUpdate() called");

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.adblocker_manual_file_title)),
                REQUEST_PICK_FILE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a file manager!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No file manager available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult() - RequestCode: " + requestCode + ", ResultCode: " + resultCode);

        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    Log.d(TAG, "Selected file URI: " + uri.toString());
                    loadHostsFileFromUri(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadHostsFileFromUri(Uri uri) {
        Log.d(TAG, "loadHostsFileFromUri() called with URI: " + uri);

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                lineCount++;
            }

            reader.close();
            inputStream.close();

            Log.d(TAG, "Read " + lineCount + " lines from file, total length: " + content.length());

            String hostsContent = content.toString();
            if (hostsContent.trim().isEmpty()) {
                Toast.makeText(this, "The file is empty or unreadable!", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Empty hosts file selected");
                return;
            }

            mAdBlockerUtils.updateHostsFileFromContent(hostsContent, new AdBlockerUtils.UpdateCallback() {
                @Override
                public void onUpdateStart() {
                    Log.d(TAG, "Manual update started");
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this,
                            "Processing hosts file...", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onUpdateSuccess(int blockedCount) {
                    Log.d(TAG, "Manual update successful, blocked count: " + blockedCount);
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this,
                            "Hosts file loaded successfully! " + blockedCount + " domains.", Toast.LENGTH_LONG).show();
                        updateUI();
                        
                        if (!mAdBlockerUtils.isEnabled()) {
                            showEnableAfterLoadDialog();
                        }
                    });
                }

                @Override
                public void onUpdateError(String error) {
                    Log.e(TAG, "Manual update error: " + error);
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this,
                            "Hosts file error: " + error, Toast.LENGTH_LONG).show();
                        showDebugDialog("Hosts File Load Error", error);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load hosts file", e);
            Toast.makeText(this, "File read error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEnableAfterLoadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hosts file loaded");
        builder.setMessage("The hosts file has been loaded successfully!\n\nWould you like to enable AdBlocker now?");
        
        builder.setPositiveButton("Enable AdBlocker", (dialog, which) -> {
            enableAdBlocker();
        });
        
        builder.setNegativeButton("Later", null);
        builder.show();
    }

    private void openGitHubHostsFile() {
        Log.d(TAG, "openGitHubHostsFile() called");

        String githubUrl = "https://github.com/StevenBlack/hosts/blob/master/hosts";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
        try {
            startActivity(intent);
            Toast.makeText(this, "On GitHub page, tap 'Download raw file' button", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open GitHub page", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to open GitHub page", e);
        }
    }

    private void openGitHubPage() {
        Log.d(TAG, "openGitHubPage() called");

        String githubUrl = "https://github.com/StevenBlack/hosts/blob/master/hosts";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open GitHub page", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to open GitHub page", e);
        }
    }
    
    private void openVpnSettings() {
        Log.d(TAG, "openVpnSettings() called");
        
        try {
            Intent intent = new Intent("android.net.vpn.SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open VPN settings", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to open VPN settings", e);
        }
    }
    
    private void showFreeVpnServers() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Free VPN Resources");
        builder.setMessage("⚠️ Security Notice:\n\n" +
                "Free VPN services may:\n" +
                "• Log your activity\n" +
                "• Inject ads\n" +
                "• Sell your data\n" +
                "• Have slow speeds\n\n" +
                "Recommended alternatives:\n" +
                "• Use built-in Private DNS\n" +
                "• Configure trusted proxy\n" +
                "• Use WireGuard with own server\n\n" +
                "For education only. Visit GitHub for configurations.");
        
        builder.setPositiveButton("View on GitHub", (dialog, which) -> {
            String url = "https://github.com/topics/free-vpn";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Failed to open browser", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void updateProxyList() {
        Toast.makeText(this, "Fetching proxy list...", Toast.LENGTH_SHORT).show();
        
        mAdBlockerUtils.updateProxyList(new AdBlockerUtils.ProxyListCallback() {
            @Override
            public void onListUpdated(int count) {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this,
                        "Proxy list updated: " + count + " proxies available", 
                        Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this,
                        "Failed to update proxy list: " + error, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void testProxy() {
        String host = mAdBlockerUtils.getProxyHost();
        int port = mAdBlockerUtils.getProxyPort();
        
        if (host.isEmpty()) {
            Toast.makeText(this, "Please set proxy host first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Testing proxy connection...", Toast.LENGTH_SHORT).show();
        
        mAdBlockerUtils.testProxy(host, port, new AdBlockerUtils.ProxyTestCallback() {
            @Override
            public void onProxyTested(String testHost, int testPort, long latency) {
                runOnUiThread(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(AdBlockerActivity.this);
                    builder.setTitle("Proxy Test Result");
                    builder.setMessage(String.format(
                        "✓ Proxy is working!\n\n" +
                        "Host: %s\n" +
                        "Port: %d\n" +
                        "Latency: %d ms\n\n" +
                        "Status: %s",
                        testHost, testPort, latency,
                        latency < 100 ? "Excellent" : 
                        latency < 300 ? "Good" : 
                        latency < 1000 ? "Fair" : "Poor"
                    ));
                    builder.setPositiveButton("OK", null);
                    builder.show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(AdBlockerActivity.this);
                    builder.setTitle("Proxy Test Failed");
                    builder.setMessage("✗ Connection failed\n\nError: " + error + 
                        "\n\nPlease check:\n• Host and port are correct\n• Proxy is online\n• Network connection");
                    builder.setPositiveButton("OK", null);
                    builder.show();
                });
            }
        });
    }
    
    private void showWhitelistDialog() {
        String[] whitelistArray = mAdBlockerUtils.getWhitelist().toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Whitelist (" + whitelistArray.length + " domains)");
        
        if (whitelistArray.length == 0) {
            builder.setMessage("No whitelisted domains.\n\nWhitelisted domains will not be blocked even if they appear in the hosts file.");
        } else {
            builder.setItems(whitelistArray, (dialog, which) -> {
                showRemoveDomainDialog(whitelistArray[which], true);
            });
        }
        
        builder.setPositiveButton("Add Domain", (dialog, which) -> {
            showAddDomainDialog(true);
        });
        
        builder.setNegativeButton("Close", null);
        builder.show();
    }
    
    private void showBlacklistDialog() {
        String[] blacklistArray = mAdBlockerUtils.getBlacklist().toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Blacklist (" + blacklistArray.length + " domains)");
        
        if (blacklistArray.length == 0) {
            builder.setMessage("No custom blocked domains.\n\nAdd domains here to block them regardless of the hosts file.");
        } else {
            builder.setItems(blacklistArray, (dialog, which) -> {
                showRemoveDomainDialog(blacklistArray[which], false);
            });
        }
        
        builder.setPositiveButton("Add Domain", (dialog, which) -> {
            showAddDomainDialog(false);
        });
        
        builder.setNegativeButton("Close", null);
        builder.show();
    }
    
    private void showAddDomainDialog(boolean isWhitelist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Domain to " + (isWhitelist ? "Whitelist" : "Blacklist"));
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("example.com");
        builder.setView(input);
        
        builder.setPositiveButton("Add", (dialog, which) -> {
            String domain = input.getText().toString().trim().toLowerCase();
            if (domain.isEmpty()) {
                Toast.makeText(this, "Domain cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (isWhitelist) {
                mAdBlockerUtils.addToWhitelist(domain);
                Toast.makeText(this, "Added to whitelist: " + domain, Toast.LENGTH_SHORT).show();
            } else {
                mAdBlockerUtils.addToBlacklist(domain);
                Toast.makeText(this, "Added to blacklist: " + domain, Toast.LENGTH_SHORT).show();
            }
            updateUI();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showRemoveDomainDialog(String domain, boolean isWhitelist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove Domain");
        builder.setMessage("Remove " + domain + " from " + (isWhitelist ? "whitelist" : "blacklist") + "?");
        
        builder.setPositiveButton("Remove", (dialog, which) -> {
            if (isWhitelist) {
                mAdBlockerUtils.removeFromWhitelist(domain);
            } else {
                mAdBlockerUtils.removeFromBlacklist(domain);
            }
            Toast.makeText(this, "Removed: " + domain, Toast.LENGTH_SHORT).show();
            updateUI();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showAppFilterDialog() {
        Toast.makeText(this, "App filtering feature coming soon!", Toast.LENGTH_SHORT).show();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("App-based Filtering");
        builder.setMessage("This feature allows you to:\n\n" +
                "• Enable/disable AdBlocker per app\n" +
                "• Whitelist specific apps\n" +
                "• Monitor per-app blocked requests\n\n" +
                "Requires root access for full functionality.\n\n" +
                "Status: Coming in next update");
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    private void resetStatistics() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Statistics");
        builder.setMessage("Are you sure you want to reset all statistics?\n\n" +
                "This will clear:\n" +
                "• Blocked requests counter\n" +
                "• Bandwidth saved data\n" +
                "• DNS query logs");
        
        builder.setPositiveButton("Reset", (dialog, which) -> {
            mAdBlockerUtils.resetStatistics();
            Toast.makeText(this, "Statistics reset", Toast.LENGTH_SHORT).show();
            updateUI();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDebugDialog(String title, String error) {
        String debugLog = mAdBlockerUtils.getDebugLog();
        String fullMessage = "Error: " + error + "\n\n" +
                            "Debug information:\n" + debugLog;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(fullMessage);
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Clear Debug Log", (dialog, which) -> {
            mAdBlockerUtils.clearDebugLog();
            Toast.makeText(this, "Debug log cleared", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showInfoDialog() {
        Log.d(TAG, "showInfoDialog() called");

        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mAdBlockerUtils.getLastUpdateTime();
        boolean isEnabled = mAdBlockerUtils.isEnabled();
        boolean hasRoot = mAdBlockerUtils.hasRootAccess();
        boolean vpnConnected = mAdBlockerUtils.isVpnConnected();
        boolean proxyEnabled = mAdBlockerUtils.isProxyEnabled();
        boolean dohEnabled = mAdBlockerUtils.isDohEnabled();
        long totalBlocked = mAdBlockerUtils.getTotalBlockedRequests();

        StringBuilder info = new StringBuilder();
        info.append("Status: ").append(isEnabled ? "✓ Enabled" : "✗ Disabled").append("\n\n");
        info.append("═══ Protection ═══\n");
        info.append("Blocked domains: ").append(String.format("%,d", blockedCount)).append("\n");
        info.append("Total blocked: ").append(String.format("%,d", totalBlocked)).append(" requests\n");
        info.append("Last update: ").append(lastUpdate).append("\n\n");
        
        info.append("═══ Network ═══\n");
        info.append("DNS Provider: ").append(getDnsProviderName(mAdBlockerUtils.getDnsProvider())).append("\n");
        info.append("DNS over HTTPS: ").append(dohEnabled ? "✓ Enabled" : "✗ Disabled").append("\n");
        info.append("VPN: ").append(vpnConnected ? "✓ Connected" : "✗ Not connected").append("\n");
        info.append("Proxy: ").append(proxyEnabled ? "✓ Enabled" : "✗ Disabled").append("\n\n");
        
        info.append("═══ System ═══\n");
        info.append("Root access: ").append(hasRoot ? "✓ Available" : "✗ Not available").append("\n");
        info.append("Method: DNS-based + Manual hosts\n");
        info.append("Source: StevenBlack/hosts\n\n");

        if (isEnabled) {
            String dnsMode = mAdBlockerUtils.getCurrentDNSMode();
            info.append("Current DNS: ").append(dnsMode);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AdBlocker Information");
        builder.setMessage(info.toString());
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Debug Log", (dialog, which) -> {
            showDebugDialog("Debug Log", mAdBlockerUtils.getDebugLog());
        });
        builder.show();
    }

    private void showMethodDialog() {
        Log.d(TAG, "showMethodDialog() called");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AdBlocker Method");

        StringBuilder methodInfo = new StringBuilder();
        methodInfo.append("═══ DNS-based Blocking ═══\n\n");
        methodInfo.append("✓ No system partition write required\n");
        methodInfo.append("✓ Compatible with Android 16\n");
        methodInfo.append("✓ Affects all applications\n");
        methodInfo.append("✓ Low resource usage\n");
        methodInfo.append("✓ Persistent after restart\n\n");

        methodInfo.append("═══ Features ═══\n\n");
        methodInfo.append("• Multiple DNS Providers\n");
        methodInfo.append("  - AdGuard DNS (ad-blocking)\n");
        methodInfo.append("  - Cloudflare (privacy)\n");
        methodInfo.append("  - Quad9 (security)\n");
        methodInfo.append("  - Google DNS (fast)\n\n");
        
        methodInfo.append("• DNS over HTTPS (DoH)\n");
        methodInfo.append("  - Encrypted DNS queries\n");
        methodInfo.append("  - Prevents DNS hijacking\n");
        methodInfo.append("  - Better privacy\n\n");
        
        methodInfo.append("• Proxy Support\n");
        methodInfo.append("  - HTTP/HTTPS proxy\n");
        methodInfo.append("  - Auto-select best proxy\n");
        methodInfo.append("  - Connection testing\n\n");
        
        methodInfo.append("• Custom Filters\n");
        methodInfo.append("  - Whitelist trusted domains\n");
        methodInfo.append("  - Blacklist additional domains\n");
        methodInfo.append("  - Per-app filtering (coming soon)\n\n");

        if (mAdBlockerUtils.hasRootAccess()) {
            methodInfo.append("═══ Root Optimization ═══\n\n");
            methodInfo.append("✓ iptables DNS redirect\n");
            methodInfo.append("✓ Global proxy support\n");
            methodInfo.append("✓ Enhanced blocking\n");
            methodInfo.append("✓ System-wide enforcement");
        } else {
            methodInfo.append("═══ Non-root Mode ═══\n\n");
            methodInfo.append("• DNS-based blocking only\n");
            methodInfo.append("• Still very effective\n");
            methodInfo.append("• Most features available");
        }

        builder.setMessage(methodInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    private String getDnsProviderName(String provider) {
        switch (provider) {
            case "adguard": return "AdGuard DNS";
            case "cloudflare": return "Cloudflare";
            case "quad9": return "Quad9";
            case "google": return "Google DNS";
            default: return "Unknown";
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp-1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
