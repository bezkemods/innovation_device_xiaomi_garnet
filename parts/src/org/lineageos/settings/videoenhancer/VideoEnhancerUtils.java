package org.lineageos.settings.videoenhancer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class VideoEnhancerUtils {
    private static final String TAG = "VideoEnhancerUtils";

    // Preference keys - Display Features
    private static final String PREF_SMOOTH_MOTION_ENABLED = "smooth_motion_enabled";
    private static final String PREF_OPTIMIZE_REFRESH_ENABLED = "optimize_refresh_enabled";
    private static final String PREF_PEAK_REFRESH_RATE = "peak_refresh_rate_enabled";
    
    // Preference keys - Performance
    private static final String PREF_FORCE_VULKAN_ENABLED = "force_vulkan_enabled";
    private static final String PREF_PURGEABLE_ASSETS_ENABLED = "purgeable_assets_enabled";
    private static final String PREF_GFX_ACCEL_ENABLED = "gfx_accel_enabled";
    private static final String PREF_ADPF_CPU_HINT_ENABLED = "adpf_cpu_hint_enabled";
    private static final String PREF_GPU_SCHEDULING = "gpu_scheduling_enabled";
    
    // Preference keys - Renderer
    private static final String PREF_SKIAGL_RENDERER_ENABLED = "skiagl_renderer_enabled";
    private static final String PREF_SKIAVK_RENDERER_ENABLED = "skiavk_renderer_enabled";
    
    // Preference keys - Advanced
    private static final String PREF_HWUI_FORCE_GPU = "hwui_force_gpu_enabled";
    private static final String PREF_HWUI_DISABLE_VSYNC = "hwui_disable_vsync";
    private static final String PREF_DISABLE_SCALER = "disable_scaler_enabled";
    private static final String PREF_MEDIA_CODEC_HW = "media_codec_hw_enabled";

    // System properties - Display Features (FIXED)
    private static final String PROP_SMOOTH_MOTION = "vendor.display.use_smooth_motion";
    private static final String PROP_OPTIMIZE_REFRESH = "vendor.display.enable_optimize_refresh";
    private static final String PROP_PEAK_REFRESH_RATE = "ro.surface_flinger.set_touch_timer_ms";
    private static final String PROP_IDLE_TIMER = "debug.sf.set_idle_timer_ms"; // NEW: idle timer
    
    // System properties - Performance (FIXED)
    private static final String PROP_HWUI_USE_VULKAN = "ro.hwui.use_vulkan"; // FIXED property name
    private static final String PROP_PURGEABLE_ASSETS = "persist.sys.purgeable_assets";
    private static final String PROP_GFX_ACCEL = "ro.config.avoid_gfx_accel";
    private static final String PROP_ADPF_CPU_HINT = "debug.sf.enable_adpf_cpu_hint";
    private static final String PROP_GPU_SCHEDULING = "persist.sys.gpu.scheduling";
    
    // System properties - Renderer
    private static final String PROP_HWUI_RENDERER = "debug.hwui.renderer";
    
    // System properties - Advanced
    private static final String PROP_HWUI_FORCE_GPU = "persist.sys.ui.hw";
    private static final String PROP_HWUI_DISABLE_VSYNC = "debug.hwui.disable_vsync";
    private static final String PROP_DISABLE_SCALER = "vendor.display.disable_scaler";
    private static final String PROP_MEDIA_CODEC_HW = "debug.stagefright.c2-poolmask";
    
    // NEW: Frame pacing properties
    private static final String PROP_DISABLE_CLIENT_COMP_CACHE = "debug.sf.disable_client_composition_cache";
    private static final String PROP_ENABLE_GL_BACKPRESSURE = "debug.sf.enable_gl_backpressure";

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public VideoEnhancerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Log.d(TAG, "VideoEnhancerUtils initialized for Redmi Note 13 Pro 5G");
    }

    public boolean isRootAvailable() {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            int exit = process.waitFor();
            boolean hasRoot = exit == 0 && output != null && output.contains("uid=0");
            Log.d(TAG, "Root check: " + (hasRoot ? "available" : "not available"));
            return hasRoot;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        } finally {
            try {
                if (reader != null) reader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error closing streams in root check", e);
            }
        }
    }

    private boolean setSystemProp(String prop, String value) {
        if (!isRootAvailable()) {
            Log.w(TAG, "Root not available, cannot set: " + prop);
            return false;
        }
        
        Process process = null;
        DataOutputStream os = null;
        BufferedReader reader = null;
        Process verifyProcess = null;
        DataOutputStream verifyOs = null;
        
        try {
            Log.d(TAG, "Setting property: " + prop + " = " + value);
            
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("setprop " + prop + " \"" + value + "\"\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.e(TAG, "setprop failed with exit code: " + exitCode);
                return false;
            }
            
            Thread.sleep(200); // FIXED: 150ms -> 200ms
            
            verifyProcess = Runtime.getRuntime().exec("su");
            verifyOs = new DataOutputStream(verifyProcess.getOutputStream());
            verifyOs.writeBytes("getprop " + prop + "\n");
            verifyOs.writeBytes("exit\n");
            verifyOs.flush();
            
            reader = new BufferedReader(new InputStreamReader(verifyProcess.getInputStream()));
            String actual = reader.readLine();
            if (actual != null) {
                actual = actual.trim();
            }
            
            verifyProcess.waitFor();
            
            boolean success;
            if (value == null || value.isEmpty()) {
                success = (actual == null || actual.isEmpty());
            } else {
                success = value.equals(actual);
            }
            
            Log.d(TAG, "Property " + prop + " verification: expected='" + value + 
                "', actual='" + actual + "', success=" + success);
            return success;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Property setting interrupted for " + prop, e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Exception setting property " + prop, e);
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (verifyOs != null) verifyOs.close();
                if (reader != null) reader.close();
                if (process != null) process.destroy();
                if (verifyProcess != null) verifyProcess.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    // ==================== DISPLAY FEATURES ====================
    
    public boolean isSmoothMotionEnabled() { 
        return mPrefs.getBoolean(PREF_SMOOTH_MOTION_ENABLED, false); 
    }
    
    public boolean setSmoothMotionEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_SMOOTH_MOTION_ENABLED, enabled).apply();
        boolean result = setSystemProp(PROP_SMOOTH_MOTION, enabled ? "1" : "0");
        Log.d(TAG, "setSmoothMotionEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isOptimizeRefreshEnabled() { 
        return mPrefs.getBoolean(PREF_OPTIMIZE_REFRESH_ENABLED, false); 
    }
    
    public boolean setOptimizeRefreshEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_OPTIMIZE_REFRESH_ENABLED, enabled).apply();
        boolean result = setSystemProp(PROP_OPTIMIZE_REFRESH, enabled ? "1" : "0");
        Log.d(TAG, "setOptimizeRefreshEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isPeakRefreshRateEnabled() {
        return mPrefs.getBoolean(PREF_PEAK_REFRESH_RATE, false);
    }
    
    public boolean setPeakRefreshRateEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_PEAK_REFRESH_RATE, enabled).apply();
        // FIXED: 500ms -> 200ms (factory value)
        // FIXED: idle timer also set
        boolean result1 = setSystemProp(PROP_PEAK_REFRESH_RATE, enabled ? "200" : "0");
        boolean result2 = setSystemProp(PROP_IDLE_TIMER, enabled ? "1100" : "0");
        boolean result = result1 && result2;
        Log.d(TAG, "setPeakRefreshRateEnabled(" + enabled + ") = " + result);
        return result;
    }

    // ==================== PERFORMANCE OPTIMIZATIONS ====================

    public boolean isForceVulkanEnabled() { 
        return mPrefs.getBoolean(PREF_FORCE_VULKAN_ENABLED, false); 
    }
    
    public boolean setForceVulkanEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_FORCE_VULKAN_ENABLED, enabled).apply();
        // FIXED: ro.hwui.use_vulkan usage (per build.prop)
        boolean result = setSystemProp(PROP_HWUI_USE_VULKAN, enabled ? "true" : "");
        Log.d(TAG, "setForceVulkanEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isPurgeableAssetsEnabled() { 
        return mPrefs.getBoolean(PREF_PURGEABLE_ASSETS_ENABLED, false); 
    }
    
    public boolean setPurgeableAssetsEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_PURGEABLE_ASSETS_ENABLED, enabled).apply();
        boolean result = setSystemProp(PROP_PURGEABLE_ASSETS, enabled ? "1" : "0");
        Log.d(TAG, "setPurgeableAssetsEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isGfxAccelEnabled() { 
        return mPrefs.getBoolean(PREF_GFX_ACCEL_ENABLED, true); 
    }
    
    public boolean setGfxAccelEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_GFX_ACCEL_ENABLED, enabled).apply();
        // Note: this property is inverted (avoid = disable)
        boolean result = setSystemProp(PROP_GFX_ACCEL, enabled ? "false" : "true");
        Log.d(TAG, "setGfxAccelEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isAdpfCpuHintEnabled() { 
        return mPrefs.getBoolean(PREF_ADPF_CPU_HINT_ENABLED, true); 
    }
    
    public boolean setAdpfCpuHintEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_ADPF_CPU_HINT_ENABLED, enabled).apply();
        boolean result = setSystemProp(PROP_ADPF_CPU_HINT, enabled ? "true" : "false");
        Log.d(TAG, "setAdpfCpuHintEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isGpuSchedulingEnabled() {
        return mPrefs.getBoolean(PREF_GPU_SCHEDULING, false);
    }
    
    public boolean setGpuSchedulingEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_GPU_SCHEDULING, enabled).apply();
        boolean result = setSystemProp(PROP_GPU_SCHEDULING, enabled ? "1" : "0");
        Log.d(TAG, "setGpuSchedulingEnabled(" + enabled + ") = " + result);
        return result;
    }

    // ==================== RENDERER SETTINGS ====================

    public boolean isSkiaGLRendererEnabled() { 
        return mPrefs.getBoolean(PREF_SKIAGL_RENDERER_ENABLED, false); 
    }
    
    public boolean setSkiaGLRendererEnabled(boolean enabled) {
        if (enabled) {
            mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, false).apply();
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "skiagl");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAGL_RENDERER_ENABLED, true).apply();
            }
            Log.d(TAG, "setSkiaGLRendererEnabled(true) = " + success);
            return success;
        } else {
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAGL_RENDERER_ENABLED, false).apply();
            }
            Log.d(TAG, "setSkiaGLRendererEnabled(false) = " + success);
            return success;
        }
    }

    public boolean isSkiaVKRendererEnabled() { 
        return mPrefs.getBoolean(PREF_SKIAVK_RENDERER_ENABLED, false); 
    }
    
    public boolean setSkiaVKRendererEnabled(boolean enabled) {
        if (enabled) {
            mPrefs.edit().putBoolean(PREF_SKIAGL_RENDERER_ENABLED, false).apply();
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "skiavk");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, true).apply();
            }
            Log.d(TAG, "setSkiaVKRendererEnabled(true) = " + success);
            return success;
        } else {
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, false).apply();
            }
            Log.d(TAG, "setSkiaVKRendererEnabled(false) = " + success);
            return success;
        }
    }

    // ==================== ADVANCED FEATURES ====================

    public boolean isHwuiForceGpuEnabled() {
        return mPrefs.getBoolean(PREF_HWUI_FORCE_GPU, false);
    }
    
    public boolean setHwuiForceGpuEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_HWUI_FORCE_GPU, enabled).apply();
        boolean result = setSystemProp(PROP_HWUI_FORCE_GPU, enabled ? "1" : "0");
        Log.d(TAG, "setHwuiForceGpuEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isHwuiDisableVsyncEnabled() {
        return mPrefs.getBoolean(PREF_HWUI_DISABLE_VSYNC, false);
    }
    
    public boolean setHwuiDisableVsyncEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_HWUI_DISABLE_VSYNC, enabled).apply();
        // WARNING: This can cause screen tearing!
        boolean result = setSystemProp(PROP_HWUI_DISABLE_VSYNC, enabled ? "true" : "false");
        Log.d(TAG, "setHwuiDisableVsyncEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isDisableScalerEnabled() {
        return mPrefs.getBoolean(PREF_DISABLE_SCALER, false);
    }
    
    public boolean setDisableScalerEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_DISABLE_SCALER, enabled).apply();
        boolean result = setSystemProp(PROP_DISABLE_SCALER, enabled ? "1" : "0");
        Log.d(TAG, "setDisableScalerEnabled(" + enabled + ") = " + result);
        return result;
    }

    public boolean isMediaCodecHwEnabled() {
        return mPrefs.getBoolean(PREF_MEDIA_CODEC_HW, true);
    }
    
    public boolean setMediaCodecHwEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_MEDIA_CODEC_HW, enabled).apply();
        // 0x1f = all hardware codecs enabled, 0x0 = software only
        boolean result = setSystemProp(PROP_MEDIA_CODEC_HW, enabled ? "0x1f" : "0x0");
        Log.d(TAG, "setMediaCodecHwEnabled(" + enabled + ") = " + result);
        return result;
    }

    // ==================== BOOT APPLY ====================

    public void applyOnBoot() {
        if (!isRootAvailable()) {
            Log.w(TAG, "Root not available, skipping boot apply");
            return;
        }
        
        Log.i(TAG, "=== Applying Video Enhancer settings on boot ===");
        
        // NEW: Essential frame pacing settings (per build.prop)
        setSystemProp(PROP_DISABLE_CLIENT_COMP_CACHE, "1");
        setSystemProp(PROP_ENABLE_GL_BACKPRESSURE, "1");
        
        // Display Features
        if (isSmoothMotionEnabled()) {
            Log.d(TAG, "Restoring Smooth Motion: enabled");
            setSmoothMotionEnabled(true);
        }
        if (isOptimizeRefreshEnabled()) {
            Log.d(TAG, "Restoring Optimize Refresh: enabled");
            setOptimizeRefreshEnabled(true);
        }
        if (isPeakRefreshRateEnabled()) {
            Log.d(TAG, "Restoring Peak Refresh Rate: enabled");
            setPeakRefreshRateEnabled(true);
        }
        
        // Performance Optimizations
        if (isForceVulkanEnabled()) {
            Log.d(TAG, "Restoring Force Vulkan: enabled");
            setForceVulkanEnabled(true);
        }
        if (isPurgeableAssetsEnabled()) {
            Log.d(TAG, "Restoring Purgeable Assets: enabled");
            setPurgeableAssetsEnabled(true);
        }
        if (isGfxAccelEnabled()) {
            Log.d(TAG, "Restoring GFX Acceleration: enabled");
            setGfxAccelEnabled(true);
        } else {
            Log.d(TAG, "Restoring GFX Acceleration: disabled");
            setGfxAccelEnabled(false);
        }
        if (isAdpfCpuHintEnabled()) {
            Log.d(TAG, "Restoring ADPF CPU Hint: enabled");
            setAdpfCpuHintEnabled(true);
        } else {
            Log.d(TAG, "Restoring ADPF CPU Hint: disabled");
            setAdpfCpuHintEnabled(false);
        }
        if (isGpuSchedulingEnabled()) {
            Log.d(TAG, "Restoring GPU Scheduling: enabled");
            setGpuSchedulingEnabled(true);
        }
        
        // Renderer Settings
        if (isSkiaGLRendererEnabled()) {
            Log.d(TAG, "Restoring SkiaGL Renderer: enabled");
            setSkiaGLRendererEnabled(true);
        }
        if (isSkiaVKRendererEnabled()) {
            Log.d(TAG, "Restoring SkiaVK Renderer: enabled");
            setSkiaVKRendererEnabled(true);
        }
        
        // Advanced Features
        if (isHwuiForceGpuEnabled()) {
            Log.d(TAG, "Restoring HWUI Force GPU: enabled");
            setHwuiForceGpuEnabled(true);
        }
        if (isHwuiDisableVsyncEnabled()) {
            Log.d(TAG, "Restoring HWUI Disable VSync: enabled");
            setHwuiDisableVsyncEnabled(true);
        }
        if (isDisableScalerEnabled()) {
            Log.d(TAG, "Restoring Disable Scaler: enabled");
            setDisableScalerEnabled(true);
        }
        if (isMediaCodecHwEnabled()) {
            Log.d(TAG, "Restoring Media Codec HW: enabled");
            setMediaCodecHwEnabled(true);
        } else {
            Log.d(TAG, "Restoring Media Codec HW: disabled");
            setMediaCodecHwEnabled(false);
        }
        
        Log.i(TAG, "=== Boot apply completed ===");
    }

    // ==================== RESET TO DEFAULTS ====================

    public void resetToDefaults() {
        Log.i(TAG, "Resetting all Video Enhancer settings to defaults");
        
        SharedPreferences.Editor editor = mPrefs.edit();
        
        // Display Features - OFF by default (factory settings)
        editor.putBoolean(PREF_SMOOTH_MOTION_ENABLED, false);
        editor.putBoolean(PREF_OPTIMIZE_REFRESH_ENABLED, false);
        editor.putBoolean(PREF_PEAK_REFRESH_RATE, false);
        
        // Performance - Conservative defaults
        editor.putBoolean(PREF_FORCE_VULKAN_ENABLED, false);
        editor.putBoolean(PREF_PURGEABLE_ASSETS_ENABLED, false);
        editor.putBoolean(PREF_GFX_ACCEL_ENABLED, true);
        editor.putBoolean(PREF_ADPF_CPU_HINT_ENABLED, true);
        editor.putBoolean(PREF_GPU_SCHEDULING, false);
        
        // Renderer - No custom renderer
        editor.putBoolean(PREF_SKIAGL_RENDERER_ENABLED, false);
        editor.putBoolean(PREF_SKIAVK_RENDERER_ENABLED, false);
        
        // Advanced - OFF by default (VSync ALWAYS enabled!)
        editor.putBoolean(PREF_HWUI_FORCE_GPU, false);
        editor.putBoolean(PREF_HWUI_DISABLE_VSYNC, false); // VSync should NEVER be disabled by default!
        editor.putBoolean(PREF_DISABLE_SCALER, false);
        editor.putBoolean(PREF_MEDIA_CODEC_HW, true);
        
        editor.apply();
        
        // Apply default settings
        setSmoothMotionEnabled(false);
        setOptimizeRefreshEnabled(false);
        setPeakRefreshRateEnabled(false);
        setForceVulkanEnabled(false);
        setPurgeableAssetsEnabled(false);
        setGfxAccelEnabled(true);
        setAdpfCpuHintEnabled(true);
        setGpuSchedulingEnabled(false);
        setSkiaGLRendererEnabled(false);
        setSkiaVKRendererEnabled(false);
        setHwuiForceGpuEnabled(false);
        setHwuiDisableVsyncEnabled(false);
        setDisableScalerEnabled(false);
        setMediaCodecHwEnabled(true);
        
        // NEW: Restore factory frame pacing settings
        setSystemProp(PROP_DISABLE_CLIENT_COMP_CACHE, "1");
        setSystemProp(PROP_ENABLE_GL_BACKPRESSURE, "1");
        
        Log.i(TAG, "Reset to defaults completed");
    }
}
