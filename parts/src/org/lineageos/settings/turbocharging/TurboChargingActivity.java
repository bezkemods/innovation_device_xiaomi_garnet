package org.lineageos.settings.turbocharging;

import android.os.Bundle;
import androidx.preference.PreferenceManager;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.settings.R;

public class TurboChargingActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.turbocharging, false);
        setContentView(R.layout.turbocharging_layout);
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, new TurboChargingFragment())
                .commit();
    }
}
