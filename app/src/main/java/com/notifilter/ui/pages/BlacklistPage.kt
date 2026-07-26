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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import com.notifilter.ui.components.AppCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.notifilter.engine.ArchiveBlockScanner
import com.notifilter.preferences.FilterRulesPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlacklistPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val filterPrefs = remember { FilterRulesPreferences(context) }
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }
    var categoryWordDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("notifilter_walkthrough", android.content.Context.MODE_PRIVATE) }
    var showRulesTooltip by remember { mutableStateOf(sharedPrefs.getBoolean("show_tooltip_rules", true)) }

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
                    val isExpanded = expandedCategoryId == category.id
                    val wordCount = uniqueWordsByCategoryId[category.id].orEmpty().size

                    AppCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val active = filterPrefs.isBlockCategoryActive(category.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategoryId = if (isExpanded) null else category.id
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(
                                            text = shortLabel(category),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.block_category_word_count, wordCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = active,
                                    onCheckedChange = {
                                        filterPrefs.toggleBlockCategory(category.id)
                                        refreshTrigger++
                                    }
                                )
                            }

                            if (isExpanded) {
                                val draft = categoryWordDrafts[category.id].orEmpty()
                                OutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = draft,
                                    onValueChange = { v ->
                                        categoryWordDrafts = categoryWordDrafts + (category.id to v)
                                    },
                                    label = { Text(stringResource(R.string.word)) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                filterPrefs.addUserCategoryWord(category.id, draft)
                                                categoryWordDrafts = categoryWordDrafts + (category.id to "")
                                                refreshTrigger++
                                                scope.launch {
                                                    ArchiveBlockScanner.rescan(context)
                                                    refreshTrigger++
                                                }
                                            },
                                            enabled = draft.trim().isNotBlank()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.add)
                                            )
                                        }
                                    }
                                )

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
                                            label = { Text(stringResource(R.string.emoji_block_toggle)) },
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

                                            FilterChip(
                                                selected = wordEnabled,
                                                onClick = {
                                                    if (isInContent) filterPrefs.toggleRecommendedContentWord(word)
                                                    if (isInChannel) filterPrefs.toggleRecommendedChannelWord(word)
                                                    refreshTrigger++
                                                },
                                                label = { Text(word) },
                                                leadingIcon = {
                                                    // Sabit ikon: bu bir "öneri" chip'i, kullanıcı
                                                    // kelimelerinden (AssistChip + X) görsel olarak ayrışır
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CustomWordCategoriesSection(
            title = stringResource(R.string.block_section_allow_words_title),
            desc = stringResource(R.string.block_section_allow_words_desc),
            filterPrefs = filterPrefs,
            isAllow = true,
            refreshTrigger = refreshTrigger,
            onRefresh = { refreshTrigger++ }
        )

        CustomWordCategoriesSection(
            title = stringResource(R.string.block_section_block_words_title),
            desc = stringResource(R.string.block_section_block_words_desc),
            filterPrefs = filterPrefs,
            isAllow = false,
            refreshTrigger = refreshTrigger,
            onRefresh = { refreshTrigger++ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomWordCategoriesSection(
    title: String,
    desc: String,
    filterPrefs: FilterRulesPreferences,
    isAllow: Boolean,
    refreshTrigger: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newCategoryTitle by remember { mutableStateOf("") }
    var wordDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    AppCard(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newCategoryTitle,
                onValueChange = { newCategoryTitle = it },
                label = { Text(stringResource(R.string.block_category_title_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val id = if (isAllow) {
                                filterPrefs.addUserContentAllowCategory(newCategoryTitle)
                            } else {
                                filterPrefs.addUserContentBlockCategory(newCategoryTitle)
                            }
                            if (id.isNotBlank()) {
                                newCategoryTitle = ""
                                onRefresh()
                            }
                        },
                        enabled = newCategoryTitle.trim().isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.block_add_category)
                        )
                    }
                }
            )

            val categories = if (isAllow) {
                filterPrefs.getUserContentAllowCategories()
            } else {
                filterPrefs.getUserContentBlockCategories()
            }

            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.block_no_categories_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.block_category_word_count, category.words.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isAllow) {
                                    filterPrefs.removeUserContentAllowCategory(category.id)
                                } else {
                                    filterPrefs.removeUserContentBlockCategory(category.id)
                                }
                                onRefresh()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }

                    val draft = wordDrafts[category.id].orEmpty()
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft,
                        onValueChange = { wordDrafts = wordDrafts + (category.id to it) },
                        label = { Text(stringResource(R.string.add_word)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isAllow) {
                                        filterPrefs.addUserContentAllowCategoryWord(category.id, draft)
                                    } else {
                                        filterPrefs.addUserContentBlockCategoryWord(category.id, draft)
                                    }
                                    wordDrafts = wordDrafts + (category.id to "")
                                    onRefresh()
                                    scope.launch {
                                        if (!isAllow) {
                                            ArchiveBlockScanner.rescan(context)
                                        }
                                        onRefresh()
                                    }
                                },
                                enabled = draft.trim().isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add)
                                )
                            }
                        }
                    )

                    if (category.words.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            category.words.sorted().forEach { word ->
                                AssistChip(
                                    onClick = {
                                        if (isAllow) {
                                            filterPrefs.removeUserContentAllowCategoryWord(category.id, word)
                                        } else {
                                            filterPrefs.removeUserContentBlockCategoryWord(category.id, word)
                                        }
                                        onRefresh()
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
                    } else {
                        Text(
                            text = stringResource(R.string.block_category_empty_words),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
