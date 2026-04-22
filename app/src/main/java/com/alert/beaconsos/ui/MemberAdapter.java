package com.alert.beaconsos.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private List<String> members;
    private boolean isUserAdmin;
    private String currentUserPhone;
    private String groupAdminPhone;
    private OnMemberRemoveListener listener;

    public interface OnMemberRemoveListener {
        void onRemove(String member, int position);
    }

    public MemberAdapter(List<String> members, boolean isUserAdmin, String currentUserPhone, String groupAdminPhone, OnMemberRemoveListener listener) {
        this.members = members;
        this.isUserAdmin = isUserAdmin;
        this.currentUserPhone = currentUserPhone;
        this.groupAdminPhone = groupAdminPhone;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        String memberPhone = members.get(position);
        
        StringBuilder displayText = new StringBuilder(memberPhone);
        
        if (memberPhone.equals(groupAdminPhone)) {
            displayText.append(" (Admin)");
        }
        
        if (memberPhone.equals(currentUserPhone)) {
            displayText.append(" (You)");
        }
        
        holder.tvMemberInfo.setText(displayText.toString());

        // Only Admin can remove members, and Admin cannot remove themselves from here (they use Exit)
        if (isUserAdmin && !memberPhone.equals(currentUserPhone)) {
            holder.btnRemoveMember.setVisibility(View.VISIBLE);
            holder.btnRemoveMember.setOnClickListener(v -> listener.onRemove(memberPhone, position));
        } else {
            holder.btnRemoveMember.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemberInfo;
        ImageButton btnRemoveMember;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberInfo = itemView.findViewById(R.id.tvMemberInfo);
            btnRemoveMember = itemView.findViewById(R.id.btnRemoveMember);
        }
    }
}
