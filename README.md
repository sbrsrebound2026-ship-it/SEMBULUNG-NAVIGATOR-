# SEMBULUNG NAVIGATOR

**SEMBULUNG NAVIGATOR — BY WONG MBRAYU** is a standalone clean-room Android marine navigation application.

Current build: **V25 Standalone — Unified Marine Map**

- Application ID: `com.sembulung.navigator`
- Version: `2.4.0-standalone-v25`
- Tagline: **Navigasi Laut Presisi**
- This project does not depend on, wrap, patch, or re-sign the Boating/Navionics APK.
- Device-bound ECDSA activation is built into the app.
- One Device Code receives one Activation Key; a key issued for another device is rejected.
- Only the activation public key is stored in the repository.

## Unified Map V25

The primary navigation screen now combines the offline PMTiles basemap, OpenSeaMap seamarks, optional GEBCO bathymetry, own-vessel GPS/NMEA position, SOG/COG, active waypoint route guidance, AIS targets with CPA/TCPA risk classes, and local sonar sounding/contour overlays in one MapLibre screen.

The app can switch position source between AUTO, device GPS, and NMEA. AIS and sonar layers can be toggled without leaving the map. Long-press SONAR opens the survey/data center.

## Implemented modules

- Online-first marine map with live GPS and marine overlays
- Waypoints, active route guidance, arrival/route controls
- Coordinate search
- Offline raster MBTiles reader with sparse-zoom handling and TMS/XYZ detection
- Offline detailed vector PMTiles renderer using MapLibre OpenGL
- Optional OpenSeaMap seamark overlay for buoy, beacon, lights and other aids to navigation
- Hybrid chart mode: offline vector basemap + online marine overlay
- Direct handoff to the sonar/depth map for local sounding and bathymetry
- Local PMTiles import through Android Storage Access Framework
- NMEA data input/store for position, heading and depth
- AIS target parsing plus CPA/TCPA collision calculations
- Sonar/depth sounding storage, filtering and survey sessions
- Bathymetry depth shading, vector contours and soundings
- Sonar CSV import/export, replay and backup
- Settings and diagnostics

## Offline map data

The primary detailed test package for V23 is the **Banyuwangi–Selat Bali–Bali PMTiles** extract built from Geofabrik Shortbread / OpenStreetMap data.

Target bounds: `113.0,-9.2,116.0,-7.0`

Vector zoom range: `0–14`

Attribution: **© OpenStreetMap contributors / Geofabrik**

This vector map and OpenSeaMap overlay are not official hydrographic charts and must not be used as the sole source for navigation safety. OpenSeaMap itself states that official nautical charts remain necessary for good seamanship.

## Data policy

SEMBULUNG NAVIGATOR must use map/chart/bathymetry data that the operator is licensed or otherwise permitted to use. Proprietary Navionics charts, services and entitlement mechanisms are not included.

## Startup

`SembulungSplashActivity` checks the local activation state. Unactivated devices are sent to `ActivationActivity`; activated devices enter `HomeActivity`.

The private activation key and permanent Android release keystore must remain outside this repository.
