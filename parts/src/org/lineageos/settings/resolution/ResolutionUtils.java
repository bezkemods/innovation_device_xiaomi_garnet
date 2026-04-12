/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.resolution;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.os.UserHandle;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import androidx.preference.PreferenceManager;

/**
 * Core utility class for per-app and system-wide resolution/density switching.
 *
 * Supported states:
 *   0 = Default  (native panel resolution)
 *   1 = 480p     (854×1904, ~178 dpi)
 *   2 = 540p     (960×2133, ~200 dpi)
 *   3 = 720p     (1280×2844, ~266 dpi)
 *   4 = Custom   (user-defined width, height, density)
 *
 * Device baseline — Redmi Note 13 Pro 5G (Garnet / SM7435):
 *   Physical AMOLED panel : 1220 × 2712 px  (as reported by Display.Mode on LineageOS)
 *   Initial display density: 395 dpi
 *
 * Note: getPhysicalWidth()/getPhysicalHeight() on Garnet returns 1220×2712, NOT 1080×2400.
 * The 1080×2400 figure is the logical/scaled default mode. We hard-code the physical values
 * as a fallback so density scaling stays correct even when the system already has a forced
 * size applied at query time.
 */
public final class ResolutionUtils {

    private static final String TAG = "ResolutionUtils";

    // -------------------------------------------------------------------------
    // SharedPreferences keys
    // -------------------------------------------------------------------------
    private static final String RESOLUTION_CONTROL      = "resolutioncontrol";
    private static final String RESOLUTION_480P         = "resolution.480p";
    private static final String RESOLUTION_540P         = "resolution.540p";
    private static final String RESOLUTION_720P         = "resolution.720p";
    private static final String RESOLUTION_CUSTOM_PKG   = "resolution.custom";   // per-app custom

    private static final String RESOLUTION_GLOBAL_STATE = "resolution.global_state";

    // Custom resolution fields (global + per-app)
    static final String KEY_CUSTOM_WIDTH   = "resolution.custom_width";
    static final String KEY_CUSTOM_HEIGHT  = "resolution.custom_height";
    static final String KEY_CUSTOM_DENSITY = "resolution.custom_density";

    // Per-app custom override (map: packageName → "WxHxD")
    private static final String KEY_CUSTOM_APP_PREFIX = "resolution.app_custom.";

    // -------------------------------------------------------------------------
    // States
    // -------------------------------------------------------------------------
    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_480P    = 1;
    protected static final int STATE_540P    = 2;
    protected static final int STATE_720P    = 3;
    protected static final int STATE_CUSTOM  = 4;

    protected static boolean isAppInList = false;

    private int mCurrentState     = STATE_DEFAULT;
    private int mSavedUserDensity = -1;

    // -------------------------------------------------------------------------
    // Garnet (SM7435 / Redmi Note 13 Pro 5G) hard-coded panel baseline
    // Physical AMOLED panel as reported by Display.Mode on LineageOS Garnet builds.
    // -------------------------------------------------------------------------
    private static final int GARNET_STOCK_WIDTH   = 1220;
    private static final int GARNET_STOCK_HEIGHT  = 2712;
    private static final int GARNET_STOCK_DENSITY = 395;

    // -------------------------------------------------------------------------
    // Internal config holder
    // -------------------------------------------------------------------------
    private static class ResolutionConfig {
        final int width;
        final int height;
        final int density;

        ResolutionConfig(int w, int h, int d) {
            width   = w;
            height  = h;
            density = d;
        }
    }

    // Index 0=default, 1=480p, 2=540p, 3=720p, 4=custom (populated lazily)
    private final ResolutionConfig[] mConfigs = new ResolutionConfig[5];

    private final SharedPreferences mSharedPrefs;
    private final Context           mContext;

    private int mStockWidth;
    private int mStockHeight;
    private int mInitialDensity;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    protected ResolutionUtils(Context context) {
        mContext     = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        initializeStockBaselines();
        calculateResolutionConfigs();
    }

