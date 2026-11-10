# Landscape Camera full-screen correction — 2 November 2026

The embedded Camera previously hid Android system bars but the home shell still reserved space for OnKlav bottom navigation and safe-drawing insets. This prevented landscape capture from expanding to the bottom.

The SDK screen now reports the same immersive state it uses for system bars to the home shell. During landscape live capture the shell hides HomeNavigation, removes its outer insets/IME padding, and removes bar backplates. The black Camera workspace reaches every edge. Camera-local safe padding still protects floating controls from cutouts. Portrait, Captures/gallery and closing Camera restore normal navigation/insets. The callback is stable and resets on disposal; state is not persisted across a recreated screen.

A focused UI regression covers landscape full-width/bottom bounds, hidden navigation/backplates and restoration in portrait. Existing portrait Camera and Phone root checks are retained. Production and all test sources compile; all 22 selected/shared host checks pass, including the landscape bounds/navigation restoration regression. Runtime bundle bytes match compiled Kotlin. Physical camera/cutout qualification remains separate. No APK built; 0.100.38-rc1/code147 predates this correction.
