package com.notifilter.ui.pages

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notifilter.BuildConfig
import com.notifilter.R
import com.notifilter.auth.SupabaseAuthManager
import com.notifilter.billing.BillingManager
import com.notifilter.sync.CloudSyncManager
import com.notifilter.ui.components.AppCard
import com.notifilter.util.NotificationAccessHelper
import kotlinx.coroutines.launch

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
    var selectedTab by remember { mutableIntStateOf(0) }

    val hasNotificationAccess = NotificationAccessHelper.isNotificationAccessEnabled(context)

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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.account), maxLines = 1) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.subscription_info_title), maxLines = 1) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.section_quick_access), maxLines = 1) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(stringResource(R.string.settings_info_title), maxLines = 1) })
            }
        }

        if (selectedTab == 3) item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_info_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_faq)) },
                        leadingContent = { Icon(Icons.Default.Feedback, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onFaqClick() }
                    )
                }
            }
        }

        if (selectedTab == 1) item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.subscription_info_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                    val statusText = when (entitlement) {
                        is BillingManager.EntitlementState.Active -> stringResource(R.string.subscription_status_active)
                        is BillingManager.EntitlementState.Inactive -> stringResource(R.string.subscription_status_inactive)
                        is BillingManager.EntitlementState.Error -> (entitlement as BillingManager.EntitlementState.Error).message
                        else -> stringResource(R.string.subscription_status_unknown)
                    }
                    val supportingText = if (BuildConfig.BILLING_PRODUCT_ID.isBlank()) {
                        stringResource(R.string.subscription_not_configured)
                    } else if (subscriptionInfo != null) {
                        val period = formatIsoPeriod(subscriptionInfo?.billingPeriod).orEmpty()
                        val trial = formatIsoPeriod(subscriptionInfo?.trialPeriod)
                        if (trial != null) {
                            stringResource(
                                R.string.subscription_trial_desc,
                                trial,
                                stringResource(R.string.subscription_price_desc, subscriptionInfo?.formattedPrice ?: "", period)
                            )
                        } else {
                            stringResource(R.string.subscription_price_desc, subscriptionInfo?.formattedPrice ?: "", period)
                        }
                    } else {
                        ""
                    }
                    ListItem(
                        headlineContent = { Text(statusText) },
                        supportingContent = { if (supportingText.isNotBlank()) Text(supportingText) },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                    if (!isEntitled && BuildConfig.BILLING_PRODUCT_ID.isNotBlank()) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.start_free_trial)) },
                            leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                val activity = context as? android.app.Activity
                                if (activity == null) {
                                    Toast.makeText(context, context.getString(R.string.error_billing_requires_activity), Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                if (BuildConfig.BILLING_PRODUCT_ID.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_billing_not_configured), Toast.LENGTH_LONG).show()
                                    return@clickable
                                }
                                billingManager.launchPurchaseFlow(activity)
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.manage_subscription)) },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
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
                    if (isEntitled) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.cancel_subscription)) },
                            supportingContent = { Text(stringResource(R.string.subscription_cancel_desc)) },
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
                    }
                }
            }
        }

        if (selectedTab == 0) item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.account),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                    if (userEmail.isNullOrBlank()) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.sign_in_with_google)) },
                            supportingContent = { Text(stringResource(R.string.auth_google_explanation)) },
                            leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_auth_not_configured), Toast.LENGTH_LONG).show()
                                } else {
                                    SupabaseAuthManager.signInWithGoogle(context)
                                }
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.signed_in_as)) },
                            supportingContent = { Text(userEmail.orEmpty()) },
                            leadingContent = { Icon(Icons.Default.Email, contentDescription = null) }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.backup_now)) },
                            leadingContent = { Icon(Icons.Default.Save, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val res = runCatching { CloudSyncManager.backupNow(context) }
                                    Toast.makeText(
                                        context,
                                        if (res.isSuccess) "Backup completed" else "Backup failed: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.restore_now)) },
                            leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val res = runCatching { CloudSyncManager.restoreNow(context) }
                                    Toast.makeText(
                                        context,
                                        if (res.isSuccess) "Restore completed" else "Restore failed: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.logout)) },
                            leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                SupabaseAuthManager.signOut(context)
                            }
                        )
                    }
                }
            }
        }


        if (selectedTab == 3) item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.rate_app_title)) },
                        supportingContent = { Text(stringResource(R.string.rate_app_desc)) },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
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
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.feedback_title)) },
                        supportingContent = { Text(stringResource(R.string.feedback_desc)) },
                        leadingContent = { Icon(Icons.Default.Feedback, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { showFeedbackDialog = true }
                    )
                }
            }
        }

        // setup header moved inside the card below

        if (selectedTab == 2) item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_setup_header),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.notification_access_manage)) },
                        supportingContent = {
                            Text(
                                text = if (hasNotificationAccess) stringResource(R.string.notification_access_action) else stringResource(R.string.notification_access_off),
                                color = if (hasNotificationAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            startActivityOrAppDetails(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quick_access_app_info)) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { openAppDetails() }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quick_access_autostart)) },
                        leadingContent = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val autostartIntent = Intent().apply {
                                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                            }
                            startActivityOrAppDetails(autostartIntent)
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.quick_access_disable_battery_optimization)) },
                            leadingContent = { Icon(Icons.Default.BatteryFull, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable {
                                startActivityOrAppDetails(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        )
                    }
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
