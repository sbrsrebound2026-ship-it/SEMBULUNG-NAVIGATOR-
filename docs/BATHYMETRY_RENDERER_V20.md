# SEMBULUNG NAVIGATOR V20 — High Density Bathymetry Renderer

V20 meningkatkan Sonar Chart Vector agar lebih menyerupai marine bathymetry chart profesional tanpa menyalin data chart komersial.

## Adaptive density
Density:
- LOW
- NORMAL
- HIGH
- ULTRA

Renderer memilih ukuran grid dan contour interval berdasarkan density + zoom.
Pada ULTRA dan zoom dekat, interval dapat turun sampai 0.5–1 m jika data sounding mendukung.

## Quality weighted interpolation
IDW tidak lagi memberi bobot sama pada semua sounding:
- GOOD = bobot penuh
- QUESTIONABLE = bobot lebih rendah
- REJECTED = tidak digunakan

Setiap bathymetry cell menyimpan confidence 0–1.

## Survey coverage mask
Cell dengan confidence rendah dapat disembunyikan agar aplikasi tidak membuat detail dasar laut palsu di area yang belum tersurvey cukup rapat.

## Depth shading
Palette dibuat sebagai nautical depth bands:
- sangat dangkal: aqua terang
- 5–20 m: cyan/blue
- 20–100 m: blue
- >100 m: deep blue

Opacity mengikuti confidence data.

## Relief / hillshade
Engine menghitung gradien bathymetry lokal dan menghasilkan relief ringan di atas depth shading.
Relief tidak mengubah nilai depth dan hanya untuk membantu membaca bentuk slope/drop-off.

## Vector contours
Marching-squares tetap menghasilkan contour vector.
V20 membedakan:
- minor contour
- major contour
- label major contour

Label menggunakan collision/declutter sehingga tidak saling menumpuk.

## Sounding declutter
Angka sounding memakai screen grid occupancy.
Zoom dekat memperbolehkan label lebih rapat; zoom jauh mengurangi label.

## Safety
Bathymetry dari sounding pengguna bersifat advisory.
Kualitas bergantung pada GNSS, transducer offset, latency, survey spacing, kecepatan kapal, dan quality flag.
Jangan gunakan sebagai pengganti chart resmi atau keputusan keselamatan pelayaran.
