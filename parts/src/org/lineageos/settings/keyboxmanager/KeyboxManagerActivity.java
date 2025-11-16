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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeyboxManagerActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private static final String TAG = "KeyboxManagerActivity";
    private static final int REQUEST_IMPORT_FILE = 1001;

    // Preference keys
    private static final String KEY_KEYBOX_STATUS = "keybox_status";
    private static final String KEY_KEYBOX_DEVICE_ID = "keybox_device_id";
    private static final String KEY_KEYBOX_CERT_COUNT = "keybox_cert_count";
    private static final String KEY_KEYBOX_EXPIRY = "keybox_expiry";
    private static final String KEY_KEYBOX_GENERATE = "keybox_generate";
    private static final String KEY_KEYBOX_SEARCH = "keybox_search";
    private static final String KEY_KEYBOX_IMPORT = "keybox_import";
    private static final String KEY_KEYBOX_EXPORT = "keybox_export";
    private static final String KEY_KEYBOX_VERIFY = "keybox_verify";
    private static final String KEY_KEYBOX_RESET = "keybox_reset";
    private static final String KEY_KEYBOX_ANALYZE = "keybox_analyze";
    private static final String KEY_KEYBOX_CHECK_API = "keybox_check_api";
    private static final String KEY_KEYBOX_COMPARE = "keybox_compare";
    private static final String KEY_KEYBOX_EXTRACT_CERTS = "keybox_extract_certs";
    private static final String KEY_KEYBOX_BATCH_VERIFY = "keybox_batch_verify";
    private static final String KEY_KEYBOX_INFO = "keybox_info";

    private Preference mStatusPref;
    private Preference mDeviceIdPref;
    private Preference mCertCountPref;
    private Preference mExpiryPref;
    private Preference mGeneratePref;
    private Preference mSearchPref;
    private Preference mImportPref;
    private Preference mExportPref;
    private Preference mVerifyPref;
    private Preference mResetPref;
    private Preference mAnalyzePref;
    private Preference mCheckApiPref;
    private Preference mComparePref;
    private Preference mExtractCertsPref;
    private Preference mBatchVerifyPref;
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
        mExpiryPref = findPreference(KEY_KEYBOX_EXPIRY);
        mGeneratePref = findPreference(KEY_KEYBOX_GENERATE);
        mSearchPref = findPreference(KEY_KEYBOX_SEARCH);
        mImportPref = findPreference(KEY_KEYBOX_IMPORT);
        mExportPref = findPreference(KEY_KEYBOX_EXPORT);
        mVerifyPref = findPreference(KEY_KEYBOX_VERIFY);
        mResetPref = findPreference(KEY_KEYBOX_RESET);
        mAnalyzePref = findPreference(KEY_KEYBOX_ANALYZE);
        mCheckApiPref = findPreference(KEY_KEYBOX_CHECK_API);
        mComparePref = findPreference(KEY_KEYBOX_COMPARE);
        mExtractCertsPref = findPreference(KEY_KEYBOX_EXTRACT_CERTS);
        mBatchVerifyPref = findPreference(KEY_KEYBOX_BATCH_VERIFY);
        mInfoPref = findPreference(KEY_KEYBOX_INFO);

        // Click listeners
        if (mGeneratePref != null) mGeneratePref.setOnPreferenceClickListener(this);
        if (mSearchPref != null) mSearchPref.setOnPreferenceClickListener(this);
        if (mImportPref != null) mImportPref.setOnPreferenceClickListener(this);
        if (mExportPref != null) mExportPref.setOnPreferenceClickListener(this);
        if (mVerifyPref != null) mVerifyPref.setOnPreferenceClickListener(this);
        if (mResetPref != null) mResetPref.setOnPreferenceClickListener(this);
        if (mAnalyzePref != null) mAnalyzePref.setOnPreferenceClickListener(this);
        if (mCheckApiPref != null) mCheckApiPref.setOnPreferenceClickListener(this);
        if (mComparePref != null) mComparePref.setOnPreferenceClickListener(this);
        if (mExtractCertsPref != null) mExtractCertsPref.setOnPreferenceClickListener(this);
        if (mBatchVerifyPref != null) mBatchVerifyPref.setOnPreferenceClickListener(this);
        if (mInfoPref != null) mInfoPref.setOnPreferenceClickListener(this);
    }

    private void updateUI() {
        try {
            KeyboxManagerUtils.KeyboxInfo info = mKeyboxUtils.getCurrentKeyboxInfo();
            
            if (info != null && info.isInstalled) {
                mStatusPref.setSummary(getString(R.string.keybox_status_summary, "✅ Valid file downloaded"));
                mStatusPref.setIcon(android.R.drawable.ic_menu_save);
                
                mDeviceIdPref.setSummary(getString(R.string.keybox_device_id_summary, 
                    info.deviceId != null ? info.deviceId : "Unknown"));
                
                mCertCountPref.setSummary(getString(R.string.keybox_cert_count_summary, 
                    info.rsaCertCount, info.ecdsaCertCount));
                
                // Show expiry date
                if (info.expiryDate != null && info.expiryDate.getTime() > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    long daysUntilExpiry = (info.expiryDate.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                    String expiryText = sdf.format(info.expiryDate);
                    if (daysUntilExpiry < 0) {
                        expiryText += " (EXPIRED)";
                    } else if (daysUntilExpiry < 30) {
                        expiryText += " (⚠️ " + daysUntilExpiry + " days left)";
                    } else {
                        expiryText += " (" + daysUntilExpiry + " days left)";
                    }
                    mExpiryPref.setSummary(getString(R.string.keybox_expiry_summary, expiryText));
                } else {
                    mExpiryPref.setSummary(getString(R.string.keybox_expiry_summary, "N/A"));
                }
                
            } else {
                mStatusPref.setSummary(getString(R.string.keybox_status_summary, getString(R.string.keybox_status_not_installed)));
                mStatusPref.setIcon(android.R.drawable.ic_dialog_alert);
                mDeviceIdPref.setSummary(getString(R.string.keybox_device_id_summary, "N/A"));
                mCertCountPref.setSummary(getString(R.string.keybox_cert_count_summary, 0, 0));
                mExpiryPref.setSummary(getString(R.string.keybox_expiry_summary, "N/A"));
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
            showExportDialog();
            return true;
        } else if (KEY_KEYBOX_VERIFY.equals(key)) {
            showVerifyDialog();
            return true;
        } else if (KEY_KEYBOX_RESET.equals(key)) {
            showResetDialog();
            return true;
        } else if (KEY_KEYBOX_ANALYZE.equals(key)) {
            showAnalyzeDialog();
            return true;
        } else if (KEY_KEYBOX_CHECK_API.equals(key)) {
            showCheckApiDialog();
            return true;
        } else if (KEY_KEYBOX_COMPARE.equals(key)) {
            showCompareDialog();
            return true;
        } else if (KEY_KEYBOX_EXTRACT_CERTS.equals(key)) {
            showExtractCertsDialog();
            return true;
        } else if (KEY_KEYBOX_BATCH_VERIFY.equals(key)) {
            showBatchVerifyDialog();
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
                .setTitle(R.string.keybox_generate_dialog_title)
                .setMessage("Generate keybox structure templates:\n\n" +
                    "1️⃣ Play Integrity Fix format (Base64)\n" +
                    "2️⃣ TrickyStore format (PEM)\n\n" +
                    "⚠️ These are STRUCTURAL TEMPLATES ONLY!\n" +
                    "Not valid for actual use.\n\n" +
                    "💡 For working keyboxes:\n" +
                    "• Use 'Search Keybox'\n" +
                    "• Or 'Import Keybox'\n\n" +
                    "Generate templates?")
                .setPositiveButton(android.R.string.ok, (d, w) -> generateTemplates())
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
            mProgressDialog.setMessage("Creating templates...");
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
            dismissProgressDialog();
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
                .setTitle(R.string.keybox_search_dialog_title)
                .setMessage("This will search online sources (GitHub, Gist) for new keybox files and download them.\n\nDo you want to continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> downloadKeyboxes())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void downloadKeyboxes() {
        new SearchKeyboxTask().execute();
    }

    private class SearchKeyboxTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_searching);
            mProgressDialog.setMessage("Connecting...");
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
                publishProgress("Fetching sources...");
                return mKeyboxUtils.searchAndDownloadKeyboxes(10);
            } catch (Exception e) {
                return new KeyboxManagerUtils.OperationResult(false, "Error: " + e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showSuccessDialog("✅ Download Complete!\n\n" + result.message);
                updateUI();
            } else {
                showInfoDialog("ℹ️ Search Results", result.message);
            }
        }
    }

    // ==================== IMPORT ====================
    
    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_import_dialog_title)
                .setMessage("This will open the file picker to select a keybox.xml file to import.\n\nDo you want to continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> openFilePicker())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/xml");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select keybox.xml"), REQUEST_IMPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importKeybox(data.getData());
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
            mProgressDialog.setMessage("Importing...");
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
            dismissProgressDialog();
            if (result.success) {
                showSuccessDialog(result.message);
                updateUI();
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== EXPORT (List) ====================
    
    private void showExportDialog() {
        KeyboxManagerUtils.OperationResult result = mKeyboxUtils.exportKeybox();
        if (result.success) {
            showInfoDialog("📂 Downloaded Keyboxes", result.message);
        } else {
            showErrorDialog(result.message);
        }
    }

    // ==================== VERIFY ====================
    
    private void showVerifyDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_verify_dialog_title)
                .setMessage("This will perform a basic validation (check XML structure, keys, format) on all downloaded keybox files.\n\nThis does NOT test Play Integrity.\n\nDo you want to continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> verifyKeybox())
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
            mProgressDialog.setMessage("Verifying...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.verifyKeybox();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success || result.basicIntegrity) {
                showInfoDialog("✅ Validation Results", result.message);
                updateUI();
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== DEEP ANALYSIS ====================
    
    private void showAnalyzeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_analyze_dialog_title)
                .setMessage(R.string.keybox_analyze_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> analyzeKeybox())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void analyzeKeybox() {
        new AnalyzeKeyboxTask().execute();
    }

    private class AnalyzeKeyboxTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_analyzing);
            mProgressDialog.setMessage("Analyzing certificates...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.analyzeKeyboxDeep();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showInfoDialog("🔬 Deep Analysis Results", result.message);
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== CHECK PLAY INTEGRITY API ====================
    
    private void showCheckApiDialog() {
        if (!mKeyboxUtils.isNetworkAvailable()) {
            showToast(getString(R.string.keybox_no_internet));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_check_api_dialog_title)
                .setMessage(R.string.keybox_check_api_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> checkPlayIntegrity())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void checkPlayIntegrity() {
        new CheckApiTask().execute();
    }

    private class CheckApiTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_checking_api);
            mProgressDialog.setMessage("Testing...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.checkPlayIntegrityApi();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showInfoDialog("✅ Play Integrity Results", result.message);
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== COMPARE KEYBOXES ====================
    
    private void showCompareDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_compare_dialog_title)
                .setMessage(R.string.keybox_compare_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> compareKeyboxes())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void compareKeyboxes() {
        new CompareKeyboxTask().execute();
    }

    private class CompareKeyboxTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_comparing);
            mProgressDialog.setMessage("Comparing...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.compareKeyboxes();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showInfoDialog("📊 Comparison Results", result.message);
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== EXTRACT CERTIFICATES ====================
    
    private void showExtractCertsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_extract_dialog_title)
                .setMessage(R.string.keybox_extract_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> extractCertificates())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void extractCertificates() {
        new ExtractCertsTask().execute();
    }

    private class ExtractCertsTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle(R.string.keybox_extracting);
            mProgressDialog.setMessage("Extracting...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.extractCertificates();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showSuccessDialog(result.message);
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== BATCH VERIFY ====================
    
    private void showBatchVerifyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Batch Verification")
                .setMessage("Verify all downloaded keyboxes with detailed reports.\n\n" +
                    "This will check:\n" +
                    "• XML structure\n" +
                    "• Certificate validity\n" +
                    "• Expiration dates\n" +
                    "• Format detection\n\n" +
                    "Continue?")
                .setPositiveButton(android.R.string.ok, (d, w) -> batchVerify())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void batchVerify() {
        new BatchVerifyTask().execute();
    }

    private class BatchVerifyTask extends AsyncTask<Void, String, KeyboxManagerUtils.OperationResult> {
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(KeyboxManagerActivity.this);
            mProgressDialog.setTitle("Batch Verifying");
            mProgressDialog.setMessage("Processing...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected KeyboxManagerUtils.OperationResult doInBackground(Void... params) {
            return mKeyboxUtils.batchVerifyKeyboxes();
        }

        @Override
        protected void onPostExecute(KeyboxManagerUtils.OperationResult result) {
            dismissProgressDialog();
            if (result.success) {
                showInfoDialog("✅ Batch Verification", result.message);
            } else {
                showErrorDialog(result.message);
            }
        }
    }

    // ==================== RESET ====================
    
    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_reset_dialog_title)
                .setMessage(R.string.keybox_reset_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> resetKeybox())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetKeybox() {
        KeyboxManagerUtils.OperationResult result = mKeyboxUtils.resetKeybox();
        if (result.success) {
            showSuccessDialog(result.message);
            updateUI();
        } else {
            showErrorDialog(result.message);
        }
    }

    // ==================== INFO ====================
    
    private void showInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.keybox_info_dialog_title)
                .setMessage(R.string.keybox_info_dialog_message)
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

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }
}
