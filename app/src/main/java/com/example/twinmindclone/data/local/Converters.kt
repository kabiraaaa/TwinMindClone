package com.example.twinmindclone.data.local

import androidx.room.TypeConverter
import com.example.twinmindclone.data.local.entity.ChunkStatus
import com.example.twinmindclone.data.local.entity.MeetingStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromMeetingStatus(status: MeetingStatus): String = status.name

    @TypeConverter
    fun toMeetingStatus(name: String): MeetingStatus = enumValueOf(name)

    @TypeConverter
    fun fromChunkStatus(status: ChunkStatus): String = status.name

    @TypeConverter
    fun toChunkStatus(name: String): ChunkStatus = enumValueOf(name)
}
