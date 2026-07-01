package com.example.p2pchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String, // UUID string
    val groupName: String,
    val creatorId: String, // phoneHash
    val timestamp: Long
)
