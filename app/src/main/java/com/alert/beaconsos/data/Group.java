package com.alert.beaconsos.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Group {
    private String id;
    private String name;
    private String description;
    private String groupImageUri;
    private String adminPhone; // Phone number of the group creator/admin
    private List<String> memberPhones;
    private List<ChatMessage> messages;
    private boolean isGroupChat; // Flag to distinguish group from individual chat
    private int unreadCount;

    // Required empty constructor for Firebase
    public Group() {
        this.memberPhones = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.isGroupChat = true; // Default to true
        this.unreadCount = 0;
    }

    public Group(String name, String adminPhone) {
        this();
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.adminPhone = adminPhone;
        this.description = "";
        addMember(adminPhone); // Admin is always a member
    }

    public Group(String name, String adminPhone, boolean isGroupChat) {
        this(name, adminPhone);
        this.isGroupChat = isGroupChat;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getGroupImageUri() { return groupImageUri; }
    public void setGroupImageUri(String groupImageUri) { this.groupImageUri = groupImageUri; }

    public String getAdminPhone() { return adminPhone; }
    public void setAdminPhone(String adminPhone) { this.adminPhone = adminPhone; }

    public List<String> getMemberPhones() { 
        if (memberPhones == null) memberPhones = new ArrayList<>();
        return memberPhones; 
    }
    public void setMemberPhones(List<String> memberPhones) { this.memberPhones = memberPhones; }
    
    public List<ChatMessage> getMessages() { 
        if (messages == null) messages = new ArrayList<>();
        return messages; 
    }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public boolean isGroupChat() { return isGroupChat; }
    public void setGroupChat(boolean groupChat) { isGroupChat = groupChat; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    
    public void addMember(String phone) {
        if (!getMemberPhones().contains(phone)) {
            getMemberPhones().add(phone);
        }
    }

    public void removeMember(String phone) {
        getMemberPhones().remove(phone);
    }

    public void addMessage(ChatMessage message) {
        getMessages().add(message);
    }
}
