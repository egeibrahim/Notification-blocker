package com.notifilter.engine

sealed class SpamResult {
    data object Allow : SpamResult()
    data class Block(val reason: String) : SpamResult()
}
