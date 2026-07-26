package com.notifilter.ui.pages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notifilter.BuildConfig
import com.notifilter.R
import com.notifilter.auth.SupabaseAuthManager
import com.notifilter.billing.BillingManager
import com.notifilter.sync.CloudSyncManager
import com.notifilter.ui.components.AppCard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.notifilter.util.NotificationAccessHelper
import kotlinx.coroutines.launch

private fun checkBatteryIgnored(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
        true
    }
}

private fun openAutostartSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val candidates = when {
        manufacturer.contains("xiaomi") -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
        manufacturer.contains("oppo") -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
        )
        manufacturer.contains("vivo") -> listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        )
        manufacturer.contains("samsung") -> listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        manufacturer.contains("asus") -> listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
        )
        else -> emptyList()
    }

    for ((pkg, cls) in candidates) {
        val intent = Intent().apply {
            component = android.content.ComponentName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = runCatching { context.startActivity(intent) }.isSuccess
        if (resolved) return
    }

    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallback)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    modifier: Modifier = Modifier,
    onFaqClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val billingManager = remember { BillingManager(context.applicationContext) }
    DisposableEffect(Unit) {
        billingManager.start()
        onDispose { billingManager.stop() }
    }
    val entitlement by billingManager.entitlement.collectAsState(initial = BillingManager.EntitlementState.Unknown)
    val isEntitled = entitlement is BillingManager.EntitlementState.Active
    val subscriptionInfo by billingManager.subscriptionInfo.collectAsState(initial = null)

    val userEmail by SupabaseAuthManager.userEmailFlow.collectAsState(initial = SupabaseAuthManager.getUserEmail(context))

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }

    var hasNotificationAccess by remember { mutableStateOf(NotificationAccessHelper.isNotificationAccessEnabled(context)) }
    var postNotificationsGranted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var isBatteryIgnored by remember { mutableStateOf(checkBatteryIgnored(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        postNotificationsGranted = isGranted
    }

    fun refreshPermissionStates() {
        hasNotificationAccess = NotificationAccessHelper.isNotificationAccessEnabled(context)
        postNotificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        isBatteryIgnored = checkBatteryIgnored(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun formatIsoPeriod(isoPeriod: String?): String? {
        if (isoPeriod.isNullOrBlank()) return null
        val match = Regex("P(\\d+)([YMWD])").find(isoPeriod) ?: return isoPeriod
        val count = match.groupValues[1].toIntOrNull() ?: 1
        val unit = when (match.groupValues[2]) {
            "D" -> if (count == 1) "day" else "days"
            "W" -> if (count == 1) "week" else "weeks"
            "M" -> if (count == 1) "month" else "months"
            "Y" -> if (count == 1) "year" else "years"
            else -> ""
        }
        return "$count $unit"
    }

    fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun startActivityOrAppDetails(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.getOrElse { openAppDetails() }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                val displayName = userEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.account)
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = userEmail ?: stringResource(R.string.auth_google_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (userEmail.isNullOrBlank()) {
                    Button(
                        onClick = {
                            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.error_auth_not_configured), Toast.LENGTH_LONG).show()
                            } else {
                                SupabaseAuthManager.signInWithGoogle(context)
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.sign_in_with_google))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF7B61FF), Color(0xFFC7B9FF))
                            )
                        )
                        .clickable {
                            val pkg = context.packageName
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(marketIntent) }.getOrElse {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(webIntent) }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.rate_app_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.rate_app_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_setup_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_setup_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.wt_permission_title)) },
                        supportingContent = {
                            Text(
                                text = if (postNotificationsGranted) stringResource(R.string.notification_access_on) else stringResource(R.string.notification_access_off),
                                color = if (postNotificationsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = {
                            if (postNotificationsGranted) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                            } else {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { openAppNotificationSettings() }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.notification_access_manage)) },
                        supportingContent = {
                            Text(
                                text = if (hasNotificationAccess) stringResource(R.string.notification_access_on) else stringResource(R.string.notification_access_off),
                                color = if (hasNotificationAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = {
                            if (hasNotificationAccess) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                            } else {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable {
                            startActivityOrAppDetails(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quick_access_disable_battery_optimization)) },
                        supportingContent = {
                            Text(
                                text = if (isBatteryIgnored) stringResource(R.string.battery_optimization_unrestricted) else stringResource(R.string.battery_optimization_restricted),
                                color = if (isBatteryIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = {
                            if (isBatteryIgnored) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                            } else {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryIgnored) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                startActivityOrAppDetails(intent)
                            } else {
                                openAppDetails()
                            }
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quick_access_autostart)) },
                        supportingContent = { Text(stringResource(R.string.autostart_desc)) },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { openAutostartSettings(context) }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quick_access_app_info)) },
                        supportingContent = { Text(stringResource(R.string.quick_access_app_info_desc)) },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { openAppDetails() }
                    )
                }
            }
        }

        item {
            val statusText = when (entitlement) {
                is BillingManager.EntitlementState.Active -> stringResource(R.string.subscription_status_active)
                is BillingManager.EntitlementState.Inactive -> stringResource(R.string.subscription_status_inactive)
                is BillingManager.EntitlementState.Error -> (entitlement as BillingManager.EntitlementState.Error).message
                else -> stringResource(R.string.subscription_status_unknown)
            }
            var showSignOutConfirm by remember { mutableStateOf(false) }

            if (showSignOutConfirm) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirm = false },
                    title = { Text(stringResource(R.string.logout)) },
                    text = { Text(stringResource(R.string.sign_out_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showSignOutConfirm = false
                            scope.launch {
                                runCatching { CloudSyncManager.backupNow(context) }
                                SupabaseAuthManager.signOut(context)
                            }
                        }) {
                            Text(stringResource(R.string.logout))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirm = false }) {
                            Text(stringResource(R.string.feedback_dialog_cancel))
                        }
                    }
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.account)) },
                        supportingContent = {
                            if (!userEmail.isNullOrBlank()) {
                                Text(userEmail ?: "")
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingContent = {
                            if (userEmail.isNullOrBlank()) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            } else {
                                Text(
                                    text = stringResource(R.string.logout),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            if (userEmail.isNullOrBlank()) {
                                if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_auth_not_configured), Toast.LENGTH_LONG).show()
                                } else {
                                    SupabaseAuthManager.signInWithGoogle(context)
                                }
                            } else {
                                showSignOutConfirm = true
                            }
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.title_help_faq)) },
                        leadingContent = { Icon(Icons.Default.Feedback, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onFaqClick() }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.manage_subscription)) },
                        supportingContent = { Text(statusText) },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                Toast.makeText(context, context.getString(R.string.error_action_requires_activity), Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            billingManager.openManageSubscription(activity)
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.feedback_title)) },
                        leadingContent = { Icon(Icons.Default.Feedback, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { showFeedbackDialog = true }
                    )
                }
            }
        }
    }


    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(stringResource(R.string.feedback_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    label = { Text(stringResource(R.string.feedback_dialog_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (feedbackText.trim().isBlank()) {
                        Toast.makeText(context, context.getString(R.string.feedback_empty_error), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("ibrahimege.dev@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "NO App Feedback")
                        putExtra(Intent.EXTRA_TEXT, feedbackText.trim())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching {
                        context.startActivity(emailIntent)
                        Toast.makeText(context, context.getString(R.string.feedback_success), Toast.LENGTH_LONG).show()
                    }.getOrElse {
                        Toast.makeText(context, context.getString(R.string.error_no_email_app), Toast.LENGTH_LONG).show()
                    }
                    feedbackText = ""
                    showFeedbackDialog = false
                }) { Text(stringResource(R.string.feedback_dialog_send)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    feedbackText = ""
                    showFeedbackDialog = false
                }) { Text(stringResource(R.string.feedback_dialog_cancel)) }
            }
        )
    }
}
