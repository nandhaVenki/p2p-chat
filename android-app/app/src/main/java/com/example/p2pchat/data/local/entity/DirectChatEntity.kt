package com.example.p2pchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_chats")
data class DirectChatEntity(
    @PrimaryKey val peerPhoneNumber: String,
    val peerPhoneHash: String,
    val lastActiveTimestamp: Long
)
