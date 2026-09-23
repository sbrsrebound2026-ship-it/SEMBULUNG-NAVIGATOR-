# SEMBULUNG NAVIGATOR

**SEMBULUNG NAVIGATOR — BY WONG MBRAYU** is a standalone clean-room Android marine navigation application.

Current build: **V21 Standalone Alpha**

- Application ID: `com.sembulung.navigator`
- Version: `2.0.0-standalone-v21`
- Tagline: **Navigasi Laut Presisi**
- This project does not depend on, wrap, patch, or re-sign the Boating/Navionics APK.
- Device-bound ECDSA activation is built into the app.
- One Device Code receives one Activation Key; a key issued for another device is rejected.
- Only the activation public key is stored in the repository.

## Implemented modules

- Online-first marine map with live GPS and marine overlays
- Waypoints, active route guidance, arrival/route controls
- Coordinate search
- Offline raster MBTiles import
- NMEA data input/store for position, heading and depth
- AIS target parsing plus CPA/TCPA collision calculations
- Sonar/depth sounding storage, filtering and survey sessions
- Bathymetry depth shading, vector contours and soundings
- Sonar CSV import/export, replay and backup
- Settings and diagnostics

## Data policy

SEMBULUNG NAVIGATOR must use map/chart/bathymetry data that the operator is licensed or otherwise permitted to use. Proprietary Navionics charts, services and entitlement mechanisms are not included.

## Startup

`SembulungSplashActivity` checks the local activation state. Unactivated devices are sent to `ActivationActivity`; activated devices enter `HomeActivity`.

The private activation key and permanent Android release keystore must remain outside this repository.
