package com.example.arcibald160.sopilatranscriptor.tab_fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.arcibald160.sopilatranscriptor.MapActivity;
import com.example.arcibald160.sopilatranscriptor.helpers.InsertFileNameDialog;
import com.example.arcibald160.sopilatranscriptor.R;
import com.example.arcibald160.sopilatranscriptor.helpers.Utils;
import com.example.arcibald160.sopilatranscriptor.helpers.VisualizerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;
import pl.bclogic.pulsator4droid.library.PulsatorLayout;


public class TabFragment2 extends Fragment {

    private Recorder recorder;

    private File tempRecFile;
    private Runnable updater;
    private long durationSec = 0;
    private final Handler timerHandler = new Handler();
    private VisualizerView musicVisualizer;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationManager mLocationManager;
    TextView durationView, sizeView, freeView, locationView, dateView;
    Location location = null;

    private static final int PERMISSION_LOCATION_REQUEST_CODE = 100;
    private static final int ADJUST_LOCATION_REQUEST_CODE = 101;

    // Moving peak for auto-gain
    private double dynamicPeak = 1000;

    public TabFragment2() {
        // Required empty public constructor
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_tab_2, container, false);

        if (getContext() != null) {
            tempRecFile = new File(getContext().getFilesDir(), "demo.wav");
        }
        
        final Switch mySwitch = view.findViewById(R.id.switch1);
        mySwitch.setClickable(false);

        musicVisualizer = view.findViewById(R.id.visualizer);
        durationView = view.findViewById(R.id.time_recorded);
        sizeView = view.findViewById(R.id.size_recorded);
        freeView = view.findViewById(R.id.free_space);
        Button locationButton = view.findViewById(R.id.location_img_btn);
        locationView = view.findViewById(R.id.location_text_view);
        dateView = view.findViewById(R.id.date_view);

        if (getContext() != null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(getContext());
        }

        // Immediately try to fetch location as soon as fragment starts
        fetchLocation(false);
        
        final PulsatorLayout pulsator = view.findViewById(R.id.pulsator);

        Date c = Calendar.getInstance().getTime();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", new Locale("hr", "HR"));
        String formattedDate = df.format(c);
        dateView.setText(formattedDate);

