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
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
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

    // Volume Trigger Variables
    private long lastVolumePressTime = 0;
    private static final long VOLUME_INTERVAL = 1500; // Increased window for manual presses
    private boolean isSendingSos = false;
    private VolumeObserver volumeObserver;
    private MediaSession mediaSession;

    private final BroadcastReceiver callReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                isPhoneCallActive = true;
                if (audioDetector != null) audioDetector.stopListening();
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                isPhoneCallActive = false;
                checkAndStartMic();
            }
        }
    };

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                Log.d(TAG, "Screen OFF. Reinforcing detection.");
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(30 * 60 * 1000L); // 30 mins to ensure background work
                }
            }
        }
    };

    private final BroadcastReceiver volumeButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                if (triggerManager.isVolumeTriggerEnabled()) {
                    int volume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                    int prevVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
                    int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                    handleVolumePress(streamType, volume, prevVolume);
                }
            }
        }
    };

    private class VolumeObserver extends ContentObserver {
        private final AudioManager am;
        private int lastVol;

        public VolumeObserver(Handler handler) {
            super(handler);
            am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            lastVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (!triggerManager.isVolumeTriggerEnabled()) return;
            
            int currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentVol != lastVol) {
                Log.d(TAG, "Volume Observer detected change: " + lastVol + " -> " + currentVol);
                handleVolumePress(AudioManager.STREAM_MUSIC, currentVol, lastVol);
                lastVol = currentVol;
            }
        }
    }

    private void handleVolumePress(int streamType, int volume, int prevVolume) {
        if (streamType == -1) streamType = AudioManager.STREAM_MUSIC;
        
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(streamType);

        // Detect Volume UP or attempt to go UP at max
        if (volume >= prevVolume && prevVolume != -1) {
            // Nudge logic to ensure future presses are detected as "changes"
            if (volume == max) {
                am.setStreamVolume(streamType, max - 1, 0);
            }

            long currentTime = System.currentTimeMillis();
            if (lastVolumePressTime != 0 && (currentTime - lastVolumePressTime < VOLUME_INTERVAL)) {
                if (!isSendingSos) {
                    isSendingSos = true;
                    Log.d(TAG, "DOUBLE VOLUME PRESS DETECTED! Triggering SOS.");
                    vibrateFeedback();
                    triggerSosProcedure("Volume Trigger");
                    mHandler.postDelayed(() -> isSendingSos = false, 5000);
                }
                lastVolumePressTime = 0;
            } else {
                lastVolumePressTime = currentTime;
            }
        } else if (volume < prevVolume) {
            // Volume DOWN resets the timer
            lastVolumePressTime = 0;
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

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeaconSOS:TriggerWakeLock");
            wakeLock.setReferenceCounted(false);
        }

        // 1. Broadcast Receiver for Volume (Legacy/Normal mode)
        IntentFilter volumeFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        volumeFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(volumeButtonReceiver, volumeFilter);

        // 2. Content Observer for Volume (Reliable Screen-OFF mode)
        volumeObserver = new VolumeObserver(mHandler);
        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver);

        // 3. Receivers for Lifecycle/Phone State
        IntentFilter callFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(callReceiver, callFilter);

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOffReceiver, screenFilter);

        // 4. Media Session (Keeps service "Active" in system's eye)
        setupMediaSession();
    }

    private void setupMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "BeaconSOS");
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                    .build());
            mediaSession.setActive(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 60 * 1000L); // 10 hours
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Beacon SOS Active")
                .setContentText("Monitoring for your safety...")
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                startForeground(1, notification, type);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(1, notification);
            }
        } catch (Exception e) {
            try { startForeground(1, notification); } catch (Exception ignored) {}
        }

        if (audioDetector == null) {
            audioDetector = new AudioDetector(this, this);
        }
        
        checkAndStartMic();
        nudgeVolumeDownIfMax();

        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        return START_STICKY;
    }

    private void nudgeVolumeDownIfMax() {
        try {
            if (triggerManager.isVolumeTriggerEnabled()) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (volume == max) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max - 1, 0);
                }
            }
        } catch (Exception e) {}
    }

    private void checkAndStartMic() {
        boolean micPermGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean micEnabledInApp = triggerManager.isMicPermissionEnabled();
        if (micPermGranted && micEnabledInApp && !isPhoneCallActive) {
            if (audioDetector != null) audioDetector.startListening();
        } else {
            if (audioDetector != null) audioDetector.stopListening();
        }
    }

    @Override
    public void onShake(int count) {
        if (triggerManager.isShakeTriggerEnabled() && count >= 2) {
            if (!isSendingSos) {
                isSendingSos = true;
                vibrateFeedback();
                triggerSosProcedure("Shake Trigger");
                mHandler.postDelayed(() -> isSendingSos = false, 5000);
            }
        }
    }

    private void vibrateFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        }
    }

    @Override
    public void onTriggerDetected() {
        if (!isSendingSos) {
            isSendingSos = true;
            vibrateFeedback();
            triggerSosProcedure("Voice Trigger");
            mHandler.postDelayed(() -> isSendingSos = false, 5000);
        }
    }

    private void triggerSosProcedure(String triggerType) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(45000); // 45 seconds to ensure GPS/Network finishes
        }
        sendEmergencySms(triggerType);
    }

    private void sendEmergencySms(String triggerType) {
        List<Contact> allContacts = triggerManager.getContacts();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();
        
        if (allContacts.isEmpty() || selectedIds.isEmpty()) {
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

            String customMessage = triggerManager.getEmergencyMessage();
            String locationPart = (triggerManager.isLocationPermissionEnabled() && 
                                  ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
                                  ? (" " + locationUrl) : " [Location Disabled]";
            
            String fullMessage = customMessage + locationPart;
            List<String> sentToNames = new ArrayList<>();
            int sentCount = 0;
            
            for (Contact contact : allContacts) {
                if (selectedIds.contains(contact.getId())) {
                    smsHelper.sendSms(contact.getPhoneNumber(), fullMessage);
                    sentToNames.add(contact.getName());
                    sentCount++;
                }
            }
            
            if (sentCount > 0) {
                SosHistoryEntry entry = new SosHistoryEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    fullMessage,
                    sentToNames,
                    triggerType
                );
                triggerManager.addSosHistoryEntry(entry);
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
        try { unregisterReceiver(screenOffReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(volumeButtonReceiver); } catch (Exception ignored) {}
        if (volumeObserver != null) getContentResolver().unregisterContentObserver(volumeObserver);
        mSensorManager.unregisterListener(mShakeDetector);
        if (audioDetector != null) audioDetector.stopListening();
        if (mediaSession != null) mediaSession.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Beacon Service Channel", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }
}
