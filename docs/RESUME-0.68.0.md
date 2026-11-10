# OnKlav 0.68.0-rc1/code76

Follow-up corrections from the user’s handset observations and two attached
visual references. No broad audit or emulator loop. Games remain deferred except
for the concrete optimized-build launch regression.

- Shared ReferenceDialog and OnKlavAlertDialog use a centered 94–100% scale and
  fade. Native window animation is disabled. Full pages/drawers still slide.
- SDK floating panels (Walkie) enter and leave centrally, retaining exit content.
- Inbox conversation long press opens only a draft Read/Unread switch and
  Cancel/Save; cancel does not persist, save writes the chosen state.
- Wand uses rounded black handle/pluses and a genuinely transparent outlined
  tip on the selected theme accent circle, matching the supplied reference.
- Contacts header contains Back and only the circled Add Contact action on right.
- Walkie panel title explicitly uses onSurface. Selected/contact-handoff names
  take precedence over numbers; a number is shown only as the name fallback.
  Signed Walkie package13 retains its publisher identity.
- Godot embedded AAR contains no consumer keep rules. Previous optimized usage
  removed Dictionary and native callbacks. Keep the Godot namespace intact and
  verify callback classes plus absence of stripped members in optimized output.

Testing/build evidence:docs/evidence/v0.68.0. Existing core JNI/game pack are
source-matched and reused. No new game features. Existing development signer and
package identity retained. Physical Samsung/game/carrier acceptance is unverified;
prior release/account qualifications remain open. See source-verification.json
and the final APK verification report for actual completed evidence.

Final evidence:24 unique affected/shared host cases pass. One optimized APK
assembled and signature/content/16KB alignment verified. The initial native
retention check was too broad for empty static initializers/R8 intermediates;
the same APK passes the narrowed original-class/callback check. No rebuild.
Source/SDK packages and TODO are saved with this APK. Device acceptance is open.
