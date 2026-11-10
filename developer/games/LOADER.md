# Animated default Games loader

The default loader uses logo purple #603D77 across the entire screen, with the established white O/K speech-bubble mark as a transparent vector, not a boxed icon. The mark approaches from a distant horizon, overshoots its final size, settles with a damped bounce and breathes subtly during longer loads. Games arrives underneath in staggered letters. A high-contrast8px track, moving highlight and actual loaded-file percentage replace the faint bar. Unknown totals use an indeterminate highlight without invented percentages. Completed downloads say Preparing game while initialization continues; errors retain a readable alert instead of reporting completion.

The short soft sweep/resolving chime uses four tapered sine voices on a temporary AudioContext; no external request or per-frame synthesis. The context closes after the cue, on a failed load, while hidden, or at handoff. It respects the early mute/volume cache written by canonical GameChrome preferences and the device's media volume. Browser autoplay policy may suppress sound; there is no permission/login prompt or delayed replay after loading. A first load without a preference cache uses the default volume. Normal game audio continues to use its existing Music/Effects buses.

CSS uses transforms/opacity for the entrance and small idle motion. System prefers-reduced-motion and cached Reduce motion suppress travel, overshoot, letter movement and looping highlights. The reduced-motion entrance has no minimum visual delay. Layout supports narrow portrait and short landscape, with a bottom safe-area allowance. The progress element remains available to assistive technology; the decorative vector/letters do not duplicate the OnKlav Games label.

Engine downloads/initialization run in parallel with the entrance. The shell wraps the public Engine.start method, so the game main loop begins only after the1.65second default entrance is settled; it does not consume play time behind a decorative hold. Slow loads remain visible until real engine readiness. The handoff fades over220ms, giving the first canvas frames time to render. Shared engine JS/WASM/audio-worklet bytes are unchanged. No permission, navigation, save, friend protocol, game rules or Android code changes are introduced.

## SDK ownership

loading.py embeds templates/shared/loading.css, loading.html and loading.js in the one shared export template. loading-logo.svg is the transparent version of the established logo; onklav-splash.svg/png provide the flat purple Godot fallback and icon. Reapplying the theme is idempotent and rejects an unexpected shell structure. scripts/build_web_engine.py retains the theme on future engine rebuilds; recorded template fingerprints must be updated after intentional changes.

All six included games use the new default shell. New SDK projects inherit it. Existing signed third-party packages retain their authored HTML until their developers upgrade/re-export with the same game ID/key and a higher version_code. Never rewrite signed game content behind its author's back.

A developer's explicit custom PNG boot_splash/image is preserved by gamekit export/build: it adds a custom-brand meta tag to retain the supplied image/backdrop and omit OnKlav's flying mark, Games lettering, cue and minimum entrance delay. The common progress/error handling remains. Godot boot splash images must be PNG; use a custom export shell if you need a wholly different loader. Canonical game controls and lifecycle remain required independently of studio artwork.

## Verification

Automated real-browser checks cover320x640,432x800 and740x360 layouts, actual sound output, real progress values, approach/overshoot/settle, text arrival, fast/slow startup gating, mute/zero volume, both reduced-motion sources, errors and custom-brand opt-out. Real shared exports start all six games and write the early preference cache; a muted reload suppresses the early cue. Fourteen SDK unit methods and actual default/studio-PNG SDK exports pass. All shared session/rule/game checks pass. Screenshots were inspected locally and are not saved deliverables. Physical Samsung performance, autoplay behavior and listening acceptance remain separate.

No APK was requested or built. Latest delivered0.100.55/code164 predates this loader and the per-game save system; pending source is0.100.56/code165.
