package com.notifilter

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.notifilter.auth.SupabaseAuthManager
import com.notifilter.preferences.FilterRulesPreferences
import com.notifilter.sync.CloudSyncManager
import com.notifilter.util.NotificationAccessHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.notifilter.billing.BillingManager
import com.notifilter.billing.EntitlementStore
import com.notifilter.preferences.FocusModePreferences
import com.notifilter.preferences.ImportantChannelsPreferences
import com.notifilter.ui.pages.ArchivePage
import com.notifilter.ui.pages.BlacklistPage
import com.notifilter.ui.pages.DashboardPage
import com.notifilter.ui.pages.HelpFaqPage
import com.notifilter.ui.pages.ProfilePage
import com.notifilter.ui.theme.NotifilterTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SupabaseAuthManager.handleRedirect(this, intent)

        setContent {
            NotifilterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenNotificationAccess = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        SupabaseAuthManager.handleRedirect(this, intent)
    }
}

enum class NavPage(@StringRes val titleRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Icons.Default.Dashboard),
    Archive(R.string.nav_archive, Icons.Default.Archive),
    Blacklist(R.string.nav_block_management, Icons.Default.Block),
    Profile(R.string.nav_profile, Icons.Default.Person)
}

enum class ProfileSubPage {
    Faq
}

@Composable
fun MainScreen(
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationAccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusPrefs = remember { FocusModePreferences(context) }
    val importantPrefs = remember { ImportantChannelsPreferences(context) }
    var currentPage by remember { mutableIntStateOf(0) }
    var currentSubPage by remember { mutableStateOf<ProfileSubPage?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("notifilter_walkthrough", android.content.Context.MODE_PRIVATE) }
    var showWalkthrough by remember { mutableStateOf(sharedPrefs.getBoolean("show_walkthrough_v1", true)) }

    val filterPrefs = remember { FilterRulesPreferences(context) }
    var isCountrySelected by remember { mutableStateOf(filterPrefs.hasSelectedCountry) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && SupabaseAuthManager.getAccessToken(context) != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { CloudSyncManager.backupNow(context) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val billingManager = remember {
        (context.applicationContext as NotifilterApplication).billingManager
    }
    val entitlement by billingManager.entitlement.collectAsState(initial = BillingManager.EntitlementState.Unknown)
    val isEntitled = (entitlement is BillingManager.EntitlementState.Active) || EntitlementStore.isEntitled(context)

    if (!isCountrySelected) {
        com.notifilter.ui.components.LanguagePackSelectionPage(
            onDone = {
                isCountrySelected = true
            }
        )
        return
    }

    if (currentSubPage != null) {
        when (currentSubPage) {
            ProfileSubPage.Faq -> HelpFaqPage(onBackClick = { currentSubPage = null })
            else -> Unit
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(12.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), clip = true),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavPage.entries.forEachIndexed { index, page ->
                    val title = stringResource(page.titleRes)
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick = { currentPage = index },
                        icon = { Icon(page.icon, contentDescription = title) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF6B6B),
                            selectedTextColor = Color(0xFFFF6B6B),
                            indicatorColor = Color(0xFFFFE6E6),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentPage) {
                0 -> DashboardPage(
                    focusPrefs = focusPrefs,
                    importantPrefs = importantPrefs,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> ArchivePage(
                    modifier = Modifier.fillMaxSize()
                )
                2 -> BlacklistPage(
                    modifier = Modifier.fillMaxSize()
                )
                3 -> ProfilePage(
                    modifier = Modifier.fillMaxSize(),
                    onFaqClick = { currentSubPage = ProfileSubPage.Faq }
                )
            }

            if (!isEntitled && currentPage != 3) {
                SubscriptionPromptCard(
                    onStartTrial = {
                        billingManager.launchPurchaseFlow(context as MainActivity)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }
        }

        if (showWalkthrough) {
            com.notifilter.ui.components.WalkthroughDialog(
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenBatterySettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                },
                onOpenAutostartSettings = {
                    context.getSharedPreferences("setup_flags", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("visited_autostart", true).apply()
                    val manufacturer = Build.MANUFACTURER.lowercase()
                    val candidates = when {
                        manufacturer.contains("xiaomi") -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
                        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                        manufacturer.contains("oppo") -> listOf("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                        manufacturer.contains("vivo") -> listOf("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                        manufacturer.contains("samsung") -> listOf("com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity")
                        manufacturer.contains("asus") -> listOf("com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity")
                        else -> emptyList()
                    }
                    var opened = false
                    for ((pkg, cls) in candidates) {
                        val intent = Intent().apply {
                            component = android.content.ComponentName(pkg, cls)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (runCatching { context.startActivity(intent) }.isSuccess) { opened = true; break }
                    }
                    if (!opened) {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback)
                    }
                },
                onOpenRestrictedSettings = {
                    context.getSharedPreferences("setup_flags", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("visited_restricted", true).apply()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                onDismiss = {
                    sharedPrefs.edit().putBoolean("show_walkthrough_v1", false).apply()
                    showWalkthrough = false
                }
            )
        }
    }
}

@Composable
private fun SubscriptionPromptCard(
    onStartTrial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.subscription_required_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.subscription_required_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStartTrial,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.start_free_trial))
            }
        }
    }
}
