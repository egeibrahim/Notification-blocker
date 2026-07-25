package com.notifilter.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.notifilter.R
import com.notifilter.preferences.FilterRulesPreferences

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlacklistPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val filterPrefs = remember { FilterRulesPreferences(context) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var customWord by remember { mutableStateOf("") }
    var allowWord by remember { mutableStateOf("") }
    var categoryWordDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val sharedPrefs = remember { context.getSharedPreferences("notifilter_walkthrough", android.content.Context.MODE_PRIVATE) }
    var showRulesTooltip by remember { mutableStateOf(sharedPrefs.getBoolean("show_tooltip_rules", true)) }

    var languagePacks by remember { mutableStateOf(filterPrefs.enabledLanguagePacks) }
    LaunchedEffect(languagePacks) {
        filterPrefs.enabledLanguagePacks = languagePacks
        refreshTrigger++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.title_block_management),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        if (showRulesTooltip) {
            com.notifilter.ui.components.TooltipCard(
                message = stringResource(R.string.wt_tooltip_rules),
                onDismiss = {
                    sharedPrefs.edit().putBoolean("show_tooltip_rules", false).apply()
                    showRulesTooltip = false
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.language_pack),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val trSelected = FilterRulesPreferences.PACK_TR in languagePacks
                    val enSelected = FilterRulesPreferences.PACK_EN in languagePacks

                    FilterChip(
                        selected = trSelected,
                        onClick = {
                            val next = if (trSelected) {
                                languagePacks - FilterRulesPreferences.PACK_TR
                            } else {
                                languagePacks + FilterRulesPreferences.PACK_TR
                            }
                            if (next.isNotEmpty()) languagePacks = next
                        },
                        label = { Text("TR") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = enSelected,
                        onClick = {
                            val next = if (enSelected) {
                                languagePacks - FilterRulesPreferences.PACK_EN
                            } else {
                                languagePacks + FilterRulesPreferences.PACK_EN
                            }
                            if (next.isNotEmpty()) languagePacks = next
                        },
                        label = { Text("EN") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                val gamesEnabled = remember(refreshTrigger) { filterPrefs.isGlobalGamesBlockEnabled }
                FilterChip(
                    selected = gamesEnabled,
                    onClick = {
                        filterPrefs.isGlobalGamesBlockEnabled = !gamesEnabled
                        refreshTrigger++
                    },
                    label = { Text("Games") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        key(refreshTrigger) {
            val categories = filterPrefs.getMergedBlockCategories()

            var globalEmojiBlock by remember { mutableStateOf(filterPrefs.isGlobalEmojiBlockEnabled) }

            val marketingCategoryIds = setOf(
                "pazarlama"
            )

            val categoryGroups: List<Pair<String, List<FilterRulesPreferences.BlockCategory>>> = listOf(
                stringResource(R.string.block_group_marketing) to categories.filter { it.id in marketingCategoryIds },
                stringResource(R.string.block_group_other) to categories.filter { it.id !in marketingCategoryIds }
            ).filter { it.second.isNotEmpty() }

            @Composable
            fun shortLabel(category: FilterRulesPreferences.BlockCategory): String {
                return when (category.id) {
                    "pazarlama" -> stringResource(R.string.category_marketing)
                    "kredi" -> stringResource(R.string.category_credit)
                    "oneriler" -> stringResource(R.string.category_recommendations)
                    "oyun" -> stringResource(R.string.category_games)
                    "haber" -> stringResource(R.string.category_news)
                    "genel" -> stringResource(R.string.category_general)
                    else -> category.label
                }
            }

            val wordOwnerCategoryId = LinkedHashMap<String, String>()
            val wordInAnyContent = HashSet<String>()
            val wordInAnyChannel = HashSet<String>()

            val uniqueWordsByCategoryId = categories.associate { category ->
                val contentWords = category.contentKeywords
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val channelWords = category.channelKeywords
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

                wordInAnyContent.addAll(contentWords)
                wordInAnyChannel.addAll(channelWords)

                val allWords = (contentWords + channelWords)
                    .toList()
                    .distinct()
                    .sorted()

                val uniqueForThisCategory = allWords.filter { word ->
                    wordOwnerCategoryId.putIfAbsent(word, category.id) == null
                }

                category.id to uniqueForThisCategory
            }

            categoryGroups.forEach { (groupTitle, groupCategories) ->
                if (groupTitle != stringResource(R.string.block_group_marketing)) {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                }

                groupCategories.forEach { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val active = filterPrefs.isBlockCategoryActive(category.id)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable {
                                            filterPrefs.toggleBlockCategory(category.id)
                                            refreshTrigger++
                                        }
                                ) {
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            filterPrefs.toggleBlockCategory(category.id)
                                            refreshTrigger++
                                        },
                                        label = { Text(shortLabel(category)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    )

                                    Icon(
                                        imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.padding(start = 8.dp, top = 10.dp)
                                    )
                                }

                                val draft = categoryWordDrafts[category.id].orEmpty()
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    value = categoryWordDrafts[category.id] ?: "",
                                    onValueChange = { v ->
                                        categoryWordDrafts = categoryWordDrafts + (category.id to v)
                                    },
                                    label = { Text(stringResource(R.string.word)) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.size(44.dp),
                                    onClick = {
                                        filterPrefs.addUserCategoryWord(category.id, draft)
                                        categoryWordDrafts = categoryWordDrafts + (category.id to "")
                                        refreshTrigger++
                                    },
                                    enabled = draft.trim().isNotBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.add),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            val userWords = filterPrefs.getUserCategoryWords(category.id)
                            if (userWords.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    userWords.sorted().forEach { word ->
                                        AssistChip(
                                            onClick = {
                                                filterPrefs.removeUserCategoryWord(category.id, word)
                                                refreshTrigger++
                                            },
                                            label = { Text(word) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.delete)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            val allWords = uniqueWordsByCategoryId[category.id].orEmpty()

                            if (allWords.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.block_category_words),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                                )

                                if (category.id == "pazarlama") {
                                    FilterChip(
                                        selected = globalEmojiBlock,
                                        onClick = {
                                            globalEmojiBlock = !globalEmojiBlock
                                            filterPrefs.isGlobalEmojiBlockEnabled = globalEmojiBlock
                                            refreshTrigger++
                                        },
                                        label = { Text("😀 Emoji Block") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    allWords.forEach { word ->
                                        val isInContent = wordInAnyContent.contains(word)
                                        val isInChannel = wordInAnyChannel.contains(word)

                                        val wordEnabled = (
                                            (!isInContent || filterPrefs.isRecommendedContentWordEnabled(word)) &&
                                                (!isInChannel || filterPrefs.isRecommendedChannelWordEnabled(word))
                                            )

                                        val selectedContainerColor = if (active) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        }
                                        val selectedLabelColor = if (active) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        }
                                        val containerColor = if (active) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                        val labelColor = if (active) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        }

                                        FilterChip(
                                            selected = wordEnabled,
                                            onClick = {
                                                if (isInContent) filterPrefs.toggleRecommendedContentWord(word)
                                                if (isInChannel) filterPrefs.toggleRecommendedChannelWord(word)
                                                refreshTrigger++
                                            },
                                            label = {
                                                Row {
                                                    Text(word)
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = selectedContainerColor,
                                                selectedLabelColor = selectedLabelColor,
                                                containerColor = containerColor,
                                                labelColor = labelColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.block_section_allow_words_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.block_section_allow_words_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                value = allowWord,
                onValueChange = { allowWord = it },
                label = { Text(stringResource(R.string.add_word)) },
                singleLine = true
            )
            IconButton(
                onClick = {
                    filterPrefs.addUserContentAllowWord(allowWord)
                    allowWord = ""
                    refreshTrigger++
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        key(refreshTrigger) {
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterPrefs.userContentAllowWords
                    .sorted()
                    .forEach { word ->
                        AssistChip(
                            onClick = {
                                filterPrefs.removeUserContentAllowWord(word)
                                refreshTrigger++
                            },
                            label = { Text(word) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        )
                    }
            }
        }

        Text(
            text = stringResource(R.string.block_section_block_words_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.block_section_block_words_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                value = customWord,
                onValueChange = { customWord = it },
                label = { Text(stringResource(R.string.add_word)) },
                singleLine = true
            )
            IconButton(
                onClick = {
                    filterPrefs.addUserContentBlockWord(customWord)
                    customWord = ""
                    refreshTrigger++
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        key(refreshTrigger) {
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterPrefs.userContentBlockWords
                    .sorted()
                    .forEach { word ->
                        AssistChip(
                            onClick = {
                                filterPrefs.removeUserContentBlockWord(word)
                                refreshTrigger++
                            },
                            label = { Text(word) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        )
                    }
            }
        }
    }
}
