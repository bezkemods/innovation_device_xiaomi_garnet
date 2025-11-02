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

    // Display Features
    private static final String KEY_SMOOTH_MOTION_ENABLED = "smooth_motion_enabled";
    private static final String KEY_OPTIMIZE_REFRESH_ENABLED = "optimize_refresh_enabled";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate_enabled";
    
    // Performance Optimizations
    private static final String KEY_FORCE_VULKAN_ENABLED = "force_vulkan_enabled";
    private static final String KEY_PURGEABLE_ASSETS_ENABLED = "purgeable_assets_enabled";
    private static final String KEY_GFX_ACCEL_ENABLED = "gfx_accel_enabled";
    private static final String KEY_ADPF_CPU_HINT_ENABLED = "adpf_cpu_hint_enabled";
    private static final String KEY_GPU_SCHEDULING = "gpu_scheduling_enabled";
    
    // Renderer Settings
    private static final String KEY_SKIAGL_RENDERER_ENABLED = "skiagl_renderer_enabled";
    private static final String KEY_SKIAVK_RENDERER_ENABLED = "skiavk_renderer_enabled";
    
    // Advanced Features
    private static final String KEY_HWUI_FORCE_GPU = "hwui_force_gpu_enabled";
    private static final String KEY_HWUI_DISABLE_VSYNC = "hwui_disable_vsync";
    private static final String KEY_DISABLE_SCALER = "disable_scaler_enabled";
    private static final String KEY_MEDIA_CODEC_HW = "media_codec_hw_enabled";
    
    // Info
    private static final String KEY_VIDEO_ENHANCER_INFO = "video_enhancer_info";
    private static final String KEY_RESET_DEFAULTS = "reset_to_defaults";

    // Display Features
    private SwitchPreference mSmoothMotionEnabled;
    private SwitchPreference mOptimizeRefreshEnabled;
    private SwitchPreference mPeakRefreshRateEnabled;
    
    // Performance
    private SwitchPreference mForceVulkanEnabled;
    private SwitchPreference mPurgeableAssetsEnabled;
    private SwitchPreference mGfxAccelEnabled;
    private SwitchPreference mAdpfCpuHintEnabled;
    private SwitchPreference mGpuSchedulingEnabled;
    
    // Renderer
    private SwitchPreference mSkiaGLRendererEnabled;
    private SwitchPreference mSkiaVKRendererEnabled;
    
    // Advanced
    private SwitchPreference mHwuiForceGpuEnabled;
    private SwitchPreference mHwuiDisableVsync;
    private SwitchPreference mDisableScalerEnabled;
    private SwitchPreference mMediaCodecHwEnabled;
    
    private Preference mInfo;
    private Preference mResetDefaults;

    private VideoEnhancerUtils mVideoEnhancerUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.video_enhancer_settings);

        mVideoEnhancerUtils = new VideoEnhancerUtils(this);
        initializePreferences();
        updateUI();

        if (!mVideoEnhancerUtils.isRootAvailable()) {
            Toast.makeText(this, "⚠️ ROOT REQUIRED: Install Magisk/KernelSU & grant permission", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void initializePreferences() {
        // Display Features
        mSmoothMotionEnabled = (SwitchPreference) findPreference(KEY_SMOOTH_MOTION_ENABLED);
        mOptimizeRefreshEnabled = (SwitchPreference) findPreference(KEY_OPTIMIZE_REFRESH_ENABLED);
        mPeakRefreshRateEnabled = (SwitchPreference) findPreference(KEY_PEAK_REFRESH_RATE);
        
        // Performance
        mForceVulkanEnabled = (SwitchPreference) findPreference(KEY_FORCE_VULKAN_ENABLED);
        mPurgeableAssetsEnabled = (SwitchPreference) findPreference(KEY_PURGEABLE_ASSETS_ENABLED);
        mGfxAccelEnabled = (SwitchPreference) findPreference(KEY_GFX_ACCEL_ENABLED);
        mAdpfCpuHintEnabled = (SwitchPreference) findPreference(KEY_ADPF_CPU_HINT_ENABLED);
        mGpuSchedulingEnabled = (SwitchPreference) findPreference(KEY_GPU_SCHEDULING);
        
        // Renderer
        mSkiaGLRendererEnabled = (SwitchPreference) findPreference(KEY_SKIAGL_RENDERER_ENABLED);
        mSkiaVKRendererEnabled = (SwitchPreference) findPreference(KEY_SKIAVK_RENDERER_ENABLED);
        
        // Advanced
        mHwuiForceGpuEnabled = (SwitchPreference) findPreference(KEY_HWUI_FORCE_GPU);
        mHwuiDisableVsync = (SwitchPreference) findPreference(KEY_HWUI_DISABLE_VSYNC);
        mDisableScalerEnabled = (SwitchPreference) findPreference(KEY_DISABLE_SCALER);
        mMediaCodecHwEnabled = (SwitchPreference) findPreference(KEY_MEDIA_CODEC_HW);
        
        mInfo = findPreference(KEY_VIDEO_ENHANCER_INFO);
        mResetDefaults = findPreference(KEY_RESET_DEFAULTS);

        // Set listeners
        setListener(mSmoothMotionEnabled);
        setListener(mOptimizeRefreshEnabled);
        setListener(mPeakRefreshRateEnabled);
        setListener(mForceVulkanEnabled);
        setListener(mPurgeableAssetsEnabled);
        setListener(mGfxAccelEnabled);
        setListener(mAdpfCpuHintEnabled);
        setListener(mGpuSchedulingEnabled);
        setListener(mSkiaGLRendererEnabled);
        setListener(mSkiaVKRendererEnabled);
        setListener(mHwuiForceGpuEnabled);
        setListener(mHwuiDisableVsync);
        setListener(mDisableScalerEnabled);
        setListener(mMediaCodecHwEnabled);
        
        if (mInfo != null) mInfo.setOnPreferenceClickListener(this);
        if (mResetDefaults != null) mResetDefaults.setOnPreferenceClickListener(this);
    }

    private void setListener(SwitchPreference pref) {
        if (pref != null) pref.setOnPreferenceChangeListener(this);
    }

    private void updateUI() {
        // Display Features
        if (mSmoothMotionEnabled != null) 
            mSmoothMotionEnabled.setChecked(mVideoEnhancerUtils.isSmoothMotionEnabled());
        if (mOptimizeRefreshEnabled != null) 
            mOptimizeRefreshEnabled.setChecked(mVideoEnhancerUtils.isOptimizeRefreshEnabled());
        if (mPeakRefreshRateEnabled != null)
            mPeakRefreshRateEnabled.setChecked(mVideoEnhancerUtils.isPeakRefreshRateEnabled());
            
        // Performance
        if (mForceVulkanEnabled != null) 
            mForceVulkanEnabled.setChecked(mVideoEnhancerUtils.isForceVulkanEnabled());
        if (mPurgeableAssetsEnabled != null) 
            mPurgeableAssetsEnabled.setChecked(mVideoEnhancerUtils.isPurgeableAssetsEnabled());
        if (mGfxAccelEnabled != null) 
            mGfxAccelEnabled.setChecked(mVideoEnhancerUtils.isGfxAccelEnabled());
        if (mAdpfCpuHintEnabled != null) 
            mAdpfCpuHintEnabled.setChecked(mVideoEnhancerUtils.isAdpfCpuHintEnabled());
        if (mGpuSchedulingEnabled != null)
            mGpuSchedulingEnabled.setChecked(mVideoEnhancerUtils.isGpuSchedulingEnabled());
            
        // Renderer
        if (mSkiaGLRendererEnabled != null) 
            mSkiaGLRendererEnabled.setChecked(mVideoEnhancerUtils.isSkiaGLRendererEnabled());
        if (mSkiaVKRendererEnabled != null) 
            mSkiaVKRendererEnabled.setChecked(mVideoEnhancerUtils.isSkiaVKRendererEnabled());
            
        // Advanced
        if (mHwuiForceGpuEnabled != null)
            mHwuiForceGpuEnabled.setChecked(mVideoEnhancerUtils.isHwuiForceGpuEnabled());
        if (mHwuiDisableVsync != null)
            mHwuiDisableVsync.setChecked(mVideoEnhancerUtils.isHwuiDisableVsyncEnabled());
        if (mDisableScalerEnabled != null)
            mDisableScalerEnabled.setChecked(mVideoEnhancerUtils.isDisableScalerEnabled());
        if (mMediaCodecHwEnabled != null)
            mMediaCodecHwEnabled.setChecked(mVideoEnhancerUtils.isMediaCodecHwEnabled());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        boolean enabled = (Boolean) newValue;
        boolean needsReboot = false;
        boolean success = false;

        // NEW: VSync disable warning
        if (KEY_HWUI_DISABLE_VSYNC.equals(key) && enabled) {
            showVsyncWarningDialog(() -> {
                boolean result = mVideoEnhancerUtils.setHwuiDisableVsyncEnabled(true);
                if (result) {
                    showRebootDialog();
                    updateUI();
                } else {
                    showErrorToast();
                    updateUI();
                }
            });
            return false; // Don't change yet
        }

        switch (key) {
            // Display Features - FIXED: all require reboot
            case KEY_SMOOTH_MOTION_ENABLED:
                success = mVideoEnhancerUtils.setSmoothMotionEnabled(enabled);
                needsReboot = true; // FIXED
                break;
            case KEY_OPTIMIZE_REFRESH_ENABLED:
                success = mVideoEnhancerUtils.setOptimizeRefreshEnabled(enabled);
                needsReboot = true; // FIXED
                break;
            case KEY_PEAK_REFRESH_RATE:
                success = mVideoEnhancerUtils.setPeakRefreshRateEnabled(enabled);
                needsReboot = true;
                break;
                
            // Performance
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
                needsReboot = true; // FIXED
                break;
            case KEY_ADPF_CPU_HINT_ENABLED:
                success = mVideoEnhancerUtils.setAdpfCpuHintEnabled(enabled);
                needsReboot = true; // FIXED
                break;
            case KEY_GPU_SCHEDULING:
                success = mVideoEnhancerUtils.setGpuSchedulingEnabled(enabled);
                needsReboot = true;
                break;
                
            // Renderer
            case KEY_SKIAGL_RENDERER_ENABLED:
                if (enabled && mSkiaVKRendererEnabled != null && mSkiaVKRendererEnabled.isChecked()) {
                    mSkiaVKRendererEnabled.setChecked(false);
                    mVideoEnhancerUtils.setSkiaVKRendererEnabled(false);
                }
                success = mVideoEnhancerUtils.setSkiaGLRendererEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_SKIAVK_RENDERER_ENABLED:
                if (enabled && mSkiaGLRendererEnabled != null && mSkiaGLRendererEnabled.isChecked()) {
                    mSkiaGLRendererEnabled.setChecked(false);
                    mVideoEnhancerUtils.setSkiaGLRendererEnabled(false);
                }
                success = mVideoEnhancerUtils.setSkiaVKRendererEnabled(enabled);
                needsReboot = true;
                break;
                
            // Advanced
            case KEY_HWUI_FORCE_GPU:
                success = mVideoEnhancerUtils.setHwuiForceGpuEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_HWUI_DISABLE_VSYNC:
                // Already handled above with warning
                success = mVideoEnhancerUtils.setHwuiDisableVsyncEnabled(enabled);
                needsReboot = true;
                break;
            case KEY_DISABLE_SCALER:
                success = mVideoEnhancerUtils.setDisableScalerEnabled(enabled);
                needsReboot = true; // FIXED
                break;
            case KEY_MEDIA_CODEC_HW:
                success = mVideoEnhancerUtils.setMediaCodecHwEnabled(enabled);
                needsReboot = true;
                break;
        }

        if (success) {
            if (needsReboot) {
                showRebootDialog();
            } else {
                Toast.makeText(this, "✓ " + getString(R.string.video_enhancer_feature_applied), 
                    Toast.LENGTH_SHORT).show();
            }
            updateUI();
            return true;
        } else {
            showErrorToast();
            updateUI();
            return false;
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        
        if (KEY_VIDEO_ENHANCER_INFO.equals(key)) {
            showInfoDialog();
            return true;
        } else if (KEY_RESET_DEFAULTS.equals(key)) {
            showResetDialog();
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
                    if (pm != null) {
                        pm.reboot(null);
                    }
                })
                .setNegativeButton(R.string.reboot_later, null)
                .setCancelable(true)
                .show();
    }

    private void showInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.video_enhancer_info_dialog_title)
                .setMessage(R.string.video_enhancer_info_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_to_defaults_title)
                .setMessage(R.string.reset_to_defaults_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    resetToDefaults();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // NEW: VSync warning dialog
    private void showVsyncWarningDialog(Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ WARNING")
                .setMessage("Disabling VSync may cause SCREEN TEARING and video STUTTERING!\n\n" +
                           "This option is recommended for testing purposes only.\n\n" +
                           "If you already have video issues, DO NOT disable VSync!\n\n" +
                           "Are you sure you want to continue?")
                .setPositiveButton("Yes, disable it", (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton("No, keep it enabled", null)
                .setCancelable(true)
                .show();
    }

    private void showErrorToast() {
        if (!mVideoEnhancerUtils.isRootAvailable()) {
            Toast.makeText(this, "⚠️ ROOT REQUIRED: Install Magisk/KernelSU & grant permission", 
                Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "✗ " + getString(R.string.video_enhancer_feature_failed), 
                Toast.LENGTH_LONG).show();
        }
    }

    private void resetToDefaults() {
        mVideoEnhancerUtils.resetToDefaults();
        updateUI();
        Toast.makeText(this, R.string.reset_to_defaults_success, Toast.LENGTH_SHORT).show();
        showRebootDialog();
    }
}
