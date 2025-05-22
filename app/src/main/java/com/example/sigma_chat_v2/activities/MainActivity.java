// file: MainActivity.java
package com.example.sigma_chat_v2.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sigma_chat_v2.adapters.RecentConversationAdapter;
import com.example.sigma_chat_v2.databinding.ActivityMainBinding;
import com.example.sigma_chat_v2.listeners.ConversionListener;
import com.example.sigma_chat_v2.models.ChatMessage;
import com.example.sigma_chat_v2.models.User;
import com.example.sigma_chat_v2.utilities.Constants;
import com.example.sigma_chat_v2.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import android.util.Log;


public class MainActivity extends BaseActivity implements ConversionListener {

    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationAdapter conversationAdapter;
    private FirebaseFirestore database;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager((getApplicationContext()));
        init();
        loadUserDetails();
        getToken();
        setListeners();
        try {
            listenConversations();
        } catch (Exception e) {
            Log.e("MainActivity", "listenConversations error", e);
            showToast("Gagal memuat percakapan");
        }


        // Optional: WindowInsets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void init(){
        conversations = new ArrayList<>();
        conversationAdapter = new RecentConversationAdapter(conversations, this);
        binding.conversationsRecyclerView.setAdapter(conversationAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.signOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), UsersActivity.class)));
    }
    private void loadUserDetails() {
        String name = preferenceManager.getString(Constants.KEY_NAME);
        String encodedImage = preferenceManager.getString(Constants.KEY_IMAGE);

        binding.textName.setText(name != null ? name : "Unknown");

        if (encodedImage != null && !encodedImage.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                binding.imageProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Gagal memuat gambar profil");
            }
        } else {
            showToast("Gambar profil tidak tersedia");
        }
    }


    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void listenConversations() {
        String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
        if (currentUserId == null || currentUserId.isEmpty()) {
            showToast("User ID tidak ditemukan");
            return;
        }

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, currentUserId)
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            error.printStackTrace();
            showToast("Terjadi kesalahan saat memuat data chat");
            return;
        }

        if (value == null) return;

        for (DocumentChange change : value.getDocumentChanges()) {
            String senderId = change.getDocument().getString(Constants.KEY_SENDER_ID);
            String receiverId = change.getDocument().getString(Constants.KEY_RECEIVER_ID);

            if (senderId == null || receiverId == null) continue;

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.senderId = senderId;
            chatMessage.receiverId = receiverId;

            boolean isCurrentUserSender = preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId);
            chatMessage.conversionImage = change.getDocument().getString(
                    isCurrentUserSender ? Constants.KEY_RECEIVER_IMAGE : Constants.KEY_SENDER_IMAGE
            );
            chatMessage.conversionName = change.getDocument().getString(
                    isCurrentUserSender ? Constants.KEY_RECEIVER_NAME : Constants.KEY_SENDER_NAME
            );
            chatMessage.conversionId = change.getDocument().getString(
                    isCurrentUserSender ? Constants.KEY_RECEIVER_ID : Constants.KEY_SENDER_ID
            );

            Object lastMessageObj = change.getDocument().get(Constants.KEY_LAST_MESSAGE);
            chatMessage.message = lastMessageObj != null ? lastMessageObj.toString() : "";

            chatMessage.dateObject = change.getDocument().getDate(Constants.KEY_TIMESTAMP);

            if (change.getType() == DocumentChange.Type.ADDED) {
                conversations.add(chatMessage);
            } else if (change.getType() == DocumentChange.Type.MODIFIED) {
                for (int i = 0; i < conversations.size(); i++) {
                    ChatMessage existing = conversations.get(i);
                    if (existing.senderId.equals(senderId) && existing.receiverId.equals(receiverId)) {
                        existing.message = chatMessage.message;
                        existing.dateObject = chatMessage.dateObject;
                        break;
                    }
                }
            }
        }

        Collections.sort(conversations, (obj1, obj2) -> {
            if (obj1.dateObject == null && obj2.dateObject == null) return 0;
            if (obj1.dateObject == null) return 1; // null dianggap lebih lama
            if (obj2.dateObject == null) return -1;
            return obj2.dateObject.compareTo(obj1.dateObject);
        });

        conversationAdapter.notifyDataSetChanged();
        binding.conversationsRecyclerView.smoothScrollToPosition(0);
        binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
    };


    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }

    private void updateToken(String token) {
        preferenceManager.putstring(Constants.KEY_FCM_TOKEN, token);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast("Unable to update token"));
    }

    private void signOut() {
        showToast("ADIOSSS......");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.clear();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> showToast("gabisa keluar"));
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }
}