        // Show location on map
        Button showOnMapBtn = view.findViewById(R.id.buttonFake);
        showOnMapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (location != null) {
                    Intent intent = new Intent(getActivity(), MapActivity.class);
                    intent.putExtra(MapActivity.EXTRA_LONGITUDE, location.getLongitude());
                    intent.putExtra(MapActivity.EXTRA_LATITUDE, location.getLatitude());
                    intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, false);
                    startActivity(intent);
                } else {
                    // Automatically try fetching if location is unavailable
                    fetchLocation(true);
                }
            }
        });

        // Acquire a reference to the system Location Manager
        if (getActivity() != null) {
            mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        }
        
        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetchLocation(true);
            }
        });

        locationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAdjustLocation();
            }
        });

        freeView.setText(Utils.getAvailableInternalMemorySize(""));

        if (tempRecFile != null && getContext() != null) {
            MediaScannerConnection.scanFile(
                    getContext(),
                    new String[]{tempRecFile.getAbsolutePath()},
                    null,
                    null
            );
        }

        final ToggleButton recButton = view.findViewById(R.id.rec_button);

        recButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recButton.isChecked()) {
                    pulsator.bringToFront();
                    pulsator.start();
                    
                    PullableSource pullableSource = new PullableSource.Default(
                            new AudioRecordConfig.Default(
                                    MediaRecorder.AudioSource.MIC,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                    AudioFormat.CHANNEL_IN_MONO,
                                    44100
                            )
                    );

                    recorder = OmRecorder.wav(
                            new PullTransport.Default(pullableSource, new PullTransport.OnAudioChunkPulledListener() {
                                @Override
                                public void onAudioChunkPulled(omrecorder.AudioChunk audioChunk) {
                                    animateVuMeter(audioChunk.maxAmplitude());
                                }
                            }),
                            tempRecFile
                    );

                    try {
                        recorder.startRecording();
                        mySwitch.setChecked(true);
                        if (location == null) {
                            fetchLocation(false);
                        }
                    } finally {
                        updateRecordInfo();
                    }
                } else {
                    pulsator.stop();
                    if (musicVisualizer != null) {
                        musicVisualizer.clear();
                    }
                    
                    if (getActivity() != null) {
                        double lat = 0, lon = 0;
                        String locText = "";
                        if (location != null) {
                            lat = location.getLatitude();
                            lon = location.getLongitude();
                        }
                        if (locationView != null) {
                            locText = locationView.getText().toString();
                        }
                        InsertFileNameDialog filenameDialog = new InsertFileNameDialog(tempRecFile, getContext(), lat, lon, locText);
                        filenameDialog.show(getActivity().getSupportFragmentManager(), "filename");
                    }

                    try {
                        if (recorder != null) {
                            recorder.stopRecording();
                        }
                        mySwitch.setChecked(false);
//                        disable updater
                        resetRecordInfo();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (tempRecFile != null && getContext() != null) {
                        MediaScannerConnection.scanFile(
                                getContext(),
                                new String[]{tempRecFile.getAbsolutePath()},
                                null,
                                null
                        );
                    }

                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Immediately fetch location on startup
        if (location == null) {
            fetchLocation(false);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // Fetch location when tab becomes visible
        if (isVisibleToUser && location == null) {
            fetchLocation(false);
        }
    }

    private void animateVuMeter(final double maxAmplitude) {
        if (musicVisualizer != null) {
            // Auto-gain peak tracking
            if (maxAmplitude > dynamicPeak) {
                dynamicPeak = maxAmplitude;
            } else {
                // Decay peak to maintain sensitivity
                dynamicPeak = Math.max(1000, dynamicPeak * 0.98);
            }

            musicVisualizer.post(new Runnable() {
                @Override
                public void run() {
                    // Normalize relative to current dynamic peak
                    float normalized = (float) (maxAmplitude / dynamicPeak);
                    
                    // Boost small variations with a square root curve
                    float boosted = (float) Math.sqrt(normalized);
                    
                    musicVisualizer.addAmplitude(boosted);
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation(final boolean showToast) {
        if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION_REQUEST_CODE);
            return;
        }

        if (fusedLocationClient != null) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location loc) {
                    if (loc != null) {
                        location = loc;
                        updateLocationUI(loc);
                        if (showToast) {
                            Toast.makeText(getContext(), getString(R.string.location_success), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // If last location is null, try to request a fresh one
                        requestFreshLocation(showToast);
                    }
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation(final boolean showToast) {
        if (fusedLocationClient == null) return;
        
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);
        locationRequest.setNumUpdates(1);

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    if (showToast) Toast.makeText(getContext(), getString(R.string.location_fail), Toast.LENGTH_SHORT).show();
                    return;
                }
                for (Location loc : locationResult.getLocations()) {
                    if (loc != null) {
                        location = loc;
                        updateLocationUI(loc);
                        if (showToast) {
                            Toast.makeText(getContext(), getString(R.string.location_success), Toast.LENGTH_SHORT).show();
                        }
                        // Stop updates after we get one
                        fusedLocationClient.removeLocationUpdates(this);
                        break;
                    }
                }
            }
        }, android.os.Looper.getMainLooper());
    }

    private void updateLocationUI(Location loc) {
        if (getContext() != null) {
            Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String cityName = address.getLocality();
                    if (cityName == null) cityName = address.getSubAdminArea();
                    String countryName = address.getCountryName();
                    locationView.setText(String.format("%s, %s", cityName, countryName));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startAdjustLocation() {
        if (location == null) {
            Toast.makeText(getContext(), "Fetch current location first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), MapActivity.class);
            intent.putExtra(MapActivity.EXTRA_LATITUDE, location.getLatitude());
            intent.putExtra(MapActivity.EXTRA_LONGITUDE, location.getLongitude());
            intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, true);
            startActivityForResult(intent, ADJUST_LOCATION_REQUEST_CODE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADJUST_LOCATION_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            double newLat = data.getDoubleExtra(MapActivity.EXTRA_LATITUDE, 0);
            double newLon = data.getDoubleExtra(MapActivity.EXTRA_LONGITUDE, 0);
            if (location == null) {
                location = new Location("manual");
            }
            location.setLatitude(newLat);
            location.setLongitude(newLon);
            updateLocationUI(location);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation(false);
            }
        }
    }

    private void updateRecordInfo() {

        updater = new Runnable() {
            @Override
            public void run() {

                String currDuration = String.format(Locale.getDefault(), "%02d m %02d s",
                        TimeUnit.SECONDS.toMinutes(durationSec),
                        durationSec - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(durationSec))
                );
                
                String currSize = "0 KB";
                if (tempRecFile != null) {
                    currSize = Utils.formatFileSize(tempRecFile.length());
                }

                durationView.setText(currDuration);
                sizeView.setText(currSize);

//                call update every second
                timerHandler.postDelayed(updater,1000);
                durationSec++;
            }
        };
        timerHandler.post(updater);
    }

    private void resetRecordInfo() {
        // stop Runnable
        timerHandler.removeCallbacks(updater);
        durationView.setText(getString(R.string.duration_default));
        durationSec = 0;
        sizeView.setText(getString(R.string.size_default));
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
//        remove updater when activity destroys to prevent memory leak
        timerHandler.removeCallbacks(updater);
    }
}
