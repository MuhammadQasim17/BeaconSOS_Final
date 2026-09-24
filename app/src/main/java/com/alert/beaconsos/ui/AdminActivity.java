package com.alert.beaconsos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.data.User;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends BaseActivity {

    private RecyclerView rvUsers;
    private Button btnLogoutAdmin;
    private TriggerManager triggerManager;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private UserAdapter adapter;
    private List<User> userList;
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // Verify user role directly from Firebase BEFORE calling setContentView or initializing Admin UI
        verifyAdminRoleAndInit(currentUser.getUid());
    }

    private void verifyAdminRoleAndInit(String uid) {
        DatabaseReference roleRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("role");
        roleRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String role = snapshot.getValue(String.class);
                if (role != null) {
                    checkRoleAndProceed(role);
                } else {
                    // Fallback check against Firestore
                    FirebaseFirestore.getInstance().collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                String fsRole = doc.getString("role");
                                checkRoleAndProceed(fsRole);
                            })
                            .addOnFailureListener(e -> denyAccess());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                denyAccess();
            }
        });
    }

    private void checkRoleAndProceed(String role) {
        if (role != null && role.equalsIgnoreCase("admin")) {
            initAdminUi();
        } else {
            denyAccess();
        }
    }

    private void denyAccess() {
        Toast.makeText(this, "Access Denied: Admin privileges required.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void initAdminUi() {
        setContentView(R.layout.activity_admin);

        triggerManager = new TriggerManager(this);
        mDatabase = FirebaseDatabase.getInstance().getReference();

        rvUsers = findViewById(R.id.rvUsers);
        btnLogoutAdmin = findViewById(R.id.btnLogoutAdmin);
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        userList = new ArrayList<>();
        adapter = new UserAdapter(userList);
        rvUsers.setAdapter(adapter);

        fetchUsersFromFirebase();

        btnLogoutAdmin.setOnClickListener(v -> {
            mAuth.signOut();
            triggerManager.clearData();
            redirectToLogin();
        });
    }

    private void showLoading(String message) {
        if (customLoading != null) {
            customLoading.setVisibility(View.VISIBLE);
            if (tvLoadingText != null) tvLoadingText.setText(message.toUpperCase());
            LoadingUtils.startCustomAnimation(customLoading);
        }
    }

    private void hideLoading() {
        if (customLoading != null) {
            customLoading.setVisibility(View.GONE);
            LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    private void fetchUsersFromFirebase() {
        showLoading("Loading Users List");
        mDatabase.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hideLoading();
                userList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    User user = postSnapshot.getValue(User.class);
                    if (user != null) {
                        userList.add(user);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                Toast.makeText(AdminActivity.this, "Failed to load users.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private List<User> users;

        public UserAdapter(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            User user = users.get(position);
            holder.tvName.setText(user.getName());
            holder.tvEmail.setText(user.getEmail());
            holder.tvRole.setText("Role: " + user.getRole());

            holder.btnDelete.setOnClickListener(v -> {
                showGlassyConfirmDialog(
                        "Delete User",
                        "Are you sure you want to delete " + user.getName() + "?",
                        "Delete",
                        () -> {
                            if (user.getId() != null) {
                                showLoading("Deleting User");
                                mDatabase.child("users").child(user.getId()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            hideLoading();
                                            Toast.makeText(AdminActivity.this, "User deleted", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(e -> {
                                            hideLoading();
                                            Toast.makeText(AdminActivity.this, "Failed to delete user", Toast.LENGTH_SHORT).show();
                                        });
                            } else {
                                Toast.makeText(AdminActivity.this, "Cannot delete user: ID is null", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvEmail, tvRole;
            ImageButton btnDelete;

            public UserViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvUserName);
                tvEmail = itemView.findViewById(R.id.tvUserEmail);
                tvRole = itemView.findViewById(R.id.tvUserRole);
                btnDelete = itemView.findViewById(R.id.btnDeleteUser);
            }
        }
    }
}
