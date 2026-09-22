# SEMBULUNG NAVIGATOR V18 — Active Route Guidance & Safety Engine

## Active Route Guidance
Marine Map membaca route/waypoint dari WaypointStore dan menghitung guidance realtime:
- DTW — distance to waypoint
- BTW — bearing to waypoint
- XTE — cross-track error untuk leg aktif
- ETA berdasarkan SOG
- arrival detection
- auto-advance waypoint
- skip waypoint
- end route

Waypoint aktif disorot warna oranye dan leg aktif digambar cyan tebal.

## Route Settings
- Auto lanjut waypoint
- Radius kedatangan: 0.02 / 0.05 / 0.10 / 0.20 NM
- Batas keluar rute: 0.05 / 0.10 / 0.15 / 0.25 / 0.50 NM

## Safety Strip
Marine Map menampilkan status keselamatan di atas bottom navigation.

### OFF ROUTE
Jika absolute XTE melewati threshold, strip menampilkan OFF ROUTE beserta nilai deviasi.

### SHALLOW AHEAD
SonarHazardEngine memeriksa cell bathymetry Sonar Chart pada koridor di depan kapal.
Parameter:
- safety depth dari pengaturan pengguna
- look-ahead 2 / 5 / 10 / 15 menit
- minimum look-ahead 250 meter
- corridor default 60 meter + allowance ukuran cell

Risk:
- DANGER: depth < 70% safety depth
- WARNING: depth < safety depth
- CLEAR: tidak ada cell dangkal pada koridor
- UNKNOWN: data chart/course tidak cukup

## Catatan Penting
Shallow Ahead hanya berdasarkan Sonar Chart lokal yang dibangun dari sounding pengguna. Ini bukan pengganti ENC resmi, echo sounder realtime, radar, visual watch, atau keputusan nakhoda.

## Quality Gate
V18 menambahkan unit test untuk:
- distance / bearing / ETA
- signed cross-track error
- arrival auto-advance
- shallow cell ahead
- shallow cell behind ignored
