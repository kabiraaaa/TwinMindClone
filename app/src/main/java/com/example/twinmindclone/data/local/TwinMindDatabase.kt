package com.example.twinmindclone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.twinmindclone.data.local.dao.AudioChunkDao
import com.example.twinmindclone.data.local.dao.MeetingDao
import com.example.twinmindclone.data.local.entity.AudioChunkEntity
import com.example.twinmindclone.data.local.entity.MeetingEntity

@Database(
    entities = [MeetingEntity::class, AudioChunkEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TwinMindDatabase : RoomDatabase() {
    abstract val meetingDao: MeetingDao
    abstract val audioChunkDao: AudioChunkDao
}
