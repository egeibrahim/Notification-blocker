package com.notifilter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.notifilter.R

/**
 * Onboarding'de bir kez gösterilen ülke seçim ekranı.
 *
 * Telefonun sistem diline değil, kullanıcının beyan ettiği ülkeye göre
 * hangi blok kelime paketlerinin (TR/EN) aktif olacağını belirler.
 * Bildirim dili telefonun sistem dilinden değil, bildirimi gönderen
 * uygulamanın sunucu kararından belirlenir — bu yüzden Türkiye'de yaşayan,
 * telefonu İngilizce olan biri bile hem Türkçe hem İngilizce bildirim alabilir.
 */
@Composable
fun CountrySelectionPage(
    onCountrySelected: (isTurkey: Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.country_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.country_selection_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onCountrySelected(true) }
                    ) {
                        Text(stringResource(R.string.country_selection_turkey))
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        onClick = { onCountrySelected(false) }
                    ) {
                        Text(stringResource(R.string.country_selection_other))
                    }
                }
            }

            Text(
                text = stringResource(R.string.country_selection_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}
