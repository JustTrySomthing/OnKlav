> Historical implementation: new Panda games now use the solo reboot in [PANDA-SOLO-REBOOT.md](PANDA-SOLO-REBOOT.md). Building and Panda friend play are retired. Legacy solo saves remain readable.

# Included in APK0.100.51 / code160

The owner-requested quick ARM64 APK now includes this update. See RELEASE-0.100.51.md and apk51-verification.json. Source-only/no-new-APK statements below describe the earlier implementation checkpoint.

# Crash Test Panda remake

Crash Test Panda is now an included game and the first default catalog entry, with its own artwork and full-screen shared Godot route. Existing installations receive a one-time move to the first game position; subsequent owner reordering is preserved. The existing game catalog and original friend-invitation dialogs offer solo or SMS friend play. Panda uses the new pt1 built-in token. Both phones need the updated host. It remains part of the single OnKlav APK.

## Play and presentation

The remake keeps the original Panda/Red Panda characters, Forest Clearing, Bamboo Forest and Temple Courtyard art, mixed block shapes, changing one-to-three platforms, drag/reposition/rotation building, three-shot cannon attacks and three-level/four-paired-round campaign. Both sides get identical course inventories. Ready fills unused blocks, Random builds a varied defense, and removing a support settles the draft. Reserve pieces disappear as placed. The solo rival builds and aims with a bounded geometric heuristic.

Beveled colored blocks, bamboo/gold platforms, animated top-first cannon loading, a dotted initial trajectory, spinning character shots, bounded trails/particles, impact flashes/shake and floating +1 reports accompany actual physics. The portrait scoreboard animates real block points and tracks round wins, level progress, shot reserves and active-input time. Centered reports ease in/out for powers, attack results, clean sweeps, scene changes and the champion. Reduced motion removes shake and extra particles and keeps report scale stationary. All primary buttons retain at least44 CSS-pixel height; small screens shorten Shockwave to Wave with descriptive help.

Three original Panda melodies — Clearing Daylight, Bamboo Breeze and Temple Lanterns — are newly arranged with bass, arpeggios, soft chords and gentle percussion. Compressed stereo loops change by environment. Original pull/launch/hit/block recordings are reused, with short synthesized power/point/win cues. All effects and music use the shared buses/master mute and pause with the shared SDK. No background playback service, downloads or new permission was added.

## Rules and powers

Rocket increases launch speed and leaves a blue trail. Heavy increases mass with a violet aura. Shockwave adds a radial impulse at the first block impact. Each charge can be used once per attack, with equal charges for both sides. The normal shot remains available. These change real body impulses/speed/mass, not reported scores.

Every knocked-off block earns one campaign point. Compare paired attack block counts, then fewer shots, then lower power for equal full clears. Exact ties give no round win. Level ties repeat an unnumbered tiebreaker; highest campaign block total wins, then round wins, then level wins. Three shots and30 seconds of active aiming per attack; physics, reports, loading, delivery, Pause and background time stop that clock. No wagers, currency, ads or purchases were added.

## Engineering and verification

The original rigid-body algorithm is adapted to GDScript: circles and oriented boxes, contact clipping, angular impulses, friction and one-way cannon passage. Simulation uses fixed240Hz ticks, bounded velocities, twelve steps per rendered frame and a seven-second maximum shot; the projectile retires before the remaining stack settles. A loaded save preserves the exact in-flight bodies/tick so Resume does not replay a launch or score twice. Save numbers are validated and normalized after JSON. Histories and visual effects are bounded.

Friend play sends only compact validated character, layout, launch, timeout and next-turn intents, never live positions or author-supplied scores. Each peer runs the same fixed-step simulation. Incoming turns wait silently during pause and future turns stay queued behind unfinished physics. Model, queued intents and native channel cursors checkpoint together. This is casual friend play with trusted contacts, not anti-cheat tournament infrastructure. Existing OnKlav save identities and other games are unchanged; this new remake does not import old Inner Circle private saves or installed icgame packages.

706 focused checks cover every course profile/inventory, legal placements, moving supports, malformed actions, all powers, exact JSON-restored physics continuation, finite saves, stable resting bodies, paired turns, tiebreakers and campaign champion logic. Phone browser flows cover touch build/aim/launch, score and charges, real WebAudio/mute, paused physics/music, saved draft and in-flight recovery, responsive layouts and champion reports. The reload check waits for the actual IndexedDB commit; a fixed600ms delay was too early after busy rendering. Normal Android return retains the live WebView while its asynchronous save completes. Two isolated friend sessions verify equal final bodies/scores, swapped builders, silent paused arrivals, future-turn ordering, duplicate deliveries, powers/timeouts and native sequence recovery. Android framework checks cover catalog order migration, preinstalled artwork/routing, the Panda invitation token and scoped transport, alongside retained messaging-turn safeguards. The standard shared game rebuild reruns the existing game checks and records source-matched assets in games-provenance.json.

Shared onklav_game.gd, game_chrome.gd and turn_channel.gd remain byte-identical to their SDK templates. No SDK API/binary contract, native core, signer, permission or dependency changed. No APK was built this increment; delivered0.100.50-rc1/code159 predates Cats vs Dogs, Blackjack and Panda. Physical-phone performance, accessibility, long-session tuning and real carrier acceptance remain open before public release. No Play Store/award certification is claimed.
