package com.alert.beaconsos.audio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.alert.beaconsos.data.TriggerManager;

import java.util.ArrayList;
import java.util.Locale;

public class AudioDetector {
    private static final String TAG = "AudioDetector";
    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private final TriggerManager triggerManager;
    private volatile boolean isListening = false;
    private volatile boolean isSpeechRecognizerActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private AudioRecord audioRecord;
    private Thread monitoringThread;
    private static final int SAMPLE_RATE = 16000;
    private static final int THRESHOLD = 800; // Lowered for better sensitivity

    public interface OnTriggerDetectedListener {
        void onTriggerDetected();
    }

    private final OnTriggerDetectedListener listener;

    public AudioDetector(Context context, OnTriggerDetectedListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.triggerManager = new TriggerManager(context);
    }

    public synchronized void startListening() {
        if (isListening) return;
        isListening = true;
        Log.d(TAG, "AudioDetector: startListening");
        startSilentMonitoring();
    }

    public synchronized void stopListening() {
        isListening = false;
        isSpeechRecognizerActive = false;
        stopSilentMonitoring();
        destroySpeechRecognizer();
    }

    private synchronized void startSilentMonitoring() {
        if (!isListening || isSpeechRecognizerActive || monitoringThread != null) return;

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE) return;

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return;
            }
            audioRecord.startRecording();
            Log.d(TAG, "Silent monitoring started");
        } catch (SecurityException e) {
            Log.e(TAG, "Mic permission denied");
            return;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioRecord: " + e.getMessage());
            return;
        }

        monitoringThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isListening && !isSpeechRecognizerActive) {
                AudioRecord currentRecorder = audioRecord;
                if (currentRecorder == null || currentRecorder.getState() != AudioRecord.STATE_INITIALIZED) break;
                
                try {
                    int read = currentRecorder.read(buffer, 0, buffer.length);
                    if (read > 0 && !isSpeechRecognizerActive) {
                        long sum = 0;
                        for (int i = 0; i < read; i++) sum += Math.abs(buffer[i]);
                        double amplitude = (double) sum / read;

                        if (amplitude > THRESHOLD) {
                            Log.d(TAG, "Loud sound detected: " + (int)amplitude);
                            // Critical: Release MIC immediately in this thread before posting
                            releaseAudioRecordInternal();
                            mainHandler.post(this::triggerSpeechRecognizer);
                            return; // End thread
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Monitoring error: " + e.getMessage());
                    break;
                }
                
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            Log.d(TAG, "Monitoring thread ending");
        }, "AudioDetector-Monitor");
        monitoringThread.start();
    }

    private synchronized void releaseAudioRecordInternal() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
        monitoringThread = null;
    }

    private synchronized void stopSilentMonitoring() {
        releaseAudioRecordInternal();
    }

    private void triggerSpeechRecognizer() {
        if (isSpeechRecognizerActive || !isListening) return;
        isSpeechRecognizerActive = true;

        Log.d(TAG, "Preparing SpeechRecognizer...");
        
        // Give the OS a full second to clear the MIC lock from AudioRecord
        mainHandler.postDelayed(() -> {
            if (!isListening) {
                isSpeechRecognizerActive = false;
                return;
            }

            destroySpeechRecognizer();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "SpeechReady"); }
                @Override public void onBeginningOfSpeech() { Log.d(TAG, "SpeechBeginning"); }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { Log.d(TAG, "SpeechEnd"); }
                
                @Override public void onError(int error) { 
                    Log.e(TAG, "SpeechError: " + error);
                    resetAfterSpeech();
                }
                
                @Override public void onResults(Bundle results) {
                    checkMatches(results);
                    resetAfterSpeech();
                }
                
                @Override public void onPartialResults(Bundle partialResults) {
                    checkMatches(partialResults);
                }
                
                @Override public void onEvent(int eventType, Bundle params) {}

                private void checkMatches(Bundle bundle) {
                    ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        String trigger = triggerManager.getVoiceTriggerPhrase();
                        Log.d(TAG, "Comparing speech with trigger: " + trigger);
                        for (String result : matches) {
                            if (result.toLowerCase().contains(trigger.toLowerCase())) {
                                Log.d(TAG, "VOICE TRIGGER MATCHED!");
                                if (listener != null) listener.onTriggerDetected();
                                break;
                            }
                        }
                    }
                }

                private void resetAfterSpeech() {
                    isSpeechRecognizerActive = false;
                    destroySpeechRecognizer();
                    if (isListening) startSilentMonitoring();
                }
            });

            try {
                speechRecognizer.startListening(intent);
                Log.d(TAG, "SpeechRecognizer listening started");
            } catch (Exception e) {
                Log.e(TAG, "StartListening error: " + e.getMessage());
                isSpeechRecognizerActive = false;
                startSilentMonitoring();
            }
        }, 1000); // 1 second delay for hardware handoff
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }
}
