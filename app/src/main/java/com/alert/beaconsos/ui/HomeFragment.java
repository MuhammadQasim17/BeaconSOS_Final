package com.alert.beaconsos.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Contact;
import com.alert.beaconsos.data.SosHistoryEntry;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.location.LocationHelper;
import com.alert.beaconsos.utils.LoadingUtils;
import com.alert.beaconsos.utils.SmsHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HomeFragment extends Fragment {

    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;

    private MaterialCardView cardStatus;
    private LinearLayout layoutStatusDetails, layoutStatusContent;
    private TextView textViewStatus, tvStatusMic, tvStatusLoc, tvStatusSms;
    private View statusDot;
    private View customLoading;
    private TextView tvLoadingText, tvWelcome;

    private View sosPulseRing;
    private AnimatorSet pulseAnimatorSet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        triggerManager = new TriggerManager(requireContext());
        locationHelper = new LocationHelper(requireContext());
        smsHelper = new SmsHelper(requireContext());

        tvWelcome = view.findViewById(R.id.tvWelcome);
        cardStatus = view.findViewById(R.id.cardStatus);
        layoutStatusContent = view.findViewById(R.id.layoutStatusContent);
        layoutStatusDetails = view.findViewById(R.id.layoutStatusDetails);
        textViewStatus = view.findViewById(R.id.textViewStatus);
        tvStatusMic = view.findViewById(R.id.tvStatusMic);
        tvStatusLoc = view.findViewById(R.id.tvStatusLoc);
        tvStatusSms = view.findViewById(R.id.tvStatusSms);
        statusDot = view.findViewById(R.id.statusDot);
        customLoading = view.findViewById(R.id.customLoading);
        tvLoadingText = view.findViewById(R.id.tvLoadingText);
        sosPulseRing = view.findViewById(R.id.sosPulseRing);

        Button btnEmergency = view.findViewById(R.id.btnEmergency);
        
        MaterialCardView cardCallEmergency = view.findViewById(R.id.cardCallEmergency);
        MaterialCardView cardAlertMessage = view.findViewById(R.id.cardAlertMessage);
        MaterialCardView cardTrustedContacts = view.findViewById(R.id.cardTrustedContacts);

        tvWelcome.setText(getString(R.string.welcome_back, triggerManager.getUserName()));

        btnEmergency.setOnClickListener(v -> triggerEmergencySms());

        View.OnClickListener callHelplineListener = v -> {
            Intent intent = new Intent(getActivity(), EmergencyNumbersActivity.class);
            startActivity(intent);
            if (getActivity() != null) getActivity().overridePendingTransition(0, 0);
        };
        
        View.OnClickListener alertMessageListener = v -> {
            Intent intent = new Intent(getActivity(), AlertMessageActivity.class);
            startActivity(intent);
            if (getActivity() != null) getActivity().overridePendingTransition(0, 0);
        };
        
        View.OnClickListener contactsListener = v -> {
            Intent intent = new Intent(getActivity(), TrustedContactsActivity.class);
            startActivity(intent);
            if (getActivity() != null) getActivity().overridePendingTransition(0, 0);
        };

        if (cardCallEmergency != null) cardCallEmergency.setOnClickListener(callHelplineListener);
        if (cardAlertMessage != null) cardAlertMessage.setOnClickListener(alertMessageListener);
        if (cardTrustedContacts != null) cardTrustedContacts.setOnClickListener(contactsListener);

        View btnCallEmergency = view.findViewById(R.id.btnCallEmergency);
        View btnAlertMessage = view.findViewById(R.id.btnAlertMessage);
        View btnTrustedContacts = view.findViewById(R.id.btnTrustedContacts);

        if (btnCallEmergency != null) btnCallEmergency.setOnClickListener(callHelplineListener);
        if (btnAlertMessage != null) btnAlertMessage.setOnClickListener(alertMessageListener);
        if (btnTrustedContacts != null) btnTrustedContacts.setOnClickListener(contactsListener);

        setupStatusCard();
        startSosPulseAnimation();

        return view;
    }

    private void startSosPulseAnimation() {
        if (sosPulseRing == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(sosPulseRing, "scaleX", 0.92f, 1.15f, 0.92f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(sosPulseRing, "scaleY", 0.92f, 1.15f, 0.92f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(sosPulseRing, "alpha", 0.5f, 0.95f, 0.5f);

        pulseAnimatorSet = new AnimatorSet();
        pulseAnimatorSet.playTogether(scaleX, scaleY, alpha);
        pulseAnimatorSet.setDuration(2200);
        pulseAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAdded() && sosPulseRing != null && sosPulseRing.isAttachedToWindow()) {
                    pulseAnimatorSet.start();
                }
            }
        });
        pulseAnimatorSet.start();
    }

    private void setupStatusCard() {
        updateStatusUI();

        cardStatus.setOnClickListener(v -> {
            boolean isVisible = layoutStatusDetails.getVisibility() == View.VISIBLE;
            
            AutoTransition transition = new AutoTransition();
            transition.setDuration(350);
            transition.setInterpolator(new AccelerateDecelerateInterpolator());
            
            TransitionManager.beginDelayedTransition((ViewGroup) cardStatus.getParent(), transition);
            
            if (isVisible) {
                layoutStatusDetails.setVisibility(View.GONE);
            } else {
                updateStatusUI();
                layoutStatusDetails.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateStatusUI() {
        if (!isAdded()) return;

        boolean micEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isMicPermissionEnabled();
        boolean locEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isLocationPermissionEnabled();
        boolean smsEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isSmsPermissionEnabled();

        int green = ContextCompat.getColor(requireContext(), R.color.color_success);
        int red = ContextCompat.getColor(requireContext(), R.color.color_error);

        if (micEnabled || locEnabled || smsEnabled) {
            textViewStatus.setText("PROTECTED");
            textViewStatus.setTextColor(green);
            statusDot.setBackgroundResource(R.drawable.status_dot_green);
        } else {
            textViewStatus.setText("UNPROTECTED");
            textViewStatus.setTextColor(red);
            statusDot.setBackgroundResource(R.drawable.status_dot_red);
        }

        tvStatusMic.setText("• Mic: " + (micEnabled ? "Active" : "Off"));
        tvStatusMic.setTextColor(micEnabled ? green : red);

        tvStatusLoc.setText("• Location: " + (locEnabled ? "Active" : "Off"));
        tvStatusLoc.setTextColor(locEnabled ? green : red);

        tvStatusSms.setText("• SMS: " + (smsEnabled ? "Active" : "Off"));
        tvStatusSms.setTextColor(smsEnabled ? green : red);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncUserDataFromFirebase();
        updateStatusUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pulseAnimatorSet != null) {
            pulseAnimatorSet.cancel();
            pulseAnimatorSet = null;
        }
    }

    private void syncUserDataFromFirebase() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null && user.getName() != null && !user.getName().isEmpty()) {
                                triggerManager.saveUserName(user.getName());
                                if (tvWelcome != null && isAdded()) {
                                    tvWelcome.setText(getString(R.string.welcome_back, user.getName()));
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
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

    private void triggerEmergencySms() {
        List<Contact> allRecipients = triggerManager.getSosRecipients();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();

        if (allRecipients.isEmpty() || selectedIds.isEmpty()) {
            Toast.makeText(getContext(), "No contacts selected. Please go to 'Alert Message' and select contacts.", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading("Fetching Location & Sending SOS");

        locationHelper.getLastLocation(locationUrl -> {
            if (!triggerManager.isSmsPermissionEnabled()) {
                hideLoading();
                Toast.makeText(getContext(), "SMS Feature is disabled in app settings!", Toast.LENGTH_LONG).show();
                return;
            }

            String customMessage = triggerManager.getEmergencyMessage();
            String locationPart = triggerManager.isLocationPermissionEnabled() ? (" My location: " + locationUrl) : " [Location Disabled]";
            String fullMessage = customMessage + locationPart;

            List<String> names = new ArrayList<>();
            int sentCount = 0;
            for (Contact contact : allRecipients) {
                String normPhone = TriggerManager.normalizePhone(contact.getPhoneNumber());
                if (selectedIds.contains(normPhone)) {
                    smsHelper.sendSms(contact.getPhoneNumber(), fullMessage);
                    names.add(contact.getName());
                    sentCount++;
                }
            }

            hideLoading();
            if (sentCount > 0) {
                triggerManager.addSosHistoryEntry(new SosHistoryEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), fullMessage, names, "Manual SOS Button"));
                Toast.makeText(getContext(), "SOS SMS SENT TO " + sentCount + " CONTACTS", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Failed to send SOS. Check contact selection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
