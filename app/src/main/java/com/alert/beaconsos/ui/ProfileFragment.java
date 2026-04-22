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
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

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
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "support@beaconsos.com", null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Beacon SOS Feedback");
            try {
                startActivity(Intent.createChooser(emailIntent, "Send feedback..."));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No email client found", Toast.LENGTH_SHORT).show();
            }
        });
        
        optionAbout.setOnClickListener(v -> Toast.makeText(requireContext(), "Beacon SOS v1.0.0\nYour Safety, Our Priority.", Toast.LENGTH_LONG).show());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            triggerManager.clearData();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        loadUserData();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
    }

    private void loadUserData() {
        if (tvName != null) tvName.setText(triggerManager.getUserName());
        if (tvPhone != null) tvPhone.setText(triggerManager.getUserPhone());

        String imageUriString = triggerManager.getProfileImageUri();
        if (ivProfilePic != null) {
            Glide.with(this)
                    .load(imageUriString != null && !imageUriString.isEmpty() ? Uri.parse(imageUriString) : null)
                    .placeholder(R.drawable.profile_pic)
                    .error(R.drawable.profile_pic)
                    .circleCrop()
                    .into(ivProfilePic);
        }
    }
}
