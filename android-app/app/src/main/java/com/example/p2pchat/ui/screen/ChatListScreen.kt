package com.example.p2pchat.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.p2pchat.ui.theme.DeepEmerald
import com.example.p2pchat.ui.theme.EmeraldGreen
import com.example.p2pchat.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatViewModel, onChatSelected: (String, Boolean) -> Unit) {
    var showAddChatDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var peerPhoneNumber by remember { mutableStateOf("") }
    
    var groupName by remember { mutableStateOf("") }
    var groupMembersInput by remember { mutableStateOf("") } // comma-separated phone numbers
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Chats, 1: Groups
    
    val activeChats by viewModel.activeChats.collectAsState()
    val groups by viewModel.groups.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Private P2P Messages") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepEmerald,
                        titleContentColor = Color.White
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DeepEmerald,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Direct", color = Color.White) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Groups (P2P Mesh)", color = Color.White) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        showAddChatDialog = true
                    } else {
                        showAddGroupDialog = true
                    }
                },
                containerColor = EmeraldGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) {
                // Direct chats list
                if (activeChats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active direct chats. Start one below!", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(activeChats) { chat ->
                            ListItem(
                                headlineContent = { Text(chat.peerPhoneNumber) },
                                leadingContent = { 
                                    Surface(shape = MaterialTheme.shapes.small, color = EmeraldGreen.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = EmeraldGreen)
                                    }
                                },
                                modifier = Modifier.clickable { onChatSelected(chat.peerPhoneNumber, false) }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
                        }
                    }
                }
            } else {
                // Group chats list
                if (groups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active groups. Create one below!", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groups) { group ->
                            ListItem(
                                headlineContent = { Text(group.groupName) },
                                supportingContent = { Text("ID: ${group.groupId}") },
                                leadingContent = { 
                                    Surface(shape = MaterialTheme.shapes.small, color = EmeraldGreen.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = EmeraldGreen)
                                    }
                                },
                                modifier = Modifier.clickable { onChatSelected(group.groupId, true) }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
                        }
                    }
                }
            }
        }

        if (showAddChatDialog) {
            AlertDialog(
                onDismissRequest = { showAddChatDialog = false },
                title = { Text("New Secure Chat") },
                text = {
                    OutlinedTextField(
                        value = peerPhoneNumber,
                        onValueChange = { peerPhoneNumber = it },
                        label = { Text("Peer Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (peerPhoneNumber.isNotBlank()) {
                            showAddChatDialog = false
                            onChatSelected(peerPhoneNumber, false)
                        }
                    }) {
                        Text("Start Chat")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChatDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddGroupDialog) {
            AlertDialog(
                onDismissRequest = { showAddGroupDialog = false },
                title = { Text("Create P2P Mesh Group") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Group Name") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = groupMembersInput,
                            onValueChange = { groupMembersInput = it },
                            label = { Text("Members' Phones (comma separated)") },
                            placeholder = { Text("+1234567,+7654321") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (groupName.isNotBlank()) {
                            val membersList = groupMembersInput.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            viewModel.createGroup(groupName, membersList)
                            showAddGroupDialog = false
                            groupName = ""
                            groupMembersInput = ""
                        }
                    }) {
                        Text("Create Group")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddGroupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
