package com.alert.beaconsos;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.alert.beaconsos.data.TriggerManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
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

        // Initialize App Check with Debug Provider for local testing
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
        
        TriggerManager triggerManager = new TriggerManager(this);
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
