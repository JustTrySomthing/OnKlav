# SHROOM Phase 16 merge

OnKlav 0.100.74 combines the cumulative Phase 16 gameplay with the 0.2.3 loader, enlarged touch controls, reload, hit feedback and delayed extraction. SHROOM's package version is 0.2.4. Its music uses the selected Below the Mycelium track. The old embedded music files were removed. The six other built-in games each use one replacement theme generated with the restored Music Maker checkpoint.

Phase 16's untested GDScript contained reserved-name and Variant type inference errors. The integration fixes those and accepts integral JSON projectile damage values in the existing allowlist. The extraction phase, ammunition and bounded floor stains round-trip through saved runs. Extraction uses a pulsing platform and HUD transition with a reduced-motion path. The render limit remains a half-resolution SubViewport with 3D MSAA disabled.

SHROOM was imported, exercised headlessly and exported with the pinned engine. Physical device gameplay and performance are separate checks.
