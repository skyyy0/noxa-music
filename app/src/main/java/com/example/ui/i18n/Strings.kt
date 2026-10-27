package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.local.AppLanguage

/**
 * In-app string dictionary for the language switcher (Pengaturan → Bahasa).
 *
 * Design rules:
 *  * **Indonesia (default) is byte-for-byte the app's current copy** — the
 *    `ID` table below is the source of truth and matches the hardcoded text.
 *    Selecting Indonesia therefore changes nothing, exactly as asked.
 *  * Every other language supplies its own map; a key that is missing falls
 *    back to the Indonesian string, which itself falls back to the key — a
 *    screen can never break because a translation is incomplete.
 *  * Only **visible, high-traffic UI** is keyed (nav, player, settings,
 *    dialogs, common actions). Deep screens keep their Indonesian copy until
 *    someone translates them — that is the honest, gradual path.
 */
class Strings(private val table: Map<String, String>) {

    /** Translated text for [key], falling back to Indonesian, then the key. */
    operator fun get(key: String): String = table[key] ?: ID[key] ?: key

    companion object {
        /**
         * The Indonesian source of truth — the exact strings the app already
         * ships with. Every other language falls back through this table.
         */
        val ID: Map<String, String> = mapOf(
            // Bottom navigation
            "nav.home" to "Home",
            "nav.search" to "Search",
            "nav.library" to "Library",
            "nav.profile" to "Profile",
            // Player actions
            "player.play" to "Putar",
            "player.pause" to "Jeda",
            "player.next" to "Lagu berikutnya",
            "player.previous" to "Lagu sebelumnya",
            "player.shuffle" to "Acak",
            "player.repeat" to "Ulangi",
            "player.playAll" to "Putar semua",
            "player.addToQueue" to "Tambah ke antrian",
            "player.queue" to "Antrian",
            "player.share" to "Bagikan",
            "player.favorite" to "Favorit",
            "player.favorited" to "Difavoritkan",
            // Album / playlist actions
            "action.playAlbum" to "Putar album",
            "action.shuffle" to "Acak",
            "action.resume" to "Lanjutkan",
            "action.retry" to "Coba lagi",
            "action.cancel" to "Batal",
            "action.save" to "Simpan",
            "action.close" to "Tutup",
            "action.delete" to "Hapus",
            "action.confirm" to "Ya",
            // Follow
            "artist.follow" to "Ikuti",
            "artist.following" to "Diikuti",
            "artist.fans" to "penggemar",
            "artist.listeners" to "pendengar",
            "artist.releases" to "rilisan",
            "artist.topTracks" to "lagu teratas",
            "artist.about" to "Tentang",
            "artist.readMore" to "Baca selengkapnya",
            // Search
            "search.hint" to "Cari lagu, artis, album…",
            "search.all" to "Semua",
            "search.youtube" to "YouTube",
            "search.deezer" to "Deezer",
            "search.audius" to "Audius",
            // Profile & settings
            "profile.settings" to "Pengaturan",
            "profile.accountSettings" to "Pengaturan Akun",
            "profile.language" to "Bahasa",
            "profile.languageSubtitle" to "Pilih bahasa tampilan aplikasi",
            "profile.signOut" to "Keluar",
            "profile.signIn" to "Masuk",
            "profile.updates" to "Pembaruan",
            "profile.upToDate" to "Kamu sudah memakai versi terbaru.",
            "profile.update" to "Update",
            "profile.changeUsername" to "Ganti Username",
            "profile.changePassword" to "Ganti Password",
            "profile.deleteAccount" to "Hapus Akun",
            // Update dialog
            "update.mandatoryTitle" to "Update Wajib Tersedia",
            "update.optionalTitle" to "Update Tersedia",
            "update.later" to "Nanti Saja",
            "update.now" to "Update Sekarang",
            "update.downloading" to "Mengunduh…",
            "update.mandatoryDesc" to "Versi kamu sudah usang. Update diperlukan supaya semua fitur & perbaikan terbaru ikut terpasang.",
            "update.optionalDesc" to "Versi baru sudah keluar. Update opsional — bisa dilakukan kapan saja lewat Pengaturan.",
            // Support chat
            "support.title" to "NOXA Support",
            "support.placeholder" to "Balas pengguna...",
            // Dev panel
            "dev.registeredAccounts" to "Akun terdaftar",
            "dev.totalReports" to "Total laporan",
            "dev.pendingReplies" to "Menunggu balasan",
            // Misc
            "common.loading" to "Memuat…",
            "common.error" to "Terjadi kesalahan",
            "common.offline" to "Tidak ada koneksi internet"
        )

        private val EN: Map<String, String> = mapOf(
            "nav.home" to "Home",
            "nav.search" to "Search",
            "nav.library" to "Library",
            "nav.profile" to "Profile",
            "player.play" to "Play",
            "player.pause" to "Pause",
            "player.next" to "Next track",
            "player.previous" to "Previous track",
            "player.shuffle" to "Shuffle",
            "player.repeat" to "Repeat",
            "player.playAll" to "Play all",
            "player.addToQueue" to "Add to queue",
            "player.queue" to "Queue",
            "player.share" to "Share",
            "player.favorite" to "Favorite",
            "player.favorited" to "Favorited",
            "action.playAlbum" to "Play album",
            "action.shuffle" to "Shuffle",
            "action.resume" to "Resume",
            "action.retry" to "Try again",
            "action.cancel" to "Cancel",
            "action.save" to "Save",
            "action.close" to "Close",
            "action.delete" to "Delete",
            "action.confirm" to "Yes",
            "artist.follow" to "Follow",
            "artist.following" to "Following",
            "artist.fans" to "fans",
            "artist.listeners" to "listeners",
            "artist.releases" to "releases",
            "artist.topTracks" to "top tracks",
            "artist.about" to "About",
            "artist.readMore" to "Read more",
            "search.hint" to "Search songs, artists, albums…",
            "search.all" to "All",
            "search.youtube" to "YouTube",
            "search.deezer" to "Deezer",
            "search.audius" to "Audius",
            "profile.settings" to "Settings",
            "profile.accountSettings" to "Account Settings",
            "profile.language" to "Language",
            "profile.languageSubtitle" to "Choose the app display language",
            "profile.signOut" to "Sign out",
            "profile.signIn" to "Sign in",
            "profile.updates" to "Updates",
            "profile.upToDate" to "You're on the latest version.",
            "profile.update" to "Update",
            "profile.changeUsername" to "Change Username",
            "profile.changePassword" to "Change Password",
            "profile.deleteAccount" to "Delete Account",
            "update.mandatoryTitle" to "Mandatory Update Available",
            "update.optionalTitle" to "Update Available",
            "update.later" to "Later",
            "update.now" to "Update Now",
            "update.downloading" to "Downloading…",
            "update.mandatoryDesc" to "Your version is outdated. An update is required so all the latest features & fixes are installed.",
            "update.optionalDesc" to "A new version is out. Optional update — you can do it anytime via Settings.",
            "support.title" to "NOXA Support",
            "support.placeholder" to "Reply to user...",
            "dev.registeredAccounts" to "Registered accounts",
            "dev.totalReports" to "Total reports",
            "dev.pendingReplies" to "Pending replies",
            "common.loading" to "Loading…",
            "common.error" to "Something went wrong",
            "common.offline" to "No internet connection"
        )

        private val MS: Map<String, String> = mapOf(
            "player.play" to "Main",
            "player.pause" to "Jeda",
            "player.next" to "Lagu seterusnya",
            "player.previous" to "Lagu sebelumnya",
            "player.shuffle" to "Rawak",
            "player.repeat" to "Ulang",
            "player.playAll" to "Main semua",
            "player.addToQueue" to "Tambah ke giliran",
            "player.queue" to "Giliran",
            "player.share" to "Kongsi",
            "player.favorite" to "Kegemaran",
            "action.playAlbum" to "Main album",
            "action.shuffle" to "Rawak",
            "action.resume" to "Sambung",
            "action.retry" to "Cuba lagi",
            "action.cancel" to "Batal",
            "action.save" to "Simpan",
            "action.close" to "Tutup",
            "action.delete" to "Padam",
            "artist.follow" to "Ikut",
            "artist.following" to "Diikuti",
            "artist.fans" to "peminat",
            "artist.listeners" to "pendengar",
            "artist.releases" to "keluaran",
            "artist.topTracks" to "lagu teratas",
            "artist.about" to "Tentang",
            "artist.readMore" to "Baca lagi",
            "search.hint" to "Cari lagu, artis, album…",
            "profile.settings" to "Tetapan",
            "profile.accountSettings" to "Tetapan Akaun",
            "profile.language" to "Bahasa",
            "profile.languageSubtitle" to "Pilih bahasa paparan aplikasi",
            "profile.signOut" to "Log keluar",
            "profile.signIn" to "Log masuk",
            "profile.updates" to "Kemas kini",
            "profile.upToDate" to "Anda sudah guna versi terkini.",
            "profile.update" to "Kemas kini",
            "update.mandatoryTitle" to "Pembaruan Wajib Tersedia",
            "update.optionalTitle" to "Pembaruan Tersedia",
            "update.later" to "Nanti",
            "update.now" to "Kemas Kini Sekarang",
            "update.downloading" to "Memuat turun…",
            "update.mandatoryDesc" to "Versi anda sudah lapuk. Kemas kini diperlukan supaya semua ciri & pembaikan terkini dipasang.",
            "update.optionalDesc" to "Versi baharu sudah keluar. Kemas kini pilihan — boleh buat bila-bila melalui Tetapan.",
            "common.loading" to "Memuat…",
            "common.error" to "Ralat berlaku",
            "common.offline" to "Tiada sambungan internet"
        )

        private val JAWA: Map<String, String> = mapOf(
            "player.play" to "Gedhekake",
            "player.pause" to "Sirep",
            "player.next" to "Lagu sabanjur",
            "player.previous" to "Lagu wingi",
            "player.shuffle" to "Awut-awutan",
            "player.repeat" to "Wangsul",
            "player.playAll" to "Gedhekake kabeh",
            "player.addToQueue" to "Tambah menyang antrean",
            "player.queue" to "Antrean",
            "player.share" to "Bagekna",
            "player.favorite" to "Kanthi ati",
            "action.playAlbum" to "Gedhekake album",
            "action.shuffle" to "Awut-awutan",
            "action.resume" to "Lanjut",
            "action.retry" to "Coba maneh",
            "action.cancel" to "Batal",
            "action.save" to "Simpen",
            "action.close" to "Tutup",
            "action.delete" to "Busak",
            "artist.follow" to "Slugut",
            "artist.following" to "Dislugut",
            "artist.fans" to "penggemar",
            "artist.listeners" to "padherek",
            "artist.releases" to "rilisan",
            "artist.topTracks" to "lagu paling akeh",
            "artist.about" to "Bab",
            "artist.readMore" to "Waca selanjutne",
            "search.hint" to "Golek lagu, artis, album…",
            "profile.settings" to "Setelan",
            "profile.accountSettings" to "Setelan Akun",
            "profile.language" to "Basa",
            "profile.languageSubtitle" to "Pilih basa tampilan aplikasi",
            "profile.signOut" to "Mlebu metu",
            "profile.signIn" to "Mlebu",
            "profile.updates" to "Pembaruan",
            "profile.upToDate" to "Kowe wis ngangge versi paling anyar.",
            "profile.update" to "Update",
            "update.mandatoryTitle" to "Pembaruan Wajib Ana",
            "update.optionalTitle" to "Pembaruan Ana",
            "update.later" to "Mengko",
            "update.now" to "Update Saiki",
            "update.downloading" to "Ngundhuh…",
            "update.mandatoryDesc" to "Versi kowe wis usang. Pembaruan dibutuhake supaya kabeh fitur & perbaikan paling anyar kepasang.",
            "update.optionalDesc" to "Versi anyar wis metu. Pembaruan opsional — bisa rampung kapan wae liwat Setelan.",
            "common.loading" to "Mbukak…",
            "common.error" to "Ana kesalahan",
            "common.offline" to "Ora ana sambungan internet"
        )

        private val SUNDA: Map<String, String> = mapOf(
            "player.play" to "Putar",
            "player.pause" to "Eureun",
            "player.next" to "Lagu salajengna",
            "player.previous" to "Lagu saméméhna",
            "player.shuffle" to "Acak",
            "player.repeat" to "Ulang",
            "player.playAll" to "Putar sadayana",
            "player.addToQueue" to "Tambah kana antrean",
            "player.queue" to "Antrean",
            "player.share" to "Bagikeun",
            "player.favorite" to "Karesep",
            "action.playAlbum" to "Putar album",
            "action.shuffle" to "Acak",
            "action.resume" to "Sambungkeun",
            "action.retry" to "Coba deui",
            "action.cancel" to "Batal",
            "action.save" to "Simpan",
            "action.close" to "Tutup",
            "action.delete" to "Hapus",
            "artist.follow" to "Ikut",
            "artist.following" to "Diikut",
            "artist.fans" to "panggemar",
            "artist.listeners" to " pendéngér",
            "artist.releases" to "rilisan",
            "artist.topTracks" to "lagu luhur",
            "artist.about" to "Ngeunaan",
            "artist.readMore" to "Baca deui",
            "search.hint" to "Milarian lagu, artis, album…",
            "profile.settings" to "Setélan",
            "profile.accountSettings" to "Setélan Akun",
            "profile.language" to "Basa",
            "profile.languageSubtitle" to "Pilih basa tampilan aplikasi",
            "profile.signOut" to "Kaluar",
            "profile.signIn" to "Asup",
            "profile.updates" to "Pembaruan",
            "profile.upToDate" to "Anjeun geus nganggo vérsi panganyarna.",
            "profile.update" to "Update",
            "update.mandatoryTitle" to "Pembaruan Wajib Tersedia",
            "update.optionalTitle" to "Pembaruan Tersedia",
            "update.later" to "Engké",
            "update.now" to "Update Ayeuna",
            "update.downloading" to "Ngunduh…",
            "update.mandatoryDesc" to "Vérsi anjeun geus kolot. Pembaruan diperyogikeun supaya sadaya fitur & paningkatan panganyarna kepasang.",
            "update.optionalDesc" to "Vérsi énggal geus kaluar. Pembaruan opsional — tiasa dilakukeun iraha waé liwat Setélan.",
            "common.loading" to "Nyandak…",
            "common.error" to "Aya kasalahan",
            "common.offline" to "Teu aya sambungan internet"
        )

        /** The dictionary for [language]; empty tables fall back to [ID]. */
        fun forLanguage(language: AppLanguage): Strings = when (language) {
            AppLanguage.ID -> Strings(ID)
            AppLanguage.EN -> Strings(EN)
            AppLanguage.MS -> Strings(MS)
            AppLanguage.JAWA -> Strings(JAWA)
            AppLanguage.SUNDA -> Strings(SUNDA)
        }
    }
}

/**
 * Current language strings. Provided at the app root from the persisted
 * [com.example.data.local.LanguageStore]; defaults to Indonesian so every
 * call site works even before the store loads.
 */
val LocalStrings = staticCompositionLocalOf { Strings(Strings.ID) }

/** Shorthand: `string("player.play")`. */
@androidx.compose.runtime.Composable
fun string(key: String): String = LocalStrings.current[key]
