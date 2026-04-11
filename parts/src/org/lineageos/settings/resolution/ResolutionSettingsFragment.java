/*
 * Copyright (C) 2025 KamiKaonashi
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

package org.lineageos.settings.resolution;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
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

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResolutionSettingsFragment extends PreferenceFragment
        implements ApplicationsState.Callbacks {

    /** How long (ms) the state badge stays visible after a selection change. */
    private static final long STATE_ICON_HIDE_DELAY_MS = 5_000L;

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private final Map<String, ApplicationsState.AppEntry> mEntryMap = new HashMap<>();
    private ResolutionUtils mResolutionUtils;
    private RecyclerView mAppsRecyclerView;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mSession.onResume();
        mActivityFilter = new ActivityFilter(getActivity().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(getActivity());
        mResolutionUtils = new ResolutionUtils(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.resolution_layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAppsRecyclerView = view.findViewById(R.id.resolution_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(getResources().getString(R.string.upscale_title));
        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.onPause();
        mSession.onDestroy();
    }

    @Override public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override public void onLoadEntriesCompleted() { rebuild(); }
    @Override public void onAllSizesComputed() {}
    @Override public void onLauncherInfoChanged() {}
    @Override public void onPackageIconChanged() {}
    @Override public void onPackageSizeChanged(String packageName) {}
    @Override public void onRunningStateChanged(boolean running) {}

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String>  sections  = new ArrayList<>();
        final ArrayList<Integer> positions = new ArrayList<>();
        final PackageManager pm = getActivity().getPackageManager();
        String lastSectionIndex = null;
        int offset = 0;

        for (ApplicationsState.AppEntry appEntry : entries) {
            final ApplicationInfo info  = appEntry.info;
            final String          label = (String) info.loadLabel(pm);
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--";
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null
                    || !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }
            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
        mEntryMap.clear();
        for (ApplicationsState.AppEntry e : entries) {
            mEntryMap.put(e.info.packageName, e);
        }
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private int getStateDrawable(int state) {
        switch (state) {
            case ResolutionUtils.STATE_480P:    return R.drawable.ic_resolution_480;
            case ResolutionUtils.STATE_540P:    return R.drawable.ic_resolution_540;
            case ResolutionUtils.STATE_720P:    return R.drawable.ic_resolution_720;
            case ResolutionUtils.STATE_DEFAULT:
            default:                            return R.drawable.ic_resolution_default;
        }
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    private class ViewHolder extends RecyclerView.ViewHolder {
        final TextView  title;
        final Spinner   mode;
        final ImageView icon;
        final ImageView stateIcon;

        ViewHolder(View view) {
            super(view);
            title     = view.findViewById(R.id.app_name);
            mode      = view.findViewById(R.id.app_mode);
            icon      = view.findViewById(R.id.app_icon);
            stateIcon = view.findViewById(R.id.state);
            view.setTag(this);
        }

        /**
         * Show the state badge and schedule auto-hide after STATE_ICON_HIDE_DELAY_MS.
         *
         * We attach the hide-Runnable directly to the ImageView via
         * {@link View#setTag(int, Object)} so that:
         *  1. Any pending callback from a previous bind is cancelled before we post
         *     a new one — no stale callbacks can fire on recycled views.
         *  2. No per-holder Handler field is needed, eliminating the recycling race.
         */
        void showStateBadge(int drawableRes) {
            // Cancel any previously scheduled hide for this exact view.
            Runnable previous = (Runnable) stateIcon.getTag(R.id.tag_hide_runnable);
            if (previous != null) stateIcon.removeCallbacks(previous);

            stateIcon.setImageResource(drawableRes);
            stateIcon.setVisibility(View.VISIBLE);

            Runnable hide = () -> stateIcon.setVisibility(View.GONE);
            stateIcon.setTag(R.id.tag_hide_runnable, hide);
            stateIcon.postDelayed(hide, STATE_ICON_HIDE_DELAY_MS);
        }

        /** Cancel any pending hide and immediately hide the badge. */
        void hideStateBadge() {
            Runnable pending = (Runnable) stateIcon.getTag(R.id.tag_hide_runnable);
            if (pending != null) {
                stateIcon.removeCallbacks(pending);
                stateIcon.setTag(R.id.tag_hide_runnable, null);
            }
            stateIcon.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Spinner adapter for the per-app mode selection
    // -------------------------------------------------------------------------

    private class ModeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final int[] items = {
                R.string.upscale_default,
                R.string.upscale_480p,
                R.string.upscale_540p,
                R.string.upscale_720p
        };

        ModeAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        @Override public int    getCount()               { return items.length; }
        @Override public Object getItem(int position)    { return items[position]; }
        @Override public long   getItemId(int position)  { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView != null) {
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

    // -------------------------------------------------------------------------
    // RecyclerView adapter
    // -------------------------------------------------------------------------

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder>
            implements AdapterView.OnItemSelectedListener {

        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[]    mPositions;

        AllPackagesAdapter(Context context) {
            mActivityFilter = new ActivityFilter(context.getPackageManager());
        }

        @Override public int  getItemCount()           { return mEntries.size(); }
        @Override public long getItemId(int position)  { return mEntries.get(position).id; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.resolution_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ApplicationsState.AppEntry entry = mEntries.get(position);
            if (entry == null) return;

            // Always hide the badge when the view is (re)bound — the timer from
            // a previous bind should not carry over to a recycled view.
            holder.hideStateBadge();

            holder.mode.setAdapter(new ModeAdapter(holder.itemView.getContext()));
            holder.mode.setOnItemSelectedListener(this);
            holder.title.setText(entry.label);
            holder.title.setOnClickListener(v -> holder.mode.performClick());

            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);

            int packageState = mResolutionUtils.getStateForPackage(entry.info.packageName);
            holder.mode.setSelection(packageState, false);
            holder.mode.setTag(entry);
            // Badge is hidden on bind; it becomes visible only when the user changes the selection.
        }

        void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mEntries   = entries;
            mSections  = sections.toArray(new String[0]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) mPositions[i] = positions.get(i);
            notifyDataSetChanged();
        }

        // Called when the user picks a different mode in the spinner
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            final ApplicationsState.AppEntry entry =
                    (ApplicationsState.AppEntry) parent.getTag();
            if (entry == null) return;

            int currentState = mResolutionUtils.getStateForPackage(entry.info.packageName);
            if (currentState == position) return;

            mResolutionUtils.writePackage(entry.info.packageName, position);

            // Find the ViewHolder that owns this spinner and show its badge
            // (the spinner's parent chain leads back to the item root view).
            View itemRoot = (View) parent.getParent().getParent();
            if (itemRoot != null) {
                Object tag = itemRoot.getTag();
                if (tag instanceof ViewHolder) {
                    ViewHolder vh = (ViewHolder) tag;
                    vh.showStateBadge(getStateDrawable(position));
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}

        // SectionIndexer helpers (used by fast scroll)
        public int getPositionForSection(int section) {
            if (mSections == null || section < 0 || section >= mSections.length) return -1;
            return mPositions[section];
        }

        public int getSectionForPosition(int position) {
            if (mPositions == null || position < 0 || position >= getItemCount()) return -1;
            int index = Arrays.binarySearch(mPositions, position);
            return index >= 0 ? index : -index - 2;
        }

        public Object[] getSections() { return mSections; }
    }

    // -------------------------------------------------------------------------
    // App filter — launcher apps only
    // -------------------------------------------------------------------------

    private class ActivityFilter implements ApplicationsState.AppFilter {
        private final PackageManager mPackageManager;
        private final List<String>   mLauncherResolveInfoList = new ArrayList<>();

        ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = mPackageManager.queryIntentActivities(i, 0);
            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : list) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override public void init() {}

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            boolean show = !mAllPackagesAdapter.mEntries.contains(entry.info.packageName);
            if (show) {
                synchronized (mLauncherResolveInfoList) {
                    show = mLauncherResolveInfoList.contains(entry.info.packageName);
                }
            }
            return show;
        }
    }
}
