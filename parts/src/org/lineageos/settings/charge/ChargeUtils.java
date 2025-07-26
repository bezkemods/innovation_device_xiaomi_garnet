/*
 * Copyright (C) 2025 The LineageOS Project
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

package org.lineageos.settings.charge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public class ChargeUtils {

    private static final String TAG = "ChargeUtils";
    
    // Primary node path
    public static final String BYPASS_CHARGE_NODE = "/sys/class/qcom-battery/input_suspend";
    
    // Alternative node paths for different devices
    private static final String[] ALTERNATIVE_NODES = {
        "/sys/class/power_supply/battery/input_suspend",
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/usb/charging_enabled",
        "/sys/devices/platform/soc/soc:qcom,pmic_glink/soc:qcom,pmic_glink:qcom,battery_charger/power_supply/battery/input_suspend"
    };
    
    private static final String PREF_BYPASS_CHARGE = "bypass_charge";
    private static final String PREF_BYPASS_NODE_PATH = "bypass_charge_node_path";

    // Bypass modes
    public static final int BYPASS_DISABLED = 0;
    public static final int BYPASS_ENABLED = 1;

    private SharedPreferences mSharedPrefs;
    private Context mContext;
    private String mActivNode;
    private Handler mHandler;

    public ChargeUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mHandler = new Handler(Looper.getMainLooper());
        mActivNode = findWorkingNode();
    }

    /**
     * Find the working node path for bypass charging
     */
    private String findWorkingNode() {
        // Check if we have a saved working node
        String savedNode = mSharedPrefs.getString(PREF_BYPASS_NODE_PATH, null);
        if (savedNode != null && isNodeAccessible(savedNode)) {
            Log.d(TAG, "Using saved node: " + savedNode);
            return savedNode;
        }

        // Try primary node first
        if (isNodeAccessible(BYPASS_CHARGE_NODE)) {
            saveWorkingNode(BYPASS_CHARGE_NODE);
            return BYPASS_CHARGE_NODE;
        }

        // Try alternative nodes
        for (String node : ALTERNATIVE_NODES) {
            if (isNodeAccessible(node)) {
                Log.d(TAG, "Found working alternative node: " + node);
                saveWorkingNode(node);
                return node;
            }
        }

        Log.w(TAG, "No working bypass charge node found");
        return null;
    }

    private void saveWorkingNode(String node) {
        mSharedPrefs.edit().putString(PREF_BYPASS_NODE_PATH, node).apply();
    }

    public boolean isBypassChargeEnabled() {
        if (mActivNode == null) {
            return false;
        }

        try {
            String value = FileUtils.readOneLine(mActivNode);
            if (value == null) {
                return false;
            }
            
            value = value.trim();
            // Handle different node value formats
            boolean enabled = "1".equals(value) || "true".equals(value.toLowerCase());
            
            Log.d(TAG, "Bypass charge status from node " + mActivNode + ": " + value + " (enabled: " + enabled + ")");
            return enabled;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bypass charge status from " + mActivNode, e);
            return false;
        }
    }

    public boolean enableBypassCharge(boolean enable) {
        if (mActivNode == null) {
            Log.e(TAG, "No active node available for bypass charging");
            return false;
        }

        try {
            String value = enable ? "1" : "0";
            boolean success = FileUtils.writeLine(mActivNode, value);
            
            if (success) {
                // Save preference
                mSharedPrefs.edit().putBoolean(PREF_BYPASS_CHARGE, enable).apply();
                
                // Verify the change took effect
                mHandler.postDelayed(() -> {
                    boolean actualState = isBypassChargeEnabled();
                    if (actualState != enable) {
                        Log.w(TAG, "Bypass charge state verification failed. Expected: " + enable + ", Actual: " + actualState);
                    } else {
                        Log.d(TAG, "Bypass charge successfully " + (enable ? "enabled" : "disabled"));
                    }
                }, 100);
                
                return true;
            } else {
                Log.e(TAG, "Failed to write bypass charge value to " + mActivNode);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write bypass charge status to " + mActivNode, e);
            return false;
        }
    }

    public boolean isNodeAccessible(String node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        
        try {
            // Test both read and write access
            String value = FileUtils.readOneLine(node);
            if (value == null) {
                return false;
            }
            
            // Test write access by writing the same value back
            boolean writeSuccess = FileUtils.writeLine(node, value.trim());
            
            Log.d(TAG, "Node " + node + " accessibility - read: " + (value != null) + ", write: " + writeSuccess);
            return writeSuccess;
        } catch (Exception e) {
            Log.d(TAG, "Node " + node + " not accessible: " + e.getMessage());
            return false;
        }
    }

    public boolean isBypassChargeSupported() {
        return mActivNode != null && isNodeAccessible(mActivNode);
    }

    public String getActiveNode() {
        return mActivNode;
    }

    /**
     * Get the saved preference value (useful for restoring state on boot)
     */
    public boolean getBypassChargePreference() {
        return mSharedPrefs.getBoolean(PREF_BYPASS_CHARGE, false);
    }

    /**
     * Force refresh of active node (useful if device state changes)
     */
    public void refreshActiveNode() {
        mActivNode = findWorkingNode();
    }

    /**
     * Get debug information about bypass charging
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Active node: ").append(mActivNode != null ? mActivNode : "None").append("\n");
        info.append("Bypass supported: ").append(isBypassChargeSupported()).append("\n");
        info.append("Current state: ").append(isBypassChargeEnabled()).append("\n");
        info.append("Preference value: ").append(getBypassChargePreference()).append("\n");
        
        info.append("Node accessibility:\n");
        info.append("  Primary: ").append(isNodeAccessible(BYPASS_CHARGE_NODE)).append("\n");
        for (String node : ALTERNATIVE_NODES) {
            info.append("  ").append(node).append(": ").append(isNodeAccessible(node)).append("\n");
        }
        
        return info.toString();
    }
}
