# Messenger, Authentic and utility refinements

## Messenger

Reopening an already-open conversation for a new incoming intent previously set draftLoaded=false although the address-keyed draft loader did not restart. The composer consequently stayed disabled. It now invalidates draft readiness only when the peer changes. The field explicitly reacquires focus and requests the keyboard on a normal tap; the send path no longer switches it into read-only mode. Outgoing text/attachments and their destination are captured before asynchronous sending. Completion removes only the sent draft prefix and sent attachments from the same conversation, retaining newly typed content.

The inbox has no Load older conversations button. A 41-row lookahead displays 40 rows, distinguishes exhaustion and loads the next page near the scroll boundary. A chronological date/id cursor avoids pin ordering corrupting pagination. The cursor participates in the loading signal so pages containing only hidden Archive/Trash rows can continue. Search and non-Inbox folders retain their own loading behavior. A small progress indicator appears only while a page is loading. The latest-message arrow now has a dark glyph on a light, theme-derived circle; the activities icon keeps its existing presentation.

## Authentic

Fresh setup collects the password/confirmation and all three grouped recovery choices/answers before Complete atomically writes any file. Leaving or backgrounding an unfinished fresh setup stores no password-only file and restarts enrollment. Complete returns to the password gate. A password-only legacy install or old backup cannot expose accounts before recovery enrollment. Its current password authenticates enrollment; existing encrypted accounts are preserved instead of silently erased. Existing explicit typed-confirmation erase/start-over remains available through help when recovery was never enrolled.

Recovery reuses VaultRecovery's authenticated second encrypted key wrap, with Authentic's distinct identity. All three normalized answers are required to reset the password. Reset authenticates and parses the complete existing ciphertext before atomically rewriting it; bad answers or corrupt data do not replace the file. Password changes preserve recovery, old encrypted backups retain their own passwords, and restored old backups require enrollment. Answers/passwords and decrypted accounts are not persisted outside the encrypted envelope. Existing secure-window/lifecycle locking, throttling, HOTP safeguards, clipboard handling and explicit import confirmation remain.

## UI details

Red eye / Stamp / Heal chips use shorter visible surfaces while retaining Material touch targets. Voice Record uses a larger outlined handheld microphone. Social's host-rendered placeholder says Coming Soon... at 28 sp; its existing permission-free signed placeholder package is retained. No native, SDK package or media-engine inputs changed and no dependency was added.

Verification is in messenger-authentic-ui-verification.json and messenger-authentic-refinements-checks. Host checks use generated data and fake providers; physical phone IME/carrier behavior and independent security qualification remain separate. No APK was requested or built; 0.100.29 predates this and the preceding Video Edit/Stamp refinements.
