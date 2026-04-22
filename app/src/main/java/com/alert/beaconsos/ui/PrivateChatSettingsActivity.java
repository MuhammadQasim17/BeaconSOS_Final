package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PrivateChatSettingsActivity extends BaseActivity {

    private String chatId;
    private String otherPhone;
    private TriggerManager triggerManager;
    private DatabaseReference chatRef;
    private FirebaseFirestore firestore;

    private ImageView ivContactImage;
    private EditText etContactName, etContactPhone;
    private View customLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat_settings);

        chatId = getIntent().getStringExtra("CHAT_ID");
        if (chatId == null) {
            finish();
            return;
        }

        triggerManager = new TriggerManager(this);
        firestore = FirebaseFirestore.getInstance();
        chatRef = FirebaseDatabase.getInstance("https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("chats").child(chatId);

        initViews();
        loadChatData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbarPrivateSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivContactImage = findViewById(R.id.ivContactImageLarge);
        etContactName = findViewById(R.id.etPrivateContactName);
        etContactPhone = findViewById(R.id.etPrivateContactPhone);
        customLoading = findViewById(R.id.customLoading);

        findViewById(R.id.btnUpdateContact).setOnClickListener(v -> saveContactChanges());
        findViewById(R.id.btnDeletePrivateChat).setOnClickListener(v -> deleteChat());
        findViewById(R.id.btnBlockPrivateChat).setOnClickListener(v -> blockContact());
        findViewById(R.id.btnClearPrivateChat).setOnClickListener(v -> clearMessages());
    }

    private void loadChatData() {
        showLoading(true);
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);
                if (snapshot.exists()) {
                    String myPhone = triggerManager.getUserPhone();
                    for (DataSnapshot member : snapshot.child("memberPhones").getChildren()) {
                        String phone = member.getValue(String.class);
                        if (phone != null && !TriggerManager.isSamePhone(phone, myPhone)) {
                            otherPhone = phone;
                            break;
                        }
                    }

                    if (otherPhone != null) {
                        String savedName = triggerManager.getContactName(otherPhone);
                        etContactName.setText(savedName != null ? savedName : otherPhone);
                        etContactPhone.setText(otherPhone);
                        loadContactImage(otherPhone);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
            }
        });
    }

    private void loadContactImage(String phone) {
        firestore.collection("users").whereEqualTo("phone", phone).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String imageUri = queryDocumentSnapshots.getDocuments().get(0).getString("profileImageUri");
                        if (imageUri != null && !imageUri.isEmpty()) {
                            Glide.with(this).load(imageUri).placeholder(R.drawable.profile_pic).circleCrop().into(ivContactImage);
                        }
                    }
                });
    }

    private void saveContactChanges() {
        String newName = etContactName.getText().toString().trim();
        if (newName.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        
        // Save the name LOCALLY for this user (Phone A's container)
        triggerManager.saveChatContact(otherPhone, newName);
        
        // Save to Firebase under PRIVATE user settings (Phone A's cloud backup)
        // This MUST NOT be under the shared 'chats' node.
        String myNorm = TriggerManager.normalizePhone(triggerManager.getUserPhone());
        DatabaseReference settingsRef = FirebaseDatabase.getInstance("https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("userChatSettings").child(myNorm).child(chatId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("customName", newName);
        updates.put("savedAt", System.currentTimeMillis());

        settingsRef.updateChildren(updates).addOnCompleteListener(task -> {
            showLoading(false);
            if (task.isSuccessful()) {
                // Ensure we DON'T update the shared 'chats' node's name
                Toast.makeText(this, "Contact updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to update contact", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteChat() {
        showGlassyConfirmDialog("Delete Chat", "Delete this entire conversation?", "Delete", () -> {
            chatRef.removeValue().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void blockContact() {
        showGlassyConfirmDialog("Block Contact", "Stop receiving messages from this contact?", "Block", () -> {
            if (otherPhone != null) {
                triggerManager.blockPhone(otherPhone);
                Toast.makeText(this, "Contact blocked", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void clearMessages() {
        showGlassyConfirmDialog("Clear Chat", "Delete all messages in this chat?", "Clear", () -> {
            chatRef.child("messages").removeValue().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Messages cleared", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showLoading(boolean show) {
        if (customLoading != null) {
            customLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) LoadingUtils.startCustomAnimation(customLoading);
            else LoadingUtils.stopCustomAnimation(customLoading);
        }
    }
}
