# SEMBULUNG NAVIGATOR V14 — Live Sonar Chart Vector

## Tujuan
Sonar Chart bukan halaman terpisah. Data GPS + depth yang valid disimpan sebagai raw sounding, lalu dibangun menjadi layer bathymetry yang tampil langsung di Marine Map.

## Pipeline
GPS position + DPT/DBT depth -> DepthSample -> quality filter -> SQLite raw sounding -> metric grid -> depth shading cells -> contour vector -> MarineMapView.

## Prinsip data
- Raw sounding tidak dihapus setelah contour dibuat.
- Titik memiliki kualitas GOOD, QUESTIONABLE, atau REJECTED.
- Data kedalaman tidak dibuat-buat. Jika tidak ada depth dari sonar/NMEA, tidak ada sounding baru.
- Waterfall/fish echo hanya boleh berasal dari perangkat yang memang mengirim raw echo.

## Layer
- LIVE SONAR CHART
- Depth shading
- Contour vector
- Sounding labels
- Survey coverage

## Default V14
- grid cell: 15 m
- contour interval: 2 m
- sample cache rendering: 3000 sounding terakhir
- data mentah tetap tersimpan di SQLite

## Catatan navigasi
Sonar Chart buatan pengguna adalah advisory/survey layer dan bukan pengganti ENC/chart navigasi resmi.
