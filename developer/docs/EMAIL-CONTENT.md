# Mail content, drafts and recovery (working source)

Scope: these are optional guest HTTPS/JMAP/account APIs, distinct from the built-in Email app’s Yahoo/Google/private-domain app-password setup. See [current host](CURRENT-HOST.md).
The public reference Mail controller now joins host OAuth accounts, configured
JMAP Session discovery, server-account/mailbox and sender-identity selection,
paged private headers, body reading, attachments and multiple local drafts.
Providers and every API/upload/download origin must be configured in the signed
manifest. Discovery never grants a new origin or copies an access token to app
state. Examples use placeholder providers; IMAP/POP/SMTP are unsupported.

`email_content` accepts server-decoded plain text (8KiB combined, up to8 parts)
and up to3 attachment descriptors of10MiB each. Explicit download uses the
configured JMAP template and existing private background download/status/cancel/
export services. HTML, scripts, remote images and attachments never execute.

`email_transfer::parse_mime` calls the real private `mail.mime.parse` service on
an immutable own file. The bounded RFC822 profile accepts16MiB input,16KiB headers,
100 headers/1000-byte lines, multipart mixed/alternative/related (depth8,32 parts),
strict base64/quoted-printable, encoded B/Q header words, and UTF8/ASCII/Latin1/
Windows1252 text. Plain text is limited to8KiB; max3 attachments/10MiB each.
RFC2231 UTF8 filenames with empty language are supported. Unsafe paths/control
characters, malformed encodings and unsupported charsets fail explicitly.
HTML is recorded as unavailable plain text and never rendered/fetched. This is
not universal MIME, S/MIME decryption or signature verification. Derived files
are disposable private cache; retained original imports can be reparsed.
Mail's private-file screen pages all own files, reopens MIME, exports selected
files and explicitly confirms deletion, including staged files left by an
interrupted import. Deletion does not remove an external original or server mail.

Compose edits To/subject/plain text (10 simple ASCII recipients,8000 body bytes,
13KiB serialized draft), adds/removes attachments and uploads a selected private
file through a configured authenticated HTTPS POST. Host validation resolves the
opaque owner file only after live signed grants/account checks; Android streams
64KiB chunks with repeated authorization and exact-size validation, max10MiB.
Paths cannot be supplied by the app. Redirects/retries remain disabled. Upload
receipts must match account/type/size before a server blob enters the draft.
Local files survive upload failure; canceled/failed upload does not submit mail.

Private optimistic saves preserve conflicts. Full scrollable review shows the
exact recipients/body/attachment names before native confirmation. A host-generated
attempt ID becomes the Message-ID; the sending lock and attempt are durably saved
before the single Email/set + EmailSubmission/set request. Reopening sending or
unknown state cannot automatically resend. A correlated receipt means provider
acceptance, never delivery. Result-save failure retains this distinction.

`email_recovery` queries that exact Message-ID and any associated submission.
Missing/ambiguous records never prove an email was unsent and do not unlock it.
An explicit duplicate-risk confirmation may unlock a new review/attempt; it does
not issue HTTP or reuse the old attempt. Known accepted mail can be moved to the
configured Sent mailbox with a separate idempotent Email/set, including explicit
retry; it never repeats submission. No automatic server-draft deletion is done.
Provider-side orphan drafts/uploads may require manual provider cleanup. Local
draft deletion never recalls mail. Unsaved Back offers save-before-exit or explicit
discard; failed saves retain the draft and uncertain/accepted outcome.

The standalone compose controller remains available; `controller_with_options`
adds slots/upload/Sent configuration. Reference Mail filters draft summaries by
current connection/account/mailbox/identity, and hides private body/header/draft
state on account change. App storage follows existing host policy, not a new
independently encrypted mail vault.

Evidence: docs/workflow-wrap-verification.json. Unit callback fixtures are
synthetic. Signed scenarios use real storage/MIME/broker paths and stop at native
HTTP/export requests; no actual email was sent. Real OAuth/provider, full Android
HTTP integration, phone UI/process recovery and delivery qualification remain
open. No APK, shipping package or SDK release.
