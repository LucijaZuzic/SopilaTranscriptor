package com.example.arcibald160.sopilatranscriptor;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.arcibald160.sopilatranscriptor.helpers.Utils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfActivity extends AppCompatActivity {

    private File mPdfFile;
    private TextView pdfName, pdfSize, pdfDate, locationView;
    private LinearLayout pdfContainer;
    private double mLat = 0, mLon = 0; // Default to 0,0
    private FusedLocationProviderClient fusedLocationClient;
    private boolean locationLoaded = false;

    private static final int PERMISSION_LOCATION_REQUEST_CODE = 100;
    private static final int ADJUST_LOCATION_REQUEST_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        pdfName = findViewById(R.id.pdf_name_id);
        pdfSize = findViewById(R.id.free_space);
        pdfDate = findViewById(R.id.date_view);
        locationView = findViewById(R.id.location_text_view);
        pdfContainer = findViewById(R.id.pdf_container_layout); // Note: Need to update XML ID or use parent
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 1. Get Bundle and check if it's null
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            pdfName.setText(R.string.error_no_data);
            return; // Stop onCreate to prevent crash
        }

        // 2. Get key and object
        String key = getString(R.string.pdf_extra_key);
        Object fileObj = extras.get(key);

        // 3. Check if object is really a File and exists on disk
        if (fileObj instanceof File) {
            mPdfFile = (File) fileObj;
            
            loadLocation();
            displayPdfInfo();
        } else {
            pdfName.setText(R.string.error_wrong_file_format);
        }

        // Map button logic (View location only)
        Button mapBtn = findViewById(R.id.buttonFake);
        if (mapBtn != null) {
            mapBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!locationLoaded && !isReadOnly()) {
                        fetchLocation(true);
                    } else {
                        openMap();
                    }
                }
            });
        }
        
        // Image button logic (Fetch Location)
        View locImgBtn = findViewById(R.id.location_img_btn);
        if (locImgBtn != null) {
            locImgBtn.setVisibility(View.VISIBLE);
            if (isReadOnly()) {
                // Display but make non-clickable for the protected file
                locImgBtn.setOnClickListener(null);
                locImgBtn.setClickable(false);
                locImgBtn.setAlpha(0.5f);
            } else {
                locImgBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // User manual click: ALWAYS show toast results
                        fetchLocation(true);
                    }
                });
            }
        }

        // Text view logic (Adjust Location)
        if (locationView != null) {
            locationView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isReadOnly()) {
                        adjustLocation();
                    }
                }
            });
        }
    }

    private boolean isReadOnly() {
        return mPdfFile != null && mPdfFile.getName().equals("sadila_je_mare_rf.pdf");
    }

    private void openMap() {
        Intent intent = new Intent(PdfActivity.this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, mLat);
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, mLon);
        intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, false); 
        startActivity(intent);
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation(final boolean showToast) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (showToast) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION_REQUEST_CODE);
            }
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location loc) {
                if (loc != null) {
                    mLat = loc.getLatitude();
                    mLon = loc.getLongitude();
                    locationLoaded = true;
                    updateLocationUI();
                    saveLocation();
                    if (showToast) {
                        Toast.makeText(PdfActivity.this, getString(R.string.location_success), Toast.LENGTH_SHORT).show();
                    }
                } else if (showToast) {
                    Toast.makeText(PdfActivity.this, getString(R.string.location_fail), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void adjustLocation() {
        if (locationLoaded) {
            startAdjustLocation();
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION_REQUEST_CODE);
                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location loc) {
                    if (loc != null) {
                        mLat = loc.getLatitude();
                        mLon = loc.getLongitude();
                        locationLoaded = true;
                        updateLocationUI();
                        saveLocation();
                        startAdjustLocation();
                    } else {
                        Toast.makeText(PdfActivity.this, R.string.error_fetch_location, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void startAdjustLocation() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra(MapActivity.EXTRA_LATITUDE, mLat);
        intent.putExtra(MapActivity.EXTRA_LONGITUDE, mLon);
        intent.putExtra(MapActivity.EXTRA_ADJUST_MODE, true);
        startActivityForResult(intent, ADJUST_LOCATION_REQUEST_CODE);
    }

    private void loadLocation() {
        if (mPdfFile == null) return;
        
        if (isReadOnly()) {
            locationView.setText(R.string.default_location_name);
            mLat = 45.0441;
            mLon = 14.4714;
            locationLoaded = true;
            return;
        }

        SharedPreferences prefs = getSharedPreferences(getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        
        if (prefs.contains("lat_" + mPdfFile.getName())) {
            mLat = prefs.getFloat("lat_" + mPdfFile.getName(), 0f);
            mLon = prefs.getFloat("lon_" + mPdfFile.getName(), 0f);
            locationLoaded = true;
            String savedLocText = prefs.getString("loc_" + mPdfFile.getName(), getString(R.string.location_not_available));
            locationView.setText(savedLocText);
        } else {
            locationView.setText(R.string.location_not_available);
            fetchLocation(false);
        }
    }

    private void saveLocation() {
        if (mPdfFile == null || isReadOnly()) return;
        SharedPreferences prefs = getSharedPreferences(getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lat_" + mPdfFile.getName(), (float) mLat);
        editor.putFloat("lon_" + mPdfFile.getName(), (float) mLon);
        editor.putString("loc_" + mPdfFile.getName(), locationView.getText().toString());
        editor.apply();
    }

    private void updateLocationUI() {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(mLat, mLon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String cityName = address.getLocality();
                if (cityName == null) cityName = address.getSubAdminArea();
                String countryName = address.getCountryName();
                locationView.setText(String.format("%s, %s", cityName, countryName));
            } else {
                locationView.setText(R.string.location_not_available);
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationView.setText(R.string.location_not_available);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADJUST_LOCATION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            mLat = data.getDoubleExtra(MapActivity.EXTRA_LATITUDE, mLat);
            mLon = data.getDoubleExtra(MapActivity.EXTRA_LONGITUDE, mLon);
            locationLoaded = true;
            updateLocationUI();
            saveLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation(true);
            }
        }
    }

    private void displayPdfInfo() {
        if (mPdfFile != null && mPdfFile.exists()) {
            pdfName.setText(mPdfFile.getName());
            String size = Utils.formatFileSize(mPdfFile.length());
            pdfSize.setText(size);

            String date = new SimpleDateFormat("yyyy-MM-dd", new Locale("hr", "HR"))
                    .format(new Date(mPdfFile.lastModified()));
            pdfDate.setText(date);

            loadPdfWithNativeRenderer();
        } else {
            pdfName.setText(R.string.error_file_not_found);
        }
    }

    private void loadPdfWithNativeRenderer() {
        try {
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(mPdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fd);
            
            if (renderer.getPageCount() > 0) {
                PdfRenderer.Page page = renderer.openPage(0);
                
                int width = getResources().getDisplayMetrics().widthPixels;
                int height = (int) ((float)width * page.getHeight() / page.getWidth());
                
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                
                ImageView imageView = new ImageView(this);
                imageView.setImageBitmap(bitmap);
                
                pdfContainer.removeAllViews();
                pdfContainer.addView(imageView);
                
                page.close();
            }
            renderer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_opening_pdf, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isReadOnly()) {
            getMenuInflater().inflate(R.menu.sheet_utils_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isReadOnly()) return super.onOptionsItemSelected(item);
        
        int id = item.getItemId();
        if (id == R.id.delete_item) {
            deletePdf();
            return true;
        } else if (id == R.id.rename_item) {
            renamePdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void renamePdf() {
        if (isReadOnly()) return;
        
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(getString(R.string.rename_title, mPdfFile.getName()));
        final EditText newNameEditText = new EditText(this);
        newNameEditText.setText(mPdfFile.getName());

        alertDialog.setView(newNameEditText);
        alertDialog.setPositiveButton(getString(R.string.save_label),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File oldFile = mPdfFile;
                        File newFile = new File(mPdfFile.getParent(), newNameEditText.getText().toString());
                        if (Utils.renameFile(mPdfFile, newFile)) {
                            migrateLocationData(oldFile.getName(), newFile.getName());
                            mPdfFile = newFile;
                            displayPdfInfo();
                        } else {
                            Toast.makeText(PdfActivity.this, R.string.error_renaming_file, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        alertDialog.setNegativeButton(getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    private void migrateLocationData(String oldName, String newName) {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
        if (!prefs.contains("lat_" + oldName)) return;

        float lat = prefs.getFloat("lat_" + oldName, 0);
        float lon = prefs.getFloat("lon_" + oldName, 0);
        String locText = prefs.getString("loc_" + oldName, getString(R.string.location_not_available));

        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lat_" + newName, lat);
        editor.putFloat("lon_" + newName, lon);
        editor.putString("loc_" + newName, locText);
        
        editor.remove("lat_" + oldName);
        editor.remove("lon_" + oldName);
        editor.remove("loc_" + oldName);
        
        editor.apply();
    }

    private void deletePdf() {
        if (isReadOnly()) return;

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(getString(R.string.delete_sheet_warning, mPdfFile.getName()));

        alertDialog.setPositiveButton(getString(R.string.delete_label),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int int_arg) {
                        String fileName = mPdfFile.getName();
                        if (mPdfFile.delete()) {
                            SharedPreferences prefs = getSharedPreferences(getString(R.string.sp_secret_key), Context.MODE_PRIVATE);
                            prefs.edit().remove("lat_" + fileName).remove("lon_" + fileName).remove("loc_" + fileName).apply();
                            finish();
                        } else {
                            Toast.makeText(PdfActivity.this, R.string.error_deleting_file, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        alertDialog.setNegativeButton(getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }
}
