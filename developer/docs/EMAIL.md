# Read-only configured JMAP mail profile (working source)

Scope: these are optional guest HTTPS/JMAP/account APIs, distinct from the built-in Email app’s Yahoo/Google/private-domain app-password setup. See [current host](CURRENT-HOST.md).
Chunk35 provides `email::Mailbox`, host account connection, authenticated JMAP
header requests, response validation and revision-checked private snapshots.
Configure a fixed HTTPS JMAP API endpoint, server account ID and mailbox ID.
Use a host-issued opaque OAuth account handle as credential; never put passwords,
tokens or authorization headers in guest state. The signed manifest must declare
network.http, accounts.connect, the chosen account provider and exact API origin.
Existing host PKCE/vault, refresh/disconnect, revocation and cancellation paths
remain authoritative. Session discovery does not authorize newly discovered URLs.
There is no automatic provider choice or credential collection screen.

`email::request` issues authenticated POST with read-only Email/query + Email/get
and correlated result references. It requests only id/subject/from/receivedAt,
20 headers per page, at most five pages/100 headers and128KiB per HTTP response.
`page` checks account IDs, method names/tags, position, bounded query/object state,
complete unique identities and missing results before returning normalized plain
metadata. No raw body, HTML, remote images, attachments or executable content is
requested or rendered. Server method errors do not become successful empty pages.

`merge` replaces a first page only after complete validation. Later pages must
match the connection binding, query state and exact previous length, with no
cross-page duplicates. A changed query requires an explicit first-page refresh;
keep the previous saved snapshot meanwhile. This is bounded foreground window
resynchronization, not push/incremental full-mailbox synchronization.
Snapshots are capped at15KiB (below the existing16KiB collection row limit), so
large metadata may stop before100 headers. Never silently truncate or mark the
rest of a mailbox synchronized. `more` and `window_limited` are explicit flags.

`save` uses an atomic compare-revision collection transaction. Adopt the new
snapshot only after the save callback succeeds; conflicts/permission failures
retain old data. `load` returns a record; validate it using `saved(mailbox,record)`
before display to prevent showing another account/mailbox snapshot. Binding
includes endpoint, server account, mailbox and opaque host handle. `clear` removes
cached headers explicitly. Account disconnect does not implicitly erase saved
private mail: hide mismatched/disconnected data and offer clear under app policy;
delete-data removal uses the existing scope cleanup. Metadata remains private
app data under existing device storage policy, not a newly encrypted mail vault.

Supported transport is configured OAuth-compatible JMAP over the existing signed
HTTPS broker. IMAP/POP/SMTP/password auth, universal provider support, background
push, MIME bodies/attachments/drafts/send (chunk36) are not implemented here.
Reference app workflows and live-provider/phone acceptance remain separate gates.
The included example uses synthetic header fixtures and real collection/account
broker paths; it never fabricates OAuth success or native HTTP responses.
No Kotlin/core source changed, so existing verified JNI outputs are reused.
No APK/SDK release. Protocol references: [JMAP Mail](https://www.rfc-editor.org/rfc/rfc8621.html)
and [JMAP Core](https://www.rfc-editor.org/rfc/rfc8620.html).
