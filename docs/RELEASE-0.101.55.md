# OnKlav 0.101.55-rc1

- The game screen now offers Try again and Copy report when the page fails to
  load or Android WebView fails to start.
- All included games can recover from a lost graphics context by reopening the
  last saved checkpoint. Late errors from an old renderer are ignored.
- Air Hockey now reports when its session is ready, so Android Back and game
  switching use save-and-return during play. Play Again starts a new match at
  the selected difficulty. Completing a match preserves the ability to save
  the next match’s checkpoint.

Install from Settings → Check for updates, or use the GitHub APK. Existing
checkpoints remain available. Hardware-specific WebView and GPU behavior still
needs testing on physical phones.
