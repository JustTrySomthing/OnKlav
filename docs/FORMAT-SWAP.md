# Format Swap

Format Swap is an included host utility, registered beside Power in the desktop and Apps settings. It uses the host's existing file, media and document services without creating a second APK or a new publisher identity. No guest conversion capability or permission bypass is added. A future distributable SDK version requires the original publisher signer.

## Flow

Four themed category cards lead into numbered File → Format → Save steps. Choose a source with OnKlav Files, choose an actual supported encoder/output, convert locally, then choose the destination using OnKlav Files. Conversion completes before asking where to save. Cancelling that picker keeps the converted copy available to save again. The final screen offers Convert another file and Done. Original files are read only. Errors keep the selection/options available for retry.

The work is scoped to the host ViewModel (survives rotation); cancellation is cooperative for copied/decoded audio and source/export streams, and Media3 cancellation releases the transformer. Android process termination can interrupt a conversion; jobs are not persistent background work. No completion notification or guarantee of surviving process death is made.

## Actual capabilities

| Category | Input | Output |
| --- | --- | --- |
| Image | Device ImageDecoder: JPG, PNG, WebP, GIF, HEIC/HEIF | JPG, PNG, WebP, single-page PDF |
| Audio | Platform extractors/decoders; MP3 uses existing shared codec | MP3, 16-bit PCM WAV, AAC in M4A, 16-bit PCM AIFF |
| Video | Device extractor/decoder: typically MP4/MOV/MKV/WebM | H.264/AAC MP4 or extract MP3/WAV/M4A audio |
| Document | DOCX, text-only ODT, HTML, TXT; also MD/CSV as text | DOCX, PDF, HTML, UTF-8 TXT, text-only RTF |
| PDF | Unencrypted PDFs supported by Android PdfRenderer | Selected page as PNG/JPG, rendered to a 2000 px longest edge |

Supported containers do not guarantee every codec inside them. Audio imports are mono/stereo, 8–192 kHz and at most two hours, matching the shared decoder. MP3 output uses the existing bundled licensed codec. No new native codec dependency is added. FLAC/OGG input depends on device support. AIFF output is native PCM serialization with an IEEE extended sample-rate field; AIFF input is not promised. AAC input depends on the platform extractor (ADTS or M4A). No standalone AAC output is mislabeled as M4A.

Legacy binary DOC, RTF input, AVI/WMV, DRM/protected media, animated GIF encoding, HEIC encoding, FLAC encoding and layout-preserving PDF-to-Word are not offered. They require additional vetted parsers/codecs or complex reconstruction. Renaming a file extension is never used as conversion.

## Options and limitations shown to users

- Images: original / 2560 / 1280 px maximum edge, without upscaling; JPG/WebP quality 50–100. Transparency becomes white for JPG. Other output retains alpha where supported. Metadata is removed; HDR is mapped to standard sRGB. GIF/animated WebP input converts only the first frame and shows a warning.
- Audio: MP3 128/192/320 kbps. WAV/AIFF preserve decoded sample rate/channels at 16-bit PCM. AAC uses the shared mono128/stereo192 encoder. Converting to a lossless container cannot restore source quality lost earlier.
- Documents: DOCX's existing importer reports unsupported features before conversion; ODT imports text only. Text/RTF exports remove formatting and images. HTML layout/styles/images may change. PDF is a repaginated Write export, not the original document layout. All document outputs ask the user to review the copy.
- Media metadata/subtitles are not copied. Video is exported with Media3 and no trimming/editing; codec failures remain visible.
- Up to 1 GB source; documents up to 16 MB / 500,000 characters. ZIP inflation is bounded (24 MB, 512 entries); XML entities/DOCTYPE rejected. Image headers capped at 32 MP and actual decoding guarded by heap budget with a resize suggestion. File streams check free space and cancellation. Work files are private cache and cleaned on replacement/model disposal. Normal cache eviction remains Android-managed after process death.
- Saves use writeFiledExport and await the durable Files receipt. A failed/cancelled write is not reported as saved. Recovered-save errors instruct users to check Files before retrying.

## Verification

Production compilation passed. Nine focused host checks passed across the main run and the final DOCX paragraph-expectation rerun. Real JPG/PNG/WebP encoding/resizing/alpha, WAV/AIFF headers/sample roundtrip, DOCX/HTML/TXT/RTF output, bounded ODT/text input, full image conversion/save/Done with original-byte preservation, silent-video targets, and portrait/landscape/large-text UI were checked. PDF native generation/rendering is unavailable in Robolectric 4.14; those paths are explicitly deferred to device acceptance, not mocked as passing. See format-swap-verification.json and format-swap-checks/; reviewed screenshots are in evidence/format-swap/. Physical phone acceptance remains required for real device MediaCodec/Media3 input combinations, HEIC/HDR, large files and end-to-end external-folder filing. No APK was requested or built for this source change.