    // -------------------------------------------------------------------------
    // Service entry point
    // -------------------------------------------------------------------------
    public static void startService(Context context) {
        new ResolutionUtils(context).applyBaselineFromGlobal();
        context.startServiceAsUser(
                new Intent(context, ResolutionService.class), UserHandle.CURRENT);
    }

    // -------------------------------------------------------------------------
    // Baseline initialisation
    // -------------------------------------------------------------------------
    private void initializeStockBaselines() {
        try {
            DisplayManager dm = mContext.getSystemService(DisplayManager.class);
            Display        d  = dm.getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode   m  = d.getMode();
            int pw = m.getPhysicalWidth();
            int ph = m.getPhysicalHeight();

            // Accept physical values only if they are at least as large as Garnet's
            // native panel; a forced-size mode may report smaller values.
            if (pw >= GARNET_STOCK_WIDTH && ph >= GARNET_STOCK_HEIGHT) {
                mStockWidth  = pw;
                mStockHeight = ph;
            } else {
                mStockWidth  = GARNET_STOCK_WIDTH;
                mStockHeight = GARNET_STOCK_HEIGHT;
            }
        } catch (Exception e) {
            mStockWidth  = GARNET_STOCK_WIDTH;
            mStockHeight = GARNET_STOCK_HEIGHT;
        }

        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            int d = wm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY);
            mInitialDensity = (d >= 280 && d <= 640) ? d : GARNET_STOCK_DENSITY;
        } catch (RemoteException e) {
            mInitialDensity = GARNET_STOCK_DENSITY;
        }
    }

    private void calculateResolutionConfigs() {
        mConfigs[STATE_DEFAULT] = new ResolutionConfig(mStockWidth, mStockHeight, mInitialDensity);
        mConfigs[STATE_480P]    = makeConfig(480);
        mConfigs[STATE_540P]    = makeConfig(540);
        mConfigs[STATE_720P]    = makeConfig(720);
        mConfigs[STATE_CUSTOM]  = loadCustomConfig();
    }

    private ResolutionConfig makeConfig(int targetWidth) {
        float scale   = (float) targetWidth / (float) mStockWidth;
        int   height  = Math.max(1,   Math.round(mStockHeight   * scale));
        int   density = Math.max(120, Math.round(mInitialDensity * scale));
        return new ResolutionConfig(targetWidth, height, density);
    }

    /** Loads the user-saved custom resolution from SharedPreferences. */
    private ResolutionConfig loadCustomConfig() {
        int w = mSharedPrefs.getInt(KEY_CUSTOM_WIDTH,   mStockWidth);
        int h = mSharedPrefs.getInt(KEY_CUSTOM_HEIGHT,  mStockHeight);
        int d = mSharedPrefs.getInt(KEY_CUSTOM_DENSITY, mInitialDensity);
        return new ResolutionConfig(w, h, d);
    }

    // -------------------------------------------------------------------------
    // Custom resolution management
    // -------------------------------------------------------------------------

    /** Returns the globally-saved custom resolution as an int[3]: {width, height, density}. */
    public int[] getCustomConfig() {
        return new int[]{
                mSharedPrefs.getInt(KEY_CUSTOM_WIDTH,   mStockWidth),
                mSharedPrefs.getInt(KEY_CUSTOM_HEIGHT,  mStockHeight),
                mSharedPrefs.getInt(KEY_CUSTOM_DENSITY, mInitialDensity)
        };
    }

    /**
     * Saves and immediately applies a new custom resolution.
     * Clamps width/height to sane bounds (240…mStockWidth / mStockHeight).
     * Density is clamped to 80…640.
     */
    public void setCustomConfig(int width, int height, int density) {
        width   = clamp(width,   240, mStockWidth);
        height  = clamp(height,  320, mStockHeight);
        density = clamp(density,  80, 640);

        mSharedPrefs.edit()
                .putInt(KEY_CUSTOM_WIDTH,   width)
                .putInt(KEY_CUSTOM_HEIGHT,  height)
                .putInt(KEY_CUSTOM_DENSITY, density)
                .apply();

        mConfigs[STATE_CUSTOM] = new ResolutionConfig(width, height, density);

        // If currently in custom state, apply immediately
        if (getGlobalState() == STATE_CUSTOM) {
            mCurrentState = STATE_DEFAULT; // force re-apply
            applyBaselineFromGlobal();
        }
    }

    /** Per-app custom config: saves a {WxHxD} string for a package. */
    public void setAppCustomConfig(String packageName, int width, int height, int density) {
        mSharedPrefs.edit()
                .putString(KEY_CUSTOM_APP_PREFIX + packageName,
                        width + "x" + height + "x" + density)
                .apply();
    }

    /** Returns per-app custom config, or null if not set. */
    public int[] getAppCustomConfig(String packageName) {
        String s = mSharedPrefs.getString(KEY_CUSTOM_APP_PREFIX + packageName, null);
        if (s == null) return null;
        String[] parts = s.split("x");
        if (parts.length < 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Removes per-app custom config. */
    public void clearAppCustomConfig(String packageName) {
        mSharedPrefs.edit().remove(KEY_CUSTOM_APP_PREFIX + packageName).apply();
    }

    // -------------------------------------------------------------------------
    // System-wide (global) baseline
    // -------------------------------------------------------------------------
    public int getGlobalState() {
        return mSharedPrefs.getInt(RESOLUTION_GLOBAL_STATE, STATE_DEFAULT);
    }

    public void setGlobalState(int state) {
        if (state < STATE_DEFAULT || state > STATE_CUSTOM) state = STATE_DEFAULT;
        mSharedPrefs.edit().putInt(RESOLUTION_GLOBAL_STATE, state).apply();
        mCurrentState = -1; // force re-apply
        applyBaselineFromGlobal();
    }

    public void applyBaselineFromGlobal() {
        int s = getGlobalState();
        if (s == STATE_CUSTOM) {
            mConfigs[STATE_CUSTOM] = loadCustomConfig();
        }
        applyResolution(mConfigs[s], s);
    }

    /** Alias kept for ResolutionService compatibility. */
    public void restoreDefaultResolution() {
        applyBaselineFromGlobal();
    }

    // -------------------------------------------------------------------------
    // Per-app list management
    // -------------------------------------------------------------------------

    /**
     * Storage: four semicolon-separated buckets (480p;540p;720p;custom),
     * each containing comma-separated package names with a trailing comma.
     */
    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(RESOLUTION_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(RESOLUTION_CONTROL, null);
        if (value == null || value.isEmpty()) {
            value = RESOLUTION_480P + ";" + RESOLUTION_540P + ";"
                  + RESOLUTION_720P + ";" + RESOLUTION_CUSTOM_PKG;
            writeValue(value);
            return value;
        }
        String[] modes = value.split(";", -1);
        // Migrate: if only 3 buckets, add empty custom bucket
        if (modes.length < 4) {
            String fixed = (modes.length > 0 ? modes[0] : RESOLUTION_480P) + ";"
                         + (modes.length > 1 ? modes[1] : RESOLUTION_540P) + ";"
                         + (modes.length > 2 ? modes[2] : RESOLUTION_720P) + ";"
                         + RESOLUTION_CUSTOM_PKG;
            writeValue(fixed);
            return fixed;
        }
        return value;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        // Remove from every bucket first
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(";", -1);
        while (modes.length < 4) {
            value += ";";
            modes = value.split(";", -1);
        }

        switch (mode) {
            case STATE_480P:   modes[0] += packageName + ","; break;
            case STATE_540P:   modes[1] += packageName + ","; break;
            case STATE_720P:   modes[2] += packageName + ","; break;
            case STATE_CUSTOM: modes[3] += packageName + ","; break;
            default: break; // STATE_DEFAULT → just removed
        }
        writeValue(modes[0] + ";" + modes[1] + ";" + modes[2] + ";" + modes[3]);
    }

    protected int getStateForPackage(String packageName) {
        String[] modes = getValue().split(";", -1);
        if (modes.length > 0 && modes[0].contains(packageName + ",")) return STATE_480P;
        if (modes.length > 1 && modes[1].contains(packageName + ",")) return STATE_540P;
        if (modes.length > 2 && modes[2].contains(packageName + ",")) return STATE_720P;
        if (modes.length > 3 && modes[3].contains(packageName + ",")) return STATE_CUSTOM;
        return STATE_DEFAULT;
    }

    /** Called by ResolutionService on every foreground app change. */
    protected void setResolution(String packageName) {
        String[] modes     = getValue().split(";", -1);
        int      globalState = getGlobalState();
        int      newState    = globalState;
        ResolutionConfig cfg = mConfigs[globalState];

        isAppInList = false;

        if (modes.length > 0 && modes[0].contains(packageName + ",")) {
            newState    = STATE_480P;
            cfg         = mConfigs[STATE_480P];
            isAppInList = true;
        } else if (modes.length > 1 && modes[1].contains(packageName + ",")) {
            newState    = STATE_540P;
            cfg         = mConfigs[STATE_540P];
            isAppInList = true;
        } else if (modes.length > 2 && modes[2].contains(packageName + ",")) {
            newState    = STATE_720P;
            cfg         = mConfigs[STATE_720P];
            isAppInList = true;
        } else if (modes.length > 3 && modes[3].contains(packageName + ",")) {
            newState    = STATE_CUSTOM;
            isAppInList = true;
            // Use per-app custom if set, otherwise fall back to global custom
            int[] appCustom = getAppCustomConfig(packageName);
            cfg = (appCustom != null)
                    ? new ResolutionConfig(appCustom[0], appCustom[1], appCustom[2])
                    : loadCustomConfig();
        }

        applyResolution(cfg, newState);
    }

    // -------------------------------------------------------------------------
    // Low-level WM application
    // -------------------------------------------------------------------------
    private void applyResolution(ResolutionConfig cfg, int newState) {
        if (newState == mCurrentState) return;

        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            if (newState != STATE_DEFAULT) {
                if (mCurrentState == STATE_DEFAULT) {
                    mSavedUserDensity = wm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
                }
                wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, cfg.width, cfg.height);
                wm.setForcedDisplayDensityForUser(
                        Display.DEFAULT_DISPLAY, cfg.density, UserHandle.USER_CURRENT);
            } else {
                wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
                if (mSavedUserDensity != -1) {
                    wm.setForcedDisplayDensityForUser(
                            Display.DEFAULT_DISPLAY, mSavedUserDensity, UserHandle.USER_CURRENT);
                    mSavedUserDensity = -1;
                } else {
                    wm.clearForcedDisplayDensityForUser(
                            Display.DEFAULT_DISPLAY, UserHandle.USER_CURRENT);
                }
            }
            mCurrentState = newState;
        } catch (RemoteException e) {
            // WM not available (early boot) — swallow
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Human-readable resolution string for the given state. */
    public String getResolutionString(int state) {
        ResolutionConfig c = resolvedConfig(state);
        return c.width + "×" + c.height;
    }

    /** Full description string including density. */
    public String getResolutionDetail(int state) {
        ResolutionConfig c = resolvedConfig(state);
        return c.width + "×" + c.height + " @ " + c.density + " dpi";
    }

    public int getNativeWidth()   { return mStockWidth;   }
    public int getNativeHeight()  { return mStockHeight;  }
    public int getNativeDensity() { return mInitialDensity; }

    private ResolutionConfig resolvedConfig(int state) {
        if (state == STATE_CUSTOM) {
            mConfigs[STATE_CUSTOM] = loadCustomConfig();
        }
        if (state >= 0 && state < mConfigs.length && mConfigs[state] != null) {
            return mConfigs[state];
        }
        return mConfigs[STATE_DEFAULT];
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
