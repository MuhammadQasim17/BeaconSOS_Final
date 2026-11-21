package com.alert.beaconsos.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationHelper {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;

    public interface OnLocationResultListener {
        void onLocationReceived(String locationUrl);
    }

    public LocationHelper(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void getLastLocation(OnLocationResultListener listener) {
        if (listener == null) return;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationReceived(null);
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(lastLoc -> {
                        if (lastLoc != null) {
                            String url = "https://www.google.com/maps/search/?api=1&query=" + 
                                         lastLoc.getLatitude() + "," + lastLoc.getLongitude();
                            listener.onLocationReceived(url);
                        } else {
                            requestFreshLocation(listener);
                        }
                    })
                    .addOnFailureListener(e -> requestFreshLocation(listener));
        } catch (Exception e) {
            listener.onLocationReceived(null);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestFreshLocation(OnLocationResultListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationReceived(null);
            return;
        }

        try {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(5000)
                    .setMaxUpdates(1)
                    .build();

            LocationCallback callback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        String url = "https://www.google.com/maps/search/?api=1&query=" + 
                                     location.getLatitude() + "," + location.getLongitude();
                        listener.onLocationReceived(url);
                    } else {
                        listener.onLocationReceived(null);
                    }
                    fusedLocationClient.removeLocationUpdates(this);
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());
        } catch (Exception e) {
            listener.onLocationReceived(null);
        }
    }
}
