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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.notifilter.util.NotificationAccessHelper
import com.notifilter.billing.BillingManager
import com.notifilter.billing.EntitlementStore
import com.notifilter.preferences.FocusModePreferences
import com.notifilter.preferences.ImportantChannelsPreferences
import com.notifilter.ui.pages.ArchivePage
import com.notifilter.ui.pages.BlacklistPage
import com.notifilter.ui.pages.DashboardPage
import com.notifilter.ui.pages.FaqPage
import com.notifilter.ui.pages.HelpPage
import com.notifilter.ui.pages.HowItWorksPage
import com.notifilter.ui.pages.ProfilePage
import com.notifilter.ui.theme.NotifilterTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SupabaseAuthManager.handleRedirect(this, intent)

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            NotifilterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
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
    Help,
    HowItWorks,
    Faq
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusPrefs = remember { FocusModePreferences(context) }
    val importantPrefs = remember { ImportantChannelsPreferences(context) }
    var currentPage by remember { mutableIntStateOf(0) }
    var currentSubPage by remember { mutableStateOf<ProfileSubPage?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("notifilter_walkthrough", android.content.Context.MODE_PRIVATE) }
    var showWalkthrough by remember { mutableStateOf(sharedPrefs.getBoolean("show_walkthrough_v1", true)) }
    var hasAutoOpenedNotification by remember { mutableStateOf(sharedPrefs.getBoolean("auto_open_notification_settings", false)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (showWalkthrough) {
                    // Keep permission dialog hidden so onboarding stays visible
                    showPermissionDialog = false
                } else if (!NotificationAccessHelper.isNotificationAccessEnabled(context)) {
                    showPermissionDialog = true
                } else {
                    showPermissionDialog = false
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

    if (currentSubPage != null) {
        when (currentSubPage) {
            ProfileSubPage.Help -> HelpPage(onBackClick = { currentSubPage = null })
            ProfileSubPage.HowItWorks -> HowItWorksPage(onBackClick = { currentSubPage = null })
            ProfileSubPage.Faq -> FaqPage(onBackClick = { currentSubPage = null })
            else -> Unit
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavPage.entries.forEachIndexed { index, page ->
                    val title = stringResource(page.titleRes)
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick = { currentPage = index },
                        icon = { Icon(page.icon, contentDescription = title) },
                        label = { Text(title) }
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
                    onHelpClick = { currentSubPage = ProfileSubPage.Help },
                    onHowItWorksClick = { currentSubPage = ProfileSubPage.HowItWorks },
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

        if (showPermissionDialog) {
            SetupGuideDialog(
                onDismiss = { showPermissionDialog = false },
                onOpenNotificationSettings = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                onOpenBattery = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }

        if (showWalkthrough) {
            com.notifilter.ui.components.WalkthroughDialog(
                onDismiss = {
                    sharedPrefs.edit().putBoolean("show_walkthrough_v1", false).apply()
                    showWalkthrough = false
                    if (!NotificationAccessHelper.isNotificationAccessEnabled(context)) {
                        if (!hasAutoOpenedNotification) {
                            sharedPrefs.edit().putBoolean("auto_open_notification_settings", true).apply()
                            hasAutoOpenedNotification = true
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        showPermissionDialog = true
                    }
                }
            )
        }
    }
}

@Composable
private fun SetupGuideDialog(
    onDismiss: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBattery: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_guide_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.setup_guide_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_guide_notifications))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_guide_battery))
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_guide_done))
                }
            }
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
