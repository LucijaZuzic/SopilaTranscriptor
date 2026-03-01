package com.example.arcibald160.sopilatranscriptor;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LATITUDE = "LATITUDE";
    public static final String EXTRA_LONGITUDE = "LONGITUDE";
    public static final String EXTRA_ADJUST_MODE = "ADJUST_MODE";

    private double lon, lat;
    private boolean isAdjustMode;
    private GoogleMap mGoogleMap;
    private Marker mMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        lat = getIntent().getDoubleExtra(EXTRA_LATITUDE, 0.0);
        lon = getIntent().getDoubleExtra(EXTRA_LONGITUDE, 0.0);
        isAdjustMode = getIntent().getBooleanExtra(EXTRA_ADJUST_MODE, false);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isAdjustMode ? "Adjust Location" : "View Location");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (googleMap == null) return;
        mGoogleMap = googleMap;

        LatLng position = new LatLng(lat, lon);
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(isAdjustMode ? "Drag to adjust" : "Sopele played here")
                .draggable(isAdjustMode);

        mGoogleMap.clear();
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15.0f));
        mMarker = mGoogleMap.addMarker(markerOptions);
        if (!isAdjustMode) {
            mMarker.showInfoWindow();
        }

        if (isAdjustMode) {
            mGoogleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    mMarker.setPosition(latLng);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isAdjustMode) {
            MenuItem saveItem = menu.add(Menu.NONE, 1001, Menu.NONE, "Confirm");
            saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 1001) {
            LatLng finalPos = mMarker.getPosition();
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_LATITUDE, finalPos.latitude);
            resultIntent.putExtra(EXTRA_LONGITUDE, finalPos.longitude);
            setResult(RESULT_OK, resultIntent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
