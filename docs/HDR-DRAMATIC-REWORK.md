# HDR local-light rebuild

The six looks now use log-linear luminance decomposition at three edge-aware spatial scales. Broad illumination is compressed in exposure stops, structural bands are amplified independently, and fine texture receives noise/dark-input confidence weighting. This replaces the previous modest global curve plus capped additive detail. Adaptation is bounded, near-black/white endpoints are protected, and a soft highlight shoulder retains display headroom. Alpha-weighted analysis is bounded to800pixels per side. The current light conversion uses a gamma2.2 luminance approximation; this is an SDR editing operator, not a calibrated radiance pipeline. Preview/export still share PhotoRender; annotations stay after grading.

Natural uses meaningful illumination compression with restrained color and texture. Surreal has the strongest compression, exaggerated structure and cool/warm separation. Shiny uses stronger finishing contrast and selective narrow highlight bloom. Gritty has coarse/mid texture, deeper contrast and subdued color. Painterly reduces fine texture and applies broader luminous bloom with rich warm color. B&W has its own compression/contrast/texture and achromatic output. Zero strength and legacy generic-HDR drafts remain compatible.

Single-image processing cannot reconstruct clipped data or replace actual bracket capture/registration/deghosting/radiance merging. This change does not add bracket import or claim HDR-display output, RAW decoding or photographer-equivalent results. No proprietary presets, photos or software copied.

## Research

- Captain Kimo, Banyan Tree processing workflow: Photomatix, Photoshop/Topaz enhancement, Lightroom tonal/contrast finishing: https://captainkimo.com/captain-kimo-banyan-tree-bridge-road-post-processing-video/
- Captain Kimo, single-exposure portrait workflow: https://captainkimo.com/captain-kimos-hdr-portrait-ebook/
- Malcolm MacGregor, How I Did It Part6: real -2/0/+2 RAW exposures, Details Enhancer, selective color/levels/curves, dodge/burn, denoise and sharpen: https://malcolmmacgregorphotography.com/2010/how-i-did-it-part-6
- Alan Fullmer official portfolio describes bold, colorful, dramatic photography: https://alanfullmer.com/photography/
- Girolamo Cracchiolo official site resolved as https://www.omalorig.com/ ; dynamic portfolio did not expose process details, so no specific technique is attributed to him.
- HDRsoft Photomatix Pro6 manual, Details Enhancer/finishing: https://www.hdrsoft.com/support/doc/PhotomatixPro6.0_UserManual_Win.pdf
- HDRsoft FAQ, noise/halo precautions: https://www.hdrsoft.com/support/faq_photomatix.html
- Durand & Dorsey2002, log-luminance edge-preserving base/detail decomposition: https://people.csail.mit.edu/fredo/PUBLI/Siggraph2002/DurandBilateral.pdf . Our implementation uses guided filters, not their bilateral algorithm or code.

All21 unique focused checks passed, including6 final renderer cases, retained11 unchanged legacy/crop cases and4 unchanged UI/layer cases. Natural has tested local adaptation and >10/255 shadow lift/highlight reduction on the split-light fixture, exact half-strength blending, and edge/noise/alpha/zero/legacy checks. All six styles exceed7/255 mean RGB change on each of three scene fixtures at full strength; this is a visibility guard, not a perceptual-quality score. Real portrait and four scenic sheets were visually reviewed. The scenic assets are existing app artwork, not neutral camera originals; the portrait fixture provenance remains in docs/fixtures/photo-quality-portrait.md. Synthetic checks and host renders do not establish award-level quality or phone performance.

Initial compile timeout and both tuning findings are retained in docs/evidence/hdr-dramatic. Final runtime bundle matches compilation. See docs/hdr-dramatic-verification.json. No new APK requested or built;0.100.20 predates this change.

User subsequently requested an APK:0.100.21-rc1/code130 is assembled and verified with the original signer. See docs/apk21-verification.json.
