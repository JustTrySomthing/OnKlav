# Phone keypad, quick calls and details — 2026-11-01

- Keypad keys are taller (60 dp compact, 74 dp normal), with 8/12 dp row gaps
  and 12 dp column gaps. Compact mode still scrolls within the existing workspace;
  the padded primary call row and root bottom navigation stay fixed.
- Main and row call buttons use the active theme's primary/on-primary colors.
  Unavailable calls retain a themed primary-container circle and remain disabled;
  blank numbers and private/unknown recent numbers cannot trigger a call.
- Every contact and recent row has a 48 dp circular quick-call control at the far
  right. The favorite toggle remains before the contact call control. A single
  valid contact number calls directly through the existing role/permission/SIM
  flow; multiple numbers open contact details for an explicit number choice.
- Contact and recent details use compact, bounded, scrollable, rounded native
  Compose dialogs: a visible close control, clear identity header, number cards,
  grouped actions, and separated destructive actions. Recent numbers are formatted
  once rather than repeated raw/formatted; date/time omit seconds. Contact numbers
  each have Message and a themed call button. Multiple-number block actions name
  their target number.
- Existing internal messaging/contact editing, number blocking, delete
  confirmations, default-Phone setup and Telecom call placement remain in use.
  Private caller presentation is honored; no extra permissions, connection engine
  or external screen shortcuts are introduced.

Validation is recorded in phone-detail-polish-verification.json and fixture-only
captures in docs/evidence/phone-detail-polish. User-supplied screenshots and their
personal contact data are not included. Physical-device touch/font/theme review
remains separate from host UI checks. These refinements are packaged in the subsequently requested 0.100.36-rc1/code145 APK; see RELEASE-0.100.36.md.
