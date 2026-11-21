package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Window;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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
    protected void setupStatusBar() {
        super.setupStatusBar();
        if (!isNightMode()) {
            Window window = getWindow();
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
            insetsController.setAppearanceLightStatusBars(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            AutofillManager afm = getSystemService(AutofillManager.class);
            if (afm != null) {
                afm.cancel();
                afm.disableAutofillServices();
            }
        }

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

        if (ccp != null) {
            ccp.showArrow(false);
            ccp.registerCarrierNumberEditText(etPhone);
        }

        ImageView ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ImageView ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);

        setupPasswordVisibilityToggle(ivTogglePassword, etPassword);
        setupPasswordVisibilityToggle(ivToggleConfirmPassword, etConfirmPassword);

        setupPasswordValidation();
        setupPhoneAuthCallbacks();
        btnRegister.setOnClickListener(v -> handleSignup());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupPasswordVisibilityToggle(ImageView toggleButton, EditText editText) {
        if (toggleButton == null || editText == null) return;
        toggleButton.setImageResource(R.drawable.ic_visibility_off);
        toggleButton.setOnClickListener(v -> {
            int selection = editText.getSelectionStart();
            int currentType = editText.getInputType();
            if ((currentType & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                toggleButton.setImageResource(R.drawable.ic_visibility_off);
            } else {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                toggleButton.setImageResource(R.drawable.ic_visibility);
            }
            if (selection >= 0) editText.setSelection(Math.min(selection, editText.getText().length()));
        });
    }

    private void setupPhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
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
                .setPositiveButton("Verify", null)
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
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
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

        checkAccountExistsAndProceed(ccp.getFullNumberWithPlus());
    }

    private void checkAccountExistsAndProceed(String phoneNumber) {
        if (!isNetworkAvailable()) {
            showErrorDialog("Network Error", "Internet connection is required for security verification.");
            return;
        }

        showLoading("Checking Account...");
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
        String normalizedPhone = "+" + cleanPhone;
        String virtualEmail = cleanPhone + "@beaconsos.app";

        mAuth.createUserWithEmailAndPassword(virtualEmail, "CheckAuthExistTemp123!")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.getCurrentUser().delete().addOnCompleteListener(deleteTask -> {
                                mAuth.signOut();
                                hideLoading();
                                sendVerificationCode(normalizedPhone);
                            });
                        } else {
                            mAuth.signOut();
                            hideLoading();
                            sendVerificationCode(normalizedPhone);
                        }
                    } else {
                        hideLoading();
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthUserCollisionException || 
                           (e != null && e.getMessage() != null && (e.getMessage().toLowerCase().contains("already") || e.getMessage().toLowerCase().contains("collision") || e.getMessage().toLowerCase().contains("in-use") || e.getMessage().toLowerCase().contains("exist")))) {
                            showAccountExistsDialog(normalizedPhone);
                        } else {
                            if (e != null && e.getMessage() != null && (e.getMessage().toLowerCase().contains("network") || e.getMessage().toLowerCase().contains("connection"))) {
                                showErrorDialog("Network Error", "Internet connection is required for security verification.");
                            } else {
                                sendVerificationCode(normalizedPhone);
                            }
                        }
                    }
                });
    }

    private void showAccountExistsDialog(String phoneNumber) {
        new AlertDialog.Builder(this)
                .setTitle("Account Already Exists")
                .setMessage("An account with the number " + phoneNumber + " is already registered.\n\nPlease log in to continue.")
                .setPositiveButton("Go to Login", (dialog, which) -> {
                    Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                    intent.putExtra("prefill_phone", phoneNumber);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        String role = "User";

        showLoading("Creating Your Account");

        if (mAuth.getCurrentUser() == null) {
            hideLoading();
            showErrorDialog("Session Error", "Phone verification failed. Please try again.");
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        String virtualEmail = phone.replace("+", "").replaceAll("\\s+", "") + "@beaconsos.app";
        User user = new User(userId, name, virtualEmail, phone, role);

        AuthCredential credential = EmailAuthProvider.getCredential(virtualEmail, password);
        mAuth.getCurrentUser().linkWithCredential(credential)
                .addOnCompleteListener(linkTask -> {
                    if (linkTask.isSuccessful()) {
                        mDatabase.child("registered_phones").child(cleanPhone).setValue(true);
                        mDatabase.child("users").child(userId).setValue(user)
                                .addOnSuccessListener(aVoid -> {
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
                    } else {
                        hideLoading();
                        mAuth.signOut();
                        Exception e = linkTask.getException();
                        if (e instanceof FirebaseAuthUserCollisionException || (e != null && e.getMessage() != null && (e.getMessage().toLowerCase().contains("already") || e.getMessage().toLowerCase().contains("collision") || e.getMessage().toLowerCase().contains("in-use")))) {
                            mDatabase.child("registered_phones").child(cleanPhone).setValue(true);
                            showAccountExistsDialog(phone);
                        } else {
                            showErrorDialog("Registration Error", e != null ? e.getMessage() : "Failed to create account.");
                        }
                    }
                });
    }
}
