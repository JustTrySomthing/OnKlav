# Panda volley, timing and audio repairs — 2026-11-04

The player now completes all three shots before the rival. Earned bonus shots stay in the same volley; destroyed targets finish that side early. Each shot retains its score report. Queued old rival turns return unused player shots; already airborne saved rival shots continue safely. The save schema remains compatible and adds a volley rule marker.

The active cannon now matches its panda helmet: gold for player, blue for rival, including barrel ribs, rim, hubs and projectile trails. Turn labels include the shot number.

Repeated impact hit-stop and dropped frame time caused slow motion. Flight uses monotonic elapsed time and bounded 240 Hz catch-up (48 steps / 200 ms), excluding suspension. Cosmetic timers share elapsed-time pacing. Extreme stalls remain bounded to avoid a catch-up spiral. Drawing styles/text widths are cached and particles avoid per-frame style allocation.

Collision sound voices no longer replace playing clips. Per-material cooldowns and lower voice level limit overload. Four harsh cues were regenerated with smooth envelopes and filtered noise; short effects use lossless PCM. Shared music/effects controls, bus routing and pause behavior are preserved.

Validation: standard game build/export and all regression suites passed, including 48,422 duel checks, 2,102 legacy checks, 15 audit checks and 57 new volley/timing/color checks. Exported-game touch, pause, save/resume, volley and injected profile-write recovery checks run at 360×780, 320×640 and 640×360. Audio signal checks confirm zero-valued edges and substantially reduced sample discontinuities. A deterministic course-12 baseline/fixed comparison preserves 1,200 simulation ticks and score 1,175; software rendering is not a handset frame-rate benchmark. See verification JSON and evidence.

No physical Android test or new APK was performed. APK 0.100.59 predates these changes. Existing Video Edit and Panda reboot/audit changes remain in the checkpoint.
