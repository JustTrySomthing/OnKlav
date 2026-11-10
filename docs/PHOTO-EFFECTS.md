# Image Editor — stronger detail and four graded looks

The previous HDR implementation moved every channel toward middle gray. It has been replaced by a luminance-guided local contrast pass: a bounded, alpha-weighted analysis grid separates broad tone from texture, applies an endpoint-preserving contrast curve, and strengthens local structure with reduced gain in near-black noise. A strong-edge gate suppresses broad-radius halos while retaining texture enhancement. There is no uniform exposure lift. Pixel color ratios are preserved where possible and output remains bounded SDR; this is an HDR-style effect, not HDR-format export or recovery of missing sensor data.

Sharpen is now an adaptive-radius luminance unsharp mask, with up to2.8x detail gain, a small noise threshold and a bounded correction. Its radius scales with preview/export size (1–4pixels). Original-row rings preserve unmodified neighbor samples while output is written, with alpha-weighted averaging around transparent edges. Alpha stays unchanged. The effect adds edge definition; it does not reconstruct detail lost to severe blur.

All four new looks live in the existing Effects selector and have independent0–1strength sliders, neutral marks, reset and existing undo/draft persistence:

- Teal & Orange: cool teal shadows, warm orange highlights and a small warm-color bias.
- Dark & Moody: deeper shadows, stronger tonal contrast, subdued saturation and earthy greens/browns.
- Film Noir: high-contrast black and white with a strong vignette.
- Retro Technicolor: vivid red/green/blue separation, richer saturation and a mild contrast curve.

Effects can be blended together. Grading runs before HDR and sharpening, then the shared geometry pass. All new strengths default to zero, retaining existing neutral drafts. Existing HDR and sharpening strength values use the improved processing automatically. No extra buttons, permissions or dependencies are introduced.

Validation uses controlled tonal/edge/alpha fixtures, distinct grading checks and a labeled contact sheet (a generated test scene, not a natural-photo qualification). The UI exercises all four new entries, strength changes and resets. Phone performance and visual acceptance on the user's own photographs remain separate. Included in verified OnKlav0.100.7/code116; handset acceptance remains separate.
