package org.lineageos.settings.adblocker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    private Preference mGithub;

    private AdBlockerUtils mAdBlockerUtils;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set title for the activity
        if (getActionBar() != null) {
            getActionBar().setTitle(R.string.adblocker_title);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        addPreferencesFromResource(R.xml.adblocker_settings);

        mAdBlockerUtils = new AdBlockerUtils(this);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        initializePreferences();
        
        // Initialize with built-in hosts if never updated
        if (mAdBlockerUtils.getBlockedDomainsCount() == 0) {
            mAdBlockerUtils.initializeBuiltInHosts();
        }
        
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
        mGithub = findPreference(KEY_ADBLOCKER_GITHUB);

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

        if (mGithub != null) {
            mGithub.setOnPreferenceClickListener(this);
        }
    }

    private void updateUI() {
        boolean isEnabled = mAdBlockerUtils.isEnabled();
        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mAdBlockerUtils.getLastUpdateTime();

        if (mAdBlockerEnabled != null) {
            mAdBlockerEnabled.setChecked(isEnabled);
        }

        if (mAdBlockerStatus != null) {
            String statusText = isEnabled ? 
                getString(R.string.adblocker_status_enabled) : 
                getString(R.string.adblocker_status_disabled);
            mAdBlockerStatus.setSummary(statusText);
        }

        if (mLastUpdate != null) {
            mLastUpdate.setSummary(lastUpdate);
        }

        if (mInfo != null) {
            mInfo.setSummary(getString(R.string.adblocker_blocked_domains, blockedCount));
        }

        if (mMethod != null) {
            boolean hasRoot = mAdBlockerUtils.hasRootAccess();
            String methodText = hasRoot ? 
                getString(R.string.adblocker_method_dns_root) : 
                getString(R.string.adblocker_method_dns_only);
            mMethod.setSummary(methodText);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_ADBLOCKER_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            handleAdBlockerToggle(enabled);
            return false; // Don't update immediately, wait for confirmation
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
                openGithubLink();
                return true;
        }

        return false;
    }

    private void handleAdBlockerToggle(boolean enable) {
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
        if (mAdBlockerUtils.enableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_enabled_toast, Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, R.string.adblocker_enable_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void disableAdBlocker() {
        if (mAdBlockerUtils.disableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_disabled_toast, Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, R.string.adblocker_disable_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void handleUpdate() {
        // Check network first
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Toast.makeText(this, R.string.adblocker_no_connectivity_manager, Toast.LENGTH_LONG).show();
            return;
        }

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        
        if (!isConnected) {
            Toast.makeText(this, R.string.adblocker_no_internet, Toast.LENGTH_LONG).show();
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
        mAdBlockerUtils.updateHostsFile(new AdBlockerUtils.UpdateCallback() {
            @Override
            public void onUpdateStart() {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        R.string.adblocker_updating, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onUpdateSuccess(int blockedCount) {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        getString(R.string.adblocker_update_success, blockedCount), 
                        Toast.LENGTH_SHORT).show();
                    updateUI();
                });
            }

            @Override
            public void onUpdateError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        getString(R.string.adblocker_update_failed_with_error, error), 
                        Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Update error: " + error);
                });
            }
        });
    }

    private void handleManualUpdate() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.adblocker_select_hosts_file)), 
                REQUEST_PICK_FILE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.adblocker_no_file_manager, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    loadHostsFileFromUri(uri);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadHostsFileFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, R.string.adblocker_file_read_error, Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            reader.close();
            inputStream.close();

            String hostsContent = content.toString();
            if (hostsContent.trim().isEmpty()) {
                Toast.makeText(this, R.string.adblocker_file_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            // Apply the loaded hosts file
            mAdBlockerUtils.updateHostsFileFromContent(hostsContent, new AdBlockerUtils.UpdateCallback() {
                @Override
                public void onUpdateStart() {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            R.string.adblocker_updating, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onUpdateSuccess(int blockedCount) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            getString(R.string.adblocker_manual_update_success, blockedCount), 
                            Toast.LENGTH_SHORT).show();
                        updateUI();
                    });
                }

                @Override
                public void onUpdateError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            getString(R.string.adblocker_update_failed_with_error, error), 
                            Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Manual update error: " + error);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load hosts file", e);
            Toast.makeText(this, R.string.adblocker_file_read_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void openGithubLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/StevenBlack/hosts"));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open GitHub link", e);
            Toast.makeText(this, R.string.adblocker_link_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showInfoDialog() {
        int blockedCount = mAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mAdBlockerUtils.getLastUpdateTime();
        boolean isEnabled = mAdBlockerUtils.isEnabled();
        boolean hasRoot = mAdBlockerUtils.hasRootAccess();

        StringBuilder info = new StringBuilder();
        info.append(getString(R.string.adblocker_info_status))
            .append(": ")
            .append(isEnabled ? getString(R.string.adblocker_status_enabled) : getString(R.string.adblocker_status_disabled))
            .append("\n\n");
        
        info.append(getString(R.string.adblocker_info_method))
            .append(": ")
            .append(getString(R.string.adblocker_info_dns_method))
            .append("\n\n");
        
        info.append(getString(R.string.adblocker_info_blocked_count))
            .append(": ")
            .append(blockedCount)
            .append("\n\n");
        
        info.append(getString(R.string.adblocker_info_last_update))
            .append(": ")
            .append(lastUpdate)
            .append("\n\n");
        
        info.append(getString(R.string.adblocker_info_source))
            .append(": StevenBlack/hosts\n")
            .append(getString(R.string.adblocker_info_source_desc))
            .append("\n\n");
        
        info.append(getString(R.string.adblocker_info_root_access))
            .append(": ")
            .append(hasRoot ? getString(R.string.adblocker_info_root_available) : getString(R.string.adblocker_info_root_unavailable))
            .append("\n\n");
        
        if (isEnabled) {
            info.append(getString(R.string.adblocker_info_dns_server))
                .append(": AdGuard DNS\n")
                .append(getString(R.string.adblocker_info_primary_dns))
                .append(": 94.140.14.14\n")
                .append(getString(R.string.adblocker_info_secondary_dns))
                .append(": 94.140.15.15");
        } else {
            info.append(getString(R.string.adblocker_info_dns_server))
                .append(": Cloudflare DNS\n")
                .append(getString(R.string.adblocker_info_primary_dns))
                .append(": 1.1.1.1\n")
                .append(getString(R.string.adblocker_info_secondary_dns))
                .append(": 1.0.0.1");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_info_dialog_title);
        builder.setMessage(info.toString());
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private void showMethodDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_method_dialog_title);
        
        StringBuilder methodInfo = new StringBuilder();
        methodInfo.append(getString(R.string.adblocker_method_dns_title))
                  .append(":\n\n");
        
        methodInfo.append("✓ ").append(getString(R.string.adblocker_method_no_system_writes)).append("\n");
        methodInfo.append("✓ ").append(getString(R.string.adblocker_method_android_compatible)).append("\n");
        methodInfo.append("✓ ").append(getString(R.string.adblocker_method_all_apps)).append("\n");
        methodInfo.append("✓ ").append(getString(R.string.adblocker_method_low_resource)).append("\n\n");
        
        methodInfo.append(getString(R.string.adblocker_method_how_works))
                  .append(":\n");
        methodInfo.append("• ").append(getString(R.string.adblocker_method_uses_adguard)).append("\n");
        methodInfo.append("• ").append(getString(R.string.adblocker_method_blocks_domains)).append("\n");
        methodInfo.append("• ").append(getString(R.string.adblocker_method_dns_filtering)).append("\n\n");
        
        if (mAdBlockerUtils.hasRootAccess()) {
            methodInfo.append(getString(R.string.adblocker_method_root_available))
                      .append(":\n");
            methodInfo.append("• ").append(getString(R.string.adblocker_method_iptables)).append("\n");
            methodInfo.append("• ").append(getString(R.string.adblocker_method_enhanced_blocking));
        } else {
            methodInfo.append(getString(R.string.adblocker_method_root_unavailable))
                      .append(":\n");
            methodInfo.append("• ").append(getString(R.string.adblocker_method_dns_only)).append("\n");
            methodInfo.append("• ").append(getString(R.string.adblocker_method_still_effective));
        }

        builder.setMessage(methodInfo.toString());
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Handle back navigation to parent activity
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
