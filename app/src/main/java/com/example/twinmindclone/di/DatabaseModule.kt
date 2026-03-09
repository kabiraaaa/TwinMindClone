package com.example.twinmindclone.di

import android.content.Context
import androidx.room.Room
import com.example.twinmindclone.data.local.TwinMindDatabase
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.dao.MeetingDao
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
    fun provideTwinMindDatabase(@ApplicationContext context: Context): TwinMindDatabase {
        return Room.databaseBuilder(
            context,
            TwinMindDatabase::class.java,
            "twinmind_db"
        )
            .build()
    }

    @Provides
    fun provideMeetingDao(database: TwinMindDatabase): MeetingDao = database.meetingDao

    @Provides
    fun provideAudioChunkDao(database: TwinMindDatabase): AudioChunkDao = database.audioChunkDao
}
