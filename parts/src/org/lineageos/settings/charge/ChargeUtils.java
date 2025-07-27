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

import java.io.File;

public class ChargeUtils {

    private static final String TAG = "ChargeUtils";
    
    // Node definitions with their expected behavior
    private static final NodeConfig[] NODE_CONFIGS = {
        // Primary QCOM battery node - 1 = suspend input (no charging), 0 = allow charging
        new NodeConfig("/sys/class/qcom-battery/input_suspend", true, "1", "0"),
        
        // Alternative battery input suspend - same logic
        new NodeConfig("/sys/class/power_supply/battery/input_suspend", true, "1", "0"),
        
        // Charging enabled nodes - 0 = disable charging, 1 = enable charging (inverted logic)
        new NodeConfig("/sys/class/power_supply/battery/charging_enabled", false, "0", "1"),
        new NodeConfig("/sys/class/power_supply/usb/charging_enabled", false, "0", "1"),
        
        // Long PMIC glink path
        new NodeConfig("/sys/devices/platform/soc/soc:qcom,pmic_glink/soc:qcom,pmic_glink:qcom,battery_charger/power_supply/battery/input_suspend", true, "1", "0"),
        
        // Additional common paths
        new NodeConfig("/sys/class/power_supply/main/charging_enabled", false, "0", "1"),
        new NodeConfig("/sys/class/power_supply/battery/battery_charging_enabled", false, "0", "1"),
        new NodeConfig("/sys/class/power_supply/bms/charging_enabled", false, "0", "1")
    };
    
    private static final String PREF_BYPASS_CHARGE = "bypass_charge";
    private static final String PREF_BYPASS_NODE_PATH = "bypass_charge_node_path";
    private static final String PREF_BYPASS_NODE_TYPE = "bypass_charge_node_type";

    private SharedPreferences mSharedPrefs;
    private Context mContext;
    private NodeConfig mActiveNodeConfig;
    private Handler mHandler;

