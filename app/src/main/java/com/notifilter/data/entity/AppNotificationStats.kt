package com.notifilter.data.entity

data class AppNotificationStats(
    val packageName: String,
    val appName: String,
    val totalCount: Int,
    val blockedCount: Int
)
