package com.notifilter.engine

/**
 * Blok motoru için sadeleştirilmiş konfigürasyon.
 */
data class FilterRulesConfig(
    val channelBlock: List<String>,
    val channelAllow: List<String>,
    val contentAllow: List<String> = emptyList(),
    val contentBlock: List<String>,
    val channelIdsBlocked: List<String> = emptyList(),
    val blockIfHasEmoji: Boolean = false
)
