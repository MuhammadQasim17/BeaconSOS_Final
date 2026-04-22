package com.alert.beaconsos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NewIndividualChatActivity extends AppCompatActivity {

    private TriggerManager triggerManager;
    private FirebaseFirestore firestore;
    private DatabaseReference rtdb;
    private DatabaseReference privateChatsRef;
    
    private EditText etReceiverName, etPhone;
    private CountryCodePicker ccp;
    private View customLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_individual_chat);

        triggerManager = new TriggerManager(this);
        firestore = FirebaseFirestore.getInstance();
        FirebaseDatabase db = FirebaseDatabase.getInstance("https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/");
        rtdb = db.getReference();
        // Separate container for private chats
        privateChatsRef = db.getReference("chats");

        Toolbar toolbar = findViewById(R.id.toolbarNewChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etReceiverName = findViewById(R.id.etNewContactName);
        etPhone = findViewById(R.id.etNewContactPhone);
        ccp = findViewById(R.id.ccpNewContact);
        customLoading = findViewById(R.id.customLoading);
        MaterialButton btnStartChat = findViewById(R.id.btnStartChat);

        if (ccp != null && etPhone != null) {
            ccp.registerCarrierNumberEditText(etPhone);
        }

        btnStartChat.setOnClickListener(v -> {
            String receiverName = etReceiverName.getText().toString().trim();
            String rawPhone = etPhone.getText().toString().trim();
            
            if (receiverName.isEmpty() || rawPhone.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            String fullPhone = ccp.getFullNumberWithPlus();
            verifyAndCreateChat(receiverName, fullPhone);
        });
    }

    private void verifyAndCreateChat(String receiverName, String otherPhone) {
        String myPhone = triggerManager.getUserPhone();
        
        if (TriggerManager.isSamePhone(myPhone, otherPhone)) {
            Toast.makeText(this, "You cannot chat with yourself", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        firestore.collection("users")
                .whereEqualTo("phone", otherPhone)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        saveAndNavigate(receiverName, otherPhone);
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "User not found on Beacon SOS", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveAndNavigate(String receiverName, String otherPhone) {
        String myPhone = triggerManager.getUserPhone();
        String myNorm = TriggerManager.normalizePhone(myPhone);
        String otherNorm = TriggerManager.normalizePhone(otherPhone);
        
        // Consistent Chat ID generation (alphabetical order of normalized phones)
        String chatId = myNorm.compareTo(otherNorm) < 0 ? myNorm + "_" + otherNorm : otherNorm + "_" + myNorm;

        // 1. Save how I want to see the other person (locally on THIS phone)
        triggerManager.saveChatContact(otherPhone, receiverName);
        
        // 2. Save/Update chat settings in RTDB (private to this user)
        Map<String, Object> mySettings = new HashMap<>();
        mySettings.put("customName", receiverName);
        mySettings.put("savedAt", System.currentTimeMillis());

        rtdb.child("userChatSettings").child(myNorm).child(chatId)
                .updateChildren(mySettings)
                .addOnSuccessListener(aVoid -> {
                    
                    // 3. Ensure the chat exists in the global 'chats' container
                    Map<String, Object> chatData = new HashMap<>();
                    chatData.put("id", chatId);
                    chatData.put("isGroupChat", false);
                    chatData.put("memberPhones", Arrays.asList(myPhone, otherPhone));
                    
                    privateChatsRef.child(chatId).updateChildren(chatData)
                            .addOnSuccessListener(aVoid2 -> {
                                showLoading(false);
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.putExtra("OPEN_CHAT", true);
                                intent.putExtra("OTHER_PHONE", otherPhone);
                                intent.putExtra("CHAT_ID", chatId);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to start chat", Toast.LENGTH_SHORT).show();
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
