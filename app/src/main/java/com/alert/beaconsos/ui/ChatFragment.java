package com.alert.beaconsos.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.ChatMessage;
import com.alert.beaconsos.data.Group;
import com.alert.beaconsos.data.TriggerManager;
import com.alert.beaconsos.utils.LoadingUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment implements GroupAdapter.OnGroupClickListener {

    private static final String TAG = "ChatFragment";
    private static final String DB_URL = "https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/";
    
    private RecyclerView rvChatGroups;
    private View customLoading;
    private TriggerManager triggerManager;
    private GroupAdapter adapter;
    
    private DatabaseReference groupsRef;
    private DatabaseReference privateChatsRef;
    
    private final List<Group> groupChatsList = new ArrayList<>();
    private final List<Group> filteredList = new ArrayList<>();
    
    private ValueEventListener groupsListener;
    private ValueEventListener privateChatsListener;

    private MaterialCardView searchCard;
    private EditText etSearchGroups;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        triggerManager = new TriggerManager(requireContext());
        
        FirebaseDatabase db = FirebaseDatabase.getInstance(DB_URL);
        groupsRef = db.getReference("groups");
        privateChatsRef = db.getReference("chats");

        rvChatGroups = view.findViewById(R.id.rvChatGroups);
        FloatingActionButton fabNewGroup = view.findViewById(R.id.fabNewGroup);
        customLoading = view.findViewById(R.id.customLoading);
        searchCard = view.findViewById(R.id.searchCard);
        etSearchGroups = view.findViewById(R.id.etSearchGroups);
        ImageButton btnChatMenu = view.findViewById(R.id.btnChatMenu);
        ImageButton btnCloseSearch = view.findViewById(R.id.btnCloseSearch);
        
        setupRecyclerView();
        listenForChats();

        if (etSearchGroups != null) {
            etSearchGroups.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnCloseSearch != null) btnCloseSearch.setOnClickListener(v -> hideSearchBar());
        
        if (fabNewGroup != null) {
            fabNewGroup.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), NewIndividualChatActivity.class);
                startActivity(intent);
            });
        }
        
        if (btnChatMenu != null) btnChatMenu.setOnClickListener(this::showPopupMenu);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocalNames();
        updateUI();
    }

    private void refreshLocalNames() {
        String userPhone = triggerManager.getUserPhone();
        for (Group group : groupChatsList) {
            applyLocalName(group, userPhone);
        }
    }

    private void setupRecyclerView() {
        adapter = new GroupAdapter(filteredList, triggerManager.getUserPhone(), this);
        rvChatGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChatGroups.setAdapter(adapter);
    }

    private void listenForChats() {
        showLoading(true);
        String userPhone = triggerManager.getUserPhone();

        groupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                updateCombinedList(snapshot, true, userPhone);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { 
                showLoading(false);
                Log.e(TAG, "Groups sync error: " + error.getMessage());
            }
        };

        privateChatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                updateCombinedList(snapshot, false, userPhone);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { 
                Log.e(TAG, "Private chats sync error: " + error.getMessage());
            }
        };

        groupsRef.addValueEventListener(groupsListener);
        privateChatsRef.addValueEventListener(privateChatsListener);
    }

    private synchronized void updateCombinedList(DataSnapshot snapshot, boolean isGroupNode, String userPhone) {
        if (isGroupNode) {
            groupChatsList.removeIf(Group::isGroupChat);
            if (snapshot.exists()) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    Group g = parseGroup(s);
                    if (g != null && g.isGroupChat() && isUserMember(g, userPhone)) {
                        calculateUnreadCount(s, g, userPhone);
                        groupChatsList.add(g);
                    }
                }
            }
        } else {
            groupChatsList.removeIf(g -> !g.isGroupChat());
            if (snapshot.exists()) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    Group g = parseGroup(s);
                    if (g != null && !g.isGroupChat() && isUserMember(g, userPhone)) {
                        applyLocalName(g, userPhone);
                        calculateUnreadCount(s, g, userPhone);
                        groupChatsList.add(g);
                    }
                }
            }
        }
        updateUI();
    }

    private void calculateUnreadCount(DataSnapshot snapshot, Group group, String userPhone) {
        int count = 0;
        DataSnapshot messagesSnap = snapshot.child("messages");
        if (messagesSnap.exists()) {
            for (DataSnapshot msgSnap : messagesSnap.getChildren()) {
                ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                if (msg != null && !TriggerManager.isSamePhone(msg.getSenderPhone(), userPhone)) {
                    // Check if message is unseen (status < STATUS_SEEN)
                    if (msg.getStatus() < ChatMessage.STATUS_SEEN) {
                        count++;
                    }
                }
            }
        }
        group.setUnreadCount(count);
    }

    private void applyLocalName(Group group, String userPhone) {
        if (group == null || group.isGroupChat()) return;
        
        List<String> members = group.getMemberPhones();
        if (members != null) {
            for (String phone : members) {
                if (!TriggerManager.isSamePhone(phone, userPhone)) {
                    String localName = triggerManager.getContactName(phone);
                    if (localName != null && !localName.isEmpty()) {
                        group.setName(localName);
                    } else {
                        // If no local name saved, fallback to phone number
                        group.setName(phone);
                    }
                    break;
                }
            }
        }
    }

    private void updateUI() {
        showLoading(false);
        filter(etSearchGroups != null ? etSearchGroups.getText().toString() : "");
    }

    private Group parseGroup(DataSnapshot snapshot) {
        try {
            Group group = new Group();
            group.setId(snapshot.getKey());
            
            // For private chats, 'name' might be null in DB, we rely on local mapping
            group.setName(snapshot.child("name").getValue(String.class));
            group.setAdminPhone(snapshot.child("adminPhone").getValue(String.class));
            
            Boolean isGroup = snapshot.child("isGroupChat").getValue(Boolean.class);
            if (isGroup == null) {
                // Fallback for older structure
                isGroup = snapshot.child("groupChat").getValue(Boolean.class);
            }
            group.setGroupChat(isGroup != null ? isGroup : true);
            
            List<String> members = new ArrayList<>();
            DataSnapshot membersSnap = snapshot.child("memberPhones");
            if (membersSnap.exists()) {
                for (DataSnapshot member : membersSnap.getChildren()) {
                    String phone = member.getValue(String.class);
                    if (phone != null) members.add(phone);
                }
            }
            group.setMemberPhones(members);
            return group;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUserMember(Group group, String userPhone) {
        if (group.getMemberPhones() == null || userPhone == null) return false;
        for (String phone : group.getMemberPhones()) {
            if (TriggerManager.isSamePhone(phone, userPhone)) return true;
        }
        return false;
    }

    private void filter(String text) {
        filteredList.clear();
        String query = text.toLowerCase().trim();
        if (query.isEmpty()) {
            filteredList.addAll(groupChatsList);
        } else {
            for (Group group : groupChatsList) {
                if (group.getName() != null && group.getName().toLowerCase().contains(query)) {
                    filteredList.add(group);
                }
            }
        }
        if (adapter != null) adapter.updateList(filteredList);
    }

    private void showLoading(boolean show) {
        if (customLoading != null) {
            customLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) LoadingUtils.startCustomAnimation(customLoading);
            else LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    @Override
    public void onGroupClick(Group group) {
        Intent intent = new Intent(getActivity(), GroupChatActivity.class);
        intent.putExtra("GROUP_ID", group.getId());
        intent.putExtra("IS_GROUP_CHAT", group.isGroupChat());
        startActivity(intent);
    }

    private void showSearchBar() {
        if (searchCard != null) {
            searchCard.setVisibility(View.VISIBLE);
            etSearchGroups.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearchGroups, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSearchBar() {
        if (searchCard != null) {
            searchCard.setVisibility(View.GONE);
            etSearchGroups.setText("");
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchGroups.getWindowToken(), 0);
        }
    }

    private void showPopupMenu(View view) {
        if (getContext() == null) return;
        ContextThemeWrapper wrapper = new ContextThemeWrapper(getContext(), R.style.GlassyPopupTheme);
        PopupMenu popup = new PopupMenu(wrapper, view);
        popup.getMenuInflater().inflate(R.menu.menu_chat_fragment, popup.getMenu());
        
        popup.getMenu().removeItem(R.id.action_delete_all);
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_search) { showSearchBar(); return true; }
            if (itemId == R.id.action_create_group) { showCreateGroupDialog(); return true; }
            if (itemId == R.id.action_blocked_contacts) {
                startActivity(new Intent(getActivity(), BlockedContactsActivity.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showCreateGroupDialog() {
        if (getActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.GlassyDialog));
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_group, null);
        builder.setView(dialogView);
        EditText etGroupNameInput = dialogView.findViewById(R.id.etGroupNameInput);
        Button btnCreate = dialogView.findViewById(R.id.btnCreateGroupAction);
        AlertDialog dialog = builder.create();
        btnCreate.setOnClickListener(v -> {
            String name = etGroupNameInput.getText().toString().trim();
            if (!name.isEmpty()) { createGroup(name); dialog.dismiss(); }
            else { Toast.makeText(getContext(), "Enter group name", Toast.LENGTH_SHORT).show(); }
        });
        dialog.show();
    }

    private void createGroup(String groupName) {
        String adminPhone = triggerManager.getUserPhone();
        showLoading(true);
        DatabaseReference newGroupRef = groupsRef.push();
        Group group = new Group(groupName, adminPhone);
        group.setId(newGroupRef.getKey());
        newGroupRef.setValue(group).addOnCompleteListener(task -> {
            showLoading(false);
            if (task.isSuccessful()) {
                Intent intent = new Intent(getActivity(), GroupChatActivity.class);
                intent.putExtra("GROUP_ID", group.getId());
                intent.putExtra("IS_GROUP_CHAT", true);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Failed to create group", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public void onGroupLongClick(Group group, View view) {}
    @Override public void onDestroyView() {
        super.onDestroyView();
        if (groupsRef != null && groupsListener != null) groupsRef.removeEventListener(groupsListener);
        if (privateChatsRef != null && privateChatsListener != null) privateChatsRef.removeEventListener(privateChatsListener);
    }
}
