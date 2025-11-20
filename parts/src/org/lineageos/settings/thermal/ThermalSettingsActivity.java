package org.lineageos.settings.thermal;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class ThermalSettingsActivity extends CollapsingToolbarBaseActivity {
    
    private static final String TAG = "ThermalSettingsActivity";
    private static final String TAG_THERMAL = "thermal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new ThermalSettingsFragment(), TAG_THERMAL)
                    .commit();
            Log.d(TAG, "ThermalSettingsActivity created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
