package com.casacheck.app.core

import androidx.compose.ui.geometry.Offset

fun parseSignature(value: String): List<List<Offset>> = value.split("|").filter { it.isNotBlank() }.map { stroke ->
    stroke.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size != 2) null else runCatching { Offset(parts[0].toFloat(), parts[1].toFloat()) }.getOrNull()?.takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}
internal fun encodeSignature(strokes: List<List<Offset>>) = strokes.joinToString("|") { s -> s.joinToString(";") { "${it.x},${it.y}" } }
