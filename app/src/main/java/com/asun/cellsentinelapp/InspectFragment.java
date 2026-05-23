package com.asun.cellsentinelapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

public class InspectFragment extends Fragment {

    private ViewPager mPager;
    private TabLayout mTabs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_inspect, container, false);
        mPager = root.findViewById(R.id.inspect_pager);
        mTabs  = root.findViewById(R.id.inspect_tabs);

        mPager.setAdapter(new InspectPagerAdapter(getChildFragmentManager()));
        mTabs.setupWithViewPager(mPager);
        mPager.setOffscreenPageLimit(3);
        return root;
    }

    @SuppressWarnings("deprecation")
    private static class InspectPagerAdapter extends FragmentPagerAdapter {

        private static final String[] TITLES = {"WiFi", "位置", "设备", "速测"};

        InspectPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: return new WifiInfoFragment();
                case 1: return new LocationInfoFragment();
                case 2: return new DeviceInfoFragment();
                case 3: return new SpeedTestFragment();
                default: return new WifiInfoFragment();
            }
        }

        @Override public int getCount() { return TITLES.length; }

        @Override
        public CharSequence getPageTitle(int position) { return TITLES[position]; }
    }
}
