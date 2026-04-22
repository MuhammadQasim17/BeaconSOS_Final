package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Contact;
import com.alert.beaconsos.data.SosHistoryEntry;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.location.LocationHelper;
import com.alert.beaconsos.utils.LoadingUtils;
import com.alert.beaconsos.utils.SmsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AlertMessageActivity extends AppCompatActivity {

    private static final String TAG = "AlertMessageActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private EditText etAlertMessage, etVoiceTrigger;
    private SwitchCompat switchVolumeTrigger, switchShakeTrigger;
    private RecyclerView rvSelectContacts, rvSosHistory;
    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;
    private Set<String> currentSelectedIds = new HashSet<>();
    private Button btnSaveConfig, btnTabConfig, btnTabHistory;
    private View viewConfig, viewHistory;
    private TextView tvEmptyHistory;
    private View customLoading;
    private TextView tvLoadingText;

    // Audio recording variables
    private FloatingActionButton fabRecord;
    private ImageButton btnPlayAudio, btnDeleteAudio;
    private TextView tvAudioStatus, tvAudioTimer;
    private MediaRecorder recorder = null;
    private MediaPlayer player = null;
    private String fileName = null;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Handler timerHandler = new Handler();
    private int secondsElapsed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_message);

        triggerManager = new TriggerManager(this);
        locationHelper = new LocationHelper(this);
        smsHelper = new SmsHelper(this);

        fileName = getExternalCacheDir().getAbsolutePath() + "/sos_audio_message.3gp";

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Navigation Tabs
        btnTabConfig = findViewById(R.id.btnTabConfig);
        btnTabHistory = findViewById(R.id.btnTabHistory);
        viewConfig = findViewById(R.id.viewConfig);
        viewHistory = findViewById(R.id.viewHistory);
        rvSosHistory = findViewById(R.id.rvSosHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        btnTabConfig.setOnClickListener(v -> showTab(true));
        btnTabHistory.setOnClickListener(v -> showTab(false));

        etAlertMessage = findViewById(R.id.etAlertMessage);
        etVoiceTrigger = findViewById(R.id.etVoiceTrigger);
        switchVolumeTrigger = findViewById(R.id.switchVolumeTrigger);
        switchShakeTrigger = findViewById(R.id.switchShakeTrigger);
        rvSelectContacts = findViewById(R.id.rvSelectContacts);
        btnSaveConfig = findViewById(R.id.btnSaveAlertConfig);
        Button btnSendAlert = findViewById(R.id.btnSendAlert);
        
        fabRecord = findViewById(R.id.fabRecord);
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        btnDeleteAudio = findViewById(R.id.btnDeleteAudio);
        tvAudioStatus = findViewById(R.id.tvAudioStatus);
        tvAudioTimer = findViewById(R.id.tvAudioTimer);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        loadConfiguration();
        setupHistoryList();

        btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        btnSendAlert.setOnClickListener(v -> sendAlert());
        fabRecord.setOnClickListener(v -> toggleRecording());
        btnPlayAudio.setOnClickListener(v -> togglePlayback());
        btnDeleteAudio.setOnClickListener(v -> deleteAudio());

        setupContactsList();
        setupChangeListeners();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void showTab(boolean isConfig) {
        if (isConfig) {
            viewConfig.setVisibility(View.VISIBLE);
            viewHistory.setVisibility(View.GONE);
            btnTabConfig.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.white));
            btnTabConfig.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            btnTabHistory.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            btnTabHistory.setTextColor(ContextCompat.getColor(this, R.color.white));
            findViewById(R.id.layout_bottom_btns).setVisibility(View.VISIBLE);
        } else {
            viewConfig.setVisibility(View.GONE);
            viewHistory.setVisibility(View.VISIBLE);
            btnTabHistory.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.white));
            btnTabHistory.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            btnTabConfig.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            btnTabConfig.setTextColor(ContextCompat.getColor(this, R.color.white));
            findViewById(R.id.layout_bottom_btns).setVisibility(View.GONE);
            loadHistory();
        }
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

    private void loadConfiguration() {
        String msg = triggerManager.getEmergencyMessage();
        if (msg != null && !msg.isEmpty()) {
            etAlertMessage.setText(msg);
        }
        
        etVoiceTrigger.setText(triggerManager.getVoiceTriggerPhrase());
        switchVolumeTrigger.setChecked(triggerManager.isVolumeTriggerEnabled());
        switchShakeTrigger.setChecked(triggerManager.isShakeTriggerEnabled());
        
        String savedAudioPath = triggerManager.getAudioMessagePath();
        if (savedAudioPath != null && new File(savedAudioPath).exists()) {
            fileName = savedAudioPath;
            
            MediaPlayer tempPlayer = new MediaPlayer();
            try {
                tempPlayer.setDataSource(fileName);
                tempPlayer.prepare();
                int durationSecs = tempPlayer.getDuration() / 1000;
                tvAudioStatus.setText("Saved (" + formatTime(durationSecs) + ")");
                tempPlayer.release();
            } catch (IOException e) {
                tvAudioStatus.setText("Audio message saved");
            }
            
            btnPlayAudio.setVisibility(View.VISIBLE);
            btnDeleteAudio.setVisibility(View.VISIBLE);
        }

        currentSelectedIds = new HashSet<>(triggerManager.getSelectedContactIds());
        btnSaveConfig.setVisibility(View.GONE);
    }

    private void setupContactsList() {
        List<Contact> allContacts = triggerManager.getContacts();
        sortContacts(allContacts);
        rvSelectContacts.setLayoutManager(new LinearLayoutManager(this));
        rvSelectContacts.setAdapter(new ContactCheckboxAdapter(allContacts));
    }

    private void sortContacts(List<Contact> contacts) {
        Collections.sort(contacts, (c1, c2) -> {
            boolean s1 = currentSelectedIds.contains(c1.getId());
            boolean s2 = currentSelectedIds.contains(c2.getId());
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            return c1.getName().compareToIgnoreCase(c2.getName());
        });
    }

    private void setupHistoryList() {
        rvSosHistory.setLayoutManager(new LinearLayoutManager(this));
        loadHistory();
    }

    private void loadHistory() {
        List<SosHistoryEntry> history = triggerManager.getSosHistory();
        if (history.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvSosHistory.setVisibility(View.GONE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            rvSosHistory.setVisibility(View.VISIBLE);
            rvSosHistory.setAdapter(new HistoryAdapter(history));
        }
    }

    private void setupChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) { checkIfChanged(); }
        };
        etAlertMessage.addTextChangedListener(watcher);
        etVoiceTrigger.addTextChangedListener(watcher);
        switchVolumeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> checkIfChanged());
        switchShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> checkIfChanged());
    }

    private void toggleRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }

        recorder.start();
        isRecording = true;
        fabRecord.setImageResource(android.R.drawable.ic_media_pause); // Use system drawable
        tvAudioStatus.setText("Recording...");
        startTimer();
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        isRecording = false;
        fabRecord.setImageResource(android.R.drawable.ic_btn_speak_now); // Use system drawable
        tvAudioStatus.setText("Recorded");
        stopTimer();
        btnPlayAudio.setVisibility(View.VISIBLE);
        btnDeleteAudio.setVisibility(View.VISIBLE);
        checkIfChanged();
    }

    private void togglePlayback() {
        if (!isPlaying) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        player = new MediaPlayer();
        try {
            player.setDataSource(fileName);
            player.prepare();
            player.start();
            isPlaying = true;
            btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause); // Use system drawable
            player.setOnCompletionListener(mp -> stopPlaying());
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    private void stopPlaying() {
        if (player != null) {
            player.release();
            player = null;
        }
        isPlaying = false;
        btnPlayAudio.setImageResource(android.R.drawable.ic_media_play); // Use system drawable
    }

    private void deleteAudio() {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        btnPlayAudio.setVisibility(View.GONE);
        btnDeleteAudio.setVisibility(View.GONE);
        tvAudioStatus.setText("No audio message");
        tvAudioTimer.setText("00:00");
        checkIfChanged();
    }

    private void startTimer() {
        secondsElapsed = 0;
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    secondsElapsed++;
                    tvAudioTimer.setText(formatTime(secondsElapsed));
                    timerHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private void stopTimer() {
        timerHandler.removeCallbacksAndMessages(null);
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
    }

    private void checkIfChanged() {
        String currentMsg = etAlertMessage.getText().toString();
        String currentVoice = etVoiceTrigger.getText().toString();
        boolean currentVolume = switchVolumeTrigger.isChecked();
        boolean currentShake = switchShakeTrigger.isChecked();
        
        boolean msgChanged = !currentMsg.equals(triggerManager.getEmergencyMessage());
        boolean voiceChanged = !currentVoice.equals(triggerManager.getVoiceTriggerPhrase());
        boolean volumeChanged = currentVolume != triggerManager.isVolumeTriggerEnabled();
        boolean shakeChanged = currentShake != triggerManager.isShakeTriggerEnabled();
        
        Set<String> savedIds = new HashSet<>(triggerManager.getSelectedContactIds());
        boolean contactsChanged = !currentSelectedIds.equals(savedIds);
        
        String savedAudio = triggerManager.getAudioMessagePath();
        boolean audioChanged = false;
        File audioFile = new File(fileName);
        if (audioFile.exists()) {
            audioChanged = savedAudio == null || !savedAudio.equals(fileName);
        } else {
            audioChanged = savedAudio != null;
        }

        if (msgChanged || voiceChanged || volumeChanged || shakeChanged || contactsChanged || audioChanged) {
            btnSaveConfig.setVisibility(View.VISIBLE);
        } else {
            btnSaveConfig.setVisibility(View.GONE);
        }
    }

    private void saveConfiguration() {
        triggerManager.saveEmergencyMessage(etAlertMessage.getText().toString());
        triggerManager.saveVoiceTriggerPhrase(etVoiceTrigger.getText().toString());
        triggerManager.setVolumeTriggerEnabled(switchVolumeTrigger.isChecked());
        triggerManager.setShakeTriggerEnabled(switchShakeTrigger.isChecked());
        triggerManager.saveSelectedContactIds(new HashSet<>(currentSelectedIds));
        
        File audioFile = new File(fileName);
        if (audioFile.exists()) {
            triggerManager.saveAudioMessagePath(fileName);
        } else {
            triggerManager.saveAudioMessagePath(null);
        }
        
        Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
        btnSaveConfig.setVisibility(View.GONE);
    }

    private void sendAlert() {
        if (currentSelectedIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one contact", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Fetching location...");
        locationHelper.getLastLocation(locationUrl -> {
            String message = etAlertMessage.getText().toString();
            String fullMessage = message;
            if (locationUrl != null) {
                fullMessage += "\n\nMy location: " + locationUrl;
            }

            List<Contact> allContacts = triggerManager.getContacts();
            int sentCount = 0;
            List<String> sentToNames = new ArrayList<>();
            
            for (Contact contact : allContacts) {
                if (currentSelectedIds.contains(contact.getId())) {
                    smsHelper.sendSms(contact.getPhoneNumber(), fullMessage);
                    sentCount++;
                    sentToNames.add(contact.getName());
                }
            }

            if (sentCount > 0) {
                SosHistoryEntry entry = new SosHistoryEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    fullMessage,
                    sentToNames,
                    "Manual Test"
                );
                triggerManager.addSosHistoryEntry(entry);
            }

            hideLoading();
            Toast.makeText(this, "Alert sent to " + sentCount + " contacts", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private class ContactCheckboxAdapter extends RecyclerView.Adapter<ContactCheckboxAdapter.ContactViewHolder> {
        private final List<Contact> contacts;
        public ContactCheckboxAdapter(List<Contact> contacts) { this.contacts = contacts; }

        @NonNull @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_checkbox, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            Contact contact = contacts.get(position);
            holder.tvName.setText(contact.getName());
            holder.tvPhone.setText(contact.getPhoneNumber());
            holder.cbContact.setOnCheckedChangeListener(null);
            holder.cbContact.setChecked(currentSelectedIds.contains(contact.getId()));
            holder.cbContact.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) currentSelectedIds.add(contact.getId());
                else currentSelectedIds.remove(contact.getId());
                checkIfChanged();
            });
        }

        @Override
        public int getItemCount() { return contacts.size(); }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbContact;
            TextView tvName, tvPhone;
            ContactViewHolder(View itemView) {
                super(itemView);
                cbContact = itemView.findViewById(R.id.cbContact);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
            }
        }
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<SosHistoryEntry> history;
        HistoryAdapter(List<SosHistoryEntry> history) { this.history = history; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SosHistoryEntry entry = history.get(position);
            holder.tvType.setText(entry.getTriggerType());
            holder.tvTime.setText(new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date(entry.getTimestamp())));
            holder.tvContacts.setText("Sent to: " + String.join(", ", entry.getContactNames()));
        }

        @Override
        public int getItemCount() { return history.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvTime, tvContacts;
            ViewHolder(View v) {
                super(v);
                tvType = v.findViewById(R.id.tvHistoryType);
                tvTime = v.findViewById(R.id.tvHistoryTime);
                tvContacts = v.findViewById(R.id.tvHistoryContacts);
            }
        }
    }
}
