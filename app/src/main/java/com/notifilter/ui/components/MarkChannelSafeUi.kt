package com.notifilter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notifilter.R
import com.notifilter.BuildConfig
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.preferences.ImportantChannelsPreferences
import com.notifilter.preferences.FilterRulesPreferences
import com.notifilter.service.NotifilterListenerService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Close

/**
 * Uygulama bildirimlerini listeleyen bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationDetailSheet(
    appName: String,
    packageName: String,
    notifications: List<NotificationRecord>,
    importantPrefs: ImportantChannelsPreferences,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onChannelMarkedSafe: () -> Unit,
    onWhitelistAdded: ((String) -> Unit)? = null
) {
    var refreshTrigger by remember { mutableStateOf(0) }
    var allowDraft by remember { mutableStateOf("") }
    var blockDraft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val context = LocalContext.current
        val filterPrefs = remember(context) { FilterRulesPreferences(context) }
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.detail_exempt_app_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                MuteSwitchView(
                    checked = importantPrefs.isWhitelisted(packageName),
                    onCheckedChange = {
                        if (importantPrefs.isWhitelisted(packageName)) {
                            importantPrefs.removeFromWhitelist(packageName)
                        } else {
                            importantPrefs.addToWhitelist(packageName)
                            onWhitelistAdded?.invoke(packageName)
                        }
                        onChannelMarkedSafe()
                    }
                )
            }

            if (filterPrefs.isGlobalEmojiBlockEnabled) {
                val emojiAllowed = remember(refreshTrigger) {
                    filterPrefs.isUserAppEmojiAllowed(packageName)
                }
                var emojiAllowState by remember { mutableStateOf(emojiAllowed) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_allow_emoji_in_app),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    MuteSwitchView(
                        checked = emojiAllowState,
                        onCheckedChange = {
                            val next = !emojiAllowState
                            emojiAllowState = next
                            filterPrefs.setUserAppEmojiAllowed(packageName, next)
                            refreshTrigger++
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.detail_notification_history),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = stringResource(R.string.detail_block_words_for_app),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = blockDraft,
                onValueChange = { blockDraft = it },
                label = { Text(stringResource(R.string.word)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            filterPrefs.addUserAppContentBlockWord(packageName, blockDraft)
                            blockDraft = ""
                            refreshTrigger++
                        },
                        enabled = blockDraft.trim().isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            )

            key(refreshTrigger) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                ) {
                    filterPrefs.getUserAppContentBlockWords(packageName).sorted().forEach { w ->
                        AssistChip(
                            onClick = {
                                filterPrefs.removeUserAppContentBlockWord(packageName, w)
                                refreshTrigger++
                            },
                            label = { Text(w) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.detail_allow_words_for_app),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = allowDraft,
                onValueChange = { allowDraft = it },
                label = { Text(stringResource(R.string.word)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            filterPrefs.addUserAppContentAllowWord(packageName, allowDraft)
                            allowDraft = ""
                            refreshTrigger++
                        },
                        enabled = allowDraft.trim().isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            )

            key(refreshTrigger) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                ) {
                    filterPrefs.getUserAppContentAllowWords(packageName).sorted().forEach { w ->
                        AssistChip(
                            onClick = {
                                filterPrefs.removeUserAppContentAllowWord(packageName, w)
                                refreshTrigger++
                            },
                            label = { Text(w) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
                            }
                        )
                    }
                }
            }

            notifications.forEach { notification ->
                NotificationRow(notification = notification)
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationRecord) {
    val context = LocalContext.current

    Column(
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
            }
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = notification.content.take(100).let { if (it.length < notification.content.length) "$it..." else it },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val sourceText = when {
            notification.isBlocked && notification.blockReason != null ->
                stringResource(
                    R.string.archive_source_blocked,
                    localizedBlockReason(notification.blockReason)
                )
            else ->
                stringResource(R.string.archive_source_notification)
        }
        Text(
            text = sourceText,
            style = MaterialTheme.typography.bodySmall,
            color = if (notification.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun localizedBlockReason(reason: String?): String {
    if (reason.isNullOrBlank()) return ""
    if (reason == "Gizle kaydet") return stringResource(R.string.reason_mute_and_save)
    return when {
        reason == "Emoji içeriyor" -> stringResource(R.string.reason_contains_emoji)
        reason == "Genel Odak Modu aktif" -> stringResource(R.string.reason_focus_mode_active)
        reason == "Odak Modu açık" -> stringResource(R.string.reason_focus_mode_active)
        reason == "Games pack active" -> stringResource(R.string.reason_games_pack_active)
        reason == "Oyun paketi aktif" -> stringResource(R.string.reason_games_pack_active)
        reason.startsWith("Manuel engellenen kanal:") ->
            stringResource(R.string.reason_manual_blocked_channel)
        reason.startsWith("Kanal ID:") ->
            stringResource(R.string.reason_channel_id)
        reason.startsWith("İçerik:") -> {
            val keyword = reason.substringAfter(":").trim().trim('"', '\'', '’')
            stringResource(R.string.reason_content, keyword)
        }
        else -> reason
    }
}
