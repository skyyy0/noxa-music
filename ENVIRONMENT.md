# NOXA — Konfigurasi Environment

NOXA membaca konfigurasi dari file `.env` di root proyek melalui
**Secrets Gradle Plugin** (`app/build.gradle.kts` → blok `secrets { ... }`),
lalu mengeksposnya sebagai field `BuildConfig` (dibaca lewat
`com.example.data.remote.Secrets`).

`.env` **tidak boleh di-commit** (sudah ada di `.gitignore`).

## Sumber musik (v1.8)

NOXA 1.8 memakai empat layanan publik. Semuanya **opsional** — aplikasi tetap
berfungsi penuh pada instalasi baru tanpa konfigurasi apa pun:

| Layanan | Dipakai untuk | Audio | Perlu key? |
| --- | --- | --- | --- |
| **Deezer** (`api.deezer.com`) | Katalog utama, termasuk musik Indonesia (Dewa 19, Sheila On 7, Iwan Fals, Tulus, NOAH, dll) dan chart global | Klip **preview 30 detik** resmi dari label (batas legal Deezer) | Tidak |
| **Audius** (`api.audius.co`) | Lagu penuh dari artis independen | **Lagu penuh** | Tidak |
| **YouTube** (search: YouTube Data API v3 opsional + instance Piped/Invidious; audio & MV: resolusi via instance Piped/Invidious saat play) | Katalog mainstream terluas | **Lagu penuh** (via instance) | Tidak |
| **LRCLIB** (`lrclib.net`) | Lirik sinkron (berpenanda waktu `[mm:ss.xx]`) | — | Tidak |

Di UI, lagu Deezer ditandai **`PREVIEW 30s`**, lagu Audius **`FULL TRACK`**,
dan lagu YouTube **`YOUTUBE • FULL`**. Pencarian menyatukan ketiganya, dengan
chip filter **Semua / YouTube / Deezer / Audius** di layar Search.

### Cara kerja sumber YouTube (v1.8)

Bagian yang tidak bisa dilakukan API resmi Google adalah **audio**: YouTube Data
API v3 hanya metadata dan tidak pernah mengembalikan URL audio. Karena itu NOXA
memakai arsitektur berikut, tanpa server apa pun:

1. **Pencarian** — YouTube Data API v3 bila `YOUTUBE_API_KEY` diisi (100 unit
   kuota Google per pencarian), plus selalu instance publik **Piped**
   (`/search?filter=videos`) dan **Invidious** (`/api/v1/search`) yang tidak
   butuh kunci. Hasil digabung & dide-duplikasi per video id.
2. **Audio saat play** — URL audio diresolusi lewat instance **Piped**
   (diprioritaskan; URL-nya di-proxy instance sehingga stabil) dan jatuh balik
   ke **Invidious**. Bila semua instance gagal, aplikasi menampilkan pesan
   error yang jujur — tidak pernah memutar rekayasa.
3. **Video MV** — pemutar penuh punya toggle **Foto / Video MV**. Video MV
   diputar di pemutar terpisah yang **selalu senyap**, jadi audio utama,
   equalizer, dan kontrol notifikasi tidak pernah terganggu. Kualitas video
   diarahkan ke ~360p (hemat kuota).
4. **Kebijakan penggunaan** — sebelum pemutaran YouTube pertama, dialog
   kebijakan ditampilkan sekali (disimpan di SharedPreferences).
5. **URL kedaluwarsa** — URL stream instance hanya hidup beberapa jam; NOXA
   meng-cache in-memory 4 jam dan menresolvi ulang saat dibutuhkan.

Instance publik bisa mati kapan saja. Daftar default bawaan aplikasi bisa
dioverride lewat `PIPED_INSTANCE` / `INVIDIOUS_INSTANCE` (pisahkan beberapa
dengan koma). `STREAM_RESOLVER_URL` (backend milik Anda, mis. yt-dlp sendiri)
tetap **didahulukan** untuk semua lagu bila diisi.

Catatan jujur: proyek ekstraksi audio YouTube (yt-dlp dsb.) adalah perangkat
lunak yang berubah hampir tiap minggu (signature cipher, PO Token) dan umumnya
berjalan di server Python — itulah sebabnya NOXA memakai instance Piped/
Invidious yang sudah menjalankannya, bukan mengimplementasikannya di dalam APK.
Hak cipta lagu tetap milik pencipta/label; kebijakan penggunaan ditampilkan ke
pengguna sebelum pemutaran pertama.

### Profil artis, album & audio utuh (v1.9)

* **Profil artis** — mengetuk nama artis (di daftar lagu mana pun atau di
  pemutar penuh) membuka halaman artis: foto, nama, centang verifikasi,
  jumlah pendengar, lagu teratas, dan rak album. Mengetuk album membuka
  **halaman album** berisi daftar lengkap lagu album itu. Sumber data:
  endpoint publik `artist/{id}`, `artist/{id}/albums`, `album/{id}` dan
  `album/{id}/tracks` dari Deezer (tanpa kunci).
* **Centang verifikasi** — **biru** untuk artis terverifikasi biasa,
  **merah** untuk artis besar dengan ≥ 1.000.000 pendengar (mis. Justin
  Bieber). Ambangnya berbasis data `nb_fan` dari Deezer, bukan daftar
  hardcoded. Jumlah pendengar juga ditampilkan apa adanya dari `nb_fan`.
* **Audio utuh untuk lagu preview** — saat play ditekan pada lagu ber-`PREVIEW 30s`
  dan kebijakan penggunaan sudah diterima, NOXA mencocokkan lagu itu ke
  video YouTube (pencarian judul + artis + durasi lewat instance; skor murni
  yang menolak karaoke, playlist, kompilasi, dan terlalu jauh durasinya),
  lalu menresolvi audionya lewat rantai Piped/Invidious yang sama dengan
  sumber YouTube. Contoh: Dewa 19 — Kangen diputar **utuh**, bukan 30 detik.
  Bila tidak ada kecocakan meyakinkan, aplikasi jujur memutar preview 30
  detik (badge tetap tampil); audio yang salah tidak pernah diputar diam-diam.
  Pengguna yang menolak kebijakan tidak ditanya lagi dan lagu tetap preview.
  `STREAM_RESOLVER_URL`, bila diisi, tetap didahulukan untuk semua lagu.

### v1.9.1 — audio utuh tanpa instance & perbaikan UI

* **NewPipeExtractor (ekstraksi di perangkat)** — library open-source (GPL-3)
  yang mengekstrak URL audio YouTube **langsung dari HP**, tanpa server
  Piped/Invidious. Urutan resolusi audio kini: `STREAM_RESOLVER_URL` →
  ekstraktor perangkat → Piped → Invidious. Pencarian YouTube juga punya
  jaring pengaman ekstraktor perangkat saat semua instance pencarian mati.
* **Semua lagu utuh, tanpa dialog** — kebijakan penggunaan kini diterima
  bawaan (keputusan pemilik: distribusi privat, tidak masuk Play Store).
  Lagu Deezer diputar **utuh**; badge menampilkan sumber ("DEEZER"), bukan
  "PREVIEW 30s". Bila kecocokan benar-benar gagal, lagu tetap diputar
  apa adanya — tidak pernah memutar audio yang salah.
