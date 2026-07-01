package com.example.p2pchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.example.p2pchat.ui.screen.ChatListScreen
import com.example.p2pchat.ui.screen.ChatScreen
import com.example.p2pchat.ui.screen.RegistrationScreen
import com.example.p2pchat.ui.theme.P2PChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            P2PChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: com.example.p2pchat.ui.viewmodel.ChatViewModel = hiltViewModel()
                    val userProfile by viewModel.userProfile.collectAsState()
                    var currentPeerId by remember { mutableStateOf<String?>(null) }
                    var forceRegistration by remember { mutableStateOf(false) }

                    when {
                        userProfile == null || forceRegistration -> {
                            RegistrationScreen(viewModel) {
                                forceRegistration = false
                            }
                        }
                        currentPeerId == null -> {
                            // Automatically init with saved number if not done yet
                            LaunchedEffect(userProfile) {
                                userProfile?.let { viewModel.init(it.phoneNumber) }
                            }
                            ChatListScreen(viewModel) { chatId, isGroup ->
                                currentPeerId = chatId
                                if (isGroup) {
                                    viewModel.startGroupChat(chatId)
                                } else {
                                    viewModel.startChat(chatId)
                                }
                            }
                        }
                        else -> {
                            ChatScreen(viewModel, peerId = currentPeerId!!) {
                                currentPeerId = null
                            }
                        }
                    }
                }
            }
        }
    }
}
