package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.SosHistoryEntry;
import com.alert.beaconsos.data.TriggerManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SosHistoryFragment extends Fragment {

    private RecyclerView rvSosHistory;
    private TextView tvEmptyHistory, tvClearHistory;
    private TriggerManager triggerManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sos_history, container, false);

        triggerManager = new TriggerManager(requireContext());
        rvSosHistory = view.findViewById(R.id.rvSosHistory);
        tvEmptyHistory = view.findViewById(R.id.tvEmptyHistory);
        tvClearHistory = view.findViewById(R.id.tvClearHistory);

        rvSosHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        
        tvClearHistory.setOnClickListener(v -> {
            if (!triggerManager.getSosHistory().isEmpty()) {
                triggerManager.clearSosHistory();
                loadHistory();
            }
        });

        loadHistory();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncHistoryFromCloud();
        loadHistory();
    }

    private void syncHistoryFromCloud() {
        if (triggerManager == null) return;
        String uid = triggerManager.getUserId();
        if (uid == null || uid.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).collection("sos_history")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        List<SosHistoryEntry> history = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            SosHistoryEntry entry = doc.toObject(SosHistoryEntry.class);
                            if (entry != null) history.add(entry);
                        }
                        triggerManager.saveSosHistory(history);
                        loadHistory();
                    }
                });
    }

    private void loadHistory() {
        List<SosHistoryEntry> history = triggerManager.getSosHistory();
        View layoutEmptyHistory = getView() != null ? getView().findViewById(R.id.layoutEmptyHistory) : null;
        if (history.isEmpty()) {
            if (layoutEmptyHistory != null) layoutEmptyHistory.setVisibility(View.VISIBLE);
            if (tvEmptyHistory != null) tvEmptyHistory.setVisibility(View.GONE);
            rvSosHistory.setVisibility(View.GONE);
            tvClearHistory.setVisibility(View.GONE);
        } else {
            if (layoutEmptyHistory != null) layoutEmptyHistory.setVisibility(View.GONE);
            if (tvEmptyHistory != null) tvEmptyHistory.setVisibility(View.GONE);
            rvSosHistory.setVisibility(View.VISIBLE);
            tvClearHistory.setVisibility(View.VISIBLE);
            rvSosHistory.setAdapter(new HistoryAdapter(history, triggerManager, this::loadHistory));
        }
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<SosHistoryEntry> history;
        private final TriggerManager triggerManager;
        private final Runnable onDeleted;

        HistoryAdapter(List<SosHistoryEntry> history, TriggerManager triggerManager, Runnable onDeleted) { 
            this.history = history; 
            this.triggerManager = triggerManager;
            this.onDeleted = onDeleted;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SosHistoryEntry entry = history.get(position);
            holder.tvType.setText(entry.getTriggerType());
            holder.tvTime.setText(new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date(entry.getTimestamp())));
            holder.tvMessage.setText(entry.getMessage());
            holder.tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
            if (entry.getContactNames() != null && !entry.getContactNames().isEmpty()) {
                holder.tvContacts.setText("Sent to: " + TextUtils.join(", ", entry.getContactNames()));
            } else {
                holder.tvContacts.setText("Sent to: Unknown Recipients");
            }

            holder.btnDelete.setOnClickListener(v -> {
                triggerManager.removeSosHistoryEntry(entry.getId());
                onDeleted.run();
            });
        }

        @Override
        public int getItemCount() { return history.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvTime, tvContacts, tvMessage;
            View btnDelete;
            ViewHolder(View v) {
                super(v);
                tvType = v.findViewById(R.id.tvHistoryType);
                tvTime = v.findViewById(R.id.tvHistoryTime);
                tvContacts = v.findViewById(R.id.tvHistoryContacts);
                tvMessage = v.findViewById(R.id.tvHistoryMessage);
                btnDelete = v.findViewById(R.id.btnDeleteHistory);
            }
        }
    }
}
