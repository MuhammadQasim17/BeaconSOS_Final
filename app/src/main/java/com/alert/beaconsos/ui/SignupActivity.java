package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class SignupActivity extends BaseActivity {

    private static final String TAG = "SignupActivity";

    private EditText etFullName, etPassword, etConfirmPassword, etPhone;
    private TextView ruleNumber, ruleLower, ruleUpper, ruleSpecial, ruleLength, tvMatchError;
    private CountryCodePicker ccp;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseFirestore mFirestore;
    private View customLoading;
    private TextView tvLoadingText;

    private boolean isPasswordValid = false;
    private boolean isMatchValid = false;

    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mFirestore = FirebaseFirestore.getInstance();

        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccp = findViewById(R.id.ccp);
        etPhone = findViewById(R.id.etPhone);
        etFullName = findViewById(R.id.etFullName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        
        ruleNumber = findViewById(R.id.ruleNumber);
        ruleLower = findViewById(R.id.ruleLower);
        ruleUpper = findViewById(R.id.ruleUpper);
        ruleSpecial = findViewById(R.id.ruleSpecial);
        ruleLength = findViewById(R.id.ruleLength);
        tvMatchError = findViewById(R.id.tvMatchError);
        
        Button btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);

        // Removed role selection
        ccp.registerCarrierNumberEditText(etPhone);

        setupPasswordValidation();
        setupPhoneAuthCallbacks();
        btnRegister.setOnClickListener(v -> handleSignup());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupPhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification: In some cases the phone number can be instantly 
                //     verified without needing to send or enter a code.
                // 2 - Auto-retrieval: On some devices Google Play services can automatically 
                //     detect the incoming verification SMS and perform verification without 
                //     user action.
                hideLoading();
                verifyAndRegister(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                hideLoading();
                Log.e(TAG, "Phone Auth Failure", e);
                
                String message = "The given number is unauthorized.";
                String errorBody = e.getMessage() != null ? e.getMessage() : "";

                if (errorBody.contains("too many requests")) {
                    message = "Too many attempts detected. Please try again later.";
                } else if (errorBody.toLowerCase().contains("network") || errorBody.toLowerCase().contains("connection")) {
                    message = "Verification failed due to a network error.";
                }
                
                showErrorDialog("Unauthorized Number", message);
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                hideLoading();
                mVerificationId = verificationId;
                mResendToken = token;
                showOtpDialog();
            }
        };
    }

    private void showOtpDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_otp, null);
        EditText etOtp = dialogView.findViewById(R.id.etOtp);
        TextView tvResend = dialogView.findViewById(R.id.tvResendOtp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Verify Phone Number")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Verify", null) // Set later to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String code = etOtp.getText().toString().trim();
                if (code.length() == 6) {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
                    verifyAndRegister(credential);
                    dialog.dismiss();
                } else {
                    etOtp.setError("Enter 6-digit code");
                }
            });
        });

        tvResend.setOnClickListener(v -> {
            dialog.dismiss();
            sendVerificationCode(ccp.getFullNumberWithPlus());
        });

        dialog.show();
    }

    private void verifyAndRegister(PhoneAuthCredential credential) {
        showLoading("Verifying Code...");
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        registerUser();
                    } else {
                        hideLoading();
                        showErrorDialog("Verification Failed", "The code entered is incorrect.");
                    }
                });
    }

    private void setupPasswordValidation() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword(s.toString());
                checkPasswordMatch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordMatch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void validatePassword(String password) {
        boolean hasNum = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find();
        boolean hasLength = password.length() >= 8;

        updateRuleUI(ruleNumber, hasNum, R.string.rule_number_check, R.string.rule_number_error);
        updateRuleUI(ruleLower, hasLower, R.string.rule_lower_check, R.string.rule_lower_error);
        updateRuleUI(ruleUpper, hasUpper, R.string.rule_upper_check, R.string.rule_upper_error);
        updateRuleUI(ruleSpecial, hasSpecial, R.string.rule_special_check, R.string.rule_special_error);
        updateRuleUI(ruleLength, hasLength, R.string.rule_length_check, R.string.rule_length_error);

        isPasswordValid = hasNum && hasLower && hasUpper && hasSpecial && hasLength;
    }

    private void updateRuleUI(TextView textView, boolean isValid, int validResId, int invalidResId) {
        if (isValid) {
            textView.setText(validResId);
            textView.setTextColor(ContextCompat.getColor(this, R.color.success_green));
        } else {
            textView.setText(invalidResId);
            textView.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        }
    }

    private void checkPasswordMatch() {
        String p1 = etPassword.getText().toString();
        String p2 = etConfirmPassword.getText().toString();
        
        if (p2.isEmpty()) {
            tvMatchError.setVisibility(View.GONE);
            isMatchValid = false;
        } else if (p1.equals(p2)) {
            tvMatchError.setVisibility(View.GONE);
            isMatchValid = true;
        } else {
            tvMatchError.setVisibility(View.VISIBLE);
            isMatchValid = false;
        }
    }

    private void handleSignup() {
        if (!isNetworkAvailable()) {
            showErrorDialog("Network Error", "Internet connection is required for security verification.");
            return;
        }

        String name = etFullName.getText().toString().trim();
        String phoneInput = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            showErrorDialog("Input Required", "Please enter your full name.");
            return;
        }

        if (TextUtils.isEmpty(phoneInput)) {
            showErrorDialog("Input Required", "Please enter your phone number.");
            return;
        }

        String rawPhone = etPhone.getText().toString().replaceAll("\\s+", "");
        if (!ccp.isValidFullNumber() && rawPhone.length() < 7) {
            String errorMsg = "The phone number format is incorrect for the selected country.\n\nTip: Enter the number without the leading zero (e.g., if your number is 0321..., just enter 321...).";
            showErrorDialog("Invalid Format", errorMsg);
            return;
        }

        if (!isPasswordValid) {
            showErrorDialog("Password Weak", "Please follow the password requirements shown on screen.");
            return;
        }

        if (!isMatchValid) {
            showErrorDialog("Password Mismatch", "Passwords do not match.");
            return;
        }

        sendVerificationCode(ccp.getFullNumberWithPlus());
    }

    private void sendVerificationCode(String phoneNumber) {
        showLoading("Sending Verification SMS...");
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Dismiss", null)
                .show();
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

    private void registerUser() {
        String name = etFullName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = ccp.getFullNumberWithPlus();
        String role = "User"; // Default role

        showLoading("Creating Your Account");

        if (mAuth.getCurrentUser() == null) {
            hideLoading();
            showErrorDialog("Session Error", "Phone verification failed. Please try again.");
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        String virtualEmail = phone.replace("+", "") + "@beaconsos.app";
        User user = new User(userId, name, virtualEmail, phone, role);

        // Link with email and password to allow password-only login later
        AuthCredential credential = EmailAuthProvider.getCredential(virtualEmail, password);
        mAuth.getCurrentUser().linkWithCredential(credential)
                .addOnCompleteListener(linkTask -> {
                    // Continue even if linking fails (might fail if user already exists or other issues)
                    // but usually it should succeed here.
                    
                    // Save to Realtime Database
                    mDatabase.child("users").child(userId).setValue(user)
                            .addOnSuccessListener(aVoid -> {
                                // Also Save to Firestore for searching/groups
                                mFirestore.collection("users").document(userId).set(user);

                                hideLoading();
                                triggerManager.saveUserId(userId);
                                triggerManager.saveUserName(user.getName());
                                triggerManager.saveUserEmail(user.getEmail());
                                triggerManager.saveUserPhone(user.getPhone());
                                triggerManager.saveUserRole(user.getRole());
                                triggerManager.setLoggedIn(true);

                                Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                hideLoading();
                                showErrorDialog("Database Error", "Failed to save profile: " + e.getMessage());
                            });
                });
    }
}