* **Perbaikan UI** — halaman artis/album tidak lagi tertutup status bar
  (inset otomatis semua perangkat); navigasi bawah di-hoist agar selalu
  aktif dari setiap halaman.
* **Catatan teknis** — seluruh jaringan resolusi dipindah ke thread I/O;
  sebelumnya sebagian berjalan di main thread sehingga sentuhan bisa
  membeku seketika dan fallback gagal diam-diam ke preview 30 detik.

### v1.10 — Pin by Sky, akun email + username unik, pemutar lebih hidup

* **Kata Sky (pinned)** — "ada lagu yang cuma berdurasi beberapa menit, tapi
  bisa membawa kita kembali ke masa yang udah ga mungkin diulang." Lagu pin
  (Dewa 19 — Kangen) mendapat **rak khusus `📌 Pinned by Sky`** di Home lengkap
  dengan quote-nya, badge `⭐ Pinned` pada kartunya, bintang di pemutar penuh &
  antrian, serta blok quote "PINNED BY SKY" di bawah kontrol pemutar penuh.
* **Username unik (Firestore)** — pendaftaran email/password mendaftarkan
  username unik lewat Firestore: nama diklaim sebagai document id
  `usernames/{nama-huruf-kecil}` **di dalam transaksi**, jadi dua orang tidak
  bisa memilih nama yang sama. Masuk/daftar dengan email+password tanpa Google.
  **API key tambahan tidak diperlukan** — SDK Firestore pakai konfigurasi
  Firebase project yang sama dengan Auth. Bila Firestore belum di-setup atau
  offline, pendaftaran tetap jalan (username disimpan sebagai display name)
  dan aplikasi tidak pernah crash.
* **Lirik karaoke** — di pemutar penuh, baris lirik sinkron tampil di atas
  artwork (baris sekarang warna lime, baris berikutnya redup di bawahnya).
  Mengikuti posisi playback sungguhan; bisa dimatikan di Pengaturan.
* **Autoplay lagu serupa** — ~15 detik sebelum lagu terakhir sebuah antrian
  habis, sampai 5 lagu teratas artis yang sama ditambahkan ke antrian. Audio
  sudah diresolusi dulu, jadi yang masuk antrian benar-benar bisa diputar.
* **Rekap mingguan mini** — kartu "Rekap Minggu Ini" di Profile: jumlah lagu
  yang diputar 7 hari terakhir, artis teratas, dan lagu paling sering. Data dari
  tabel Room baru `play_records` (migrasi `MIGRATION_2_3` — favorit, history dan
  playlist lama tetap aman; record lebih tua dari 30 hari dibersihkan otomatis).
* **Pengaturan pemutar (baru)** — kualitas video MV (360p / 480p / 720p, pilihan
  mengikuti stream terdekat dan di-cache per kualitas), toggle **Autoplay lagu
  serupa**, toggle **Lirik karaoke**, dan toggle **Mulai otomatis saat dibuka**
  (melanjutkan sesi terakhir begitu aplikasi dibuka).
* **MV untuk lagu yang di-match** — video MV kini juga tersedia untuk lagu
  Deezer yang audionya sudah cocok ke video YouTube (`Song.matchedVideoId`);
  kualitasnya mengikuti pilihan di Pengaturan. Toggle MV kini rail vertikal di
  samping artwork (Foto / MV).
* **5 lagu pin Sky** — Kangen (Dewa 19), Pupus (Dewa 19), Dan (Sheila On 7),
  Terlalu Manis (Slank), dan Laskar Pelangi (Nidji), masing-masing dengan
  kata-kata Sky sendiri yang tampil di rak pin, di kartu, dan di pemutar penuh.

### v1.11 — kelima pin tampil, ikon vektor, akun tanpa Firebase, browse bergambar

* **Semua 5 lagu pin kembali tampil** — pencarian pin kini menemukan lagu lewat
  *top tracks* artis (kemudian fallback search), jadi "Dan" (Sheila On 7) dan
  "Laskar Pelangi" (Nidji) tidak lagi tenggelam di hasil pencarian umum.
  Quote kini menempel pada semua lagu pin berkat satu aturan matching yang
  dipakai bersama (judul dengan akhiran remaster/live/bracket tetap cocok).
* **Ikon vektor, bukan emoji** — rak pin, badge `Pinned`, bintang di pemutar
  penuh & antrian, ikon rekap mingguan, dan badge pada kartu share kini
  memakai ikon Material (PushPin/Star) dan star path hasil gambar sendiri,
  bukan glyph emoji.
* **Akun selalu bisa dibuat (tanpa keys)** — build rilis GitHub tidak
  membawa `google-services.json`, sehingga Firebase Auth/Firestore tidak
  tersedia dan pendaftaran gagal dengan pesan "belum dikonfigurasi". Kini
  ada **akun bawaan aplikasi**: email + password (hash PBKDF2-HMAC-SHA256,
  tidak ada password tersimpan), username unik **per perangkat**, sesi
  bertahan setelah app ditutup. Firebase tetap dipakai otomatis pada build
  yang membawa konfigurasinya (lengkap dengan registry Firestore).
* **Animasi ala iOS** — kartu rak pin & shelf masuk bertahap (fade + naik
  lembut), tombol play pemutar penuh & mini player memantul lembut saat
  ditekan, ikon play/pause pop saat berganti, mini player meluncur dengan
  spring.
* **Bagikan gambar diperbaiki** — URI kartu share kini ikut ditaruh di
  `ClipData` (pola resmi Android untuk meneruskan izin baca URI ke aplikasi
  tujuan); banyak share target/OEM mengabaikan `EXTRA_STREAM` semata, yang
  dulu bikin "Bagikan sebagai gambar" gagal. Setiap kegagalan kini juga
  tercatat di logcat (`ShareCard`) supaya mudah didiagnosis.
* **Explore Genres bergambar** — kartu browse di Search kini memakai foto
  artis asli dari Deezer + cover Global Top 50 dengan scrim gradasi gelap
  ala Spotify; kartu yang belum punya gambar tetap memakai warna temanya.
* **Package baru `com.noxa.music`** — embel-embel `aistudio` dilepas dari
  `applicationId`. Karena identitas instalasi berubah, APK baru terpasang
  sebagai app baru (uninstall app lama lebih dulu); data lokal (akun, Riwayat
  & Room) tidak ikut pindah.
* **MV lebih tangguh** — resolusi stream kini mencoba kualitas pilihan, lalu
  jatuh ke 360p dan 240p; kalau semua gagal dicoba ulang sekali setelah jeda.
  Bila stream keburu expired di tengah pemutaran, pemutar video memicu
  resolusi ulang otomatis (satu kali) alih-alih meninggalkan layar hitam.
* **Tiga fitur baru** — shelf **Dengar Terakhir** di Home (loncat cepat ke
  lagu yang baru didengar), tombol **Mix Sky** (antrian acak dari lagu pin +
  favorit + riwayat), dan **long-press pada baris lagu** mana pun untuk
  membuka menu cepat (antrian, playlist, bagikan) tanpa mencari tombol ⋮.

### v1.12 — crash fix, akun global + support chat, panel developer

