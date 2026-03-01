package com.example.arcibald160.sopilatranscriptor.tab_fragments;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.arcibald160.sopilatranscriptor.MapActivity;
import com.example.arcibald160.sopilatranscriptor.R;
import com.example.arcibald160.sopilatranscriptor.adapters.Tab3Adapter;

import java.io.File;

public class TabFragment3 extends Fragment {

    private RecyclerView mRecyclerViewTab3;
    private Tab3Adapter mAdapter;
    public static final int ADJUST_LOCATION_FOR_PDF_REQUEST_CODE = 104;

    public TabFragment3() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tab_3, container, false);

        // Recycler view
        mRecyclerViewTab3 = (RecyclerView) view.findViewById(R.id.recycler_view_tab3);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerViewTab3.setHasFixedSize(true);

        // use a linear layout manager
        mRecyclerViewTab3.setLayoutManager(new LinearLayoutManager(getContext()));

        // specify an adapter (see also next example)
        mAdapter = new Tab3Adapter(getContext(), this);
        mRecyclerViewTab3.setAdapter(mAdapter);

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADJUST_LOCATION_FOR_PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
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
        if (isVisibleToUser && mAdapter != null) {
            mAdapter.refreshSheetDir();
        }
    }


}
