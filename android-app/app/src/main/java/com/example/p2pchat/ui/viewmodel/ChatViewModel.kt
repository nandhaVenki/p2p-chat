package com.example.p2pchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.p2pchat.data.local.entity.MessageEntity
import com.example.p2pchat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

import android.content.Context
import android.content.Intent
import com.example.p2pchat.service.ChatService
import dagger.hilt.android.qualifiers.ApplicationContext

import com.example.p2pchat.billing.BillingManager
import android.app.Activity

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import com.example.p2pchat.data.model.ConnectionStatus

import com.example.p2pchat.data.local.entity.UserProfileEntity

import com.example.p2pchat.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val billingManager: BillingManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    val groups: StateFlow<List<GroupEntity>> = repository.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChats: StateFlow<List<com.example.p2pchat.data.local.entity.DirectChatEntity>> = repository.getDirectChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus

    val isPremium: StateFlow<Boolean> = combine(
        billingManager.isSubscribed,
        billingManager.isLifetimeActive
    ) { sub, life -> true } // Temporarily making everyone premium as requested
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _isActiveChatGroup = MutableStateFlow(false)
    val isActiveChatGroup = _isActiveChatGroup.asStateFlow()

    private var messagesJob: kotlinx.coroutines.Job? = null

    fun register(firstName: String, lastName: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.registerUser(firstName, lastName, phoneNumber)
        }
    }

    fun init(phoneNumber: String) {
        val intent = Intent(context, ChatService::class.java).apply {
            putExtra("PHONE_NUMBER", phoneNumber)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        repository.initialize(phoneNumber)
    }

    fun startChat(peerId: String) {
        _isActiveChatGroup.value = false
        repository.startChatWith(peerId)
        
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages().collect {
                _messages.value = it
            }
        }
    }

    fun startGroupChat(groupId: String) {
        _isActiveChatGroup.value = true
        
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getGroupMessages(groupId).collect {
                _messages.value = it
            }
        }
    }

    fun createGroup(name: String, membersPhoneNumbers: List<String>) {
        viewModelScope.launch {
            repository.createGroup(name, membersPhoneNumbers)
        }
    }

    fun sendMessage(text: String, chatPartnerId: String) {
        if (_isActiveChatGroup.value) {
            repository.sendGroupMessage(chatPartnerId, text)
        } else {
            repository.sendMessage(text)
        }
    }

    fun createManualOffer(onOfferCreated: (String) -> Unit) {
        repository.createManualOffer(onOfferCreated)
    }

    fun acceptManualOffer(sdp: String, onAnswerCreated: (String) -> Unit) {
        repository.acceptManualOffer(sdp, onAnswerCreated)
    }

    fun acceptManualAnswer(sdp: String) {
        repository.acceptManualAnswer(sdp)
    }
}
