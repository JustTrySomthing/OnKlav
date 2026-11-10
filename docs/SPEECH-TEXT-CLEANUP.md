# Shared speech text cleanup — 2026-10-30

All generated speech passing through SdkSpeechController (Messages, Read Aloud, eBooks and SDK speech clients) now uses the same host-owned SpeechReading projection before the existing neural backend. Source documents/messages remain unchanged. No app-specific model, extra voice, cloud call or pronunciation-data rewrite was introduced. Existing two voices, speed settings, native synthesis frontend and audio lifecycle remain.

## Automatic rules

- Expand Mr., Mrs., Ms., Dr., Prof., Rev., Capt., Lt. and Sgt. before a following word. Their abbreviation periods do not reach sentence segmentation. Ordinary spaces and non-breaking spaces are recognized.
- Expand No. before a number; preserve a sentence such as “No. I disagree.”
- Remove dots in a conservative multi-initial name pattern such as “J. R. R. Tolkien”; retain isolated initials/ordinary sentences where context is ambiguous.
- Silence standalone canonical ASCII Roman section markers such as I., II>, III and IV. Pure Chapter/Section/Part/Book labels with a Roman or numeric index are skipped. Inline Henry VIII and World War II remain. A lone unpunctuated I remains to protect the pronoun across hard line wraps.
- Silence Unicode emoji/pictographs, joined family/profession sequences, skin tones, flags and keycaps. Ordinary ASCII digits, #tags, arithmetic, percentages, punctuation and plain ©/®/™ remain. Explicit emoji presentation of those marks is silent. Literal words such as “very satisfied” are never deleted.
- Silence standalone decorative dividers and simple standalone ASCII smileys; preserve ordinary hyphenated words and inline punctuation.
- Remove discretionary soft hyphens, BOM/word-joiner controls, and normalize zero-width/Unicode word spacing without joining adjacent words.

Every spoken UTF-16 position maps back to its original source position, including expanded honorifics and removed emoji. Highlighting and resume continue to use the original document. Emoji/marker-only requests complete silently rather than opening a synthesizer and reporting failure. The original SDK input bound remains 50,000 units; the internal projected-text allowance is bounded at four times that amount for expanded titles. Native synthesis pieces remain bounded at 220 units. Initial-name matching is bounded to prevent pathological backtracking in long text. The approved ordinary-prose sample remains text-identical.

## Additional improvements worth considering

1. A user-editable shared pronunciation dictionary for personal names and unusual places, with explicit overrides rather than silently guessing.
2. Context-sensitive abbreviations: St. (Saint/Street), Dr. as a road suffix, a.m./p.m., dates, units, fractions and currencies. Preserve meaning until context is reliable; the existing native frontend continues handling them.
3. Reader-aware suppression of navigation, repeated headers, page numbers and other explicitly non-narrative markup. EPUB has structure, but the shared speech input is currently plain text; global rules must not blindly delete meaningful numbers or quoted text.
4. Optional natural readings for URLs, email addresses and technical/code content, where user intent differs from ordinary prose.

These are deterministic English reading rules, not semantic understanding. Plain standalone Roman-looking words can be ambiguous; no blanket Roman-numeral deletion is applied inside sentences. Real hyphen-plus-line-break repair, ambiguous abbreviations, emoticon-like code, and all possible publisher markup are not claimed solved. Recorded audiobook audio is unaffected by text preparation.

Android Unicode properties used: https://developer.android.com/reference/android/icu/lang/UProperty

## Verification
Production Kotlin/runtime compilation passed. 19 unique focused host checks passed: normalization, Unicode sequences, title/number/initial handling, source position mapping, silent completion, maximum input/regex bounds, unchanged approved prose, SDK authorization and stale callbacks, speech-service lifecycle, safe synthesis-piece planning and warm-model trimming. Existing model/native data are unchanged. No new acoustic quality measurement, device listening pass or APK is claimed.

## Conservative safety follow-up

Unlabelled Roman-marker filtering is now restricted to compact forms. Standalone I/V/X/L/C/M, units such as 12 V and 20 °C, and Roman-looking words such as MIX/MIX. are retained. Clear I./II>/XX> markers and explicit Chapter/Section labels still work. Title, number-label and multi-initial expansion only uses horizontal whitespace; it cannot join separate lines. Normal spaces, tabs and non-breaking spaces are supported. This intentionally favors preserving ambiguous text over aggressively deleting it.

A pronunciation editor, new dictionary files, broad EPUB parsing changes, voice/model adjustments and new settings are deferred. Existing synthesis, audio focus, cancellation, permissions and reading-position mapping are unchanged.

Safety follow-up verification: production compilation and 21 focused host checks passed, including both added preservation regressions. No native/model change or APK build.
