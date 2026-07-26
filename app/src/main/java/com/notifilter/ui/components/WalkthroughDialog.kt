package com.notifilter.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notifilter.R

@Composable
fun WalkthroughDialog(
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    var currentSlide by remember { mutableIntStateOf(0) }
    val totalSlides = 5

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            // Close / Skip top button
            if (currentSlide < totalSlides - 1) {
                Text(
                    text = stringResource(R.string.wt_skip),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
            }

            // Slide Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Giant Graphic / Emoji representation
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (currentSlide) {
                            0 -> "👋"
                            1 -> "�"
                            2 -> "👁"
                            3 -> "�🛡️"
                            else -> "🧘"
                        },
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                // Slide Title
                Text(
                    text = when (currentSlide) {
                        0 -> stringResource(R.string.wt_welcome_title)
                        1 -> stringResource(R.string.wt_permission_title)
                        2 -> stringResource(R.string.wt_access_title)
                        3 -> stringResource(R.string.wt_filter_title)
                        else -> stringResource(R.string.wt_focus_title)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                // Slide Description
                Text(
                    text = when (currentSlide) {
                        0 -> stringResource(R.string.wt_welcome_desc)
                        1 -> stringResource(R.string.wt_permission_desc)
                        2 -> stringResource(R.string.wt_access_desc)
                        3 -> stringResource(R.string.wt_filter_desc)
                        else -> stringResource(R.string.wt_focus_desc)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Bottom Actions and Slide Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Dot Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until totalSlides) {
                        Box(
                            modifier = Modifier
                                .size(if (i == currentSlide) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == currentSlide) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Next or Finish Button
                Button(
                    onClick = {
                        when (currentSlide) {
                            1 -> onRequestNotificationPermission()
                            2 -> onOpenNotificationAccess()
                        }
                        if (currentSlide < totalSlides - 1) {
                            currentSlide++
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when (currentSlide) {
                            1 -> stringResource(R.string.wt_permission_button)
                            2 -> stringResource(R.string.wt_access_button)
                            totalSlides - 1 -> stringResource(R.string.wt_finish)
                            else -> stringResource(R.string.wt_next)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