* **Force-close "memuat lagu" diperbaiki** — feed Home dibangun fail-fast:
  satu kegagalan provider (HTTP error, bug mapping, OOM) membuat exception
  lolos dari coroutine view model dan membunuh aplikasi tepat saat Home masih
  menampilkan "Memuat musik...". Kini setiap rak melewati `safeShelf`
  (rak yang gagal hanya kehilangan raknya sendiri), `loadHome` punya
  `try/catch` dengan tombol coba lagi, dan jalur lirik/panas-antrian/MV ikut
  dijaga. **Crash logger**: trace exception terakhir disimpan ke
  `files/crash/latest-crash.txt` dan otomatis terlampir di laporan support
  pertama setiap giliran.
* **Registry akun global keyless (Telegraph)** — halaman Telegraph tunggal
  (`api.telegra.ph`) menjadi dokumen JSON bersama: semua instalasi membaca &
  menulis registry yang sama tanpa API key. Password tetap hanya hash
  PBKDF2-HMAC-SHA256 (60k iterasi). Pendaftaran/masuk kini mencoba server
  dulu, lalu jatuh ke akun lokal per-perangkat; akun yang dibuat di server
  otomatis di-mirror lokal (masuk offline tetap bisa).
* **Akun developer bawaan** — `skydev@noxa.com` / `skydevnoxa` role
  `DEVELOPER` (ter-seed di server). Masuk lewat form email biasa.
* **Chat NOXA Support** — menu "Chat dengan NOXA Support" di Profile:
  lapor bug/keluhan langsung dari aplikasi. Aturan Sky: user hanya bisa kirim
  **satu pesan sampai Support membalas**; composer terkunci otomatis.
  Badge verified **ungu** kustom (canvas + glow) khusus identitas Support.
* **NOXA Admin (panel developer)** — role DEVELOPER melihat kartu "NOXA
  Admin": jumlah akun terdaftar, semua thread laporan + balas instan, dan
  "Buat Akun untuk User" dengan role picker (Admin/Partner/Moderator/User)
  plus kartu info akun siap salin setelah dibuat.
* **Pengaturan akun** — ganti username & password dari aplikasi (verifikasi
  password lama; tersinkron ke server + mirror lokal).
* **Form daftar lebih matang** — bar kekuatan password (Lemah → Sangat
  Kuat) dan NOXA Bot: maskot robot vektor yang **menutup mata** saat kamu
  mengetik password (tidak ikut melihat, katanya).
* **Emoji diganti vektor** — sisa glyph emoji di UI (rekap, share text)
  diganti teks/icon Material; ikon baru digambar sebagai path vektor.

### v1.13 — NOXA AI (asisten musik di dalam app)

* **Chat NOXA AI** — menu "Chat dengan NOXA AI" di Profile membuka chat
  dengan asisten berbasis endpoint chat Omegatech (`api.omegatech.app`,
  tanpa API key) yang punya memori sesi (`sessionId`/`chatId` dipersist). **AI
  bisa kontrol musik**: prompt ketat menyuruh model mengakhiri balasan dengan
  tag `[NOXA_PLAY|artis|judul]` atau `[NOXA_PLAYLIST|nama|judul1;judul2]`,
  lalu aplikasi yang mengeksekusi — cari di katalog, putar, atau bikin
  playlist. Tag tidak pernah tampil di bubble; emoji di-strip dari balasan
  meski model nakal, dan prompt melarang emoji + menyamar sebagai AI lain.
* **Kepribadian & efek** — gaya santai/nyeleneh (curhat putusin pacar bisa
  berujung rekomendasi lagu yang nyeleneh). Saat AI "berpikir", muncul efek
  gradasi lime shimmer + avatar berdenyut ala AI modern. Kartu lagu
  "DIPILIH OLEH NOXA AI" tampil di chat lengkap dengan artwork & tombol
  putar; riwayat chat dipersist antar buka app.
* **Catatan Play Protect** — peringatan "aplikasi belum pernah dilihat
  developer ini" muncul karena NOXA disebarkan di luar Play Store; itu
  kebijakan sisi server Google dan tidak bisa dimatikan dari APK. APK NOXA
  ditandatangani keystore yang stabil (`my-upload-key.jks`) sehingga
  reputasi sertifikat menumpuk seiring waktu, dan instalasi tetap bisa
  dilanjutkan lewat "Tetap instal".

### v1.13.1 — tahan-crash jalur pemutar, layout semua ukuran layar, NOXA AI bahasa baku

* **Force-close "memuat lagu" ditangani menyeluruh** — semua 34 tugas latar
  view model kini berjalan di scope ber-handler (`safeScope`): throwable
  tak tertangkap (kegagalan parse ekstrem, lonjakan memori saat artwork
  Home ter-decode bersamaan dengan ekstraksi audio penuh, dll) sekarang
  muncul sebagai pesan error jujur di pemutar + tercatat di crash log,
  bukan membunuh aplikasi. Crash logger tetap menulis
  `files/crash/latest-crash.txt` yang otomatis terlampir di chat NOXA
  Support — jadi bila penyebabnya crash native (codec) yang tak bisa
  ditangkap kode mana pun, laporan dari aplikasi langsung membawa stack
  trace aslinya.
* **Layout semua perangkat** — chat NOXA Support, NOXA AI, dan NOXA Admin
  kini punya `statusBarsPadding` (tombol kembali tidak lagi tertutup jam
  di HP ber-status-bar tinggi) dan lebar maksimal 680dp yang di-center,
  sehingga nyaman di HP kecil, HP tinggi/lebar, maupun tablet.
* **NOXA AI bahasa baku** — persona diubah ke bahasa Indonesia baku yang
  rapi dan profesional (tanpa bahasa gaul), sapaan "Anda", kesan aplikasi
  resmi; saran chat yang tidak pantas dihapus. Efek & kontrol musik tetap.

### v1.13.3 — tombol skip full-track, crash rak pin, keyboard chat, akun global

* **Tombol skip pemutar penuh kini full-track** — skip (lanjut/sebelumnya)
  dulu langsung lompat ke entri antrian mentah, jadi lagu Deezer berikutnya
  mulai sebagai klip preview 30 detik; lagu utuh hanya didapat dengan keluar
  dan menekan lagunya manual. Kini target skip di-resolve dulu persis seperti
  tap (ladder resolusi bersama + `pendingNextIndex`/`pendingPreviousIndex` di
  pemutar, hormati shuffle/repeat), dan entri antrian tetap fallback jujur
  bila stream utuh tidak tersedia.
* **Force-close rak pin (duplikat key) diperbaiki** — resolusi pin kini
  memakai matcher *per-pin*: "Kangen" dan "Pupus" (sama-sama Dewa 19) dulu
  bisa merebut lagu top-track yang sama → satu lagu tampil dua kali di rak →
  LazyRow melempar `IllegalArgumentException: Key "deezer:…" was already
  used` tepat saat Home selesai memuat. Semua rak Home (dan Audius) juga
  kini `distinctBy { it.id }` sebagai jaring pengaman, dan key chat AI
  berganti posisional (dua balasan AI identik dulu bisa crash list chat).
* **Keyboard chat tidak lagi menumpuk ke atas** — komposer NOXA AI &
  NOXA Support kini memakai `ime ∪ navigationBars` (padding = terbesar dari
  keduanya) plus `android:windowSoftInputMode="adjustResize"` pada activity:
  di HP dengan tombol navigasi 3-button, input dulu terdorong dobel ke atas
  saat keyboard muncul.
