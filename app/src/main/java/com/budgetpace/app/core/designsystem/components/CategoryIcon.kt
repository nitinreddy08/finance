package com.budgetpace.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Category emoji choices offered in the add/edit category picker (spec §4). */
val CATEGORY_EMOJI_CHOICES = listOf(
    "🏠", "🥚", "🍎", "🍔", "🛍", "🚗", "🚌", "📚", "❤️", "🎁",
    "☕", "✈️", "🎬", "📱", "🛒", "🐾", "🎮", "🔧", "💊", "👕",
)

/** A category's iconKey is either a chosen emoji, or "default"/blank for categories predating this. */
fun isEmojiIcon(iconKey: String?): Boolean =
    !iconKey.isNullOrBlank() && iconKey != "default"

/**
 * Renders a category's chosen emoji (spec §4: "the selected emoji/icon should appear
 * consistently beside the category throughout the application"), falling back to a neutral
 * letter avatar for categories created before this existed.
 */
@Composable
fun CategoryIcon(iconKey: String, name: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isEmojiIcon(iconKey)) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isEmojiIcon(iconKey)) {
            // Converts the container's Dp size to Sp through the real density/font-scale, rather
            // than reinterpreting the raw Dp float as an Sp value (which drifts under a non-default
            // font scale — the emoji would no longer track the tile size it's drawn in).
            val density = LocalDensity.current
            val emojiFontSize = with(density) { (size * 0.6f).toSp() }
            Text(iconKey, fontSize = emojiFontSize, textAlign = TextAlign.Center)
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
