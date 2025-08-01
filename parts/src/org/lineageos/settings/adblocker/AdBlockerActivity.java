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

    private SwitchPreference mAdBlockerEnabled;
    private Preference mAdBlockerStatus;
    private Preference mLastUpdate;
    private Preference mUpdate;
    private Preference mManualUpdate;
    private Preference mInfo;

    private SystemlessAdBlockerUtils mSystemlessAdBlockerUtils;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.adblocker_settings);

        mSystemlessAdBlockerUtils = new SystemlessAdBlockerUtils(this);
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
    }

    private void updateUI() {
        boolean isEnabled = mSystemlessAdBlockerUtils.isEnabled();
        int blockedCount = mSystemlessAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mSystemlessAdBlockerUtils.getLastUpdateTime();

        if (mAdBlockerEnabled != null) {
            mAdBlockerEnabled.setChecked(isEnabled);
        }

        if (mAdBlockerStatus != null) {
            mAdBlockerStatus.setSummary(isEnabled ? 
                getString(R.string.adblocker_status_enabled) : 
                getString(R.string.adblocker_status_disabled));
        }

        if (mLastUpdate != null) {
            mLastUpdate.setSummary(lastUpdate);
        }

        if (mInfo != null) {
            mInfo.setSummary(getString(R.string.adblocker_blocked_domains, blockedCount));
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
        }

        return false;
    }

    private void handleAdBlockerToggle(boolean enable) {
        if (!mSystemlessAdBlockerUtils.hasRootAccess()) {
            Toast.makeText(this, R.string.adblocker_root_required, Toast.LENGTH_LONG).show();
            return;
        }

        if (!mSystemlessAdBlockerUtils.isSystemMounted()) {
            Toast.makeText(this, "System partition is not writable", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.adblocker_confirm_title);
        builder.setMessage(enable ? 
            R.string.adblocker_confirm_enable : 
            R.string.adblocker_confirm_disable);

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
        if (mSystemlessAdBlockerUtils.enableAdBlocker()) {
            // If we have never updated, trigger an automatic update
            if (mSystemlessAdBlockerUtils.getBlockedDomainsCount() == 0) {
                performUpdate();
            } else {
                Toast.makeText(this, R.string.adblocker_enabled, Toast.LENGTH_SHORT).show();
                updateUI();
            }
        } else {
            Toast.makeText(this, "Failed to enable AdBlocker. Check root access.", Toast.LENGTH_LONG).show();
        }
    }

    private void disableAdBlocker() {
        if (mSystemlessAdBlockerUtils.disableAdBlocker()) {
            Toast.makeText(this, R.string.adblocker_disabled, Toast.LENGTH_SHORT).show();
            updateUI();
        } else {
            Toast.makeText(this, "Failed to disable AdBlocker. Check root access.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleUpdate() {
        if (!mSystemlessAdBlockerUtils.hasRootAccess()) {
            Toast.makeText(this, R.string.adblocker_root_required, Toast.LENGTH_LONG).show();
            return;
        }

        if (!mSystemlessAdBlockerUtils.isSystemMounted()) {
            Toast.makeText(this, "System partition is not writable", Toast.LENGTH_LONG).show();
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
        mSystemlessAdBlockerUtils.updateHostsFile(new SystemlessAdBlockerUtils.UpdateCallback() {
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
                        R.string.adblocker_update_success, Toast.LENGTH_SHORT).show();
                    updateUI();
                });
            }

            @Override
            public void onUpdateError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        "Update failed: " + error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Update error: " + error);
                });
            }
        });
    }

    private void handleManualUpdate() {
        if (!mSystemlessAdBlockerUtils.hasRootAccess()) {
            Toast.makeText(this, R.string.adblocker_root_required, Toast.LENGTH_LONG).show();
            return;
        }

        if (!mSystemlessAdBlockerUtils.isSystemMounted()) {
            Toast.makeText(this, "System partition is not writable", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.adblocker_manual_file_title)), 
                REQUEST_PICK_FILE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Cannot open selected file", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Selected file is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate hosts file content
            if (!mSystemlessAdBlockerUtils.isValidHostsFile(hostsContent)) {
                Toast.makeText(this, "Invalid hosts file format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Apply the loaded hosts file
            mSystemlessAdBlockerUtils.updateHostsFileFromContent(hostsContent, new SystemlessAdBlockerUtils.UpdateCallback() {
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
                            "Manual update successful. " + blockedCount + " domains blocked.", 
                            Toast.LENGTH_SHORT).show();
                        updateUI();
                    });
                }

                @Override
                public void onUpdateError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            "Manual update failed: " + error, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Manual update error: " + error);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load hosts file", e);
            Toast.makeText(this, "Failed to read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showInfoDialog() {
        int blockedCount = mSystemlessAdBlockerUtils.getBlockedDomainsCount();
        String lastUpdate = mSystemlessAdBlockerUtils.getLastUpdateTime();
        boolean isEnabled = mSystemlessAdBlockerUtils.isEnabled();
        boolean hasRoot = mSystemlessAdBlockerUtils.hasRootAccess();
        boolean systemMounted = mSystemlessAdBlockerUtils.isSystemMounted();

        StringBuilder info = new StringBuilder();
        info.append("Status: ").append(isEnabled ? "Enabled" : "Disabled").append("\n\n");
        info.append("Blocked domains: ").append(blockedCount).append("\n\n");
        info.append("Last update: ").append(lastUpdate).append("\n\n");
        info.append("Source: StevenBlack/hosts").append("\n");
        info.append("GitHub repository with regularly updated hosts file").append("\n\n");
        info.append("Root access: ").append(hasRoot ? "Available" : "Not available").append("\n");
        info.append("System writable: ").append(systemMounted ? "Yes" : "No");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AdBlocker Information");
        builder.setMessage(info.toString());
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
