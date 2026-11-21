package com.alert.beaconsos.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriggerManager {
    private static final String PREF_NAME = "BeaconSOSPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_TRIGGER_WORD = "trigger_word";
    private static final String KEY_EMERGENCY_CONTACTS = "emergency_contacts_list";
    private static final String KEY_SOS_RECIPIENTS = "sos_recipients_list";
    private static final String KEY_CHAT_CONTACTS = "chat_contacts_map";
    private static final String KEY_SELECTED_CONTACT_IDS = "selected_contact_ids";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_EMERGENCY_MESSAGE = "emergency_message";
    private static final String KEY_PROFILE_IMAGE_URI = "profile_image_uri";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: System, 1: Light, 2: Dark
    private static final String KEY_BLOCKED_PHONES = "blocked_phones";
    private static final String KEY_SOS_HISTORY = "sos_history";
    
    // Feature Keys
    private static final String KEY_SHAKE_ENABLED = "shake_trigger_enabled";

    // Permission Keys
    private static final String KEY_PERM_MIC = "perm_mic_enabled";
    private static final String KEY_PERM_LOC = "perm_loc_enabled";
    private static final String KEY_PERM_SMS = "perm_sms_enabled";
    private static final String KEY_PERM_NOTIF = "perm_notif_enabled";
    private static final String KEY_PERM_CAM = "perm_cam_enabled";

    // Audio Keys
    private static final String KEY_AUDIO_MESSAGE_PATH = "audio_message_path";
    private static final String KEY_VOICE_TRIGGER_PHRASE = "voice_trigger_phrase";

    private static final String DEFAULT_TRIGGER = "help";
    private static final String DEFAULT_MESSAGE = "Emergency! I need help. My location:";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    public TriggerManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    public static boolean isSamePhone(String p1, String p2) {
        if (p1 == null || p2 == null) return false;
        String n1 = normalizePhone(p1);
        String n2 = normalizePhone(p2);
        if (n1.isEmpty() || n2.isEmpty()) return false;
        
        // Handle cases where one might have a country code and the other doesn't (last 10 digits)
        if (n1.length() >= 10 && n2.length() >= 10) {
            return n1.substring(n1.length() - 10).equals(n2.substring(n2.length() - 10));
        }
        return n1.equals(n2);
    }

    public void saveUserId(String id) {
        sharedPreferences.edit().putString(KEY_USER_ID, id).apply();
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public void saveTriggerWord(String word) {
        sharedPreferences.edit().putString(KEY_TRIGGER_WORD, word.toLowerCase().trim()).apply();
    }

    public String getTriggerWord() {
        return sharedPreferences.getString(KEY_TRIGGER_WORD, DEFAULT_TRIGGER);
    }

    public void saveContacts(List<Contact> contacts) {
        String json = gson.toJson(contacts);
        sharedPreferences.edit().putString(KEY_EMERGENCY_CONTACTS, json).apply();
    }

    public List<Contact> getContacts() {
        String json = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<Contact>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveSosRecipients(List<Contact> contacts) {
        String json = gson.toJson(contacts);
        sharedPreferences.edit().putString(KEY_SOS_RECIPIENTS, json).apply();
    }

    public List<Contact> getSosRecipients() {
        String json = sharedPreferences.getString(KEY_SOS_RECIPIENTS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<Contact>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveChatContact(String phone, String name) {
        Map<String, String> chatContacts = getChatContacts();
        chatContacts.put(normalizePhone(phone), name);
        String json = gson.toJson(chatContacts);
        sharedPreferences.edit().putString(KEY_CHAT_CONTACTS, json).apply();
    }

    public Map<String, String> getChatContacts() {
        String json = sharedPreferences.getString(KEY_CHAT_CONTACTS, null);
        if (json == null) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public String getContactName(String phone) {
        if (phone == null) return null;
        String normalized = normalizePhone(phone);
        
        // Check chat contacts first
        Map<String, String> chatContacts = getChatContacts();
        // Check exact match first
        if (chatContacts.containsKey(normalized)) {
            return chatContacts.get(normalized);
        }
        // Fallback to fuzzy match (last 10 digits)
        if (normalized.length() >= 10) {
            String last10 = normalized.substring(normalized.length() - 10);
            for (Map.Entry<String, String> entry : chatContacts.entrySet()) {
                if (entry.getKey().endsWith(last10)) return entry.getValue();
            }
        }
        
        // Then check emergency contacts
        List<Contact> contacts = getContacts();
        for (Contact c : contacts) {
            if (isSamePhone(phone, c.getPhoneNumber())) {
                return c.getName();
            }
        }
        return null;
    }

    public boolean isContactSaved(String phone) {
        return getContactName(phone) != null;
    }

    public void blockPhone(String phone) {
        Set<String> blocked = getBlockedPhones();
        blocked.add(normalizePhone(phone));
        sharedPreferences.edit().putStringSet(KEY_BLOCKED_PHONES, blocked).apply();
    }

    public void unblockPhone(String phone) {
        Set<String> blocked = getBlockedPhones();
        blocked.remove(normalizePhone(phone));
        sharedPreferences.edit().putStringSet(KEY_BLOCKED_PHONES, blocked).apply();
    }

    public boolean isPhoneBlocked(String phone) {
        String normalized = normalizePhone(phone);
        Set<String> blocked = getBlockedPhones();
        if (blocked.contains(normalized)) return true;
        
        if (normalized.length() >= 10) {
            String last10 = normalized.substring(normalized.length() - 10);
            for (String b : blocked) {
                if (b.endsWith(last10)) return true;
            }
        }
        return false;
    }

    public Set<String> getBlockedPhones() {
        return new HashSet<>(sharedPreferences.getStringSet(KEY_BLOCKED_PHONES, new HashSet<>()));
    }

    public void saveSelectedContactIds(Set<String> phones) {
        Set<String> normalizedPhones = new HashSet<>();
        for (String p : phones) {
            normalizedPhones.add(normalizePhone(p));
        }
        sharedPreferences.edit().putStringSet(KEY_SELECTED_CONTACT_IDS, normalizedPhones).apply();
    }

    public Set<String> getSelectedContactIds() {
        Set<String> set = sharedPreferences.getStringSet(KEY_SELECTED_CONTACT_IDS, null);
        if (set == null) return new HashSet<>();
        return new HashSet<>(set);
    }

    public void saveEmergencyMessage(String message) {
        sharedPreferences.edit().putString(KEY_EMERGENCY_MESSAGE, message).apply();
    }

    public String getEmergencyMessage() {
        return sharedPreferences.getString(KEY_EMERGENCY_MESSAGE, DEFAULT_MESSAGE);
    }

    public void saveUserRole(String role) {
        sharedPreferences.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public String getUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, "");
    }

    public void saveUserName(String name) {
        sharedPreferences.edit().putString(KEY_USER_NAME, name).apply();
    }

    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    public void saveUserEmail(String email) {
        sharedPreferences.edit().putString(KEY_USER_EMAIL, email).apply();
    }

    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    public void saveUserPhone(String phone) {
        sharedPreferences.edit().putString(KEY_USER_PHONE, phone).apply();
    }

    public String getUserPhone() {
        return sharedPreferences.getString(KEY_USER_PHONE, "");
    }

    public void setLoggedIn(boolean isLoggedIn) {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void saveProfileImageUri(String uri) {
        sharedPreferences.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply();
    }

    public String getProfileImageUri() {
        return sharedPreferences.getString(KEY_PROFILE_IMAGE_URI, null);
    }

    public void saveLanguage(String language) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "English");
    }

    public void saveThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getThemeMode() {
        return sharedPreferences.getInt(KEY_THEME_MODE, 0); // 0 is System/Follow System
    }

    // Permission Management Methods
    public void setMicPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_MIC, enabled).apply();
    }

    public boolean isMicPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_MIC, true);
    }

    public void setLocationPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_LOC, enabled).apply();
    }

    public boolean isLocationPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_LOC, true);
    }

    public void setSmsPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_SMS, enabled).apply();
    }

    public boolean isSmsPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_SMS, true);
    }

    public void setNotificationPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_NOTIF, enabled).apply();
    }

    public boolean isNotificationPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_NOTIF, true);
    }

    public void setCameraPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_CAM, enabled).apply();
    }

    public boolean isCameraPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_CAM, true);
    }

    // Feature toggles
    public void setShakeTriggerEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply();
    }

    public boolean isShakeTriggerEnabled() {
        return sharedPreferences.getBoolean(KEY_SHAKE_ENABLED, true);
    }

    // Audio Methods
    public void saveAudioMessagePath(String path) {
        sharedPreferences.edit().putString(KEY_AUDIO_MESSAGE_PATH, path).apply();
    }

    public String getAudioMessagePath() {
        return sharedPreferences.getString(KEY_AUDIO_MESSAGE_PATH, null);
    }

    public void saveVoiceTriggerPhrase(String phrase) {
        sharedPreferences.edit().putString(KEY_VOICE_TRIGGER_PHRASE, phrase.toLowerCase().trim()).apply();
    }

    public String getVoiceTriggerPhrase() {
        return sharedPreferences.getString(KEY_VOICE_TRIGGER_PHRASE, DEFAULT_TRIGGER);
    }

    // History Methods
    public void addSosHistoryEntry(SosHistoryEntry entry) {
        List<SosHistoryEntry> history = getSosHistory();
        history.add(0, entry); // Add to top
        if (history.size() > 50) history.remove(history.size() - 1); // Keep last 50
        String json = gson.toJson(history);
        sharedPreferences.edit().putString(KEY_SOS_HISTORY, json).apply();

        String userId = getUserId();
        if (userId != null && !userId.isEmpty() && entry != null) {
            try {
                FirebaseDatabase.getInstance().getReference("users")
                        .child(userId).child("sos_history").child(entry.getId()).setValue(entry);
                FirebaseFirestore.getInstance().collection("users")
                        .document(userId).collection("sos_history").document(entry.getId()).set(entry);
            } catch (Exception ignored) {}
        }
    }

    public List<SosHistoryEntry> getSosHistory() {
        String json = sharedPreferences.getString(KEY_SOS_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<SosHistoryEntry>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveSosHistory(List<SosHistoryEntry> history) {
        if (history == null) history = new ArrayList<>();
        String json = gson.toJson(history);
        sharedPreferences.edit().putString(KEY_SOS_HISTORY, json).apply();
    }

    public void clearSosHistory() {
        sharedPreferences.edit().remove(KEY_SOS_HISTORY).apply();
        String userId = getUserId();
        if (userId != null && !userId.isEmpty()) {
            try {
                FirebaseDatabase.getInstance().getReference("users")
                        .child(userId).child("sos_history").removeValue();
            } catch (Exception ignored) {}
        }
    }

    public void removeSosHistoryEntry(String entryId) {
        List<SosHistoryEntry> history = getSosHistory();
        boolean removed = false;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(entryId)) {
                history.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            String json = gson.toJson(history);
            sharedPreferences.edit().putString(KEY_SOS_HISTORY, json).apply();
        }

        String userId = getUserId();
        if (userId != null && !userId.isEmpty() && entryId != null) {
            try {
                FirebaseDatabase.getInstance().getReference("users")
                        .child(userId).child("sos_history").child(entryId).removeValue();
                FirebaseFirestore.getInstance().collection("users")
                        .document(userId).collection("sos_history").document(entryId).delete();
            } catch (Exception ignored) {}
        }
    }

    public void clearData() {
        sharedPreferences.edit().clear().apply();
    }
}
