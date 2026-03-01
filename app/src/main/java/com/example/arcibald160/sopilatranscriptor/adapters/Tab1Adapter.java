package com.example.arcibald160.sopilatranscriptor.adapters;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.example.arcibald160.sopilatranscriptor.MapActivity;
import com.example.arcibald160.sopilatranscriptor.R;
import com.example.arcibald160.sopilatranscriptor.helpers.NetworkUtils;
import com.example.arcibald160.sopilatranscriptor.helpers.Utils;
import com.example.arcibald160.sopilatranscriptor.tab_fragments.TabFragment1;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class Tab1Adapter extends RecyclerView.Adapter<Tab1Adapter.ListViewHolder> {

    private File[] mRecordings;
    private Context mContext;
    private static String PATH;
    private FusedLocationProviderClient mFusedLocationClient;
    private TabFragment1 mFragment;
    public static final int ADJUST_LOCATION_FOR_WAV_REQUEST_CODE = 103;


    public Tab1Adapter(File[] list, Context context, TabFragment1 fragment) {
        mRecordings = list;
        mContext = context;
        mFragment = fragment;
        PATH = context.getExternalFilesDir(null).toString() + "/" + context.getString(R.string.rec_folder);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // Add this just in case directory exists before reading
        File dir = new File(PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Auto-fetch missing locations on startup
        checkAndFetchMissingLocations();
    }

    private void checkAndFetchMissingLocations() {
        if (mRecordings == null) return;
        
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        boolean needsFetch = false;
        
        for (File file : mRecordings) {
            if (!prefs.contains("lat_" + file.getName())) {
                needsFetch = true;
                break;
            }
        }
        
        if (needsFetch) {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location loc) {
                        if (loc != null) {
                            for (File file : mRecordings) {
                                if (!prefs.contains("lat_" + file.getName())) {
                                    saveLocationForFile(file.getName(), loc.getLatitude(), loc.getLongitude());
                                }
                            }
                            notifyDataSetChanged();
                        }
                    }
                });
            }
        }
    }

    @NonNull
    @Override
    public ListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // new view
        View view = LayoutInflater.from(mContext).inflate(R.layout.list_recordings_view, parent, false);
        return new ListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ListViewHolder holder, int position) {

        final File file = mRecordings[position];
        // bytes to kilo bytes
        String size = Utils.formatFileSize(file.length());
        String duration = Utils.getFileDuration(file);
        final String date = new SimpleDateFormat("yyyy-MM-dd", new Locale("hr", "HR")).format(new Date(file.lastModified()));

        // Set values
        holder.recName.setText(file.getName());
        holder.recTimeAndSize.setText(duration + " - " + size);
        holder.recDateCreated.setText(date);

        // Load saved location
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        String savedLocText = prefs.getString("loc_" + file.getName(), "Location not available");
        holder.recLocation.setText(savedLocText);

        View.OnClickListener playListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                        m.invoke(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    Uri apkURI = FileProvider.getUriForFile(
                            view.getContext(),
                            view.getContext().getPackageName() + ".provider", file);
                    intent.setDataAndType(apkURI, "audio/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    view.getContext().startActivity(intent);
                }
            }
        };

        holder.playButton.setOnClickListener(playListener);
        holder.textContainer.setOnClickListener(playListener);


        holder.menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                PopupMenu recordingMenu = new PopupMenu(view.getContext(), holder.menuButton);
                recordingMenu.getMenuInflater().inflate(R.menu.recording_utils_menu, recordingMenu.getMenu());

                recordingMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                    final Context context = view.getContext();
                    int id = menuItem.getItemId();

                    if (id == R.id.rename_item) {
                        renameListItem(context, file);
                    } else if (id == R.id.delete_item) {
                        deleteListItem(context, file);
                    } else if (id == R.id.export_item) {
                        NetworkUtils.uploadRecording(view.getContext(), file);
                    } else if (id == R.id.show_on_map_item) {
                        showOnMap(file);
                    } else if (id == R.id.fetch_location_item) {
                        fetchLocationForFile(file);
                    } else if (id == R.id.adjust_location_item) {
                        adjustLocationForFile(file);
                    }
                    return true;
                    }
                });
                recordingMenu.show();
            }
        });
    }

    private void showOnMap(File file) {
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        if (!prefs.contains("lat_" + file.getName())) {
            Toast.makeText(mContext, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }
        double lat = prefs.getFloat("lat_" + file.getName(), 0f);
        double lon = prefs.getFloat("lon_" + file.getName(), 0f);

        Intent intent = new Intent(mContext, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, lat);
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, lon);
        intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, false);
        mContext.startActivity(intent);
    }

    private void fetchLocationForFile(final File file) {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(mContext, "Location permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location loc) {
                if (loc != null) {
                    saveLocationForFile(file.getName(), loc.getLatitude(), loc.getLongitude());
                    Toast.makeText(mContext, mContext.getString(R.string.location_success), Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                } else {
                    Toast.makeText(mContext, mContext.getString(R.string.location_fail), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void adjustLocationForFile(final File file) {
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        
        if (prefs.contains("lat_" + file.getName())) {
            double lat = prefs.getFloat("lat_" + file.getName(), 0f);
            double lon = prefs.getFloat("lon_" + file.getName(), 0f);
            startAdjustActivity(file, lat, lon);
        } else {
            // If no location yet, try to fetch it first
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mContext, "Location permission required to fetch starting point", Toast.LENGTH_SHORT).show();
                return;
            }

            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location loc) {
                    if (loc != null) {
                        saveLocationForFile(file.getName(), loc.getLatitude(), loc.getLongitude());
                        notifyDataSetChanged();
                        startAdjustActivity(file, loc.getLatitude(), loc.getLongitude());
                    } else {
                        Toast.makeText(mContext, "Could not fetch current location. Please try again or move to a place with better reception.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void startAdjustActivity(File file, double lat, double lon) {
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        Intent intent = new Intent(mContext, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, lat);
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, lon);
        intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, true);
        
        // Save the file name being adjusted so onActivityResult knows which file to update
        prefs.edit().putString("adjusting_file_name", file.getName()).apply();
        
        if (mFragment != null) {
            mFragment.startActivityForResult(intent, ADJUST_LOCATION_FOR_WAV_REQUEST_CODE);
        } else if (mContext instanceof Activity) {
            ((Activity) mContext).startActivityForResult(intent, ADJUST_LOCATION_FOR_WAV_REQUEST_CODE);
        }
    }

    public void saveLocationForFile(String fileName, double lat, double lon) {
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lat_" + fileName, (float) lat);
        editor.putFloat("lon_" + fileName, (float) lon);

        // Update address string
        Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String cityName = address.getLocality();
                if (cityName == null) cityName = address.getSubAdminArea();
                String countryName = address.getCountryName();
                editor.putString("loc_" + fileName, String.format("%s, %s", cityName, countryName));
            } else {
                editor.putString("loc_" + fileName, "Location not available");
            }
        } catch (IOException e) {
            e.printStackTrace();
            editor.putString("loc_" + fileName, "Location not available");
        }
        editor.apply();
    }

    @Override
    public int getItemCount() {

        if (mRecordings == null) {
            return 0;
        }

        return mRecordings.length;
    }

    public void refreshRecDir() {
        mRecordings = null;
        File recordingsDirectory = new File(PATH);
        mRecordings = recordingsDirectory.listFiles();
        
        // Auto-fetch missing locations after refresh
        checkAndFetchMissingLocations();

        notifyDataSetChanged();
    }

    private void renameListItem(Context context, final File file) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(context.getString(R.string.rename_title, file.getName()));
        final EditText newNameEditText = new EditText(context);
        newNameEditText.setText(file.getName());

        alertDialog.setView(newNameEditText);
        alertDialog.setPositiveButton(context.getString(R.string.save_label),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        File oldFile = file;
                        File newFile = new File(file.getParent(), newNameEditText.getText().toString());
                        if (Utils.renameFile(file, newFile)) {
                            migrateLocationData(oldFile.getName(), newFile.getName());
                            refreshRecDir();
                        }
                    }
                });
        alertDialog.setNegativeButton(context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    private void migrateLocationData(String oldName, String newName) {
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        if (!prefs.contains("lat_" + oldName)) return;

        float lat = prefs.getFloat("lat_" + oldName, 0);
        float lon = prefs.getFloat("lon_" + oldName, 0);
        String locText = prefs.getString("loc_" + oldName, "Location not available");

        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lat_" + newName, lat);
        editor.putFloat("lon_" + newName, lon);
        editor.putString("loc_" + newName, locText);
        
        editor.remove("lat_" + oldName);
        editor.remove("lon_" + oldName);
        editor.remove("loc_" + oldName);
        
        editor.apply();
    }

    private void deleteListItem(Context context, final File file) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(context.getString(R.string.delete_recording_warning, file.getName()));

        alertDialog.setPositiveButton(context.getString(R.string.delete_label),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String fileName = file.getName();
                        if (file.delete()) {
                            SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
                            prefs.edit().remove("lat_" + fileName).remove("lon_" + fileName).remove("loc_" + fileName).apply();
                            refreshRecDir();
                        }
                    }
                });
        alertDialog.setNegativeButton(context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }


    public class ListViewHolder extends RecyclerView.ViewHolder{
        TextView recName, recTimeAndSize, recDateCreated, recLocation;
        ImageButton menuButton, playButton;
        LinearLayout textContainer;

        public ListViewHolder(View itemView) {
            super(itemView);
            recName = itemView.findViewById(R.id.recording_name);
            recTimeAndSize = itemView.findViewById(R.id.time_and_size);
            recDateCreated = itemView.findViewById(R.id.date_created);
            recLocation = itemView.findViewById(R.id.recording_location);
            playButton = itemView.findViewById(R.id.play_recording);
            menuButton = itemView.findViewById(R.id.more_button);
            textContainer = (LinearLayout) itemView.findViewById(R.id.rec_entry);
        }
    }
}
