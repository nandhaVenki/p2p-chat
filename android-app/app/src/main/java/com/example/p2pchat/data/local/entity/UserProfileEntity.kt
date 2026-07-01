package com.example.p2pchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val phoneNumber: String,
    val firstName: String,
    val lastName: String,
    val isRegistered: Boolean = true
)
