package com.example.p2pchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val isSent: Boolean,
    val isMedia: Boolean = false,
    val mediaUri: String? = null,
    val fileTransferId: String? = null, // For tracking chunks
    val groupId: String? = null, // Null for 1-on-1 private chats, non-null UUID for group chats
    val messageId: String = java.util.UUID.randomUUID().toString()
)
