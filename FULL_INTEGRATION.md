# Full Production Integration Branch

Branch ini digunakan untuk mengintegrasikan engine produksi lengkap ke baseline GitHub yang sudah berhasil dibuild.

Aturan:
- main tidak disentuh sampai CI branch ini lulus.
- source V10 ringan disalin ke migration/v10-lightweight untuk audit/porting.
- S-63 protected ENC tidak dibypass.
- build harus lolos unit test + assembleDebug sebelum source penuh dimaterialisasi.
- artifact debug dibuat dari source yang sama dengan commit integrasi.
