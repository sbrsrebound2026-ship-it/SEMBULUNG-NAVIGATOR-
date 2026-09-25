# SEMBULUNG NAVIGATOR — Claude Instructions

## Project identity
- App name: SEMBULUNG NAVIGATOR
- Credit: BY WONG MBRAYU
- Tagline: Navigasi Laut Presisi
- Android package: com.sembulung.navigator
- Main repository: sbrsrebound2026-ship-it/SEMBULUNG-NAVIGATOR-
- Primary branch: main

## Mission
Maintain and improve SEMBULUNG NAVIGATOR as a professional marine navigation/chartplotter Android application. Prioritize a clean, map-first experience comparable in UX quality to modern marine navigation apps, without copying proprietary branding or assets.

## Critical working rules
1. Inspect the existing implementation before changing it. Do not guess method names, fields, IDs, layouts, or architecture.
2. Preserve existing working navigation, map, GPS, NMEA, AIS, route/waypoint, sonar, bathymetry, MBTiles import/export, offline-chart, activation, and settings functionality unless the task explicitly requests a behavior change.
3. Make focused changes. Do not rewrite whole activities merely to change visual styling.
4. Do not remove an existing feature simply because it is not currently visible in the main UI.
5. Do not introduce duplicate methods, imports, listeners, resources, or IDs.
6. After source changes, run the available Gradle tests/build. Do not claim an APK is verified unless the build actually succeeds.
7. Keep production code compilable on the project's existing Android/Gradle versions. Do not upgrade dependencies or SDK versions without a concrete reason.
8. Never expose secrets, API keys, signing credentials, activation keys, tokens, or private user data in source, logs, issues, or documentation.

## UI/UX direction
- Map is the dominant surface.
- Keep persistent controls minimal and easy to reach.
- Use compact marine-style status indicators for GPS, depth, heading, speed, and AIS.
- Put secondary features in menus/layers rather than large permanent buttons.
- Route guidance HUD should appear only when a route/navigation session is active.
- Prefer clear hierarchy, restrained typography, compact cards, and professional dark marine styling.
- Avoid excessive glow, bevel, ornament, oversized buttons, or dense technical panels.
- Bottom navigation should contain only genuine top-level destinations.
- Preserve touch targets and accessibility.

## Marine features
Treat these as existing product capabilities that must remain coherent:
- MapLibre/custom marine map
- OSM/chart layers
- GEBCO bathymetry
- Local sonar/sounding and SonarChart
- GPS/NMEA data
- AIS targets and CPA/TCPA
- Routes and waypoints
- Track/navigation guidance
- Offline MBTiles charts
- Bathymetry radius download and cached areas
- Bathymetry MBTiles export
- Activation flow
- Settings

### Bathymetry rules
- Keep geographic-radius selection behavior accurate.
- Preserve longitude-wrap handling.
- Preview and actual download selection must use the same geographic logic.
- Do not reintroduce viewport-only MBTiles export unless explicitly requested.
- Keep cached-area accounting consistent with downloaded tiles.

## Important classes
Before modifying a class, inspect its current source:
- MarineMapActivity
- MarineMapView
- MarineTileLoader
- OfflineMapActivity
- NavigationActivity
- AisActivity
- NmeaActivity
- SettingsActivity
- SembulungSplashActivity
- activation/ActivationActivity
- BathymetryMbtilesExporter

## Build discipline
Use the repository's existing GitHub Actions/Gradle configuration.
At minimum, validate:
- unit tests
- debug APK compilation
- Android resource compilation
- Java/Kotlin compilation

If CI is unavailable, state that clearly instead of claiming successful compilation.

## Git workflow
- Work on the current branch unless the user asks for a separate branch.
- Use descriptive commits.
- Keep commits focused.
- Do not force-push or rewrite history unless explicitly requested.
- Before editing an existing file through the GitHub API, fetch the current version and use its current blob SHA.
- When a task involves multiple related files, inspect all affected files before making the first write.

## User communication
The user prefers actual implementation over generic instructions. When asked to "kerjakan", perform the repository change when tools permit it. Report:
1. what was changed,
2. commit SHA,
3. build/test status,
4. APK/artifact link only if an actual artifact was produced.

Do not say an APK is ready merely because source code was committed.
