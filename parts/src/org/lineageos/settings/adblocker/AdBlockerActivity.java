package org.lineageos.settings.adblocker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
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

    private SwitchPreference mAdBlockerEnabled;
    private Preference mAdBlockerStatus;
    private Preference mLastUpdate;
    private Preference mUpdate;
    private Preference mManualUpdate;
    private Preference mInfo;
    private Preference mMethod;
    private Preference mGitHub;

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
            mAdBlockerStatus.setSummary(statusText + " (DNS-based)");
        }

        if (mLastUpdate != null) {
            mLastUpdate.setSummary(lastUpdate);
        }

        if (mInfo != null) {
            if (blockedCount > 0) {
                mInfo.setSummary(getString(R.string.adblocker_blocked_domains, blockedCount));
            } else {
                mInfo.setSummary("No hosts file loaded - Tap to update!");
            }
        }

        if (mMethod != null) {
            boolean hasRoot = mAdBlockerUtils.hasRootAccess();
            String methodText = hasRoot ? "DNS + Root optimization" : "DNS-based blocking";
            mMethod.setSummary(methodText);
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
        }

        return false;
    }

    private void handleAdBlockerToggle(boolean enable) {
        Log.d(TAG, "handleAdBlockerToggle(" + enable + ")");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_confirm_title);

        String message = enable ?
            getString(R.string.adblocker_confirm_enable) :
            getString(R.string.adblocker_confirm_disable);
        builder.setMessage(message);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            if (enable) {
                enableAdBlocker();
            } else {
                disableAdBlocker();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void enableAdBlocker() {
        Log.d(TAG, "enableAdBlocker() called");

        if (mAdBlockerUtils.enableAdBlocker()) {
            if (mAdBlockerUtils.getBlockedDomainsCount() == 0) {
                Toast.makeText(this, "AdBlocker enabled, downloading hosts file...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "No hosts file found, triggering automatic update");
                performUpdate();
            } else {
                Toast.makeText(this, R.string.adblocker_enabled, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "AdBlocker enabled with existing hosts file");
                updateUI();
            }
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

        if (!mAdBlockerUtils.isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection!", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No network connection available for update");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_confirm_title);
        builder.setMessage(R.string.adblocker_confirm_update);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> performUpdate());
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void performUpdate() {
        Log.d(TAG, "performUpdate() called");

        mAdBlockerUtils.updateHostsFile(new AdBlockerUtils.UpdateCallback() {
            @Override
            public void onUpdateStart() {
                Log.d(TAG, "Update started");
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this,
                        "Starting hosts file download...", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onUpdateSuccess(int blockedCount) {
                Log.d(TAG, "Update successful, blocked count: " + blockedCount);
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this,
                        "Update successful! " + blockedCount + " domains blocked.", Toast.LENGTH_LONG).show();
                    updateUI();
                });
            }

            @Override
            public void onUpdateError(String error) {
                Log.e(TAG, "Update error: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this,
                        "Update error: " + error, Toast.LENGTH_LONG).show();
                    showDebugDialog("Update Error Details", error);
                });
            }
        });
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
                            "Manual update successful! " + blockedCount + " domains.", Toast.LENGTH_LONG).show();
                        updateUI();
                    });
                }

                @Override
                public void onUpdateError(String error) {
                    Log.e(TAG, "Manual update error: " + error);
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this,
                            "Manual update error: " + error, Toast.LENGTH_LONG).show();
                        showDebugDialog("Manual Update Error", error);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load hosts file", e);
            Toast.makeText(this, "File read error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        boolean hasNetwork = mAdBlockerUtils.isNetworkAvailable();

        StringBuilder info = new StringBuilder();
        info.append("Status: ").append(isEnabled ? "Enabled" : "Disabled").append("\n\n");
        info.append("Method: DNS-based blocking").append("\n\n");
        info.append("Blocked domains: ").append(blockedCount).append("\n\n");
        info.append("Last update: ").append(lastUpdate).append("\n\n");
        info.append("Source: StevenBlack/hosts").append("\n");
        info.append("GitHub repository with updated hosts file").append("\n\n");
        info.append("Root access: ").append(hasRoot ? "Available" : "Not available").append("\n");
        info.append("Internet connection: ").append(hasNetwork ? "Available" : "Not available").append("\n\n");

        if (isEnabled) {
            info.append("DNS server: AdGuard DNS (ad-blocking)").append("\n");
            info.append("Primary: 94.140.14.14").append("\n");
            info.append("Secondary: 94.140.15.15");
        } else {
            info.append("DNS server: Cloudflare (neutral)").append("\n");
            info.append("Primary: 1.1.1.1").append("\n");
            info.append("Secondary: 1.0.0.1");
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
        methodInfo.append("DNS-based ad blocking:\n\n");
        methodInfo.append("✓ No system partition write required\n");
        methodInfo.append("✓ Compatible with Android 16\n");
        methodInfo.append("✓ Affects all applications\n");
        methodInfo.append("✓ Low resource usage\n\n");

        methodInfo.append("Operation:\n");
        methodInfo.append("• Uses AdGuard DNS servers\n");
        methodInfo.append("• Blocks known ad domains\n");
        methodInfo.append("• Automatic filtering at DNS level\n\n");

        if (mAdBlockerUtils.hasRootAccess()) {
            methodInfo.append("Root optimization available:\n");
            methodInfo.append("• iptables rules\n");
            methodInfo.append("• Enhanced blocking");
        } else {
            methodInfo.append("Root not available:\n");
            methodInfo.append("• DNS-based blocking only\n");
            methodInfo.append("• Still effective");
        }

        builder.setMessage(methodInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
