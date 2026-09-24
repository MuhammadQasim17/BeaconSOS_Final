package com.alert.beaconsos;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.alert.beaconsos.data.TriggerManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

public class BeaconApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Emoji Manager
        EmojiManager.install(new GoogleEmojiProvider());

        // Explicitly initialize Firebase
        FirebaseApp.initializeApp(this);

        // Re-enable App Check but with the Debug Provider for testing
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());

        // FORCE the token to appear in red in the Logcat
        android.util.Log.e("APP_CHECK_TOKEN", "BEACON APPLICATION STARTED - REQUESTING TOKEN...");
        
        firebaseAppCheck.getAppCheckToken(false)
            .addOnSuccessListener(token -> {
                android.util.Log.e("APP_CHECK_TOKEN", "SUCCESS! Check logs for 'DebugAppCheckProvider' to get the actual token.");
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("APP_CHECK_TOKEN", "FAILED to get token: " + e.getMessage());
            });
        
        TriggerManager triggerManager = new TriggerManager(this);
        
        // Safety Check: If the app was restored from backup but the session is invalid, clear data.
        if (triggerManager.isLoggedIn() && FirebaseAuth.getInstance().getCurrentUser() == null) {
            triggerManager.clearData();
        } else if (!triggerManager.isLoggedIn() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            // Logged in via Firebase but no local data - Force Signout to show Login Screen
            FirebaseAuth.getInstance().signOut();
        }

        int themeMode = triggerManager.getThemeMode();
        
        switch (themeMode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
