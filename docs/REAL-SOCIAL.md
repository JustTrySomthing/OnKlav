# Real Social

Real Social uses the existing included Social app identity and the OnKlav
theme. Its native screen owns My Page, Friends, and Feed. Posts, attachments,
friendship state, and pending carrier updates live in the private device store.

Each recipient gets an individual carrier message. Control packets and short
text posts use SMS. An attachment uses MMS with one media part and the same
routing envelope. The receiver intercepts recognized frames before filing into
Messages. When a carrier submission fails, Real Social reports the failure
and avoids automatic replay of an ambiguous send. Carrier charges and size
limits still apply.

A friendship requires acceptance. The host checks Android's originating
number against the friendship and its random token. This is carrier identity,
not end-to-end encryption. New friendships exchange up to three posts from
the last 30 days. A periodic maintenance job expires received posts and their
local attachment copies. A separate Files copy is kept. Replies can travel
directly between the post author and a friend without exposing other friends'
private replies. Removal exchanges a silent control message. Blocking also
rejects subsequent requests locally.

Verified peers reuse the existing game verification ledger. Real Social tries
a direct invitation first for a known peer, then falls back to the shared
OnKlav probe if needed. Connected Walkie peers can contribute to the shared
ledger. Existing game and Walkie invitation protocols remain compatible.

The signed Social package continues to supply its original identity and
installation metadata. OnKlav's host supplies the new screen and display
label. Test coverage includes packet validation, local storage, the friend
handshake and history limit, and the Android screen. Actual two-phone carrier
delivery still requires device validation.
