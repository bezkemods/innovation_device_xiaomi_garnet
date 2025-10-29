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

    // Preference keys
    private static final String PREF_SMOOTH_MOTION_ENABLED = "smooth_motion_enabled";
    private static final String PREF_OPTIMIZE_REFRESH_ENABLED = "optimize_refresh_enabled";
    private static final String PREF_FORCE_VULKAN_ENABLED = "force_vulkan_enabled";
    private static final String PREF_PURGEABLE_ASSETS_ENABLED = "purgeable_assets_enabled";
    private static final String PREF_GFX_ACCEL_ENABLED = "gfx_accel_enabled";
    private static final String PREF_ADPF_CPU_HINT_ENABLED = "adpf_cpu_hint_enabled";
    private static final String PREF_SKIAGL_RENDERER_ENABLED = "skiagl_renderer_enabled";
    private static final String PREF_SKIAVK_RENDERER_ENABLED = "skiavk_renderer_enabled";

    // System properties
    private static final String PROP_SMOOTH_MOTION = "vendor.display.use_smooth_motion";
    private static final String PROP_OPTIMIZE_REFRESH = "vendor.display.enable_optimize_refresh";
    private static final String PROP_FORCE_VULKAN = "persist.sys.force_vulkan";
    private static final String PROP_PURGEABLE_ASSETS = "persist.sys.purgeable_assets";
    private static final String PROP_GFX_ACCEL = "ro.config.avoid_gfx_accel";
    private static final String PROP_ADPF_CPU_HINT = "debug.sf.enable_adpf_cpu_hint";
    private static final String PROP_HWUI_RENDERER = "debug.hwui.renderer";

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public VideoEnhancerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Log.d(TAG, "VideoEnhancerUtils initialized");
    }

    // Root check - JAVÍTOTT: KernelSU támogatás
    public boolean isRootAvailable() {
        try {
            // Próbáljuk először az 'su' parancsot
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output = reader.readLine();
            reader.close();
            int exit = p.waitFor();
            p.destroy();
            
            boolean hasRoot = exit == 0 && output != null && output.contains("uid=0");
            Log.d(TAG, "Root check: " + (hasRoot ? "available" : "not available") + " (output: " + output + ")");
            return hasRoot;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }

    // Generic setprop with root + verify - JAVÍTOTT: Jobb hibakezelés és logging
    private boolean setSystemProp(String prop, String value) {
        if (!isRootAvailable()) {
            Log.w(TAG, "Root not available, cannot set: " + prop);
            return false;
        }

        Process process = null;
        DataOutputStream os = null;
        BufferedReader reader = null;

        try {
            Log.d(TAG, "Setting property: " + prop + " = " + value);

            // Setprop parancs végrehajtása
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

            // Ellenőrzés: tényleg beállítódott-e
            Thread.sleep(100); // Rövid várakozás a rendszer frissítéséhez
            
            Process verifyProcess = Runtime.getRuntime().exec("su");
            DataOutputStream verifyOs = new DataOutputStream(verifyProcess.getOutputStream());
            verifyOs.writeBytes("getprop " + prop + "\n");
            verifyOs.writeBytes("exit\n");
            verifyOs.flush();

            reader = new BufferedReader(new InputStreamReader(verifyProcess.getInputStream()));
            String actual = reader.readLine();
            if (actual != null) {
                actual = actual.trim();
            }
            reader.close();
            verifyProcess.waitFor();

            // Ellenőrzés
            boolean success = false;
            if (value == null || value.isEmpty()) {
                success = (actual == null || actual.isEmpty());
            } else {
                success = value.equals(actual);
            }

            Log.d(TAG, "Property " + prop + " verification: expected='" + value + "', actual='" + actual + "', success=" + success);
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Exception setting property " + prop, e);
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (reader != null) reader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    // === GETTERS & SETTERS - Változatlan, de a preference kulcsokat mindig mentjük ===

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

    public boolean isForceVulkanEnabled() { 
        return mPrefs.getBoolean(PREF_FORCE_VULKAN_ENABLED, false); 
    }
    
    public boolean setForceVulkanEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_FORCE_VULKAN_ENABLED, enabled).apply();
        boolean result = setSystemProp(PROP_FORCE_VULKAN, enabled ? "1" : "0");
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
        // FIGYELEM: Itt invertálva van a logika!
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

    public boolean isSkiaGLRendererEnabled() { 
        return mPrefs.getBoolean(PREF_SKIAGL_RENDERER_ENABLED, false); 
    }
    
    public boolean setSkiaGLRendererEnabled(boolean enabled) {
        if (enabled) {
            // GL bekapcsolása esetén kikapcsoljuk a VK-t
            mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, false).apply();
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "skiagl");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAGL_RENDERER_ENABLED, true).apply();
            }
            Log.d(TAG, "setSkiaGLRendererEnabled(true) = " + success);
            return success;
        } else {
            // GL kikapcsolása - üres értékre állítjuk
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
            // VK bekapcsolása esetén kikapcsoljuk a GL-t
            mPrefs.edit().putBoolean(PREF_SKIAGL_RENDERER_ENABLED, false).apply();
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "skiavk");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, true).apply();
            }
            Log.d(TAG, "setSkiaVKRendererEnabled(true) = " + success);
            return success;
        } else {
            // VK kikapcsolása - üres értékre állítjuk
            boolean success = setSystemProp(PROP_HWUI_RENDERER, "");
            if (success) {
                mPrefs.edit().putBoolean(PREF_SKIAVK_RENDERER_ENABLED, false).apply();
            }
            Log.d(TAG, "setSkiaVKRendererEnabled(false) = " + success);
            return success;
        }
    }

    // Apply all on boot - JAVÍTOTT: részletesebb logging
    public void applyOnBoot() {
        if (!isRootAvailable()) {
            Log.w(TAG, "Root not available, skipping boot apply");
            return;
        }
        
        Log.i(TAG, "=== Applying Video Enhancer settings on boot ===");

        // Alkalmazzuk az összes mentett beállítást
        if (isSmoothMotionEnabled()) {
            Log.d(TAG, "Restoring Smooth Motion: enabled");
            setSmoothMotionEnabled(true);
        }
        
        if (isOptimizeRefreshEnabled()) {
            Log.d(TAG, "Restoring Optimize Refresh: enabled");
            setOptimizeRefreshEnabled(true);
        }
        
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
        
        if (isSkiaGLRendererEnabled()) {
            Log.d(TAG, "Restoring SkiaGL Renderer: enabled");
            setSkiaGLRendererEnabled(true);
        }
        
        if (isSkiaVKRendererEnabled()) {
            Log.d(TAG, "Restoring SkiaVK Renderer: enabled");
            setSkiaVKRendererEnabled(true);
        }

        Log.i(TAG, "=== Boot apply completed ===");
    }
}
