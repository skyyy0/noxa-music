package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale for NOXA.
 *
 * The whole app draws from these four steps plus `full`. Anything that needs
 * a corner picks the step that matches its *role*, not its size:
 *
 *  - [xs]  6dp  — chips, tags, small inline controls, thumbnails inside rows.
 *  - [sm]  10dp — compact cards, list groups, search field.
 *  - [md]  14dp — standard cards, artwork tiles, dialogs.
 *  - [lg]  20dp — sheets, hero panels, anything that owns a whole region.
 *  - [full]      — pills, avatars, circular play buttons (clamped to a round end).
 *
 * Having one scale is what stops the app drifting into "every screen rounds
 * corners differently". New surfaces must pick from here before inventing a
 * radius, and no radius outside this scale should be introduced without a
 * new role.
 */
object NoxaRadius {

    /** Chips, tags, inline controls, small in-row thumbnails. */
    val xs = 6.dp

    /** Compact cards, list groups, the search field. */
    val sm = 10.dp

    /** Standard cards and artwork tiles. */
    val md = 14.dp

    /** Sheets, hero panels and other region-owning surfaces. */
    val lg = 20.dp

    /**
     * Perfect pill / circle. Deliberately larger than half of any element we
     * build, so the corner clamps to a round end regardless of the element's
     * height — same token works for a 32dp chip and a 56dp avatar.
     */
    val full = 999.dp
}

/**
 * Material3 shape roles bound to the same scale, so any Material component
 * dropped into the app (Card, Dialog, Snackbar, TextField…) inherits NOXA's
 * corners without per-call overrides.
 *
 * The M3 scale has five roles while NOXA defines four steps + full; `full`
 * is intentionally not exposed as a shape role — pills are always built
 * explicitly with [NoxaRadius.full] so nothing becomes round by accident.
 */
val NoxaShapes = Shapes(
    extraSmall = RoundedCornerShape(NoxaRadius.xs),
    small = RoundedCornerShape(NoxaRadius.sm),
    medium = RoundedCornerShape(NoxaRadius.md),
    large = RoundedCornerShape(NoxaRadius.lg),
    extraLarge = RoundedCornerShape(NoxaRadius.lg)
)