* **Login akun global diperbaiki** — sign-in & daftar kini konsultasikan
  registry Telegraph (Telegraph = database tanpa key) di SEMUA build; dulu
  build ber-Firebase melompatinya, sehingga akun developer `skydev@noxa.com`
  (yang hanya hidup di registry) tidak pernah bisa masuk. Baca registry
  kini retry dengan backoff, dan server yang tak terjangkau melapor jujur
  ("server tidak terjangkau"), bukan menyalahkan password.
* **Firebase valid kembali** — `google-services.json` untuk
  `com.noxa.music` ikut di-commit (repo privat, pola sama dengan keystore
  rilis): Firebase Auth/Firestore & Google Sign-In aktif otomatis di setiap
  build CI, tanpa secret tambahan. Firebase jadi lapis kedua setelah
  registry (konflik email diselesaikan registry dulu).
* **Lapor bug via Telegram** — baris WhatsApp di Profil diganti Telegram
  (ikon vektor pesan kertas, `@sekaynotdev`); laporan tetap bisa lewat Chat
  NOXA Support di dalam aplikasi.
* **Versi** — `versionName`/`versionCode` akhirnya ikut rilis: **1.13.3 (10)**.
  Android minimal: **7.0 (API 24)**.

### v1.13.4 — Google Sign-In mandiri & login email tanpa provider Firebase

* **Masuk dengan Google benar-benar jalan** — pemilih akun Google dulu
  kembali ke NOXA tanpa login karena token diserahkan ke Firebase Auth,
  yang menolaknya (provider Google belum diaktifkan di proyek Firebase).
  Kini token diverifikasi **langsung terhadap kunci publik Google**
  (`GoogleIdTokenVerifier`, RS256 + iss/aud/exp — keyless, tanpa setup
  console): sesi dibuat lokal dan bertahan setelah app ditutup. Firebase
  tetap jadi cadangan bila kunci Google tak terjangkau. Error Google kini
  juga tampil di layar profil (dulu hilang senyap).
