# SEMBULUNG NAVIGATOR V17 — Production Marine Data Service

V17 memindahkan penerimaan data marine dari Activity ke satu foreground service bersama.

## Arsitektur
- MarineDataService menerima UDP pada satu port (default 10110).
- NMEA GPS/heading/depth/speed diparse oleh NmeaSentenceParser dan ditulis ke NmeaDataStore.
- !AIVDM/!AIVDO diparse oleh AisParser dan target fresh ditulis ke AisTargetStore.
- NmeaActivity dan AisActivity menjadi panel kontrol/monitor, bukan pemilik socket.
- Listener tetap hidup saat pengguna berpindah ke Marine Map.

## Unified Map
- Route/waypoint overlay dipanggil kembali di onDraw.
- AIS overlay dipanggil kembali di onDraw.
- Target AIS diberi warna berdasarkan risk CPA/TCPA.
- WARNING/DANGER menampilkan label CPA/TCPA.
- Own-vessel speed/course dikirim dari GPS/NMEA ke map untuk relative-motion assessment.

## Alerts
MarineDataService membuat notification channel khusus AIS collision.
- DANGER: cooldown 60 detik per MMSI.
- WARNING: cooldown 120 detik per MMSI.
- Alert hanya dibuat jika posisi, SOG, dan COG own ship tersedia.

## Compatibility
- minSdk 23
- notification builder kompatibel API 23+
- Android 13+ meminta POST_NOTIFICATIONS dari panel NMEA/AIS.
- Android modern menggunakan foregroundServiceType=connectedDevice untuk koneksi perangkat marine melalui jaringan lokal.

## Safety
CPA/TCPA dan shallow-water data adalah bantuan situational awareness, bukan pengganti radar, visual watch, chart resmi, atau keputusan nakhoda.
