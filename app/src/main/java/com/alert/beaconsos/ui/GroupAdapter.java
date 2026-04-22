package com.alert.beaconsos.ui;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Group;
import com.alert.beaconsos.data.TriggerManager;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private static final String TAG = "GroupAdapter";
    private List<Group> groups;
    private OnGroupClickListener listener;
    private String currentUserPhone;
    private TriggerManager triggerManager;

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
        void onGroupLongClick(Group group, View view);
    }

    public GroupAdapter(List<Group> groups, String currentUserPhone, OnGroupClickListener listener) {
        this.groups = new ArrayList<>(groups);
        this.currentUserPhone = currentUserPhone;
        this.listener = listener;
    }

    public void updateList(List<Group> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new GroupDiffCallback(this.groups, newList));
        this.groups.clear();
        this.groups.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (triggerManager == null) {
            triggerManager = new TriggerManager(parent.getContext());
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        final Group group = groups.get(position);
        
        String displayName = group.getName();
        
        if (!group.isGroupChat() && group.getMemberPhones() != null) {
            // For 1-on-1 chats, show the saved name or the phone number
            for (String phone : group.getMemberPhones()) {
                if (!TriggerManager.isSamePhone(phone, currentUserPhone)) {
                    String savedName = triggerManager.getContactName(phone);
                    displayName = (savedName != null && !savedName.isEmpty()) ? savedName : phone;
                    break;
                }
            }
        }
        
        holder.tvGroupName.setText(displayName);
        
        if (group.isGroupChat()) {
            holder.tvChatTypeTag.setVisibility(View.VISIBLE);
            holder.tvChatTypeTag.setText("GROUP");
            
            if (group.getMemberPhones() != null) {
                holder.tvMemberCount.setVisibility(View.VISIBLE);
                holder.tvMemberCount.setText(group.getMemberPhones().size() + " Members");
            } else {
                holder.tvMemberCount.setVisibility(View.GONE);
            }
        } else {
            holder.tvMemberCount.setVisibility(View.GONE);
            holder.tvChatTypeTag.setVisibility(View.GONE);
        }

        if (group.getUnreadCount() > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(String.valueOf(group.getUnreadCount()));
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }

        Glide.with(holder.itemView.getContext())
                .load(group.getGroupImageUri() != null && !group.getGroupImageUri().isEmpty() ? Uri.parse(group.getGroupImageUri()) : null)
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .circleCrop()
                .into(holder.ivGroupIcon);
        
        // Use the root itemView for click listener to ensure the whole card is clickable
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                Log.d(TAG, "Item clicked: " + group.getName() + " (ID: " + group.getId() + ")");
                listener.onGroupClick(group);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onGroupLongClick(group, v);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return groups != null ? groups.size() : 0;
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupName, tvMemberCount, tvChatTypeTag, tvUnreadCount;
        ImageView ivGroupIcon;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvChatTypeTag = itemView.findViewById(R.id.tvChatTypeTag);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            ivGroupIcon = itemView.findViewById(R.id.ivGroupIcon);
            
            // Disable clicks on children to let the parent handle them
            tvGroupName.setClickable(false);
            tvMemberCount.setClickable(false);
            tvChatTypeTag.setClickable(false);
            ivGroupIcon.setClickable(false);
        }
    }

    private static class GroupDiffCallback extends DiffUtil.Callback {
        private final List<Group> oldList;
        private final List<Group> newList;

        public GroupDiffCallback(List<Group> oldList, List<Group> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldList.get(oldItemPosition).getId(), newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Group oldGroup = oldList.get(oldItemPosition);
            Group newGroup = newList.get(newItemPosition);
            return Objects.equals(oldGroup.getName(), newGroup.getName()) &&
                    Objects.equals(oldGroup.getGroupImageUri(), newGroup.getGroupImageUri()) &&
                    oldGroup.isGroupChat() == newGroup.isGroupChat() &&
                    oldGroup.getMemberPhones().size() == newGroup.getMemberPhones().size() &&
                    oldGroup.getUnreadCount() == newGroup.getUnreadCount();
        }
    }
}
