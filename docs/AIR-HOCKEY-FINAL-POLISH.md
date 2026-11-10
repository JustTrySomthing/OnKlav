# Air Hockey polish and pause-motion repair

The reported Sound-button failure was reproduced: the Sound dialog opened and closed, the rally remained PLAYING and ticks advanced, but the puck stayed at the same coordinates with zero velocity. Freezing the Godot rigid body had erased its velocity. A paused checkpoint also wrote that zero velocity, preserving the stall after reload. This was a motion/lifecycle fault rather than an observed renderer crash in the browser reproduction.

Rink now captures position, velocity and pending contact before freezing. Resume restores them at the physics synchronization boundary. Paused saves retain the captured velocity, and older zero-velocity PLAYING saves recover a legal moving rally without changing scores. Audio, Settings, Pause, return/reload and collision timing share this handling. The footer now says Paused when paused, rather than leaving a misleading Playing label.

Player input now follows fast drags with a higher bounded paddle speed, rejects emulated mouse events immediately after touch and retains first-finger ownership. A render-frame interpolation pass smooths the puck and both paddles between120Hz physics poses; the trail is shortened from20 to7 samples. Godot collision detection/CCD, goal mouths, ten-goal scoring, AI bounds and existing save format are preserved.

Goal feedback no longer has a rectangle. Larger neon GOAL/RIVAL SCORES lettering pops in with a damped scale, radial streaks and an expanding energy ring. Final results float over the rink with a concise Victory/Rival Wins/Match Drawn headline and the actual score. Reduced motion removes particles and scale movement. The new original Neon Rally loop is an upbeat124BPM arrangement with lead, arpeggios, bass, pads and percussion, using the existing shared Music bus and controls. Existing impact/serve/score cues are retained.

## Shared controls and SDK prevention

The other four bundled games already use OnKlavGame's SceneTree pause, which preserves rigid-body velocity. Their live Audio/Settings flows were checked. Shared GameChrome now preserves whether gameplay was running before a temporary sheet, so Sound/Settings opened while paused do not unexpectedly resume play. Host-state probes and unknown messages no longer cancel a sheet's Done action. Layout callbacks ignore dialogs queued for deletion. The canonical bundled helper and SDK template match exactly, and both old/current helper hashes are recognized for safe upgrades.

SDK lifecycle guidance now distinguishes tree pause from manually freezing bodies. Its portable template tests exercise a real moving rigid body through Sound/Done, verify that host probes cannot strand the dialog and ensure a pre-existing pause stays paused. No new SDK API, engine binary, host permission, library or signer was introduced.

## Verification

95 Hockey checks pass, including actual collisions and real velocity/position progression after Audio/Settings, paused-disk velocity, old stalled-save recovery and prior pause intent. The SDK template passes14 checks, including native rigid-body tree pause;11 Python SDK tooling checks pass. Phone browser flows at320×640 and432×800 pass for actual toolbar taps, responsive touch movement, collisions, unboxed goal/rival/final feedback, audible music, saved return/reload and resumed movement. The other four games pass live Audio/Settings pause/snapshot/resume checks with no script/browser errors. Evidence is in evidence/hockey-final-polish. QA screenshots remain transient. After browser checks, only goal font size and the final single-line headline were refined; the final shared build parses/exports these changes.

No new APK was built. Delivered0.100.51/code160 predates this and the preceding Naval/loader refinement. Physical Galaxy/WebView performance, accessibility, sound balance and carrier acceptance remain separate from these native/browser checks. No Play Store or award certification is claimed.

## Pocket and rail lighting

Each pocket sends two trails along the rounded rails to center ice and back. Trails run between serves and pause with the game. A goal lights the mouth the puck entered and sends one brief flash around the rails. Reduced Motion uses a steady pocket highlight. The effect is drawn separately from puck and goal physics.

Verified with 96 hockey checks and a phone-sized browser check of outward and returning trails, both goal mouths, and Reduced Motion.
