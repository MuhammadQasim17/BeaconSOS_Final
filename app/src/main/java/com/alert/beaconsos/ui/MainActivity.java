package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.services.BeaconService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private TriggerManager triggerManager;
    private BottomNavigationView bottomNavigationView;
    private ViewPager2 viewPager;
    private View onlineStatusDot;
    private boolean isNavigatingProgrammatically = false;
    private DatabaseReference userPresenceRef;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        triggerManager = new TriggerManager(this);
        
        if (!triggerManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        viewPager = findViewById(R.id.viewPager);
        viewPager.setUserInputEnabled(false); // Disable swipe to prevent interference with Map zoom/pan
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        onlineStatusDot = findViewById(R.id.onlineStatusDot);

        // Handle Window Insets for both 3-button and gesture navigation
        int basePaddingBottom = bottomNavigationView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePaddingBottom + systemBars.bottom);
            return insets;
        });

        setupViewPager();

        updateOnlineStatus();
        setupNetworkCallback();

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int position = getPositionFromItemId(item.getItemId());
            if (viewPager.getCurrentItem() != position) {
                isNavigatingProgrammatically = true;
                // Jump to the new page without slide animation
                viewPager.setCurrentItem(position, false);
            }
            animateNavIconPulse(item.getItemId());
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // Indicator removed
            }

            @Override
            public void onPageSelected(int position) {
                int itemId = getItemIdFromPosition(position);
                if (bottomNavigationView.getSelectedItemId() != itemId) {
                    bottomNavigationView.setSelectedItemId(itemId);
                }
            }
        });

        int targetId = getIntent().getIntExtra("TARGET_NAV_ID", R.id.nav_home);
        bottomNavigationView.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                bottomNavigationView.setSelectedItemId(targetId);
            }
        });

        startBeaconService();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomNavigationView.getSelectedItemId() != R.id.nav_home) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Initialize presence tracking
        String userId = triggerManager.getUserId();
        if (userId != null && !userId.isEmpty()) {
            userPresenceRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("online");
            userPresenceRef.setValue(true);
            userPresenceRef.onDisconnect().setValue(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userPresenceRef != null) {
            userPresenceRef.setValue(true);
        }
        updateOnlineStatus();
        updateBottomNavProfileIcon(bottomNavigationView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // We keep it true if they just switched activities within the app, 
        // but onDisconnect handles actual app close/crash.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    private void setupNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull android.net.Network network) {
                    runOnUiThread(() -> updateOnlineStatus());
                }

                @Override
                public void onLost(@NonNull android.net.Network network) {
                    runOnUiThread(() -> updateOnlineStatus());
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        }
    }

    private void setupViewPager() {
        // Use setOffscreenPageLimit to keep fragments in memory
        viewPager.setOffscreenPageLimit(4);
        
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                Log.d(TAG, "Creating fragment at position: " + position);
                switch (position) {
                    case 0: return new HomeFragment();
                    case 1: return new SafetyTimerFragment();
                    case 2: return new MapFragment();
                    case 3: return new SosHistoryFragment();
                    case 4: return new ProfileFragment();
                    default: return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 5;
            }
        };
        viewPager.setAdapter(adapter);
    }

    private int getPositionFromItemId(int itemId) {
        if (itemId == R.id.nav_home) return 0;
        if (itemId == R.id.nav_safety_timer) return 1;
        if (itemId == R.id.nav_map) return 2;
        if (itemId == R.id.nav_history) return 3;
        if (itemId == R.id.nav_profile) return 4;
        return 0;
    }

    private int getItemIdFromPosition(int position) {
        switch (position) {
            case 0: return R.id.nav_home;
            case 1: return R.id.nav_safety_timer;
            case 2: return R.id.nav_map;
            case 3: return R.id.nav_history;
            case 4: return R.id.nav_profile;
            default: return R.id.nav_home;
        }
    }

    private void animateNavIconPulse(int itemId) {
        View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView != null) {
            itemView.animate()
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> 
                        itemView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(450)
                                .setInterpolator(new OvershootInterpolator(1.5f))
                                .start()
                    ).start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int targetId = intent.getIntExtra("TARGET_NAV_ID", R.id.nav_home);
        bottomNavigationView.setSelectedItemId(targetId);
    }

    private void updateOnlineStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isConnected = false;
        if (cm != null) {
            Network network = cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                if (nc != null) {
                    // Check for specific transports (WiFi or Mobile Data) and internet capability
                    isConnected = (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                  nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) &&
                                  nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            }
        }
        
        if (onlineStatusDot != null) {
            final boolean finalIsConnected = isConnected;
            runOnUiThread(() -> onlineStatusDot.setBackgroundResource(finalIsConnected ? R.drawable.status_dot_green : R.drawable.status_dot_red));
        }
    }

    private void startBeaconService() {
        Intent intent = new Intent(this, BeaconService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
