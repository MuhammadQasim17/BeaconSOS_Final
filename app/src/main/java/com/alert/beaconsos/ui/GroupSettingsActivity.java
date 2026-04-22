package com.alert.beaconsos.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Group;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GroupSettingsActivity extends BaseActivity {

    private static final String TAG = "GroupSettingsActivity";
    private ImageView ivGroupImageLarge;
    private TextInputEditText etGroupName, etGroupDescription;
    private RecyclerView rvMembersSettings;
    private Group currentGroup;
    private String selectedImageUri;
    private DatabaseReference groupRef;
    private String userPhone;
    private Button btnSave, btnAddMember, btnDeleteGroupForEveryone;
    private FloatingActionButton btnChangeImage;
    private ValueEventListener groupListener;
    private View customLoading;
    private TextView tvLoadingText;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to take persistable URI permission", e);
                    }
                    selectedImageUri = imageUri.toString();
                    if (!isFinishing() && !isDestroyed()) {
                        Glide.with(this).load(imageUri).circleCrop().into(ivGroupImageLarge);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);

        TriggerManager triggerManager = new TriggerManager(this);
        userPhone = triggerManager.getUserPhone();
        
        String groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId == null) {
            finish();
            return;
        }
        groupRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);

        Toolbar toolbar = findViewById(R.id.toolbarGroupSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivGroupImageLarge = findViewById(R.id.ivGroupImageLarge);
        etGroupName = findViewById(R.id.etGroupName);
        etGroupDescription = findViewById(R.id.etGroupDescription);
        rvMembersSettings = findViewById(R.id.rvMembersSettings);
        btnChangeImage = findViewById(R.id.btnChangeGroupImage);
        btnSave = findViewById(R.id.btnSaveGroupSettings);
        btnAddMember = findViewById(R.id.btnAddMemberSettings);
        Button btnExitGroup = findViewById(R.id.btnExitGroup);
        btnDeleteGroupForEveryone = findViewById(R.id.btnDeleteGroupForEveryone);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        listenForGroupData();

        btnChangeImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        btnAddMember.setOnClickListener(v -> showGlassyConfirmDialog(
                "Add Member",
                "Do you want to add a new member to this group?",
                "Add",
                () -> {
                    Intent intent = new Intent(this, AddMemberActivity.class);
                    intent.putExtra("GROUP_ID", groupId);
                    startActivity(intent);
                }
        ));

        btnSave.setOnClickListener(v -> saveChanges());
        
        btnExitGroup.setOnClickListener(v -> showExitConfirmation());

        btnDeleteGroupForEveryone.setOnClickListener(v -> showDeleteConfirmation());

        ivGroupImageLarge.setOnClickListener(v -> {
            if (currentGroup != null && currentGroup.getGroupImageUri() != null && !currentGroup.getGroupImageUri().isEmpty()) {
                Intent intent = new Intent(this, ImagePreviewActivity.class);
                intent.putExtra("IMAGE_URL", currentGroup.getGroupImageUri());
                startActivity(intent);
            } else if (selectedImageUri != null && !selectedImageUri.isEmpty()) {
                 Intent intent = new Intent(this, ImagePreviewActivity.class);
                 intent.putExtra("IMAGE_URL", selectedImageUri);
                 startActivity(intent);
            } else {
                Toast.makeText(this, "No image to preview", Toast.LENGTH_SHORT).show();
            }
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

    private void listenForGroupData() {
        showLoading("Loading Group Profile");
        groupListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hideLoading();
                if (isFinishing() || isDestroyed()) return;
                
                currentGroup = new Group();
                currentGroup.setId(snapshot.getKey());
                currentGroup.setName(snapshot.child("name").getValue(String.class));
                currentGroup.setDescription(snapshot.child("description").getValue(String.class));
                currentGroup.setAdminPhone(snapshot.child("adminPhone").getValue(String.class));
                currentGroup.setGroupImageUri(snapshot.child("groupImageUri").getValue(String.class));

                // Manual memberPhones parsing
                List<String> members = new ArrayList<>();
                DataSnapshot membersSnapshot = snapshot.child("memberPhones");
                if (membersSnapshot.exists()) {
                    for (DataSnapshot member : membersSnapshot.getChildren()) {
                        String phone = member.getValue(String.class);
                        if (phone != null) members.add(phone);
                    }
                }
                currentGroup.setMemberPhones(members);
                
                if (currentGroup != null) {
                    updateUIBasedOnPermissions();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(GroupSettingsActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
                }
            }
        };
        groupRef.addValueEventListener(groupListener);
    }

    private void updateUIBasedOnPermissions() {
        if (currentGroup == null || isFinishing() || isDestroyed()) return;

        boolean isAdmin = userPhone != null && userPhone.equals(currentGroup.getAdminPhone());

        // Use setText if content changed to avoid losing cursor position while typing (if applicable)
        String currentName = etGroupName.getText() != null ? etGroupName.getText().toString() : "";
        if (!currentName.equals(currentGroup.getName())) {
            etGroupName.setText(currentGroup.getName());
        }
        
        String currentDesc = etGroupDescription.getText() != null ? etGroupDescription.getText().toString() : "";
        if (currentGroup.getDescription() != null && !currentDesc.equals(currentGroup.getDescription())) {
            etGroupDescription.setText(currentGroup.getDescription());
        }
        
        String imageUri = currentGroup.getGroupImageUri();
        
        if (imageUri != null && !imageUri.isEmpty()) {
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(this)
                        .load(Uri.parse(imageUri))
                        .placeholder(R.drawable.profile)
                        .circleCrop()
                        .into(ivGroupImageLarge);
            }
        } else {
            ivGroupImageLarge.setImageResource(R.drawable.profile);
        }

        // Admin Only Permissions
        etGroupName.setEnabled(isAdmin);
        etGroupDescription.setEnabled(isAdmin);
        btnChangeImage.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        btnSave.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        btnAddMember.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        btnDeleteGroupForEveryone.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        setupMembersList(isAdmin);
    }

    private void setupMembersList(boolean isAdmin) {
        if (currentGroup == null || isFinishing() || isDestroyed()) return;

        MemberAdapter memberAdapter = new MemberAdapter(currentGroup.getMemberPhones(), isAdmin, userPhone, currentGroup.getAdminPhone(), this::showRemoveMemberConfirmation);
        rvMembersSettings.setLayoutManager(new LinearLayoutManager(this));
        rvMembersSettings.setAdapter(memberAdapter);
    }

    private void showRemoveMemberConfirmation(String memberPhone, int position) {
        showGlassyConfirmDialog("Remove Member", 
            "Are you sure you want to remove " + memberPhone + " from the group?", 
            "Remove", () -> {
                if (currentGroup != null && currentGroup.getMemberPhones() != null) {
                    showLoading("Updating Members");
                    currentGroup.getMemberPhones().remove(position);
                    groupRef.child("memberPhones").setValue(currentGroup.getMemberPhones()).addOnSuccessListener(aVoid -> {
                        hideLoading();
                        Toast.makeText(GroupSettingsActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        hideLoading();
                        Toast.makeText(GroupSettingsActivity.this, "Failed to remove member", Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }

    private void saveChanges() {
        if (currentGroup == null) return;

        String name = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading("Updating Group Settings");
        
        currentGroup.setName(name);
        String desc = etGroupDescription.getText() != null ? etGroupDescription.getText().toString().trim() : "";
        currentGroup.setDescription(desc);
        currentGroup.setGroupImageUri(selectedImageUri != null ? selectedImageUri : currentGroup.getGroupImageUri());
        
        groupRef.setValue(currentGroup).addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "Group updated", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> hideLoading());
    }

    private void showExitConfirmation() {
        showGlassyConfirmDialog("Exit Group", 
            "Are you sure you want to leave this group?", 
            "Exit", this::exitGroup);
    }

    private void showDeleteConfirmation() {
        showGlassyConfirmDialog("Delete Group", 
            "Are you sure you want to delete this group for everyone? This action cannot be undone.", 
            "Delete", this::deleteGroupForEveryone);
    }

    private void deleteGroupForEveryone() {
        if (groupRef == null) return;
        showLoading("Deleting Group");
        groupRef.removeValue().addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "Group deleted for everyone", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("TARGET_NAV_ID", R.id.nav_message); // Ensure we return to chat tab
                startActivity(intent);
                finish();
            }
        }).addOnFailureListener(e -> hideLoading());
    }

    private void exitGroup() {
        if (currentGroup == null) return;
        
        showLoading("Exiting Group");
        
        currentGroup.getMemberPhones().remove(userPhone);
        
        // If the admin is leaving, assign a new admin if members still exist
        if (userPhone != null && userPhone.equals(currentGroup.getAdminPhone())) {
            if (!currentGroup.getMemberPhones().isEmpty()) {
                currentGroup.setAdminPhone(currentGroup.getMemberPhones().get(0));
            } else {
                // If no members left, delete group
                groupRef.removeValue().addOnSuccessListener(aVoid -> {
                    hideLoading();
                    if (!isFinishing() && !isDestroyed()) {
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("TARGET_NAV_ID", R.id.nav_message);
                        startActivity(intent);
                        finish();
                    }
                }).addOnFailureListener(e -> hideLoading());
                return;
            }
        }
        
        groupRef.setValue(currentGroup).addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "You left the group", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("TARGET_NAV_ID", R.id.nav_message); // Ensure we return to chat tab
                startActivity(intent);
                finish();
            }
        }).addOnFailureListener(e -> hideLoading());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupRef != null && groupListener != null) {
            groupRef.removeEventListener(groupListener);
        }
    }
}
