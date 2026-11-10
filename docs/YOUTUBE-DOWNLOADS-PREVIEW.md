# YouTube downloads and Video Editor preview recovery — 0.98

The native YouTube header replaces Home with the existing themed down-arrow download icon. History, Channels and Bookmarks retain their positions and behavior. The dialog offers Video with 480p/1080p targets, or Audio explicitly labeled M4A. Progress reports actual received bytes, format preparation, saving, cancellation and completion. Completed media goes through the live session/capability broker into Files > Video > YouTube or Files > Audio > YouTube. Native shared media imports stream up to 2 GiB without loading the media into Rust memory; other file-type limits remain unchanged. Core tests cover destination restrictions, revocation, staging preservation and a sparse media file larger than the old 256 MiB limit.

## Sources and formats

The host reads the current signed-out page's player response and observes requests to public HTTPS googlevideo.com media endpoints. Metadata must match the current video; observed URLs must match the advertised media identity when present. Stale pages and ad playback are excluded. Signature-cipher URLs are not treated as working download links. There is no external downloader service, added extractor dependency, credential forwarding, signature deciphering or DRM bypass.

The selected quality must be available at that resolution or higher. Larger sources can be scaled down; sources are not upscaled. Quality labels use the short edge for portrait videos, and output dimensions preserve aspect ratio. Separate video/audio streams are downloaded and combined through Media3; native H.264/AAC output produces MP4 video and AAC-in-MP4 M4A audio. There is no MP3 encoder in this implementation, and AAC data is never mislabeled MP3. Active live streams are rejected until the broadcast ends.

Some videos do not expose usable public stream addresses to this viewer, use a different streaming protocol, have expired links, or require sign-in. The dialog gives retry/quality feedback and cancels cleanly rather than publishing an incomplete file. Playing a video at the requested quality may be necessary before the page requests its media URL. This is not a claim that every YouTube video is downloadable. No live YouTube download was verified in this environment; the HTTP range/redirect checks use controlled responses. The maximum is 2 GiB per stream and final imported media; temporary conversion space is also required. Downloads are foreground session operations, not persisted resumable background jobs.

Every transfer rechecks the current session/video and granted storage/file-management access. HTTPS hosts, ports, paths and redirects are constrained. Range offsets, total sizes and available validators are checked; expired URLs, broken ranges and storage failures are surfaced. Files become visible in the shared library only after successful conversion and brokered commit. Cancellation/failure removes temporary output and partial staging.

## YouTube sound

A new video or new media element receives an explicit unmute pass, independent of the separate external-link autoplay intent. This covers bookmarks and normal page navigation. The bounded startup guard also handles a late site mute; trusted use of the mute control ends it. This does not override Android's device volume. Existing pause, signed-out navigation, channel metadata and viewport behavior are preserved. Provider markup can change, so phone acceptance remains separate.

## Editor preview

The reported screenshot identifies a Media3 video-frame-processing failure, but does not include its underlying exception or the original clip. The exact phone failure was not reproduced here.

Previously even an unchanged single clip used the experimental composition/frame-processing pipeline. Unchanged clips now use a standard ExoPlayer with decoder fallback and the same trim/volume, bypassing unnecessary video effects. Actual edits retain composition preview. If frame processing fails, the editor prepares a compatible 720p-capped temporary MP4 using decoder tone mapping and plays it through the standard player. Export also prefers decoder HDR-to-SDR tone mapping; identity rotation/zoom effects are omitted.

Preparing the compatible preview shows progress and supports cancellation. Failure is latched to the current project revision, so returning to idle cannot start an endless automatic retry loop. A new edit or explicit Play can retry. The native UI checks do not exercise phone GPU/codec rendering; test the original clip on the handset before claiming that specific failure is physically resolved.

## Verification

- 286 Rust core cases passed on the final core source, including the three new media-import/save-policy cases.
- Seven existing Video Editor checks passed (six backend and one themed UI).
- Seven YouTube repair checks passed (six backend and one themed dialog), including checked redirects/ranges, incomplete-range rejection, source identities, no upscale, portrait dimensions and header behavior.
- Four exact-source JavaScript fixture groups passed: ad/pause cleanup, navigation/unmute/viewport, channel ownership and download metadata.
- Full debug lint includes main, host-test and instrumented-test sources: zero errors; warning counts are recorded in the verification JSON.
- Linux, main ARM64 and diagnostic ARM64 JNI match current source, retain 16 KiB load alignment, and use the original package/publisher identities. The included YouTube package is version41 with an explicit files.manage capability.
- The native themed dialog was inspected. Runtime class-byte comparison is recorded separately. Physical media decoding, rendering, live provider downloads, storage pressure and long-file performance remain unverified.

The requested OnKlav0.98.0ARM64 APK is assembled and verified with the original signer and all22included apps. The packaging configuration merges the email libraries’ license/notice resources. Preserve the previous APK and Email account-qualification requirements; physical acceptance remains open.
