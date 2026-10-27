package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.support.NoxaBackend
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.i18n.string
import com.example.ui.viewmodel.MusicViewModel

/**
 * The in-app update popup.
 *
 * Two modes, chosen by the developer when pushing the update:
 *
 *  * **Mandatory** (`mandatory = true`) — cannot be dismissed: the only way
 *    out is updating. Reserved for changes that really need it.
 *  * **Optional** (`mandatory = false`) — carries a "Nanti Saja" escape, and
 *    the copy points at Settings → Pembaruan so the user can update later.
 *    Small patches get pushed this way so they never block the app.
 *
 * The DEVELOPER account never sees this dialog at all (gated in NoxaApp).
 */
@Composable
fun UpdateDialog(
    info: NoxaBackend.UpdateInfo,
    phase: MusicViewModel.UpdatePhase,
    error: String?,
    mandatory: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!mandatory) onDismiss() },
        containerColor = NoxaSurfaceElevated,
        title = {
            Text(
                if (mandatory) string("update.mandatoryTitle") else string("update.optionalTitle"),
                color = NoxaTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NOXA ${info.versionName}",
                    color = NoxaLime,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (info.note.isNotBlank()) {
                    Text(
                        text = info.note,
                        color = NoxaTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = if (mandatory) {
                        "${string("update.mandatoryDesc")} (kamu di ${com.example.BuildConfig.VERSION_NAME})"
                    } else {
                        "${string("update.optionalDesc")} (kamu di ${com.example.BuildConfig.VERSION_NAME})"
                    },
                    color = NoxaTextTertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                when (phase) {
                    MusicViewModel.UpdatePhase.Resolving -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Menyiapkan link unduhan…", color = NoxaTextTertiary, fontSize = 11.sp)
                    }

                    MusicViewModel.UpdatePhase.Downloading -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Mengunduh update… pantau di notifikasi.",
                            color = NoxaTextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    MusicViewModel.UpdatePhase.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Unduhan selesai — panel instalasi Android dibuka otomatis. " +
                                "Ketuk \"Update\" di panel tersebut.",
                            color = NoxaLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    MusicViewModel.UpdatePhase.Failed -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = error ?: "Terjadi kesalahan saat mengunduh.",
                            color = NoxaLime,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    MusicViewModel.UpdatePhase.Idle -> Unit
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!mandatory && phase != MusicViewModel.UpdatePhase.Downloading) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("update_later_btn")
                    ) {
                        Text(string("update.later"), color = NoxaTextSecondary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                val label = when (phase) {
                    MusicViewModel.UpdatePhase.Resolving,
                    MusicViewModel.UpdatePhase.Downloading -> string("update.downloading")

                    MusicViewModel.UpdatePhase.Failed -> string("action.retry")
                    else -> string("update.now")
                }
                val enabled = phase != MusicViewModel.UpdatePhase.Resolving &&
                    phase != MusicViewModel.UpdatePhase.Downloading
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (enabled) NoxaLime else NoxaBorder)
                        .clickable(enabled = enabled) { onUpdate() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("update_now_btn")
                ) {
                    Text(
                        text = label,
                        color = if (enabled) NoxaBlack else NoxaTextTertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}
