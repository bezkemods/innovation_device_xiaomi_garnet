package org.lineageos.settings.adblocker;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
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
import java.util.List;

public class AdBlockerActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "AdBlockerActivity";
    private static final int REQUEST_PICK_FILE = 1001;

    // Keys
    private static final String KEY_ADBLOCKER_ENABLED = "adblocker_enabled";
    private static final String KEY_ADBLOCKER_STATUS = "adblocker_status";
    private static final String KEY_ADBLOCKER_LAST_UPDATE = "adblocker_last_update";
    private static final String KEY_ADBLOCKER_UPDATE = "adblocker_update";
    private static final String KEY_ADBLOCKER_MANUAL_UPDATE = "adblocker_manual_update";
    private static final String KEY_ADBLOCKER_INFO = "adblocker_info";
    private static final String KEY_ADBLOCKER_METHOD = "adblocker_method";
    private static final String KEY_ADBLOCKER_GITHUB = "adblocker_github";
    
    // VPN & Proxy keys
    private static final String KEY_VPN_STATUS = "vpn_status";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_VPN_AUTO_CONNECT = "vpn_auto_connect";
    private static final String KEY_VPN_FREE_SERVERS = "vpn_free_servers";
    
    private static final String KEY_PROXY_ENABLED = "proxy_enabled";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final String KEY_PROXY_AUTO_SELECT = "proxy_auto_select";
    private static final String KEY_PROXY_UPDATE_LIST = "proxy_update_list";
    private static final String KEY_PROXY_TEST = "proxy_test";
    
    // DNS keys
    private static final String KEY_DNS_PROVIDER = "dns_provider";
    private static final String KEY_DOH_ENABLED = "doh_enabled";
    private static final String KEY_DNS_FALLBACK = "dns_fallback";
    
    // Filter keys
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_APP_FILTER = "app_filter";
    
    // Statistics keys
    private static final String KEY_STATS_BLOCKED = "stats_blocked";
    private static final String KEY_STATS_BANDWIDTH = "stats_bandwidth";
    private static final String KEY_STATS_RESET = "stats_reset";

    // Preference objects
    private SwitchPreference mAdBlockerEnabled;
    private Preference mAdBlockerStatus;
    private Preference mLastUpdate;
    private Preference mUpdate;
    private Preference mManualUpdate;
    private Preference mInfo;
    private Preference mMethod;
    private Preference mGitHub;
    
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
    
    private ListPreference mDnsProvider;
    private SwitchPreference mDohEnabled;
    private SwitchPreference mDnsFallback;
    
    private Preference mWhitelist;
    private Preference mBlacklist;
    private Preference mAppFilter;
    
    private Preference mStatsBlocked;
    private Preference mStatsBandwidth;
    private Preference mStatsReset;

    private AdBlockerUtils mAdBlockerUtils;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.adblocker_settings);

        mAdBlockerUtils = new AdBlockerUtils(this);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        initializePreferences();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void initializePreferences() {
        mAdBlockerEnabled = (SwitchPreference) findPreference(KEY_ADBLOCKER_ENABLED);
        mAdBlockerStatus = findPreference(KEY_ADBLOCKER_STATUS);
        mLastUpdate = findPreference(KEY_ADBLOCKER_LAST_UPDATE);
        mUpdate = findPreference(KEY_ADBLOCKER_UPDATE);
        mManualUpdate = findPreference(KEY_ADBLOCKER_MANUAL_UPDATE);
        mInfo = findPreference(KEY_ADBLOCKER_INFO);
        mMethod = findPreference(KEY_ADBLOCKER_METHOD);
        mGitHub = findPreference(KEY_ADBLOCKER_GITHUB);
        
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
        
        mDnsProvider = (ListPreference) findPreference(KEY_DNS_PROVIDER);
        mDohEnabled = (SwitchPreference) findPreference(KEY_DOH_ENABLED);
        mDnsFallback = (SwitchPreference) findPreference(KEY_DNS_FALLBACK);
        
        mWhitelist = findPreference(KEY_WHITELIST);
        mBlacklist = findPreference(KEY_BLACKLIST);
        mAppFilter = findPreference(KEY_APP_FILTER);
        
        mStatsBlocked = findPreference(KEY_STATS_BLOCKED);
        mStatsBandwidth = findPreference(KEY_STATS_BANDWIDTH);
        mStatsReset = findPreference(KEY_STATS_RESET);

        // Listeners
        if (mAdBlockerEnabled != null) mAdBlockerEnabled.setOnPreferenceChangeListener(this);
        if (mUpdate != null) mUpdate.setOnPreferenceClickListener(this);
        if (mManualUpdate != null) mManualUpdate.setOnPreferenceClickListener(this);
        if (mInfo != null) mInfo.setOnPreferenceClickListener(this);
        if (mMethod != null) mMethod.setOnPreferenceClickListener(this);
        if (mGitHub != null) mGitHub.setOnPreferenceClickListener(this);
        if (mVpnSettings != null) mVpnSettings.setOnPreferenceClickListener(this);
        if (mVpnAutoConnect != null) mVpnAutoConnect.setOnPreferenceChangeListener(this);
        if (mVpnFreeServers != null) mVpnFreeServers.setOnPreferenceClickListener(this);
        if (mProxyEnabled != null) mProxyEnabled.setOnPreferenceChangeListener(this);
        if (mProxyHost != null) mProxyHost.setOnPreferenceChangeListener(this);
        if (mProxyPort != null) mProxyPort.setOnPreferenceChangeListener(this);
        if (mProxyAutoSelect != null) mProxyAutoSelect.setOnPreferenceChangeListener(this);
        if (mProxyUpdateList != null) mProxyUpdateList.setOnPreferenceClickListener(this);
        if (mProxyTest != null) mProxyTest.setOnPreferenceClickListener(this);
        if (mDnsProvider != null) mDnsProvider.setOnPreferenceChangeListener(this);
        if (mDohEnabled != null) mDohEnabled.setOnPreferenceChangeListener(this);
        if (mDnsFallback != null) mDnsFallback.setOnPreferenceChangeListener(this);
        if (mWhitelist != null) mWhitelist.setOnPreferenceClickListener(this);
        if (mBlacklist != null) mBlacklist.setOnPreferenceClickListener(this);
        if (mAppFilter != null) mAppFilter.setOnPreferenceClickListener(this);
        if (mStatsReset != null) mStatsReset.setOnPreferenceClickListener(this);
    }

    private void updateUI() {
        boolean isEnabled = mAdBlockerUtils.isEnabled();
        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();

        if (mAdBlockerEnabled != null) mAdBlockerEnabled.setChecked(isEnabled);
        if (mAdBlockerStatus != null) {
            String status = isEnabled ? getString(R.string.adblocker_status_enabled) : getString(R.string.adblocker_status_disabled);
            mAdBlockerStatus.setSummary(status + " (DNS: " + mAdBlockerUtils.getCurrentDNSMode() + ")");
        }
        if (mLastUpdate != null) mLastUpdate.setSummary(mAdBlockerUtils.getLastUpdateTime());
        if (mInfo != null) mInfo.setSummary(blockedCount > 0 ? getString(R.string.adblocker_blocked_domains, blockedCount) : "No hosts file loaded");
        if (mMethod != null) mMethod.setSummary(mAdBlockerUtils.hasRootAccess() ? "DNS + Root optimization" : "DNS-based blocking");
        
        if (mVpnStatus != null) mVpnStatus.setSummary(mAdBlockerUtils.isVpnConnected() ? "VPN Connected" : "VPN Not Connected");
        if (mVpnAutoConnect != null) mVpnAutoConnect.setChecked(mAdBlockerUtils.isVpnAutoConnectEnabled());
        
        if (mProxyEnabled != null) mProxyEnabled.setChecked(mAdBlockerUtils.isProxyEnabled());
        if (mProxyHost != null) {
            String host = mAdBlockerUtils.getProxyHost();
            mProxyHost.setSummary(host.isEmpty() ? "Not set" : host);
            mProxyHost.setText(host);
        }
        if (mProxyPort != null) {
            int port = mAdBlockerUtils.getProxyPort();
            mProxyPort.setSummary(String.valueOf(port));
            mProxyPort.setText(String.valueOf(port));
        }
        if (mProxyAutoSelect != null) mProxyAutoSelect.setChecked(mAdBlockerUtils.isProxyAutoSelectEnabled());
        
        if (mDnsProvider != null) {
            String provider = mAdBlockerUtils.getDnsProvider();
            mDnsProvider.setValue(provider);
            mDnsProvider.setSummary(getDnsProviderName(provider));
        }
        if (mDohEnabled != null) mDohEnabled.setChecked(mAdBlockerUtils.isDohEnabled());
        if (mDnsFallback != null) mDnsFallback.setChecked(mAdBlockerUtils.isDnsFallbackEnabled());
        
        if (mWhitelist != null) mWhitelist.setSummary(mAdBlockerUtils.getWhitelistCount() + " domains");
        if (mBlacklist != null) mBlacklist.setSummary(mAdBlockerUtils.getBlacklistCount() + " domains");
        if (mAppFilter != null) mAppFilter.setSummary(mAdBlockerUtils.getFilteredAppsCount() + " apps");
        
        if (mStatsBlocked != null) mStatsBlocked.setSummary(String.format("%,d requests", mAdBlockerUtils.getTotalBlockedRequests()));
        if (mStatsBandwidth != null) mStatsBandwidth.setSummary(formatBytes(mAdBlockerUtils.getSavedBandwidth()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        switch (key) {
            case KEY_ADBLOCKER_ENABLED:
                handleAdBlockerToggle((Boolean) newValue);
                return false;
            case KEY_PROXY_ENABLED:
                handleProxyToggle((Boolean) newValue);
                return true;
            case KEY_PROXY_HOST:
                mAdBlockerUtils.setProxyHost((String) newValue);
                mProxyHost.setSummary((String) newValue);
                return true;
            case KEY_PROXY_PORT:
                try {
                    int port = Integer.parseInt((String) newValue);
                    mAdBlockerUtils.setProxyPort(port);
                    mProxyPort.setSummary(String.valueOf(port));
                    return true;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
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
                showFreeVpnServers(); // UPDATED METHOD
                return true;
            case KEY_PROXY_UPDATE_LIST:
                updateProxyList(); // UPDATED METHOD
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

    // --- NEW / UPDATED METHODS ---

    private void updateProxyList() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Updating proxy list from GitHub...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        mAdBlockerUtils.updateProxyList(new AdBlockerUtils.ProxyListCallback() {
            @Override
            public void onListUpdated(int count) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(AdBlockerActivity.this, "Updated: " + count + " proxies found.", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(AdBlockerActivity.this, "Update failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFreeVpnServers() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.vpn_free_servers_title);
        builder.setMessage("Fetch free VPN configurations (vmess/ss/trojan) from 'sharkDoor/vpn-free-nodes'.\n\n1. Fetch Nodes\n2. Select to copy\n3. Import to VPN app.");
        
        builder.setPositiveButton("Fetch Nodes", (dialog, which) -> fetchAndShowVpnList());
        
        builder.setNegativeButton("Open GitHub", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sharkDoor/vpn-free-nodes"));
            try { startActivity(intent); } catch (Exception e) {}
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }

    private void fetchAndShowVpnList() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Fetching VPN nodes...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mAdBlockerUtils.fetchFreeVpnNodes(new AdBlockerUtils.VpnListCallback() {
            @Override
            public void onVpnListFetched(List<String> nodes) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    showVpnSelectionDialog(nodes);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(AdBlockerActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showVpnSelectionDialog(List<String> nodes) {
        String[] items = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            String label = "Node " + (i + 1) + ": ";
            if (node.startsWith("vmess://")) label += "VMess";
            else if (node.startsWith("ss://")) label += "Shadowsocks";
            else if (node.startsWith("trojan://")) label += "Trojan";
            else label += "Unknown";
            
            if (node.contains("#")) {
                try {
                    label += " (" + java.net.URLDecoder.decode(node.substring(node.lastIndexOf("#") + 1), "UTF-8") + ")";
                } catch (Exception e) {
                    label += " (" + node.substring(node.lastIndexOf("#") + 1) + ")";
                }
            }
            items[i] = label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select to Copy");
        builder.setItems(items, (dialog, which) -> {
            copyToClipboard(nodes.get(which));
            Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("VPN Config", text);
        if (clipboard != null) clipboard.setPrimaryClip(clip);
    }

    // --- EXISTING METHODS (Simplified for brevity, logic remains) ---

    private void handleAdBlockerToggle(boolean enable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_confirm_title);
        if (enable) {
            builder.setMessage(getString(R.string.adblocker_confirm_enable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                if (mAdBlockerUtils.getBlockedDomainsCount() == 0) showFirstTimeSetupDialog();
                else enableAdBlocker();
            });
        } else {
            builder.setMessage(getString(R.string.adblocker_confirm_disable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> disableAdBlocker());
        }
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
    
    private void handleProxyToggle(boolean enable) {
        if (enable) {
            String host = mAdBlockerUtils.getProxyHost();
            int port = mAdBlockerUtils.getProxyPort();
            if (host.isEmpty()) {
                Toast.makeText(this, "Set proxy host first", Toast.LENGTH_SHORT).show();
                mProxyEnabled.setChecked(false);
                return;
            }
            if (mAdBlockerUtils.hasRootAccess()) {
                if (mAdBlockerUtils.setGlobalProxy(host, port)) {
                    mAdBlockerUtils.setProxyEnabled(true);
                    Toast.makeText(this, "Global proxy enabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to set global proxy", Toast.LENGTH_SHORT).show();
                    mProxyEnabled.setChecked(false);
                }
            } else {
                mAdBlockerUtils.setProxyEnabled(true);
                Toast.makeText(this, "Proxy enabled (Non-root)", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (mAdBlockerUtils.hasRootAccess()) mAdBlockerUtils.clearGlobalProxy();
            mAdBlockerUtils.setProxyEnabled(false);
        }
    }
    
    private void handleProxyAutoSelect(boolean enable) {
        if (enable) {
            Toast.makeText(this, "Finding best proxy...", Toast.LENGTH_SHORT).show();
            mAdBlockerUtils.findBestProxy(new AdBlockerUtils.ProxyTestCallback() {
                @Override
                public void onProxyTested(String host, int port, long latency) {
                    runOnUiThread(() -> {
                        mProxyHost.setText(host);
                        mProxyPort.setText(String.valueOf(port));
                        Toast.makeText(AdBlockerActivity.this, "Best proxy: " + host + " (" + latency + "ms)", Toast.LENGTH_LONG).show();
                        updateUI();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                        mProxyAutoSelect.setChecked(false);
                    });
                }
            });
        }
        mAdBlockerUtils.setProxyAutoSelect(enable);
    }

    private void handleDnsProviderChange(String provider) {
        if (mAdBlockerUtils.setDnsProvider(provider)) updateUI();
    }

    private void showFirstTimeSetupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("First-time setup")
            .setMessage("Download and load hosts file first.")
            .setPositiveButton("Download", (d, w) -> openGitHubHostsFile())
            .setNegativeButton("Load", (d, w) -> handleManualUpdate())
            .show();
    }

    private void enableAdBlocker() {
        if (mAdBlockerUtils.enableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_enabled, Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, "Failed to enable", Toast.LENGTH_SHORT).show();
        }
    }

    private void disableAdBlocker() {
        if (mAdBlockerUtils.disableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_disabled, Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, "Failed to disable", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleUpdate() {
        new AlertDialog.Builder(this)
            .setTitle("Update hosts file")
            .setMessage("Download new hosts file from GitHub?")
            .setPositiveButton("Download", (d, w) -> openGitHubHostsFile())
            .setNegativeButton("Load", (d, w) -> handleManualUpdate())
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void handleManualUpdate() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.adblocker_manual_file_title)), REQUEST_PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) loadHostsFileFromUri(uri);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadHostsFileFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append("\n");
            reader.close();
            is.close();

            mAdBlockerUtils.updateHostsFileFromContent(content.toString(), new AdBlockerUtils.UpdateCallback() {
                @Override
                public void onUpdateStart() {
                    runOnUiThread(() -> Toast.makeText(AdBlockerActivity.this, "Processing...", Toast.LENGTH_SHORT).show());
                }
                @Override
                public void onUpdateSuccess(int count) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, "Loaded " + count + " domains", Toast.LENGTH_LONG).show();
                        updateUI();
                        if (!mAdBlockerUtils.isEnabled()) enableAdBlocker();
                    });
                }
                @Override
                public void onUpdateError(String error) {
                    runOnUiThread(() -> Toast.makeText(AdBlockerActivity.this, "Error: " + error, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Read error", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGitHubHostsFile() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/StevenBlack/hosts/blob/master/hosts"));
        try { startActivity(intent); } catch (Exception e) {}
    }

    private void openGitHubPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/StevenBlack/hosts"));
        try { startActivity(intent); } catch (Exception e) {}
    }
    
    private void openVpnSettings() {
        try {
            Intent intent = new Intent("android.net.vpn.SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open VPN settings", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void testProxy() {
        String host = mAdBlockerUtils.getProxyHost();
        int port = mAdBlockerUtils.getProxyPort();
        if (host.isEmpty()) {
            Toast.makeText(this, "Set proxy host first", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Testing...", Toast.LENGTH_SHORT).show();
        mAdBlockerUtils.testProxy(host, port, new AdBlockerUtils.ProxyTestCallback() {
            @Override
            public void onProxyTested(String h, int p, long l) {
                runOnUiThread(() -> new AlertDialog.Builder(AdBlockerActivity.this)
                    .setTitle("Success")
                    .setMessage("Proxy working! Latency: " + l + "ms")
                    .setPositiveButton("OK", null)
                    .show());
            }
            @Override
            public void onError(String e) {
                runOnUiThread(() -> new AlertDialog.Builder(AdBlockerActivity.this)
                    .setTitle("Failed")
                    .setMessage("Error: " + e)
                    .setPositiveButton("OK", null)
                    .show());
            }
        });
    }
    
    private void showWhitelistDialog() { showFilterDialog(mAdBlockerUtils.getWhitelist().toArray(new String[0]), true); }
    private void showBlacklistDialog() { showFilterDialog(mAdBlockerUtils.getBlacklist().toArray(new String[0]), false); }
    
    private void showFilterDialog(String[] items, boolean isWhitelist) {
        new AlertDialog.Builder(this)
            .setTitle(isWhitelist ? "Whitelist" : "Blacklist")
            .setItems(items, (d, w) -> {
                if (isWhitelist) mAdBlockerUtils.removeFromWhitelist(items[w]);
                else mAdBlockerUtils.removeFromBlacklist(items[w]);
                updateUI();
            })
            .setPositiveButton("Add", (d, w) -> showAddDomainDialog(isWhitelist))
            .setNegativeButton("Close", null)
            .show();
    }
    
    private void showAddDomainDialog(boolean isWhitelist) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
            .setTitle("Add Domain")
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                String domain = input.getText().toString().trim();
                if (!domain.isEmpty()) {
                    if (isWhitelist) mAdBlockerUtils.addToWhitelist(domain);
                    else mAdBlockerUtils.addToBlacklist(domain);
                    updateUI();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showAppFilterDialog() {
        new AlertDialog.Builder(this).setTitle("Coming Soon").setMessage("App filtering will be available in next update.").setPositiveButton("OK", null).show();
    }
    
    private void resetStatistics() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Stats")
            .setMessage("Clear all statistics?")
            .setPositiveButton("Yes", (d, w) -> {
                mAdBlockerUtils.resetStatistics();
                updateUI();
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void showInfoDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Info")
            .setMessage("AdBlocker Active\nBlocked: " + mAdBlockerUtils.getBlockedDomainsCount() + "\nDNS: " + mAdBlockerUtils.getCurrentDNSMode())
            .setPositiveButton("OK", null)
            .show();
    }

    private void showMethodDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Method")
            .setMessage("DNS-based blocking. Root access enables iptables optimization.")
            .setPositiveButton("OK", null)
            .show();
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
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
