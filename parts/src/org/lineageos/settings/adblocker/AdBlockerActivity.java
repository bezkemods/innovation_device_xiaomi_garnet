package org.lineageos.settings.adblocker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
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
    private static final String KEY_PROXY_CATEGORY = "proxy_category";
    private static final String KEY_PROXY_ENABLED = "proxy_enabled";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";

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
    private SwitchPreference mProxyEnabled;
    private EditTextPreference mProxyHost;
    private EditTextPreference mProxyPort;

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
        mProxyEnabled = (SwitchPreference) findPreference(KEY_PROXY_ENABLED);
        mProxyHost = (EditTextPreference) findPreference(KEY_PROXY_HOST);
        mProxyPort = (EditTextPreference) findPreference(KEY_PROXY_PORT);

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
        
        if (mProxyEnabled != null) {
            mProxyEnabled.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyHost != null) {
            mProxyHost.setOnPreferenceChangeListener(this);
        }
        
        if (mProxyPort != null) {
            mProxyPort.setOnPreferenceChangeListener(this);
        }

        Log.d(TAG, "All preferences initialized");
    }

    private void updateUI() {
        Log.d(TAG, "updateUI() called");

        boolean isEnabled = mAdBlockerUtils.isEnabled();
        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mAdBlockerUtils.getLastUpdateTime();

        Log.d(TAG, "UI Update - Enabled: " + isEnabled + ", Blocked: " + blockedCount + ", LastUpdate: " + lastUpdate);

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

        Log.d(TAG, "updateUI() completed");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceChange() - Key: " + key + ", Value: " + newValue);

        if (KEY_ADBLOCKER_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            handleAdBlockerToggle(enabled);
            return false;
        } else if (KEY_PROXY_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            handleProxyToggle(enabled);
            return true;
        } else if (KEY_PROXY_HOST.equals(key)) {
            String host = (String) newValue;
            mAdBlockerUtils.setProxyHost(host);
            mProxyHost.setSummary(host.isEmpty() ? "Not set" : host);
            return true;
        } else if (KEY_PROXY_PORT.equals(key)) {
            try {
                int port = Integer.parseInt((String) newValue);
                mAdBlockerUtils.setProxyPort(port);
                mProxyPort.setSummary(String.valueOf(port));
                return true;
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
                return false;
            }
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

        StringBuilder info = new StringBuilder();
        info.append("Status: ").append(isEnabled ? "Enabled" : "Disabled").append("\n\n");
        info.append("Method: DNS-based blocking").append("\n\n");
        info.append("Blocked domains: ").append(blockedCount).append("\n\n");
        info.append("Last update: ").append(lastUpdate).append("\n\n");
        info.append("Source: StevenBlack/hosts\n");
        info.append("GitHub repository with updated hosts file\n\n");
        info.append("Root access: ").append(hasRoot ? "Available" : "Not available").append("\n\n");
        info.append("VPN: ").append(vpnConnected ? "Connected" : "Not connected").append("\n");
        info.append("Proxy: ").append(proxyEnabled ? "Enabled" : "Disabled").append("\n\n");

        if (isEnabled) {
            info.append("DNS: AdGuard DNS (ad-blocking)\n");
            info.append("Hostname: dns.adguard-dns.com");
        } else {
            info.append("DNS: Cloudflare (neutral)\n");
            info.append("Hostname: one.one.one.one");
        }

        info.append("\n\nHow to update hosts file:\n");
        info.append("1. Go to GitHub (tap 'Update list')\n");
        info.append("2. Download the raw hosts file\n");
        info.append("3. Load it using 'Manual update'");

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
        methodInfo.append("DNS-based ad blocking:\n\n");
        methodInfo.append("✓ No system partition write required\n");
        methodInfo.append("✓ Compatible with Android 16\n");
        methodInfo.append("✓ Affects all applications\n");
        methodInfo.append("✓ Low resource usage\n\n");

        methodInfo.append("Operation:\n");
        methodInfo.append("• Uses Private DNS (DNS-over-TLS)\n");
        methodInfo.append("• AdGuard DNS for ad blocking\n");
        methodInfo.append("• Manual hosts file loading\n");
        methodInfo.append("• Persistent after restart\n\n");

        if (mAdBlockerUtils.hasRootAccess()) {
            methodInfo.append("Root optimization available:\n");
            methodInfo.append("• iptables DNS redirect\n");
            methodInfo.append("• Global proxy support\n");
            methodInfo.append("• Enhanced blocking");
        } else {
            methodInfo.append("Root not available:\n");
            methodInfo.append("• DNS-based blocking only\n");
            methodInfo.append("• Still very effective");
        }

        methodInfo.append("\n\nTo load hosts file:\n");
        methodInfo.append("1. Tap 'Update list' → 'Download hosts file'\n");
        methodInfo.append("2. On GitHub, tap 'Download raw file'\n");
        methodInfo.append("3. Come back and tap 'Load hosts file'\n");
        methodInfo.append("4. Select the downloaded file");

        builder.setMessage(methodInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
