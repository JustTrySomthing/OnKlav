Current rules/replay update: see GAME-REPLAY-AND-TOURNAMENT-REPAIRS.md. It supersedes the historical scoring, match-length and finished-game entry behavior below.

# Included in APK0.100.51 / code160

The owner-requested quick ARM64 APK now includes this update. See RELEASE-0.100.51.md and apk51-verification.json. Source-only/no-new-APK statements below describe the earlier implementation checkpoint.

# Blackjack polish

Blackjack now uses one emerald felt table for classic solo and the compatible ten-hand friend showdown. Ivory cards have suit pips, rank corners, royal artwork and patterned backs. Curved deals, smooth hand reflow, a squeezed hole-card flip and a centered result report make every action visible. Original deal, landing, stand/reveal, shuffle and result cues use the shared Effects bus. The scoreboard shows real totals, soft/hard/bust status and hidden-hand privacy; friend mode adds wins, round count and hand history. There are no wagers or purchases.

Classic solo correctly values aces, gives two-card naturals priority over later21s, pushes equal naturals and stands the dealer on all17s. Friend rules and the existing H/S/N turn protocol remain unchanged: equal players over ten hands, alternate starter, host deals the next hand, compare totals without a special natural bonus. Deterministic friend deals provide casual play, not cryptographic card secrecy.

The canonical SDK controls own the top bar, Pause, Help, Sound, Settings, confirmed Quit, return/save and New/Resume. No additional game mode or replacement dialogs were introduced. Logical results commit before animation; paused incoming turns remain silent and do not advance visually until resume. Saved games restore without replaying sounds. Original solo snapshots and friend turn journals are supported. Reduced motion removes card travel and sparkles.

## Verification

179 focused Blackjack model checks cover deck integrity, JSON numeric normalization, aces, naturals, dealer steps, bust/terminal actions, legacy solo saves and ten-round friend replay. The shared rebuild also runs the existing Cats vs Dogs, Naval, Hockey and shared rules checks. Source-matched export hashes are recorded in games-provenance.json.

Phone browser checks at320×560 and432×740 cover deals, hit/stand, hole-card reveal, result totals, stable table layout, genuine WebAudio output and mute silence, paused animation/audio, restore without repeated cues, old solo saves and shared dialogs. Two isolated friend browsers cover ten hands, hidden cards, alternating starters, host-only dealing, paused arrivals, duplicate delivery, matching wins/history, old journals and final saved return. Text evidence and reproducible harnesses are in evidence/blackjack-polish; QA screenshots are transient.

No Android host, native core, SDK contract, permissions or signing identity changed. No APK was built for this source increment. Latest delivered APK remains0.100.50-rc1/code159 and predates Cats vs Dogs and Blackjack polish. Physical-phone touch/audio/lifecycle, accessibility and carrier acceptance remain pending; this is not a claim of Play Store certification.
