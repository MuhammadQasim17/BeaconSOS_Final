package com.alert.beaconsos.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileFragment extends Fragment {

    private ImageView ivProfilePic;
    private TextView tvName, tvPhone;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        triggerManager = new TriggerManager(requireContext());
        mAuth = FirebaseAuth.getInstance();

        ivProfilePic = view.findViewById(R.id.ivProfilePic);
        tvName = view.findViewById(R.id.tvProfileName);
        tvPhone = view.findViewById(R.id.tvProfilePhone);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        
        LinearLayout optionEditProfile = view.findViewById(R.id.optionEditProfile);
        LinearLayout optionPermissions = view.findViewById(R.id.optionPermissions);
        LinearLayout optionChangePassword = view.findViewById(R.id.optionChangePassword);
        LinearLayout optionSettings = view.findViewById(R.id.optionSettings);
        LinearLayout optionFeedback = view.findViewById(R.id.optionFeedback);
        LinearLayout optionAbout = view.findViewById(R.id.optionAbout);

        optionEditProfile.setOnClickListener(v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        
        optionPermissions.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), PermissionsActivity.class));
        });

        optionChangePassword.setOnClickListener(v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));
        optionSettings.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        
        optionFeedback.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "muhammadqasim891011@gmail.com", null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Beacon SOS Feedback");
            try {
                startActivity(Intent.createChooser(emailIntent, "Send feedback..."));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No email client found", Toast.LENGTH_SHORT).show();
            }
        });
        
        optionAbout.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null);
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.GlassyDialog)
                .setView(dialogView)
                .create();
            
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            dialogView.findViewById(R.id.btnAboutClose).setOnClickListener(view1 -> dialog.dismiss());

            dialog.show();
        });

        btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof BaseActivity) {
                ((BaseActivity) getActivity()).showGlassyConfirmDialog("Logout", "Are you sure you want to logout?", "Logout", this::performLogout);
            } else {
                performLogout();
            }
        });

        loadUserData();

        return view;
    }

    private void performLogout() {
        mAuth.signOut();
        triggerManager.clearData();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncUserDataFromFirebase();
        loadUserData();
    }

    private void syncUserDataFromFirebase() {
        String uid = mAuth.getUid();
        if (uid == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                triggerManager.saveUserName(user.getName());
                                triggerManager.saveUserPhone(user.getPhone());
                                triggerManager.saveProfileImageUri(user.getProfileImageUri());
                                loadUserData();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadUserData() {
        String firebaseEmail = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;
        String phoneToShow = triggerManager.getUserPhone();

        if (firebaseEmail != null && firebaseEmail.endsWith("@beaconsos.app")) {
            String derivedPhone = "+" + firebaseEmail.replace("@beaconsos.app", "");
            if (!derivedPhone.equals(phoneToShow)) {
                phoneToShow = derivedPhone;
                triggerManager.saveUserPhone(derivedPhone);
                String uid = mAuth.getUid();
                if (uid != null) {
                    FirebaseDatabase.getInstance().getReference("users").child(uid).child("phone").setValue(derivedPhone);
                }
            }
        }

        if (tvName != null) tvName.setText(triggerManager.getUserName());
        if (tvPhone != null) tvPhone.setText(phoneToShow);

        String imageUriString = triggerManager.getProfileImageUri();
        if (ivProfilePic != null) {
            if (imageUriString != null && !imageUriString.isEmpty()) {
                ivProfilePic.setPadding(0, 0, 0, 0);
                ivProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(this)
                        .load(imageUriString)
                        .placeholder(R.drawable.ic_user_avatar_default)
                        .error(R.drawable.ic_user_avatar_default)
                        .centerCrop()
                        .override(512, 512)
                        .into(ivProfilePic);
                ivProfilePic.setImageTintList(null);
            } else {
                ivProfilePic.setImageResource(R.drawable.ic_user_avatar_default);
                ivProfilePic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivProfilePic.setPadding(0, 0, 0, 0);
                ivProfilePic.setImageTintList(null);
            }
        }
    }
}
