package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AlertMessageActivity extends AppCompatActivity {

    private EditText etAlertMessage, etVoiceTrigger;
    private RecyclerView rvSelectContacts;
    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;
    private Set<String> currentSelectedIds = new HashSet<>();
    private Button btnSaveConfig;
    private View customLoading;
    private TextView tvLoadingText;
    private ListenerRegistration recipientsListener;
    private String userId;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openContactPicker();
                else Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    processPickedContact(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_message);

        triggerManager = new TriggerManager(this);
        locationHelper = new LocationHelper(this);
        smsHelper = new SmsHelper(this);
        userId = FirebaseAuth.getInstance().getUid();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etAlertMessage = findViewById(R.id.etAlertMessage);
        etVoiceTrigger = findViewById(R.id.etVoiceTrigger);
        rvSelectContacts = findViewById(R.id.rvSelectContacts);
        btnSaveConfig = findViewById(R.id.btnSaveAlertConfig);
        Button btnSendAlert = findViewById(R.id.btnSendAlert);
        Button btnSelectFromContacts = findViewById(R.id.btnSelectFromContactsAlert);
        TextView tvClearAll = findViewById(R.id.tvClearAllRecipients);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        if (btnSaveConfig != null) {
            btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        }
        
        loadConfiguration();

        btnSendAlert.setOnClickListener(v -> sendAlert());
        btnSelectFromContacts.setOnClickListener(v -> checkPermissionAndOpenPicker());
        if (tvClearAll != null) {
            tvClearAll.setOnClickListener(v -> {
                if (!currentSelectedIds.isEmpty()) {
                    currentSelectedIds.clear();
                    setupContactsList();
                    checkIfChanged();
                    Toast.makeText(this, "All selections cleared", Toast.LENGTH_SHORT).show();
                }
            });
        }

        observeRecipients();
        setupChangeListeners();
    }

    private void observeRecipients() {
        if (userId == null) return;

        recipientsListener = FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("sos_recipients")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    List<Contact> recipients = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            recipients.add(doc.toObject(Contact.class));
                        }
                    }
                    triggerManager.saveSosRecipients(recipients);
                    setupContactsList();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recipientsListener != null) {
            recipientsListener.remove();
        }
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
        
        currentSelectedIds = new HashSet<>(triggerManager.getSelectedContactIds());
        if (btnSaveConfig != null) {
            btnSaveConfig.setVisibility(View.GONE);
        }

        if (userId != null && !userId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String cloudMsg = documentSnapshot.getString("emergencyMessage");
                            if (cloudMsg != null && !cloudMsg.isEmpty()) {
                                etAlertMessage.setText(cloudMsg);
                                triggerManager.saveEmergencyMessage(cloudMsg);
                            }

                            String cloudVoice = documentSnapshot.getString("voiceTriggerPhrase");
                            if (cloudVoice != null && !cloudVoice.isEmpty()) {
                                etVoiceTrigger.setText(cloudVoice);
                                triggerManager.saveVoiceTriggerPhrase(cloudVoice);
                            }

                            List<String> cloudIds = (List<String>) documentSnapshot.get("selectedContactIds");
                            if (cloudIds != null && !cloudIds.isEmpty()) {
                                currentSelectedIds = new HashSet<>(cloudIds);
                                triggerManager.saveSelectedContactIds(currentSelectedIds);
                                setupContactsList();
                            }
                            checkIfChanged();
                        }
                    });
        }
    }

    private void setupContactsList() {
        List<Contact> allContacts = triggerManager.getSosRecipients();
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
    }

    private void checkPermissionAndOpenPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openContactPicker();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void processPickedContact(Uri contactUri) {
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                
                String name = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);

                Contact newContact = new Contact(UUID.randomUUID().toString(), name, number, "SOS Recipient");
                List<Contact> currentRecipients = triggerManager.getSosRecipients();
                
                // Avoid duplicates
                boolean exists = false;
                String normalizedNew = TriggerManager.normalizePhone(number);
                for (Contact c : currentRecipients) {
                    if (TriggerManager.isSamePhone(c.getPhoneNumber(), number)) {
                        exists = true;
                        currentSelectedIds.add(TriggerManager.normalizePhone(c.getPhoneNumber()));
                        break;
                    }
                }
                
                if (!exists) {
                    currentRecipients.add(newContact);
                    triggerManager.saveSosRecipients(currentRecipients);
                    currentSelectedIds.add(normalizedNew);
                    
                    // Sync to Firestore separate collection for SOS recipients
                    String userId = triggerManager.getUserId();
                    if (userId != null && !userId.isEmpty()) {
                        FirebaseFirestore.getInstance()
                            .collection("users").document(userId)
                            .collection("sos_recipients").document(newContact.getId())
                            .set(newContact, SetOptions.merge());
                    }
                }
                
                // Auto-save selection when contact is picked/added
                triggerManager.saveSelectedContactIds(new HashSet<>(currentSelectedIds));
                
                setupContactsList();
                checkIfChanged();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load contact info", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkIfChanged() {
        if (triggerManager == null || btnSaveConfig == null) return;

        // Compare Message
        String currentMsg = etAlertMessage.getText().toString();
        String savedMsg = triggerManager.getEmergencyMessage();
        boolean msgChanged = !currentMsg.equals(savedMsg);

        // Compare Voice Phrase
        String currentVoice = etVoiceTrigger.getText().toString();
        String savedVoice = triggerManager.getVoiceTriggerPhrase();
        boolean voiceChanged = !currentVoice.equals(savedVoice);

        // Compare Selected Contacts
        Set<String> savedIds = triggerManager.getSelectedContactIds();
        boolean contactsChanged = !currentSelectedIds.equals(savedIds);

        // Show button if anything is different from saved state
        if (msgChanged || voiceChanged || contactsChanged) {
            btnSaveConfig.setVisibility(View.VISIBLE);
        } else {
            btnSaveConfig.setVisibility(View.GONE);
        }
    }

    private void saveConfiguration() {
        String msg = etAlertMessage.getText().toString();
        String voice = etVoiceTrigger.getText().toString();
        Set<String> selectedIds = new HashSet<>(currentSelectedIds);

        triggerManager.saveEmergencyMessage(msg);
        triggerManager.saveVoiceTriggerPhrase(voice);
        triggerManager.saveSelectedContactIds(selectedIds);

        if (userId != null && !userId.isEmpty()) {
            Map<String, Object> configUpdates = new HashMap<>();
            configUpdates.put("emergencyMessage", msg);
            configUpdates.put("voiceTriggerPhrase", voice);
            configUpdates.put("selectedContactIds", new ArrayList<>(selectedIds));

            FirebaseFirestore.getInstance().collection("users").document(userId)
                    .set(configUpdates, SetOptions.merge());

            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(userId)
                    .updateChildren(configUpdates);
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
            String fullMessage;
            
            if (message.contains("[URL]")) {
                fullMessage = message.replace("[URL]", locationUrl != null ? locationUrl : "[Location Unknown]");
            } else {
                fullMessage = message;
                if (locationUrl != null) {
                    fullMessage += "\n\nMy location: " + locationUrl;
                }
            }

            List<Contact> allRecipients = triggerManager.getSosRecipients();
            int sentCount = 0;
            List<String> sentToNames = new ArrayList<>();
            
            for (Contact contact : allRecipients) {
                if (currentSelectedIds.contains(TriggerManager.normalizePhone(contact.getPhoneNumber()))) {
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
            String normPhone = TriggerManager.normalizePhone(contact.getPhoneNumber());
            holder.tvName.setText(contact.getName());
            holder.tvPhone.setText(contact.getPhoneNumber());
            holder.cbContact.setOnCheckedChangeListener(null);
            holder.cbContact.setChecked(currentSelectedIds.contains(normPhone));
            holder.cbContact.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) currentSelectedIds.add(normPhone);
                else currentSelectedIds.remove(normPhone);
                checkIfChanged();
            });

            holder.btnDelete.setOnClickListener(v -> {
                // Remove from local selection
                currentSelectedIds.remove(normPhone);
                
                // Remove from Firestore
                if (userId != null) {
                    FirebaseFirestore.getInstance()
                            .collection("users").document(userId)
                            .collection("sos_recipients").document(contact.getId())
                            .delete();
                }
                
                // TriggerManager update will happen via the snapshot listener
                Toast.makeText(AlertMessageActivity.this, "Contact removed from SOS recipients", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() { return contacts.size(); }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbContact;
            TextView tvName, tvPhone;
            View btnDelete;
            ContactViewHolder(View itemView) {
                super(itemView);
                cbContact = itemView.findViewById(R.id.cbContact);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
                btnDelete = itemView.findViewById(R.id.btnDeleteContact);
            }
        }
    }
}
