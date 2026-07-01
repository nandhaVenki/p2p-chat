package com.example.p2pchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.p2pchat.data.local.entity.DirectChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectChatDao {
    @Query("SELECT * FROM direct_chats ORDER BY lastActiveTimestamp DESC")
    fun getDirectChats(): Flow<List<DirectChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectChat(directChat: DirectChatEntity)

    @Query("SELECT peerPhoneHash FROM direct_chats")
    suspend fun getAllDirectChatHashes(): List<String>
}
