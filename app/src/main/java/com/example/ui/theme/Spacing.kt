package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale for NOXA.
 *
 * Every gap in the app — between rows, between a section header and its
 * content, inside a card, around a screen — comes from these steps. The scale
 * is deliberately short and 4dp-based so two adjacent elements always have a
 * *perceptible* rhythm instead of a pile of near-identical values (7, 9, 11,
 * 13…) that reads as noise.
 *
 *  - [xs]    4dp  — icon ↔ label, the smallest relationship that still reads.
 *  - [sm]    8dp  — between related lines (title ↔ subtitle, tile internals).
 *  - [md]   12dp  — between items in a dense list or grid.
 *  - [lg]   16dp  — default padding inside cards and rows.
 *  - [xl]   20dp  — screen gutter (see [gutter]); generous but not loose.
 *  - [xxl]  24dp  — between a section header and its content.
 *  - [xxxl] 32dp  — between sections / major regions of a page.
 *
 * Rule of thumb for new UI: pick the step by *relationship* (how related are
 * the two things?), never by eye. If a layout needs a value that is not here,
 * that is a sign the hierarchy is wrong — not a reason to add a token.
 */
object NoxaSpacing {

    /** The smallest step: icon ↔ label, tight inline relationships. */
    val xs = 4.dp

    /** Between related lines: title ↔ subtitle, tile internals. */
    val sm = 8.dp

    /** Between items in a dense list or grid. */
    val md = 12.dp

    /** Default padding inside cards, rows and controls. */
    val lg = 16.dp

    /** Generous inset for grouped content and large controls. */
    val xl = 20.dp

    /** Between a section header and the content it introduces. */
    val xxl = 24.dp

    /** Between sections / major regions of a page. */
    val xxxl = 32.dp

    /**
     * The single horizontal inset every screen shares, so content lines up
     * across Home, Search, Library and the player. Same value as [xl]; it is
     * named separately because it is a *layout* decision, not a gap.
     */
    val gutter = 20.dp
}