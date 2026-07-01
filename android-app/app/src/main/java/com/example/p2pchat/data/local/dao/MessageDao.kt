package com.example.p2pchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.p2pchat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :userId AND receiverId = :peerId) OR (senderId = :peerId AND receiverId = :userId) ORDER BY timestamp ASC")
    fun getMessagesForChat(userId: String, peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessagesForGroup(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE messageId = :messageId LIMIT 1)")
    suspend fun messageExists(messageId: String): Boolean

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT DISTINCT CASE WHEN senderId = :myId THEN receiverId ELSE senderId END FROM messages WHERE groupId IS NULL OR groupId = ''")
    suspend fun getDirectPeers(myId: String): List<String>
}
