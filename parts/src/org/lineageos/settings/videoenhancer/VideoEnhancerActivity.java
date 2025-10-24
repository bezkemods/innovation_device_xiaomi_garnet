package org.lineageos.settings.videoenhancer;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
import android.widget.Toast;

import org.lineageos.settings.R;

public class VideoEnhancerActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "VideoEnhancerActivity";

    private static final String KEY_VIDEO_ENHANCER_ENABLED = "video_enhancer_enabled";
    private static final String KEY_MEMC_ENABLED = "memc_enabled";
    private static final String KEY_SUPER_RESOLUTION_ENABLED = "super_resolution_enabled";
    private static final String KEY_AI_HDR_ENABLED = "ai_hdr_enabled";
    private static final String KEY_VIDEO_ENHANCER_INFO = "video_enhancer_info";

    private SwitchPreference mVideoEnhancerEnabled;
    private SwitchPreference mMemcEnabled;
    private SwitchPreference mSuperResolutionEnabled;
    private SwitchPreference mAiHdrEnabled;
    private Preference mInfo;

    private VideoEnhancerUtils mVideoEnhancerUtils;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() called");

        addPreferencesFromResource(R.xml.video_enhancer_settings);

        mVideoEnhancerUtils = new VideoEnhancerUtils(this);
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

        mVideoEnhancerEnabled = (SwitchPreference) findPreference(KEY_VIDEO_ENHANCER_ENABLED);
        mMemcEnabled = (SwitchPreference) findPreference(KEY_MEMC_ENABLED);
        mSuperResolutionEnabled = (SwitchPreference) findPreference(KEY_SUPER_RESOLUTION_ENABLED);
        mAiHdrEnabled = (SwitchPreference) findPreference(KEY_AI_HDR_ENABLED);
        mInfo = findPreference(KEY_VIDEO_ENHANCER_INFO);

        if (mVideoEnhancerEnabled != null) {
            mVideoEnhancerEnabled.setOnPreferenceChangeListener(this);
        }
        if (mMemcEnabled != null) {
            mMemcEnabled.setOnPreferenceChangeListener(this);
        }
        if (mSuperResolutionEnabled != null) {
            mSuperResolutionEnabled.setOnPreferenceChangeListener(this);
        }
        if (mAiHdrEnabled != null) {
            mAiHdrEnabled.setOnPreferenceChangeListener(this);
        }
        if (mInfo != null) {
            mInfo.setOnPreferenceClickListener(this);
        }

        Log.d(TAG, "All preferences initialized");
    }

    private void updateUI() {
        Log.d(TAG, "updateUI() called");

        boolean isEnabled = mVideoEnhancerUtils.isEnabled();
        boolean isMemcEnabled = mVideoEnhancerUtils.isMemcEnabled();
        boolean isSuperResolutionEnabled = mVideoEnhancerUtils.isSuperResolutionEnabled();
        boolean isAiHdrEnabled = mVideoEnhancerUtils.isAiHdrEnabled();

        Log.d(TAG, "UI Update - Enabled: " + isEnabled + ", MEMC: " + isMemcEnabled +
                ", SuperRes: " + isSuperResolutionEnabled + ", AIHDR: " + isAiHdrEnabled);

        if (mVideoEnhancerEnabled != null) {
            mVideoEnhancerEnabled.setChecked(isEnabled);
        }
        if (mMemcEnabled != null) {
            mMemcEnabled.setChecked(isMemcEnabled);
            mMemcEnabled.setEnabled(isEnabled);
        }
        if (mSuperResolutionEnabled != null) {
            mSuperResolutionEnabled.setChecked(isSuperResolutionEnabled);
            mSuperResolutionEnabled.setEnabled(isEnabled && !isMemcEnabled); // Cannot be used with MEMC
        }
        if (mAiHdrEnabled != null) {
            mAiHdrEnabled.setChecked(isAiHdrEnabled);
            mAiHdrEnabled.setEnabled(isEnabled);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceChange() - Key: " + key + ", Value: " + newValue);

        boolean enabled = (Boolean) newValue;

        switch (key) {
            case KEY_VIDEO_ENHANCER_ENABLED:
                handleVideoEnhancerToggle(enabled);
                return true;
            case KEY_MEMC_ENABLED:
                if (enabled && mSuperResolutionEnabled.isChecked()) {
                    Toast.makeText(this, R.string.memc_super_res_conflict, Toast.LENGTH_LONG).show();
                    return false; // Prevent enabling if Super Res is on
                }
                mVideoEnhancerUtils.setMemcEnabled(enabled);
                updateUI();
                return true;
            case KEY_SUPER_RESOLUTION_ENABLED:
                if (enabled && mMemcEnabled.isChecked()) {
                    Toast.makeText(this, R.string.memc_super_res_conflict, Toast.LENGTH_LONG).show();
                    return false; // Prevent enabling if MEMC is on
                }
                mVideoEnhancerUtils.setSuperResolutionEnabled(enabled);
                updateUI();
                return true;
            case KEY_AI_HDR_ENABLED:
                mVideoEnhancerUtils.setAiHdrEnabled(enabled);
                updateUI();
                return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        Log.d(TAG, "onPreferenceClick() - Key: " + key);

        if (KEY_VIDEO_ENHANCER_INFO.equals(key)) {
            showInfoDialog();
            return true;
        }

        return false;
    }

    private void handleVideoEnhancerToggle(boolean enable) {
        Log.d(TAG, "handleVideoEnhancerToggle(" + enable + ")");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.video_enhancer_confirm_title);

        if (enable) {
            builder.setMessage(getString(R.string.video_enhancer_confirm_enable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                enableVideoEnhancer();
            });
        } else {
            builder.setMessage(getString(R.string.video_enhancer_confirm_disable));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> disableVideoEnhancer());
        }

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void enableVideoEnhancer() {
        Log.d(TAG, "enableVideoEnhancer() called");

        if (mVideoEnhancerUtils.enableVideoEnhancer()) {
            Toast.makeText(this, R.string.video_enhancer_enabled, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video Enhancer enabled successfully");
            updateUI();
        } else {
            Toast.makeText(this, "Failed to enable Video Enhancer!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to enable Video Enhancer");
        }
    }

    private void disableVideoEnhancer() {
        Log.d(TAG, "disableVideoEnhancer() called");

        if (mVideoEnhancerUtils.disableVideoEnhancer()) {
            Toast.makeText(this, R.string.video_enhancer_disabled, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video Enhancer disabled successfully");
            updateUI();
        } else {
            Toast.makeText(this, "Failed to disable Video Enhancer!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to disable Video Enhancer");
        }
    }

    private void showInfoDialog() {
        Log.d(TAG, "showInfoDialog() called");

        StringBuilder info = new StringBuilder();
        info.append("Video Enhancer features:\n\n");
        info.append("MEMC: Adds frames to low frame rate videos for smoother playback.\n\n");
        info.append("Super Resolution: Upscales ≤720p videos to higher resolution.\n\n");
        info.append("AI HDR: Applies HDR effects using AI algorithms.\n\n");
        info.append("Note: MEMC and Super Resolution cannot be enabled simultaneously.\n\n");
        info.append("These features may require root access or specific system permissions.\n");
        info.append("Supported on Xiaomi Redmi Note 13 Pro 5G (Garnet).");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Video Enhancer Information");
        builder.setMessage(info.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
