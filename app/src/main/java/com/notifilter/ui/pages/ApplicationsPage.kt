package com.notifilter.ui.pages

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notifilter.R
import com.notifilter.NotifilterApplication
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.preferences.ImportantChannelsPreferences
import com.notifilter.ui.components.AppIcon
import com.notifilter.ui.components.MuteSwitchView
import com.notifilter.ui.components.NotificationDetailSheet
import com.notifilter.util.AppCategoryUtil
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class AppItem(
    val packageName: String,
    val appName: String,
    val categoryLabel: String,
    val totalCount: Int,
    val blockedCount: Int,
    val isSystem: Boolean
)

private data class InstalledApp(
    val packageName: String,
    val appName: String,
    val categoryLabel: String,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsPage(
    importantPrefs: ImportantChannelsPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotifilterApplication
    val dao = app.database.notificationRecordDao()

    val scope = rememberCoroutineScope()

    var appItems by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var whitelistVersion by remember { mutableStateOf(0) }
    var selectedPackage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sheetNotifications by remember { mutableStateOf<List<NotificationRecord>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val since = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val installed = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val result = LinkedHashMap<String, InstalledApp>()

            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .forEach { ai ->
                    val pkg = ai.packageName
                    val name = ai.loadLabel(pm).toString()
                    val category = AppCategoryUtil.getCategoryLabel(context, ai)
                    val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    result[pkg] = InstalledApp(pkg, name, category, isSystem)
                }

            val launcherResolveInfos = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(
                        (PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS)
            }

            launcherResolveInfos
                .asSequence()
                .mapNotNull { it.activityInfo?.applicationInfo }
                .filter { it.packageName != context.packageName }
                .forEach { ai ->
                    val pkg = ai.packageName
                    if (pkg in result) return@forEach
                    val name = ai.loadLabel(pm).toString()
                    val category = AppCategoryUtil.getCategoryLabel(context, ai)
                    val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    result[pkg] = InstalledApp(pkg, name, category, isSystem)
                }

            result.values.sortedBy { it.appName.lowercase() }
        }
        dao.getNotificationStatsSince(since).collect { stats ->
            val statsMap = stats.associateBy { it.packageName }
            val full = installed.map { installedApp ->
                val s = statsMap[installedApp.packageName]
                AppItem(
                    packageName = installedApp.packageName,
                    appName = installedApp.appName,
                    categoryLabel = installedApp.categoryLabel,
                    totalCount = s?.totalCount ?: 0,
                    blockedCount = s?.blockedCount ?: 0,
                    isSystem = installedApp.isSystem
                )
            }

            appItems = full
                .sortedWith(compareByDescending<AppItem> { it.blockedCount }.thenBy { it.appName.lowercase() })
        }
    }

    var sheetRefreshTrigger by remember { mutableStateOf(0) }
    selectedPackage?.let { (pkg, appName) ->
        LaunchedEffect(pkg, sheetRefreshTrigger) {
            dao.getByPackageSince(pkg, since).collect { sheetNotifications = it }
        }
        NotificationDetailSheet(
            appName = appName,
            packageName = pkg,
            notifications = sheetNotifications,
            importantPrefs = importantPrefs,
            sheetState = sheetState,
            onDismiss = { selectedPackage = null },
            onChannelMarkedSafe = { whitelistVersion++ },
            onWhitelistAdded = null,
            onBlockWordAdded = { sheetRefreshTrigger++ }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.applications_installed_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = stringResource(R.string.applications_installed_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true
        )

        val filteredItems = remember(appItems, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isBlank()) return@remember appItems
            appItems.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        val listState = remember { LazyListState() }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = filteredItems,
                key = { _, it -> "${it.packageName}-${importantPrefs.isWhitelisted(it.packageName)}" }
            ) { index, item ->
                AppStatsRow(
                    item = item,
                    isMuted = importantPrefs.isWhitelisted(item.packageName),
                    onMuteToggle = {
                        if (importantPrefs.isWhitelisted(item.packageName)) {
                            importantPrefs.removeFromWhitelist(item.packageName)
                        } else {
                            importantPrefs.addToWhitelist(item.packageName)
                        }
                        whitelistVersion++
                    },
                    onClick = {
                        scope.launch {
                            runCatching {
                                listState.animateScrollToItem(index)
                            }
                            selectedPackage = item.packageName to item.appName
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppStatsRow(
    item: AppItem,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val blockRate = if (item.totalCount > 0) item.blockedCount.toFloat() / item.totalCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = item.packageName)
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                Text(
                    text = item.appName.ifBlank { item.packageName },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = item.categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.categoryLabel != "—") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Toplam: ${item.totalCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Blok: ${item.blockedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LinearProgressIndicator(
                    progress = blockRate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dashboard_mute_save_label),
                    style = MaterialTheme.typography.labelSmall
                )
                MuteSwitchView(
                    checked = isMuted,
                    onCheckedChange = onMuteToggle
                )
            }
        }
    }
}
