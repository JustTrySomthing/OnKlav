# OnKlav 0.68.0-rc1

Focused fixes for the reported handset behavior, code76/target36. Existing
package and signing identity retained. This is a sideload release candidate.

- Floating dialogs use a subtle centered fade and grow (94–100%), with Android
  window travel disabled. SDK floating panels use a centered transition. Full
  pages and drawers retain their slide animation.
- Message-list long press opens the reference Read/Unread switch and Cancel/Save.
  The choice is a draft until Save. Save waits for persistence and updates the
  row before closing, preventing an immediately reopened stale switch.
- Wand matches the supplied reference: rounded black handle and plus marks,
  transparent outlined tip, and selected theme accent behind it.
- Contacts has Back and the circled Add Contact button; that is the only header
  action on the right and opens the phone’s contact editor.
- Walkie-Talkie title explicitly uses readable theme text. A saved/contact name
  takes precedence; the phone number is shown only when no name is available.
  This applies to the main panel and connection-error dialog. Message/contact
  handoff resolves the saved contact when Contacts permission allows it.
- Signed Walkie package13 keeps its publisher identity. Existing recipient data
  and preferences are retained on update.
- Fixed a concrete game-launch packaging defect: the embedded Godot AAR has no
  consumer shrink rules. The prior optimized usage report removed Dictionary
  and native callbacks. The new rules retain Godot classes and members, and APK
  verification rejects removed native callback classes/members. Game features,
  native binaries and the game pack are unchanged.

24 unique affected/shared host checks pass:12 app and12 shared. An initial test
compilation mistake was fixed; then a motion-measurement assertion was corrected
and a real immediate-reopen stale-state issue was fixed. Only those two affected
cases were rerun. Evidence, including the initial failures, is retained in
`docs/evidence/v0.68.0`. The previous0.67 broad451 checks and lint0 errors/143
warnings remain separate baseline evidence; they were not all rerun or counted
again here. No broad emulator/test loop was started.

No attached handset is available. Actual game launch on the Samsung phone,
carrier/two-phone behavior and measured performance still require physical-device
confirmation. Retaining the Godot bridge fixes a demonstrated build defect; it
cannot rule out a separate handset-specific crash without its crash trace.
Existing Google OAuth/Drive project, carrier/MMS acceptance and release-signing
qualifications remain open. No store publication was performed.

The optimized APK was assembled once. Its initial retention check rejected
harmless empty static initializers and R8-generated intermediate interfaces.
The check now allows only those entries and rejects removed original native
classes/callbacks; the same APK passes. Signature continuity, source/package
contents, ZIP integrity and16KB ZIP/ELF alignment all pass.

APK bytes: 39441232

SHA256: `10c16a9378fe9bab906902456c19584f462e491fd715d1b76b9e11357563b854`
