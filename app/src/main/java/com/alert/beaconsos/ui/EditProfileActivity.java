package com.alert.beaconsos.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private EditText etName, etPhone;
    private ImageView ivEditProfilePic;
    private CountryCodePicker ccp;
    private TriggerManager triggerManager;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String userId;
    private View customLoading;
    private TextView tvLoadingText;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri imageUri = result.getData().getData();
                    uploadImageToFirebase(imageUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        triggerManager = new TriggerManager(this);
        db = FirebaseFirestore.getInstance();
        
        try {
            storage = FirebaseStorage.getInstance("gs://beacon-sos.firebasestorage.app");
        } catch (Exception e) {
            storage = FirebaseStorage.getInstance();
            Log.e(TAG, "Storage fallback used", e);
        }
        
        userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Edit Profile");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivEditProfilePic = findViewById(R.id.ivEditProfilePic);
        etName = findViewById(R.id.etEditName);
        etPhone = findViewById(R.id.etEditPhone);
        ccp = findViewById(R.id.ccpEditProfile);
        Button btnSave = findViewById(R.id.btnSaveProfile);
        FloatingActionButton btnChangePhoto = findViewById(R.id.btnChangePhoto);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccp.registerCarrierNumberEditText(etPhone);
        loadCurrentData();

        btnChangePhoto.setOnClickListener(v -> openFileChooser());
        btnSave.setOnClickListener(v -> handleSaveProfile());
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

    private void loadCurrentData() {
        etName.setText(triggerManager.getUserName());
        String fullPhone = triggerManager.getUserPhone();
        if (fullPhone != null && !fullPhone.isEmpty()) {
            ccp.setFullNumber(fullPhone);
        }

        String imageUriString = triggerManager.getProfileImageUri();
        Glide.with(this)
                .load(imageUriString != null && !imageUriString.isEmpty() ? Uri.parse(imageUriString) : null)
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .circleCrop()
                .into(ivEditProfilePic);
        
        View verifyBtn = findViewById(R.id.btnVerifyPhone);
        if (verifyBtn != null) verifyBtn.setVisibility(View.GONE);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void uploadImageToFirebase(Uri imageUri) {
        if (imageUri == null) return;
        
        showLoading("Uploading Profile Photo");

        StorageReference profileRef = storage.getReference().child("profile_images/" + userId + ".jpg");
        
        profileRef.putFile(imageUri)
            .addOnSuccessListener(taskSnapshot -> {
                profileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();
                    db.collection("users").document(userId).update("profileImageUri", downloadUrl)
                        .addOnSuccessListener(aVoid -> {
                            hideLoading();
                            triggerManager.saveProfileImageUri(downloadUrl);
                            Glide.with(EditProfileActivity.this).load(downloadUrl).circleCrop().into(ivEditProfilePic);
                            Toast.makeText(EditProfileActivity.this, "Photo updated successfully!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            hideLoading();
                            Log.e(TAG, "Firestore update failed", e);
                            Toast.makeText(EditProfileActivity.this, "Failed to update profile link", Toast.LENGTH_SHORT).show();
                        });
                }).addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Failed to get download URL", e);
                    Toast.makeText(EditProfileActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Upload failed", e);
                if (e.getMessage() != null && e.getMessage().contains("Object does not exist")) {
                    Toast.makeText(EditProfileActivity.this, "Error: Storage bucket not initialized in Firebase Console.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(EditProfileActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void handleSaveProfile() {
        final String newName = etName.getText().toString().trim();
        final String newPhone = ccp.getFullNumberWithPlus();

        if (newName.isEmpty() || etPhone.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ccp.isValidFullNumber()) {
            Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        updateProfileInFirestore(newName, newPhone);
    }

    private void updateProfileInFirestore(String name, String phone) {
        showLoading("Saving Profile Changes");
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);

        db.collection("users").document(userId).set(updates, SetOptions.merge())
            .addOnCompleteListener(task -> {
                hideLoading();
                if (task.isSuccessful()) {
                    triggerManager.saveUserName(name);
                    triggerManager.saveUserPhone(phone);
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Log.e(TAG, "Firestore update failed", task.getException());
                    Toast.makeText(EditProfileActivity.this, "Database update failed", Toast.LENGTH_SHORT).show();
                }
            });
    }
}
