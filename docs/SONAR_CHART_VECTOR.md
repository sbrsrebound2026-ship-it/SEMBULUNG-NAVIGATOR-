# SEMBULUNG NAVIGATOR — Sonar Chart Vector V15

## Tujuan
V15 menambahkan Sonar Chart yang tampil langsung pada Marine Map sebagai overlay bathymetry lokal yang dibangun dari pasangan data posisi + kedalaman.

## Alur data
1. NMEA position (RMC/GGA/GLL) dan depth (DPT/DBT) masuk ke NmeaDataStore.
2. MarineMapActivity hanya menerima pasangan position/depth yang masih fresh.
3. Sounding disimpan sebagai data mentah CSV di penyimpanan internal aplikasi.
4. SonarChartEngine membentuk grid bathymetry dengan interpolasi IDW.
5. Grid menghasilkan:
   - depth shading cell
   - contour vector dengan marching squares
   - sounding labels opsional
6. MarineMapView menggambar overlay langsung di atas base chart dan di bawah seamarks/navigational symbols.

## Penyimpanan sounding
Data mentah tidak dibuang setelah chart terbentuk. Field yang disimpan:
- timestamp
- latitude
- longitude
- depth meter
- speed knot
- course degree
- quality
- source

Quality:
- GOOD
- QUESTIONABLE
- REJECTED

## Filter rekaman live
Sounding baru direkam jika position dan depth masih fresh.
Untuk mengurangi duplikasi, sampel berikutnya diterima setelah kapal bergerak sekitar 4 meter atau interval sekitar 4 detik.
Skew waktu position-depth yang besar atau kecepatan sangat tinggi ditandai QUESTIONABLE.

## Vector engine
Contour dibuat menggunakan marching squares pada regular bathymetric grid.
Default saat ini:
- cell target: 20 m
- contour interval: 5 m
- maksimum grid axis: 80
- maksimum sounding yang dihitung pada satu build: 3000

## Layer map
Long-press tombol **SC** di Marine Map membuka:
- Sonar Chart ON/OFF
- Depth Shading
- Contour Vector
- Soundings
- Rebuild contour
- Clear raw soundings

## Safety
Sonar Chart V15 adalah chart lokal hasil sounding pengguna dan bukan pengganti ENC/chart navigasi resmi.
Kualitas hasil bergantung pada akurasi GNSS, instalasi transducer, latency, offset transducer, densitas survey, dan kualitas depth source.

## Quality gate
CI harus menjalankan unit test SonarChartEngine sebelum APK debug dibangun.
Test mencakup:
- grid/statistik bathymetry
- contour vector dari gradient depth
- exclusion untuk sounding REJECTED
