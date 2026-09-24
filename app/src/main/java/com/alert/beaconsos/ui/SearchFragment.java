package com.alert.beaconsos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beaconsos.R;
import com.alert.beaconsos.data.Contact;
import com.alert.beaconsos.data.SearchResult;
import com.alert.beaconsos.data.TriggerManager;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private SearchView searchView;
    private RecyclerView rvSearchResults;
    private TextView tvNoResults;
    private SearchAdapter adapter;
    private TriggerManager triggerManager;
    private List<SearchResult> allSearchableItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        triggerManager = new TriggerManager(requireContext());
        searchView = view.findViewById(R.id.searchView);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        setupRecyclerView();
        prepareSearchData();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                return true;
            }
        });

        return view;
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(new ArrayList<>());
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSearchResults.setAdapter(adapter);
    }

    private void prepareSearchData() {
        allSearchableItems = new ArrayList<>();

        // Add Settings/Profile items
        allSearchableItems.add(new SearchResult("Profile", "View and edit your profile", R.drawable.ic_edit_profile_new, new Intent(getContext(), ProfileActivity.class)));
        allSearchableItems.add(new SearchResult("Settings", "App preferences and configuration", R.drawable.ic_settings_new, new Intent(getContext(), SettingsActivity.class)));
        allSearchableItems.add(new SearchResult("Emergency Message", "Edit your SOS message", R.drawable.ic_emergency_contacts_new, new Intent(getContext(), AlertMessageActivity.class)));
        allSearchableItems.add(new SearchResult("Change Password", "Update your account security", R.drawable.ic_password_new, new Intent(getContext(), ChangePasswordActivity.class)));
        allSearchableItems.add(new SearchResult("Trusted Contacts", "Manage your emergency contacts", R.drawable.ic_trusted_contacts_new, new Intent(getContext(), TrustedContactsActivity.class)));

        // Add Contacts
        List<Contact> contacts = triggerManager.getContacts();
        for (Contact contact : contacts) {
            Intent intent = new Intent(getContext(), EditContactActivity.class);
            intent.putExtra("contact_id", contact.getId());
            intent.putExtra("contact_name", contact.getName());
            intent.putExtra("contact_phone", contact.getPhoneNumber());
            intent.putExtra("contact_relation", contact.getRelation());
            allSearchableItems.add(new SearchResult(contact.getName(), "Contact: " + contact.getPhoneNumber(), R.drawable.ic_profile_new, intent));
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            adapter.updateData(new ArrayList<>());
            tvNoResults.setVisibility(View.GONE);
            return;
        }

        List<SearchResult> filteredResults = new ArrayList<>();
        for (SearchResult item : allSearchableItems) {
            if (item.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                item.getSubtitle().toLowerCase().contains(query.toLowerCase())) {
                filteredResults.add(item);
            }
        }

        adapter.updateData(filteredResults);
        tvNoResults.setVisibility(filteredResults.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
