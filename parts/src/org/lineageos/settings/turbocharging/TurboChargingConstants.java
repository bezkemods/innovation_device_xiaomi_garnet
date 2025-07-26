package org.lineageos.settings.turbocharging;

public class TurboChargingConstants {
    public static final String PREF_TURBO_ENABLED = "turbo_enable";
    public static final String PREF_SPORTS_MODE = "sports_mode";
    public static final String PREF_TURBO_CURRENT = "turbo_current";
    public static final String PROP_TURBO_CURRENT = "persist.sys.turbo_charge_current";
    public static final String SPORTS_MODE_NODE = "/sys/class/qcom-battery/sport_mode";
    public static final String DEFAULT_OFF_VALUE = "4700000";
    public static final String DEFAULT_ON_VALUE = "6700000";
    
    // Toast messages
    public static final String TAG = "TurboCharging";
    
    // Service monitoring interval
    public static final int MONITORING_INTERVAL_MS = 5000;
    
    // UEvent paths
    public static final String USB_POWER_SUPPLY_PATH = "DEVPATH=/sys/class/power_supply/usb";
    
    private TurboChargingConstants() {
        // Prevent instantiation
    }
}
