# OnKlav 0.100.37-rc1 / code146

Owner-requested fast ARM64 unshrunk debug APK, original package and signer.

Phone: removed Hold helper text, inline Speed dial/Voicemail links, Paste and upper Delete controls. Speed dial and Voicemail are right-aligned header icons. Delete (hold to clear) replaces the overflow beside Call; Save, Call and Delete now belong to the same non-scrolling responsive keypad group. Key target heights and maximum group width reduced 10%; layout adapts to available height. Number formatting, long-press voicemail/speed dial/plus, call role/permission/SIM checks and contact saving remain.

Apps: adds 10 dp above the desktop grid, outside its drag coordinate viewport to preserve drop geometry.

Host layout/input checks and full production/test lint are recorded in phone37-verification.json. APK verification is recorded in apk37-verification.json. Physical phone/carrier acceptance remains separate.
