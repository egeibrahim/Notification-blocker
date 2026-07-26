package com.notifilter.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notifilter.BuildConfig
import com.notifilter.R
import com.notifilter.NotifilterApplication
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.service.NotifilterListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchivePage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as NotifilterApplication
    val dao = app.database.notificationRecordDao()

    var notifications by remember { mutableStateOf<List<NotificationRecord>>(emptyList()) }
    val since = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L

    LaunchedEffect(Unit) {
        dao.getSince(since).collect { notifications = it }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.archive_last_7_days_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = notifications, key = { it.id }) { notification ->
                ArchiveNotificationCard(notification = notification)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveNotificationCard(notification: NotificationRecord) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    @Composable
    fun localizedBlockReason(reason: String?): String {
        if (reason.isNullOrBlank()) return ""
        return when {
            reason == "EMOJI" -> stringResource(R.string.reason_contains_emoji)
            reason == "FOCUS_MODE" || reason == "Genel Odak Modu aktif" || reason == "Odak Modu açık" ->
                stringResource(R.string.reason_focus_mode_active)
            reason == "GAMES_PACK" || reason == "Games pack active" || reason == "Oyun paketi aktif" ->
                stringResource(R.string.reason_games_pack_active)
            reason == "MUTE_SAVE" || reason == "Gizle kaydet" ->
                stringResource(R.string.reason_mute_and_save)
            reason.startsWith("MANUAL_CHANNEL") || reason.startsWith("Manuel engellenen kanal:") ->
                stringResource(R.string.reason_manual_blocked_channel)
            reason.startsWith("CHANNEL_ID") || reason.startsWith("Kanal ID:") ->
                stringResource(R.string.reason_channel_id)
            reason.startsWith("CONTENT") || reason.startsWith("İçerik:") -> {
                val keyword = reason.substringAfter(":").trim().trim('"', '\'', '’')
                stringResource(R.string.reason_content, keyword)
            }
            else -> reason
        }
    }

    val sourceText = when {
        notification.isBlocked && notification.blockReason != null ->
            stringResource(R.string.archive_source_blocked, localizedBlockReason(notification.blockReason))
        else ->
            stringResource(R.string.archive_source_notification)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val opened = NotifilterListenerService.openActiveNotification(
                    packageName = notification.packageName,
                    timestamp = notification.timestamp,
                    content = notification.content
                )
                if (!opened) {
                    val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent?.let { context.startActivity(it) }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isBlocked) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = notification.appName.ifBlank { notification.packageName },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(notification.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = notification.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = sourceText,
                style = MaterialTheme.typography.bodySmall,
                color = if (notification.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
