package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etLoginPhone, etLoginPassword;
    private CountryCodePicker ccpLogin;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Auto-redirect if already logged in
        if (mAuth.getCurrentUser() != null && triggerManager.isLoggedIn()) {
            redirectUserByRole();
            return;
        }

        setContentView(R.layout.activity_login);

        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccpLogin = findViewById(R.id.ccpLogin);
        etLoginPhone = findViewById(R.id.etLoginPhone);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoToSignup = findViewById(R.id.tvGoToSignup);

        ccpLogin.registerCarrierNumberEditText(etLoginPhone);

        btnLogin.setOnClickListener(v -> {
            String phoneInput = etLoginPhone.getText().toString().trim();
            String passwordInput = etLoginPassword.getText().toString().trim();

            if (TextUtils.isEmpty(phoneInput)) {
                etLoginPhone.setError("Phone number required");
                return;
            }
            
            if (TextUtils.isEmpty(passwordInput)) {
                etLoginPassword.setError("Password required");
                return;
            }

            String fullPhone = ccpLogin.getFullNumberWithPlus();
            performPasswordLogin(fullPhone, passwordInput);
        });

        tvGoToSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
    }

    private void performPasswordLogin(String phoneNumber, String password) {
        showLoading("Logging in...");
        
        // Construct the virtual email from the phone number
        String virtualEmail = phoneNumber.replace("+", "") + "@beaconsos.app";
        
        // Log in with Email/Password (Free/Unlimited)
        mAuth.signInWithEmailAndPassword(virtualEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String uid = mAuth.getCurrentUser().getUid();
                        fetchUserProfileAndRedirect(uid, phoneNumber);
                    } else {
                        hideLoading();
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthException) {
                            String errorCode = ((FirebaseAuthException) e).getErrorCode();
                            switch (errorCode) {
                                case "ERROR_USER_NOT_FOUND":
                                    showErrorDialog("Account Not Found", "No account exists for this number. Please sign up first.");
                                    break;
                                case "ERROR_WRONG_PASSWORD":
                                case "ERROR_INVALID_CREDENTIAL":
                                    showErrorDialog("Login Failed", "Incorrect password. Please try again.");
                                    break;
                                case "ERROR_USER_DISABLED":
                                    showErrorDialog("Account Disabled", "This account has been disabled.");
                                    break;
                                case "ERROR_TOO_MANY_REQUESTS":
                                    showErrorDialog("Too Many Attempts", "Too many unsuccessful login attempts. Please try again later.");
                                    break;
                                default:
                                    showErrorDialog("Login Error", "Authentication failed: " + e.getMessage());
                                    break;
                            }
                        } else {
                            String msg = e != null ? e.getMessage() : "Authentication failed";
                            showErrorDialog("Login Error", msg);
                        }
                    }
                });
    }

    private void fetchUserProfileAndRedirect(String uid, String loginPhone) {
        // Primary fetch from Realtime Database
        mDatabase.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        // Check if name is missing, try to recover from Firestore if so
                        if (user.getName() == null || user.getName().isEmpty()) {
                            recoverNameFromFirestore(user, uid, loginPhone);
                        } else {
                            finalizeLogin(user, uid, loginPhone);
                        }
                    }
                } else {
                    // Try Firestore as fallback if RTDB is empty
                    recoverUserFromFirestore(uid, loginPhone);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                showErrorDialog("Database Error", "Access Denied: " + error.getMessage());
            }
        });
    }

    private void recoverNameFromFirestore(User user, String uid, String loginPhone) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null) user.setName(name);
                    }
                    finalizeLogin(user, uid, loginPhone);
                })
                .addOnFailureListener(e -> finalizeLogin(user, uid, loginPhone));
    }

    private void recoverUserFromFirestore(String uid, String loginPhone) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    hideLoading();
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        if (user != null) finalizeLogin(user, uid, loginPhone);
                    } else {
                        mAuth.signOut();
                        showErrorDialog("Account Error", "Profile data not found. Please sign up again.");
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    mAuth.signOut();
                    showErrorDialog("Connection Error", "Could not retrieve profile.");
                });
    }

    private void finalizeLogin(User user, String uid, String loginPhone) {
        // Ensure the phone number in memory matches the login number
        if (user.getPhone() == null || user.getPhone().isEmpty() || !user.getPhone().equals(loginPhone)) {
            user.setPhone(loginPhone);
            mDatabase.child("users").child(uid).child("phone").setValue(loginPhone);
        }
        
        // If we recovered a name or fixed the phone, ensure RTDB is updated
        mDatabase.child("users").child(uid).setValue(user);
        
        hideLoading();
        saveDataAndRedirect(user);
    }

    private void saveDataAndRedirect(User user) {
        triggerManager.saveUserId(user.getId());
        triggerManager.saveUserName(user.getName());
        triggerManager.saveUserEmail(user.getEmail());
        triggerManager.saveUserPhone(user.getPhone());
        triggerManager.saveUserRole(user.getRole());
        triggerManager.saveProfileImageUri(user.getProfileImageUri());
        triggerManager.setLoggedIn(true);

        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
        checkAndRequestPermissions();
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

    private void showErrorDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void redirectUserByRole() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.SEND_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_CONTACTS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (permissionsNeeded.isEmpty()) {
            redirectUserByRole();
        } else {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) redirectUserByRole();
    }
}
