package com.alert.beaconsos.ui;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.ChatMessage;
import com.bumptech.glide.Glide;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT_TEXT = 1;
    private static final int VIEW_TYPE_RECEIVED_TEXT = 2;
    private static final int VIEW_TYPE_SENT_IMAGE = 3;
    private static final int VIEW_TYPE_RECEIVED_IMAGE = 4;
    private static final int VIEW_TYPE_SENT_AUDIO = 5;
    private static final int VIEW_TYPE_RECEIVED_AUDIO = 6;
    private static final int VIEW_TYPE_SYSTEM = 7;

    private final List<ChatMessage> messages;
    private final String currentUserPhone;
    private String otherMemberName;
    private boolean isGroupChat = true;
    
    private MediaPlayer mediaPlayer;
    private String currentlyPlayingUrl = null;
    private AudioViewHolder currentPlayingViewHolder = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSeekBarTask = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && currentPlayingViewHolder != null) {
                currentPlayingViewHolder.audioSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                handler.postDelayed(this, 100);
            }
        }
    };

    public MessageAdapter(List<ChatMessage> messages, String currentUserPhone) {
        this.messages = messages;
        this.currentUserPhone = currentUserPhone;
    }

    public void setGroupInfo(String name, boolean isGroupChat) {
        this.otherMemberName = name;
        this.isGroupChat = isGroupChat;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        
        if (ChatMessage.TYPE_SYSTEM.equals(message.getType())) {
            return VIEW_TYPE_SYSTEM;
        }

        boolean isSent = message.getSenderPhone() != null && message.getSenderPhone().equals(currentUserPhone);
        String type = message.getType() != null ? message.getType() : ChatMessage.TYPE_TEXT;

        if (isSent) {
            switch (type) {
                case ChatMessage.TYPE_IMAGE: return VIEW_TYPE_SENT_IMAGE;
                case ChatMessage.TYPE_AUDIO: return VIEW_TYPE_SENT_AUDIO;
                default: return VIEW_TYPE_SENT_TEXT;
            }
        } else {
            switch (type) {
                case ChatMessage.TYPE_IMAGE: return VIEW_TYPE_RECEIVED_IMAGE;
                case ChatMessage.TYPE_AUDIO: return VIEW_TYPE_RECEIVED_AUDIO;
                default: return VIEW_TYPE_RECEIVED_TEXT;
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_SYSTEM:
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false));
            case VIEW_TYPE_SENT_IMAGE:
                return new SentImageViewHolder(inflater.inflate(R.layout.item_message_sent_image, parent, false));
            case VIEW_TYPE_RECEIVED_IMAGE:
                return new ReceivedImageViewHolder(inflater.inflate(R.layout.item_message_received_image, parent, false));
            case VIEW_TYPE_SENT_AUDIO:
                return new SentAudioViewHolder(inflater.inflate(R.layout.item_message_sent_audio, parent, false));
            case VIEW_TYPE_RECEIVED_AUDIO:
                return new ReceivedAudioViewHolder(inflater.inflate(R.layout.item_message_received_audio, parent, false));
            case VIEW_TYPE_SENT_TEXT:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            default:
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof SystemMessageViewHolder) ((SystemMessageViewHolder) holder).bind(message);
        else if (holder instanceof SentMessageViewHolder) ((SentMessageViewHolder) holder).bind(message);
        else if (holder instanceof ReceivedMessageViewHolder) ((ReceivedMessageViewHolder) holder).bind(message, isGroupChat, otherMemberName);
        else if (holder instanceof SentImageViewHolder) ((SentImageViewHolder) holder).bind(message);
        else if (holder instanceof ReceivedImageViewHolder) ((ReceivedImageViewHolder) holder).bind(message, isGroupChat, otherMemberName);
        else if (holder instanceof AudioViewHolder) ((AudioViewHolder) holder).bind(message, this);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void playAudio(String audioUrl, AudioViewHolder holder) {
        if (mediaPlayer != null) {
            if (audioUrl.equals(currentlyPlayingUrl)) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
                    handler.removeCallbacks(updateSeekBarTask);
                } else {
                    mediaPlayer.start();
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                    handler.post(updateSeekBarTask);
                }
                return;
            } else {
                stopAudio();
            }
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
            currentlyPlayingUrl = audioUrl;
            currentPlayingViewHolder = holder;
            
            mediaPlayer.setOnPreparedListener(mp -> {
                holder.audioSeekBar.setMax(mp.getDuration());
                mp.start();
                holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                handler.post(updateSeekBarTask);
            });

            mediaPlayer.setOnCompletionListener(mp -> stopAudio());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (currentPlayingViewHolder != null) {
            currentPlayingViewHolder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
            currentPlayingViewHolder.audioSeekBar.setProgress(0);
        }
        currentlyPlayingUrl = null;
        currentPlayingViewHolder = null;
        handler.removeCallbacks(updateSeekBarTask);
    }

    public void releasePlayer() {
        stopAudio();
    }

    protected static void setStatusIcon(ImageView ivStatus, int status) {
        if (ivStatus == null) return;
        
        switch (status) {
            case ChatMessage.STATUS_SENT:
                ivStatus.setImageResource(R.drawable.ic_tick_single);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_gray));
                break;
            case ChatMessage.STATUS_DELIVERED:
                ivStatus.setImageResource(R.drawable.ic_tick_double);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_gray));
                break;
            case ChatMessage.STATUS_SEEN:
                ivStatus.setImageResource(R.drawable.ic_tick_double);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_blue));
                break;
            default:
                ivStatus.setVisibility(View.GONE);
                return;
        }
        ivStatus.setVisibility(View.VISIBLE);
    }

    protected static void makeLinksClickable(TextView textView, String message) {
        SpannableString spannableString = new SpannableString(message);
        Matcher matcher = Patterns.WEB_URL.matcher(message);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int start = matcher.start();
            int end = matcher.end();
            String url = message.substring(start, end);
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Uri uri = Uri.parse(url.startsWith("http") ? url : "http://" + url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    widget.getContext().startActivity(intent);
                }
            };
            spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (found) {
            textView.setText(spannableString);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            textView.setText(message);
        }
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSystemMessage;
        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSystemMessage = itemView.findViewById(R.id.tvSystemMessage);
        }
        void bind(ChatMessage message) {
            tvSystemMessage.setText(message.getMessage());
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp;
        ImageView ivStatus;
        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
        }
        void bind(ChatMessage message) {
            makeLinksClickable(tvMessage, message.getMessage());
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvMessage, tvTimestamp;
        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
        void bind(ChatMessage message, boolean isGroupChat, String otherName) {
            if (isGroupChat) {
                tvSenderName.setText(message.getSenderName());
            } else {
                tvSenderName.setText(otherName != null ? otherName : message.getSenderName());
            }
            makeLinksClickable(tvMessage, message.getMessage());
            tvTimestamp.setText(formatTime(message.getTimestamp()));
        }
    }

    static class SentImageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMessage;
        TextView tvTimestamp;
        ImageView ivStatus;
        SentImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMessage = itemView.findViewById(R.id.ivMessageImage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
        }
        void bind(ChatMessage message) {
            Glide.with(itemView.getContext()).load(message.getFileUri()).into(ivMessage);
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
        }
    }

    static class ReceivedImageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName;
        ImageView ivMessage;
        TextView tvTimestamp;
        ReceivedImageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            ivMessage = itemView.findViewById(R.id.ivMessageImage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
        void bind(ChatMessage message, boolean isGroupChat, String otherName) {
            if (isGroupChat) {
                tvSenderName.setText(message.getSenderName());
            } else {
                tvSenderName.setText(otherName != null ? otherName : message.getSenderName());
            }
            Glide.with(itemView.getContext()).load(message.getFileUri()).into(ivMessage);
            tvTimestamp.setText(formatTime(message.getTimestamp()));
        }
    }

    abstract static class AudioViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnPlayAudio;
        SeekBar audioSeekBar;
        TextView tvTimestamp;
        ImageView ivStatus;

        AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
            audioSeekBar = itemView.findViewById(R.id.audioSeekBar);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
        }

        void bind(ChatMessage message, MessageAdapter adapter) {
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
            
            if (message.getFileUri() != null && message.getFileUri().equals(adapter.currentlyPlayingUrl)) {
                adapter.currentPlayingViewHolder = this;
                btnPlayAudio.setImageResource(adapter.mediaPlayer != null && adapter.mediaPlayer.isPlaying() ? 
                        android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                if (adapter.mediaPlayer != null) {
                    audioSeekBar.setMax(adapter.mediaPlayer.getDuration());
                    audioSeekBar.setProgress(adapter.mediaPlayer.getCurrentPosition());
                }
            } else {
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
                audioSeekBar.setProgress(0);
            }

            btnPlayAudio.setOnClickListener(v -> {
                if (message.getFileUri() != null) {
                    adapter.playAudio(message.getFileUri(), this);
                }
            });

            audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && message.getFileUri().equals(adapter.currentlyPlayingUrl) && adapter.mediaPlayer != null) {
                        adapter.mediaPlayer.seekTo(progress);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    static class SentAudioViewHolder extends AudioViewHolder {
        SentAudioViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class ReceivedAudioViewHolder extends AudioViewHolder {
        TextView tvSenderName;
        ReceivedAudioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
        }
        @Override
        void bind(ChatMessage message, MessageAdapter adapter) {
            super.bind(message, adapter);
            if (tvSenderName != null) {
                if (adapter.isGroupChat) {
                    tvSenderName.setText(message.getSenderName());
                } else {
                    tvSenderName.setText(adapter.otherMemberName != null ? adapter.otherMemberName : message.getSenderName());
                }
            }
        }
    }

    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
