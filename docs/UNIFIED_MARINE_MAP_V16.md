# SEMBULUNG NAVIGATOR V16 — Unified Marine Map

V16 menyatukan fungsi inti ke Marine Map agar aplikasi tidak terasa sebagai kumpulan layar terpisah.

## Marine Map
Kontrol langsung:
- Layer
- Cari koordinat
- Download / peta offline
- Zoom + / -
- Tengah ke kapal
- Sonar Chart ON/OFF
- Menu menuju NMEA, AIS, Settings

Overlay:
- vessel / own ship
- Sonar Chart depth shading
- vector contours
- sounding labels
- seamarks
- waypoint & route
- AIS target fresh (<120 detik)

## Cari Koordinat
Mendukung:
- Decimal Degrees (DD), contoh: -8.123456, 114.123456
- DMS, contoh: 8°07'24.4"S 114°07'24.4"E
- clipboard paste
- buka hasil langsung di Marine Map
- simpan hasil sebagai waypoint

## Settings
- layar tetap hidup
- auto-center kapal
- unit jarak
- unit kedalaman
- Sonar Chart ON/OFF
- shallow-water warning dan threshold
- AIS ON/OFF
- shortcut ke Offline Map, NMEA, AIS

## AIS
AisActivity menyimpan target fresh ke shared target store.
Jika pengguna membuka Marine Map dari layar AIS, UDP listener tetap hidup saat AisActivity berada di back stack, sehingga overlay peta dapat menerima pembaruan target.

## Safety
Sonar Chart dan AIS adalah bantuan situational awareness. Hasil aplikasi bukan pengganti chart resmi, radar, visual watch, atau keputusan nakhoda.

## Quality Gate
PR V16 wajib lulus:
1. unit tests
2. debug APK compilation
3. artifact upload
sebelum merge ke main.