* **Login/daftar email tidak lagi menyentuh provider Firebase** — urutannya
  registry → mirror lokal; provider Email/Password memang nonaktif di
  proyek Firebase (dulu error Inggris "operation-not-allowed" bocor ke
  pengguna). Registry yang tak terjangkau kini melapor jujur ("server
  tidak terjangkau"), bukan menyalahkan password.
* **Versi** — `versionName` **1.13.4** / `versionCode` 11.

### v1.14 — halaman Get Started: Google · Email · Phone (OTP)

* **Satu pintu masuk "Get Started"** — kartu tamu di Home dan kartu login
  di Profile kini hanya satu tombol **Get Started** (bukan lagi tombol
  Google + form email langsung di sana). Sekali klik membuka halaman hub
  berisi pilihan metode: **Continue with Google**, **Continue with Email**,
  dan **Continue with Phone**. Form email (masuk/daftar, NOXA Bot, bar
  kekuatan password) pindah ke halaman itu, lengkap dengan opsi "Maybe
  later — continue as guest".
* **Masuk nomor telepon (OTP SMS) — baru** — verifikasi nomor lewat
  Firebase Phone Auth: input E.164 (`+62812…`), kode6 digit dikirim via
  SMS, layar verifikasi + tombol ganti nomor; di perangkat yang mendukung
  verifikasi instan, login bisa terjadi tanpa mengetik kode. Pesan error
  diterjemahkan jujur (nomor salah, kode salah/kedaluwarsa, kuota, provider
  nonaktif, butuh penagihan Blaze, region SMS). **Syarat kirim SMS** di
  konsol Firebase: plan Blaze aktif dan kebijakan region SMS mengizinkan
  Indonesia. Provider Email/Password, Phone, dan Google semuanya sudah
  aktif di proyek.
* **Email/Password tanpa OTP** — riset Firebase: login email memakai
  email + password biasa; kode OTP tidak terlibat. Verifikasi *email*
  (tautan konfirmasi) hanya opsional dan terpisah — NOXA tidak memakainya.
  OTP khusus jalur telepon.
* **Fix verifikasi token Google** — JWKS Google
  (`www.googleapis.com/oauth2/v3/certs`) ternyata mengeluarkan kunci RSA
  telanjang **tanpa rantai `x5c`**, sehingga jalur parse lama selalu kosong
  dan verifikasi token jatuh ke Firebase. Kini `n`/`e` (modulus/exponent
  base64url) diparse langsung jadi `RSAPublicKeySpec`; `x5c` tetap jadi
  cadangan.
* **Versi** — `versionName` **1.14.0** / `versionCode` 12.

### v1.14.1 — login email/Google tangguh, ikon Google resmi, dukung developer (QRIS)

* **Login/daftar email jadi tiga lapis** — urutan barunya: registry keyless
  (Telegraph) → **Firebase Email/Password** (provider kini aktif di proyek;
  tanpa SHA, langsung jalan) → akun lokal. Kalau registry tidak terjangkau,
  email yang sudah ada di Firebase tetap bisa masuk, dan pendaftaran tetap
  berhasil lewat Firebase sebelum jatuh ke lokal. Email yang sudah terdaftar
  di Firebase otomatis dipulihkan (sign-in) saat daftar ulang.
* **Login Google — akar masalah terdiagnosa** — token Google diverifikasi
  lokal terhadap kunci publik Google (jalur utama, terbukti oleh unit test
  baru yang menandatangani JWT sendiri + JWKS format Google tanpa `x5c`).
  Cadangan Firebase selama ini **pasti gagal** karena `google-services.json`
  tidak punya klien Android (client_type 1) untuk package `com.noxa.music`
  — hanya klien web. **Yang harus di konsol Firebase**: daftarkan app
  `com.noxa.music` dengan SHA-1 & SHA-256 keystore rilis, lalu unggah
  google-services.json baru. Error `MISSING_ANDROID_INTEGRATION` kini
  diterjemahkan jujur, dan setiap tahap picker→verifikasi→Firebase
  tercatat di logcat (`AuthManager`).
* **Ikon Google resmi** — tombol Google di halaman Get Started kini
  memakai ikon “G” empat warna resmi Google (vektor), bukan huruf G.
* **Support Developer NOXA** — menu baru di Profile (Direct Links) membuka
  popup mengambang berisi gambar QRIS untuk mendukung pembangunan &
  pengembangan NOXA.
* **Versi** — `versionName` **1.14.1** / `versionCode` 13.

### v1.15 — push update in-app, akun developer Google, hapus akun, cooldown

* **Push Update in-app (fitur baru)** — akun developer (NOXA Admin) punya
  tab **Push Update**: isi link APK (file langsung `.apk` atau link
  MediaFire — di-resolve otomatis ke host filenya), version name/code, dan
  catatan, lalu **Share Update ke Semua User**. Setiap user dengan version
  code lebih rendah langsung menerima **popup update wajib** (tidak bisa
  ditutup), mengunduh APK **langsung dari aplikasi** (DownloadManager +
  notifikasi), dan begitu selesai **panel instalasi Android resmi terbuka
  otomatis** — user tidak perlu mencari file hasil unduhan. Entry update
  disimpan di registry backend (`update`: versionCode/versionName/url/note).
* **Akun developer auto-detect** — login dengan Google
  `sekay77771@gmail.com` otomatis mendapat role `DEVELOPER` (juga di jalur
  Firebase email), jadi inbox laporan user langsung terbuka dari NOXA
  Admin tanpa perlu login email developer.
* **Sign out ada konfirmasi** — tombol Sign Out kini membuka popup
  Yes/No ("Yakin ingin keluar?").
* **Hapus akun** — menu **Hapus Akun** di Pengaturan Akun: ketik ulang
  email untuk konfirmasi, lalu akun dihapus permanen dari registry
  (termasuk thread chat support), Firebase (bila sesi dari sana), dan
  mirror lokal; sesi langsung berakhir.
* **Cooldown akun** — ganti username maksimal **1x tiap 7 hari**, ganti
  password maksimal **1x tiap 14 hari**. Aturan disimpan di registry
  (berlaku lintas perangkat) + mirror lokal (jaga-jaga offline); pesan
  sisa hari ditampilkan jujur.
* **5 lagu pin kembali lengkap** — "Laskar Pelangi" (Nidji) ternyata
  sudah tidak ada di katalog Deezer (delisted), sehingga rak pin hanya
  menampilkan 3–4 lagu. Kini pin yang gagal di Deezer otomatis dicari di
  sumber YouTube, jadi kelima pin selalu tampil. Quote Kangen diperbarui
  menjadi: *"ada lagu yang hanya berdurasi beberapa menit, tapi bisa
  membawa kita kembali ke masa yang sudah tidak mungkin dapat diulang."*
  dan subtitle rak menjadi **"Lagu khusus pilihan Sky"**.
* **Keyboard chat diperbaiki** — composer chat Support/AI sempat melayang
  di tengah layar dengan jarak setinggi keyboard saat keyboard terbuka
  (beberapa OEM mengerjakan `adjustResize` DAN inset Compose sekaligus →
  layout menyusut dua kali). Manifest kini mengandalkan inset edge-to-edge
  saja.
* **Versi** — `versionName` **1.15.0** / `versionCode` 14.

### v1.15.5 — widget Now Playing, visualizer spektrum, Dengar Bareng, efek ketik

* **Widget layar utama "Now Playing" (baru)** — widget launcher berisi artwork,
  judul, artis, dan tombol **play/pause + next**. Widget tidak punya state
  sendiri: ia membaca facade pemutar bersama (`player.uiState`) yang sama dengan
  mini player, jadi tidak pernah beda tampilan dengan notifikasi. Artwork diambil
  lewat Coil di background (di-cache, 256px supaya aman batas Binder), ketuk
  bagian lain membuka NOXA, dan setelah proses dibunuh sistem widget tetap
  menampilkan lagu terakhir dari sesi tersimpan tanpa autoplay. Broadcast kontrol
  pakai `exported="false"` — sistem tetap bisa mengirim update widget,
  aplikasi lain tidak bisa menyadap tombolnya.
* **Visualizer spektrum di pemutar penuh (baru)** — baris FFT live di atas
  artwork, dua gaya (bars/wave) ikut tema lime NOXA. Sumbernya
  `audiofx.Visualizer` pada sesi audio yang sama dengan equalizer; karena Android
  memperlakukannya sebagai audio monitoring, izin mikrofon hanya diminta saat
  user menyalakan visualizer — tanpa izin / sesi mati / perangkat tanpa effect,
  tampilan jujur menampilkan garis datar, bukan palsu atau crash.
* **Dengar Bareng (room, baru)** — kartu entry di Home/Profil dan tab navigasi:
  buat room dengan kode, teman masuk pakai kode, lalu **lagu & posisi pemutaran
  sinkron** antar anggota. State room lewat backend registry (`rooms`):
  publish/read/join/leave + pruning otomatis; kode di-normalisasi agar aman
  diketik ulang. Layar room punya status Joining/Failed/InRoom yang jujur.
* **Balasan AI mengetik sendiri (typewriter)** — balasan terbaru di chat NOXA AI
  terungkap karakter demi karakter dengan caret lime berkedip; transcript lama
  langsung tampil penuh (dan tidak mengetik ulang saat di-scroll).
* **Versi** — `versionName` **1.15.5** / `versionCode` 15.

### Berbagi lagu: link NOXA & kartu canvas (v1.10)

Sebelumnya membagikan lagu mengirim link Deezer (`deezer.com/track/…`). Sejak
v1.10 tombol bagikan memakai sheet NOXA sendiri dengan dua pilihan:

1. **Bagikan sebagai gambar** — kartu canvas ala Spotify (artwork membulat,
   judul, artis, badge ⭐ untuk lagu pin, branding NOXA + skyvibes.my.id)
   dirender sebagai gambar 1080×1080, **otomatis tersimpan ke galeri**
   (`Pictures/NOXA`, tanpa izin di Android 10+), lalu dikirim lewat share
   sheet sistem (WhatsApp dll) memakai FileProvider.
2. **Salin link NOXA** — menyalin deep link `https://skyvibes.my.id/track/<id>`
   (contoh: `…/track/deezer%3A76056788`).

Link tersebut dibuka **langsung di pemutar NOXA** lewat intent-filter
(`https://skyvibes.my.id/track/*` dan skema cadangan `noxa://play/<id>`,
`launchMode=singleTask` supaya app yang sedang terbuka tidak dibuat ulang).
Lagu di-resolve ulang berdasarkan id: `deezer:<id>` lewat `GET /track/{id}`,
`audius:<id>` lewat `GET /v1/tracks/{id}`, dan `youtube:<id>` lewat ekstraktor
di perangkat (judul/artis ikut terbawa).

> Catatan Android: untuk https tanpa verifikasi App Links, Android menampilkan
> pilihan aplikasi (chooser) saat link diketuk — pilih NOXA dan ingat pilihan
> itu. Agar tanpa chooser, host `assetlinks.json` di `skyvibes.my.id`
> (package `com.noxa.music`) sesuai docs Android App Links.
> Pesan teks share tetap memakai format `🎵 Judul — Artis` + link +
> `Didengarkan lewat NOXA Music`.

Aturan Firestore yang disarankan untuk registry username (console → Firestore
→ Rules) — pembuatan nama hanya untuk pemiliknya, nama yang sudah diklaim tidak
bisa diambil alih:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /usernames/{name} {
      allow read: if true;
      allow create: if request.auth != null &&
        request.resource.data.uid == request.auth.uid;
      allow update, delete: if request.auth != null &&
        resource.data.uid == request.auth.uid;
    }
  }
}
```

> Catatan jujur: Firestore menjamin uniqueness lewat document id dalam
> transaksi, sehingga registry tetap benar bahkan dengan rules test-mode;
> rules di atas hanya mencegah orang lain mengubah/menghapus nama yang sudah
> diklaim. Tanpa `google-services.json`, email/password juga tidak tersedia
> (build tetap jalan sebagai mode tamu).

### v1.15.6 — next lagu tunggu audio utuh, tanpa nama provider, APK lebih besar

* **Next lagu menunggu audio utuh (fix utama)** — saat lagu selesai dan pemutar
  berpindah otomatis ke lagu berikutnya, trek Deezer yang belum sempat dihangatkan
  dulu diputar sebagai klip 30 detik. Kini transisi ditahan: klip singkat dijeda
  sebelum terdengar, audio utuh diambil dulu, baru lagu diputar dari awal. Bila
  memang tidak ada audio utuh, preview jujur dilanjutkan. Pemanasan antrian kini
  bergulir (rolling) sehingga penahanan jarang terjadi.
* **Pemutar pulih sendiri saat jaringan goyang** — bila stream terputus di
  tengah lagu (URL instance kedaluwarsa, koneksi putus seketika, HTTP error),
  pemutar diam-diam mengambil URL baru dan melanjutkan dari posisi terakhir —
  sampai dua kali percobaan per kegagalan. Error hanya tampil bila semua
  percobaan gagal, jadi pemutaran terasa mulus tanpa campur tangan user.
* **Quote pin "Laskar Pelangi" kembali tampil** — lagu pin yang diambil dari
  YouTube (karena sudah tidak ada di katalog) kini tetap mendapat kata-kata
  Sky dan bintang pin di kartu maupun pemutar penuh; urutan rak pin juga
  kembali sesuai daftar.
* **Tanpa nama provider di UI** — badge pemutar tak lagi menulis "YouTube" / 
  "Deezer" (kini cuma `FULL`), chip filter sumber di Search dihapus, dan pesan
  error tidak lagi menyebut nama layanan — NOXA berdiri sendiri sebagai aplikasi
  musik, bukan tampak seperti depanan layanan lain.
* **APK 30–50 MB** — minifikasi & resource-shrinking R8 dimatikan lagi seperti
  versi 1.15.0, sehingga ukuran APK kembali besar dan tidak lagi terkesan "cuma
  6 MB". Konfigurasi resource (Google/Firebase) tetap utuh.
* **Build CI lebih cepat** — GitHub Actions kini menyimpan cache Gradle
  (`~/.gradle`) antar-run, jadi build berikutnya memakai ulang dependensi & build
  cache dan selesai jauh lebih cepat.

> Catatan jujur untuk **login Google**: "belum dikonfigurasi" muncul ketika APK
> release tidak membawa `google-services.json` (resource `default_web_client_id`
> hilang). Ini bukan regresi kode — sejak awal APK dari GitHub Actions hanya
> memuat file itu bila secret `GOOGLE_SERVICES_JSON_BASE64` diisi (lihat
> [Mendapatkan file APK](#mendapatkan-file-apk)). Isi secret itu agar login
> Google ikut ter-baking ke APK yang didistribusikan.

### v1.15.7 — fix force-close NPE, NOXA AI Gemini2, beranda ala Spotify + statistik

* **Force-close `NullPointerException` diperbaiki (fix utama)** — crash
  `MutableStateFlow.setValue on a null object reference` di
  `MusicViewModel$2$1.emit` terjadi karena collector role DEVELOPER yang
  diluncurkan dari `init` menyentuh `_updateInfo` **sebelum** deklarasinya
  terjalankan. `viewModelScope` memakai `Dispatchers.Main.immediate`, jadi
  emisi pertama StateFlow berjalan sinkron tepat saat konstruksi — dan akun
  Sky yang otomatis masuk kembali langsung memicunya. Collector kini dipindah
  ke bawah deklarasi `_updateInfo`, sehingga field-nya selalu siap.
* **Pin "Laskar Pelangi" dihapus atas permintaan Sky** — rak pin kini 4 lagu
  (Kangen, Pupus, Dan, Terlalu Manis). Aturan matching tetap longgar untuk
  sumber YouTube bila ada pin yang tak tersedia di katalog.
* **NOXA AI pindah ke Omegatech Gemini2** — provider `Chatgpt-v2` mulai error;
  kini memakai `api.omegatech.app/api/ai/Gemini2` (tanpa API key). Balasan
  dibaca dari `data.response` (fallback `data.reply`), `sessionId` tetap
  disimpan dan diputar balik tiap pesan sehingga ingatan obrolan lintas
  sesi tetap hidup. Persona, tag aksi pemutar, dan aturan tanpa emoji tetap
  sama.
* **Beranda ala Spotify** — tiga blok baru:
  **Putar cepat**, grid 2 kolom lagu yang paling sering diputar (fallback ke
  riwayat terakhir), **Statistik kamu**, kartu jumlah lagu 7 hari + chip artis
  teratas dengan jumlah putaran, dan rak **Buat kamu**, rekomendasi dari selera
  sendiri dengan subjudul "Karena kamu sering dengar …".
* **Rekomendasi dari statistik pemakaian (on-device)** — engine rasa baca
  `play_records` 7 hari terakhir: 2 artis paling sering didengar → lagu
  teratas mereka + lagu teratas **artis terkait Deezer** (sinyal "genre
  mirip" yang jujur, bukan tebakan genre dari judul). Hanya di-refresh saat
  selera berubah; gagal jaringan = rak disembunyikan, tidak pernah crash.
  Data tetap di perangkat, tidak dikirim ke mana pun.
* **Detail kecil ala iOS** — getaran halus (haptic) di tab navigasi, tombol
  play/pause mini player & pemutar penuh, dan hati favorit (getar lebih
  berat); ikon tab terpilih membesar 12% lalu mendarat dengan spring; rak &
  kartu baru masuk bertahap (stagger) dengan fade + naik lembut.

### v1.15.8 — Vote Skip, Peta Panas Dengar + Wrapped Mini, keamanan menyeluruh

* **Vote Skip di Dengar Bareng** — anggota room bisa vote untuk lanjut ke
  lagu berikutnya. Mayoritas ketat (`anggota/2 + 1`): 2 orang = 2 vote,
  5 orang = 3 vote. Eksekusi deterministik — semua perangkat menghitung
  *skipper* yang sama (uid pertama terurut), jadi walaupun lima perangkat
  melihat mayoritas, room hanya melewati lagu **satu kali**. Bila skipper
  keluar, kepemimpinan pindah otomatis ke uid berikutnya. Suara dari member
  yang sudah keluar room dibuang; setiap perubahan playback (ganti lagu /
  play-pause) otomatis mereset voting.
* **Peta Panas Dengar** — heatmap 365 hari ala GitHub di Profile: satu kotak
  lime per hari, kecerahan mengikuti jumlah putaran, plus total putaran,
  hari aktif, streak sekarang & streak terbaik. Semua dihitung di SQLite
  (agregat per hari) — baris mentah tidak pernah menumpuk di memori.
* **NOXA Wrapped Mini** — kartu rekap setahun di bawah heatmap: total
  putaran, jumlah artis berbeda, 3 artis teratas, lagu paling sering, dan
  "jam paling rajin dengar". Data sepenuhnya on-device (Room `play_records`),
  retensi kini 365 hari (dulu 30) karena heatmap butuh setahun.
* **Pop-up update dikunci per-versi** — aturan tunggal di `UpdateGate`:
  popup **hanya** muncul untuk instalasi yang `versionCode`-nya di bawah
  versi yang di-push. Pengguna di versi terbaru (atau lebih baru) tidak
  pernah melihatnya lagi; state basi dibersihkan, dialog tidak bisa dibuka
  ulang dari Pengaturan bila versinya sudah sama, dan DEVELOPER tetap kebal.
  Aturannya kini punya unit test (`UpdateGateTest`).
* **Keamanan (mode seimbang)**
  * **APK update diverifikasi** — sebelum panel instalasi dibuka, file yang
    diunduh dicek tanda tangannya harus sama dengan NOXA yang terpasang;
    file hasil tukar/patch palsu ditolak dengan pesan jujur.
  * **HTTPS wajib** — network security config memblokir cleartext (http)
    global dan hanya mempercayai sistem CA. Catatan: `STREAM_RESOLVER_URL`
    kini wajib `https://`.
  * **Backup data dimatikan** (`allowBackup=false`) — ledger akun lokal
    (hash PBKDF2), sesi, dan Room tidak lagi ikut backup cloud/adb.
  * **Brute-force login dibatasi** — 5 percobaan salah pertama bebas,
    lalu jeda naik 15s → 30s → 1m → 2m → maks 5 menit (per perangkat,
    per email); password benar memaafkan seluruh hitungan. Aturan murninya
    punya unit test (`LoginThrottleTest`).
  * **Log debug dibuang dari rilis** — R8 `assumenosideeffects` menghapus
    `Log.v`/`Log.d` (URL stream, email) dari APK release; `i/w/e` tetap
    ada untuk diagnostik jujur.

