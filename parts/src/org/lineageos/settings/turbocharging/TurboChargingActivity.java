package org.lineageos.settings.turbocharging;

import android.os.Bundle;
import androidx.preference.PreferenceManager;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.settings.R;

public class TurboChargingActivity extends CollapsingToolbarBaseActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            PreferenceManager.setDefaultValues(this, R.xml.turbocharging, false);
            setContentView(R.layout.turbocharging_layout);
            
            // Use modern FragmentManager
            if (getSupportFragmentManager().findFragmentById(R.id.content_frame) == null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.content_frame, new TurboChargingFragment())
                        .commit();
            }
        } catch (Exception e) {
            android.util.Log.e(TurboChargingConstants.TAG, "Error in TurboChargingActivity onCreate", e);
            finish(); // Close activity if setup fails
        }
    }
}
