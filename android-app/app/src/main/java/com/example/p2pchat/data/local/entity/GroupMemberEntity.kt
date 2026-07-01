package com.example.p2pchat.data.local.entity

import androidx.room.Entity

@Entity(tableName = "group_members", primaryKeys = ["groupId", "memberPhoneHash"])
data class GroupMemberEntity(
    val groupId: String,
    val memberPhoneHash: String // hashed phone number of group member
)
