package com.notifilter.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.core.graphics.drawable.toBitmap
import com.notifilter.R

private data class WalkthroughSlide(
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val titleRes: Int,
    val descRes: Int
)

@Composable
fun WalkthroughDialog(
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val slides = remember {
        listOf(
            WalkthroughSlide(null, R.string.wt_welcome_title, R.string.wt_welcome_desc),
            WalkthroughSlide(Icons.Filled.Notifications, R.string.wt_permission_title, R.string.wt_permission_desc),
            WalkthroughSlide(Icons.Filled.Visibility, R.string.wt_access_title, R.string.wt_access_desc),
            WalkthroughSlide(Icons.Filled.FilterList, R.string.wt_filter_title, R.string.wt_filter_desc),
            WalkthroughSlide(Icons.Filled.CenterFocusStrong, R.string.wt_focus_title, R.string.wt_focus_desc),
            WalkthroughSlide(Icons.Filled.Apps, R.string.wt_app_words_title, R.string.wt_app_words_desc)
        )
    }
    val totalSlides = slides.size
    var currentSlide by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    // painterResource can't load the adaptive-icon XML used on API 26+, so render the
    // real (already-composited) app icon bitmap via PackageManager instead.
    val appIconPainter = remember {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = drawable.toBitmap(width = 192, height = 192)
            BitmapPainter(bitmap.asImageBitmap())
        }.getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            // Slide Content (scrollable so nothing pushes the buttons off-screen)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val slide = slides[currentSlide]

                if (currentSlide == 0) {
                    if (appIconPainter != null) {
                        Image(
                            painter = appIconPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wt_welcome_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon ?: Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text(
                    text = stringResource(slide.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(slide.descRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Dot Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalSlides) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == currentSlide) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == currentSlide) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Back / Next Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentSlide > 0) {
                    OutlinedButton(
                        onClick = { currentSlide-- },
                        modifier = Modifier
                            .weight(0.4f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBackIosNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.wt_back),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
                        .weight(1f)
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
