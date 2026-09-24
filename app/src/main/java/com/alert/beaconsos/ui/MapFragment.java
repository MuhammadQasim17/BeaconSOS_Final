package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MapFragment extends Fragment {

    private MapView map = null;
    private MyLocationNewOverlay mLocationOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private TriggerManager triggerManager;
    private View customLoading;
    private TextView tvLoadingText;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    initMapLocation();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Important: Set the user agent to avoid getting banned from OSM servers
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(requireActivity().getPackageName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        triggerManager = new TriggerManager(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        customLoading = view.findViewById(R.id.customLoading);
        tvLoadingText = view.findViewById(R.id.tvLoadingText);

        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        initMapLocation();

        return view;
    }

    private void initMapLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            // Setup OSM Location Overlay
            if (mLocationOverlay == null) {
                mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
                mLocationOverlay.enableMyLocation();
                mLocationOverlay.enableFollowLocation();
                map.getOverlays().add(mLocationOverlay);
            }

            showLoading("Locating Device...");
            
            // Strategy: Try last location first, then wait for fix, and set a safety timeout
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        hideLoading();
                        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                        map.getController().animateTo(startPoint);
                        map.getController().setZoom(17.0);
                        addLocationMarker(startPoint);
                    } else {
                        // If no cached location, wait for the overlay to get a fix
                        waitForFix();
                    }
                })
                .addOnFailureListener(e -> {
                    waitForFix();
                });

            // Safety timeout: hide loading after 8 seconds anyway to let user interact with map
            map.postDelayed(() -> {
                if (isAdded()) hideLoading();
            }, 8000);

        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void waitForFix() {
        mLocationOverlay.runOnFirstFix(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hideLoading();
                    GeoPoint myLocation = mLocationOverlay.getMyLocation();
                    if (myLocation != null) {
                        map.getController().animateTo(myLocation);
                        addLocationMarker(myLocation);
                    }
                });
            }
        });
    }

    private void addLocationMarker(GeoPoint point) {
        // Remove existing markers
        for (int i = 0; i < map.getOverlays().size(); i++) {
            if (map.getOverlays().get(i) instanceof Marker) {
                map.getOverlays().remove(i);
                i--;
            }
        }
        
        Marker startMarker = new Marker(map);
        startMarker.setPosition(point);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("You are here");
        startMarker.setIcon(ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default));
        map.getOverlays().add(startMarker);
        map.invalidate();
    }

    private void showLoading(String message) {
        if (customLoading != null) {
            customLoading.setVisibility(View.VISIBLE);
            if (tvLoadingText != null) tvLoadingText.setText(message.toUpperCase());
            LoadingUtils.startCustomAnimation(customLoading);
        }
    }

    private void hideLoading() {
        if (customLoading != null) {
            customLoading.setVisibility(View.GONE);
            LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
