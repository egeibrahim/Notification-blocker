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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.notifilter.BuildConfig
import com.notifilter.R
import com.notifilter.auth.SupabaseAuthManager
import com.notifilter.billing.BillingManager
import com.notifilter.sync.CloudSyncManager
import com.notifilter.ui.components.AppCard
import com.notifilter.util.NotificationAccessHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    modifier: Modifier = Modifier,
    onHelpClick: () -> Unit = {},
    onHowItWorksClick: () -> Unit = {},
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

    var userEmail by remember { mutableStateOf(SupabaseAuthManager.getUserEmail(context)) }
    var authEmailInput by remember { mutableStateOf("") }
    var authPasswordInput by remember { mutableStateOf("") }
    var authPasswordVisible by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(false) }
    var currentAuthTab by remember { mutableStateOf(0) }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        userEmail = withContext(Dispatchers.IO) {
            SupabaseAuthManager.refreshUserEmailIfNeeded(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val email = withContext(Dispatchers.IO) {
                        SupabaseAuthManager.refreshUserEmailIfNeeded(context)
                    }
                    userEmail = email
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
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
                        headlineContent = { Text(stringResource(R.string.settings_help)) },
                        leadingContent = { Icon(Icons.Default.Help, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onHelpClick() }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_how_it_works)) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onHowItWorksClick() }
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

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.subscription_info_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val statusText = when (entitlement) {
                        is BillingManager.EntitlementState.Active -> stringResource(R.string.subscription_status_active)
                        is BillingManager.EntitlementState.Inactive -> stringResource(R.string.subscription_status_inactive)
                        is BillingManager.EntitlementState.Error -> (entitlement as BillingManager.EntitlementState.Error).message
                        else -> stringResource(R.string.subscription_status_unknown)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (entitlement) {
                            is BillingManager.EntitlementState.Active -> MaterialTheme.colorScheme.primary
                            is BillingManager.EntitlementState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (BuildConfig.BILLING_PRODUCT_ID.isBlank()) {
                        Text(
                            text = stringResource(R.string.subscription_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (subscriptionInfo != null) {
                        val period = formatIsoPeriod(subscriptionInfo?.billingPeriod).orEmpty()
                        val trial = formatIsoPeriod(subscriptionInfo?.trialPeriod)
                        val infoText = if (trial != null) {
                            stringResource(
                                R.string.subscription_trial_desc,
                                trial,
                                stringResource(R.string.subscription_price_desc, subscriptionInfo?.formattedPrice ?: "", period)
                            )
                        } else {
                            stringResource(R.string.subscription_price_desc, subscriptionInfo?.formattedPrice ?: "", period)
                        }
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isEntitled) {
                        Button(
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity == null) {
                                    Toast.makeText(context, context.getString(R.string.error_billing_requires_activity), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (BuildConfig.BILLING_PRODUCT_ID.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_billing_not_configured), Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                billingManager.launchPurchaseFlow(activity)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.start_free_trial)) }
                    }
                    OutlinedButton(
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                Toast.makeText(context, context.getString(R.string.error_action_requires_activity), Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            billingManager.openManageSubscription(activity)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.manage_subscription)) }
                    if (isEntitled) {
                        OutlinedButton(
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity == null) {
                                    Toast.makeText(context, context.getString(R.string.error_action_requires_activity), Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                billingManager.openManageSubscription(activity)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.cancel_subscription)) }
                        Text(
                            text = stringResource(R.string.subscription_cancel_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Text(
                            text = stringResource(R.string.account),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (userEmail.isNullOrBlank()) {
                        Text(
                            text = when (currentAuthTab) {
                                0 -> stringResource(R.string.auth_login)
                                1 -> stringResource(R.string.auth_register)
                                else -> stringResource(R.string.auth_forgot_password)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = authEmailInput,
                            onValueChange = { authEmailInput = it },
                            label = { Text(stringResource(R.string.auth_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (currentAuthTab != 2) {
                            OutlinedTextField(
                                value = authPasswordInput,
                                onValueChange = { authPasswordInput = it },
                                label = { Text(stringResource(R.string.auth_password)) },
                                singleLine = true,
                                visualTransformation = if (authPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                trailingIcon = {
                                    Text(
                                        text = if (authPasswordVisible) stringResource(R.string.auth_password_hide) else stringResource(R.string.auth_password_show),
                                        modifier = Modifier
                                            .clickable { authPasswordVisible = !authPasswordVisible }
                                            .padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (authLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
                        } else {
                            Button(
                                onClick = {
                                    val email = authEmailInput.trim()
                                    if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                        Toast.makeText(context, context.getString(R.string.auth_invalid_email), Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (currentAuthTab != 2 && authPasswordInput.length < 6) {
                                        Toast.makeText(context, context.getString(R.string.auth_invalid_password), Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        authLoading = true
                                        val res = when (currentAuthTab) {
                                            0 -> SupabaseAuthManager.signInWithEmail(context, email, authPasswordInput)
                                            1 -> SupabaseAuthManager.signUpWithEmail(context, email, authPasswordInput)
                                            else -> SupabaseAuthManager.resetPassword(context, email)
                                        }
                                        authLoading = false
                                        if (res.success) {
                                            val successMsg = when (currentAuthTab) {
                                                0 -> "Giriş başarılı!"
                                                1 -> res.errorMessage ?: context.getString(R.string.auth_success_signup)
                                                else -> context.getString(R.string.auth_success_recovery)
                                            }
                                            Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                                            userEmail = SupabaseAuthManager.getUserEmail(context)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.error_generic, res.errorMessage), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when (currentAuthTab) {
                                        0 -> stringResource(R.string.auth_login)
                                        1 -> stringResource(R.string.auth_register)
                                        else -> stringResource(R.string.auth_send_recovery)
                                    }
                                )
                            }
                        }
                        when (currentAuthTab) {
                            0 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { currentAuthTab = 2 }) {
                                        Text(stringResource(R.string.auth_forgot_password))
                                    }
                                    TextButton(onClick = { currentAuthTab = 1 }) {
                                        Text(stringResource(R.string.auth_no_account))
                                    }
                                }
                            }
                            1 -> TextButton(onClick = { currentAuthTab = 0 }) {
                                Text(stringResource(R.string.auth_have_account))
                            }
                            else -> TextButton(onClick = { currentAuthTab = 0 }) {
                                Text(stringResource(R.string.auth_back_to_login))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Divider(modifier = Modifier.weight(1f))
                            Text(
                                text = "  ${stringResource(R.string.auth_or_divider)}  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider(modifier = Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = {
                                if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_auth_not_configured), Toast.LENGTH_LONG).show()
                                    return@OutlinedButton
                                }
                                SupabaseAuthManager.signInWithGoogle(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.sign_in_with_google)) }
                    } else {
                        Text(
                            text = "${stringResource(R.string.signed_in_as)}: ${userEmail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = runCatching { CloudSyncManager.backupNow(context) }
                                    Toast.makeText(
                                        context,
                                        if (res.isSuccess) "Backup completed" else "Backup failed: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.backup_now)) }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = runCatching { CloudSyncManager.restoreNow(context) }
                                    Toast.makeText(
                                        context,
                                        if (res.isSuccess) "Restore completed" else "Restore failed: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.restore_now)) }
                        OutlinedButton(
                            onClick = {
                                SupabaseAuthManager.signOut(context)
                                userEmail = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.logout)) }
                    }
                }
            }
        }


        item {
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

        item {
            Text(
                text = stringResource(R.string.settings_setup_header),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
