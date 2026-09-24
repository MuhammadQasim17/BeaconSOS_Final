package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.services.BeaconService;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class SafetyTimerFragment extends Fragment {

    private TextView tvTimerDisplay, tvTimerStatus;
    private EditText etHours, etMinutes, etSeconds;
    private MaterialButton btnStart, btnCheckIn, btnApplyCustomTime;
    private View layoutTimeSelection;
    
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = 0;
    private boolean timerRunning = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_safety_timer, container, false);

        tvTimerDisplay = view.findViewById(R.id.tvTimerDisplay);
        tvTimerStatus = view.findViewById(R.id.tvTimerStatus);
        btnStart = view.findViewById(R.id.btnStartTimer);
        btnCheckIn = view.findViewById(R.id.btnCheckIn);
        etHours = view.findViewById(R.id.etHours);
        etMinutes = view.findViewById(R.id.etMinutes);
        etSeconds = view.findViewById(R.id.etSeconds);
        btnApplyCustomTime = view.findViewById(R.id.btnApplyCustomTime);
        layoutTimeSelection = view.findViewById(R.id.layoutTimeSelection);

        MaterialButton btn5 = view.findViewById(R.id.btnTime5);
        MaterialButton btn15 = view.findViewById(R.id.btnTime15);
        MaterialButton btn30 = view.findViewById(R.id.btnTime30);

        btn5.setOnClickListener(v -> setTime(0, 5, 0));
        btn15.setOnClickListener(v -> setTime(0, 15, 0));
        btn30.setOnClickListener(v -> setTime(0, 30, 0));

        btnApplyCustomTime.setOnClickListener(v -> {
            int h = parseTime(etHours);
            int m = parseTime(etMinutes);
            int s = parseTime(etSeconds);

            if (h == 0 && m == 0 && s == 0) {
                Toast.makeText(getContext(), "Please set a duration", Toast.LENGTH_SHORT).show();
                return;
            }

            if (m >= 60 || s >= 60) {
                Toast.makeText(getContext(), "Minutes and seconds must be less than 60", Toast.LENGTH_SHORT).show();
                return;
            }

            setTime(h, m, s);
            etHours.clearFocus();
            etMinutes.clearFocus();
            etSeconds.clearFocus();
        });

        btnStart.setOnClickListener(v -> startTimer());
        btnCheckIn.setOnClickListener(v -> stopTimer());

        updateCountDownText();

        return view;
    }

    private int parseTime(EditText editText) {
        String val = editText.getText().toString().trim();
        if (val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setTime(int hours, int minutes, int seconds) {
        if (timerRunning) return;
        timeLeftInMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L;
        updateCountDownText();
    }

    private void startTimer() {
        if (timeLeftInMillis <= 0) {
            Toast.makeText(getContext(), "Please select a duration first", Toast.LENGTH_SHORT).show();
            return;
        }

        timerRunning = true;
        tvTimerStatus.setText("PROTECTION ACTIVE");
        tvTimerStatus.setTextColor(0xFF4CAF50);
        btnStart.setVisibility(View.GONE);
        btnCheckIn.setVisibility(View.VISIBLE);
        layoutTimeSelection.setVisibility(View.GONE);

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                tvTimerStatus.setText("TIMER EXPIRED - SOS TRIGGERED");
                tvTimerStatus.setTextColor(0xFFD32F2F);
                triggerSos();
            }
        }.start();
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timerRunning = false;
        timeLeftInMillis = 0;
        updateCountDownText();
        tvTimerStatus.setText("Inactive");
        tvTimerStatus.setTextColor(0xFFFFEB3B);
        btnStart.setVisibility(View.VISIBLE);
        btnCheckIn.setVisibility(View.GONE);
        layoutTimeSelection.setVisibility(View.VISIBLE);
        Toast.makeText(getContext(), "Safety Check-in Successful", Toast.LENGTH_SHORT).show();
    }

    private void updateCountDownText() {
        int hours = (int) (timeLeftInMillis / 1000) / 3600;
        int minutes = (int) ((timeLeftInMillis / 1000) % 3600) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        
        String timeLeftFormatted;
        if (hours > 0) {
            timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
        tvTimerDisplay.setText(timeLeftFormatted);
    }

    private void triggerSos() {
        if (getActivity() != null) {
            // We can't directly call the service's private method, but we can trigger it via a broadcast or intent
            // Since BeaconService is already running and monitoring, we can simulate an SOS trigger.
            // For now, let's show a toast and we can implement a proper trigger mechanism.
            Toast.makeText(getContext(), "SOS ALERT SENT AUTOMATICALLY!", Toast.LENGTH_LONG).show();
            
            // To properly trigger the same logic as Shake/Voice, we should communicate with BeaconService
            android.content.Intent intent = new android.content.Intent(getContext(), com.alert.beaconsos.services.BeaconService.class);
            intent.setAction("TRIGGER_SOS_FROM_TIMER");
            getActivity().startService(intent);
        }
    }
}
