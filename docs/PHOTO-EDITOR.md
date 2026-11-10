Current crop behavior: geometry, color and effects remain visible in Crop, with projected handles and inverse pointer mapping. The selected region uses the exact export grading; outside context permits expansion. See [EDITOR-CROP-CONSISTENCY.md](EDITOR-CROP-CONSISTENCY.md). The historical untouched-original Crop behavior described below is superseded.

See [PHOTO-EFFECTS.md](PHOTO-EFFECTS.md) for the newer HDR/sharpen processing and four graded looks.

# Image Editor — direct geometry and compact controls

Geometry now uses an on-image control frame, with eight resize/perspective points and a separate rotation handle. Rotate, linked scale, width/height scale and the two perspective axes are selected from the left tool button. Dragging a side in Scale changes that axis; corners scale both axes. The rotation handle works in every geometry mode. Each drag commits one history entry. Quarter turns and flips remain discrete entries in the geometry menu. Fit restores a complete view after enlargement. Preview and export share photoGeometry's bitmap matrix; the color/effect pass happens before geometry so spatial effects also agree.

Open and Save Copy are in the host's actual top bar, at the right, Save to the left of Open. The workspace's old extra toolbar is removed when embedded in the host. Undo, Redo, Compare, Fit and Reset share the lower row; Fit sits immediately left of Reset. A filled, left-aligned tool button opens an anchored menu. Geometry's frame icon differs from Reset; there is no secondary effects button. The four mode tabs remain below.

Warm Film is removed from the UI. Color's menu includes Original Color, Black & White and Sepia, retaining independent color adjustments. Effects contains adjustable HDR, Vignette and Sharpen. HDR is a single-image SDR tone/detail effect, not recovery of missing sensor data or HDR-format export. Sepia uses a channel matrix and keeps transparency. Existing edited drafts are retained, including any manually adjusted warmth/contrast values.

All remaining sliders (including JPEG quality) have a default tick, snap gently near the default, and use a neutral thumb/track at the default versus the theme accent when changed. Each has its own reset. Sliders read current model values, with history/all-reset re-creating slider state so the thumb, value and image stay synchronized. All-reset also restores viewport framing. No geometry sliders remain.

Original app ID, signed version3 shell, publisher, capabilities and private draft paths are preserved. No core/JNI changes. No new APK is requested for this source task; the latest delivered build remains0.100.5. Phone touch/provider acceptance remains separate.

## Previous implementation history

# Image Editor — workspace polish

The existing Photo app is now named Image Editor in the launcher, signed app manifest and frame title. The app ID `app.onklav.photo`, package filename and private draft path are unchanged, retaining upgrades, permissions and user edits. Signed app version is now 3.

Crop uses the full oriented source inside its actual fitted image bounds. Eight corner/side handles resize the selection; dragging inside moves it without resizing. Edges also accept touches along their length. Selection is bounded to the image, with a 5% minimum span, an outside scrim and thirds grid. One completed/cancelled drag commits one undo entry. Crop coordinates are applied before geometry transforms, matching the existing renderer. Ratios are centered presets; subsequent manual dragging is freeform. Crop mode shows the source so previously cropped edges remain available to expand again; other tabs display the rendered edits.

The image takes the remaining workspace height. The old rows of scrolling chips and crop sliders are gone. Top icon actions open, undo, redo, compare and save; all have accessibility descriptions. Four labeled icon tabs switch editing modes. Ratios, transforms, effect presets and adjustment choices use dismissible dialogs. Non-crop tabs show only one adjustment slider and its value. Fit restores pinch/pan; Reset and replacement remain confirmed. Undo, draft persistence, export formats, capabilities and original-file preservation remain unchanged.

No new APK requested for this task. Physical phone gesture/provider acceptance remains separate from local checks.

## Earlier implementation details

# Original Photo implementation (0.92)

Photo is one signed included SDK app inside OnKlav, not a second APK. The Rust/Wasm shell supplies app identity/lifecycle and the Android host supplies a bitmap editing workspace. The native workspace is restricted to the reserved first-party identity; this is not yet a public third-party image-processing SDK component. No core/JNI source change is needed.

Tools: reversible centered crop ratios and per-edge crop; horizontal/vertical flip; quarter turns and continuous straighten; linked and independent width/height scaling; horizontal and vertical perspective; exposure, brightness, contrast, saturation, hue, warmth, tint, black/white points and gamma midtones; vignette and sharpen; monochrome and warm presets. Pinch/pan, Fit, alignment grid, before/after, bounded parameter undo/redo, reset confirmation, and replacement confirmation. Original imported bytes are private and unchanged. A single current draft survives reentry/process restart; undo history lasts for this host ViewModel only.

Open uses Android's document picker, without broad gallery/storage permissions. Save Copy uses Android CreateDocument and writes JPEG (quality choice, white transparency background) or PNG (alpha preserved). Reopening an edit never overwrites a user's original. Import validates the new file before replacing the old private source. Delete-app-data removes the draft and private source. Only user-selected files are accessed. No network services.

Limits: 150MB encoded input; sampled previews up to1200px per side; export decoding up to4000px per side (power-of-two sampling may be smaller); output capped at16MP. Import/export errors stay visible. This is a raster photo editor, not a layered illustration/RAW/CMYK editor. Wide-gamut/HDR, lossless JPEG transforms, masks, clone/heal, freeform selections and layers are not implemented. Color calculations are bounded sRGB approximations. Export intentionally does not copy original location/EXIF metadata. Phone gestures and document-provider acceptance remain to be verified on a handset.

Validation:3unique Android tests passed in the final selection, covering pixels and signed app launch/tool navigation/undo/save dialog/permission revocation. Full lint passed with0errors186warnings. The remaining EXIF warning recommends the AndroidX parser; the framework parser is valid for this app’s API29minimum. Stock KTX suggestions remain warnings. Screenshot inspected for layout; the final surface contrast refinement is in the final UI capture. No APK requested or built; last delivered APK remains0.91.
