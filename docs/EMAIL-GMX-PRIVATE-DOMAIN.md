> Historical GMX/private-domain refinement. GMX is now removed completely; current choices are Yahoo, Google and Private domain. See GMX-REMOVAL.md.

# Email — GMX and Private domain

New-account enrollment offers exactly **GMX** and **Private domain**. The empty
mailbox has two large theme-aware buttons; Add account opens the same two choices
in the account form. Gmail and other historical provider records remain readable
and manageable, preserving existing encrypted credentials, downloaded mail and
local drafts. They are not offered for new enrollment.

GMX uses IMAP TLS on port 993 and SMTP with required STARTTLS on port 587.
International defaults are imap.gmx.com / mail.gmx.com; gmx.de, gmx.net, gmx.at
and gmx.ch addresses use imap.gmx.net / mail.gmx.net. Typing an address updates
unchanged provider defaults and email-based usernames, while preserving manually
customized server names and usernames. Provider switching clears typed passwords.

The GMX form has Setup directions: two short numbered steps to enable IMAP and
return to enter the full email address/password. It explains that two-factor
accounts use a provider-issued application-specific password, which OnKlav stores
in the existing encrypted account store. Subsequent mail access does not prompt
for a two-factor code on every launch; revocation or provider policy may require
reconnection. Official IMAP and app-password help links remain available through
the existing external-link capability check.

Private domain opens blank server fields, safe port/encryption defaults and
editable incoming/outgoing usernames. The hosting provider must supply a working
mailbox and secure IMAP/SMTP access; domain registration alone is not mail hosting.
No discovery guesses, new dependencies, backend, permission bypass or change to
the existing send/draft/notification logic is introduced. Existing Google and
Microsoft authentication code is retained solely for configured historical accounts.

Official setup references:
- https://support.gmx.com/pop-imap/toggle.html
- https://support.gmx.com/pop-imap/imap/server.html
- https://support.gmx.com/security/2fa/application-specific-passwords.html
- https://hilfe.gmx.net/pop-imap/imap/outlook.html

## Verification

Fourteen focused checks passed: GMX presets/round-trip/default updates, both setup
choices on a small large-text screen, GMX help and blank private-domain fields,
legacy encrypted-account handling and local TLS IMAP/SMTP transport. Production
and test compilation passed. Full lint including test source has zero errors.
See email-gmx-verification.json and evidence/email-gmx. Local protocol fixtures are not a live GMX login
or physical Android handset acceptance. No APK was requested.
