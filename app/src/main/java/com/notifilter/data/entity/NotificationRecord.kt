package com.notifilter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_record")
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val content: String,
    val channelId: String?,
    val timestamp: Long,
    val isBlocked: Boolean,
    val blockReason: String?
)
