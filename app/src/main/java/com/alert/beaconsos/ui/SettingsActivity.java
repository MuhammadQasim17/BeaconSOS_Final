package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;

public class SettingsActivity extends BaseActivity {

    private RadioGroup rgThemeMode;
    private AutoCompleteTextView languageDropdown;
    private SwitchCompat swShakeTrigger, swVolumeTrigger;
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
        swVolumeTrigger = findViewById(R.id.swVolumeTrigger);

        // Setup Shake Trigger Toggle
        swShakeTrigger.setChecked(triggerManager.isShakeTriggerEnabled());
        swShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setShakeTriggerEnabled(isChecked);
        });

        // Setup Volume Trigger Toggle
        swVolumeTrigger.setChecked(triggerManager.isVolumeTriggerEnabled());
        swVolumeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setVolumeTriggerEnabled(isChecked);
        });

        // Setup Theme Mode UI from Saved Preference
        int savedMode = triggerManager.getThemeMode();
        if (savedMode == 1) {
            rgThemeMode.check(R.id.rbLightMode);
        } else if (savedMode == 2) {
            rgThemeMode.check(R.id.rbDarkMode);
        } else {
            // Default or System
            int systemMode = AppCompatDelegate.getDefaultNightMode();
            if (systemMode == AppCompatDelegate.MODE_NIGHT_YES) {
                rgThemeMode.check(R.id.rbDarkMode);
            } else {
                rgThemeMode.check(R.id.rbLightMode);
            }
        }

        rgThemeMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDarkMode) {
                if (triggerManager.getThemeMode() != 2) {
                    triggerManager.saveThemeMode(2);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
            } else if (checkedId == R.id.rbLightMode) {
                if (triggerManager.getThemeMode() != 1) {
                    triggerManager.saveThemeMode(1);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
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

    @Override
    public void finish() {
        super.finish();
    }
}
