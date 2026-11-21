package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.alert.beaconsos.R;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends BaseActivity {

    private EditText etCurrent, etNew, etConfirm;
    private Button btnUpdate;
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etCurrent = findViewById(R.id.etCurrentPassword);
        etNew = findViewById(R.id.etNewPassword);
        etConfirm = findViewById(R.id.etConfirmNewPassword);
        btnUpdate = findViewById(R.id.btnUpdatePassword);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ImageView ivToggleCurrent = findViewById(R.id.ivToggleCurrentPassword);
        ImageView ivToggleNew = findViewById(R.id.ivToggleNewPassword);
        ImageView ivToggleConfirm = findViewById(R.id.ivToggleConfirmPassword);

        setupPasswordVisibilityToggle(ivToggleCurrent, etCurrent);
        setupPasswordVisibilityToggle(ivToggleNew, etNew);
        setupPasswordVisibilityToggle(ivToggleConfirm, etConfirm);

        btnUpdate.setOnClickListener(v -> {
            String current = etCurrent.getText().toString().trim();
            String newPass = etNew.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();

            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirm)) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 8) {
                Toast.makeText(this, "New password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            
            updatePasswordInFirebase(current, newPass);
        });
    }

    private void setupPasswordVisibilityToggle(ImageView toggleButton, EditText editText) {
        if (toggleButton == null || editText == null) return;
        
        // Ensure default state is eye-off (hidden password mode)
        toggleButton.setImageResource(R.drawable.ic_visibility_off);

        toggleButton.setOnClickListener(v -> {
            int selection = editText.getSelectionStart();
            int currentInputType = editText.getInputType();
            
            if ((currentInputType & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                // Switch to hidden password mode & set eye-off icon
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                toggleButton.setImageResource(R.drawable.ic_visibility_off);
            } else {
                // Switch to visible password mode & set eye icon
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                toggleButton.setImageResource(R.drawable.ic_visibility);
            }
            editText.setSelection(selection >= 0 ? Math.min(selection, editText.getText().length()) : editText.getText().length());
        });
    }

    private void updatePasswordInFirebase(String currentPassword, String newPassword) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "You must be logged in to change password", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Updating Password...");

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
            if (reauthTask.isSuccessful()) {
                user.updatePassword(newPassword).addOnCompleteListener(authTask -> {
                    hideLoading();
                    if (authTask.isSuccessful()) {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String error = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to update password";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                hideLoading();
                Toast.makeText(this, "Incorrect current password", Toast.LENGTH_LONG).show();
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
}
