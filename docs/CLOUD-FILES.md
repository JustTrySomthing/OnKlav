# Cloud Files

Cloud Files is a host-native included app with a theme-tinted outline cloud icon.
It connects directly to user-chosen HTTPS WebDAV providers. There is no OnKlav
cloud backend, account database, relay subscription or new binary dependency.

## Workflow

- First launch offers Sign in and Create a free account. The connection form has
  provider choices, provider signup links, password visibility, concise sign-in
  help and a connection check before saving credentials.
- New connections offer pCloud US/EU and Koofr. Legacy provider editor choices have been removed; see FILES-SIMPLIFICATION.md. UI labels use plain cloud-storage wording.
  pCloud uses the account's data region; 2FA may require approving a login email.
  Koofr requires an app password and can restrict free-plan file types over WebDAV.
  Each setup choice links to the provider’s free-plan signup and sign-in help.
- Browse folders, search the current folder, refresh, move up, see file sizes and
  quota when reported. Create folders. Upload a user-selected OnKlav Files item,
  choosing its name; existing files are not silently overwritten.
- Save a cloud file to OnKlav Files. Choose the destination before network transfer. Downloads are staged in private cache; a failed filing keeps a complete copy for retry without redownloading. The staged copy is deleted after successful save/cancel/app exit;
  abandoned copies older than a day are cleaned on startup.
- Visible transfer progress and Cancel; no automatic write retry. Keep Cloud Files
  open during transfers. Interrupted uploads may leave a partial file online;
  the UI directs users to refresh before retrying. There is no remote auto-delete.
- Multiple accounts, edit sign-in, confirmed disconnect. Disconnect removes local
  saved sign-in, not online files. This is file access, not automatic backup/sync
  or a provider-specific Crypto/Vault implementation.
- Messages/Groups paperclip → Share via Cloud Files opens this browser in sharing
  mode. Browse an existing file or upload through OnKlav Files, confirm creation,
  review/copy the resulting link and add it to the current draft. Sending remains
  explicit. pCloud and Koofr sharing use separate bounded HTTPS provider API calls.
  See EMAIL-CLOUD-SHARING.md for scope and verification.

## Boundaries

Credentials and account metadata use an AtomicFile AES-256-GCM envelope backed by
AndroidSecretKeys in noBackupFilesDir. No plaintext or software-key fallback; an
unreadable existing envelope is kept rather than overwritten with a new key.
Account passwords are not in saved-instance state, URLs, logs or exported backups.
A provider-generated password for a public sharing link is distinct from account
credentials; if returned, it is displayed with that link for the recipient.
Sharing credentials are sent only to fixed pCloud regional API or Koofr API
origins, never to a file URL or returned public-link URL.

Requests are HTTPS-only, with Basic credentials confined to the configured
origin/root. No redirects, cookies or automatic connection/write retries. WebDAV
hrefs are checked for origin/root/direct-child boundaries, traversal and ambiguous
encoded separators. XML is namespace-aware, rejects DOCTYPE/entities, and has
response size/entry/depth/token bounds. Only successful propstat values are used.
Uploads use If-None-Match: * and one-shot streamed bodies. Downloads use a strong
ETag precondition when available and discard failed private temporary files.
Transfers are limited to 2 GB, folder listings to 4 MB / 10,000 entries and saved
accounts to 20. Provider/server limits may be lower.

All ordinary file selection/save actions use existing OnKlav Files contracts and
streams. Credential primitives, HTTP client library, app theme and navigation are
reused. No new SDK credential capability is exposed to guest apps.

## Google Drive retirement

Drive settings and attachment-sharing entry points are removed; setup has no
Drive step. The settings entry is now Backups with a neutral archive icon. The
existing app document, metadata and message backup/restore tools and file-copy
utility remain available through OnKlav Files. Legacy pending-upload data and
backend helpers are retained without an OAuth/sign-in UI; no existing remote
Google file or local backup is deleted during migration.

## Provider references checked 2026-11-01

- https://help.pcloud.com/article/connect-to-pcloud-using-webdav-and-rsync
- https://koofr.eu/help/koofr_with_webdav/how-do-i-connect-a-service-to-koofr-through-webdav/
- https://koofr.eu/help/share-files-and-folders/i-have-a-free-account-and-cannot-share-certain-files-why/
- https://www.4shared.com/features/access_to_4shared_with_webdav/howto.jsp
  (retrieval of the main domain was unavailable; its provider mirror publishes
  the same endpoint at https://www.4sharedfb.com/features/access_to_4shared_with_webdav/howto.jsp)

Production compilation, 14 focused host data/UI checks and full production/test lint passed with no errors. Evidence and source hashes are in cloud-files-verification.json and docs/evidence/cloud-files. A successful upload is acknowledged before refreshing the folder; failed refresh does not reopen upload/retry or lose the chosen filename.

No live account/provider transfer or physical phone qualification is claimed. No new APK has been built.
