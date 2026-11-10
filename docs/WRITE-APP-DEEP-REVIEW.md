# Write app review — 2026-10-30

## Main conclusion
The biggest everyday usability defect was the selection-to-formatting transition. The editor explicitly hid the keyboard, grew bottom padding from 40dp to 280dp, scrolled to the selection, and opened bottom-positioned controls. Android's selection action mode could remain active through that transition. Those competing behaviors explain the reported movement and awkward formatting interaction.

Color and Highlight now replace the existing two toolbar rows with an inline palette of the same height. Swatches apply immediately and remain available for comparison, with a selected-color indicator, Default/Remove, Custom, and Done. Formatting preserves the selected range and scroll position, dismisses the native selection menu only when entering formatting, and leaves ordinary copy/cut/paste available during normal selection. Typing or tapping the page leaves formatting mode. Font/list/alignment/indent menus anchor near the toolbar rather than rising from the bottom. Custom colors remain an explicit dialog. The keyboard is not forcibly hidden for ordinary formatting, and artificial padding/scrolling was removed.

The preceding Text-first toolbar arrangement, separate Lists/Align/Indent, and stacked Redo/Undo are preserved.

## Confirmed findings addressed in source

| Priority | Finding | Change |
|---|---|---|
| High | Backwards selections can collapse into a caret because start/end are clamped without ordering | Normalize selection endpoints before character and paragraph formatting |
| High | Selection, keyboard, padding, forced scrolling and the native action menu compete | Stable inline palette, retained range/scroll, contextual-menu handoff, no artificial scrolling or keyboard dismissal |
| High | A damaged current document is silently ignored, then shown as a blank Saved document | Visible opening failure, blocked placeholder editing/saving, Retry and document-browser recovery; original retained |
| High | A delayed autosave belongs to the ViewModel and can be canceled when the editor is cleared | Flush on backgrounding; tracked final snapshot outside the cleared ViewModel scope, ordered by the same disk mutex, with a newer-snapshot check; reopen waits for pending saves |
| Medium | A character-formatting operation can throw after spans are removed, leaving the native editor guarded | Validate the replacement before mutating spans; restore the formatting guard in finally |
| Medium | Typing immediately after formatting can merge into the formatting undo entry | Separate typing and formatting groups using monotonic edit timing |
| Medium | Open/import/delete/export can be re-entered during a busy operation | Explicit busy guards; failed-open recovery does not autosave a blank placeholder |
| Medium | Saved/Saving is hidden; operation errors add/remove a row and move the editing area | Persistent compact save-state feedback with access to details in the existing footer |
| Medium | DOCX indentation is exported but not imported | Import supported paragraph indentation from Word units |
| Medium | DOCX imports silently omit images, table layout, headers and footers | Detect these unsupported features and show import details; preserve the original file |
| Medium | Replace-all size arithmetic uses Int multiplication and can overflow before applying the document limit | Validate the projected size with Long arithmetic |

## Remaining confirmed issues and refinements

1. **Image fitting on resize:** `WriteNativeEditor.scaleLists` only reduces drawable bounds, and has no editor size-change handler. Images can remain at a smaller prior width, and orientation/window changes need explicit refitting from intrinsic dimensions. This remains a source-confirmed follow-up, not a measured handset defect.
2. **Import fidelity:** DOCX images/tables/headers/footers remain unsupported even though their omission is now reported. HTML import does not restore embedded base64 images with a custom image getter. Retain source files; use Write format for editable fidelity. Extending import support is a substantial follow-up.
3. **Selection indicators:** toolbar color/size display uses the start of a selection and can fall back to a remembered color on unformatted text. A selection containing several colors or font sizes needs an explicit Mixed state rather than implying uniform formatting.
4. **Document library performance and damaged-file visibility:** listing parses each full document before truncating its preview, and excludes unreadable documents. A metadata index, paged loading and recoverable damaged-document rows are useful for larger libraries. Slowness is a source-level scaling concern; no device benchmark is claimed.
5. **Undo and recovery:** history is limited to 30 in-memory document snapshots and is not durable revision history. Add a recoverable earlier revision and memory budgeting for large rich documents, while preserving explicit Save/Discard semantics.
6. **Long-document feedback:** replace-all is guarded against oversize output, but many edits/proofreading/rendering operations still run across an entire document. Add measured large-document qualification, progress/cancel for lengthy work, and avoid repeated image decoding. No broad responsiveness guarantee is claimed.

## Important feature gaps for everyday word processing

- Named paragraph styles, line spacing, paragraph spacing and meaningful style editing rather than only size/bold presets.
- Tables and fuller Word import fidelity, followed by page breaks, configurable paper/margins/orientation, headers/footers and page numbering options. PDF currently uses a fixed A4 layout with page numbers; the editing view is not a paginated WYSIWYG Word layout.
- Better image selection, resizing, alignment and alternative text; currently insertion is inline and bounded.
- Find/replace case and whole-word options; dedicated formatting/save keyboard shortcuts and a discoverable shortcut list.
- Durable revision recovery and optional document trash independent of external linkage.
- Broader language proofreading. Current English (US) spelling/common-rule grammar is explicit and offline; it is not a general grammar/style engine.

## What was retained and inspected
Private atomic document storage, migration from Notes and linked Files, Undo/Redo, word count, search/replace, spell/grammar review, hyperlinks, image insertion, HTML/TXT/Write/DOCX/PDF export, document browser, capability checks and revocation behavior remain. Existing transactional export handling is reused. No new model, dependency, guest permission, APK or signing identity was added.

The review inspected WriteEditor, WriteTools, WriteDocument, WriteImages, WriteDocumentsDrawer, WriteSaveDialog, WritePendingSaves and their existing tests and export/migration paths. It is not an independent security audit or proof of full Microsoft Word compatibility. Physical Samsung selection handles, Gboard, keyboard resize, rotation, accessibility and long-document performance need handset qualification. Force-stop/process death before a write can be scheduled cannot be claimed lossless.

## Verification
Final production compilation and 16 unique focused host checks passed. Coverage includes range/scroll preservation during native action-mode dismissal; backwards selection; two successive highlight choices without changing keyboard state, editor bounds, padding or scroll; text color application; real editor formatting/export/clipboard revocation flow; damaged-current-document recovery; final draft persistence after ViewModel clearing; separate undo grouping; invalid character-format safety; indentation import and unsupported Word-content notices; atomic storage and explicit Save/Discard. Screenshot review confirmed the palette occupies the toolbar rather than the page or a bottom sheet. The palette fixture restores deliberate test-only permission revocation from the prior case. No product permission revocation is overridden.
