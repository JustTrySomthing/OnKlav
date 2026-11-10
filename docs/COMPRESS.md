# Compress

Compress is an included OnKlav host utility beside Format Swap, with its own inward-arrow squeeze icon. It is part of the single APK, not a newly signed SDK package. Existing publisher/Android identities and prior app work are unchanged; no codec/native dependency was added. A distributable third-party SDK package would need the original publisher signer.

## User experience

Four themed cards: Images, Videos, Audio, Files & folders. The numbered Choose → Size → Save indicator makes progress explicit. Original files are read only; the user saves a separate copy through OnKlav Files. The size summary and Compress/Create ZIP action remain fixed below the scrolling options. During work, this area shows progress and Cancel. After compression, it shows actual before/after sizes and percentage saved; if the result is larger, it says so and offers Adjust settings or Save copy anyway. A cancelled save picker leaves the result available. Completion offers Share saved copy, Compress something else, and Done.

Sharing opens Android's user-controlled chooser with a read-only content URI to the durably saved copy. It never automatically sends a message or email. Applications/carriers impose their own limits; the app does not promise a universal MMS/email attachment size. File/folder selection and every save use OnKlav Files, including connected phone locations. Only Files' existing Connect phone folder action uses Android's tree grant.

## Controls and output

| Content | Simple controls | Output and size feedback |
| --- | --- | --- |
| Images | Small800 / Medium1600 / Large2560 px longest edge; Smaller/Balanced/Clearer; JPG or WebP | Real local encode with 55/75/90 quality. The debounced size preview measures that same encoder, so it matches the output bytes for unchanged source/settings. No upscaling. JPG flattens alpha to white; WebP preserves it. |
| Videos | 480p / 720p / 1080p; Smaller/Balanced/Clearer | H.264/AAC MP4, aspect ratio retained, requested bitrate by size/quality; up to30fps via frame-drop effect. Portrait uses the chosen short edge. Output dimensions shown. Estimated size is duration × requested bitrates plus overhead; codec fallback/content can change the actual result. |
| Audio | Smaller64 / Balanced128 / Clearer192 kbps | MP3 via shared decoder/encoder. Estimate uses duration and chosen bitrate plus overhead. Original sample rate/channels are kept where the existing encoder supports them. |
| Files/folders | Choose up to20 files or one folder; no lossy quality control | ZIP level9, preserves file bytes, relative paths and empty subfolders. Duplicate sibling names are disambiguated. Projected size honestly says it varies; the finished archive is measured. Already-compressed content may grow. |

Video bitrate requests (Mbps): 480p .6/1/1.8; 720p1.2/2.5/4; 1080p2.5/5/8. Video AAC requests96kbps. Hardware encoders can fall back, and compatible existing AAC may be passed through; estimates are never presented as guarantees. No frame interpolation, trimming, background playback or recompression of opaque ZIP member contents is performed.

## Format and resource limits

- Image inputs use Android ImageDecoder: common JPG/PNG/WebP/GIF/HEIC/HEIF subject to device decoding support. Animated inputs convert the first frame with a warning. Existing shared image code enforces32MP/header and heap-budget limits, removes location metadata and emits standard sRGB (API34+ gain maps removed).
- Audio/video input uses existing platform extractors and decoders; MP3 has the shared native codec. Protected media and unsupported codecs fail visibly. AVI/WMV are not promised. Audio decoding is mono/stereo,8–192kHz, at most two hours, matching the existing shared service. WAV/FLAC inputs are reencoded to MP3; lossless input quality cannot be preserved at a lower lossy bitrate.
- A maximum1GB of source data is staged privately. At most10,000 archive entries, depth32, with per-stream space/cancellation checks. The host Files archive limit is256MB; oversized ZIP results are rejected before offering Save. Media saves use existing audio/video limits. ZIP paths reject absolute/traversal/backslash paths; app-selected names are sanitized and duplicate names numbered.
- Folder traversal uses FileSelectionSession and its signed Files controller, per-action authorization and folder-vault checks. It never parses the content index, follows arbitrary filesystem paths, silently skips unreadable entries or accepts revoked access. Encrypted root/child folders are rejected for ZIP; choose unencrypted content instead.
- Source copies, ZIP streams, shared audio decode/encode and save streams check coroutine cancellation. Media3 cancellation releases its transformer. Image previews are debounced and serialized with final image compression to avoid overlapping full decodes; stale preview generations cannot replace current size feedback.
- Work survives configuration changes in the activity ViewModel, but is not a persistent background job. Android process termination can interrupt it. Cache files are cleaned when replaced/reset/model-cleared; orphan caches after process death remain Android-managed. No survival or screen-off completion guarantee is claimed.
- Saves use writeFiledExport and wait for the Files durable receipt before saying saved. Interrupted saves are not shown as complete; Files' existing recovery feedback is retained.

## Validation

Production compilation and eight focused host checks passed. Real image encode/decode, size preview/output equality, ZIP byte and hierarchy round trips, actual signed Files folder traversal/revocation, original-preserving save, cancellation/error recovery, estimates, portrait/landscape/large-text UI were checked. A visual review moved the size/action area into a fixed footer and simplified image size labels; the same bounded eight checks then passed again. See compress-verification.json and compress-checks/. Actual device acceptance is still required for MediaCodec/Media3 output playback/bitrate behavior, HEIC/HDR images, long media, screen-off/process death and connected phone-folder sharing. No new APK was requested or built. Reviewed screenshots are in evidence/compress/.
