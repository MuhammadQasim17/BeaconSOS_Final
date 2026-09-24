package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MenuItemCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyThemeMode();
        super.onCreate(savedInstanceState);
    }

    private void applyThemeMode() {
        TriggerManager triggerManager = new TriggerManager(this);
        int themeMode = triggerManager.getThemeMode();
        int targetMode;
        switch (themeMode) {
            case 1: targetMode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case 2: targetMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(updateBaseContextLocale(newBase));
    }

    private Context updateBaseContextLocale(Context context) {
        TriggerManager triggerManager = new TriggerManager(context);
        String language = triggerManager.getLanguage();
        String langCode = getLanguageCode(language);
        
        Locale locale = Locale.forLanguageTag(langCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        
        return context.createConfigurationContext(configuration);
    }

    private String getLanguageCode(String language) {
        switch (language) {
            case "Urdu": return "ur";
            case "Spanish": return "es";
            case "Russian": return "ru";
            case "Chinese": return "zh";
            case "Japanese": return "ja";
            case "Arabic": return "ar";
            default: return "en";
        }
    }

    protected void updateBottomNavProfileIcon(BottomNavigationView bnv) {
        if (bnv == null) return;
        
        // 1. Disable global tinting
        bnv.setItemIconTintList(null);

        // 2. Apply tints to all items EXCEPT Profile
        ColorStateList navTint = ContextCompat.getColorStateList(this, R.color.nav_item_color);
        Menu menu = bnv.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() != R.id.nav_profile) {
                MenuItemCompat.setIconTintList(item, navTint);
            } else {
                MenuItemCompat.setIconTintList(item, null);
            }
        }

        // 3. Load the actual Profile Photo
        TriggerManager triggerManager = new TriggerManager(this);
        String imageUriString = triggerManager.getProfileImageUri();
        MenuItem profileItem = menu.findItem(R.id.nav_profile);

        if (imageUriString == null || imageUriString.isEmpty()) {
            // If no custom profile photo, use the vector drawable directly to keep it sharp
            if (profileItem != null) {
                profileItem.setIcon(R.drawable.ic_profile_new);
                // Ensure no tint is applied to the profile icon
                MenuItemCompat.setIconTintList(profileItem, null);
            }
        } else {
            // Load custom profile photo via Glide
            Glide.with(this)
                    .asBitmap()
                    .load(imageUriString)
                    .placeholder(R.drawable.ic_profile_new)
                    .error(R.drawable.ic_profile_new)
                    .circleCrop()
                    .override(512, 512) // Matched high resolution with large profile to prevent blurriness
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            if (profileItem != null) {
                                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), resource);
                                profileItem.setIcon(bitmapDrawable);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) { }
                    });
        }
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, Runnable onConfirm) {
        showGlassyConfirmDialog(title, message, positiveText, "Cancel", onConfirm);
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, String negativeText, Runnable onConfirm) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_glassy_dialog, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.GlassyDialog)
                .setView(dialogView)
                .create();

        // Ensure the dialog window background is transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.dialogMessage);
        Button btnNegative = dialogView.findViewById(R.id.btnNegative);
        Button btnPositive = dialogView.findViewById(R.id.btnPositive);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);
        if (btnPositive != null) btnPositive.setText(positiveText);
        if (btnNegative != null) btnNegative.setText(negativeText);

        if (btnNegative != null) {
            btnNegative.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnPositive != null) {
            btnPositive.setOnClickListener(v -> {
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            });
        }

        dialog.show();

        // Set dialog width
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }
}
