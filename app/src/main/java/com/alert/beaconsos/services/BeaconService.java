package com.alert.beaconsos.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.alert.beaconsos.R;
import com.alert.beaconsos.audio.AudioDetector;
import com.alert.beaconsos.data.Contact;
import com.alert.beaconsos.data.SosHistoryEntry;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.location.LocationHelper;
import com.alert.beaconsos.utils.ShakeDetector;
import com.alert.beaconsos.utils.SmsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BeaconService extends Service implements AudioDetector.OnTriggerDetectedListener, ShakeDetector.OnShakeListener {

    private static final String TAG = "BeaconService";
    private static final String CHANNEL_ID = "BeaconServiceChannel";
    private AudioDetector audioDetector;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;
    private TriggerManager triggerManager;
    
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    
    private boolean isPhoneCallActive = false;
    private boolean isSendingSos = false;
    private boolean isInitialized = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if ("perm_mic_enabled".equals(key)) checkAndStartMic();
    };

    private final BroadcastReceiver callReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            isPhoneCallActive = TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state);
            if (isPhoneCallActive && audioDetector != null) audioDetector.stopListening();
            else if (audioDetector != null) checkAndStartMic();
        }
    };

    private void executeSosAction(String triggerType) {
        if (!isSendingSos) {
            isSendingSos = true;
            vibrateFeedback();
            mHandler.post(() -> Toast.makeText(getApplicationContext(), "SOS TRIGGER DETECTED!", Toast.LENGTH_SHORT).show());
            triggerSosProcedure(triggerType);
            mHandler.postDelayed(() -> isSendingSos = false, 5000); // 5s cooldown
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        triggerManager = new TriggerManager(this);
        locationHelper = new LocationHelper(this);
        smsHelper = new SmsHelper(this);
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(this);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeaconSOS:TriggerWakeLock");
            wakeLock.setReferenceCounted(false);
            if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 60 * 1000L); // High-persistence lock
        }

        registerReceiver(callReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
        
        getSharedPreferences("BeaconSOSPrefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener);
        audioDetector = new AudioDetector(this, this);

        // Startup Safety Delay
        mHandler.postDelayed(() -> {
            isInitialized = true;
        }, 3000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "TRIGGER_SOS_FROM_TIMER".equals(intent.getAction())) {
            executeSosAction("Safety Timer");
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Beacon SOS Protection Active")
                .setContentText("Monitoring for emergency triggers...")
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true).build();

        try {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(1, notification, type);
            else startForeground(1, notification);
        } catch (Exception e) { startForeground(1, notification); }
        
        checkAndStartMic();
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        return START_STICKY;
    }

    private void checkAndStartMic() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (granted && triggerManager.isMicPermissionEnabled() && !isPhoneCallActive) {
            if (audioDetector != null) audioDetector.startListening();
        } else if (audioDetector != null) audioDetector.stopListening();
    }

    @Override
    public void onShake(int count) {
        if (isInitialized && triggerManager.isShakeTriggerEnabled() && count >= 3) {
            executeSosAction("Shake Trigger");
        }
    }

    private void vibrateFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(500);
        }
    }

    @Override
    public void onTriggerDetected() {
        if (isInitialized) {
            executeSosAction("Voice Trigger");
        }
    }

    private void triggerSosProcedure(String triggerType) {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(45000); 
        sendEmergencySms(triggerType);
    }

    private void sendEmergencySms(String triggerType) {
        List<Contact> allRecipients = triggerManager.getSosRecipients();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();
        
        if (allRecipients.isEmpty() || selectedIds.isEmpty()) {
            isSendingSos = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        locationHelper.getLastLocation(locationUrl -> {
            if (!triggerManager.isSmsPermissionEnabled()) {
                isSendingSos = false;
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }

            String msg = triggerManager.getEmergencyMessage();
            String full = msg.contains("[URL]") ? msg.replace("[URL]", locationUrl != null ? locationUrl : "[Location Unknown]") : msg + (locationUrl != null ? " " + locationUrl : "");
            
            List<String> names = new ArrayList<>();
            int count = 0;
            for (Contact contact : allRecipients) {
                String normPhone = TriggerManager.normalizePhone(contact.getPhoneNumber());
                if (selectedIds.contains(normPhone)) {
                    smsHelper.sendSms(contact.getPhoneNumber(), full);
                    names.add(contact.getName());
                    count++;
                }
            }
            
            if (count > 0) {
                triggerManager.addSosHistoryEntry(new SosHistoryEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), full, names, triggerType));
            }
            
            mHandler.post(() -> {
                Toast.makeText(getApplicationContext(), "EMERGENCY SOS SENT (" + triggerType + ")", Toast.LENGTH_LONG).show();
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            });
        });
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(callReceiver); } catch (Exception ignored) {}
        getSharedPreferences("BeaconSOSPrefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener);
        mSensorManager.unregisterListener(mShakeDetector);
        if (audioDetector != null) audioDetector.stopListening();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Beacon Service Channel", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
