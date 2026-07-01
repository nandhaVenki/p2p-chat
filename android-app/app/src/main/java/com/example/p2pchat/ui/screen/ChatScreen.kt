package com.example.p2pchat.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.p2pchat.ui.viewmodel.ChatViewModel

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.example.p2pchat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

import com.example.p2pchat.data.model.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, peerId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    var messageText by remember { mutableStateOf("") }
    
    var manualSdpInput by remember { mutableStateOf("") }
    var showSdpDialog by remember { mutableStateOf(false) }
    var sdpToDisplay by remember { mutableStateOf("") }

    val messages by viewModel.messages.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    if (showSdpDialog) {
        AlertDialog(
            onDismissRequest = { showSdpDialog = false },
            title = { Text("Share Secure Identity") },
            text = { 
                Column {
                    Text(
                        "Send this secure code to your peer to establish a private P2P connection.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    ) {
                        Text(
                            text = sdpToDisplay, 
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp).fillMaxSize(),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "P2P_CONNECT:$sdpToDisplay")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    showSdpDialog = false 
                }) {
                    Text("Share Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSdpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Private Chat", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> Color(0xFF00FF00)
                                    ConnectionStatus.CONNECTING -> Color(0xFFFFFF00)
                                    ConnectionStatus.ERROR -> Color(0xFFFF0000)
                                    else -> Color.Gray
                                }
                                Surface(
                                    color = statusColor,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    peerId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepEmerald,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isSystemInDarkTheme()) DarkBackgroundColor else BackgroundColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(shadowElevation = 2.dp) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(
                                onClick = { 
                                    viewModel.createManualOffer { sdp -> sdpToDisplay = sdp; showSdpDialog = true }
                                },
                                modifier = Modifier.weight(1f).padding(4.dp)
                            ) { Text("1. Invite", style = MaterialTheme.typography.labelSmall) }
                            
                            Button(
                                onClick = { 
                                    if (manualSdpInput.isNotEmpty()) {
                                        viewModel.acceptManualOffer(manualSdpInput) { answer ->
                                            sdpToDisplay = answer; showSdpDialog = true; manualSdpInput = ""
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) { Text("2. Reply", style = MaterialTheme.typography.labelSmall) }

                            Button(
                                onClick = { 
                                    if (manualSdpInput.isNotEmpty()) {
                                        viewModel.acceptManualAnswer(manualSdpInput)
                                        manualSdpInput = ""
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                            ) { Text("3. Finalize", style = MaterialTheme.typography.labelSmall) }
                        }
                        
                        TextField(
                            value = manualSdpInput,
                            onValueChange = { manualSdpInput = it },
                            placeholder = { Text("Paste code from peer here...") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    reverseLayout = false
                ) {
                    items(messages) { msg ->
                        ChatBubble(msg)
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSystemInDarkTheme()) DarkPeerBubbleColor else Color.White,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Message") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotEmpty()) {
                                viewModel.sendMessage(messageText, peerId)
                                messageText = ""
                            }
                        },
                        shape = CircleShape,
                        containerColor = EmeraldGreen,
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: com.example.p2pchat.data.local.entity.MessageEntity) {
    val isMe = msg.isSent
    val isDark = isSystemInDarkTheme()
    
    val bubbleColor = when {
        isMe && isDark -> EmeraldGreen.copy(alpha = 0.8f)
        isMe && !isDark -> EmeraldGreen.copy(alpha = 0.15f)
        !isMe && isDark -> DarkPeerBubbleColor
        else -> Color(0xFFF0F0F0)
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            shadowElevation = 0.5.dp,
            border = if (!isMe && !isDark) BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f)) else null
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.isMedia) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "File",
                            modifier = Modifier.size(20.dp),
                            tint = if (isMe) EmeraldGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                } else {
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDark) Color.White else Color.Black
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f)
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Sent",
                            modifier = Modifier.size(12.dp),
                            tint = EmeraldGreen.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
