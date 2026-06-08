# Changelog - NaturalVelocity 🌐

Dokumentasi riwayat pembaruan, perbaikan bug, dan rilis fitur untuk plugin **NaturalVelocity** (Proxy Management Plugin).

---

## [v1.1.0] - Maintenance & Sync Update
### ✨ Fitur Baru
- **Maintenance Rewrite (nvelo_mt)**: Rancang ulang sistem maintenance agar tersinkronisasi menggunakan tabel database MySQL `nvelo_mt`. Mendukung hitung mundur dinamis dan pengalihan otomatis ke server tujuan tertentu.
- **Temporary Closed Mode**: Menambahkan opsi `temp-closed.enabled` untuk menutup akses server sementara dengan visualisasi MOTD gambar kustom, pesan kick terformat, serta melompati proses koneksi database/sync demi kestabilan.
- **Rotating Head MOTD**: Fitur menampilkan animasi kepala pemain yang berganti-ganti pada tampilan Server List client.
- **Console Permission Fix**: Mengizinkan Console Server untuk menjalankan command `/maintenance` dan `/nvelocity reload` tanpa terhambat pengecekan permission pemain.
- **Permission Prefix & Color Stripping**: Mengubah prefix permission menjadi `naturalvelocity` dan mengimplementasikan helper pembersih warna (color stripping helper) yang aman untuk eksekusi via console.

### 🐛 Perbaikan Bug
- **BindException Resolution**: Memperbaiki masalah `Address already in use` (BindException) yang kerap terjadi di `SyncServer` saat admin melakukan reload plugin.
- **Protocol Matching & Ping Fix**: Mengembalikan penanganan default protocol matching untuk menjamin indikator ping hijau pada client tidak berubah menjadi silang merah saat kustomisasi teks versi aktif.
- **MOTD Protocol Range**: Membatasi jangkauan Head MOTD hanya pada protocol 773-775 guna mencegah crash pada client Minecraft versi lawas.
