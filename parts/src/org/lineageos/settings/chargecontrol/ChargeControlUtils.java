/*
 * Copyright (C) 2025 kenway214
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

package org.lineageos.settings.chargecontrol;

import android.util.Log;

import org.lineageos.settings.Constants;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Utility class for charge control sysfs access via root shell.
 *
 * The sysfs nodes under /sys/class/qcom-battery/ and /sys/class/power_supply/
 * are owned by root and not writable by the app UID directly.
 * All reads and writes are done through "su -c" shell commands.
 *
 * Node semantics:
 *   input_suspend / battery/input_suspend  →  "1" = stop charging, "0" = allow
 *   battery/charging_enabled               →  "0" = stop charging, "1" = allow  (inverted)
 *   battery/battery_charging_enabled       →  "0" = stop charging, "1" = allow  (inverted)
 */
public final class ChargeControlUtils {

    private static final String TAG = "ChargeControlUtils";

    // Cached node resolved at first call to resolveNode()
    private static String sResolvedNode = null;
    // Whether the resolved node is inverted (0=stop instead of 1=stop)
    private static boolean sNodeInverted = false;

    private ChargeControlUtils() {}

    /**
     * Probes candidate nodes via root and caches the first accessible one.
     * Returns null if no node is accessible (root not available, or no matching node).
     */
    public static synchronized String resolveNode() {
        if (sResolvedNode != null) return sResolvedNode;

        String[] nodes = Constants.NODES_STOP_CHARGING;
        for (String node : nodes) {
            String result = rootRead(node);
            if (result != null) {
                sResolvedNode = node;
                // charging_enabled and battery_charging_enabled use inverted logic
                sNodeInverted = node.endsWith("charging_enabled");
                Log.i(TAG, "Resolved charge control node: " + node
                        + (sNodeInverted ? " (inverted)" : ""));
                return sResolvedNode;
            }
        }
        Log.e(TAG, "No accessible charge control node found");
        return null;
    }

    /**
     * Returns true if a usable node is accessible via root.
     */
    public static boolean isNodeAccessible() {
        return resolveNode() != null;
    }

    /**
     * Suspends (stops) charging input.
     * Writes the appropriate value depending on node semantics.
     */
    public static boolean setChargingSuspended(boolean suspend) {
        String node = resolveNode();
        if (node == null) {
            Log.e(TAG, "setChargingSuspended: no node available");
            return false;
        }
        // inverted node: charging_enabled → write "0" to stop, "1" to allow
        // normal node:   input_suspend    → write "1" to stop, "0" to allow
        String value;
        if (sNodeInverted) {
            value = suspend ? "0" : "1";
        } else {
            value = suspend ? "1" : "0";
        }
        return rootWrite(node, value);
    }

    /**
     * Returns current charging suspended state, or false on error.
     */
    public static boolean isChargingSuspended() {
        String node = resolveNode();
        if (node == null) return false;
        String val = rootRead(node);
        if (val == null) return false;
        val = val.trim();
        if (sNodeInverted) {
            return "0".equals(val);
        } else {
            return "1".equals(val);
        }
    }

    /**
     * Reads a sysfs node via "su -c cat <path>".
     * Returns trimmed content, or null on failure.
     */
    private static String rootRead(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + path});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            int exit = p.waitFor();
            if (exit == 0 && line != null) {
                return line.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "rootRead failed for " + path + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Writes a value to a sysfs node via "su -c echo <value> > <path>".
     * Returns true on success.
     */
    static boolean rootWrite(String path, String value) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "echo " + value + " > " + path});
            int exit = p.waitFor();
            if (exit == 0) {
                Log.d(TAG, "rootWrite: " + path + " = " + value);
                return true;
            } else {
                Log.e(TAG, "rootWrite failed (exit " + exit + "): " + path + " = " + value);
            }
        } catch (Exception e) {
            Log.e(TAG, "rootWrite exception for " + path + ": " + e.getMessage());
        }
        return false;
    }
}
