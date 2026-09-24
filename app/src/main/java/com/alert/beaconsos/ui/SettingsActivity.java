package com.alert.beaconsos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class SettingsActivity extends BaseActivity {

    private RadioGroup rgThemeMode;
    private AutoCompleteTextView languageDropdown;
    private SwitchCompat swShakeTrigger;
    private MaterialButton btnDeleteAccount;
    private TriggerManager triggerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rgThemeMode = findViewById(R.id.rgThemeMode);
        languageDropdown = findViewById(R.id.languageDropdown);
        swShakeTrigger = findViewById(R.id.swShakeTrigger);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        // Setup Shake Trigger Toggle
        swShakeTrigger.setChecked(triggerManager.isShakeTriggerEnabled());
        swShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setShakeTriggerEnabled(isChecked);
        });

        // Setup Delete Account Button
        btnDeleteAccount.setOnClickListener(v -> {
            showGlassyConfirmDialog(
                    getString(R.string.delete_account_confirm_title),
                    getString(R.string.delete_account_confirm_msg),
                    getString(R.string.btn_delete),
                    this::handleDeleteAccount
            );
        });

        // Setup Theme Mode UI from Saved Preference
        int savedMode = triggerManager.getThemeMode();
        if (savedMode == 1) {
            rgThemeMode.check(R.id.rbLightMode);
        } else if (savedMode == 2) {
            rgThemeMode.check(R.id.rbDarkMode);
        } else {
            rgThemeMode.check(R.id.rbSystemMode);
        }

        rgThemeMode.setOnCheckedChangeListener((group, checkedId) -> {
            int currentMode = triggerManager.getThemeMode();
            if (checkedId == R.id.rbDarkMode) {
                if (currentMode != 2) {
                    triggerManager.saveThemeMode(2);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    recreate();
                }
            } else if (checkedId == R.id.rbLightMode) {
                if (currentMode != 1) {
                    triggerManager.saveThemeMode(1);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    recreate();
                }
            } else if (checkedId == R.id.rbSystemMode) {
                if (currentMode != 0) {
                    triggerManager.saveThemeMode(0);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    recreate();
                }
            }
        });

        // Setup Modern Language Dropdown
        setupLanguageDropdown();
    }

    private void setupLanguageDropdown() {
        String[] languages = {"English", "Urdu", "Spanish", "Russian", "Chinese", "Japanese", "Arabic"};
        // Use custom glassy layout for dropdown items
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown_glassy, languages);
        languageDropdown.setAdapter(adapter);

        // Set current selection
        String currentLang = triggerManager.getLanguage();
        languageDropdown.setText(currentLang, false);

        languageDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLang = (String) parent.getItemAtPosition(position);
            if (!selectedLang.equals(triggerManager.getLanguage())) {
                triggerManager.saveLanguage(selectedLang);
                updateAppLocale(selectedLang);
            }
        });
        
        // Fix for filtering: clear filter on focus or click to show all options
        languageDropdown.setOnClickListener(v -> languageDropdown.showDropDown());
        languageDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) languageDropdown.showDropDown();
        });
        
        // Ensure the popup background also has a glassy look (optional, depends on theme)
        languageDropdown.setDropDownBackgroundResource(R.drawable.glass_tag_bg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset adapter on resume to prevent filtering issues after language change/recreation
        setupLanguageDropdown();
    }

    private void updateAppLocale(String language) {
        String langCode = "en";
        switch (language) {
            case "Urdu": langCode = "ur"; break;
            case "Spanish": langCode = "es"; break;
            case "Russian": langCode = "ru"; break;
            case "Chinese": langCode = "zh"; break;
            case "Japanese": langCode = "ja"; break;
            case "Arabic": langCode = "ar"; break;
        }

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    private void handleDeleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in to delete your account.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        
        // Use a flag to ensure we don't try to delete twice if something hangs
        btnDeleteAccount.setEnabled(false);
        Toast.makeText(this, "Deleting account...", Toast.LENGTH_SHORT).show();

        // 1. Delete user data from Realtime Database first
        FirebaseDatabase.getInstance().getReference("users").child(userId).removeValue()
                .addOnCompleteListener(dbTask -> {
                    // 2. Now delete the Auth record
                    user.delete().addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            // 3. Clear local data and redirect
                            triggerManager.clearData();
                            Toast.makeText(this, R.string.msg_account_deleted, Toast.LENGTH_LONG).show();
                            
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            btnDeleteAccount.setEnabled(true);
                            if (authTask.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                                // This is common. Firebase requires a fresh login for security.
                                showErrorDialog("Security Requirement", getString(R.string.msg_reauth_required));
                            } else {
                                String error = authTask.getException() != null ? authTask.getException().getMessage() : "Deletion failed";
                                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                });
    }

    private void showErrorDialog(String title, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void finish() {
        super.finish();
    }
}
