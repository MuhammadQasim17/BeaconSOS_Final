package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alert.beaconsos.R;
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
    private View customLoading, layoutMapEmpty;
    private TextView tvLoadingText, tvMapStatus;
    private View mapStatusDot;

    private Marker myLocationMarker = null;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (layoutMapEmpty != null) layoutMapEmpty.setVisibility(View.GONE);
                    initMapLocation();
                } else {
                    if (layoutMapEmpty != null) layoutMapEmpty.setVisibility(View.VISIBLE);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(requireActivity().getPackageName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        customLoading = view.findViewById(R.id.customLoading);
        tvLoadingText = view.findViewById(R.id.tvLoadingText);
        layoutMapEmpty = view.findViewById(R.id.layoutMapEmpty);
        tvMapStatus = view.findViewById(R.id.tvMapStatus);
        mapStatusDot = view.findViewById(R.id.mapStatusDot);

        View btnRecenterMap = view.findViewById(R.id.btnRecenterMap);
        View btnZoomIn = view.findViewById(R.id.btnZoomIn);
        View btnZoomOut = view.findViewById(R.id.btnZoomOut);
        View btnGrantPermission = view.findViewById(R.id.btnGrantPermission);

        if (btnRecenterMap != null) {
            btnRecenterMap.setOnClickListener(v -> initMapLocation());
        }

        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> {
                if (map != null) map.getController().zoomIn();
            });
        }

        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> {
                if (map != null) map.getController().zoomOut();
            });
        }

        if (btnGrantPermission != null) {
            btnGrantPermission.setOnClickListener(v -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION));
        }

        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.getController().setZoom(16.0);

        initMapLocation();

        return view;
    }

    private void initMapLocation() {
        Context context = getContext();
        if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (layoutMapEmpty != null) layoutMapEmpty.setVisibility(View.GONE);
            if (tvMapStatus != null) tvMapStatus.setText("GPS Active");
            if (mapStatusDot != null) mapStatusDot.setBackgroundResource(R.drawable.status_dot_green);
            
            // Setup OSM Location Overlay
            if (mLocationOverlay == null && map != null) {
                mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), map);
                mLocationOverlay.enableMyLocation();
                mLocationOverlay.setDrawAccuracyEnabled(false);
                map.getOverlays().add(mLocationOverlay);
            }

            // Always place marker immediately at current map center or initial point
            if (map != null && map.getMapCenter() != null) {
                GeoPoint initialPoint = new GeoPoint(map.getMapCenter().getLatitude(), map.getMapCenter().getLongitude());
                updateLocationMarker(initialPoint);
            }

            showLoading("Locating Device...");
            
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        hideLoading();
                        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                        map.getController().animateTo(startPoint);
                        map.getController().setZoom(17.0);
                        updateLocationMarker(startPoint);
                    } else {
                        waitForFix();
                    }
                })
                .addOnFailureListener(e -> waitForFix());

            map.postDelayed(() -> {
                if (isAdded()) hideLoading();
            }, 8000);

        } else {
            if (layoutMapEmpty != null) layoutMapEmpty.setVisibility(View.VISIBLE);
            if (tvMapStatus != null) tvMapStatus.setText("GPS Inactive");
            if (mapStatusDot != null) mapStatusDot.setBackgroundResource(R.drawable.status_dot_red);
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
                        updateLocationMarker(myLocation);
                    }
                });
            }
        });
    }

    private synchronized void updateLocationMarker(GeoPoint point) {
        if (map == null || point == null || !isAdded()) return;

        if (myLocationMarker == null) {
            Context ctx = getContext();
            if (ctx == null) return;
            myLocationMarker = new Marker(map);
            myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            myLocationMarker.setRotation(0.0f);
            myLocationMarker.setFlat(false);
            myLocationMarker.setTitle("You are here");
            
            Drawable markerDrawable = ContextCompat.getDrawable(ctx, R.drawable.ic_map_marker_beacon);
            if (markerDrawable != null) {
                myLocationMarker.setIcon(markerDrawable);
            }
            map.getOverlays().add(myLocationMarker);
        }

        myLocationMarker.setPosition(point);
        myLocationMarker.setRotation(0.0f);
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
