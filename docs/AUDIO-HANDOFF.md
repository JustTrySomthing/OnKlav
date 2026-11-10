# Audio app handoff

User-reported symptom: after Radio → Podcasts or another audio app, the new item may not play until restarting OnKlav.

Concrete source issues addressed:

- A delayed foreground-service start for the old generation called stopSelf on the same service needed by the new player. Obsolete starts are now ignored while playback is active; an idle stale start uses stopSelfResult(startId).
- Repeated delivery of the current start recreated its MediaSession and prepared the decoder again. Attachment is now idempotent for the current service/generation.
- Radio released ExoPlayer synchronously inside the carrier/registry monitor. Its loader can need that monitor for live authorization. The old engine now pauses immediately and releases on the main queue after the broker call returns, matching finite-decoder release ordering.
- An old internal-audio interruption could enqueue an unguarded pause against whichever player was current later. Interruption now checks the captured generation/owner, and executes synchronously on the main thread when possible. The former owner relinquishes focus before the new focus request.
- Old cleanup failures cannot hold the audio ownership monitor or throw through a new claim. Stop invalidates stale callbacks and still releases players/focus/sessions if position saving fails.

All 14 focused Android handoff/reconnect/voice tests passed; runtime classes match compilation. See audio-handoff-verification.json. Only decoding/network bytes are simulated; tests use real signed owners, capability checks, media service and leases. Actual phone decoder, Bluetooth/wired routes and Wi-Fi/mobile handoff acceptance are separate. No APK built.
