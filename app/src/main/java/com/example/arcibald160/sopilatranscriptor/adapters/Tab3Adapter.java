package com.example.arcibald160.sopilatranscriptor.adapters;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.example.arcibald160.sopilatranscriptor.MapActivity;
import com.example.arcibald160.sopilatranscriptor.PdfActivity;
import com.example.arcibald160.sopilatranscriptor.R;
import com.example.arcibald160.sopilatranscriptor.helpers.Utils;
import com.example.arcibald160.sopilatranscriptor.tab_fragments.TabFragment3;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Tab3Adapter extends RecyclerView.Adapter<Tab3Adapter.ListViewHolder> {

    private Context mContext;
    private File[] mSheets;
    private FusedLocationProviderClient mFusedLocationClient;
    private TabFragment3 mFragment;

    public Tab3Adapter(Context context, TabFragment3 fragment) {
        mContext = context;
        mFragment = fragment;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        
        // Auto-fetch missing locations on startup
        checkAndFetchMissingLocations();
    }

    private void checkAndFetchMissingLocations() {
        if (mSheets == null) return;
        
        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        boolean needsFetch = false;
        
        for (File file : mSheets) {
            // Skip the special protected file
            if (file.getName().equals("sadila_je_mare_rf.pdf")) continue;
            
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
                            for (File file : mSheets) {
                                if (!file.getName().equals("sadila_je_mare_rf.pdf") && !prefs.contains("lat_" + file.getName())) {
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
        View view = LayoutInflater.from(mContext).inflate(R.layout.list_sheets, parent, false);
        return new ListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ListViewHolder holder, int position) {
        final File file = mSheets[position];
        // bytes to kilo bytes
        String size = Utils.formatFileSize(file.length());
        final String date = new SimpleDateFormat("yyyy-MM-dd", new Locale("hr", "HR")).format(new Date(file.lastModified()));

        // Set values
        holder.sheetName.setText(file.getName());
        holder.sheetSize.setText(size);
        holder.sheetDateCreated.setText(date);

        // Check if this is the protected test file
        final boolean isProtectedFile = file.getName().equals("sadila_je_mare_rf.pdf");

        // Load saved location
        if (isProtectedFile) {
            holder.sheetLocation.setText(R.string.default_location_name);
        } else {
            SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
            String savedLocText = prefs.getString("loc_" + file.getName(), mContext.getString(R.string.location_not_available));
            holder.sheetLocation.setText(savedLocText);
        }

        holder.menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                PopupMenu sheetMenu = new PopupMenu(view.getContext(), holder.menuButton);
                sheetMenu.getMenuInflater().inflate(R.menu.sheet_utils_menu, sheetMenu.getMenu());

                // Hide rename/delete for protected file
                if (isProtectedFile) {
                    sheetMenu.getMenu().findItem(R.id.rename_item).setVisible(false);
                    sheetMenu.getMenu().findItem(R.id.delete_item).setVisible(false);
                    sheetMenu.getMenu().findItem(R.id.fetch_location_item).setVisible(false);
                    sheetMenu.getMenu().findItem(R.id.adjust_location_item).setVisible(false);
                }

                sheetMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        final Context context = view.getContext();
                        int id = menuItem.getItemId();

                        if (id == R.id.rename_item) {
                            renameListItem(context, file);
                        } else if (id == R.id.delete_item) {
                            deleteListItem(context, file);
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
                sheetMenu.show();
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), PdfActivity.class);

                intent.putExtra(view.getContext().getString(R.string.pdf_extra_key), file);
                intent.setType("application/pdf");
                
                if (isProtectedFile) {
                    intent.putExtra("READ_ONLY_MODE", true);
                }

                view.getContext().startActivity(intent);
            }
        });
    }

    private void showOnMap(File file) {
        double lat, lon;
        if (file.getName().equals("sadila_je_mare_rf.pdf")) {
            lat = 45.0441;
            lon = 14.4714;
        } else {
            SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
            if (!prefs.contains("lat_" + file.getName())) {
                Toast.makeText(mContext, R.string.location_not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            lat = prefs.getFloat("lat_" + file.getName(), 0f);
            lon = prefs.getFloat("lon_" + file.getName(), 0f);
        }

        Intent intent = new Intent(mContext, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, lat);
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, lon);
        intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, false);
        mContext.startActivity(intent);
    }

    private void fetchLocationForFile(final File file) {
        if (file.getName().equals("sadila_je_mare_rf.pdf")) return;

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
        if (file.getName().equals("sadila_je_mare_rf.pdf")) return;

        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        
        if (prefs.contains("lat_" + file.getName())) {
            double lat = prefs.getFloat("lat_" + file.getName(), 0f);
            double lon = prefs.getFloat("lon_" + file.getName(), 0f);
            startAdjustActivity(file, lat, lon);
        } else {
            // Try fetching first if no location available
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
                        Toast.makeText(mContext, R.string.error_fetch_location, Toast.LENGTH_LONG).show();
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
        
        // Use a shared key to identify which file is being updated
        prefs.edit().putString("adjusting_file_name", file.getName()).apply();
        
        if (mFragment != null) {
            mFragment.startActivityForResult(intent, TabFragment3.ADJUST_LOCATION_FOR_PDF_REQUEST_CODE);
        }
    }

    public void saveLocationForFile(String fileName, double lat, double lon) {
        if (fileName.equals("sadila_je_mare_rf.pdf")) return;

        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lat_" + fileName, (float) lat);
        editor.putFloat("lon_" + fileName, (float) lon);

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
                editor.putString("loc_" + fileName, mContext.getString(R.string.location_not_available));
            }
        } catch (IOException e) {
            e.printStackTrace();
            editor.putString("loc_" + fileName, mContext.getString(R.string.location_not_available));
        }
        editor.apply();
    }

    private void renameListItem(Context context, final File file) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(context.getString(R.string.rename_title, file.getName()));
        final EditText newNameEditText = new EditText(context);
        newNameEditText.setText(file.getName());

        alertDialog.setView(newNameEditText);
        alertDialog.setPositiveButton(
            context.getString(R.string.save_label),
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    File oldFile = file;
                    File newFile = new File(file.getParent(), newNameEditText.getText().toString());
                    if (Utils.renameFile(file, newFile)) {
                        migrateLocationData(oldFile.getName(), newFile.getName());
                        refreshSheetDir();
                    }
                }
            }
        );
        alertDialog.setNegativeButton(context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    private void migrateLocationData(String oldName, String newName) {
        if (oldName.equals("sadila_je_mare_rf.pdf")) return;

        SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        if (!prefs.contains("lat_" + oldName)) return;

        float lat = prefs.getFloat("lat_" + oldName, 0);
        float lon = prefs.getFloat("lon_" + oldName, 0);
        String locText = prefs.getString("loc_" + oldName, mContext.getString(R.string.location_not_available));

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
        alertDialog.setTitle(context.getString(R.string.delete_sheet_warning, file.getName()));

        alertDialog.setPositiveButton(context.getString(R.string.delete_label),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String fileName = file.getName();
                        if (file.delete()) {
                            SharedPreferences prefs = mContext.getSharedPreferences(mContext.getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
                            prefs.edit().remove("lat_" + fileName).remove("lon_" + fileName).remove("loc_" + fileName).apply();
                            refreshSheetDir();
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

    @Override
    public int getItemCount() {

        if (mSheets == null) {
            return 0;
        }

        return mSheets.length;
    }

    public void refreshSheetDir() {
        mSheets = null;
        File sheetsDirectory = Utils.getDownloadsDir(mContext);
        if (sheetsDirectory != null) {
            mSheets = sheetsDirectory.listFiles();
        }
        
        // Auto-fetch missing locations after refresh
        checkAndFetchMissingLocations();

        notifyDataSetChanged();
    }

    public class ListViewHolder extends RecyclerView.ViewHolder {
        TextView sheetName, sheetDateCreated, sheetSize, sheetLocation;
        ImageButton menuButton;

        public ListViewHolder(View itemView) {
            super(itemView);
            sheetName = itemView.findViewById(R.id.sheet_name);
            sheetDateCreated = itemView.findViewById(R.id.sheet_date_created);
            sheetSize = itemView.findViewById(R.id.sheet_size);
            sheetLocation = itemView.findViewById(R.id.sheet_location);
            menuButton = itemView.findViewById(R.id.more_button);
        }
    }
}
