# SEMBULUNG NAVIGATOR V19 — Sonar Survey & Production Hardening

## Sonar Survey Center
Layar khusus untuk mengelola data sounding:
- statistik GOOD / QUESTIONABLE / REJECTED
- jumlah sounding yang dipakai chart
- min / max / mean depth
- survey coverage
- interval contour aktif
- quality filter aktif
- start / stop survey session
- export sounding CSV
- import sounding CSV (append atau replace)
- replay session terbaru
- backup data ZIP
- diagnostic screen

## Session Logging
Saat session aktif, setiap sounding yang direkam Marine Map ditulis ke:
- working sonar store
- file session terpisah

Session disimpan sebagai CSV agar dapat dianalisis ulang tanpa kehilangan data mentah.

## Contour & Quality
Pengaturan baru:
- interval contour: 1 / 2 / 5 / 10 meter
- quality filter:
  - GOOD only
  - GOOD + QUESTIONABLE

REJECTED tidak pernah dipakai untuk bathymetry.

## CSV
Format canonical:
timestamp,lat,lon,depth_m,speed_kn,course_deg,quality,source

Import mengabaikan baris malformed. Replace menggunakan file sementara agar store utama tidak ditulis setengah jalan.

## Replay
Replay dasar memutar sample session terbaru berurutan dan menampilkan:
- posisi
- depth
- quality
- progress

## Backup ZIP
Backup berisi:
- sonar/soundings.csv
- data/waypoints.json
- data/settings.txt
- diagnostic/marine_service.txt

## Diagnostic
Diagnostic menampilkan:
- version
- status MarineDataService
- packet counters
- freshness NMEA/depth
- source
- jumlah sounding/AIS/waypoint
- ukuran file sounding
- contour/quality/safety settings

## Safety
Sonar Survey dan Sonar Chart adalah bantuan survey/navigation advisory. Hasilnya bergantung pada kualitas GNSS, transducer, latency, offset, kepadatan sounding, dan kalibrasi.
