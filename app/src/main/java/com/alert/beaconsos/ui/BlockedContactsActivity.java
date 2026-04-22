package com.alert.beaconsos.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BlockedContactsActivity extends BaseActivity {

    private RecyclerView rvBlocked;
    private TextView tvNoBlocked;
    private TriggerManager triggerManager;
    private BlockedAdapter adapter;
    private List<String> blockedList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_contacts);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbarBlocked);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Blocked Contacts");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvBlocked = findViewById(R.id.rvBlockedContacts);
        tvNoBlocked = findViewById(R.id.tvNoBlocked);

        rvBlocked.setLayoutManager(new LinearLayoutManager(this));
        loadBlockedContacts();
    }

    private void loadBlockedContacts() {
        Set<String> blockedSet = triggerManager.getBlockedPhones();
        blockedList.clear();
        blockedList.addAll(blockedSet);

        if (blockedList.isEmpty()) {
            tvNoBlocked.setVisibility(View.VISIBLE);
            rvBlocked.setVisibility(View.GONE);
        } else {
            tvNoBlocked.setVisibility(View.GONE);
            rvBlocked.setVisibility(View.VISIBLE);
            adapter = new BlockedAdapter(blockedList);
            rvBlocked.setAdapter(adapter);
        }
    }

    private void unblockContact(String phone) {
        showGlassyConfirmDialog(
                "Unblock Contact",
                "Do you want to unblock " + phone + "?",
                "Unblock",
                () -> {
                    triggerManager.unblockPhone(phone);
                    Toast.makeText(this, "Contact unblocked", Toast.LENGTH_SHORT).show();
                    loadBlockedContacts();
                }
        );
    }

    private class BlockedAdapter extends RecyclerView.Adapter<BlockedAdapter.ViewHolder> {
        private final List<String> phones;

        BlockedAdapter(List<String> phones) {
            this.phones = phones;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_contact, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String phone = phones.get(position);
            String name = triggerManager.getContactName(phone);
            
            holder.tvPhone.setText(phone);
            if (name != null) {
                holder.tvName.setText(name);
            } else {
                holder.tvName.setText("Unknown Contact");
            }

            holder.btnUnblock.setOnClickListener(v -> unblockContact(phone));
        }

        @Override
        public int getItemCount() {
            return phones.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone;
            Button btnUnblock;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvBlockedName);
                tvPhone = itemView.findViewById(R.id.tvBlockedPhone);
                btnUnblock = itemView.findViewById(R.id.btnUnblock);
            }
        }
    }
}
