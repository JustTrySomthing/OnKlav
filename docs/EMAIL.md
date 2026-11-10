# Email — native OnKlav app

Email is a signed built-in SDK package (`app.onklav.email`), dispatched to Kotlin/Compose host services. It shares OnKlav's shell, theme colors, font scale, safe areas and top-left app exit. It is not a separate APK. See FILES-SIMPLIFICATION.md for the current shared Files implementation and provider removals.

Current refinement: see EMAIL-EVERYDAY-POLISH.md and email-polish-verification.json for conversation grouping, recipient suggestions, mobile UI and draft/background reliability changes.

## Implemented workflow

- Up to 20 accounts; All inboxes or one account, cached header lists, unread filter, Starred and local Drafts & sent.
- TLS IMAP with server UIDVALIDITY/UID identity, batched header fetching, older pages, server sender/recipient/subject search and local header search.
- Server read/unread and star flags; multiple selection by long press; archive, Trash, copy, move and explicit permanent deletion. MOVE/UIDPLUS are required for safe move/targeted expunge; unsupported servers get a specific alternative, never a broad expunge of other mail.
- Server folder browser; create, rename and delete empty custom folders. Standard/special-use folders have no edit action; the protocol also refuses their modification.
- Named, editable and removable local inbox filters: sender, recipient or subject contains; star, mark read, or move to a selected account folder. They run on the messages checked by OnKlav, not continuously on the provider.
- Conversation grouping links explicit Message-ID/References/In-Reply-To headers within each account. Equal subjects alone never merge mail. Individual-message view remains available in Settings; only downloaded messages can be grouped.
- Inline recipient suggestions come from the selected account’s downloaded correspondence and local drafts, without phone-contact grants.
- Native rich text composer reuses Write's formatting engine and editor, with Email's own clipboard grant. To/Cc/Bcc, account selection, signature, bold/italic/underline/strike, numbered/bulleted lists, alignment, text/background colors and links.
- Reply, reply all (excluding the account's own address), forward, persistent local drafts and attachments. Draft state and send phase survive restart. Local sent copies remain available separately in Drafts & sent.
- MIME alternative plain text/HTML plus attachments; subject/address validation; Bcc recipients remain in the SMTP envelope and are omitted from the transmitted message headers.
- Native message rendering preserves safe text formatting and selectable text. There is no HTML WebView or script execution. Remote images are never loaded; external links require an explicit Open action.
- On-demand attachment download and original .eml export to OnKlav Files, cancellation and byte/progress feedback. Inbound display is bounded to 1 MB of text/500 MIME parts/16 nesting levels; original export is the alternative for larger email.
- Compose attachments stream into authenticated encryption (25 MB total, 20 files); no plaintext attachment spool is needed to send them. Actual server message-size limits may be smaller after MIME encoding.
- Device-bound Android Keystore AES-GCM encrypts account metadata, passwords, cached bodies/headers, drafts and attachments in no-backup storage. No plaintext/key fallback. Atomic writes, AAD-bound filenames and fail-closed lost-key behavior.
- Foreground Check mail and Android JobScheduler background checks (Manual/15/30/60 minutes). Background tasks and notifications require their separate OnKlav grants; Android notification/battery/network policy also applies. Notifications omit message content and open Email. Filters apply during background checks too.
- Clear downloaded mail keeps accounts/drafts/server content; removing an account removes its local credentials and private content without deleting provider mail. Removing Email cancels its scheduled job.

## Current account choices

New-account setup offers **Yahoo**, **Google** and **Private domain**.
Yahoo and Google use provider-issued app passwords with secure server presets;
Setup directions links to official instructions and account settings. There is
no OAuth choice, Google sign-in button or publisher registration required for
these new connections. Google app passwords require two-step verification and
are unavailable for some account policies. Private-domain setup uses user-supplied secure IMAP/SMTP settings. GMX and all dedicated retired-provider presets, OAuth transport and retirement/migration code have been deleted. See FILES-SIMPLIFICATION.md.

## Provider connection requirements

| Provider | Native path | Qualification status |
| --- | --- | --- |
| Google | TLS IMAP/SMTP with provider-issued app password | Requires an eligible account with two-step verification. Live account acceptance remains open. |
| Yahoo | TLS IMAP/SMTP with Yahoo-generated app password | Live account acceptance remains open. |
| Private domain | User-supplied IMAP TLS and SMTP TLS/required STARTTLS, servers, ports, usernames and password | Standards-compatible servers; live account acceptance remains open. |

Official setup references are linked in the account form. No real account was accessed or message sent to a recipient during implementation.

## Sending and recovery

Sending requires an explicit confirmation showing To/Cc/Bcc and subject. Credentials are retrieved only inside the host and are bound to the selected account. All network protocols validate server identity, require TLS 1.2/1.3 and have bounded connection/read/write timeouts.

The send transaction persists `sending` before SMTP DATA submission. Once accepted, it persists `sent` before attempting a server Sent-folder copy. Loss of acknowledgement or partial delivery becomes `uncertain`; a crash while `sending` is presented as unconfirmed. There is no automatic resend. The user checks Sent and can explicitly create a new draft. Gmail normally files its own sent copy; other providers use a discovered Sent folder, with local-copy feedback if that step fails.

## Scope still outside this implementation

No full server-side rules/labels editor, snooze/scheduled send, mail encryption/signing (PGP/S/MIME), enterprise device policies, automatic configuration discovery, phone address-book autocomplete or POP/Exchange-specific protocol support. Recipient suggestions use cached correspondence. Background checks are periodic, not instant push/IMAP IDLE. Drafts are device-local rather than synchronized to the provider Drafts folder. Native Proton support cannot be supplied by an ordinary Android IMAP client.

## Verification

See `email-app-verification.json` and `docs/evidence/v0.97.0`. Compile, local test fixtures, UI host checks and lint are distinct from real-account and handset acceptance. Local TLS fixtures never weaken the production transport's certificate checks. No APK was requested for this increment.
