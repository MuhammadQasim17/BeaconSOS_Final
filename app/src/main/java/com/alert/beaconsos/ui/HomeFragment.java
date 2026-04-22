package com.alert.beaconsos.ui;

import android.Manifest;
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
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.location.LocationHelper;
import com.alert.beaconsos.utils.LoadingUtils;
import com.alert.beaconsos.utils.SmsHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment {

    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;

    private MaterialCardView cardStatus;
    private LinearLayout layoutStatusDetails, layoutStatusContent;
    private TextView textViewStatus, tvStatusMic, tvStatusLoc, tvStatusSms;
    private View statusDot;
    private View customLoading;
    private TextView tvLoadingText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        triggerManager = new TriggerManager(requireContext());
        locationHelper = new LocationHelper(requireContext());
        smsHelper = new SmsHelper(requireContext());

        TextView tvWelcome = view.findViewById(R.id.tvWelcome);
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

        Button btnEmergency = view.findViewById(R.id.btnEmergency);
        Button btnCallEmergency = view.findViewById(R.id.btnCallEmergency);
        Button btnAlertMessage = view.findViewById(R.id.btnAlertMessage);
        Button btnTrustedContacts = view.findViewById(R.id.btnTrustedContacts);

        tvWelcome.setText(getString(R.string.welcome_back, triggerManager.getUserName()));

        btnEmergency.setOnClickListener(v -> triggerEmergencySms());

        btnCallEmergency.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), EmergencyNumbersActivity.class));
        });

        btnAlertMessage.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AlertMessageActivity.class));
        });

        btnTrustedContacts.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), TrustedContactsActivity.class));
        });

        setupStatusCard();

        return view;
    }

    private void setupStatusCard() {
        updateStatusUI();

        cardStatus.setOnClickListener(v -> {
            boolean isVisible = layoutStatusDetails.getVisibility() == View.VISIBLE;
            
            // Define a smoother transition
            AutoTransition transition = new AutoTransition();
            transition.setDuration(400); // Slightly longer for a more fluid feel
            transition.setInterpolator(new AccelerateDecelerateInterpolator());
            
            // Animate the entire card row for a more consistent expansion
            TransitionManager.beginDelayedTransition((ViewGroup) cardStatus.getParent(), transition);
            
            if (isVisible) {
                layoutStatusDetails.setVisibility(View.GONE);
            } else {
                updateStatusUI(); // Refresh statuses before showing
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

        // Colors from resources
        int green = ContextCompat.getColor(requireContext(), R.color.success_green);
        int yellow = ContextCompat.getColor(requireContext(), R.color.warning_yellow);
        int red = ContextCompat.getColor(requireContext(), R.color.error_red);

        // Update main status text and dot color based on permissions
        if (micEnabled && locEnabled) {
            // Full protection (Mic + Location)
            textViewStatus.setText("PROTECTED");
            textViewStatus.setTextColor(green);
            statusDot.setBackgroundResource(R.drawable.status_dot_green);
        } else if (smsEnabled) {
            // Partial protection (SMS only or SMS + one other)
            textViewStatus.setText("PROTECTED");
            textViewStatus.setTextColor(yellow);
            statusDot.setBackgroundResource(R.drawable.status_dot_yellow);
        } else {
            // No critical permissions granted
            textViewStatus.setText("UNPROTECTED");
            textViewStatus.setTextColor(red);
            statusDot.setBackgroundResource(R.drawable.status_dot_red);
        }

        // Update detail texts with status-specific colors (Green if active, Red if off)
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
        updateStatusUI();
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
        List<Contact> allContacts = triggerManager.getContacts();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();

        if (allContacts.isEmpty() || selectedIds.isEmpty()) {
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

            int sentCount = 0;
            for (Contact contact : allContacts) {
                if (selectedIds.contains(contact.getId())) {
                    smsHelper.sendSms(contact.getPhoneNumber(), fullMessage);
                    sentCount++;
                }
            }

            hideLoading();
            if (sentCount > 0) {
                Toast.makeText(getContext(), "SOS SMS SENT TO " + sentCount + " CONTACTS", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Failed to send SOS. Check contact selection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
