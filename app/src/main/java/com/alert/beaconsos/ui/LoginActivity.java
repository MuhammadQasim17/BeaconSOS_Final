package com.alert.beaconsos.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import android.view.Window;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Contact;
import com.alert.beaconsos.data.SosHistoryEntry;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.firestore.DocumentSnapshot;
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

    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etLoginPhone, etLoginPassword;
    private CountryCodePicker ccpLogin;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private View customLoading;
    private TextView tvLoadingText;

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
        
        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Auto-redirect if already logged in
        if (mAuth.getCurrentUser() != null && triggerManager.isLoggedIn()) {
            redirectUserByRole();
            return;
        }

        setContentView(R.layout.activity_login);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            AutofillManager afm = getSystemService(AutofillManager.class);
            if (afm != null) {
                afm.cancel();
                afm.disableAutofillServices();
            }
        }

        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccpLogin = findViewById(R.id.ccpLogin);
        etLoginPhone = findViewById(R.id.etLoginPhone);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoToSignup = findViewById(R.id.tvGoToSignup);

        if (ccpLogin != null) {
            ccpLogin.showArrow(false);
            ccpLogin.registerCarrierNumberEditText(etLoginPhone);

            String prefillPhone = getIntent().getStringExtra("prefill_phone");
            if (prefillPhone != null && !prefillPhone.isEmpty()) {
                ccpLogin.setFullNumber(prefillPhone);
            }
        }

        ImageView ivToggleLoginPassword = findViewById(R.id.ivToggleLoginPassword);
        if (ivToggleLoginPassword != null && etLoginPassword != null) {
            ivToggleLoginPassword.setImageResource(R.drawable.ic_visibility_off);
            ivToggleLoginPassword.setOnClickListener(v -> {
                int selection = etLoginPassword.getSelectionStart();
                int currentType = etLoginPassword.getInputType();
                if ((currentType & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    etLoginPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    ivToggleLoginPassword.setImageResource(R.drawable.ic_visibility_off);
                } else {
                    etLoginPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    ivToggleLoginPassword.setImageResource(R.drawable.ic_visibility);
                }
                if (selection >= 0) etLoginPassword.setSelection(Math.min(selection, etLoginPassword.getText().length()));
            });
        }

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

        String cleanPhone = phoneNumber.replace("+", "").replaceAll("\\s+", "");
        String virtualEmail = cleanPhone + "@beaconsos.app";
        
        mAuth.signInWithEmailAndPassword(virtualEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String uid = mAuth.getCurrentUser().getUid();
                        fetchUserProfileAndRedirect(uid, phoneNumber);
                    } else {
                        Exception e = task.getException();
                        handleLoginError(phoneNumber, e);
                    }
                });
    }

    private void handleLoginError(String phoneNumber, Exception e) {
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");

        if (e instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(errorCode)) {
                hideLoading();
                showErrorDialog("Account Disabled", "This account has been disabled.");
                return;
            } else if ("ERROR_TOO_MANY_REQUESTS".equals(errorCode)) {
                hideLoading();
                showErrorDialog("Too Many Attempts", "Too many unsuccessful login attempts. Please try again later.");
                return;
            }
        }

        mDatabase.child("registered_phones").child(cleanPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                            hideLoading();
                            showErrorDialog("Login Failed", "Incorrect password. Please try again.");
                        } else {
                            checkUsersExistInDb(phoneNumber, cleanPhone);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        checkUsersExistInDb(phoneNumber, cleanPhone);
                    }
                });
    }

    private void checkUsersExistInDb(String phoneNumber, String cleanPhone) {
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String dbPhone = child.child("phone").getValue(String.class);
                        if (dbPhone != null && isPhoneMatching(dbPhone, cleanPhone)) {
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    hideLoading();
                    mDatabase.child("registered_phones").child(cleanPhone).setValue(true);
                    showErrorDialog("Login Failed", "Incorrect password. Please try again.");
                } else {
                    FirebaseFirestore.getInstance().collection("users").get()
                            .addOnCompleteListener(task -> {
                                boolean fsFound = false;
                                if (task.isSuccessful() && task.getResult() != null) {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                                        String fsPhone = doc.getString("phone");
                                        if (fsPhone != null && isPhoneMatching(fsPhone, cleanPhone)) {
                                            fsFound = true;
                                            break;
                                        }
                                    }
                                }

                                hideLoading();
                                if (fsFound) {
                                    mDatabase.child("registered_phones").child(cleanPhone).setValue(true);
                                    showErrorDialog("Login Failed", "Incorrect password. Please try again.");
                                } else {
                                    showAccountNotFoundDialog(phoneNumber);
                                }
                            })
                            .addOnFailureListener(err -> {
                                hideLoading();
                                showAccountNotFoundDialog(phoneNumber);
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                showAccountNotFoundDialog(phoneNumber);
            }
        });
    }

    private boolean isPhoneMatching(String dbPhone, String cleanInputPhone) {
        if (dbPhone == null || cleanInputPhone == null) return false;
        String cleanDbPhone = dbPhone.replaceAll("[^0-9]", "");
        if (cleanDbPhone.equals(cleanInputPhone)) return true;

        int inputLen = cleanInputPhone.length();
        int dbLen = cleanDbPhone.length();
        int matchLength = Math.min(Math.min(inputLen, dbLen), 10);
        if (matchLength >= 8) {
            String inputSuffix = cleanInputPhone.substring(inputLen - matchLength);
            String dbSuffix = cleanDbPhone.substring(dbLen - matchLength);
            return inputSuffix.equals(dbSuffix);
        }
        return false;
    }

    private void showAccountNotFoundDialog(String phoneNumber) {
        new AlertDialog.Builder(this)
                .setTitle("Account Not Found")
                .setMessage("No account exists for " + phoneNumber + ".\n\nPlease sign up to create a new account.")
                .setPositiveButton("Go to Sign Up", (dialog, which) -> {
                    Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchUserProfileAndRedirect(String uid, String loginPhone) {
        mDatabase.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        if (user.getName() == null || user.getName().isEmpty()) {
                            recoverNameFromFirestore(user, uid, loginPhone);
                        } else {
                            finalizeLogin(user, uid, loginPhone);
                        }
                    }
                } else {
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
        if (user.getPhone() == null || user.getPhone().isEmpty() || !user.getPhone().equals(loginPhone)) {
            user.setPhone(loginPhone);
            mDatabase.child("users").child(uid).child("phone").setValue(loginPhone);
        }
        
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

        syncUserCloudData(user.getId());

        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
        checkAndRequestPermissions();
    }

    private void syncUserCloudData(String uid) {
        if (uid == null || uid.isEmpty()) return;

        // 1. Restore Contacts from Cloud Firestore
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("contacts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        List<Contact> contacts = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            Contact contact = doc.toObject(Contact.class);
                            if (contact != null) contacts.add(contact);
                        }
                        triggerManager.saveContacts(contacts);
                    }
                });

        // 2. Restore SOS History from Cloud Firestore
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("sos_history")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        List<SosHistoryEntry> history = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            SosHistoryEntry entry = doc.toObject(SosHistoryEntry.class);
                            if (entry != null) history.add(entry);
                        }
                        triggerManager.saveSosHistory(history);
                    }
                });

        // 3. Restore SOS Configuration & Settings from Cloud Firestore
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String msg = documentSnapshot.getString("emergencyMessage");
                        if (msg != null && !msg.isEmpty()) triggerManager.saveEmergencyMessage(msg);

                        String voice = documentSnapshot.getString("voiceTriggerPhrase");
                        if (voice != null && !voice.isEmpty()) triggerManager.saveVoiceTriggerPhrase(voice);

                        Boolean shake = documentSnapshot.getBoolean("shakeTriggerEnabled");
                        if (shake != null) triggerManager.setShakeTriggerEnabled(shake);

                        List<?> rawIds = (List<?>) documentSnapshot.get("selectedContactIds");
                        if (rawIds != null) {
                            java.util.Set<String> ids = new java.util.HashSet<>();
                            for (Object obj : rawIds) {
                                if (obj != null) ids.add(obj.toString());
                            }
                            triggerManager.saveSelectedContactIds(ids);
                        }
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
