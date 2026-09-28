package com.aria.nn

import java.util.Locale

data class ModelInfo(
    val name: String,
    val version: Int,
    val params: Long,
) {
    fun label(): String = "$name$version:${formatParams(params)}"
    fun fileName(): String = "${name}${version}_${formatParams(params)}.bin"

    companion object {
        fun formatParams(count: Long): String = when {
            count >= 1_000_000_000L ->
                String.format(Locale.US, "%.1fb", count / 1_000_000_000.0)
            count >= 1_000_000L ->
                String.format(Locale.US, "%.1fm", count / 1_000_000.0)
            count >= 1_000L ->
                String.format(Locale.US, "%.1fk", count / 1_000.0)
            else -> count.toString()
        }
    }
}