    public ChargeUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mHandler = new Handler(Looper.getMainLooper());
        mActiveNodeConfig = findWorkingNode();
    }

    private static class NodeConfig {
        final String path;
        final boolean isSuspendType; // true for input_suspend nodes, false for charging_enabled nodes
        final String bypassValue;    // value to write when bypass is enabled
        final String normalValue;    // value to write when bypass is disabled
        
        NodeConfig(String path, boolean isSuspendType, String bypassValue, String normalValue) {
            this.path = path;
            this.isSuspendType = isSuspendType;
            this.bypassValue = bypassValue;
            this.normalValue = normalValue;
        }
    }

    /**
     * Find the working node configuration for bypass charging
     */
    private NodeConfig findWorkingNode() {
        // Check if we have a saved working node
        String savedPath = mSharedPrefs.getString(PREF_BYPASS_NODE_PATH, null);
        boolean savedType = mSharedPrefs.getBoolean(PREF_BYPASS_NODE_TYPE, true);
        
        if (savedPath != null) {
            for (NodeConfig config : NODE_CONFIGS) {
                if (config.path.equals(savedPath) && config.isSuspendType == savedType) {
                    if (isNodeAccessible(config)) {
                        Log.d(TAG, "Using saved node: " + savedPath + " (type: " + (config.isSuspendType ? "suspend" : "enable") + ")");
                        return config;
                    }
                    break;
                }
            }
        }

        // Try all node configurations
        for (NodeConfig config : NODE_CONFIGS) {
            if (isNodeAccessible(config)) {
                Log.d(TAG, "Found working node: " + config.path + " (type: " + (config.isSuspendType ? "suspend" : "enable") + ")");
                saveWorkingNode(config);
                return config;
            }
        }

        Log.w(TAG, "No working bypass charge node found");
        return null;
    }

    private void saveWorkingNode(NodeConfig config) {
        mSharedPrefs.edit()
                .putString(PREF_BYPASS_NODE_PATH, config.path)
                .putBoolean(PREF_BYPASS_NODE_TYPE, config.isSuspendType)
                .apply();
    }

    public boolean isBypassChargeEnabled() {
        if (mActiveNodeConfig == null) {
            return false;
        }

        try {
            String value = FileUtils.readOneLine(mActiveNodeConfig.path);
            if (value == null) {
                return false;
            }
            
            value = value.trim();
            boolean enabled = value.equals(mActiveNodeConfig.bypassValue);
            
            Log.d(TAG, "Bypass charge status from node " + mActiveNodeConfig.path + ": " + value + 
                  " (bypass value: " + mActiveNodeConfig.bypassValue + ", enabled: " + enabled + ")");
            return enabled;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bypass charge status from " + mActiveNodeConfig.path, e);
            return false;
        }
    }

    public boolean enableBypassCharge(boolean enable) {
        if (mActiveNodeConfig == null) {
            Log.e(TAG, "No active node available for bypass charging");
            return false;
        }

        try {
            String value = enable ? mActiveNodeConfig.bypassValue : mActiveNodeConfig.normalValue;
            Log.d(TAG, "Setting bypass charge to " + enable + " by writing '" + value + "' to " + mActiveNodeConfig.path);
            
            boolean success = FileUtils.writeLine(mActiveNodeConfig.path, value);
            
            if (success) {
                // Save preference
                mSharedPrefs.edit().putBoolean(PREF_BYPASS_CHARGE, enable).apply();
                
                // Verify the change took effect with multiple attempts
                mHandler.postDelayed(() -> verifyBypassState(enable, 0), 100);
                
                return true;
            } else {
                Log.e(TAG, "Failed to write bypass charge value to " + mActiveNodeConfig.path);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write bypass charge status to " + mActiveNodeConfig.path, e);
            return false;
        }
    }

    private void verifyBypassState(boolean expectedState, int attemptCount) {
        if (attemptCount >= 5) {
            Log.w(TAG, "Bypass charge state verification failed after 5 attempts");
            return;
        }
        
        boolean actualState = isBypassChargeEnabled();
        if (actualState != expectedState) {
            Log.w(TAG, "Bypass charge state verification failed (attempt " + (attemptCount + 1) + 
                  "). Expected: " + expectedState + ", Actual: " + actualState);
            
            // Retry verification after delay
            mHandler.postDelayed(() -> verifyBypassState(expectedState, attemptCount + 1), 200);
        } else {
            Log.d(TAG, "Bypass charge successfully " + (expectedState ? "enabled" : "disabled"));
        }
    }

    public boolean isNodeAccessible(NodeConfig config) {
        if (config == null || config.path == null || config.path.isEmpty()) {
            return false;
        }
        
        try {
            // Check if file exists
            File nodeFile = new File(config.path);
            if (!nodeFile.exists()) {
                Log.d(TAG, "Node " + config.path + " does not exist");
                return false;
            }
            
            // Test read access
            String value = FileUtils.readOneLine(config.path);
            if (value == null) {
                Log.d(TAG, "Node " + config.path + " cannot be read");
                return false;
            }
            
            value = value.trim();
            
            // Test write access by writing the same value back
            boolean writeSuccess = FileUtils.writeLine(config.path, value);
            
            if (!writeSuccess) {
                Log.d(TAG, "Node " + config.path + " is not writable");
                return false;
            }
            
            // Validate that the node accepts expected values
            boolean validationSuccess = validateNodeValues(config);
            
            Log.d(TAG, "Node " + config.path + " accessibility - read: true, write: " + writeSuccess + 
                  ", validation: " + validationSuccess);
            return validationSuccess;
        } catch (Exception e) {
            Log.d(TAG, "Node " + config.path + " not accessible: " + e.getMessage());
            return false;
        }
    }

    private boolean validateNodeValues(NodeConfig config) {
        try {
            // Save current value
            String originalValue = FileUtils.readOneLine(config.path);
            if (originalValue == null) {
                return false;
            }
            originalValue = originalValue.trim();
            
            // Try writing bypass value
            if (!FileUtils.writeLine(config.path, config.bypassValue)) {
                return false;
            }
            
            // Read back and verify
            String readValue = FileUtils.readOneLine(config.path);
            if (readValue == null || !readValue.trim().equals(config.bypassValue)) {
                // Restore original value
                FileUtils.writeLine(config.path, originalValue);
                return false;
            }
            
            // Try writing normal value
            if (!FileUtils.writeLine(config.path, config.normalValue)) {
                // Restore original value
                FileUtils.writeLine(config.path, originalValue);
                return false;
            }
            
            // Read back and verify
            readValue = FileUtils.readOneLine(config.path);
            if (readValue == null || !readValue.trim().equals(config.normalValue)) {
                // Restore original value
                FileUtils.writeLine(config.path, originalValue);
                return false;
            }
            
            // Restore original value
            FileUtils.writeLine(config.path, originalValue);
            
            Log.d(TAG, "Node " + config.path + " validation successful");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Node validation failed for " + config.path, e);
            return false;
        }
    }

    public boolean isBypassChargeSupported() {
        return mActiveNodeConfig != null;
    }

    public String getActiveNode() {
        return mActiveNodeConfig != null ? mActiveNodeConfig.path : null;
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
        mActiveNodeConfig = findWorkingNode();
    }

    /**
     * Get debug information about bypass charging
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Active node: ").append(mActiveNodeConfig != null ? mActiveNodeConfig.path : "None").append("\n");
        if (mActiveNodeConfig != null) {
            info.append("Node type: ").append(mActiveNodeConfig.isSuspendType ? "suspend" : "enable").append("\n");
            info.append("Bypass value: ").append(mActiveNodeConfig.bypassValue).append("\n");
            info.append("Normal value: ").append(mActiveNodeConfig.normalValue).append("\n");
        }
        info.append("Bypass supported: ").append(isBypassChargeSupported()).append("\n");
        info.append("Current state: ").append(isBypassChargeEnabled()).append("\n");
        info.append("Preference value: ").append(getBypassChargePreference()).append("\n");
        
        info.append("\nAll nodes accessibility:\n");
        for (NodeConfig config : NODE_CONFIGS) {
            File nodeFile = new File(config.path);
            boolean exists = nodeFile.exists();
            boolean accessible = exists && isNodeAccessible(config);
            info.append("  ").append(config.path)
                .append(" (").append(config.isSuspendType ? "suspend" : "enable").append(")")
                .append(": exists=").append(exists)
                .append(", accessible=").append(accessible);
            
            if (exists) {
                try {
                    String currentValue = FileUtils.readOneLine(config.path);
                    info.append(", value=").append(currentValue != null ? currentValue.trim() : "null");
                } catch (Exception e) {
                    info.append(", value=error");
                }
            }
            info.append("\n");
        }
        
        return info.toString();
    }

    /**
     * Test all nodes and return detailed information
     */
    public String testAllNodes() {
        StringBuilder result = new StringBuilder("Node Test Results:\n\n");
        
        for (NodeConfig config : NODE_CONFIGS) {
            result.append("Testing: ").append(config.path).append("\n");
            result.append("Type: ").append(config.isSuspendType ? "suspend" : "enable").append("\n");
            
            File nodeFile = new File(config.path);
            if (!nodeFile.exists()) {
                result.append("Status: NOT FOUND\n\n");
                continue;
            }
            
            try {
                // Read current value
                String currentValue = FileUtils.readOneLine(config.path);
                result.append("Current value: ").append(currentValue != null ? currentValue.trim() : "null").append("\n");
                
                if (currentValue == null) {
                    result.append("Status: READ FAILED\n\n");
                    continue;
                }
                
                // Test accessibility
                boolean accessible = isNodeAccessible(config);
                result.append("Accessible: ").append(accessible).append("\n");
                
                if (accessible) {
                    result.append("Status: WORKING\n");
                } else {
                    result.append("Status: NOT ACCESSIBLE\n");
                }
                
            } catch (Exception e) {
                result.append("Status: ERROR - ").append(e.getMessage()).append("\n");
            }
            
            result.append("\n");
        }
        
        return result.toString();
    }
}