### v2.0.0 — Sistem Pintar, follow artis + radar rilis, cari pakai lirik

Rilis besar pertama NOXA 2.x: aplikasi kini mengenali perangkatnya sendiri
sebelum memutar apa pun, dan tiga alat baru untuk menemukan musik.

* **Sistem Pintar (sekali di awal)** — saat pertama dibuka, NOXA memindai
  RAM, jumlah inti prosesor, dan chipset, lalu memilih satu tier performa:
  **Hemat** (≤ 2 GB / flag low-RAM), **Seimbang** (≤ 6 GB), atau **Ngebut**.
  Tier ini menentukan berapa banyak lagu yang di-*warm* di depan (1/2/4),
  jendela pra-muat stream (30/45/60 detik sebelum lagu habis), panjang
  fade artwork, dan apakah crossfade diizinkan (HP kelas Hemat melewatkannya
  demi kelancaran). Layar pemindaian tampil sekali, lengkap dengan daftar
  probe yang menyala satu per satu; hasilnya bisa dilihat di Profile →
  Sistem Pintar. Aturannya murni dan punya unit test (`PerfProfileTest`).
* **Ikuti artis + Radar Rilis** — tombol **Ikuti** di halaman artis menyimpan
  artis ke Room (`followed_artists`); Home kini punya shelf **Radar Rilis**
  berisi album baru (≤ 90 hari) dari artis yang diikuti, lengkap dengan
  tipe rilis (Album/Single/EP) dan tanggal rilisnya. Migrasi Room 3→4
  menambah tabel tanpa menyentuh data lama.
