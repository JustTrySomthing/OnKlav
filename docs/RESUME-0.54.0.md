# 0.54.0 — requested message and settings corrections

Charles requested these changes using three screenshots. Preserve the established design.

- Native saved reactions now share the bounded Unicode rules already used by group reactions. The old ten-symbol allowlist rejected choices from the full emoji picker after sending. Persistence and removal are covered.
- Retain the large floating emoji timing. Smaller particles gain a little lateral drift and lower, gradually fading opacity.
- The playing speech arrow matches the supplied circular-arrow reference and uses an explicit zero scale origin. The previous implicit pivot displaced the drawing at phone density.
- Speech actions appear for text and meaningful captions, not blank media or placeholder labels. Text-only messages saying Audio or Video retain speech.
- Feed photo/video previews use their decoded aspect ratio, Fit, and a 640dp maximum instead of a 220dp center crop. GIF previews use their intrinsic ratio. Square gallery thumbnails remain unchanged.
- Remove the extra Contacts person button; retain Back.
- Selected theme fills get contrasting text. Walkie offline text/icon are no longer faded accent text; the panel background and verification-button disabled text stay readable. Verification follows the selected theme fill.
- Setup follows the original two-item checklist and optional permissions/SIM/number choices, with a persistent next-action button. Start messaging returns to Messages; Back returns to the prior page. Phone-test consent and Android role prompts remain explicit.
- Restore all and Empty trash remain readable even when Trash is empty. Red is dark/light aware; empty actions remain disabled.

Validation is in source-verification.json, apk-verification-debug.json and evidence/v0.54.0. The broad Rust run had 165 passes and four SDK failures. All four were reproduced on the unchanged delivered 0.53.1 source with the same packages; see unchanged-0.53.1-sdk-baseline.log. Focused messaging and group-wire checks pass. Those existing SDK failures are not claimed fixed. No repeated full-suite loop or emulator boot was run.

OnKlav is the only APK. Package/signing continuity and SDK/game identities are preserved. Full migration and actual handset/carrier acceptance remain incomplete.

The final Setup completion callback explicitly selects Messages, while Back preserves the prior page. That small callback wiring was compiled and package-verified after the focused host/visual checks; no repeat broad suite was added.
