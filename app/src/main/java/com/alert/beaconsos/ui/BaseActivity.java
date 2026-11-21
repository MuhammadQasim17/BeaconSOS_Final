package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
        setupStatusBar();
    }

    protected void setupStatusBar() {
        Window window = getWindow();
        boolean isDark = isNightMode();
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
        insetsController.setAppearanceLightNavigationBars(!isDark);
    }

    protected boolean isNightMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
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
        
        // 1. Set global icon tint list for navigation items
        ColorStateList navTint = ContextCompat.getColorStateList(this, R.color.nav_item_color);
        bnv.setItemIconTintList(navTint);

        // 2. Load custom user profile photo if set, or use standard Flaticon line-art outline vector icon
        TriggerManager triggerManager = new TriggerManager(this);
        String imageUriString = triggerManager.getProfileImageUri();
        Menu menu = bnv.getMenu();
        MenuItem profileItem = menu.findItem(R.id.nav_profile);

        if (profileItem == null) return;

        if (imageUriString != null && !imageUriString.isEmpty()) {
            boolean isSelected = profileItem.isChecked();
            int activeColor = ContextCompat.getColor(this, R.color.color_primary);
            int inactiveColor = ContextCompat.getColor(this, R.color.color_on_surface_variant);

            Glide.with(this)
                    .asBitmap()
                    .load(imageUriString)
                    .circleCrop()
                    .override(128, 128)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            ProfilePhotoDrawable photoDrawable = new ProfilePhotoDrawable(resource, isSelected, activeColor, inactiveColor);
                            profileItem.setIcon(photoDrawable);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) { }
                    });
        } else {
            // No custom photo set: use clean line-art Flaticon outline vector icon matching Home, Timer, Map, History
            profileItem.setIcon(R.drawable.ic_nav_profile);
        }
    }

    /**
     * Circular Custom Profile Photo Drawable with Active/Inactive Ring
     * Used when the user has uploaded a custom profile photo!
     */
    public static class ProfilePhotoDrawable extends Drawable {
        private final Bitmap bitmap;
        private final Paint photoPaint;
        private final Paint borderPaint;

        public ProfilePhotoDrawable(Bitmap bitmap, boolean isSelected, int activeColor, int inactiveColor) {
            this.bitmap = bitmap;
            this.photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            this.borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.borderPaint.setStyle(Paint.Style.STROKE);
            this.borderPaint.setStrokeWidth(4.0f);
            this.borderPaint.setColor(isSelected ? activeColor : inactiveColor);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) return;

            Rect bounds = getBounds();
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float width = bounds.width();
            float height = bounds.height();
            float size = Math.min(width, height);
            float radius = size / 2f - 3f;

            if (radius <= 0) return;

            // Draw circular cropped custom profile photo
            canvas.save();
            Path clipPath = new Path();
            clipPath.addCircle(centerX, centerY, radius - 2f, Path.Direction.CW);
            canvas.clipPath(clipPath);
            RectF destRect = new RectF(centerX - radius + 2f, centerY - radius + 2f, centerX + radius - 2f, centerY + radius - 2f);
            canvas.drawBitmap(bitmap, null, destRect, photoPaint);
            canvas.restore();

            // Draw outer active (Crimson) or inactive (Border) ring
            canvas.drawCircle(centerX, centerY, radius, borderPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            photoPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            // NO-OP: Ignore BottomNavigationView automatic color tinting so the custom photo retains its real colors!
        }

        @Override
        public void setTintList(@Nullable ColorStateList tint) {
            // NO-OP: Ignore setTintList!
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, Runnable onConfirm) {
        showGlassyConfirmDialog(title, message, positiveText, "Cancel", onConfirm);
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, String negativeText, Runnable onConfirm) {
        if (isFinishing() || isDestroyed()) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_glassy_dialog, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.GlassyDialog)
                .setView(dialogView)
                .create();

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

        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }
}
