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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import com.notifilter.ui.components.AppCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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


        key(refreshTrigger) {
            val categories = filterPrefs.getMergedBlockCategories()
            var globalEmojiBlock by remember { mutableStateOf(filterPrefs.isGlobalEmojiBlockEnabled) }

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.block_categories_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Emoji block chip
                        Surface(
                            onClick = {
                                globalEmojiBlock = !globalEmojiBlock
                                filterPrefs.isGlobalEmojiBlockEnabled = globalEmojiBlock
                                refreshTrigger++
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (globalEmojiBlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (globalEmojiBlock) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.emoji_block_toggle),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Icon(
                                    imageVector = if (globalEmojiBlock) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Category chips
                        categories.forEach { category ->
                            val active = filterPrefs.isBlockCategoryActive(category.id)
                            Surface(
                                onClick = {
                                    filterPrefs.toggleBlockCategory(category.id)
                                    refreshTrigger++
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
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
                // Category tags as chips with checkmark + delete
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        Surface(
                            onClick = {
                                if (isAllow) {
                                    filterPrefs.removeUserContentAllowCategory(category.id)
                                } else {
                                    filterPrefs.removeUserContentBlockCategory(category.id)
                                }
                                onRefresh()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Word management for each category (always visible, no expansion)
                categories.forEach { category ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

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
                        }
                    }
                }
            }
        }
    }
}
