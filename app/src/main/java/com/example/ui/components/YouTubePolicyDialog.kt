package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

/**
 * One-time consent shown before the first YouTube playback.
 *
 * NOXA resolves YouTube audio and video through third-party public instances
 * (Piped / Invidious). The user has to acknowledge what that means — personal
 * use, no affiliation with Google, availability depending on public instances —
 * before the first YouTube track plays. Accepting is stored by
 * `UsagePolicyStore` and the dialog never appears again.
 */
@Composable
fun YouTubePolicyDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = modifier.testTag("youtube_policy_dialog"),
        containerColor = NoxaSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kebijakan Penggunaan",
                    color = NoxaTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                PolicyPoint(
                    title = "Bukan aplikasi resmi",
                    body = "NOXA tidak berafiliasi dengan Google atau YouTube."
                )
                PolicyPoint(
                    title = "Sumber pemutaran",
                    body = "Lagu YouTube diambil lewat layanan pihak ketiga (instance publik " +
                        "Piped/Invidious), bukan API resmi. Hak cipta lagu tetap milik " +
                        "pencipta dan labelnya."
                )
                PolicyPoint(
                    title = "Untuk pribadi",
                    body = "Gunakan untuk kebutuhan pribadi Anda. Jangan didistribusikan " +
                        "atau dipakai untuk keperluan komersial."
                )
                PolicyPoint(
                    title = "Ketersediaan tidak dijamin",
                    body = "Instance publik bisa sibuk, berubah, atau mati sewaktu-waktu. " +
                        "Bila itu terjadi, lagu tidak bisa diputar — bukan kerusakan aplikasi."
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dengan menekan \"Setuju\", Anda menyatakan membaca dan menerima " +
                        "ketentuan di atas.",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NoxaLime,
                    contentColor = NoxaBlack
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Setuju & Lanjutkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Nanti Saja", color = NoxaTextSecondary, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun PolicyPoint(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NoxaSurfaceElevated)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            color = NoxaLime,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = NoxaTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
