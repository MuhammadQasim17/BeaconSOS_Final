package com.alert.beaconsos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alert.beaconsos.data.ChatMessage
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

class IndividualChatViewModel : ViewModel() {
    private val database: DatabaseReference = FirebaseDatabase.getInstance("https://beacon-sos-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _customName = MutableStateFlow<String?>(null)
    val customName: StateFlow<String?> = _customName

    private var messagesListener: ValueEventListener? = null
    private var settingsListener: ValueEventListener? = null
    
    private var settingsRef: DatabaseReference? = null
    private var chatQuery: Query? = null

    private fun getChatId(phone1: String, phone2: String): String {
        val p1 = phone1.filter { it.isDigit() }
        val p2 = phone2.filter { it.isDigit() }
        return if (p1 < p2) "${p1}_${p2}" else "${p2}_${p1}"
    }

    fun initChat(currentUserPhone: String, otherUserPhone: String) {
        val cid = getChatId(currentUserPhone, otherUserPhone)
        val myPhoneNorm = currentUserPhone.filter { it.isDigit() }

        cleanup()

        val sRef = database.child("userChatSettings").child(myPhoneNorm).child(cid)
        settingsRef = sRef
        settingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _customName.value = snapshot.child("customName").getValue(String::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        sRef.addValueEventListener(settingsListener!!)

        val mQuery = database.child("chats").child(cid).child("messages").orderByChild("timestamp")
        chatQuery = mQuery
        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val msg = child.getValue(ChatMessage::class.java)
                    if (msg != null) {
                        msg.id = child.key
                        list.add(msg)
                    }
                }
                _messages.value = list
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        mQuery.addValueEventListener(messagesListener!!)
    }

    fun sendMessage(currentUserPhone: String, otherUserPhone: String, text: String) {
        if (text.isBlank()) return
        val cid = getChatId(currentUserPhone, otherUserPhone)
        val msgRef = database.child("chats").child(cid).child("messages").push()
        val message = ChatMessage(
            currentUserPhone,
            "", // senderName
            text.trim(),
            System.currentTimeMillis()
        )
        msgRef.setValue(message)
    }

    fun saveCustomName(currentUserPhone: String, otherUserPhone: String, newName: String) {
        val cid = getChatId(currentUserPhone, otherUserPhone)
        val myPhoneNorm = currentUserPhone.filter { it.isDigit() }
        val ref = database.child("userChatSettings").child(myPhoneNorm).child(cid)
        val settings = mapOf(
            "customName" to newName.trim(),
            "savedAt" to ServerValue.TIMESTAMP
        )
        ref.setValue(settings)
    }
    
    private fun cleanup() {
        val sL = settingsListener
        if (sL != null) {
            settingsRef?.removeEventListener(sL)
        }
        val mL = messagesListener
        if (mL != null) {
            chatQuery?.removeEventListener(mL)
        }
        settingsListener = null
        messagesListener = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualChatScreen(
    currentUserPhone: String,
    otherUserPhone: String,
    onBack: () -> Unit,
    viewModel: IndividualChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val customName by viewModel.customName.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(currentUserPhone, otherUserPhone) {
        viewModel.initChat(currentUserPhone, otherUserPhone)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showNameDialog = true }
                    ) {
                        AsyncImage(
                            model = "https://ui-avatars.com/api/?name=${customName ?: otherUserPhone}&background=random",
                            contentDescription = "Avatar",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = customName ?: otherUserPhone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "online", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            ChatInputBar(text = inputText, onTextChange = { inputText = it }, onSend = {
                viewModel.sendMessage(currentUserPhone, otherUserPhone, inputText)
                inputText = ""
            })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFE5DDD5))) {
            LazyColumn(
                state = listState, 
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(8.dp), 
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id ?: it.timestamp }) { message ->
                    MessageBubble(message = message, isMe = message.senderPhone == currentUserPhone)
                }
            }
        }

        if (showNameDialog) {
            EditNameDialog(currentName = customName ?: "", onDismiss = { showNameDialog = false }, onSave = { newName ->
                viewModel.saveCustomName(currentUserPhone, otherUserPhone, newName)
                showNameDialog = false
            })
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    val bubbleColor = if (isMe) Color(0xFFDCF8C6) else Color.White
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(
        topStart = 12.dp, 
        topEnd = 12.dp,
        bottomStart = if (isMe) 12.dp else 0.dp,
        bottomEnd = if (isMe) 0.dp else 12.dp
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(modifier = Modifier.widthIn(max = 280.dp).clip(shape).background(bubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = message.message ?: "", fontSize = 16.sp)
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                fontSize = 10.sp, 
                color = Color.Gray, 
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text, 
                onValueChange = onTextChange, 
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, 
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent, 
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    contentDescription = "Send", 
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

@Composable
fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Contact Name") },
        text = {
            OutlinedTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text("Custom Name") }, 
                singleLine = true, 
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { 
            Button(onClick = { onSave(name) }) { Text("Save") } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Cancel") } 
        }
    )
}
