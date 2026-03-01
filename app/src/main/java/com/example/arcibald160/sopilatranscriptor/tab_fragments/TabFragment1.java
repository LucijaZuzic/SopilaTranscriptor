package com.example.arcibald160.sopilatranscriptor.tab_fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.example.arcibald160.sopilatranscriptor.MapActivity;
import com.example.arcibald160.sopilatranscriptor.R;
import com.example.arcibald160.sopilatranscriptor.adapters.Tab1Adapter;

import java.io.File;

public class TabFragment1 extends Fragment {

    private RecyclerView mRecyclerViewTab1;
    private Tab1Adapter mAdapter;
    private String path;
    private LinearLayout mEmptyView;
    private Button mGoToRecordButton;

    public TabFragment1() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view =  inflater.inflate(R.layout.fragment_tab_1, container, false);
        path = view.getContext().getExternalFilesDir(null).toString() + "/" + view.getContext().getString(R.string.rec_folder);

        // Recycler view
        mRecyclerViewTab1 = (RecyclerView) view.findViewById(R.id.recycler_view_tab1);
        mEmptyView = (LinearLayout) view.findViewById(R.id.empty_view_tab1);
        mGoToRecordButton = (Button) view.findViewById(R.id.btn_go_to_record);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerViewTab1.setHasFixedSize(true);

        // use a linear layout manager
        mRecyclerViewTab1.setLayoutManager(new LinearLayoutManager(getContext()));

        mGoToRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirect to Tab 1 (Index 1 is the middle tab for recording)
                ViewPager viewPager = getActivity().findViewById(R.id.view_pager);
                if (viewPager != null) {
                    viewPager.setCurrentItem(1);
                }
            }
        });

        refreshList();

        return view;
    }

    private void refreshList() {
        File recordingsDirectory = new File(path);
        File[] files = recordingsDirectory.listFiles();

        if (files == null || files.length == 0) {
            mRecyclerViewTab1.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerViewTab1.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            
            if (mAdapter == null) {
                mAdapter = new Tab1Adapter(files, getContext(), this);
                mRecyclerViewTab1.setAdapter(mAdapter);
            } else {
                mAdapter.refreshRecDir();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Tab1Adapter.ADJUST_LOCATION_FOR_WAV_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            double newLat = data.getDoubleExtra(MapActivity.EXTRA_LATITUDE, 0);
            double newLon = data.getDoubleExtra(MapActivity.EXTRA_LONGITUDE, 0);
            
            SharedPreferences prefs = getContext().getSharedPreferences(getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
            String fileName = prefs.getString("adjusting_file_name", null);
            
            if (fileName != null && mAdapter != null) {
                mAdapter.saveLocationForFile(fileName, newLat, newLon);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            refreshList();
        }
    }
}
