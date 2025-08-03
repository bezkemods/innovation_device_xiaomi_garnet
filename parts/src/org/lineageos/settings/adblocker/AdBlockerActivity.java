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
            mAdBlockerStatus.setSummary(statusText + " (DNS alapú)");
        }

        if (mLastUpdate != null) {
            mLastUpdate.setSummary(lastUpdate);
        }

        if (mInfo != null) {
            if (blockedCount > 0) {
                mInfo.setSummary(getString(R.string.adblocker_blocked_domains, blockedCount));
            } else {
                mInfo.setSummary("Nincs betöltött hosts fájl - Kattints a frissítéshez!");
            }
        }

        if (mMethod != null) {
            boolean hasRoot = mAdBlockerUtils.hasRootAccess();
            String methodText = hasRoot ? "DNS + Root optimalizálás" : "DNS alapú blokkolás";
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
            return false; // Don't update immediately, wait for confirmation
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
            // If we have never updated, trigger an automatic update
            if (mAdBlockerUtils.getBlockedDomainsCount() == 0) {
                Toast.makeText(this, "AdBlocker engedélyezve, hosts fájl letöltése...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "No hosts file found, triggering automatic update");
                performUpdate();
            } else {
                Toast.makeText(this, R.string.adblocker_enabled, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "AdBlocker enabled with existing hosts file");
                updateUI();
            }
        } else {
            Toast.makeText(this, "AdBlocker engedélyezése sikertelen!", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "AdBlocker letiltása sikertelen!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to disable AdBlocker");
        }
    }

    private void handleUpdate() {
        Log.d(TAG, "handleUpdate() called");
        
        // Ellenőrizzük az internet kapcsolatot
        if (!mAdBlockerUtils.isNetworkAvailable()) {
            Toast.makeText(this, "Nincs internet kapcsolat!", Toast.LENGTH_SHORT).show();
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
                        "Hosts fájl letöltése elkezdődött...", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onUpdateSuccess(int blockedCount) {
                Log.d(TAG, "Update successful, blocked count: " + blockedCount);
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        "Sikeres frissítés! " + blockedCount + " domain blokkolva.", Toast.LENGTH_LONG).show();
                    updateUI();
                });
            }

            @Override
            public void onUpdateError(String error) {
                Log.e(TAG, "Update error: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(AdBlockerActivity.this, 
                        "Frissítési hiba: " + error, 
                        Toast.LENGTH_LONG).show();
                    
                    // Show debug dialog
                    showDebugDialog("Frissítési hiba részletei", error);
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
            Toast.makeText(this, "Kérlek telepíts egy fájlkezelőt!", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "A fájl üres vagy nem olvasható!", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Empty hosts file selected");
                return;
            }

            // Apply the loaded hosts file
            mAdBlockerUtils.updateHostsFileFromContent(hostsContent, new AdBlockerUtils.UpdateCallback() {
                @Override
                public void onUpdateStart() {
                    Log.d(TAG, "Manual update started");
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            "Hosts fájl feldolgozása...", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onUpdateSuccess(int blockedCount) {
                    Log.d(TAG, "Manual update successful, blocked count: " + blockedCount);
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            "Manuális frissítés sikeres! " + blockedCount + " domain.", Toast.LENGTH_LONG).show();
                        updateUI();
                    });
                }

                @Override
                public void onUpdateError(String error) {
                    Log.e(TAG, "Manual update error: " + error);
                    runOnUiThread(() -> {
                        Toast.makeText(AdBlockerActivity.this, 
                            "Manuális frissítési hiba: " + error, 
                            Toast.LENGTH_LONG).show();
                        
                        // Show debug dialog
                        showDebugDialog("Manuális frissítési hiba", error);
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load hosts file", e);
            Toast.makeText(this, "Fájl olvasási hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGitHubPage() {
        Log.d(TAG, "openGitHubPage() called");
        
        String githubUrl = "https://github.com/StevenBlack/hosts";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Nem sikerült megnyitni a GitHub oldalt", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to open GitHub page", e);
        }
    }

    private void showDebugDialog(String title, String error) {
        String debugLog = mAdBlockerUtils.getDebugLog();
        String fullMessage = "Hiba: " + error + "\n\n" + 
                            "Debug információk:\n" + debugLog;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(fullMessage);
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Debug törlése", (dialog, which) -> {
            mAdBlockerUtils.clearDebugLog();
            Toast.makeText(this, "Debug log törölve", Toast.LENGTH_SHORT).show();
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
        info.append("Állapot: ").append(isEnabled ? "Engedélyezve" : "Letiltva").append("\n\n");
        info.append("Módszer: DNS alapú blokkolás").append("\n\n");
        info.append("Blokkolt domainek: ").append(blockedCount).append("\n\n");
        info.append("Utolsó frissítés: ").append(lastUpdate).append("\n\n");
        info.append("Forrás: StevenBlack/hosts").append("\n");
        info.append("GitHub repository frissített hosts fájllal").append("\n\n");
        info.append("Root hozzáférés: ").append(hasRoot ? "Elérhető" : "Nem elérhető").append("\n");
        info.append("Internet kapcsolat: ").append(hasNetwork ? "Elérhető" : "Nem elérhető").append("\n\n");
        
        if (isEnabled) {
            info.append("DNS szerver: AdGuard DNS (ad-blocking)").append("\n");
            info.append("Elsődleges: 94.140.14.14").append("\n");
            info.append("Másodlagos: 94.140.15.15");
        } else {
            info.append("DNS szerver: Cloudflare (semleges)").append("\n");
            info.append("Elsődleges: 1.1.1.1").append("\n");
            info.append("Másodlagos: 1.0.0.1");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AdBlocker Információ");
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
        builder.setTitle("AdBlocker Módszer");
        
        StringBuilder methodInfo = new StringBuilder();
        methodInfo.append("DNS alapú reklámblokkolás:\n\n");
        methodInfo.append("✓ Nincs szükség rendszerpartíció írásra\n");
        methodInfo.append("✓ Kompatibilis Android 16-tal\n");
        methodInfo.append("✓ Minden alkalmazást érint\n");
        methodInfo.append("✓ Alacsony erőforrásigény\n\n");
        
        methodInfo.append("Működés:\n");
        methodInfo.append("• AdGuard DNS szervereket használ\n");
        methodInfo.append("• Ismert reklámdomain-eket blokkolja\n");
        methodInfo.append("• Automatikus szűrés DNS szinten\n\n");
        
        if (mAdBlockerUtils.hasRootAccess()) {
            methodInfo.append("Root optimalizálás elérhető:\n");
            methodInfo.append("• iptables szabályok\n");
            methodInfo.append("• Fokozott blokkolás");
        } else {
            methodInfo.append("Root nem elérhető:\n");
            methodInfo.append("• DNS-based blocking only\n");
            methodInfo.append("• Még mindig hatékony");
        }

        builder.setMessage(methodInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
