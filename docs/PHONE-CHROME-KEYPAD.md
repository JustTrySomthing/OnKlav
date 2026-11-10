# Phone chrome and keypad clarity — 2026-11-01

Phone now uses HomeHeader, the same header component as Apps, Messages and Groups:
the same background, 22 sp title styling, horizontal padding, height and themed
48 dp action controls. Embedded Phone has no back arrow; standalone/add-call
Phone retains its normal OnKlav back action. This replaces the mismatched Material
TopAppBar surface and semibold title. Main bottom-menu routing and insets remain
unchanged.

Phone and the Messages number-entry popup share DialPadColors. Each key has a
subtly accent-tinted surface and an opaque one-dp outline derived from onSurface
at 60% over that fill. Digits use the theme accent when its contrast is at least
4.5:1, otherwise onSurface. Letter labels use onSurfaceVariant. This deliberately
separates keys even when the parent's surface matches a key's original fill;
the former popup outline was only 5.5% foreground opacity. Tall Phone keys and
spacing, all input/long-press behavior, the prominent call button and root menu
clearance are retained. Messages keys now expose explicit button roles and
spoken Plus/Delete digit labels.

Contacts and Recents retain their circular themed quick-call controls, search,
favorites and filters. Cards have consistent 18 dp corners and theme outlines;
names have stronger weight. Unnamed callable recents show the formatted number.
Private/unknown presentation, multi-number choices, internal editing/messaging,
Telecom/default-role routing and destructive confirmations remain unchanged.

Validation: phone-chrome-keypad-verification.json and docs/evidence/phone-chrome-keypad.
Palette checks cover key boundary and text contrast in all 15 light/dark palettes.
Native UI fixtures exercise input/delete, Phone header size, both keypads, details,
and normal/small root-menu layouts. Host captures contain synthetic data only.
Physical-device font/theme/touch acceptance remains separate. These refinements are packaged in the subsequently requested 0.100.36-rc1/code145 APK; see RELEASE-0.100.36.md.
