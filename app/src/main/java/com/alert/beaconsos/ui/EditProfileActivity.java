package com.alert.beaconsos.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {

    private static final String TAG = "EditProfileActivity";
    private EditText etName;
    private ImageView ivEditProfilePic;
    private TriggerManager triggerManager;
    private FirebaseFirestore db;
    private DatabaseReference mDatabase;
    private String userId;
    private View customLoading;
    private TextView tvLoadingText;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri sourceUri = result.getData().getData();
                    startCrop(sourceUri);
                }
            }
    );

    private final ActivityResultLauncher<CropImageContractOptions> cropResultLauncher = registerForActivityResult(
            new CropImageContract(),
            result -> {
                if (result.isSuccessful()) {
                    Uri resultUri = result.getUriContent();
                    if (resultUri != null) {
                        processAndSaveImage(resultUri);
                    }
                } else {
                    Exception error = result.getError();
                    if (error != null) {
                        Log.e(TAG, "Crop error", error);
                        Toast.makeText(this, "Crop failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void startCrop(Uri sourceUri) {
        CropImageOptions cropImageOptions = new CropImageOptions();
        cropImageOptions.guidelines = CropImageView.Guidelines.ON;
        cropImageOptions.cropShape = CropImageView.CropShape.OVAL;
        cropImageOptions.fixAspectRatio = true;
        cropImageOptions.aspectRatioX = 1;
        cropImageOptions.aspectRatioY = 1;
        
        cropImageOptions.toolbarColor = ContextCompat.getColor(this, R.color.color_primary);
        cropImageOptions.toolbarTitleColor = ContextCompat.getColor(this, R.color.white);
        cropImageOptions.activityMenuIconColor = ContextCompat.getColor(this, R.color.white);
        
        cropImageOptions.activityTitle = "Crop Photo";
        cropImageOptions.cropMenuCropButtonTitle = "DONE";
        cropImageOptions.cropMenuCropButtonIcon = R.drawable.ic_tick_single;
        
        cropImageOptions.allowRotation = true;
        cropImageOptions.allowFlipping = true;
        cropImageOptions.showCropOverlay = true;

        cropImageOptions.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        cropImageOptions.outputCompressQuality = 90;

        cropResultLauncher.launch(new CropImageContractOptions(sourceUri, cropImageOptions));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        triggerManager = new TriggerManager(this);
        db = FirebaseFirestore.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

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
        Button btnSave = findViewById(R.id.btnSaveProfile);
        FloatingActionButton btnChangePhoto = findViewById(R.id.btnChangePhoto);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        loadCurrentData();

        btnChangePhoto.setOnClickListener(v -> showPhotoOptionsDialog());
        btnSave.setOnClickListener(v -> handleSaveProfile());
    }

    private void showPhotoOptionsDialog() {
        String[] options = {"Change Photo", "Remove Photo", "Cancel"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openFileChooser();
                    } else if (which == 1) {
                        handleDeletePhoto();
                    }
                })
                .show();
    }

    private void handleDeletePhoto() {
        String currentPhoto = triggerManager.getProfileImageUri();
        if (currentPhoto == null || currentPhoto.isEmpty()) {
            Toast.makeText(this, "No photo to remove", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Removing Photo");

        Map<String, Object> update = new HashMap<>();
        update.put("profileImageUri", "");

        db.collection("users").document(userId).update(update)
            .addOnSuccessListener(aVoid -> {
                mDatabase.child("users").child(userId).child("profileImageUri").setValue("")
                    .addOnSuccessListener(v -> {
                        hideLoading();
                        triggerManager.saveProfileImageUri("");
                        if (ivEditProfilePic != null) {
                            ivEditProfilePic.setImageResource(R.drawable.ic_user_avatar_default);
                            ivEditProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ivEditProfilePic.setPadding(0, 0, 0, 0);
                            ivEditProfilePic.setImageTintList(null);
                        }
                        Toast.makeText(EditProfileActivity.this, "Photo removed successfully!", Toast.LENGTH_SHORT).show();
                    });
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Delete failed", e);
                Toast.makeText(EditProfileActivity.this, "Failed to remove photo", Toast.LENGTH_SHORT).show();
            });
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

        String imageUriString = triggerManager.getProfileImageUri();
        if (ivEditProfilePic != null) {
            if (imageUriString != null && !imageUriString.isEmpty()) {
                ivEditProfilePic.setPadding(0, 0, 0, 0);
                ivEditProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(this)
                        .load(imageUriString)
                        .placeholder(R.drawable.ic_user_avatar_default)
                        .error(R.drawable.ic_user_avatar_default)
                        .centerCrop()
                        .override(512, 512)
                        .into(ivEditProfilePic);
                ivEditProfilePic.setImageTintList(null);
            } else {
                ivEditProfilePic.setImageResource(R.drawable.ic_user_avatar_default);
                ivEditProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivEditProfilePic.setPadding(0, 0, 0, 0);
                ivEditProfilePic.setImageTintList(null);
            }
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void processAndSaveImage(Uri imageUri) {
        if (imageUri == null) return;

        showLoading("Processing Photo");

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) inputStream.close();

                if (originalBitmap == null) {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int width = originalBitmap.getWidth();
                int height = originalBitmap.getHeight();
                float ratio = (float) width / (float) height;
                int newWidth = 512;
                int newHeight = (int) (512 / ratio);
                if (ratio < 1) {
                    newHeight = 512;
                    newWidth = (int) (512 * ratio);
                }

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
                
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
                byte[] byteArray = outputStream.toByteArray();
                String base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT);
                String finalBase64 = "data:image/jpeg;base64," + base64Image;

                runOnUiThread(() -> {
                    showLoading("Updating Profile Photo");
                    
                    Map<String, Object> update = new HashMap<>();
                    update.put("profileImageUri", finalBase64);

                    db.collection("users").document(userId).update(update)
                        .addOnSuccessListener(aVoid -> {
                            mDatabase.child("users").child(userId).child("profileImageUri").setValue(finalBase64)
                                .addOnSuccessListener(v -> {
                                    hideLoading();
                                    triggerManager.saveProfileImageUri(finalBase64);
                                    if (ivEditProfilePic != null) {
                                        ivEditProfilePic.setPadding(0, 0, 0, 0);
                                        ivEditProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                        ivEditProfilePic.setImageTintList(null);
                                        Glide.with(EditProfileActivity.this)
                                            .load(finalBase64)
                                            .centerCrop()
                                            .into(ivEditProfilePic);
                                    }
                                    Toast.makeText(EditProfileActivity.this, "Photo updated successfully!", Toast.LENGTH_SHORT).show();
                                });
                        })
                        .addOnFailureListener(e -> {
                            hideLoading();
                            Log.e(TAG, "Delete failed", e);
                            Toast.makeText(EditProfileActivity.this, "Failed to update photo", Toast.LENGTH_SHORT).show();
                        });
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void handleSaveProfile() {
        final String newName = etName.getText().toString().trim();

        if (newName.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        updateProfileInDatabases(newName);
    }

    private void updateProfileInDatabases(String name) {
        showLoading("Saving Profile Changes");
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);

        db.collection("users").document(userId).set(updates, SetOptions.merge())
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    mDatabase.child("users").child(userId).updateChildren(updates)
                        .addOnCompleteListener(rtdTask -> {
                            hideLoading();
                            if (rtdTask.isSuccessful()) {
                                triggerManager.saveUserName(name);
                                Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(EditProfileActivity.this, "Realtime Database update failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                } else {
                    hideLoading();
                    Log.e(TAG, "Firestore update failed", task.getException());
                    Toast.makeText(EditProfileActivity.this, "Firestore update failed", Toast.LENGTH_SHORT).show();
                }
            });
    }
}
