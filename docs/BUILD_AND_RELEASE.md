# Build and Release

## Debug APK
A debug APK is built automatically by GitHub Actions on every push to `main`.

## Signed release APK
Release signing is intentionally not stored in this repository. Configure these GitHub Actions secrets before manually running the release workflow:

- `SEMBULUNG_KEYSTORE_B64`
- `SEMBULUNG_STORE_PASSWORD`
- `SEMBULUNG_KEY_ALIAS`
- `SEMBULUNG_KEY_PASSWORD`

Expected release certificate SHA-256:

`A59D35A100AA9694708A867764F68384F14CBE84E9892AEA3C9441EB476D31A8`

The private ECDSA activation key must remain outside this repository.
