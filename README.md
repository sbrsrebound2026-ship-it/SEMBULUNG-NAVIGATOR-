# SEMBULUNG NAVIGATOR

**SEMBULUNG NAVIGATOR — BY WONG MBRAYU** is a standalone clean-room Android marine navigation application.

Current build: **V27 FINAL / 3.0.0**

- Application ID: `com.sembulung.navigator`
- Version code: `27`
- Version name: `3.0.0-final`
- Tagline: **Navigasi Laut Presisi**
- Standalone: does not depend on, wrap, patch, or re-sign the Boating/Navionics APK.
- Device-bound ECDSA activation is built into the app.
- One Device Code receives one Activation Key; a key issued for another device is rejected.
- Only the activation public key is stored in the repository.

## Final unified marine map

The primary MapLibre screen combines:

- offline PMTiles vector basemap
- OpenSeaMap seamarks
- optional GEBCO depth relief
- own-vessel GPS with NMEA fallback
- SOG, COG and heading
- tap-to-create route planning
- waypoint route line, DTG, bearing, XTE and ETA
- route arrival and automatic waypoint advance
- persistent track recording with GPX/KML export
- 0.5 / 1 / 2 NM range rings
- AIS targets with CPA/TCPA risk colour and audible collision warning
- local sonar soundings and shallow-water / shallow-ahead warning
- follow-vessel chartplotter mode

## Other implemented modules

- Coordinate search
- Offline raster MBTiles reader with sparse zoom and TMS/XYZ handling
- NMEA input/store for position, heading, speed and depth
- AIS parsing and target store
- Sonar survey sessions, depth sounding storage, CSV import/export and replay
- Local bathymetry grid, relief, contour generation and forward hazard assessment
- Settings for shallow depth, route arrival radius, XTE/off-route threshold and look-ahead
- Device-bound offline activation
- Diagnostics and data-management screens

## Offline map data

Primary regional test package:
**Banyuwangi – Selat Bali – Bali PMTiles**

Target bounds: `113.0,-9.2,116.0,-7.0`

Vector zoom range: approximately `0–14`

Attribution: **© OpenStreetMap contributors / Geofabrik**

OpenSeaMap, GEBCO and OSM-derived layers are open-data navigation aids, not official hydrographic charts. They must not be treated as the sole source for navigation safety.

## Data policy

SEMBULUNG NAVIGATOR uses only map/chart/bathymetry data that the operator is licensed or permitted to use. Proprietary Navionics charts, services, DRM, entitlement systems and private APIs are not included.

## Startup

`SembulungSplashActivity` checks local activation state. Unactivated devices are sent to `ActivationActivity`; activated devices enter `HomeActivity`.

The private activation key and permanent Android release keystore must remain outside this repository.
