package org.lineageos.settings.videoenhancer;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
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

    private static final String KEY_SMOOTH_MOTION_ENABLED = "smooth_motion_enabled";
    private static final String KEY_OPTIMIZE_REFRESH_ENABLED = "optimize_refresh_enabled";
    private static final String KEY_FORCE_VULKAN_ENABLED = "force_vulkan_enabled";
    private static final String KEY_PURGEABLE_ASSETS_ENABLED = "purgeable_assets_enabled";
    private static final String KEY_GFX_ACCEL_ENABLED = "gfx_accel_enabled";
    private static final String KEY_ADPF_CPU_HINT_ENABLED = "adpf_cpu_hint_enabled";
    private static final String KEY_SKIAGL_RENDERER_ENABLED = "skiagl_renderer_enabled";
    private static final String KEY_SKIAVK_RENDERER_ENABLED = "skiavk_renderer_enabled";
    private static final String KEY_VIDEO_ENHANCER_INFO = "video_enhancer_info";

    private SwitchPreference mSmoothMotionEnabled;
    private SwitchPreference mOptimizeRefreshEnabled;
    private SwitchPreference mForceVulkanEnabled;
    private SwitchPreference mPurgeableAssetsEnabled;
    private SwitchPreference mGfxAccelEnabled;
    private SwitchPreference mAdpfCpuHintEnabled;
    private SwitchPreference mSkiaGLRendererEnabled;
    private SwitchPreference mSkiaVKRendererEnabled;
    private Preference mInfo;

    private VideoEnhancerUtils mVideoEnhancerUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.video_enhancer_settings);

        mVideoEnhancerUtils = new VideoEnhancerUtils(this);
        initializePreferences();
        updateUI();

        if (!mVideoEnhancerUtils.isRootAvailable()) {
            Toast.makeText(this, "ROOT REQUIRED: Install Magisk & grant permission", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void initializePreferences() {
        mSmoothMotionEnabled = (SwitchPreference) findPreference(KEY_SMOOTH_MOTION_ENABLED);
        mOptimizeRefreshEnabled = (SwitchPreference) findPreference(KEY_OPTIMIZE_REFRESH_ENABLED);
        mForceVulkanEnabled = (SwitchPreference) findPreference(KEY_FORCE_VULKAN_ENABLED);
        mPurgeableAssetsEnabled = (SwitchPreference) findPreference(KEY_PURGEABLE_ASSETS_ENABLED);
        mGfxAccelEnabled = (SwitchPreference) findPreference(KEY_GFX_ACCEL_ENABLED);
        mAdpfCpuHintEnabled = (SwitchPreference) findPreference(KEY_ADPF_CPU_HINT_ENABLED);
        mSkiaGLRendererEnabled = (SwitchPreference) findPreference(KEY_SKIAGL_RENDERER_ENABLED);
        mSkiaVKRendererEnabled = (SwitchPreference) findPreference(KEY_SKIAVK_RENDERER_ENABLED);
        mInfo = findPreference(KEY_VIDEO_ENHANCER_INFO);

        setListener(mSmoothMotionEnabled);
        setListener(mOptimizeRefreshEnabled);
        setListener(mForceVulkanEnabled);
        setListener(mPurgeableAssetsEnabled);
        setListener(mGfxAccelEnabled);
        setListener(mAdpfCpuHintEnabled);
        setListener(mSkiaGLRendererEnabled);
        setListener(mSkiaVKRendererEnabled);
        if (mInfo != null) mInfo.setOnPreferenceClickListener(this);
    }

    private void setListener(SwitchPreference pref) {
        if (pref != null) pref.setOnPreferenceChangeListener(this);
    }

    private void updateUI() {
        if (mSmoothMotionEnabled != null) mSmoothMotionEnabled.setChecked(mVideoEnhancerUtils.isSmoothMotionEnabled());
        if (mOptimizeRefreshEnabled != null) mOptimizeRefreshEnabled.setChecked(mVideoEnhancerUtils.isOptimizeRefreshEnabled());
        if (mForceVulkanEnabled != null) mForceVulkanEnabled.setChecked(mVideoEnhancerUtils.isForceVulkanEnabled());
        if (mPurgeableAssetsEnabled != null) mPurgeableAssetsEnabled.setChecked(mVideoEnhancerUtils.isPurgeableAssetsEnabled());
        if (mGfxAccelEnabled != null) mGfxAccelEnabled.setChecked(mVideoEnhancerUtils.isGfxAccelEnabled());
        if (mAdpfCpuHintEnabled != null) mAdpfCpuHintEnabled.setChecked(mVideoEnhancerUtils.isAdpfCpuHintEnabled());
        if (mSkiaGLRendererEnabled != null) mSkiaGLRendererEnabled.setChecked(mVideoEnhancerUtils.isSkiaGLRendererEnabled());
        if (mSkiaVKRendererEnabled != null) mSkiaVKRendererEnabled.setChecked(mVideoEnhancerUtils.isSkiaVKRendererEnabled());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        boolean enabled = (Boolean) newValue;
        boolean needsReboot = false;
        boolean success = false;

        switch (key) {
            case KEY_SMOOTH_MOTION_ENABLED:
                success = mVideoEnhancerUtils.setSmoothMotionEnabled(enabled);
                break;
            case KEY_OPTIMIZE_REFRESH_ENABLED:
                success = mVideoEnhancerUtils.setOptimizeRefreshEnabled(enabled);
                break;
            case KEY_FORCE_VULKAN_ENABLED:
                success = mVideoEnhancerUtils.setForceVulkanEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_PURGEABLE_ASSETS_ENABLED:
                success = mVideoEnhancerUtils.setPurgeableAssetsEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_GFX_ACCEL_ENABLED:
                success = mVideoEnhancerUtils.setGfxAccelEnabled(enabled);
                break;
            case KEY_ADPF_CPU_HINT_ENABLED:
                success = mVideoEnhancerUtils.setAdpfCpuHintEnabled(enabled);
                break;
            case KEY_SKIAGL_RENDERER_ENABLED:
                if (enabled && mSkiaVKRendererEnabled.isChecked()) {
                    mSkiaVKRendererEnabled.setChecked(false);
                    mVideoEnhancerUtils.setSkiaVKRendererEnabled(false);
                }
                success = mVideoEnhancerUtils.setSkiaGLRendererEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_SKIAVK_RENDERER_ENABLED:
                if (enabled && mSkiaGLRendererEnabled.isChecked()) {
                    mSkiaGLRendererEnabled.setChecked(false);
                    mVideoEnhancerUtils.setSkiaGLRendererEnabled(false);
                }
                success = mVideoEnhancerUtils.setSkiaVKRendererEnabled(enabled);
                needsReboot = true;
                break;
        }

        if (success) {
            if (needsReboot) showRebootDialog();
            else Toast.makeText(this, R.string.video_enhancer_feature_applied, Toast.LENGTH_SHORT).show();
            updateUI();
            return true;
        } else {
            if (!mVideoEnhancerUtils.isRootAvailable()) {
                Toast.makeText(this, "ROOT REQUIRED: Install Magisk & grant permission", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.video_enhancer_feature_failed, Toast.LENGTH_LONG).show();
            }
            updateUI();
            return false;
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (KEY_VIDEO_ENHANCER_INFO.equals(preference.getKey())) {
            showInfoDialog();
            return true;
        }
        return false;
    }

    private void showRebootDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reboot_required_title)
                .setMessage(R.string.reboot_required_message)
                .setPositiveButton(R.string.reboot_now, (d, w) -> {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    pm.reboot(null);
                })
                .setNegativeButton(R.string.reboot_later, null)
                .show();
    }

    private void showInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.video_enhancer_info_dialog_title)
                .setMessage(R.string.video_enhancer_info_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