* **Cari pakai lirik** — di Search ada mode baru: ketik sepenggal lirik
  (mis. "kuingin kau tahu perasaanku"), LRCLIB mencari lagu mana yang
  membawa kata-kata itu, lalu NOXA mencocokkannya ke katalog yang benar-benar
  bisa diputar. Baris lirik yang dicocokkan ditampilkan di atas hasil.
* **Crossfade lembut** — transisi antar lagu kini punya kurva volume
  (`CrossfadeRules`: fade-out 4 dtk di ekor lagu, fade-in 1,5 dtk di awal
  berikutnya), bisa dimatikan di Pengaturan → Pemutar. Lagu yang durasinya
  lebih pendek dari gabungan kedua fade tetap konstan 0,5 agar tetap
  terdengar. Sleep timer tetap mendapat prioritas atas volume.
* **Tema dinamis artwork** — latar pemutar penuh mengambil warna dominan
  dari sampul lagu yang sedang diputar (kuantisasi 64 px, warna kelabu &
  terlalu gelap diabaikan), jadi pemutar ikut "bernapas" mengikuti album.
* **Gestur swipe-down di pemutar penuh** — gagang di atas bisa ditarik ke
  bawah untuk menutup pemutar (iOS-style); sheet mengikuti jari, meredup
  seiring jarak tarikan, lalu menutup atau memantul kembali.
* **Beri bintang & "Jangan putar lagi"** — menu ⋮ di baris lagu mana pun
  (Search, Artist, Library) kini punya rating 1–5 bintang serta pembisuan
  per lagu dan per artis. Lagu/artis yang dibisukan dikeluarkan dari antrian
  dan dilewati oleh autoplay; daftarnya bisa dibatalkan per item di
  Profile → Jangan putar lagi.

### v2.1.0 — Komentar per Lagu, NOXA Wrapped tahunan, statistik dev, detail pemutar

* **Komentar per Lagu** — ikon komentar baru di bar atas pemutar penuh membuka
  sheet komentar bersama per lagu. Dokumen Telegraph khusus
  (`NOXA-Song-Comments-10-18`, satu halaman per aplikasi, tanpa API key)
  menyimpan JSON `songs → [komentar]` — setiap instalasi membaca & menulis
  thread yang sama. Batas jujur: teks maksimal 400 karakter, 40 komentar
  terbaru per lagu, cooldown 10 detik per perangkat, dan tulis optimistis
  ber-`seq` (retry singkat) supaya kiriman pengguna lain tidak tertimpa.
  Komentar butuh akun masuk; tamun melihat prompt "Masuk untuk berkomentar".
  Gagal jaringan = pesan jujur, draft tetap ada di kolom komentar.
* **NOXA Wrapped Tahunan** — kartu "NOXA Wrapped Mini" di Profile kini punya
  tombol ke layar Wrapped lengkap (route `wrapped`): total lagu setahun,
  hari mendengar, artis berbeda, hari terbanyak, artis jiwamu, 5 artis & 5
  lagu paling sering, jam paling rajin dengar + streak, peta panas 365 hari,
  dan penutup privasi ("semua angka ini berasal dari HP-mu sendiri"). Sumber
  datanya sama dengan peta panas (`Room.play_records`, retensi 365 hari,
  agregat di SQLite) — tanpa data, layar mengatakan "Wrapped-mu belum
  terbentuk" apa adanya. Bagian muncul bertahap ala story.
* **Statistik developer (NOXA Admin)** — kartu kehadiran baru: "Aktif hari
  ini", "Online sekarang", "Akun baru hari ini" + titik hijau per akun.
  Backend kini menyimpan stempel `lastSeenAt` per akun; klien menulis
  heartbeat **dibatasi 1 sekali per 3 menit** (dibaca-ditulis ulang, bukan
  tulis membabi buta), "online" = stempel < 2 menit. Semua angka dihitung
  dari dokumen bersama — 0 bila memang belum ada, tidak pernah dikarang.
* **Tombol "Putar album" kini punya status** — saat lagu album sedang diputar,
  tombol berubah jadi "Jeda" (ikon ❚❚), saat berhenti "Lanjutkan"; ikon
  pop saat berganti, tekanan memantul lembut + haptic (noxaTap) seperti
  tombol lain di NOXA.
* **Multi-bahasa** — Pengaturan → Bahasa kini menerjemahkan label navigasi
  bawah, dialog pembaruan, dan bagian profil/artis; tabel sumber kebenaran
  di `ui/i18n/Strings.kt` (ID sebagai fallback untuk kunci yang belum ada).

## Variabel `.env` (semuanya opsional)

