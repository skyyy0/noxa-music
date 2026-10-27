package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.EqualizerBand
import com.example.media.EqualizerState
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

@Composable
fun EqualizerScreen(
    equalizerState: EqualizerState,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSetBandLevel: (Int, Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetVirtualizer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("equalizer_screen")
    ) {
        // Equalizer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Equalizer",
                    color = NoxaTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hardware DSP Audio Processing",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp
                )
            }

            val haptic = LocalHapticFeedback.current
            Switch(
                checked = equalizerState.isEnabled,
                onCheckedChange = {
                    // Master switch: the heavy tick, since this one arms the
                    // whole DSP chain.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NoxaBlack,
                    checkedTrackColor = NoxaLime,
                    uncheckedThumbColor = NoxaTextTertiary,
                    uncheckedTrackColor = NoxaSurfaceSecondary
                ),
                modifier = Modifier.testTag("equalizer_master_switch")
            )
        }

        // Presets Horizontal Row
        Text(
            text = "Presets",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(equalizerState.presets) { preset ->
                val isSelected = equalizerState.currentPreset == preset
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) NoxaLime else NoxaSurfaceSecondary)
                        .border(1.dp, if (isSelected) NoxaLime else NoxaBorder, CircleShape)
                        .clickable(enabled = equalizerState.isEnabled) {
                            onSelectPreset(preset)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("preset_$preset")
                ) {
                    Text(
                        text = preset,
                        color = if (isSelected) NoxaBlack else NoxaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Frequency Band Sliders Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frequency Bands",
                        color = NoxaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (equalizerState.isEnabled) "ACTIVE" else "BYPASS",
                        color = if (equalizerState.isEnabled) NoxaLime else NoxaTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val bands = if (equalizerState.bands.isNotEmpty()) {
                    equalizerState.bands
                } else {
                    // Fallback visual bands if audio session is initializing
                    listOf(
                        EqualizerBand(0, 60, -1500, 1500, 300),
                        EqualizerBand(1, 230, -1500, 1500, 600),
                        EqualizerBand(2, 910, -1500, 1500, 0),
                        EqualizerBand(3, 3600, -1500, 1500, 400),
                        EqualizerBand(4, 14000, -1500, 1500, 700)
                    )
                }

                bands.forEach { band ->
                    val min = band.minLevelMilliBels.toFloat()
                    val max = band.maxLevelMilliBels.toFloat()
                    val current = band.currentLevelMilliBels.toFloat()
                    val dB = (band.currentLevelMilliBels / 100)

                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000} kHz"
                    } else {
                        "${band.centerFreqHz} Hz"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = freqLabel,
                                color = NoxaTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${if (dB > 0) "+$dB" else "$dB"} dB",
                                color = if (dB != 0) NoxaLime else NoxaTextTertiary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = current,
                            onValueChange = { newVal ->
                                onSetBandLevel(band.index, newVal.toInt())
                            },
                            valueRange = min..max,
                            enabled = equalizerState.isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = NoxaLime,
                                activeTrackColor = NoxaLime,
                                inactiveTrackColor = NoxaSurfaceSecondary,
                                disabledThumbColor = NoxaTextTertiary,
                                disabledActiveTrackColor = NoxaSurfaceSecondary
                            ),
                            modifier = Modifier.testTag("slider_band_${band.index}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Real platform audio effects, not just labels: these drive BassBoost and
        // Virtualizer on the player's audio session.
        EffectSliderCard(
            title = "Dynamic Bass",
            subtitle = "BassBoost pada sesi audio",
            value = equalizerState.bassBoostStrength,
            enabled = equalizerState.supportsBassBoost && equalizerState.isEnabled,
            unsupportedNote = "Perangkat ini tidak mendukung BassBoost",
            onChange = onSetBassBoost,
            testTag = "bass_boost_slider"
        )

        Spacer(modifier = Modifier.height(12.dp))

        EffectSliderCard(
            title = "3D Clarity",
            subtitle = "Virtualizer pada sesi audio",
            value = equalizerState.virtualizerStrength,
            enabled = equalizerState.supportsVirtualizer && equalizerState.isEnabled,
            unsupportedNote = "Perangkat ini tidak mendukung Virtualizer",
            onChange = onSetVirtualizer,
            testTag = "virtualizer_slider"
        )
    }
}

/**
 * A 0..1000 strength slider for one platform audio effect. The effect itself is
 * probed per device, so unsupported hardware is explained instead of silently
 * doing nothing.
 */
@Composable
private fun EffectSliderCard(
    title: String,
    subtitle: String,
    value: Int,
    enabled: Boolean,
    unsupportedNote: String,
    onChange: (Int) -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = title,
                        tint = if (enabled) NoxaLime else NoxaTextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            color = NoxaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (enabled || value > 0) subtitle else unsupportedNote,
                            color = NoxaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = "${value * 100 / 1000}%",
                    color = if (enabled) NoxaLime else NoxaTextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = NoxaLime,
                    activeTrackColor = NoxaLime,
                    inactiveTrackColor = NoxaSurfaceSecondary,
                    disabledThumbColor = NoxaTextTertiary,
                    disabledActiveTrackColor = NoxaSurfaceSecondary
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
