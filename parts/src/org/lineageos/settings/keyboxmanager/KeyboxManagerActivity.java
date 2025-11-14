package org.lineageos.settings.keyboxmanager;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;
import org.lineageos.settings.R;

public class KeyboxManagerActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private static final String TAG = "KeyboxManagerActivity";
    private static final int REQUEST_IMPORT_FILE = 1001;

    // Preference keys
    private static final String KEY_KEYBOX_STATUS = "keybox_status";
    private static final String KEY_KEYBOX_DEVICE_ID = "keybox_device_id";
    private static final String KEY_KEYBOX_CERT_COUNT = "keybox_cert_count";
    private static final String KEY_KEYBOX_GENERATE = "keybox_generate";
    private static final String KEY_KEYBOX_SEARCH = "keybox_search";
    private static final String KEY_KEYBOX_IMPORT = "keybox_import";
    private static final String KEY_KEYBOX_EXPORT = "keybox_export"; // This now lists downloaded files
    private static final String KEY_KEYBOX_VERIFY = "keybox_verify"; // This now verifies downloaded files
    private static final String KEY_KEYBOX_RESET = "keybox_reset";   // This now deletes downloaded files
    private static final String KEY_KEYBOX_INFO = "keybox_info";

    private Preference mStatusPref;
    private Preference mDeviceIdPref;
    private Preference mCertCountPref;
    private Preference mGeneratePref;
    private Preference mSearchPref;
    private Preference mImportPref;
    private Preference mExportPref;
    private Preference mVerifyPref;
    private Preference mResetPref;
    private Preference mInfoPref;

    private KeyboxManagerUtils mKeyboxUtils;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.keybox_manager_settings);

        mKeyboxUtils = new KeyboxManagerUtils(this);
        initializePreferences();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void initializePreferences() {
        mStatusPref = findPreference(KEY_KEYBOX_STATUS);
        mDeviceIdPref = findPreference(KEY_KEYBOX_DEVICE_ID);
        mCertCountPref = findPreference(KEY_KEYBOX_CERT_COUNT);
        mGeneratePref = findPreference(KEY_KEYBOX_GENERATE);
        mSearchPref = findPreference(KEY_KEYBOX_SEARCH);
        mImportPref = findPreference(KEY_KEYBOX_IMPORT);
        mExportPref = findPreference(KEY_KEYBOX_EXPORT);
        mVerifyPref = findPreference(KEY_KEYBOX_VERIFY);
        mResetPref = findPreference(KEY_KEYBOX_RESET);
        mInfoPref = findPreference(KEY_KEYBOX_INFO);

        // Click listeners
        if (mGeneratePref != null) mGeneratePref.setOnPreferenceClickListener(this);
        if (mSearchPref != null) mSearchPref.setOnPreferenceClickListener(this);
        if (mImportPref != null) mImportPref.setOnPreferenceClickListener(this);
        if (mExportPref != null) mExportPref.setOnPreferenceClickListener(this);
        if (mVerifyPref != null) mVerifyPref.setOnPreferenceClickListener(this);
        if (mResetPref != null) mResetPref.setOnPreferenceClickListener(this);
        if (mInfoPref != null) mInfoPref.setOnPreferenceClickListener(this);
    }

    private void updateUI() {
        try {
            // Status now refers to downloaded/validated files
            KeyboxManagerUtils.KeyboxInfo info = mKeyboxUtils.getCurrentKeyboxInfo();
            
            if (info != null && info.isInstalled) {
                // There is at least one valid downloaded keybox
                mStatusPref.setSummary(getString(R.string.keybox_status_summary, "✅ Valid file downloaded"));
                // Use a built-in icon that is guaranteed to exist
                mStatusPref.setIcon(android.R.drawable.ic_menu_save); 
                
                mDeviceIdPref.setSummary(getString(R.string.keybox_device_id_summary, 
                    info.deviceId != null ? info.deviceId : "Unknown"));
                
                mCertCountPref.setSummary(getString(R.string.keybox_cert_count_summary, 
                    info.rsaCertCount, info.ecdsaCertCount));
                
            } else {
                // No downloaded keyboxes or all are invalid/templates
                mStatusPref.setSummary(getString(R.string.keybox_status_summary, getString(R.string.keybox_status_not_installed)));
                mStatusPref.setIcon(android.R.drawable.ic_dialog_alert);
                
                mDeviceIdPref.setSummary(getString(R.string.keybox_device_id_summary, "N/A"));
                mCertCountPref.setSummary(getString(R.string.keybox_cert_count_summary, 0, 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (KEY_KEYBOX_GENERATE.equals(key)) {
            showGenerateDialog();
            return true;
        } else if (KEY_KEYBOX_SEARCH.equals(key)) {
            showSearchDialog();
            return true;
        } else if (KEY_KEYBOX_IMPORT.equals(key)) {
            showImportDialog();
            return true;
        } else if (KEY_KEYBOX_EXPORT.equals(key)) {
            showExportDialog(); // List files
            return true;
        } else if (KEY_KEYBOX_VERIFY.equals(key)) {
            showVerifyDialog(); // Verify files
            return true;
        } else if (KEY_KEYBOX_RESET.equals(key)) {
            showResetDialog(); // Delete files
            return true;
        } else if (KEY_KEYBOX_INFO.equals(key)) {
            showInfoDialog();
            return true;
        }
        return false;
    }

    // ==================== GENERATE (TEMPLATES) ====================
    
    private void showGenerateDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_generate_dialog_title) // "Generate Templates"
                .setMessage("This will create keybox structure templates:\n\n" +
                    "1️⃣ Play Integrity Fix format (Base64)\n" +
                    "2️⃣ TrickyStore format (PEM)\n\n" +
                    "⚠️ These are ONLY STRUCTURAL TEMPLATES!\n" +
                    "They do NOT contain valid, Google-signed certificates.\n\n" +
                    "💡 For working keyboxes:\n" +
                    "• Use 'Search Keybox' to download from community\n" +
                    "• Or 'Import Keybox' if you have one.\n\n" +
                    "Generate templates for reference?")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    generateTemplates();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void generateTemplates() {
        new GenerateTemplateTask().execute();
    }

    private class GenerateTemplateTask extends AsyncTask<Void, Void, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setMessage("Creating templates..."); // getString(R.string.keybox_generating)
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.generateKeybox();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (result.success) {
                showSuccessDialog(result.message);
                updateUI();
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== SEARCH ====================
    
    private void showSearchDialog() {
        if (!mKeyboxUtils.isNetworkAvailable()) {
            showToast(getString(R.string.keybox_no_internet));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_search_dialog_title) // "Search Community Keyboxes"
                .setMessage("Search for working keyboxes from community sources.\n\n" +
                    "📥 What happens:\n" +
                    "• Tries to reach multiple community sources\n" +
                    "• Downloads valid keyboxes only\n" +
                    "• Saves to: /sdcard/Download/Keyboxes/\n" +
                    "• Automatically removes duplicates\n\n" +
                    "⏱️ Time: 1-3 minutes\n" +
                    "🌐 Internet required\n\n" +
                    "⚠️ NOTE:\n" +
                    "Public sources may be limited. If no keyboxes found:\n" +
                    "• Check DroidWin.com comments\n" +
                    "• Join Telegram: @PlayIntegrityFix\n" +
                    "• Visit XDA Forums\n\n" +
                    "Downloaded files must be installed manually via Magisk/KernelSU.\n\n" +
                    "Continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    downloadKeyboxes();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void downloadKeyboxes() {
        new SearchKeyboxTask().execute();
    }

    private class SearchKeyboxTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        private final int maxAttempts = 10; // Max downloads at a time

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_searching);
            mProgressDialog.setMessage("Connecting to community sources...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(values[0]);
            }
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            try {
                publishProgress("Fetching source list...");
                Thread.sleep(500);
                
                publishProgress(getString(R.string.keybox_downloading));
                
                return mKeyboxUtils.searchAndDownloadKeyboxes(maxAttempts);
            } catch (Exception e) {
                Log.e(TAG, "Error in download task", e);
                return new KeyboxManagerUtils.OperationResult(false, "Task error: " + e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (result.success) {
                showSuccessDialog("✅ Download Complete!\n\n" + result.message);
                updateUI();
            } else {
                // Show info even on failure (e.g., "not found")
                showInfoDialog("ℹ️ Search Results", result.message);
            }
        }
    }

    // ==================== IMPORT ====================
    
    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_import_dialog_title) // "Import Keybox"
                .setMessage("Select a keybox.xml file to import.\n\n" +
                    "✅ Supported formats:\n" +
                    "• Play Integrity Fix (Base64 keys)\n" +
                    "• TrickyStore (PEM keys)\n\n" +
                    "📁 File will be copied to:\n/sdcard/Download/Keyboxes/\n\n" +
                    "⚠️ Make sure it's a valid keybox!\n\n" +
                    "Continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    openFilePicker();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/xml"); // Only XML files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select keybox.xml"), REQUEST_IMPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    importKeybox(uri);
                }
            }
        }
    }

    private void importKeybox(Uri uri) {
        new ImportKeyboxTask(uri).execute();
    }

    private class ImportKeyboxTask extends AsyncTask<Void, Void, KeyboxManagerUtils.OperationResult> {
        private final Uri uri;

        ImportKeyboxTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setMessage("Importing and validating...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.importKeybox(uri);
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (result.success) {
                showSuccessDialog(result.message);
                updateUI();
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== EXPORT (List Downloads) ====================
    
    private void showExportDialog() {
        // This function now lists downloaded files
        KeyboxManagerUtils.OperationResult result = mKeyboxUtils.exportKeybox();
        
        if (result.success) {
            showInfoDialog("📂 Downloaded Keyboxes", result.message);
        } else {
            showErrorDialog(result.message);
        }
    }

    // ==================== VERIFY (Downloads) ====================
    
    private void showVerifyDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_verify_dialog_title) // "Verify Keyboxes"
                .setMessage("This validates the XML structure of downloaded keybox files.\n\n" +
                    "✅ Checks:\n" +
                    "• Valid XML format\n" +
                    "• Device ID present\n" +
                    "• RSA + ECDSA keys exist\n" +
                    "• Certificate chains present\n" +
                    "• Detects format (PIF/TrickyStore)\n\n" +
                    "⚠️ LIMITATION:\n" +
                    "This does NOT test the Play Integrity API!\n\n" +
                    "To test actual integrity:\n" +
                    "1. Install the keybox in a Magisk module\n" +
                    "2. Use YASNAC or a banking app\n\n" +
                    "Continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    verifyKeybox();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void verifyKeybox() {
        new VerifyKeyboxTask().execute();
    }

    private class VerifyKeyboxTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_verifying);
            mProgressDialog.setMessage("Verifying keybox structures...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(values[0]);
            }
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            try {
                publishProgress("Parsing XML files...");
                Thread.sleep(500);
                
                publishProgress("Validating certificates...");
                Thread.sleep(500);
                
                return mKeyboxUtils.verifyKeybox();
            } catch (Exception e) {
                Log.e(TAG, "Error in verify task", e);
                return new KeyboxManagerUtils.OperationResult(false, "Task error: " + e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            if (result.success || result.basicIntegrity) { // Success if there's anything to show
                showInfoDialog("✅ Validation Results", result.message);
                updateUI();
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== RESET (Delete Downloads) ====================
    
    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_reset_dialog_title) // "Delete Downloads"
                .setMessage("This will delete ALL downloaded keybox files from:\n" +
                    "/sdcard/Download/Keyboxes/\n\n" +
                    "⚠️ This action cannot be undone!\n\n" +
                    "Template files will also be deleted.\n\n" +
                    "Continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    resetKeybox();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetKeybox() {
        KeyboxManagerUtils.OperationResult result = mKeyboxUtils.resetKeybox();
        
        if (result.success) {
            showSuccessDialog(result.message);
            updateUI(); // Update UI to "Not downloaded" status
        } else {
            showErrorDialog(result.message);
        }
    }

    // ==================== INFO DIALOG ====================
    
    private void showInfoDialog() {
        // This dialog uses the strings from your strings.xml
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_info_dialog_title)
                .setMessage(R.string.keybox_info_dialog_message) // From your strings.xml
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ==================== HELPER DIALOGS ====================

    private void showSuccessDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("✅ Success")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("❌ Error")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
