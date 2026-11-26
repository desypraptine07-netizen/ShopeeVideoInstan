# Shopee Video Instan Android — versi 2.0

## Yang sudah disiapkan di project
- Pilih video dari galeri dan pratinjau.
- Hook POV dan hook jualan siap pilih.
- Isi nama produk dan harga untuk membuat naskah.
- Pilih suara perempuan/laki-laki berdasarkan voice Indonesia yang tersedia di mesin TTS Android.
- Coba voice-over.
- Render video menjadi MP4 menggunakan AndroidX Media3 Transformer.
- Voice-over TTS menjadi audio hasil akhir.
- Panel teks naskah dibakar (burn-in) ke bagian bawah video.
- Hasil ekspor disalin ke Galeri pada folder Movies/Shopee Video Instan.

## Cara membangun APK
1. Ekstrak ZIP di komputer.
2. Buka folder `ShopeeVideoInstan-Full` menggunakan Android Studio.
3. Tunggu Gradle Sync dan unduh dependensi.
4. Pilih **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan teknis dan batasan
- Ini adalah source project Android Studio, bukan file APK yang sudah dikompilasi atau diuji di perangkat.
- Perlu koneksi internet saat Gradle pertama kali mengunduh dependensi.
- Engine TTS dan pilihan gender bergantung pada voice yang terpasang di HP; bila tidak tersedia, sistem memakai voice Indonesia default.
- Panel subtitle menampilkan naskah ringkas sebagai satu overlay sepanjang video. Subtitle karaoke/per-kalimat tersinkronisasi, trim otomatis 10/15/30 detik, pengaturan posisi/warna teks, musik latar, dan pengaturan rasio 9:16 belum termasuk.
- Ekspor memerlukan perangkat Android yang mendukung codec video Media3.

## Build APK dari HP menggunakan GitHub Actions

Project ini sudah menyertakan workflow `.github/workflows/build-apk.yml`.
1. Buat repository baru di GitHub dari browser HP.
2. Upload seluruh isi folder project ini (termasuk folder `.github`) ke repository, pada branch `main`.
3. Buka tab **Actions**, pilih **Build Android APK**, lalu tekan **Run workflow** jika belum berjalan otomatis.
4. Setelah status build hijau, buka hasil run dan unduh artifact **ShopeeVideoInstan-debug-apk**.
5. Ekstrak ZIP artifact, lalu buka `app-debug.apk` di HP Android untuk memasang.

Catatan: APK debug ini belum ditandatangani untuk distribusi Play Store. Android mungkin meminta izin memasang aplikasi dari browser/file manager. Build tetap perlu diuji di perangkat; fitur TTS mengikuti suara yang tersedia di HP.
