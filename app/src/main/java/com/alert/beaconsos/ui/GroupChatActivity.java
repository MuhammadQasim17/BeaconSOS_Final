package com.alert.beaconsos.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.ChatMessage;
import com.alert.beaconsos.data.Group;
import com.alert.beaconsos.data.TriggerManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GroupChatActivity extends BaseActivity {
    private static final String TAG = "GroupChatActivity";
    private static final int REQUEST_PERMISSION = 200;
    private static final String DB_URL = "https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private RecyclerView rvMessages;
    private EmojiEditText etMessage;
    private ImageButton btnSend, btnMic;
    private View layoutMessageInput, layoutAudioRecording;
    private TextView tvRecordingTimer, tvRecordingStatus;
    private ImageButton btnPlayPreview;

    private View customLoading;
    private TextView tvLoadingText;

    private String chatId;
    private boolean isGroupChat;
    
    private DatabaseReference chatRef;
    
    private Group currentGroup;
    private TriggerManager triggerManager;
    private MessageAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();

    private ImageView ivToolbarGroupImage;
    private TextView tvToolbarGroupName, tvToolbarGroupTag;
    
    private EmojiPopup emojiPopup;

    private MediaRecorder recorder;
    private MediaPlayer mediaPlayer;
    private String audioPath;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean isPreviewMode = false;
    private long startTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording && !isPaused) {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                tvRecordingTimer.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    private Uri photoUri;
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    uploadFile(photoUri, ChatMessage.TYPE_IMAGE);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        chatId = getIntent().getStringExtra("GROUP_ID");
        isGroupChat = getIntent().getBooleanExtra("IS_GROUP_CHAT", true);
        
        if (chatId == null) {
            finish();
            return;
        }

        triggerManager = new TriggerManager(this);
        
        FirebaseDatabase db = FirebaseDatabase.getInstance(DB_URL);
        if (isGroupChat) {
            chatRef = db.getReference("groups").child(chatId);
        } else {
            chatRef = db.getReference("chats").child(chatId);
        }

        setupToolbar();
        initViews();
        setupInputListeners();
        setupRecyclerView();
        listenForMessages();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (emojiPopup != null && emojiPopup.isShowing()) {
                    emojiPopup.dismiss();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        ivToolbarGroupImage = findViewById(R.id.ivToolbarGroupImage);
        tvToolbarGroupName = findViewById(R.id.tvToolbarGroupName);
        tvToolbarGroupTag = findViewById(R.id.tvToolbarGroupTag);
        LinearLayout layoutToolbarClickable = findViewById(R.id.layoutToolbarClickable);

        if (layoutToolbarClickable != null) {
            layoutToolbarClickable.setOnClickListener(v -> openSettings());
        }
    }

    private void openSettings() {
        if (isGroupChat) {
            Intent intent = new Intent(this, GroupSettingsActivity.class);
            intent.putExtra("GROUP_ID", chatId);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, PrivateChatSettingsActivity.class);
            intent.putExtra("CHAT_ID", chatId);
            startActivity(intent);
        }
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);

        ImageButton btnCamera = findViewById(R.id.btnCamera);
        ImageButton btnAttach = findViewById(R.id.btnAttach);
        ImageButton btnEmoji = findViewById(R.id.btnEmoji);
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);
        
        layoutMessageInput = findViewById(R.id.layout_message_input);
        layoutAudioRecording = findViewById(R.id.layout_audio_recording);
        tvRecordingTimer = findViewById(R.id.tvRecordingTimer);
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus);
        ImageButton btnDeleteRecording = findViewById(R.id.btnDeleteRecording);
        btnPlayPreview = findViewById(R.id.btnPlayPreview);
        ImageButton btnSendRecording = findViewById(R.id.btnSendRecording);
        
        View layoutUnknownActions = findViewById(R.id.layoutUnknownContactActions);
        if (layoutUnknownActions != null) layoutUnknownActions.setVisibility(View.GONE);

        View rootView = findViewById(R.id.rootView);
        emojiPopup = new EmojiPopup(rootView, etMessage);

        btnCamera.setOnClickListener(v -> openCamera());
        btnEmoji.setOnClickListener(v -> emojiPopup.toggle());
        
        btnAttach.setOnClickListener(v -> {
            View menu = findViewById(R.id.layoutAttachmentMenu);
            if (menu != null) {
                menu.setVisibility(menu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        
        btnDeleteRecording.setOnClickListener(v -> cancelRecording());
        btnPlayPreview.setOnClickListener(v -> playAudioPreview());
        btnSendRecording.setOnClickListener(v -> finishAndSendRecording());

        rvMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && !messageList.isEmpty()) {
                rvMessages.postDelayed(() -> rvMessages.smoothScrollToPosition(messageList.size() - 1), 100);
            }
        });
    }

    private void clearChat() {
        showGlassyConfirmDialog("Clear Chat", "Are you sure you want to clear your messages?", "Clear", () -> {
            if (chatRef != null) {
                chatRef.child("messages").removeValue().addOnSuccessListener(aVoid -> 
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteChat() {
        showGlassyConfirmDialog("Delete Chat", "Delete this chat history for you?", "Delete", () -> {
            if (chatRef != null) {
                chatRef.removeValue().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat_activity, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem blockItem = menu.findItem(R.id.action_block_chat);
        if (blockItem != null) blockItem.setVisible(!isGroupChat);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit_chat) { openSettings(); return true; }
        if (id == R.id.action_clear_chat) { clearChat(); return true; }
        if (id == R.id.action_delete_chat) { deleteChat(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInputListeners() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean empty = s.toString().trim().isEmpty();
                btnSend.setVisibility(empty ? View.GONE : View.VISIBLE);
                btnMic.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnSend.setOnClickListener(v -> sendMessage());
        
        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startAudioRecordingUI();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRecordingForPreview();
                return true;
            }
            return false;
        });
    }

    private void startAudioRecordingUI() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION);
            return;
        }
        isPreviewMode = false;
        layoutMessageInput.setVisibility(View.GONE);
        layoutAudioRecording.setVisibility(View.VISIBLE);
        tvRecordingStatus.setText("Recording...");
        tvRecordingStatus.setTextColor(0xFFD32F2F);
        btnPlayPreview.setVisibility(View.GONE);
        startRecording();
    }

    private void startRecording() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        audioPath = dir.getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".3gp";
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(audioPath);
        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            isPaused = false;
            startTime = System.currentTimeMillis();
            timerHandler.post(timerRunnable);
        } catch (IOException e) { Log.e(TAG, "Recording failed", e); }
    }

    private void stopRecordingForPreview() {
        if (isRecording) {
            stopRecordingState();
            isPreviewMode = true;
            tvRecordingStatus.setText("Preview");
            tvRecordingStatus.setTextColor(0xFF1A73E8);
            btnPlayPreview.setVisibility(View.VISIBLE);
        }
    }

    private void playAudioPreview() {
        if (audioPath == null) return;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            btnPlayPreview.setImageResource(android.R.drawable.ic_media_play);
            return;
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            btnPlayPreview.setImageResource(android.R.drawable.ic_media_pause);
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPreview.setImageResource(android.R.drawable.ic_media_play);
                mediaPlayer.release();
                mediaPlayer = null;
            });
        } catch (IOException e) {
            Log.e(TAG, "Playback failed", e);
        }
    }

    private void cancelRecording() {
        stopRecordingState();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (audioPath != null) {
            File file = new File(audioPath);
            if (file.exists()) file.delete();
        }
        resetRecordingUI();
    }

    private void finishAndSendRecording() {
        stopRecordingState();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (audioPath != null) uploadFile(Uri.fromFile(new File(audioPath)), ChatMessage.TYPE_AUDIO);
        resetRecordingUI();
    }

    private void stopRecordingState() {
        if (recorder != null) {
            try { if (isRecording) recorder.stop(); } catch (RuntimeException e) { Log.e(TAG, "Stop failed", e); }
            finally { recorder.release(); recorder = null; isRecording = false; timerHandler.removeCallbacks(timerRunnable); }
        }
    }

    private void resetRecordingUI() {
        layoutAudioRecording.setVisibility(View.GONE);
        layoutMessageInput.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(messageList, triggerManager.getUserPhone());
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);
    }

    private void listenForMessages() {
        if (chatRef == null) return;
        chatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed() || !snapshot.exists()) return;

                currentGroup = new Group();
                currentGroup.setId(snapshot.getKey());
                currentGroup.setName(snapshot.child("name").getValue(String.class));
                
                Boolean isGroup = snapshot.child("isGroupChat").getValue(Boolean.class);
                if (isGroup == null) {
                    isGroup = snapshot.child("groupChat").getValue(Boolean.class);
                }
                currentGroup.setGroupChat(isGroup != null ? isGroup : true);
                
                List<String> members = new ArrayList<>();
                for (DataSnapshot m : snapshot.child("memberPhones").getChildren()) {
                    String p = m.getValue(String.class); if (p != null) members.add(p);
                }
                currentGroup.setMemberPhones(members);

                updateToolbarUI();

                messageList.clear();
                String myPhone = triggerManager.getUserPhone();
                DataSnapshot msgs = snapshot.child("messages");
                if (msgs.exists()) {
                    for (DataSnapshot d : msgs.getChildren()) {
                        ChatMessage msg = d.getValue(ChatMessage.class);
                        if (msg != null) {
                            messageList.add(msg);
                            // Mark message as seen if it's from others
                            if (!TriggerManager.isSamePhone(msg.getSenderPhone(), myPhone) && msg.getStatus() < ChatMessage.STATUS_SEEN) {
                                d.getRef().child("status").setValue(ChatMessage.STATUS_SEEN);
                            }
                        }
                    }
                }
                adapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) rvMessages.scrollToPosition(messageList.size() - 1);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { 
                Log.e(TAG, "Sync error: " + error.getMessage());
            }
        });
    }

    private void updateToolbarUI() {
        if (currentGroup == null) return;
        
        String displayName = currentGroup.getName();
        
        if (!currentGroup.isGroupChat() && currentGroup.getMemberPhones() != null) {
            String myPhone = triggerManager.getUserPhone();
            for (String phone : currentGroup.getMemberPhones()) {
                if (!TriggerManager.isSamePhone(phone, myPhone)) {
                    String savedName = triggerManager.getContactName(phone);
                    displayName = (savedName != null && !savedName.isEmpty()) ? savedName : phone;
                    break;
                }
            }
        }
        
        tvToolbarGroupName.setText(displayName);
        
        if (currentGroup.isGroupChat()) {
            tvToolbarGroupTag.setVisibility(View.VISIBLE);
            tvToolbarGroupTag.setText("GROUP");
        } else {
            tvToolbarGroupTag.setVisibility(View.GONE);
        }
        
        if (adapter != null) adapter.setGroupInfo(displayName, currentGroup.isGroupChat());
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        doSendMessage(text, ChatMessage.TYPE_TEXT, null);
        etMessage.setText("");
    }

    private void doSendMessage(String text, String type, String fileUri) {
        if (chatRef == null) return;
        String msgKey = chatRef.child("messages").push().getKey();
        if (msgKey == null) return;

        ChatMessage msg = new ChatMessage(triggerManager.getUserPhone(), triggerManager.getUserName(), text, System.currentTimeMillis());
        msg.setType(type);
        msg.setFileUri(fileUri);

        chatRef.child("messages").child(msgKey).setValue(msg);
    }

    private void uploadFile(Uri uri, String type) {
        showLoading("Uploading...");
        StorageReference sr = FirebaseStorage.getInstance().getReference("chat_files").child(UUID.randomUUID().toString());
        sr.putFile(uri).addOnSuccessListener(ts -> sr.getDownloadUrl().addOnSuccessListener(dUri -> {
            doSendMessage("", type, dUri.toString()); hideLoading();
        })).addOnFailureListener(e -> { hideLoading(); Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show(); });
    }

    private void showLoading(String msg) {
        if (customLoading != null) { customLoading.setVisibility(View.VISIBLE); if (tvLoadingText != null) tvLoadingText.setText(msg); }
    }
    private void hideLoading() { if (customLoading != null) customLoading.setVisibility(View.GONE); }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION); return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File dir = getExternalFilesDir(null);
        if (dir != null) {
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", new File(dir, "IMG_" + System.currentTimeMillis() + ".jpg"));
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); cameraLauncher.launch(intent);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }
}
