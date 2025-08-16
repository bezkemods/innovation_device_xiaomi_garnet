package org.lineageos.settings.xiaomiparts;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.ListView;
import android.util.Log;

import org.lineageos.settings.R;
import org.lineageos.settings.thermal.ThermalSettingsActivity;
import org.lineageos.settings.dirac.DiracActivity;
import org.lineageos.settings.speaker.ClearSpeakerActivity;
import org.lineageos.settings.saturation.SaturationActivity;
import org.lineageos.settings.autohbm.AutoHbmActivity;
import org.lineageos.settings.gamebar.GameBarSettingsActivity;
import org.lineageos.settings.turbocharging.TurboChargingActivity;
import org.lineageos.settings.aboutme.AboutMeActivity;
import org.lineageos.settings.corecontrol.CoreControlActivity;
import org.lineageos.settings.charge.ChargeActivity;
import org.lineageos.settings.kernelmanager.KernelManagerActivity;
import org.lineageos.settings.gpumanager.GpuManagerActivity;
import org.lineageos.settings.chargecontrol.ChargeControlActivity;
import org.lineageos.settings.logcatviewer.MainActivity;
import org.lineageos.settings.logcatviewer.LogcatSettingsPreference;
import org.lineageos.settings.adblocker.AdBlockerActivity;
import org.lineageos.settings.performance.PerformanceActivity;

public class XiaomiPartsActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private static final String TAG = "XiaomiPartsActivity";
    
    private static final String KEY_THERMAL = "thermal_settings";
    private static final String KEY_DIRAC = "dirac_settings";
    private static final String KEY_CLEAR_SPEAKER = "clear_speaker";
    private static final String KEY_SATURATION = "saturation_settings";
    private static final String KEY_AUTO_HBM = "auto_hbm";
    private static final String KEY_GAMEBAR = "gamebar_settings";
    private static final String KEY_TURBO_CHARGING = "turbo_charging";
    private static final String KEY_ABOUTME = "about_me_settings";
    private static final String KEY_CORE_CONTROL = "core_control_settings";
    private static final String KEY_CHARGE_BYPASS = "charge_bypass";
    private static final String KEY_KERNEL_MANAGER = "kernel_manager";
    private static final String KEY_GPU_MANAGER = "gpu_manager";
    private static final String KEY_CHARGE_CONTROL = "charge_control";
    private static final String KEY_LOGCAT_VIEWER = "open_logcat_viewer";
    private static final String KEY_ADBLOCKER = "adblocker_settings";
    private static final String KEY_PERFORMANCE = "performance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            addPreferencesFromResource(R.xml.xiaomi_parts_settings);

            // Remove dividers
            ListView listView = getListView();
            if (listView != null) {
                listView.setDivider(null); 
                listView.setDividerHeight(0);
            }

            setupPreferences();
            
            Log.d(TAG, "XiaomiPartsActivity created successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating XiaomiPartsActivity", e);
        }
    }

    private void setupPreferences() {
        try {
            PreferenceScreen prefScreen = getPreferenceScreen();
            if (prefScreen == null) {
                Log.e(TAG, "PreferenceScreen is null");
                return;
            }

            setupPreference(KEY_THERMAL);
            setupPreference(KEY_DIRAC);
            setupPreference(KEY_CLEAR_SPEAKER);
            setupPreference(KEY_SATURATION);
            setupPreference(KEY_AUTO_HBM);
            setupPreference(KEY_GAMEBAR);
            setupPreference(KEY_TURBO_CHARGING);
            setupPreference(KEY_CORE_CONTROL);
            setupPreference(KEY_CHARGE_BYPASS);
            setupPreference(KEY_CHARGE_CONTROL);
            setupPreference(KEY_KERNEL_MANAGER);
            setupPreference(KEY_GPU_MANAGER);
            setupPreference(KEY_ADBLOCKER);
            setupPreference(KEY_PERFORMANCE);
            
            // Logcat viewer - speciális kezelés
            setupLogcatViewer();
            
            // About Me - speciális kezelés
            setupAboutMe();
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up preferences", e);
        }
    }

    private void setupPreference(String key) {
        try {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setOnPreferenceClickListener(this);
                Log.d(TAG, "Setup preference: " + key);
            } else {
                Log.w(TAG, "Preference not found: " + key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up preference: " + key, e);
        }
    }
    
    private void setupLogcatViewer() {
        try {
            Preference logcatViewerPref = findPreference(KEY_LOGCAT_VIEWER);
            if (logcatViewerPref != null) {
                logcatViewerPref.setOnPreferenceClickListener(preference -> {
                    try {
                        LogcatSettingsPreference.handleLogcatViewerClick(this);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening logcat viewer", e);
                        return false;
                    }
                });
                Log.d(TAG, "Logcat viewer preference setup complete");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up logcat viewer", e);
        }
    }
    
    private void setupAboutMe() {
        try {
            Preference aboutMePref = findPreference(KEY_ABOUTME);
            if (aboutMePref != null) {
                aboutMePref.setOnPreferenceClickListener(preference -> {
                    try {
                        Intent intent = new Intent(this, AboutMeActivity.class);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting About Me activity", e);
                        return false;
                    }
                });
                Log.d(TAG, "About Me preference setup complete");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up About Me", e);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        try {
            if (KEY_THERMAL.equals(key)) {
                startActivity(new Intent(this, ThermalSettingsActivity.class));
                return true;
            } else if (KEY_DIRAC.equals(key)) {
                startActivity(new Intent(this, DiracActivity.class));
                return true;
            } else if (KEY_CLEAR_SPEAKER.equals(key)) {
                startActivity(new Intent(this, ClearSpeakerActivity.class));
                return true;
            } else if (KEY_SATURATION.equals(key)) {
                startActivity(new Intent(this, SaturationActivity.class));
                return true;
            } else if (KEY_AUTO_HBM.equals(key)) {
                startActivity(new Intent(this, AutoHbmActivity.class));
                return true;
            } else if (KEY_GAMEBAR.equals(key)) {
                startActivity(new Intent(this, GameBarSettingsActivity.class));
                return true;
            } else if (KEY_TURBO_CHARGING.equals(key)) {
                startActivity(new Intent(this, TurboChargingActivity.class));
                return true;
            } else if (KEY_CORE_CONTROL.equals(key)) {
                startActivity(new Intent(this, CoreControlActivity.class));
                return true;
            } else if (KEY_CHARGE_BYPASS.equals(key)) {
                startActivity(new Intent(this, ChargeActivity.class));
                return true;
            } else if (KEY_CHARGE_CONTROL.equals(key)) {
                startActivity(new Intent(this, ChargeControlActivity.class));
                return true;
            } else if (KEY_KERNEL_MANAGER.equals(key)) {
                startActivity(new Intent(this, KernelManagerActivity.class));
                return true;
            } else if (KEY_GPU_MANAGER.equals(key)) {
                startActivity(new Intent(this, GpuManagerActivity.class));
                return true;
            } else if (KEY_ADBLOCKER.equals(key)) {
                startActivity(new Intent(this, AdBlockerActivity.class));
                return true;
            } else if (KEY_PERFORMANCE.equals(key)) {
                startActivity(new Intent(this, PerformanceActivity.class));
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling preference click for key: " + key, e);
        }
        return false;
    }
}
