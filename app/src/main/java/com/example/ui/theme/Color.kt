package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/* =========================================================================
 * NOXA visual identity — "signal, not decoration".
 *
 * Three rules the whole palette encodes:
 *
 * 1. CHARCOAL, NOT BLACK. The canvas is a near-black with a faint cool cast
 *    so panels can step *up* in tone instead of being outlined. Hierarchy
 *    comes from tonal steps (#08090A -> #141519 -> #1B1D22), not from boxes.
 * 2. LIME IS A SIGNAL. Brand lime only marks something that is happening
 *    right now: the primary play affordance, the active tab, the playhead,
 *    the row that is currently sounding. It is never wallpaper, never a
 *    label background, never a decorative ring.
 * 3. TWO SUPPORT HUES. Ember (warm) and Haze (cool) exist only as ambient
 *    light and quiet category accents, so the app is never just black + lime.
 * ======================================================================= */

// --- Canvas ---------------------------------------------------------------

/** True black — reserved for hard scrims over artwork. Not a background. */
val NoxaInk = Color(0xFF000000)

/**
 * The app canvas. Near-black charcoal rather than #000: pure black kills the
 * sense of a lit surface and makes every panel read as a hole.
 */
val NoxaBlack = Color(0xFF08090A)

/**
 * Tone between the canvas and the panels — used when a surface needs to sit
 * above the background without an outline (sheets, pressed states).
 */
val NoxaSurface = Color(0xFF141519)
val NoxaSurfaceElevated = Color(0xFF1B1D22)
val NoxaSurfaceSecondary = Color(0xFF22242A)
val NoxaSurfaceTertiary = Color(0xFF2E3138)

// --- Brand accent ---------------------------------------------------------

val NoxaLime = Color(0xFFC6FF00)
val NoxaLimeDark = Color(0xFFA6D600)
val NoxaLimeGlow = 0x33C6FF00

// Warm brand secondary — a deeper, moodier lime that lets cards, bars and
// ambient glows sit *behind* the bright NoxaLime accent instead of competing
// with it. This is what keeps the dark UI from reading as flat black+lime.
val NoxaLimeDeep = Color(0xFF6F8F0A)
val NoxaLimeMist = 0x14C6FF00

// --- Support hues ---------------------------------------------------------

/** Warm support hue: ambient light, "hot" shelves (fire/stats). Low alpha only. */
val NoxaEmber = Color(0xFFFF8A3D)
val NoxaEmberMist = 0x1AFF8A3D

/** Cool support hue: depth in the backdrop, secondary category accents. */
val NoxaHaze = Color(0xFF7C8CFF)
val NoxaHazeMist = 0x147C8CFF

// --- Text -----------------------------------------------------------------

val NoxaWhite = Color(0xFFFFFFFF)
val NoxaTextPrimary = Color(0xFFF5F5F7)
val NoxaTextSecondary = Color(0xFF9EA3AC)
val NoxaTextTertiary = Color(0xFF6B7079)

// --- Hairlines ------------------------------------------------------------
//
// Translucent white instead of a solid grey: an edge reads as a change of
// material rather than a drawn outline, and it stays correct over any tone.

/** The single structural hairline: dock edge, sheets, dividers, chip outlines. */
val NoxaBorder = Color(0x14FFFFFF)
val NoxaBorderSubtle = Color(0x0AFFFFFF)

// --- Player & status ------------------------------------------------------

val NoxaError = Color(0xFFFF453A)
val NoxaSuccess = Color(0xFF30D158)

// NOXA Support identity — the purple verified badge of NOXA Support / staff.
val NoxaPurple = Color(0xFFA855F7)
val NoxaPurpleDeep = Color(0xFF7C3AED)
val NoxaPurpleGlow = 0x40A855F7

// --- Ambient wash anchors -------------------------------------------------
//
// Low-alpha glows layered behind content. Each screen picks the pair it
// needs; they are decorative only and cost two static gradients.

val NoxaAmbientLime = Color(0x16C6FF00)
val NoxaAmbientWarm = Color(0x18FF8A3D)
val NoxaAmbientHaze = Color(0x147C8CFF)