package com.notifilter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tüm sayfa kartları için tek referans nokta.
 *
 * Neden: MaterialTheme.colorScheme.surface + tonalElevation birlikte kullanıldığında
 * her kart "primary" renginin bir tonuyla boyanıyor. Elevation aynı olduğu için
 * (defaultElevation = 2.dp) TÜM kartlar aynı mor tonuna boyanıyor ve sayfa
 * tek renk gibi görünüyor. Bu bileşen elevation yerine ince border kullanır,
 * container rengini açıkça beyaza sabitler.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(content = content)
    }
}
