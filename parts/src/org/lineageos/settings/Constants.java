/*
 * Copyright (C) 2024 The LineageOS Project
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

package org.lineageos.settings;

public class Constants {

    // AutoHbm
    public static final String KEY_AUTO_HBM = "auto_hbm";
    public static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";
    public static final String KEY_AUTO_HBM_ENABLE_TIME = "auto_hbm_enable_time";
    public static final String KEY_AUTO_HBM_DISABLE_TIME = "auto_hbm_disable_time";
    public static final String KEY_CURRENT_LUX_LEVEL = "current_lux_level";
    public static final String NODE_BRIGHTNESS = "/sys/class/backlight/panel0-backlight/brightness";

    // Saturation
    public static final String KEY_SATURATION = "saturation";
    public static final String KEY_SATURATION_PREVIEW = "saturation_preview";

    // Charge control
    public static final String KEY_CHARGE_CONTROL = "charge_control";
    public static final String KEY_STOP_CHARGING = "stop_charging";
    // NODE_STOP_CHARGING: write "1" to suspend input (stop charging), "0" to allow charging.
    // Candidate nodes in priority order — ChargeControlUtils probes these at runtime.
    public static final String[] NODES_STOP_CHARGING = {
        "/sys/class/qcom-battery/input_suspend",
        "/sys/class/power_supply/battery/input_suspend",
        "/sys/class/power_supply/battery/charging_enabled",   // inverted: "0" = stop
        "/sys/class/power_supply/battery/battery_charging_enabled", // inverted: "0" = stop
    };
    // Convenience alias resolved at runtime by ChargeControlUtils.resolveNode()
    public static final String NODE_STOP_CHARGING = NODES_STOP_CHARGING[0];
    // Default stop threshold in percent
    public static final int DEFAULT_STOP_CHARGING = 80;
}
