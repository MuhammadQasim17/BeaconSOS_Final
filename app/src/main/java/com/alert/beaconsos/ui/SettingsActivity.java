package com.alert.beaconsos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
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

        swShakeTrigger.setChecked(triggerManager.isShakeTriggerEnabled());
        swShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setShakeTriggerEnabled(isChecked);
            String userId = triggerManager.getUserId();
            if (userId != null && !userId.isEmpty()) {
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("shakeTriggerEnabled", isChecked);
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(userId)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge());
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(userId)
                        .child("shakeTriggerEnabled").setValue(isChecked);
            }
        });

        btnDeleteAccount.setOnClickListener(v -> {
            showGlassyConfirmDialog(
                    getString(R.string.delete_account_confirm_title),
                    getString(R.string.delete_account_confirm_msg),
                    getString(R.string.btn_delete),
                    this::handleDeleteAccount
            );
        });

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
                    smoothRecreate();
                }
            } else if (checkedId == R.id.rbLightMode) {
                if (currentMode != 1) {
                    triggerManager.saveThemeMode(1);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    smoothRecreate();
                }
            } else if (checkedId == R.id.rbSystemMode) {
                if (currentMode != 0) {
                    triggerManager.saveThemeMode(0);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    smoothRecreate();
                }
            }
        });

        setupLanguageDropdown();
    }

    private void smoothRecreate() {
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }

    private void setupLanguageDropdown() {
        String[] languages = {"English", "Urdu", "Spanish", "Russian", "Chinese", "Japanese", "Arabic"};
        if (languageDropdown instanceof MaterialAutoCompleteTextView) {
            ((MaterialAutoCompleteTextView) languageDropdown).setSimpleItems(languages);
        } else {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown_glassy, languages);
            languageDropdown.setAdapter(adapter);
        }

        String currentLang = triggerManager.getLanguage();
        languageDropdown.setText(currentLang, false);

        languageDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLang = (String) parent.getItemAtPosition(position);
            languageDropdown.dismissDropDown();
            languageDropdown.clearFocus();
            if (!selectedLang.equals(triggerManager.getLanguage())) {
                triggerManager.saveLanguage(selectedLang);
                updateAppLocale(selectedLang);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        smoothRecreate();
    }

    private void handleDeleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in to delete your account.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        
        btnDeleteAccount.setEnabled(false);
        Toast.makeText(this, "Deleting account...", Toast.LENGTH_SHORT).show();

        FirebaseDatabase.getInstance().getReference("users").child(userId).removeValue()
                .addOnCompleteListener(dbTask -> {
                    user.delete().addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            triggerManager.clearData();
                            Toast.makeText(this, R.string.msg_account_deleted, Toast.LENGTH_LONG).show();
                            
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            btnDeleteAccount.setEnabled(true);
                            if (authTask.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                                showErrorDialog("Security Requirement", getString(R.string.msg_reauth_required));
                            } else {
                                String error = authTask.getException() != null ? authTask.getException().getMessage() : "Deletion failed";
                                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showErrorDialog(String title, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