| Variabel | Wajib | Keterangan |
| --- | --- | --- |
| `AUDIUS_API_KEY` | Tidak | Kunci developer Audius. Tanpa ini pun Audius tetap bisa dibaca (anonim). Bila diisi, dikirim sebagai header `x-api-key`. |
| `STREAM_RESOLVER_URL` | Tidak | Endpoint milik Anda sendiri. Bila diisi, setiap lagu yang mau diputar ditawarkan ke resolver ini **didahulukan** untuk mendapatkan URL audio **utuh** (lihat di bawah). |
| `YOUTUBE_API_KEY` | Tidak | Kunci YouTube Data API v3 untuk pencarian (kuota 100 unit/pencarian). Tanpa ini pencarian YouTube tetap jalan lewat instance Piped/Invidious. |
| `PIPED_INSTANCE` | Tidak | Override instance Piped (dipisah koma). Bila kosong, memakai daftar default bawaan aplikasi. |
| `INVIDIOUS_INSTANCE` | Tidak | Override instance Invidious (dipisah koma). Bila kosong, memakai daftar default bawaan aplikasi. |

Contoh isi `.env`:

```dotenv
AUDIUS_API_KEY=
STREAM_RESOLVER_URL=https://backend-anda.example/api/resolve
YOUTUBE_API_KEY=
PIPED_INSTANCE=
INVIDIOUS_INSTANCE=
```

> Catatan: nama variabel baru di atas ditambahkan ke `.env.example` secara
> manual bila dibutuhkan; Secrets Plugin membaca `.env` Anda, dan bila sebuah
> nama tidak ada di BuildConfig aplikasi tetap aman (kembali ke string kosong).

### Cara kerja `STREAM_RESOLVER_URL` (jalur lagu utuh)

Deezer hanya mengizinkan klip 30 detik, dan Audius tidak punya katalog
mainstream Indonesia. Satu-satunya jalur legal untuk memutar lagu **utuh** dari
katalog mainstream adalah backend milik Anda sendiri (mis. CDN berlisensi atau
layanan berlangganan Anda). NOXA tidak menyertakan scraper apa pun; ia hanya
menanyakan URL ke endpoint yang Anda tentukan.

Saat pengguna menekan play, NOXA memanggil:

```
GET <STREAM_RESOLVER_URL>?id=<song id>&source=<AUDIUS|DEEZER>&title=<judul>
                         &artist=<artis>&durationMs=<ms>&remoteId=<id provider>
Accept: application/json
```

Resolver menjawab salah satu dari ini:

```json
{ "streamUrl": "https://cdn.anda/lagu-utuh.mp3" }
```

```json
{ "url": "https://cdn.anda/lagu-utuh.mp3" }
```

Kalau resolver membalas 404, badan kosong, atau tanpa URL, lagu tetap diputar
dari sumber aslinya (preview Deezer / Audius). URL harus `https://` dan
langsung bisa diputar Media3 (mp3/aac/ogg; HLS `.m3u8` juga didukung ExoPlayer).

Karena pencarian menunggu jawaban resolver sebelum mulai memutar, buat resolver
Anda cepat (di bawah ~1 detik). Batas waktu di aplikasi: 6 detik koneksi,
10 detik baca.

> Tanggung jawab Anda: apa pun yang Anda taruh di balik endpoint itu. Bila
> `STREAM_RESOLVER_URL` kosong, lagu YouTube ditandai dan dibatalkan (dengan
> pesan error yang jujur); lagu Deezer/Audius tetap diputar dari sumber
> resminya.

## Membaca konfigurasi dengan aman

`com.example.data.remote.Secrets` membaca field `BuildConfig` secara reflektif.
Dengan begitu, bila `.env` tidak ada (mis. clone baru) proyek tetap dapat
di-build, dan aplikasi menampilkan status "belum dikonfigurasi" alih-alih gagal
kompilasi.

## Mendapatkan file APK

APK dibangun otomatis oleh GitHub Actions pada setiap push ke `main`
(`.github/workflows/android-apk.yml`) dan diterbitkan sebagai **GitHub Release**,
sehingga selalu ada tautan unduh yang menetap:

```
https://github.com/skyyy0/noxa-app/releases/latest
```Yang dibangun secara default adalah **APK release yang sudah ditandatangani**
dengan keystore repositori (`my-upload-key.jks`, ter-commit di repo privat ini),
sehingga bisa langsung dipasang tanpa setelan apa pun. `codemagic.yaml` tetap
disimpan, tetapi sudah tidak diperlukan.

### Update di tempat & Play Protect (v1.8+)

Mulai v1.8, **setiap** build CI memakai SATU keystore yang sama. Akibatnya:

* APK baru terpasang **menimpa** versi lama — tidak perlu hapus aplikasi
  lama lagi (data, favorit, playlist ikut terjaga).
* **Transisi sekali saja:** APK ≤1.7 dipasang dengan kunci debug sementara CI,
  jadi saat pertama kali naik ke v1.8 Anda masih harus menghapus APK lama
  satu kali. Setelah itu tidak lagi.
* Play Protect memblokir/memperingatkan APK debug-signed. APK release bertanda
  tangan keystore tetap, non-debuggable, jauh lebih jarang diperingatkan.
  (Aplikasi sideload tetap akan *dipindai* Play Protect — itu perilaku normal;
  yang hilang adalah flag utama "app debug".)

Jangan commit keystore lain atau mengganti `my-upload-key.jks`: fingerprint
aplikasi harus tetap sama agar update di tempat terus berfungsi.

### APK release bertanda tangan sendiri (opsional)

Tambahkan secret berikut di **Settings -> Secrets and variables -> Actions**:

| Secret | Isi |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 my-upload-key.jks` |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | alias kunci |
| `KEY_PASSWORD` | password kunci |
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 app/google-services.json` |

Selama `KEYSTORE_BASE64` ada, workflow juga membangun dan menerbitkan APK release
yang sudah ditandatangani dengan keystore tersebut. Tanpa secret pun APK release
selalu dibangun memakai keystore repositori. Perlu diingat: keystore yang
berbeda = aplikasi yang tidak bisa saling menimpa — kalau Anda pernah memakai
`KEYSTORE_BASE64`, terus pakai keystore yang sama untuk update berikutnya.

> Repositori ini privat, sehingga halaman Release hanya bisa dibuka setelah Anda
> masuk ke GitHub dengan akun yang punya akses ke repositori.

## Catatan tentang kontrol di notifikasi

Kontrol pemutar di notifikasi / lock screen berasal dari **MediaSession** milik
Media3. Agar muncul, aplikasi butuh izin notifikasi (`POST_NOTIFICATIONS`, diminta
saat pertama dibuka). Bila izin itu ditolak, suara tetap jalan tetapi kontrol
tidak akan tampil sampai izin diberikan di Pengaturan Android.

Sejak v1.8 tombol **acak (shuffle)** dan **ulang (repeat)** di notifikasi memakai
custom session command — satu-satunya mekanisme yang benar-benar dirender
`DefaultMediaNotificationProvider` (tombol berbasis player command disaring keluar
oleh Media3). Ikonnya mengikuti state: shuffle-on, repeat-all, repeat-one. Progress
bar & tombol previous/play/next berasal dari pemutar itu sendiri.
