# SEMBULUNG NAVIGATOR

Android project for **SEMBULUNG NAVIGATOR — BY WONG MBRAYU**.

Current build: **V14**

- Application ID: `com.sembulung.navigator`
- Tagline: `Navigasi Laut Presisi`
- Activation gate is shown before the main application.
- One Device Code receives one ECDSA-signed Activation Key.
- A key issued for one Device Code is rejected on a different device.
- WhatsApp activation requests are addressed to admin `+62 812-3440-6456` and automatically include the Device Code.
- Activation status is stored locally and cryptographically re-verified when the app starts.
- Only the activation **public key** is stored in the Android source.
- The private activation key and release keystore must remain outside this repository.

The launcher opens the SEMBULUNG splash first. The splash checks activation state and routes an unactivated installation to `ActivationActivity`; after successful activation the user enters `HomeActivity`.
