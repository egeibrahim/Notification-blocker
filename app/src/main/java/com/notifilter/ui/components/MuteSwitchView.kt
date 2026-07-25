package com.notifilter.ui.components

import android.widget.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Native Android Switch - Compose'da touch event sorunlarını aşmak için.
 */
@Composable
fun MuteSwitchView(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            Switch(context).apply {
                setOnCheckedChangeListener { _, _ -> onCheckedChange() }
            }
        },
        update = { switch ->
            if (switch.isChecked != checked) {
                switch.setOnCheckedChangeListener(null)
                switch.isChecked = checked
                switch.setOnCheckedChangeListener { _, _ -> onCheckedChange() }
            }
        },
        modifier = modifier
    )
}
