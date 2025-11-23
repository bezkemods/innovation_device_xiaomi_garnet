package org.lineageos.settings.thermal;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.widget.MainSwitchPreference;
import org.lineageos.settings.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThermalSettingsFragment extends PreferenceFragment 
        implements ApplicationsState.Callbacks {

    private static final String TAG = "ThermalSettingsFragment";
    private static final String THERMAL_ENABLE_KEY = "thermal_enable";
    
    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private ThermalUtils mThermalUtils;
    private RecyclerView mAppsRecyclerView;
    private MainSwitchPreference mMainSwitch;
    private Map<String, ApplicationsState.AppEntry> mEntryMap = new HashMap<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.thermal_settings);
            mThermalUtils = ThermalUtils.getInstance(getActivity());
            
            mMainSwitch = (MainSwitchPreference) findPreference(THERMAL_ENABLE_KEY);
            if (mMainSwitch != null) {
                mMainSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    mThermalUtils.setEnabled(enabled);
                    if (mAppsRecyclerView != null) {
                        mAppsRecyclerView.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    }
                    return true;
                });
                mMainSwitch.setChecked(mThermalUtils.isEnabled());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreatePreferences", e);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            mApplicationsState = ApplicationsState.getInstance(
                    getActivity().getApplication());
            mSession = mApplicationsState.newSession(this);
            mSession.onResume();
            mActivityFilter = new ActivityFilter(getActivity().getPackageManager());
            mAllPackagesAdapter = new AllPackagesAdapter(getActivity());
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
            Bundle savedInstanceState) {
        try {
            // Betöltjük az új LinearLayout-os XML-t
            View view = inflater.inflate(R.layout.thermal_settings_fragment, 
                    container, false);
            
            // Megkeressük a kapcsolónak fenntartott helyet
            ViewGroup prefsContainer = view.findViewById(R.id.thermal_prefs_container);
            
            // Létrehozzuk a kapcsolót (a PreferenceFragment alapértelmezett nézetét)
            View prefsView = super.onCreateView(inflater, prefsContainer, savedInstanceState);
            
            // Belehelyezzük a kapcsolót a konténerbe
            if (prefsView != null && prefsContainer != null) {
                prefsContainer.addView(prefsView); 
            }
            
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreateView", e);
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            mAppsRecyclerView = view.findViewById(R.id.thermal_rv_view);
            if (mAppsRecyclerView != null) {
                mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
                mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
                mAppsRecyclerView.setVisibility(
                        mThermalUtils.isEnabled() ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            getActivity().setTitle(getResources().getString(R.string.thermal_title));
            if (mSession != null) {
                mSession.onResume();
                rebuild();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (mSession != null) {
                mSession.onPause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mSession != null) {
                mSession.onDestroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            if (mAllPackagesAdapter != null) {
                mAllPackagesAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override 
    public void onPackageListChanged() { 
        if (mActivityFilter != null) {
            mActivityFilter.updateLauncherInfoList();
        }
        rebuild();
    }
    
    @Override 
    public void onLoadEntriesCompleted() { 
        rebuild();
    }
    
    @Override public void onAllSizesComputed() {}
    @Override public void onLauncherInfoChanged() {}
    @Override public void onPackageIconChanged() {}
    @Override public void onPackageSizeChanged(String packageName) {}
    @Override public void onRunningStateChanged(boolean running) {}

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        mEntryMap.clear();
        for (ApplicationsState.AppEntry entry : entries) {
            mEntryMap.put(entry.info.packageName, entry);
        }
        mAllPackagesAdapter.setEntries(entries);
    }

    private void rebuild() {
        if (mSession != null && mActivityFilter != null) {
            mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
        }
    }

    private int getStateIcon(int state) {
        switch (state) {
            case ThermalUtils.STATE_BENCHMARK:
                return R.drawable.ic_thermal_benchmark;
            case ThermalUtils.STATE_BROWSER:
                return R.drawable.ic_thermal_browser;
            case ThermalUtils.STATE_CAMERA:
                return R.drawable.ic_thermal_camera;
            case ThermalUtils.STATE_DIALER:
                return R.drawable.ic_thermal_dialer;
            case ThermalUtils.STATE_GAMING:
                return R.drawable.ic_thermal_gaming;
            case ThermalUtils.STATE_NAVIGATION:
                return R.drawable.ic_thermal_navigation;
            case ThermalUtils.STATE_STREAMING:
                return R.drawable.ic_thermal_streaming;
            case ThermalUtils.STATE_VIDEO:
                return R.drawable.ic_thermal_video;
            case ThermalUtils.STATE_DEFAULT:
            default:
                return R.drawable.ic_thermal_default;
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private Spinner mode;
        private ImageView icon;
        private ImageView stateIcon;

        private ViewHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.app_name);
            this.mode = view.findViewById(R.id.app_mode);
            this.icon = view.findViewById(R.id.app_icon);
            this.stateIcon = view.findViewById(R.id.state);
        }
    }

    private class ModeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final int[] items = {
            R.string.thermal_default, 
            R.string.thermal_benchmark, 
            R.string.thermal_browser,
            R.string.thermal_camera, 
            R.string.thermal_dialer, 
            R.string.thermal_gaming,
            R.string.thermal_navigation, 
            R.string.thermal_streaming, 
            R.string.thermal_video
        };
        
        private ModeAdapter(Context context) { 
            inflater = LayoutInflater.from(context); 
        }
        
        @Override 
        public int getCount() { 
            return items.length; 
        }
        
        @Override 
        public Object getItem(int position) { 
            return items[position]; 
        }
        
        @Override 
        public long getItemId(int position) { 
            return position; 
        }
        
        @Override 
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView != null && convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = (TextView) inflater.inflate(
                        android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            view.setText(items[position]);
            view.setTextSize(14f);
            return view;
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder> 
            implements AdapterView.OnItemSelectedListener {
        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private Context mContext;

        public AllPackagesAdapter(Context context) {
            mContext = context;
        }

        public void setEntries(List<ApplicationsState.AppEntry> entries) {
            mEntries = entries != null ? entries : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thermal_list_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            try {
                ApplicationsState.AppEntry entry = mEntries.get(position);
                if (entry == null || entry.info == null) {
                    return;
                }

                holder.title.setText(entry.label);
                holder.title.setOnClickListener(v -> holder.mode.performClick());
                
                if (mApplicationsState != null) {
                    mApplicationsState.ensureIcon(entry);
                }
                if (entry.icon != null) {
                    holder.icon.setImageDrawable(entry.icon);
                }

                holder.mode.setOnItemSelectedListener(null); 
                holder.mode.setAdapter(new ModeAdapter(mContext));
                
                int packageState = mThermalUtils.getStateForPackage(entry.info.packageName);
                holder.mode.setSelection(packageState, false);
                holder.mode.setTag(entry);
                
                if (holder.stateIcon != null) {
                    holder.stateIcon.setImageResource(getStateIcon(packageState));
                }
                
                holder.mode.setOnItemSelectedListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Error binding view holder at position " + position, e);
            }
        }

        @Override
        public int getItemCount() { 
            return mEntries.size(); 
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            try {
                ApplicationsState.AppEntry entry = (ApplicationsState.AppEntry) parent.getTag();
                if (entry != null && entry.info != null) {
                    int currentState = mThermalUtils.getStateForPackage(entry.info.packageName);
                    if (currentState != position) {
                        mThermalUtils.writePackage(entry.info.packageName, position);
                        notifyDataSetChanged();
                        Log.d(TAG, "Set thermal mode " + position + " for " + 
                                entry.info.packageName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onItemSelected", e);
            }
        }

        @Override 
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {
        private final android.content.pm.PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<>();

        private ActivityFilter(android.content.pm.PackageManager packageManager) {
            this.mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_MAIN);
            intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            List<android.content.pm.ResolveInfo> resolveInfoList = 
                    mPackageManager.queryIntentActivities(intent, 0);

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (android.content.pm.ResolveInfo ri : resolveInfoList) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherResolveInfoList) {
                return mLauncherResolveInfoList.contains(entry.info.packageName);
            }
        }
    }
}
