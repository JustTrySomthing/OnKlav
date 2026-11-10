# OnKlav 0.101.51-rc1

This update addresses game startup and conversation activity failures reported
on 0.101.50-rc1.

- Games use an opaque Android window for their WebView and orientation changes.
- Opening Walkie-Talkie from a conversation uses the existing contact selection
  broker, including when changing an already selected recipient.
- Verification replies and game turns recognize the reported carrier separator
  conversion while retaining strict frame validation and the existing send format.
- Walkie control routing recognizes the same converted separators and preserves
  app and publisher routing checks.

Both participants should install this update before retrying the invitation.
