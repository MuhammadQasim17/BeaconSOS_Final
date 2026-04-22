package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.services.BeaconService;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private TriggerManager triggerManager;
    private BottomNavigationView bottomNavigationView;
    private ViewPager2 viewPager;
    private View navSelectionIndicator;
    private ImageView ivToolbarProfile;
    private View onlineStatusDot;
    private boolean isNavigatingProgrammatically = false;
    private DatabaseReference userPresenceRef;

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
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        navSelectionIndicator = findViewById(R.id.nav_selection_indicator);
        ivToolbarProfile = findViewById(R.id.ivToolbarProfile);
        onlineStatusDot = findViewById(R.id.onlineStatusDot);

        setupViewPager();

        ivToolbarProfile.setOnClickListener(v -> bottomNavigationView.setSelectedItemId(R.id.nav_profile));

        updateProfileData();
        updateOnlineStatus();

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int position = getPositionFromItemId(item.getItemId());
            if (viewPager.getCurrentItem() != position) {
                isNavigatingProgrammatically = true;
                // Jump to the new page without slide animation
                viewPager.setCurrentItem(position, false);
                // Manually animate the indicator to the new position
                animateIndicatorToPosition(position);
            }
            animateNavIconPulse(item.getItemId());
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // Only update position via scroll callback if it's a user swipe
                if (!isNavigatingProgrammatically) {
                    updateIndicatorPosition(position, positionOffset);
                }
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
        String phone = triggerManager.getUserPhone();
        if (!phone.isEmpty()) {
            userPresenceRef = FirebaseDatabase.getInstance().getReference("users").child(phone).child("online");
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        // We keep it true if they just switched activities within the app, 
        // but onDisconnect handles actual app close/crash.
    }

    private void updateIndicatorPosition(int position, float positionOffset) {
        if (navSelectionIndicator == null || bottomNavigationView == null) return;
        
        float navWidth = bottomNavigationView.getWidth();
        if (navWidth == 0) return;

        float itemWidth = navWidth / 4f;
        float translationX = (position + positionOffset) * itemWidth;
        
        navSelectionIndicator.setTranslationX(translationX);
    }

    private void animateIndicatorToPosition(int position) {
        if (navSelectionIndicator == null || bottomNavigationView == null) return;

        float navWidth = bottomNavigationView.getWidth();
        if (navWidth == 0) return;

        float itemWidth = navWidth / 4f;
        float targetX = position * itemWidth;

        navSelectionIndicator.animate()
                .translationX(targetX)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> isNavigatingProgrammatically = false)
                .start();
    }

    private void setupViewPager() {
        // Use setOffscreenPageLimit to keep fragments in memory
        viewPager.setOffscreenPageLimit(3);
        
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                Log.d(TAG, "Creating fragment at position: " + position);
                switch (position) {
                    case 0: return new HomeFragment();
                    case 1: return new ChatFragment();
                    case 2: return new MapFragment();
                    case 3: return new ProfileFragment();
                    default: return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        };
        viewPager.setAdapter(adapter);
    }

    private int getPositionFromItemId(int itemId) {
        if (itemId == R.id.nav_home) return 0;
        if (itemId == R.id.nav_message) return 1;
        if (itemId == R.id.nav_map) return 2;
        if (itemId == R.id.nav_profile) return 3;
        return 0;
    }

    private int getItemIdFromPosition(int position) {
        switch (position) {
            case 0: return R.id.nav_home;
            case 1: return R.id.nav_message;
            case 2: return R.id.nav_map;
            case 3: return R.id.nav_profile;
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

    private void updateProfileData() {
        String imageUriString = triggerManager.getProfileImageUri();
        Glide.with(this)
                .load(imageUriString != null && !imageUriString.isEmpty() ? Uri.parse(imageUriString) : null)
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .circleCrop()
                .into(ivToolbarProfile);
    }

    private void updateOnlineStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isConnected = false;
        if (cm != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            isConnected = nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        onlineStatusDot.setBackgroundResource(isConnected ? R.drawable.status_dot_green : R.drawable.status_dot_red);
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
