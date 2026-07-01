package com.example.p2pchat.di

import android.content.Context
import androidx.room.Room
import com.example.p2pchat.data.local.AppDatabase
import com.example.p2pchat.data.local.dao.MessageDao
import com.example.p2pchat.data.local.dao.UserProfileDao
import com.example.p2pchat.data.local.dao.GroupDao
import com.example.p2pchat.data.local.dao.DirectChatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "p2p_chat.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao {
        return db.messageDao()
    }

    @Provides
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao {
        return db.userProfileDao()
    }

    @Provides
    fun provideGroupDao(db: AppDatabase): GroupDao {
        return db.groupDao()
    }

    @Provides
    fun provideDirectChatDao(db: AppDatabase): DirectChatDao {
        return db.directChatDao()
    }
}
