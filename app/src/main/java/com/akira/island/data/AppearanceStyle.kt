package com.akira.island.data

import com.akira.island.core.NoticeStyle

/** Exclusive editor modes. Legacy saved widths remain readable without a migration. */
enum class BorderMode { INHERIT, NONE, CUSTOM }
enum class BorderWidths { UNIFORM, EDGES }

fun IslandSettings.borderMode(card: Boolean): BorderMode = when {
    card && expandedOutlineWidth < 0f -> BorderMode.INHERIT
    card && expandedOutlineWidth == 0f && cardEdges().all { it <= 0f } -> BorderMode.NONE
    card -> BorderMode.CUSTOM
    outline -> BorderMode.CUSTOM
    else -> BorderMode.NONE
}

internal fun IslandSettings.cardEdges() = listOf(expandedOutlineTopWidth, expandedOutlineRightWidth, expandedOutlineBottomWidth, expandedOutlineLeftWidth)
internal fun IslandSettings.capsuleEdges() = listOf(outlineTopWidth, outlineRightWidth, outlineBottomWidth, outlineLeftWidth)
fun IslandSettings.borderWidths(card: Boolean): BorderWidths =
    if ((if (card) cardEdges() else capsuleEdges()).any { it >= 0f }) BorderWidths.EDGES else BorderWidths.UNIFORM

fun IslandSettings.withBorderMode(card: Boolean, mode: BorderMode): IslandSettings = if (card) {
    when (mode) {
        BorderMode.INHERIT -> copy(expandedOutlineWidth = -1f, expandedOutlineTopWidth = -1f, expandedOutlineRightWidth = -1f, expandedOutlineBottomWidth = -1f, expandedOutlineLeftWidth = -1f)
        BorderMode.NONE -> copy(expandedOutlineWidth = 0f, expandedOutlineTopWidth = -1f, expandedOutlineRightWidth = -1f, expandedOutlineBottomWidth = -1f, expandedOutlineLeftWidth = -1f)
        BorderMode.CUSTOM -> copy(expandedOutlineWidth = expandedOutlineWidth.takeIf { it > 0f } ?: outlineWidth.coerceAtLeast(1f))
    }
} else copy(outline = mode == BorderMode.CUSTOM)

fun IslandSettings.withBorderWidths(card: Boolean, mode: BorderWidths): IslandSettings {
    val value = if (mode == BorderWidths.UNIFORM) -1f else
        (if (card && expandedOutlineWidth >= 0f) expandedOutlineWidth else outlineWidth).coerceIn(0f, 6f)
    return if (card) copy(expandedOutlineTopWidth = value, expandedOutlineRightWidth = value, expandedOutlineBottomWidth = value, expandedOutlineLeftWidth = value)
    else copy(outlineTopWidth = value, outlineRightWidth = value, outlineBottomWidth = value, outlineLeftWidth = value)
}

data class ResolvedOutline(val visible: Boolean, val color: Long, val opacity: Int, val fromActivity: Boolean, val edges: List<Float>)

/** Single source for the actual island and every settings preview. */
fun IslandSettings.resolveOutline(card: Boolean): ResolvedOutline {
    val own = card && expandedOutlineWidth >= 0f
    val width = if (own) expandedOutlineWidth else outlineWidth
    val edges = (if (own) cardEdges() else capsuleEdges()).map { (if (it < 0f) width else it).coerceIn(0f, 6f) }
    return ResolvedOutline(
        visible = (own || outline) && edges.any { it > 0f },
        color = if (own) expandedOutlineColor else outlineColor,
        opacity = (if (own) expandedOutlineOpacity else outlineOpacity).coerceIn(0, 100),
        // A manually selected card outline must never be overridden by the capsule's adaptive color.
        fromActivity = !own && outlineFromArt,
        edges = edges,
    )
}

/** Banner geometry determines its surface; notification preferences do not affect unrelated events. */
fun IslandSettings.usesCardSurface(expanded: Boolean, style: NoticeStyle?, detail: Boolean = false): Boolean =
    expanded || when (style) {
        null, NoticeStyle.PILL -> false
        NoticeStyle.MESSAGE -> detail || messageStyle == MessageStyle.CARD
        else -> true
    }
