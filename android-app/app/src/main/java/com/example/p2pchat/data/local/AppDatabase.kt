package com.example.p2pchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.p2pchat.data.local.dao.MessageDao
import com.example.p2pchat.data.local.dao.UserProfileDao
import com.example.p2pchat.data.local.entity.MessageEntity
import com.example.p2pchat.data.local.entity.UserProfileEntity

import com.example.p2pchat.data.local.dao.GroupDao
import com.example.p2pchat.data.local.entity.GroupEntity
import com.example.p2pchat.data.local.entity.GroupMemberEntity

@Database(
    entities = [
        MessageEntity::class,
        UserProfileEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun groupDao(): GroupDao
}
